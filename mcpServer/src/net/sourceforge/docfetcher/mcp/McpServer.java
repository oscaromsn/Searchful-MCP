package net.sourceforge.docfetcher.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MCP (Model Context Protocol) server for DocFetcher's Lucene indexes.
 * Communicates via JSON-RPC 2.0 over stdio.
 */
public class McpServer {

	private static final String PROTOCOL_VERSION = "2024-11-05";
	private static final String SERVER_NAME = "docfetcher-mcp";
	private static final String SERVER_VERSION = "1.0.0";

	private static final ObjectMapper mapper = new ObjectMapper();
	private static IndexManager indexManager;
	private static ContentExtractor contentExtractor;
	private static SnippetGenerator snippetGenerator;

	public static void main(String[] args) throws Exception {
		String indexesPath = null;
		for (int i = 0; i < args.length; i++) {
			if ("--indexes".equals(args[i]) && i + 1 < args.length) {
				indexesPath = args[++i];
			}
		}

		if (indexesPath == null) {
			System.err.println("Usage: docfetcher-mcp --indexes <path-to-indexes-directory>");
			System.err.println();
			System.err.println("  --indexes  Path to the DocFetcher indexes directory");
			System.err.println("             (typically ~/.docfetcher/indexes or the 'indexes' folder");
			System.err.println("             inside the portable DocFetcher installation)");
			System.exit(1);
		}

		File indexesDir = new File(indexesPath);
		if (!indexesDir.isDirectory()) {
			System.err.println("Error: Not a directory: " + indexesPath);
			System.exit(1);
		}

		indexManager = new IndexManager(indexesDir);
		int count = indexManager.loadIndexes();
		contentExtractor = new ContentExtractor(indexManager);
		snippetGenerator = new SnippetGenerator(contentExtractor, indexManager.getAnalyzer());

		System.err.println(SERVER_NAME + " v" + SERVER_VERSION + " started.");
		System.err.println("Loaded " + count + " index(es) from " + indexesDir.getAbsolutePath());

		BufferedReader reader = new BufferedReader(
			new InputStreamReader(System.in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty()) continue;

			try {
				JsonNode request = mapper.readTree(line);
				JsonNode response = handleMessage(request);
				if (response != null) {
					System.out.println(mapper.writeValueAsString(response));
					System.out.flush();
				}
			} catch (Exception e) {
				System.err.println("Error processing message: " + e.getMessage());
			}
		}
	}

	private static JsonNode handleMessage(JsonNode request) {
		String method = request.path("method").asText("");
		JsonNode id = request.get("id");
		JsonNode params = request.has("params") ? request.get("params") : mapper.createObjectNode();

		// Notifications have no id and don't get responses
		if (id == null || id.isNull()) {
			return null;
		}

		try {
			JsonNode result = switch (method) {
				case "initialize" -> handleInitialize(params);
				case "ping" -> mapper.createObjectNode();
				case "tools/list" -> handleToolsList(params);
				case "tools/call" -> handleToolsCall(params);
				default -> throw new McpException(-32601, "Method not found: " + method);
			};
			return successResponse(id, result);
		} catch (McpException e) {
			return errorResponse(id, e.code, e.getMessage());
		} catch (Exception e) {
			return errorResponse(id, -32603, e.getMessage());
		}
	}

	// --- MCP Handlers ---

	private static JsonNode handleInitialize(JsonNode params) {
		ObjectNode result = mapper.createObjectNode();
		result.put("protocolVersion", PROTOCOL_VERSION);

		ObjectNode capabilities = mapper.createObjectNode();
		capabilities.putObject("tools");
		result.set("capabilities", capabilities);

		ObjectNode serverInfo = mapper.createObjectNode();
		serverInfo.put("name", SERVER_NAME);
		serverInfo.put("version", SERVER_VERSION);
		result.set("serverInfo", serverInfo);

		return result;
	}

