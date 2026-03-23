# Searchful-MCP

Searchful-MCP is a fork of [DocFetcher](http://docfetcher.sourceforge.net/), a desktop document search application, extended with a **Model Context Protocol (MCP) server**. The MCP server exposes DocFetcher's Lucene-based document indexes to large language models and other MCP-compatible clients, enabling AI-powered search over locally indexed documents without requiring the GUI.

The project contains two independent entry points: the original DocFetcher desktop application (SWT-based GUI), and a new headless MCP server that communicates over stdio using JSON-RPC 2.0.


## Table of Contents

- [Features](#features)
- [Supported Document Formats](#supported-document-formats)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Building](#building)
- [Running the MCP Server](#running-the-mcp-server)
- [Adding to Claude Code](#adding-to-claude-code)
- [Adding to Claude Desktop](#adding-to-claude-desktop)
- [MCP Tools Reference](#mcp-tools-reference)
- [Running the Desktop Application](#running-the-desktop-application)
- [Lucene Index Format](#lucene-index-format)
- [Query Syntax](#query-syntax)
- [Creating a Test Index](#creating-a-test-index)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Internationalization](#internationalization)
- [Building Release Artifacts](#building-release-artifacts)
- [License](#license)


## Features

- Full-text search over thousands of documents using Apache Lucene 6.6.3
- MCP server for integrating document search into LLM workflows (Claude, etc.)
- Document content extraction with query-aware highlighted snippets
- Support for 20+ document formats including PDF, Office, email, and archives
- Cross-platform: Windows, Linux, and macOS
- Indexing of files within ZIP, 7z, RAR, and tar archives
- Outlook PST email indexing
- Regex-based file inclusion/exclusion rules during indexing
- Folder watching for automatic index updates
- Multi-language UI with 18 translations


## Supported Document Formats

| Category | Formats |
|---|---|
| PDF | PDF (with annotation extraction) |
| Microsoft Office (legacy) | DOC, XLS, PPT, VSD |
| Microsoft Office (2007+) | DOCX, XLSX, PPTX |
| OpenOffice / LibreOffice | ODT, ODS, ODP, ODG |
| Email | Outlook PST files |
| Web | HTML, XHTML, SHTML |
| Ebooks | EPUB, CHM |
| Images (metadata) | JPEG, TIFF, PNG (EXIF/XMP metadata extraction) |
| Audio (metadata) | MP3 (ID3 tags), FLAC (Vorbis comments) |
| Rich Text | RTF |
| Vector Graphics | SVG |
| Other | AbiWord (ABW), plain text (TXT and many source code extensions) |
| Archives | Contents of ZIP, 7z, RAR, and tar archives are indexed recursively |


## Architecture

The project is structured as two Mill modules sharing a common model layer:

```
+---------------------------+       +---------------------------+
|   DocFetcher GUI (SWT)    |       |   MCP Server (headless)   |
|   net.sourceforge.        |       |   net.sourceforge.        |
|   docfetcher.gui          |       |   docfetcher.mcp          |
+---------------------------+       +---------------------------+
|   McpServer               |       |   Application             |
|   IndexManager            |       |   SearchBar               |
|   ContentExtractor        |       |   ResultPanel             |
+-------------+-------------+       +-------------+-------------+
              |                                    |
              +------------------+-----------------+
                                 |
              +------------------v-----------------+
              |         Shared Core Model          |
              |   net.sourceforge.docfetcher.model  |
              |                                     |
              |   LuceneIndex, IndexRegistry,       |
              |   Fields, Document, TreeIndex,      |
              |   Folder, IndexingConfig            |
              +------------------+-----------------+
                                 |
         +-----------------------+-----------------------+
         |                       |                       |
+--------v--------+   +---------v--------+   +----------v---------+
|  model/index/   |   |  model/search/   |   |   model/parse/     |
|  IndexingQueue  |   |  Searcher        |   |   ParseService     |
|  FileIndex      |   |  HighlightSvc    |   |   PdfParser        |
|  OutlookIndex   |   |  PhraseDetecting |   |   MSOfficeParser   |
|  IndexingConfig |   |  QueryParser     |   |   HtmlParser, ...  |
+-----------------+   +------------------+   +--------------------+
```

The **MCP server** (`mcpServer` module) depends on the core module but does not use SWT or any GUI classes. It reads Lucene indexes directly and uses the core `Fields`, `LuceneIndex`, `ParseService`, and `PhraseDetectingQueryParser` classes.

The **GUI application** (root module) is the original DocFetcher desktop application providing index creation, management, searching, and document preview through an SWT-based interface.


## Prerequisites

- **JDK 21** or newer
- The JDK `bin` directory must be on your `PATH`
- No additional dependency management is needed; all JARs are bundled in the `lib/` directory


## Building

The project uses the [Mill](https://mill-build.org/) build tool. A Mill launcher script (`./mill`) is included in the repository.

### Compile everything (core + MCP server)

```bash
./mill compile
./mill mcpServer.compile
```

### Clean build (required to see all warnings)

```bash
./mill clean && ./mill compile &> /tmp/build-output.txt
```

Mill compiles incrementally, so subsequent compilations only process changed files. This means compiler warnings may not appear on the second run. Always use a clean build to see the full set of warnings.

### Inspecting build output

Build output can be very long. Always redirect it to a file and inspect with standard tools:

```bash
grep "\[error\]" /tmp/build-output.txt    # Find compilation errors
grep "\[warn\]" /tmp/build-output.txt     # Find compiler warnings
```

### Compile only the MCP server module

```bash
./mill mcpServer.compile
```


## Running the MCP Server

The MCP server is a headless process that communicates over stdio using JSON-RPC 2.0, following the [Model Context Protocol](https://modelcontextprotocol.io/) specification (protocol version `2024-11-05`).

There are three ways to run the server, ranging from quickest setup to most portable:

### Option A: Via Mill (development)

```bash
./mill mcpServer.run -- --indexes <path-to-indexes-directory>
```

This compiles and runs in one step. Convenient for development, but has startup overhead as Mill checks for compilation changes each time. Not suitable for MCP client configuration because Mill's own stdout output interferes with the JSON-RPC protocol.

### Option B: Via the launch script (recommended for local use)

```bash
./mcpServer/run.sh --indexes <path-to-indexes-directory>
```

The `run.sh` script assembles the classpath from compiled class directories and `lib/` JARs, then launches the server directly with `java`. It requires that the project has been compiled first with `./mill mcpServer.compile`. This is the recommended approach for configuring MCP clients because it starts quickly and produces clean stdio output.

### Option C: Via the fat JAR (recommended for distribution)

First, build the fat JAR:

```bash
./mcpServer/build-jar.sh
```

This produces `mcpServer/docfetcher-mcp.jar` (~75 MB), a self-contained JAR with all dependencies merged. It properly handles Lucene's SPI service file merging, which is required for the Lucene codecs to load correctly. Then run:

```bash
java -jar mcpServer/docfetcher-mcp.jar --indexes <path-to-indexes-directory>
```

The fat JAR is fully portable -- it can be copied to any machine with Java 21+ without needing the rest of the repository.

### The --indexes argument

The `--indexes` argument must point to a directory containing one or more DocFetcher Lucene index subdirectories. This is typically:

- `~/.docfetcher/indexes` (standard DocFetcher installation on Linux/macOS)
- `%APPDATA%\DocFetcher\indexes` (standard DocFetcher installation on Windows)
- The `indexes` folder inside a portable DocFetcher installation

Each subdirectory within the indexes directory should be a Lucene index (containing segment files). On startup, the server prints diagnostic information to stderr, including the number of indexes loaded and their document counts.


## Adding to Claude Code

Claude Code is Anthropic's CLI tool for interacting with Claude. It supports MCP servers natively, allowing Claude to call the `search`, `get_document_content`, and `list_indexes` tools during a conversation.

### Prerequisites

1. Compile the project: `./mill mcpServer.compile`
2. Know the absolute path to your DocFetcher indexes directory
3. Have Claude Code installed (`npm install -g @anthropic-ai/claude-code`)

### Method 1: CLI command (quickest)

Run this from the project root to register the server with Claude Code:

```bash
claude mcp add --transport stdio docfetcher -- \
  /absolute/path/to/Searchful-MCP/mcpServer/run.sh \
  --indexes /absolute/path/to/indexes
```

This adds the server to your local (user-level, project-scoped) configuration. To make it available across all projects, add `--scope user`:

```bash
claude mcp add --transport stdio --scope user docfetcher -- \
  /absolute/path/to/Searchful-MCP/mcpServer/run.sh \
  --indexes /absolute/path/to/indexes
```

If you built the fat JAR, you can use it instead:

```bash
claude mcp add --transport stdio docfetcher -- \
  java -jar /absolute/path/to/Searchful-MCP/mcpServer/docfetcher-mcp.jar \
  --indexes /absolute/path/to/indexes
```

### Method 2: JSON configuration (recommended for team sharing)

Create a `.mcp.json` file in your project root:

```json
{
  "mcpServers": {
    "docfetcher": {
      "type": "stdio",
      "command": "/absolute/path/to/Searchful-MCP/mcpServer/run.sh",
      "args": ["--indexes", "/absolute/path/to/indexes"]
    }
  }
}
```

Or with the fat JAR:

```json
{
  "mcpServers": {
    "docfetcher": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar", "/absolute/path/to/Searchful-MCP/mcpServer/docfetcher-mcp.jar",
        "--indexes", "/absolute/path/to/indexes"
      ]
    }
  }
}
```

The `.mcp.json` file can be committed to version control so that all team members get access to the same MCP server. When another user opens the project in Claude Code for the first time, they will be prompted to approve the server before it runs.

### Method 3: Direct JSON via CLI

```bash
claude mcp add-json docfetcher '{
  "type": "stdio",
  "command": "/absolute/path/to/Searchful-MCP/mcpServer/run.sh",
  "args": ["--indexes", "/absolute/path/to/indexes"]
}'
```

### Using environment variables in paths

The `.mcp.json` configuration supports environment variable expansion, which is useful for team setups where paths differ between machines:

```json
{
  "mcpServers": {
    "docfetcher": {
      "type": "stdio",
      "command": "${HOME}/projects/Searchful-MCP/mcpServer/run.sh",
      "args": ["--indexes", "${HOME}/.docfetcher/indexes"]
    }
  }
}
```

You can also use the `${VAR:-default}` syntax for fallback values:

```json
{
  "mcpServers": {
    "docfetcher": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar", "${DOCFETCHER_MCP_JAR:-/opt/docfetcher-mcp/docfetcher-mcp.jar}",
        "--indexes", "${DOCFETCHER_INDEXES:-/opt/docfetcher/indexes}"
      ]
    }
  }
}
```

### Verifying the server is connected

After adding the server, start Claude Code and run the `/mcp` slash command. You should see `docfetcher` listed with its three tools: `search`, `get_document_content`, and `list_indexes`.

You can also verify from the command line:

```bash
claude mcp list          # List all configured servers
claude mcp get docfetcher  # Show details for this server
```

### Scopes

Claude Code has three configuration scopes for MCP servers:

| Scope | Storage | Shared | Use case |
|---|---|---|---|
| `local` (default) | `~/.claude.json` | No | Personal, project-specific |
| `user` | `~/.claude.json` | No | Personal, available in all projects |
| `project` | `.mcp.json` in project root | Yes (via version control) | Team-wide, per-project |

When adding via CLI, specify the scope with `--scope`:

```bash
claude mcp add --transport stdio --scope project docfetcher -- ...
claude mcp add --transport stdio --scope user docfetcher -- ...
```

### Removing the server

```bash
claude mcp remove docfetcher
```

Or delete the entry from `.mcp.json` / `~/.claude.json` manually.

### Troubleshooting

**Server fails to start:**
- Ensure the project is compiled: `./mill mcpServer.compile`
- Check that Java 21+ is on your `PATH`: `java -version`
- Verify the indexes directory exists and contains index subdirectories

**Server starts but tools are not available:**
- Run `/mcp` inside Claude Code to check the server status
- Look at stderr output for errors (the server logs to stderr)
- Confirm the indexes directory is not empty

**"No indexes loaded" on startup:**
- The `--indexes` path must point to the parent directory that *contains* index subdirectories, not to an index subdirectory itself
- Each index subdirectory must contain Lucene segment files (e.g., `segments_1`)

**Windows-specific:**
- On native Windows (not WSL), use backslashes or forward slashes in paths -- both work in the JSON config
- If using WSL, the `run.sh` script works natively; ensure the indexes path is a WSL-accessible path (e.g., `/mnt/c/Users/.../indexes`)
- On native Windows, you may need to invoke `run.sh` through bash:
  ```json
  {
    "mcpServers": {
      "docfetcher": {
        "type": "stdio",
        "command": "bash",
        "args": [
          "C:/path/to/Searchful-MCP/mcpServer/run.sh",
          "--indexes", "C:/path/to/indexes"
        ]
      }
    }
  }
  ```


## Adding to Claude Desktop

For Claude Desktop (the graphical app), add the server to `claude_desktop_config.json`:

- macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
- Windows: `%APPDATA%\Claude\claude_desktop_config.json`

Using the launch script:

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

Using the fat JAR:

```json
{
  "mcpServers": {
    "docfetcher": {
      "command": "java",
      "args": [
        "-jar", "/absolute/path/to/Searchful-MCP/mcpServer/docfetcher-mcp.jar",
        "--indexes", "/absolute/path/to/indexes"
      ]
    }
  }
}
```

Restart Claude Desktop after editing the configuration. The `search`, `get_document_content`, and `list_indexes` tools will appear in Claude's tool list.


## MCP Tools Reference

The MCP server exposes three tools:

### search

Search across all loaded DocFetcher indexes. Returns document metadata (not content).

| Parameter | Type | Required | Description |
|---|---|---|---|
| `query` | string | Yes | Lucene query string (see [Query Syntax](#query-syntax)) |
| `file_types` | string[] | No | Filter by file extension, e.g. `["pdf", "docx"]` |
| `max_results` | integer | No | Maximum results to return (default: 20, max: 100) |
| `min_size_kb` | integer | No | Minimum file size in KB |
| `max_size_kb` | integer | No | Maximum file size in KB |

Returns an array of result objects, each containing: `uid`, `filename`, `path`, `title`, `authors`, `type`, `size_kb`, `score` (0-100), `last_modified` (ISO 8601), and `is_email`.

### get_document_content

Retrieve the text content of a document by file path. The original file must be accessible on disk (it is re-parsed at request time). Optionally provide a query to receive highlighted snippets around matches instead of the full text.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `path` | string | Yes | File path (as returned in search results) |
| `query` | string | No | Query for highlighting matches in the content |
| `max_chars` | integer | No | Maximum characters to return (default: 5000, max: 100000) |

When a `query` is provided, the response contains the best matching text fragments with matches delimited by `>>` and `<<` markers. Fragments are separated by `\n...\n`.

### list_indexes

List all loaded indexes with metadata. Takes no parameters.

Returns an array of objects, each containing: `name`, `root_path`, `document_count`, and `is_email_index`.


## Running the Desktop Application

The desktop application requires SWT (Standard Widget Toolkit), which is platform-specific. The correct SWT JAR is automatically selected from `lib/swt/` based on the operating system.

### From the command line with Mill

```bash
./mill run
```

### From IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Go to **Settings > Appearance & Behavior > Path Variables**
3. Add a variable named `SWT_JAR` pointing to the appropriate SWT JAR in `lib/swt/` for your platform:
   - Windows: `swt-4.34-win32-win32-x86_64.jar`
   - Linux: `swt-4.34-gtk-linux-x86_64.jar`
   - macOS: `swt-4.34-cocoa-macosx-x86_64.jar`
4. Create a Run Configuration of type "Application":
   - **Main class:** `net.sourceforge.docfetcher.gui.Application`
   - **JDK:** Java 21 or newer
   - **VM options (platform-specific):**
     - Windows: `-Djava.library.path="lib/jnotify;lib/jintellitype"`
     - Linux: `-Djava.library.path="lib/jnotify:lib/jxgrabkey"`
     - macOS: `-Djava.library.path="lib/jnotify" -XstartOnFirstThread`
   - For UI translations, add the `dist/lang` folder to the classpath


## Lucene Index Format

Each index is stored as a subdirectory under the indexes parent directory. An index directory contains:

| File | Description |
|---|---|
| Lucene segment files | Standard Lucene 6.6.3 index files (`segments_*`, `*.si`, `*.cfs`, etc.) |
| `tree-index.ser` | Serialized `LuceneIndex` Java object containing index metadata (root path, email flag, indexing configuration). Optional but provides richer metadata. |
| `index-name.txt` | Human-readable display name for the index. First line is used as the name. Optional; falls back to the directory name. |

### Document fields stored in the index

| Field | Type | Description |
|---|---|---|
| `uid` | StringField (stored) | Unique identifier. Format: `file:///absolute/path` or `outlook:///path/to/entry` |
| `content` | TextField (not stored) | Full-text content for searching. Not retrievable after indexing. |
| `filename` | TextField (stored) | Original file name |
| `type` | StringField (stored) | File extension (lowercase) or email type |
| `title` | TextField (stored) | Document title or email subject |
| `author` | TextField (stored) | Document author or email sender |
| `size` | LegacyLongField (stored) | File size in bytes |
| `parser` | StringField (stored) | Name of the parser class used to extract text |
| `last_modified` | StringField (stored) | Last modification timestamp in milliseconds since epoch |
| `subject` | TextField (stored) | Email subject (email documents only) |
| `sender` | TextField (stored) | Email sender (email documents only) |
| `recipients` | TextField (stored) | Email recipients (email documents only) |
| `date` | StringField (stored) | Email date in milliseconds since epoch (email documents only) |

There are two document types, defined by the `DocumentType` enum:
- **FILE** -- UIDs prefixed with `file://`, used for filesystem documents
- **OUTLOOK** -- UIDs prefixed with `outlook://`, used for Outlook PST email entries


## Query Syntax

The search tools accept [Lucene query syntax](https://lucene.apache.org/core/6_6_3/queryparser/org/apache/lucene/queryparser/classic/package-summary.html):

| Syntax | Example | Description |
|---|---|---|
| Terms | `machine learning` | Matches documents containing both terms (implicit AND) |
| Phrases | `"exact phrase"` | Matches the exact phrase |
| Boolean | `java AND performance` | Boolean operators: AND, OR, NOT |
| Wildcards | `auto*`, `te?t` | `*` matches zero or more characters, `?` matches one |
| Fuzzy | `roam~` | Fuzzy matching based on edit distance |
| Field-specific | `title:report` | Search within a specific field |
| Grouping | `(java OR python) AND tutorial` | Group clauses with parentheses |

Leading wildcards are enabled (e.g., `*tion`). Smart quotes (curly quotes) are automatically normalized to straight quotes before parsing.


## Creating a Test Index

The `TestIndexCreator` utility creates a sample Lucene index with 5 test documents for development and testing:

```bash
./mill mcpServer.run -- TestIndexCreator /path/to/output-directory
```

This creates a subdirectory named `test-index_<timestamp>` containing a valid Lucene index with sample PDF, DOCX, TXT, and HTML documents. The test index can then be used with the MCP server:

```bash
./mill mcpServer.run -- --indexes /path/to/output-directory
```


## Project Structure

```
Searchful-MCP/
|-- build.mill                  Mill build definition (two modules)
|-- mill                        Mill launcher script
|-- src/                        DocFetcher core sources
|   +-- net/sourceforge/docfetcher/
|       |-- Main.java           Entry point (delegates to gui.Application)
|       |-- gui/                SWT desktop UI
|       |   |-- Application.java    Main window, shell, layout
|       |   |-- SearchBar.java      Search input with history
|       |   |-- ResultPanel.java    Search results table
|       |   |-- filter/             File type, size, location filters
|       |   |-- indexing/           Index creation/update dialogs
|       |   |-- pref/               Preferences dialogs
|       |   +-- preview/            Document preview pane
|       |-- model/              Core domain model
|       |   |-- LuceneIndex.java    Interface for index metadata
|       |   |-- IndexRegistry.java  Manages collection of indexes
|       |   |-- Fields.java         Lucene field definitions (enum)
|       |   |-- Document.java       Indexed document representation
|       |   |-- TreeIndex.java      Tree-structured index implementation
|       |   |-- Folder.java         Folder node in index tree
|       |   |-- FolderWatcher.java  Filesystem change monitoring
|       |   |-- index/              Indexing pipeline
|       |   |   |-- IndexingConfig.java     Per-index configuration
|       |   |   |-- IndexingQueue.java      Background indexing scheduler
|       |   |   |-- file/                   Filesystem document indexing
|       |   |   +-- outlook/                Outlook PST email indexing
|       |   |-- search/             Search engine
|       |   |   |-- Searcher.java                  Main search coordinator
|       |   |   |-- HighlightService.java           Result highlighting
|       |   |   +-- PhraseDetectingQueryParser.java Custom query parser
|       |   +-- parse/              Document text extraction
|       |       |-- ParseService.java    Parser registry and dispatch
|       |       |-- PdfParser.java       PDF text extraction (PDFBox)
|       |       |-- MSOfficeParser.java  Legacy Office (POI)
|       |       |-- MSOffice2007Parser.java  OOXML Office (POI)
|       |       |-- HtmlParser.java      HTML (Jericho)
|       |       +-- ...                  20+ format-specific parsers
|       |-- enums/              Configuration and message enums
|       |   |-- ProgramConf.java    Application-wide settings
|       |   |-- SettingsConf.java   User-modifiable settings
|       |   +-- Msg.java            Localized UI messages
|       +-- util/               Utilities and SWT helpers
|-- mcpServer/src/              MCP server sources
|   +-- net/sourceforge/docfetcher/mcp/
|       |-- McpServer.java          JSON-RPC message loop, MCP protocol
|       |-- IndexManager.java       Index discovery, loading, search
|       |-- ContentExtractor.java   Text extraction with highlighting
|       +-- TestIndexCreator.java   Test index generation utility
|-- lib/                        Bundled JAR dependencies
|   |-- lucene/                 Apache Lucene 6.6.3
|   |-- swt/                    SWT 4.34 (platform-specific JARs)
|   |-- pdfbox/                 Apache PDFBox
|   |-- poi/                    Apache POI (Office parsing)
|   |-- jackson/                Jackson JSON (used by MCP server)
|   |-- tika/                   Apache Tika
|   +-- ...                     30+ library directories
|-- dist/                       Distribution resources
|   |-- program-conf.txt        Default application configuration
|   |-- lang/                   UI translation property files (18 languages)
|   |-- launchers/              Platform launcher scripts and executables
|   +-- help/                   User manual (HTML, multi-language)
|-- subprojects/                Rust subproject (macOS native launcher)
|-- src-daemon/                 Background daemon sources
|-- build.py                    Release build script (Python)
+-- current-version.txt         Version number (currently 1.1.27)
```


## Configuration

Application settings are defined in `dist/program-conf.txt` and loaded at startup by the `ProgramConf` enum. Key settings include:

| Setting | Default | Description |
|---|---|---|
| `AppName` | DocFetcher | Window title and branding |
| `MaxResultsTotal` | 10000 | Maximum search results per query |
| `HtmlExtensions` | html;htm;xhtml;shtml;shtm | Recognized HTML file extensions |
| `IndexExcelFormulas` | true | Index Excel formulas vs. computed values |
| `SkipTarArchives` | false | Skip tar archive contents during indexing |
| `IgnoreJunctionsAndSymlinks` | true | Ignore NTFS junctions and symlinks |
| `PythonApiEnabled` | false | Enable Py4J scripting API (TCP server, security risk) |
| `TextPreviewEnabled` | true | Show text preview pane |

The full list of settings with documentation is in `dist/program-conf.txt`.


## Internationalization

The desktop application supports 18 languages. Translation files are Java properties files in `dist/lang/` named `Resource_<locale>.properties`. The `Msg` enum in `src/net/sourceforge/docfetcher/enums/` defines all localizable message keys. User-facing help manuals are stored per-language in `dist/help/`.


## Building Release Artifacts

Release builds are handled by `build.py` (Python 3.12+), which produces platform-specific distributables with bundled JREs. This is separate from the Mill build, which is only for incremental compilation during development.

Additional requirements for release builds:
- Python 3.12 or newer
- Rust (for the macOS native launcher subproject)
- On Linux: MUSL (`rustup target add x86_64-unknown-linux-musl`) and optionally osxcross for cross-compiling to macOS
- JRE paths configured in `build-jre.txt`

Optional code signing:
- Windows: `code-signing-windows.txt` with SHA1 certificate thumbprint; requires Windows SDK and `signtool.exe`
- macOS: `code-signing-macos.txt` with certificate, password, and App Store Connect API credentials; `dmg-notarize.py` for DMG notarization

See `readme.txt` for detailed setup instructions.


## License

The original DocFetcher code is licensed under the [Eclipse Public License v1.0](http://www.eclipse.org/legal/epl-v10.html). See the license headers in source files and `dist/epl-v10.html` for details.
