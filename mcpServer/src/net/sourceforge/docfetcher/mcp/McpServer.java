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

		int maxResults = args.path("max_results").asInt(20);
		maxResults = Math.min(Math.max(maxResults, 1), 100);

		Long minSizeKb = args.has("min_size_kb") ? args.get("min_size_kb").asLong() : null;
		Long maxSizeKb = args.has("max_size_kb") ? args.get("max_size_kb").asLong() : null;

		List<String> fileTypes = null;
		if (args.has("file_types") && args.get("file_types").isArray()) {
			fileTypes = new java.util.ArrayList<>();
			for (JsonNode ft : args.get("file_types")) {
				fileTypes.add(ft.asText());
			}
		}

		List<IndexManager.SearchResult> results = indexManager.search(
			query, maxResults, fileTypes, minSizeKb, maxSizeKb);

		ArrayNode jsonResults = mapper.createArrayNode();
		for (IndexManager.SearchResult r : results) {
			ObjectNode obj = mapper.createObjectNode();
			obj.put("uid", r.uid);
			obj.put("filename", r.filename);
			obj.put("path", r.path);
			if (r.title != null && !r.title.isEmpty()) obj.put("title", r.title);
			if (r.authors != null && !r.authors.isEmpty()) obj.put("authors", r.authors);
			obj.put("type", r.type);
			obj.put("size_kb", r.sizeInKb);
			obj.put("score", r.score);
			if (r.lastModified != null) obj.put("last_modified", r.lastModified);
			obj.put("is_email", r.isEmail);
			jsonResults.add(obj);
		}
		return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResults);
	}

	private static String executeGetDocumentContent(JsonNode args) throws Exception {
		String path = args.path("path").asText(null);
		if (path == null || path.isBlank()) {
			throw new McpException(-32602, "Missing required parameter: path");
		}

		String query = args.path("query").asText(null);
		int maxChars = args.path("max_chars").asInt(5000);
		maxChars = Math.min(Math.max(maxChars, 100), 100000);

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

	// --- Tool Definitions ---

	private static ObjectNode buildSearchToolDef() {
		ObjectNode tool = mapper.createObjectNode();
		tool.put("name", "search");
		tool.put("description",
			"Search DocFetcher indexed documents. Supports Lucene query syntax: " +
			"terms, phrases (\"exact phrase\"), boolean (AND, OR, NOT), " +
			"wildcards (*, ?), fuzzy (term~), field-specific (title:word). " +
			"Returns document metadata (path, title, authors, type, size, score). " +
			"Content is NOT returned — use get_document_content for that.");

		ObjectNode schema = mapper.createObjectNode();
		schema.put("type", "object");

		ObjectNode props = mapper.createObjectNode();

		ObjectNode queryProp = mapper.createObjectNode();
		queryProp.put("type", "string");
		queryProp.put("description", "Lucene query string");
		props.set("query", queryProp);

		ObjectNode fileTypesProp = mapper.createObjectNode();
		fileTypesProp.put("type", "array");
		ObjectNode ftItems = mapper.createObjectNode();
		ftItems.put("type", "string");
		fileTypesProp.set("items", ftItems);
		fileTypesProp.put("description", "Filter by file extension, e.g. [\"pdf\", \"docx\"]");
		props.set("file_types", fileTypesProp);

		ObjectNode maxResultsProp = mapper.createObjectNode();
		maxResultsProp.put("type", "integer");
		maxResultsProp.put("description", "Maximum results to return (default 20, max 100)");
		props.set("max_results", maxResultsProp);

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
			"The file must be accessible on disk (the original file is re-parsed). " +
			"Optionally provide a query to get highlighted snippets around matches.");

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