	private static JsonNode handleToolsList(JsonNode params) {
		ObjectNode result = mapper.createObjectNode();
		ArrayNode tools = mapper.createArrayNode();

		tools.add(buildSearchToolDef());
		tools.add(buildGetDocumentContentToolDef());
		tools.add(buildListIndexesToolDef());
		tools.add(buildListDirectoriesToolDef());

		result.set("tools", tools);
		return result;
	}

	private static JsonNode handleToolsCall(JsonNode params) {
		String toolName = params.path("name").asText("");
		JsonNode arguments = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();

		try {
			String resultText = switch (toolName) {
				case "search" -> executeSearch(arguments);
				case "get_document_content" -> executeGetDocumentContent(arguments);
				case "list_indexes" -> executeListIndexes(arguments);
				case "list_directories" -> executeListDirectories(arguments);
				default -> throw new McpException(-32602, "Unknown tool: " + toolName);
			};

			ObjectNode result = mapper.createObjectNode();
			ArrayNode content = mapper.createArrayNode();
			ObjectNode textContent = mapper.createObjectNode();
			textContent.put("type", "text");
			textContent.put("text", resultText);
			content.add(textContent);
			result.set("content", content);
			return result;
		} catch (McpException e) {
			throw e;
		} catch (Exception e) {
			ObjectNode result = mapper.createObjectNode();
			ArrayNode content = mapper.createArrayNode();
			ObjectNode textContent = mapper.createObjectNode();
			textContent.put("type", "text");
			textContent.put("text", "Error: " + e.getMessage());
			content.add(textContent);
			result.set("content", content);
			result.put("isError", true);
			return result;
		}
	}

	// --- Tool Execution ---

	private static String executeSearch(JsonNode args) throws Exception {
		String query = args.path("query").asText(null);
		if (query == null || query.isBlank()) {
			throw new McpException(-32602, "Missing required parameter: query");
		}

		int page = args.path("page").asInt(1);
		page = Math.min(Math.max(page, 1), 100);
		int pageSize = 10;

		Long minSizeKb = args.has("min_size_kb") ? args.get("min_size_kb").asLong() : null;
		Long maxSizeKb = args.has("max_size_kb") ? args.get("max_size_kb").asLong() : null;

		List<String> fileTypes = null;
		if (args.has("file_types") && args.get("file_types").isArray()) {
			fileTypes = new java.util.ArrayList<>();
			for (JsonNode ft : args.get("file_types")) {
				fileTypes.add(ft.asText());
			}
		}

		List<String> scope = null;
		if (args.has("scope") && args.get("scope").isArray()) {
			scope = new java.util.ArrayList<>();
			for (JsonNode s : args.get("scope")) {
				scope.add(s.asText());
			}
		}

		IndexManager.PaginatedSearchResult psr = indexManager.search(
			query, page, pageSize, fileTypes, minSizeKb, maxSizeKb, scope);

		// Enrich results with excerpts and page numbers
		snippetGenerator.enrichResults(psr.results, psr.parsedQuery);

		// Determine scope prefix for relative paths
		String scopePrefix = null;
		if (scope != null && scope.size() == 1) {
			scopePrefix = scope.get(0);
			if (!scopePrefix.endsWith("/") && !scopePrefix.endsWith("\\")) {
				scopePrefix += "/";
			}
		}

		// Format as compact text
		StringBuilder sb = new StringBuilder();
		sb.append("Found ~").append(psr.totalHits).append(" results");
		sb.append(" (page ").append(psr.page).append("/").append(psr.totalPages).append(")\n");
		if (scopePrefix != null) {
			sb.append("Scope: ").append(scope.get(0)).append("\n");
		}

		int baseRank = (psr.page - 1) * psr.pageSize;
		for (int i = 0; i < psr.results.size(); i++) {
			IndexManager.SearchResult r = psr.results.get(i);
			sb.append("\n[").append(baseRank + i + 1).append("] ");
			sb.append(r.filename);
			sb.append(" (score:").append(r.score);
			sb.append(", ").append(r.sizeInKb).append("KB");
			if (r.pageCount != null) sb.append(", ").append(r.pageCount).append("p");
			sb.append(")\n");

			// Show relative path when single scope is active
			String displayPath = r.path;
			if (scopePrefix != null && displayPath.startsWith(scopePrefix)) {
				displayPath = displayPath.substring(scopePrefix.length());
			}
			sb.append("    ").append(displayPath).append("\n");

			if (r.title != null && !r.title.isEmpty()
					&& !r.title.equals(r.filename)
					&& !r.title.equals(stripExtension(r.filename))) {
				sb.append("    title: ").append(r.title).append("\n");
			}
			if (r.excerpt != null) {
				sb.append("    ");
				if (r.pageNumber != null) {
					sb.append("[p.").append(r.pageNumber).append("] ");
				}
				sb.append(r.excerpt).append("\n");
			}
		}

		if (psr.page < psr.totalPages) {
			sb.append("\nPage ").append(psr.page).append("/").append(psr.totalPages);
			sb.append(". Use search with page:").append(psr.page + 1).append(" to see more results.");
		}

		return sb.toString();
	}

