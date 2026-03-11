#!/bin/bash
# Launch script for DocFetcher MCP Server
# Usage: ./mcpServer/run.sh --indexes <path-to-indexes-directory>
#
# The MCP server communicates via JSON-RPC 2.0 over stdio.
# Configure in Claude Desktop's MCP settings:
# {
#   "mcpServers": {
#     "docfetcher": {
#       "command": "/path/to/docfetcher/mcpServer/run.sh",
#       "args": ["--indexes", "/path/to/indexes"]
#     }
#   }
# }

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Find Java - try Mill's cached JDK, then system Java
JAVA=""
if [ -z "${JAVA:-}" ]; then
    # Check Mill's coursier cache for JDK
    MILL_JAVA=$(find "$HOME/.cache/coursier" -name "java" -path "*/bin/java" -type f 2>/dev/null | head -1)
    if [ -n "$MILL_JAVA" ]; then
        JAVA="$MILL_JAVA"
    fi
fi
if [ -z "$JAVA" ]; then
    if command -v java &>/dev/null; then
        JAVA="java"
    else
        echo "Error: Java not found. Install Java 21+ or run ./mill mcpServer.compile first." >&2
        exit 1
    fi
fi

# Build classpath
CLASSES_DIR="$PROJECT_DIR/out/mcpServer/compile.dest/classes"
if [ ! -d "$CLASSES_DIR" ]; then
    echo "Error: MCP server not compiled. Run: ./mill mcpServer.compile" >&2
    exit 1
fi

# Main module classes
MAIN_CLASSES="$PROJECT_DIR/out/compile.dest/classes"

CP="$CLASSES_DIR"
if [ -d "$MAIN_CLASSES" ]; then
    CP="$CP:$MAIN_CLASSES"
fi

# Add all library JARs
while IFS= read -r -d '' jar; do
    CP="$CP:$jar"
done < <(find "$PROJECT_DIR/lib" -name "*.jar" ! -name "*-sources.jar" -print0 2>/dev/null)

exec "$JAVA" -cp "$CP" net.sourceforge.docfetcher.mcp.McpServer "$@"
