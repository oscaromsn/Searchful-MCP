#!/bin/bash
# Build the DocFetcher MCP Server fat JAR with properly merged Lucene SPI files.
# Usage: ./mcpServer/build-jar.sh
# Output: mcpServer/docfetcher-mcp.jar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

echo "Compiling MCP server..."
./mill mcpServer.compile

echo "Building assembly..."
./mill mcpServer.assembly

ASSEMBLY="out/mcpServer/assembly.dest/out.jar"
FIXED_JAR="$SCRIPT_DIR/docfetcher-mcp.jar"
TEMP_DIR=$(mktemp -d)

echo "Fixing Lucene SPI service files..."

# Strip the prepended shell script from Mill's assembly to get a pure JAR
# Find the PK zip signature offset
ZIP_OFFSET=$(python3 -c "
import sys
data = open('$ASSEMBLY', 'rb').read()
idx = data.find(b'PK\x03\x04')
print(idx if idx >= 0 else 0)
")

# Create pure JAR
tail -c "+$((ZIP_OFFSET + 1))" "$ASSEMBLY" > "$TEMP_DIR/pure.jar"

# Extract pure JAR contents
mkdir -p "$TEMP_DIR/contents"
cd "$TEMP_DIR/contents"
unzip -o -q "$TEMP_DIR/pure.jar"

# Merge service files from all library JARs
mkdir -p "$TEMP_DIR/merged_services"
while IFS= read -r -d '' jar; do
    EXTRACT_DIR="$TEMP_DIR/jar_extract"
    rm -rf "$EXTRACT_DIR"
    mkdir -p "$EXTRACT_DIR"
    unzip -o -q "$jar" "META-INF/services/*" -d "$EXTRACT_DIR" 2>/dev/null || continue
    if [ -d "$EXTRACT_DIR/META-INF/services" ]; then
        for svc in "$EXTRACT_DIR/META-INF/services/"*; do
            [ -f "$svc" ] || continue
            svc_name=$(basename "$svc")
            grep -v '^#' "$svc" | grep -v '^$' >> "$TEMP_DIR/merged_services/$svc_name" 2>/dev/null || true
        done
    fi
done < <(find "$PROJECT_DIR/lib" -name "*.jar" -print0 2>/dev/null)

# Deduplicate and replace service files in extracted contents
for svc in "$TEMP_DIR/merged_services/"*; do
    [ -f "$svc" ] || continue
    sort -u "$svc" | grep -v '^$' > "$svc.dedup"
    svc_name=$(basename "$svc")
    mkdir -p "$TEMP_DIR/contents/META-INF/services"
    mv "$svc.dedup" "$TEMP_DIR/contents/META-INF/services/$svc_name"
done

# Find jar tool
JAR_TOOL=$(find "$HOME/.cache/coursier" -name "jar" -path "*/bin/jar" -type f 2>/dev/null | head -1)
if [ -z "$JAR_TOOL" ]; then JAR_TOOL="jar"; fi

# Repack as JAR with main class
cd "$TEMP_DIR/contents"
"$JAR_TOOL" cfe "$FIXED_JAR" net.sourceforge.docfetcher.mcp.McpServer .

cd "$PROJECT_DIR"
rm -rf "$TEMP_DIR"

echo "Built: $FIXED_JAR ($(du -h "$FIXED_JAR" | cut -f1))"
echo ""
echo "Usage:"
echo "  java -jar mcpServer/docfetcher-mcp.jar --indexes /path/to/indexes"
