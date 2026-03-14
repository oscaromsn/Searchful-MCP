# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Searchful-MCP is a fork of DocFetcher (open-source desktop full-text search app) enhanced with an MCP (Model Context Protocol) server. The MCP server exposes DocFetcher's Lucene-based search capabilities to LLMs over JSON-RPC 2.0 via stdio. The main DocFetcher codebase is a Java/SWT application; the MCP server runs headlessly without any SWT dependency.

## Build System

**Mill 1.1.0-RC3** (Scala-based build tool). Java 21 required.

```bash
# Compile everything (incremental)
./mill compile

# Compile only the MCP server submodule
./mill mcpServer.compile

# Clean build (to see all warnings)
./mill clean && ./mill compile
```

**Important:** Build output can be very long. Always redirect to a file for analysis:
```bash
./mill compile &> /tmp/build-output.txt
grep "\[error\]" /tmp/build-output.txt   # find errors
grep "\[warn\]" /tmp/build-output.txt    # find warnings
```

Mill compiles incrementally — compiling twice in a row may not show warnings the second time. Use `./mill clean && ./mill compile` to see all warnings.

## Running

```bash
# MCP server via launch script (dev)
./mcpServer/run.sh --indexes /path/to/indexes

# MCP server via fat JAR (distribution)
./mcpServer/build-jar.sh
java -jar mcpServer/docfetcher-mcp.jar --indexes /path/to/indexes

# DocFetcher desktop app (requires platform-specific SWT setup)
# Main class: net.sourceforge.docfetcher.gui.Application
# See readme.txt for IntelliJ IDEA configuration
```

## Testing

```bash
# Create a sample index with 5 test documents, then query it
./mill mcpServer.compile
CP=$(./mill show mcpServer.runClasspath 2>/dev/null \
  | tr -d '[]"' | tr ',' '\n' | grep "ref:" \
  | sed 's|.*ref:v0:[^:]*:||' | tr '\n' ':')
java -cp "$CP" net.sourceforge.docfetcher.mcp.TestIndexCreator /tmp/test-indexes

# Send JSON-RPC messages via stdin
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search","arguments":{"query":"machine learning"}}}' \
  | ./mcpServer/run.sh --indexes /tmp/test-indexes
```

No JUnit/TestNG framework is currently configured. Some test utility classes exist in `src/` (e.g., `UtilTest`, `PathTest`).

## Architecture

### Two-Module Mill Build

The `build.mill` defines two modules:
- **Root module** (`package`) — the DocFetcher desktop app. Main class: `net.sourceforge.docfetcher.Main`. Depends on SWT and all JARs in `lib/`.
- **`mcpServer`** — nested submodule depending on the root module. Main class: `net.sourceforge.docfetcher.mcp.McpServer`. Reuses model/parsing classes but never touches SWT at runtime.

### MCP Server (`mcpServer/src/.../mcp/`)

Three classes implement the full MCP server:

- **`McpServer.java`** — Stdio loop reading JSON-RPC 2.0 messages. Handles MCP protocol lifecycle (`initialize`, `tools/list`, `tools/call`). Dispatches to IndexManager and ContentExtractor.
- **`IndexManager.java`** — Discovers Lucene indexes on disk, opens `DirectoryReader`s, combines them via `DecoratedMultiReader`. Runs searches using `PhraseDetectingQueryParser` with filters (size, file type) composed as `BooleanQuery` clauses. Extracts stored field metadata from results.
- **`ContentExtractor.java`** — Re-parses files on demand since content is not stored in the Lucene index (the `CONTENT` field uses `TextField.TYPE_NOT_STORED`). Uses DocFetcher's `ParseService` for format-specific extraction. Falls back to plain text. Uses Lucene `Highlighter` for snippet extraction with `>>markers<<`.

Three MCP tools are exposed: `search`, `get_document_content`, `list_indexes`.

### DocFetcher Core (`src/net/sourceforge/docfetcher/`)

- **`model/`** — Core business logic (~124 Java files). Key types: `LuceneIndex` (interface), `Fields` (Lucene field definitions enum), plus `index/`, `parse/`, and `search/` subpackages.
- **`gui/`** — SWT desktop UI. Entry point: `Application.java`.
- **`util/`** — Collections, concurrency, GUI helpers.
- **`enums/`** — Configuration enums.

### Key Design Decisions

- **Content not stored in index.** `get_document_content` must re-parse the original file from disk each time.
- **Read-only index access.** Lucene supports concurrent readers, so the MCP server can run alongside the DocFetcher GUI.
- **Analyzer compatibility.** Server defaults to `StandardAnalyzer` with empty stop-word set, matching DocFetcher's default. Mismatched analyzers will cause search failures.
- **Platform-specific JARs.** The build auto-detects the OS and selects the correct SWT JAR from `lib/swt/`.

### Dependencies

All dependencies are vendored JARs in `lib/`. Key libraries: Lucene 6.6.3, SWT, Tika, Jackson (JSON for MCP), PDFBox, Apache POI, JNA, Commons.
