# DocFetcher MCP Server

An [MCP (Model Context Protocol)](https://modelcontextprotocol.io/) server that exposes DocFetcher's full-text search capabilities to LLMs. This allows AI assistants like Claude to search your locally indexed documents, find relevant files, and retrieve text excerpts — all through your existing DocFetcher indexes.

## Prerequisites

- **Java 21+** (Mill will download one automatically on first build)
- **DocFetcher indexes** already created via the DocFetcher desktop app

## Quick Start

```bash
# 1. Compile (from the DocFetcher project root)
./mill mcpServer.compile

# 2. Run
./mcpServer/run.sh --indexes /path/to/your/indexes
```

The `--indexes` path is the directory containing your DocFetcher index folders — typically `~/.docfetcher/indexes` on Linux/macOS, or the `indexes` folder inside a portable DocFetcher installation.

## Building

### Option A: Launch Script (recommended for development)

```bash
./mill mcpServer.compile
./mcpServer/run.sh --indexes /path/to/indexes
```

This compiles the MCP server as a Mill submodule and runs it using the classpath directly. Fastest iteration cycle.

### Option B: Fat JAR (recommended for distribution)

```bash
./mcpServer/build-jar.sh
java -jar mcpServer/docfetcher-mcp.jar --indexes /path/to/indexes
```

This produces a self-contained 75 MB JAR that bundles all dependencies. The build script handles merging Lucene's SPI service files, which is required for the fat JAR to work correctly.

## Configuring with Claude Desktop

Add to your Claude Desktop MCP configuration (`claude_desktop_config.json`):

### Using the launch script

```json
{
  "mcpServers": {
    "docfetcher": {
      "command": "/absolute/path/to/docfetcher/mcpServer/run.sh",
      "args": ["--indexes", "/absolute/path/to/indexes"]
    }
  }
}
```

### Using the fat JAR

```json
{
  "mcpServers": {
    "docfetcher": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/mcpServer/docfetcher-mcp.jar", "--indexes", "/absolute/path/to/indexes"]
    }
  }
}
```

## Tools

The server exposes three MCP tools:

### `search`

Find documents matching a query across all loaded indexes.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query` | string | yes | Lucene query string |
| `file_types` | string[] | no | Filter by extension, e.g. `["pdf", "docx"]` |
| `max_results` | integer | no | Max results to return (default 20, max 100) |
| `min_size_kb` | integer | no | Minimum file size in KB |
| `max_size_kb` | integer | no | Maximum file size in KB |

**Returns:** JSON array of matching documents with metadata:

```json
[
  {
    "uid": "file:///home/user/docs/report.pdf",
    "filename": "report.pdf",
    "path": "/home/user/docs/report.pdf",
    "title": "Annual Report",
    "authors": "Jane Doe",
    "type": "pdf",
    "size_kb": 1024,
    "score": 95,
    "last_modified": "2025-01-15T10:30:00",
    "is_email": false
  }
]
```

**Query syntax examples:**

| Query | Description |
|-------|-------------|
| `machine learning` | Documents containing both terms |
| `"neural network"` | Exact phrase match |
| `python OR java` | Documents containing either term |
| `title:report` | Search only in the title field |
| `deploy*` | Wildcard — matches "deploy", "deployment", etc. |
| `roam~` | Fuzzy — matches "foam", "roam", etc. |
| `"server error"~5` | Proximity — terms within 5 words of each other |

### `get_document_content`

Retrieve the text content of a file, optionally with highlighted snippets around query matches.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `path` | string | yes | File path (typically from search results) |
| `query` | string | no | Query string for highlighting matches |
| `max_chars` | integer | no | Max characters to return (default 5000) |

When a `query` is provided, the tool returns snippets around matches with `>>highlighted<<` markers instead of the full document text. Without a query, it returns the raw text truncated to `max_chars`.

The original file must be accessible on disk — document content is **not** stored in the Lucene index, so the file is re-parsed on each request.

**Content extraction supports:** PDF, DOCX, DOC, XLSX, XLS, PPTX, PPT, HTML, ODT, RTF, plain text, and other formats supported by DocFetcher's parsers. Falls back to reading as plain text if no parser matches.

### `list_indexes`

List all loaded DocFetcher indexes.

**Returns:**

```json
[
  {
    "name": "My Documents",
    "root_path": "/home/user/docs",
    "document_count": 1523,
    "is_email_index": false
  }
]
```

## How It Works

```
LLM Client (Claude Desktop, etc.)
    |  MCP protocol (JSON-RPC 2.0 over stdio)
    v
McpServer.java          -- Parses JSON-RPC messages, dispatches to tools
    |
    |-- IndexManager.java   -- Discovers & opens Lucene indexes, runs searches
    |       |
    |       |-- Scans --indexes dir for subdirectories containing Lucene segments
    |       |-- Opens each with DirectoryReader (Lucene 6.6.3)
    |       |-- Combines into DecoratedMultiReader for cross-index search
    |       |-- Parses queries with PhraseDetectingQueryParser
    |       |-- Applies filters (size, file type) as BooleanQuery clauses
    |       |-- Extracts stored fields (UID, filename, title, size, etc.)
    |       |
    |       `-- Optionally deserializes tree-index.ser for richer metadata
    |
    `-- ContentExtractor.java  -- Retrieves document text on demand
            |
            |-- Looks up parser name from the Lucene index (PARSER field)
            |-- Calls ParseService.renderText() to re-parse the original file
            |-- Falls back to plain text reading if parsing fails
            `-- Uses Lucene Highlighter for snippet extraction with >>markers<<
```

### Key design decisions

- **No GUI dependency at runtime.** The MCP server reuses DocFetcher's model and parsing classes but never touches SWT. It runs headlessly.
- **Read-only index access.** Lucene supports concurrent readers, so the MCP server can run alongside the DocFetcher GUI without conflict.
- **Content is not stored in the index.** DocFetcher indexes text for searching but does not store it (the `CONTENT` field uses `TextField.TYPE_NOT_STORED`). The `get_document_content` tool re-parses the original file each time, which means the file must still be accessible on disk.
- **Analyzer compatibility.** The server defaults to `StandardAnalyzer` with an empty stop-word set, matching DocFetcher's default. If you indexed with a different analyzer (source code, Chinese/ANSJ, whitespace), queries may not match correctly.

## Project Structure

```
mcpServer/
  src/net/sourceforge/docfetcher/mcp/
    McpServer.java          Main class — stdio loop, JSON-RPC, MCP protocol
    IndexManager.java       Index discovery, Lucene search, result extraction
    ContentExtractor.java   File re-parsing, snippet highlighting
    TestIndexCreator.java   Utility to create a sample index for testing
  run.sh                    Launch script (classpath-based)
  build-jar.sh              Fat JAR builder (merges Lucene SPI files)
  docfetcher-mcp.jar        Built fat JAR (after running build-jar.sh)

build.mill                  Mill build — mcpServer is a nested submodule
lib/jackson/                Jackson JSON library JARs (used by MCP server)
```

## Troubleshooting

**"Loaded 0 index(es)"** — The `--indexes` path doesn't contain any Lucene index directories. Make sure you're pointing to the directory that *contains* the index folders (e.g. `my-docs_1234567890/`), not to an index folder itself.

**Search returns no results for a query that works in the DocFetcher GUI** — Likely an analyzer mismatch. The MCP server defaults to `StandardAnalyzer`. If you changed DocFetcher's analyzer setting (Settings > Advanced > Lucene Analyzer), the tokenization won't match.

**`get_document_content` returns "File not found"** — The original file is no longer at the path stored in the index. This happens if files were moved or deleted after indexing, or if the index was created on a different machine.

**Fat JAR crashes with "Cannot instantiate SPI class"** — The Lucene SPI service files weren't merged correctly. Rebuild with `./mcpServer/build-jar.sh` which handles the merge, or use `./mcpServer/run.sh` instead.

## Testing

Create a sample index and query it:

```bash
# Compile
./mill mcpServer.compile

# Create test index with 5 sample documents
CP=$(./mill show mcpServer.runClasspath 2>/dev/null \
  | tr -d '[]"' | tr ',' '\n' | grep "ref:" \
  | sed 's|.*ref:v0:[^:]*:||' | tr '\n' ':')
java -cp "$CP" net.sourceforge.docfetcher.mcp.TestIndexCreator /tmp/test-indexes

# Run MCP server against it
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"search","arguments":{"query":"machine learning"}}}' \
  | ./mcpServer/run.sh --indexes /tmp/test-indexes
```