	private static String stripExtension(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot >= 0 ? filename.substring(0, dot) : filename;
	}

	private static String executeGetDocumentContent(JsonNode args) throws Exception {
		String path = args.path("path").asText(null);
		if (path == null || path.isBlank()) {
			throw new McpException(-32602, "Missing required parameter: path");
		}

		// Resolve relative paths using scope
		String scopeDir = args.path("scope").asText(null);
		if (scopeDir != null && !path.contains(":") && !path.startsWith("/")) {
			String prefix = scopeDir.endsWith("/") || scopeDir.endsWith("\\") ? scopeDir : scopeDir + "/";
			path = prefix + path;
		}

		String query = args.path("query").asText(null);
		int maxChars = args.path("max_chars").asInt(5000);
		maxChars = Math.min(Math.max(maxChars, 100), 100000);

		// Check for page-range parameters
		boolean hasStartPage = args.has("start_page") && !args.get("start_page").isNull();
		boolean hasEndPage = args.has("end_page") && !args.get("end_page").isNull();

		if (hasStartPage || hasEndPage) {
			int startPage = hasStartPage ? args.get("start_page").asInt(1) : 1;
			int endPage = hasEndPage ? args.get("end_page").asInt(startPage + 4) : startPage + 4;

			ContentExtractor.PagedContent pc = contentExtractor.extractPages(
				path, startPage, endPage, query, maxChars);

			StringBuilder sb = new StringBuilder();
			String filename = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
			sb.append("Document: ").append(filename);
			if (pc.totalPages > 0) {
				sb.append(" (").append(pc.totalPages).append(" pages, showing pages ");
				sb.append(pc.startPage).append("-").append(pc.endPage).append(")\n\n");
			} else {
				sb.append("\n\n");
			}
			sb.append(pc.text);

			if (pc.totalPages > 0 && pc.endPage < pc.totalPages) {
				sb.append("\nUse get_document_content with start_page:")
					.append(pc.endPage + 1).append(" to continue reading.");
			}

			return sb.toString();
		}

		// No page params — check for search-within-document mode (PDF + query)
		if (query != null && !query.isBlank()) {
			String ext = "";
			int dotIdx = path.lastIndexOf('.');
			if (dotIdx >= 0) ext = path.substring(dotIdx + 1).toLowerCase();

			if ("pdf".equals(ext)) {
				ContentExtractor.PageSearchResult psr =
					contentExtractor.searchPages(path, query, maxChars, 10);

				StringBuilder sb = new StringBuilder();
				String filename = path.substring(
					Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
				sb.append("Document: ").append(filename);
				sb.append(" (").append(psr.totalPages).append(" pages, ");
				sb.append(psr.matchingPages).append(" with matches)\n\n");
				sb.append(psr.text);
				sb.append("\n\nUse get_document_content with start_page/end_page to read full page context.");
				return sb.toString();
			}
		}

		// Fallback: existing behavior (full text with optional highlighting)
		return contentExtractor.extract(path, query, maxChars);
	}

	private static String executeListIndexes(JsonNode args) throws Exception {
		List<IndexManager.IndexInfo> indexes = indexManager.listIndexes();

		ArrayNode jsonResults = mapper.createArrayNode();
		for (IndexManager.IndexInfo info : indexes) {
			ObjectNode obj = mapper.createObjectNode();
			obj.put("name", info.name);
			obj.put("root_path", info.rootPath);
			obj.put("document_count", info.documentCount);
			obj.put("is_email_index", info.isEmailIndex);
			jsonResults.add(obj);
		}
		return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResults);
	}

