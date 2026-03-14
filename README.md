# Searchful-MCP

An [MCP (Model Context Protocol)](https://modelcontextprotocol.io/) server that exposes [DocFetcher](http://docfetcher.sourceforge.net/)'s full-text search capabilities to LLMs. This allows AI assistants like Claude to search your locally indexed documents, find relevant files, and retrieve text excerpts — all through your existing DocFetcher indexes.

Searchful-MCP is a fork of DocFetcher 1.1 that adds a headless MCP server alongside the original desktop application. The MCP server reuses DocFetcher's Lucene-based indexing and parsing infrastructure without any GUI dependency.

## How It Works

```
LLM Client (Claude Desktop, Claude Code, etc.)
    |  MCP protocol (JSON-RPC 2.0 over stdio)
    v
McpServer              Parses JSON-RPC messages, dispatches to tools
    |
    |-- IndexManager   Discovers & opens Lucene indexes, runs searches
    |       |
    |       |-- Scans --indexes dir for subdirectories containing Lucene segments
    |       |-- Opens each with DirectoryReader (Lucene 6.6.3)
    |       |-- Combines into DecoratedMultiReader for cross-index search
    |       |-- Parses queries with PhraseDetectingQueryParser
    |       |-- Applies filters (size, file type) as BooleanQuery clauses
    |       `-- Extracts stored fields (UID, filename, title, size, etc.)
    |
    `-- ContentExtractor   Retrieves document text on demand
            |
            |-- Looks up parser from the Lucene index (PARSER field)
            |-- Calls ParseService.renderText() to re-parse the original file
            |-- Falls back to plain text reading if parsing fails
            `-- Uses Lucene Highlighter for snippet extraction with >>markers<<
```

## Prerequisites

- **Java 21+** — Mill will download a JDK automatically on first build if none is found
- **DocFetcher indexes** — created via the DocFetcher desktop application (this project, or the [upstream release](http://docfetcher.sourceforge.net/))

## Quick Start

```bash
# 1. Clone the repository
git clone <repo-url>
cd Searchful-MCP

# 2. Compile the MCP server
./mill mcpServer.compile

# 3. Run against your DocFetcher indexes
./mcpServer/run.sh --indexes /path/to/your/indexes
```

The `--indexes` path is the directory containing your DocFetcher index folders — typically `~/.docfetcher/indexes` on Linux/macOS, or the `indexes` folder inside a portable DocFetcher installation.

## Building

### Option A: Launch Script (recommended for development)

```bash
./mill mcpServer.compile
./mcpServer/run.sh --indexes /path/to/indexes
```

Compiles the MCP server as a Mill submodule and runs it using the classpath directly. Fastest iteration cycle.

### Option B: Fat JAR (recommended for distribution)

```bash
./mcpServer/build-jar.sh
java -jar mcpServer/docfetcher-mcp.jar --indexes /path/to/indexes
```

Produces a self-contained ~75 MB JAR that bundles all dependencies. The build script handles merging Lucene's SPI service files, which is required for the fat JAR to work correctly.

### Compiling the Full Project

```bash
# Compile everything (both root module and MCP server)
./mill compile

# Clean build (required to see all compiler warnings)
./mill clean && ./mill compile
```

The build system is [Mill](https://mill-build.org/) 1.1.0-RC3. It compiles incrementally, so running `./mill compile` twice in a row may not show warnings the second time.

## Configuring with Claude Desktop

Add to your Claude Desktop MCP configuration (`claude_desktop_config.json`):

### Using the launch script

```json
{
  "mcpServers": {
    "docfetcher": {
      "command": "/absolute/path/to/Searchful-MCP/mcpServer/run.sh",
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

## MCP Tools

The server exposes three tools:

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

### `get_document_content`

Retrieve the text content of a file, optionally with highlighted snippets around query matches.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `path` | string | yes | File path (typically from search results) |
| `query` | string | no | Query string for highlighting matches |
| `max_chars` | integer | no | Max characters to return (default 5000, max 100000) |

When a `query` is provided, the tool returns snippets around matches with `>>highlighted<<` markers instead of the full document text. Without a query, it returns the raw text truncated to `max_chars`.

The original file must be accessible on disk — document content is **not** stored in the Lucene index, so the file is re-parsed on each request.

### `list_indexes`

List all loaded DocFetcher indexes. Takes no parameters.

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

## Query Syntax

The `search` tool accepts [Lucene query syntax](https://lucene.apache.org/core/6_6_3/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description):

| Query | Description |
|-------|-------------|
| `machine learning` | Documents containing both terms |
| `"neural network"` | Exact phrase match |
| `python OR java` | Documents containing either term |
| `deploy AND NOT staging` | Boolean combination |
| `title:report` | Search in the title field only |
| `author:"Jane Doe"` | Search in the author field |
| `filename:*.pdf` | Search by filename |
| `deploy*` | Wildcard — matches "deploy", "deployment", etc. |
| `roam~` | Fuzzy — matches "foam", "roam", etc. |
| `"server error"~5` | Proximity — terms within 5 words of each other |

**Available search fields:** `content` (default), `title`, `author`, `filename`, `type`, `subject`, `sender`, `recipients` (email-only fields: subject, sender, recipients).

## Supported File Formats

DocFetcher's parsers can index and extract text from the following formats:

| Category | Formats |
|----------|---------|
| **PDF** | `.pdf` |
| **Microsoft Office 2007+** | `.docx`, `.docm`, `.dotx`, `.dotm`, `.xlsx`, `.xlsm`, `.xltx`, `.xltm`, `.pptx`, `.pptm`, `.ppsx`, `.ppsm`, `.potx`, `.potm` |
| **Microsoft Office (legacy)** | `.doc`, `.dot`, `.xls`, `.xlt`, `.ppt`, `.pps`, `.pot`, `.vsd`, `.vss`, `.vst`, `.vsw` |
| **LibreOffice / OpenOffice** | `.odt`, `.ott`, `.ods`, `.ots`, `.odp`, `.otp`, `.odg`, `.otg` |
| **Web & markup** | `.html`, `.htm`, `.xhtml`, `.asp` |
| **E-books** | `.epub`, `.chm` |
| **Rich text** | `.rtf` |
| **Plain text** | `.txt`, `.java`, `.py`, `.cpp`, `.c`, `.h`, `.xml`, `.json`, `.yaml`, `.csv`, `.md`, `.log`, `.sh`, and other text-based files |
| **Graphics (metadata)** | `.svg`, `.jpg`/`.jpeg` (EXIF metadata) |
| **Audio (metadata)** | `.mp3`, `.flac` (tags only) |
| **AbiWord** | `.abw`, `.abw.gz`, `.zabw` |
| **Email** | Outlook PST archives (via DocFetcher's email indexing) |

## Lucene Index Schema

Each indexed document is stored with the following fields (defined in `Fields.java`):

| Field | Type | Stored | Description |
|-------|------|--------|-------------|
| `uid` | String | Yes | Unique ID (`file:///path` or `outlook://path`) |
| `content` | Text | **No** | Full text — tokenized for search but not stored |
| `type` | String | Yes | File extension (e.g. `pdf`, `docx`) |
| `size` | Long | Yes | File size in bytes |
| `parser` | String | Yes | Parser class name used for indexing |
| `filename` | Text | Yes | Original filename |
| `title` | Text | Yes | Document title (from metadata) |
| `author` | Text | Yes | Document author (from metadata) |
| `last_modified` | String | Yes | File modification timestamp (epoch millis) |
| `subject` | Text | Yes | Email subject (emails only) |
| `sender` | Text | Yes | Email sender (emails only) |
| `recipients` | Text | Yes | Email recipients (emails only) |
| `date` | String | Yes | Email date (emails only) |

The `content` field uses `TextField.TYPE_NOT_STORED` — text is tokenized and indexed for full-text search, but the original content is not stored in the index. This is why `get_document_content` must re-parse the original file from disk.

## Project Structure

```
Searchful-MCP/
├── build.mill                      Mill build configuration (two modules)
├── mcpServer/                      MCP server module
│   ├── src/net/sourceforge/docfetcher/mcp/
│   │   ├── McpServer.java          Stdio loop, JSON-RPC 2.0, MCP protocol
│   │   ├── IndexManager.java       Index discovery, Lucene search, result extraction
│   │   ├── ContentExtractor.java   File re-parsing, snippet highlighting
│   │   └── TestIndexCreator.java   Utility to create sample indexes for testing
│   ├── run.sh                      Launch script (classpath-based, for development)
│   └── build-jar.sh                Fat JAR builder (merges Lucene SPI files)
├── src/                            DocFetcher core (root Mill module)
│   └── net/sourceforge/docfetcher/
│       ├── Main.java               Desktop app entry point
│       ├── gui/                    SWT desktop UI (Application.java, dialogs, panels)
│       ├── model/                  Core business logic (~124 files)
│       │   ├── Fields.java         Lucene field definitions enum
│       │   ├── LuceneIndex.java    Interface for Lucene indexes
│       │   ├── index/              Indexing logic, DecoratedMultiReader
│       │   ├── parse/              Document parsers (22 parser classes)
│       │   └── search/             Search logic, PhraseDetectingQueryParser
│       ├── util/                   Collections, concurrency, GUI helpers
│       └── enums/                  Configuration enums
├── lib/                            Vendored third-party JARs
│   ├── lucene/                     Lucene 6.6.3
│   ├── jackson/                    Jackson JSON (for MCP communication)
│   ├── swt/                        Platform-specific SWT JARs
│   ├── pdfbox/                     PDF parsing
│   ├── poi/                        MS Office parsing
│   ├── tika/                       Document format detection
│   └── ...                         ~40 library directories total
├── dist/                           Distribution assets
│   ├── program-conf.txt            Advanced program configuration
│   ├── lang/                       UI language files
│   └── help/                       Multi-language documentation
└── dev/                            Development tools, icons, test files
```

### Module Layout

The `build.mill` defines two Mill modules:

- **Root module** (`package`) — the DocFetcher desktop application. Entry point: `net.sourceforge.docfetcher.Main`. Depends on SWT and all vendored JARs in `lib/`. The build auto-detects the OS and selects the correct platform-specific SWT JAR.
- **`mcpServer`** — nested submodule that depends on the root module. Entry point: `net.sourceforge.docfetcher.mcp.McpServer`. Reuses DocFetcher's model and parsing classes but never touches SWT at runtime.

## Key Design Decisions

- **No GUI dependency at runtime.** The MCP server reuses DocFetcher's model and parsing classes but runs completely headless. SWT is on the compile classpath (inherited from the root module) but is never loaded at runtime.

- **Read-only index access.** Lucene supports concurrent readers, so the MCP server can safely run alongside the DocFetcher GUI without conflicts or locking issues.

- **Content is not stored in the index.** DocFetcher indexes text for searching but does not store it (the `CONTENT` field uses `TextField.TYPE_NOT_STORED`). The `get_document_content` tool re-parses the original file each time, which means the file must still be accessible on disk at its original path.

- **Analyzer compatibility.** The server defaults to `StandardAnalyzer` with an empty stop-word set, matching DocFetcher's default configuration. If you indexed with a different analyzer (source code, Chinese/ANSJ, whitespace), queries may not match correctly. The analyzer setting is in DocFetcher's Settings > Advanced > Lucene Analyzer.

- **Smart quote normalization.** The server automatically normalizes Unicode smart quotes (`\u201C`, `\u201D`, etc.) to ASCII quotes before parsing queries, so copy-pasted text from word processors works correctly.

- **Score normalization.** Search result scores are normalized to a 0-100 scale relative to the top-scoring result in each query.

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

The `TestIndexCreator` utility creates a Lucene index with 5 synthetic documents (PDFs, Word docs, HTML, text) that can be searched without needing real files on disk. Note that `get_document_content` will not work against this test index since the referenced files don't exist.

## Troubleshooting

**"Loaded 0 index(es)"**
The `--indexes` path doesn't contain any Lucene index directories. Make sure you're pointing to the directory that *contains* the index folders (e.g., `my-docs_1234567890/`), not to an index folder itself.

**Search returns no results for a query that works in the DocFetcher GUI**
Likely an analyzer mismatch. The MCP server defaults to `StandardAnalyzer`. If you changed DocFetcher's analyzer setting (Settings > Advanced > Lucene Analyzer), the tokenization won't match.

**`get_document_content` returns "File not found"**
The original file is no longer at the path stored in the index. This happens if files were moved or deleted after indexing, or if the index was created on a different machine.

**Fat JAR crashes with "Cannot instantiate SPI class"**
The Lucene SPI service files weren't merged correctly. Rebuild with `./mcpServer/build-jar.sh` which handles the merge, or use `./mcpServer/run.sh` instead.

**Compilation errors**
Build output can be very long. Redirect to a file for analysis:
```bash
./mill compile &> /tmp/build-output.txt
grep "\[error\]" /tmp/build-output.txt
```

## Running the Desktop Application

The DocFetcher GUI can be run from IntelliJ IDEA. See `readme.txt` for full IDE setup instructions. Key requirements:

- Set the `SWT_JAR` path variable to point to the correct platform JAR in `lib/swt/`
- Set VM options:
  - **Windows:** `-Djava.library.path="lib/jnotify;lib/jintellitype"`
  - **Linux:** `-Djava.library.path="lib/jnotify:lib/jxgrabkey"`
  - **macOS:** `-Djava.library.path="lib/jnotify" -XstartOnFirstThread`
- Main class: `net.sourceforge.docfetcher.gui.Application`

## Third-Party Libraries

All dependencies are vendored as JARs in the `lib/` directory. Key libraries:

| Library | Version | Purpose |
|---------|---------|---------|
| Apache Lucene | 6.6.3 | Full-text search and indexing engine |
| SWT | 4.34 | Cross-platform GUI toolkit (desktop app only) |
| Apache Tika | — | Document format detection and parsing |
| Apache PDFBox | — | PDF text extraction |
| Apache POI | — | Microsoft Office format parsing |
| Jackson | — | JSON serialization (MCP server communication) |
| JNA | — | Java Native Access (platform integration) |
| Guava | — | Core Java utilities |

## License

DocFetcher is licensed under the [Eclipse Public License v1.0](dist/epl-v10.html).
