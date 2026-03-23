# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **Searchful-MCP**, a fork of DocFetcher (a desktop document search application) extended with an MCP (Model Context Protocol) server. The project has two main components:

1. **DocFetcher core** — A Java/SWT desktop application that indexes and searches documents using Apache Lucene. It supports PDF, Office docs, email (Outlook PST), HTML, RTF, OpenOffice, ebooks, and plain text.
2. **MCP server** — A headless JSON-RPC 2.0 server (over stdio) that exposes DocFetcher's Lucene indexes to LLM tools via MCP. It provides three tools: `search`, `get_document_content`, and `list_indexes`.

## Build System

This is a Java/SWT project using [Mill](https://mill-build.org/) for compilation. JARs are managed as unmanaged dependencies in the `lib/` directory (not Maven/Gradle).

- **Mill build file:** `build.mill`
- **Java version:** 21
- **Incremental compile:** `./mill compile`
- **Clean + compile:** `./mill clean && ./mill compile`
- **Compile MCP server module:** `./mill mcpServer.compile`

**Important:** Build output can be very long. Always redirect to a file and inspect with grep:
```bash
./mill clean && ./mill compile &> /tmp/build-output.txt
grep "\[error\]" /tmp/build-output.txt
grep "\[warn\]" /tmp/build-output.txt
```

Mill compiles incrementally — to see all warnings, you must clean first.

The `build.py` script produces release artifacts (bundled JREs, installers). Mill is only for fast incremental compilation during development.

## Module Structure

The Mill build defines two modules in `build.mill`:

- **Root module (`package`)** — The DocFetcher core. Entry point: `net.sourceforge.docfetcher.Main` → `gui.Application`.
- **`mcpServer`** — The MCP server. Depends on the root module. Entry point: `net.sourceforge.docfetcher.mcp.McpServer`. Launched with `--indexes <path>`.

## Source Layout

- `src/` — DocFetcher core sources under `net.sourceforge.docfetcher`
  - `gui/` — SWT UI (Application, SearchBar, ResultPanel, filters, preview, indexing dialogs)
  - `model/` — Core domain: `Document`, `LuceneIndex`, `IndexRegistry`, `Folder`/`TreeNode` hierarchy
  - `model/index/` — Indexing pipeline: `IndexingConfig`, `IndexingQueue`, file/Outlook indexers
  - `model/search/` — Search: `Searcher`, `HighlightService`, `PhraseDetectingQueryParser`
  - `model/parse/` — File parsers: PDF, Office, HTML, RTF, ebook, etc. via `ParseService`
  - `enums/` — Config classes: `ProgramConf`, `SettingsConf`, `SystemConf`, message enums
  - `util/` — Utilities, GUI helpers, concurrency
- `mcpServer/src/` — MCP server sources under `net.sourceforge.docfetcher.mcp`
  - `McpServer.java` — JSON-RPC message loop and MCP protocol handling
  - `IndexManager.java` — Discovers/loads Lucene indexes, executes searches
  - `ContentExtractor.java` — Extracts and highlights document text for `get_document_content`
- `lib/` — Bundled JARs (Lucene, SWT, Tika, POI, PDFBox, Jackson, etc.). Platform-specific SWT JARs are filtered by the build.
- `dist/` — Distribution resources (launcher scripts, lang files)
- `subprojects/` — Rust subproject (macOS launcher)
- `src-daemon/` — Daemon process sources

## Key Architectural Details

- **Lucene version:** Uses a legacy/custom Lucene build (in `lib/lucene/`), not a standard Maven artifact. Includes `LegacyNumericRangeQuery` and custom `DecoratedMultiReader`.
- **SWT dependency:** Platform-specific. The build auto-detects the OS and selects the correct SWT JAR from `lib/swt/`. For IntelliJ, a `SWT_JAR` path variable must be configured (see `readme.txt`).
- **Index format:** Each index is a directory containing Lucene index files plus `tree-index.ser` (serialized `LuceneIndex` metadata) and optionally `index-name.txt`.
- **Document UIDs:** Stored as `file:///path/to/file` or `outlook:///path/to/pst/entry`. The MCP server's `IndexManager.extractPathFromUid()` strips the scheme prefix.
- **MCP server is headless** — it does not use SWT or any GUI code. It reads indexes directly via Lucene APIs and reuses core model/parse/search classes.