	private static String executeListDirectories(JsonNode args) throws Exception {
		int depth = args.path("depth").asInt(2);
		depth = Math.min(Math.max(depth, 1), 10);
		return indexManager.listDirectories(depth);
	}

	// --- Tool Definitions ---

	private static ObjectNode buildSearchToolDef() {
		ObjectNode tool = mapper.createObjectNode();
		tool.put("name", "search");
		tool.put("description",
			"Search indexed documents with paginated results. Returns 10 results per page, " +
			"each with a relevance-highlighted excerpt. For PDFs, shows which page the match " +
			"appears on and total page count. Supports Lucene query syntax: terms, phrases " +
			"(\"exact phrase\"), boolean (AND, OR, NOT), wildcards (*, ?), fuzzy (term~), " +
			"field-specific (title:word). Use get_document_content to read full document text.");

		ObjectNode schema = mapper.createObjectNode();
		schema.put("type", "object");

		ObjectNode props = mapper.createObjectNode();

		ObjectNode queryProp = mapper.createObjectNode();
		queryProp.put("type", "string");
		queryProp.put("description", "Lucene query string");
		props.set("query", queryProp);

		ObjectNode pageProp = mapper.createObjectNode();
		pageProp.put("type", "integer");
		pageProp.put("description", "Page number (1-indexed, default 1). 10 results per page.");
		props.set("page", pageProp);

		ObjectNode fileTypesProp = mapper.createObjectNode();
		fileTypesProp.put("type", "array");
		ObjectNode ftItems = mapper.createObjectNode();
		ftItems.put("type", "string");
		fileTypesProp.set("items", ftItems);
		fileTypesProp.put("description", "Filter by file extension, e.g. [\"pdf\", \"docx\"]");
		props.set("file_types", fileTypesProp);

		ObjectNode scopeProp = mapper.createObjectNode();
		scopeProp.put("type", "array");
		ObjectNode scopeItems = mapper.createObjectNode();
		scopeItems.put("type", "string");
		scopeProp.set("items", scopeItems);
		scopeProp.put("description",
			"Filter by directory paths. Only documents under these directories are returned. " +
			"Use paths as shown in search results.");
		props.set("scope", scopeProp);

		ObjectNode minSizeProp = mapper.createObjectNode();
		minSizeProp.put("type", "integer");
		minSizeProp.put("description", "Minimum file size in KB");
		props.set("min_size_kb", minSizeProp);

		ObjectNode maxSizeProp = mapper.createObjectNode();
		maxSizeProp.put("type", "integer");
		maxSizeProp.put("description", "Maximum file size in KB");
		props.set("max_size_kb", maxSizeProp);

		schema.set("properties", props);

		ArrayNode required = mapper.createArrayNode();
		required.add("query");
		schema.set("required", required);

		tool.set("inputSchema", schema);
		return tool;
	}

	private static ObjectNode buildGetDocumentContentToolDef() {
		ObjectNode tool = mapper.createObjectNode();
		tool.put("name", "get_document_content");
		tool.put("description",
			"Retrieve the text content of a document by file path. " +
			"For PDFs, supports page-range reading (start_page/end_page). " +
			"Optionally provide a query to highlight matches with >> and << markers.");

		ObjectNode schema = mapper.createObjectNode();
		schema.put("type", "object");

		ObjectNode props = mapper.createObjectNode();

		ObjectNode pathProp = mapper.createObjectNode();
		pathProp.put("type", "string");
		pathProp.put("description", "File path (from search results)");
		props.set("path", pathProp);

		ObjectNode queryProp = mapper.createObjectNode();
		queryProp.put("type", "string");
		queryProp.put("description", "Query for highlighting matches in the content");
		props.set("query", queryProp);

		ObjectNode contentScopeProp = mapper.createObjectNode();
		contentScopeProp.put("type", "string");
		contentScopeProp.put("description", "Directory scope for resolving relative paths (from search results)");
		props.set("scope", contentScopeProp);

		ObjectNode startPageProp = mapper.createObjectNode();
		startPageProp.put("type", "integer");
		startPageProp.put("description", "Start page (1-indexed, PDF only)");
		props.set("start_page", startPageProp);

		ObjectNode endPageProp = mapper.createObjectNode();
		endPageProp.put("type", "integer");
		endPageProp.put("description", "End page (1-indexed, PDF only, default: start_page + 4)");
		props.set("end_page", endPageProp);

		ObjectNode maxCharsProp = mapper.createObjectNode();
		maxCharsProp.put("type", "integer");
		maxCharsProp.put("description", "Maximum characters to return (default 5000)");
		props.set("max_chars", maxCharsProp);

		schema.set("properties", props);

		ArrayNode required = mapper.createArrayNode();
		required.add("path");
		schema.set("required", required);

		tool.set("inputSchema", schema);
		return tool;
	}

	private static ObjectNode buildListIndexesToolDef() {
		ObjectNode tool = mapper.createObjectNode();
		tool.put("name", "list_indexes");
		tool.put("description",
			"List all available DocFetcher indexes with their names, " +
			"root paths, document counts, and whether they are email indexes.");

		ObjectNode schema = mapper.createObjectNode();
		schema.put("type", "object");
		schema.set("properties", mapper.createObjectNode());

		tool.set("inputSchema", schema);
		return tool;
	}

	private static ObjectNode buildListDirectoriesToolDef() {
		ObjectNode tool = mapper.createObjectNode();
		tool.put("name", "list_directories");
		tool.put("description",
			"List the directory tree of indexed documents. Use this to discover " +
			"available directory paths for the scope parameter in search. " +
			"Returns an indented tree of directories.");

		ObjectNode schema = mapper.createObjectNode();
		schema.put("type", "object");

		ObjectNode props = mapper.createObjectNode();

		ObjectNode depthProp = mapper.createObjectNode();
		depthProp.put("type", "integer");
		depthProp.put("description", "Tree depth to show (default 2, max 10)");
		props.set("depth", depthProp);

		schema.set("properties", props);
		tool.set("inputSchema", schema);
		return tool;
	}

	// --- JSON-RPC Helpers ---

	private static ObjectNode successResponse(JsonNode id, JsonNode result) {
		ObjectNode response = mapper.createObjectNode();
		response.put("jsonrpc", "2.0");
		response.set("id", id);
		response.set("result", result);
		return response;
	}

	private static ObjectNode errorResponse(JsonNode id, int code, String message) {
		ObjectNode response = mapper.createObjectNode();
		response.put("jsonrpc", "2.0");
		response.set("id", id);
		ObjectNode error = mapper.createObjectNode();
		error.put("code", code);
		error.put("message", message != null ? message : "Unknown error");
		response.set("error", error);
		return response;
	}

	static class McpException extends RuntimeException {
		final int code;
		McpException(int code, String message) {
			super(message);
			this.code = code;
		}
	}
}
