package net.sourceforge.docfetcher.mcp;

import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.index.DecoratedMultiReader;
import net.sourceforge.docfetcher.model.search.PhraseDetectingQueryParser;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LegacyNumericRangeQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Manages DocFetcher Lucene indexes: discovery, loading, and searching.
 */
public class IndexManager {

	private final File indexParentDir;
	private final Analyzer analyzer;
	private final List<LoadedIndex> loadedIndexes = new ArrayList<>();
	private IndexSearcher searcher;
	private DecoratedMultiReader multiReader;

	public IndexManager(File indexParentDir) {
		this.indexParentDir = indexParentDir;
		this.analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET);
		BooleanQuery.setMaxClauseCount(Integer.MAX_VALUE);
	}

	/**
	 * Discovers and loads all indexes in the index parent directory.
	 * Returns the number of successfully loaded indexes.
	 */
	public int loadIndexes() {
		File[] dirs = indexParentDir.listFiles();
		if (dirs == null) return 0;

		List<IndexReader> readers = new ArrayList<>();

		for (File dir : dirs) {
			if (!dir.isDirectory()) continue;

			// Check for Lucene index files (any segments file indicates a Lucene index)
			File serFile = new File(dir, "tree-index.ser");
			boolean hasSerFile = serFile.exists();

			// Try to open the directory as a Lucene index
			DirectoryReader reader;
			try {
				reader = DirectoryReader.open(FSDirectory.open(dir.toPath()));
			} catch (Exception e) {
				if (hasSerFile) {
					System.err.println("Warning: Found tree-index.ser but cannot open Lucene index in "
						+ dir.getName() + ": " + e.getMessage());
				}
				continue;
			}

			// Try to deserialize tree-index.ser for metadata
			LuceneIndex luceneIndex = null;
			if (hasSerFile) {
				try {
					luceneIndex = deserializeIndex(serFile);
				} catch (Exception e) {
					System.err.println("Warning: Failed to deserialize " + serFile.getName()
						+ " in " + dir.getName() + ": " + e.getMessage());
				}
			}

			// Read display name
			String name = readIndexName(dir);
			if (name == null) name = dir.getName();

			// Determine root path and email status from documents
			String rootPath = null;
			boolean isEmail = false;
			if (luceneIndex != null) {
				try {
					File rootFile = luceneIndex.getCanonicalRootFile();
					if (rootFile != null) rootPath = rootFile.getAbsolutePath();
				} catch (Exception e) { /* ignore */ }
				isEmail = luceneIndex.isEmailIndex();
			} else if (reader.numDocs() > 0) {
				// Infer from first document's UID
				try {
					Document firstDoc = reader.document(0);
					String uid = firstDoc.get(Fields.UID.key());
					if (uid != null) {
						isEmail = uid.startsWith("outlook://");
						// Extract root path from UID (common prefix)
						rootPath = extractPathFromUid(uid);
						if (rootPath != null) {
							File f = new File(rootPath);
							rootPath = f.getParent();
						}
					}
				} catch (Exception e) { /* ignore */ }
			}
			if (rootPath == null) rootPath = dir.getAbsolutePath();

			LoadedIndex loaded = new LoadedIndex();
			loaded.directory = dir;
			loaded.name = name;
			loaded.rootPath = rootPath;
			loaded.isEmail = isEmail;
			loaded.reader = reader;
			loaded.luceneIndex = luceneIndex;
			loaded.documentCount = reader.numDocs();
			loadedIndexes.add(loaded);
			readers.add(reader);

			System.err.println("  Loaded index: " + name + " (" + reader.numDocs() + " docs"
				+ (isEmail ? ", email" : "") + ")");
		}

		if (!readers.isEmpty()) {
			try {
				multiReader = new DecoratedMultiReader(
					readers.toArray(new IndexReader[0]));
				searcher = new IndexSearcher(multiReader);
			} catch (IOException e) {
				System.err.println("Error creating multi-reader: " + e.getMessage());
			}
		}

		return loadedIndexes.size();
	}

	/**
	 * Search across all loaded indexes with pagination.
	 *
	 * @param queryStr Lucene query string
	 * @param page 1-indexed page number
	 * @param pageSize results per page
	 * @param fileTypes optional file extension filter
	 * @param minSizeKb optional minimum size filter
	 * @param maxSizeKb optional maximum size filter
	 */
	public PaginatedSearchResult search(
			String queryStr, int page, int pageSize,
			List<String> fileTypes, Long minSizeKb, Long maxSizeKb,
			List<String> scope) throws Exception {
		PaginatedSearchResult psr = new PaginatedSearchResult();
		psr.page = page;
		psr.pageSize = pageSize;

		if (searcher == null) {
			psr.results = List.of();
			psr.totalHits = 0;
			psr.totalPages = 0;
			return psr;
		}

		// Normalize smart quotes
		queryStr = queryStr.replace('\u201C', '"').replace('\u201D', '"');
		queryStr = queryStr.replace('\u2018', '\'').replace('\u2019', '\'');

		// Parse query
		PhraseDetectingQueryParser parser = new PhraseDetectingQueryParser(
			Fields.CONTENT.key(), analyzer);
		parser.setAllowLeadingWildcard(true);
		parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
		Query query = parser.parse(queryStr);
		psr.parsedQuery = query;

		// Build composite query with filters
		BooleanQuery.Builder bqBuilder = new BooleanQuery.Builder();
		bqBuilder.add(query, BooleanClause.Occur.MUST);

		// Size filter (stored in bytes, input in KB)
		if (minSizeKb != null || maxSizeKb != null) {
			Long minBytes = minSizeKb != null ? minSizeKb * 1024 : null;
			Long maxBytes = maxSizeKb != null ? maxSizeKb * 1024 : null;
			@SuppressWarnings("deprecation")
			Query sizeQuery = LegacyNumericRangeQuery.newLongRange(
				Fields.SIZE.key(), minBytes, maxBytes, true, true);
			bqBuilder.add(sizeQuery, BooleanClause.Occur.FILTER);
		}

		// File type filter (match against TYPE field which stores the extension)
		if (fileTypes != null && !fileTypes.isEmpty()) {
			BooleanQuery.Builder typeBuilder = new BooleanQuery.Builder();
			for (String type : fileTypes) {
				typeBuilder.add(
					new TermQuery(new Term(Fields.TYPE.key(), type.toLowerCase())),
					BooleanClause.Occur.SHOULD);
			}
			bqBuilder.add(typeBuilder.build(), BooleanClause.Occur.FILTER);
		}

		// Scope filter (restrict to specific directories by UID prefix)
		if (scope != null && !scope.isEmpty()) {
			BooleanQuery.Builder scopeBuilder = new BooleanQuery.Builder();
			for (String dir : scope) {
				String prefix = dir.endsWith("/") || dir.endsWith("\\") ? dir : dir + "/";
				scopeBuilder.add(
					new PrefixQuery(new Term(Fields.UID.key(), "file://" + prefix)),
					BooleanClause.Occur.SHOULD);
				scopeBuilder.add(
					new PrefixQuery(new Term(Fields.UID.key(), "outlook://" + prefix)),
					BooleanClause.Occur.SHOULD);
			}
			bqBuilder.add(scopeBuilder.build(), BooleanClause.Occur.FILTER);
		}

		BooleanQuery finalQuery = bqBuilder.build();

		// Fetch extra results to account for deduplication
		int topN = page * pageSize * 3;
		TopDocs topDocs = searcher.search(finalQuery, topN);

		psr.totalHits = (int) topDocs.totalHits;

		// Build results with deduplication (skip docs with same filename+size)
		Set<String> seen = new LinkedHashSet<>();
		List<SearchResult> results = new ArrayList<>();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		float maxScore = topDocs.getMaxScore();
		int uniqueCount = 0;
		int targetStart = (page - 1) * pageSize;

		for (int i = 0; i < topDocs.scoreDocs.length && results.size() < pageSize; i++) {
			ScoreDoc scoreDoc = topDocs.scoreDocs[i];
			Document doc = searcher.doc(scoreDoc.doc);

			String filename = doc.get(Fields.FILENAME.key());
			String sizeStr = doc.get(Fields.SIZE.key());
			String dedupKey = (filename != null ? filename : "") + "|" + (sizeStr != null ? sizeStr : "");

			if (!seen.add(dedupKey)) continue; // skip duplicate
			uniqueCount++;

			if (uniqueCount <= targetStart) continue; // skip to requested page

			SearchResult result = new SearchResult();
			result.uid = doc.get(Fields.UID.key());
			result.filename = filename != null ? filename : "";
			result.type = doc.get(Fields.TYPE.key());
			result.title = doc.get(Fields.TITLE.key());
			result.authors = doc.get(Fields.AUTHOR.key());
			result.parserName = doc.get(Fields.PARSER.key());

			result.path = extractPathFromUid(result.uid);
			result.isEmail = result.uid != null && result.uid.startsWith("outlook://");

			if (sizeStr != null) {
				try {
					result.sizeInKb = Long.parseLong(sizeStr) / 1024;
				} catch (NumberFormatException e) {
					result.sizeInKb = 0;
				}
			}

			result.score = maxScore > 0 ? Math.round(scoreDoc.score / maxScore * 100) : 0;

			String lastModStr = doc.get(Fields.LAST_MODIFIED.key());
			if (lastModStr != null) {
				try {
					long millis = Long.parseLong(lastModStr);
					if (millis > 0) {
						result.lastModified = dateFormat.format(new Date(millis));
					}
				} catch (NumberFormatException e) { /* ignore */ }
			}

			if (result.isEmail) {
				String subject = doc.get(Fields.SUBJECT.key());
				if (subject != null && !subject.isEmpty()) result.title = subject;
				String sender = doc.get(Fields.SENDER.key());
				if (sender != null && !sender.isEmpty()) result.authors = sender;
				String dateStr = doc.get(Fields.DATE.key());
				if (dateStr != null) {
					try {
						long millis = Long.parseLong(dateStr);
						if (millis > 0) result.lastModified = dateFormat.format(new Date(millis));
					} catch (NumberFormatException e) { /* ignore */ }
				}
			}

			if (result.type == null) result.type = "";
			if (result.path == null) result.path = "";

			results.add(result);
		}

		// Approximate total pages based on dedup ratio observed
		psr.totalPages = (uniqueCount + pageSize - 1) / pageSize;
		if (topDocs.scoreDocs.length < topDocs.totalHits && uniqueCount > 0) {
			// Estimate total unique from ratio seen so far
			double dedupRatio = (double) uniqueCount / topDocs.scoreDocs.length;
			int estimatedUnique = (int) (psr.totalHits * dedupRatio);
			psr.totalPages = (estimatedUnique + pageSize - 1) / pageSize;
			psr.totalHits = estimatedUnique;
		} else {
			psr.totalHits = uniqueCount;
		}

		psr.results = results;
		return psr;
	}

	/**
	 * Returns metadata about all loaded indexes.
	 */
	public List<IndexInfo> listIndexes() {
		List<IndexInfo> infos = new ArrayList<>();
		for (LoadedIndex loaded : loadedIndexes) {
			IndexInfo info = new IndexInfo();
			info.name = loaded.name;
			info.rootPath = loaded.rootPath;
			info.documentCount = loaded.documentCount;
			info.isEmailIndex = loaded.isEmail;
			infos.add(info);
		}
		return infos;
	}

	/**
	 * Looks up the parser name for a given file path from the Lucene index.
	 */
	public String getParserNameForPath(String filePath) {
		if (searcher == null) return null;
		try {
			// Search for the document by UID prefix matching
			String fileUid = "file://" + filePath;
			String outlookUid = "outlook://" + filePath;

			for (String uid : new String[]{fileUid, outlookUid}) {
				Query uidQuery = new TermQuery(new Term(Fields.UID.key(), uid));
				TopDocs results = searcher.search(uidQuery, 1);
				if (results.totalHits > 0) {
					Document doc = searcher.doc(results.scoreDocs[0].doc);
					return doc.get(Fields.PARSER.key());
				}
			}
		} catch (Exception e) {
			System.err.println("Error looking up parser for " + filePath + ": " + e.getMessage());
		}
		return null;
	}

	/**
	 * Find the LuceneIndex that contains a given file path.
	 */
	public LuceneIndex findLuceneIndexForPath(String filePath) {
		for (LoadedIndex loaded : loadedIndexes) {
			if (loaded.luceneIndex == null) continue;
			if (loaded.rootPath != null && filePath.startsWith(loaded.rootPath)) {
				return loaded.luceneIndex;
			}
		}
		return null;
	}

	public Analyzer getAnalyzer() {
		return analyzer;
	}

	public IndexSearcher getSearcher() {
		return searcher;
	}

	public int getIndexCount() {
		return loadedIndexes.size();
	}

	// --- Directory listing ---

	private String cachedDirectoryTree = null;
	private int cachedDirectoryDepth = -1;

	/**
	 * List directories in the index, formatted as an indented tree.
	 * Results are cached since indexes don't change during a session.
	 */
	public String listDirectories(int depth) throws Exception {
		if (cachedDirectoryTree != null && cachedDirectoryDepth == depth) {
			return cachedDirectoryTree;
		}

		if (searcher == null) return "No indexes loaded.";

		// Collect all unique parent directories from UIDs
		Set<String> dirs = new TreeSet<>();
		var reader = searcher.getIndexReader();
		var uidFieldSet = java.util.Collections.singleton(Fields.UID.key());

		for (int i = 0; i < reader.maxDoc(); i++) {
			Document doc = reader.document(i, uidFieldSet);
			String uid = doc.get(Fields.UID.key());
			String path = extractPathFromUid(uid);
			if (path == null) continue;
			// Extract parent directory
			int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
			if (lastSep > 0) {
				dirs.add(path.substring(0, lastSep).replace('\\', '/'));
			}
		}

		if (dirs.isEmpty()) return "No directories found.";

		// Group directories by root (first 2 path components, e.g. "C:/Users")
		// Then find common prefix within each root group
		// For simplicity: find common prefix across all dirs, if long enough use it;
		// otherwise show full paths.
		String root = findCommonPrefix(dirs);

		StringBuilder sb = new StringBuilder();
		Set<String> printed = new LinkedHashSet<>();

		if (root.length() > 3) {
			// Common root found
			sb.append("Root: ").append(root).append("\n");
			String rootPrefix = root.endsWith("/") ? root : root + "/";

			for (String dir : dirs) {
				String relative = dir.startsWith(rootPrefix)
					? dir.substring(rootPrefix.length()) : dir;
				if (relative.isEmpty()) continue;

				String[] parts = relative.split("/");
				int showDepth = Math.min(parts.length, depth);

				for (int d = 0; d < showDepth; d++) {
					StringBuilder pathBuilder = new StringBuilder();
					for (int j = 0; j <= d; j++) {
						if (j > 0) pathBuilder.append("/");
						pathBuilder.append(parts[j]);
					}
					String partialPath = pathBuilder.toString();
					if (printed.add(partialPath)) {
						sb.append("  ".repeat(d + 1)).append(parts[d]).append("/\n");
					}
				}
			}
		} else {
			// No single common root — group dirs by drive/prefix
			// Find groups sharing a long common prefix
			java.util.Map<String, Set<String>> groups = new java.util.LinkedHashMap<>();
			for (String dir : dirs) {
				// Group by first 2 components (e.g. "C:/Users" or "J:/01 - Clientes Ativos")
				String[] parts = dir.split("/");
				String groupKey = parts.length >= 2 ? parts[0] + "/" + parts[1] : parts[0];
				groups.computeIfAbsent(groupKey, k -> new TreeSet<>()).add(dir);
			}

			for (var entry : groups.entrySet()) {
				Set<String> groupDirs = entry.getValue();
				String groupRoot = findCommonPrefix(groupDirs);
				sb.append("\nRoot: ").append(groupRoot).append("\n");
				String grPrefix = groupRoot.endsWith("/") ? groupRoot : groupRoot + "/";

				for (String dir : groupDirs) {
					String relative = dir.startsWith(grPrefix)
						? dir.substring(grPrefix.length()) : dir;
					if (relative.isEmpty()) continue;

					String[] parts = relative.split("/");
					int showDepth = Math.min(parts.length, depth);

					for (int d = 0; d < showDepth; d++) {
						StringBuilder pathBuilder = new StringBuilder();
						for (int j = 0; j <= d; j++) {
							if (j > 0) pathBuilder.append("/");
							pathBuilder.append(parts[j]);
						}
						String partialPath = groupRoot + "/" + pathBuilder;
						if (printed.add(partialPath)) {
							sb.append("  ".repeat(d + 1)).append(parts[d]).append("/\n");
						}
					}
				}
			}
		}

		cachedDirectoryTree = sb.toString().stripTrailing();
		cachedDirectoryDepth = depth;
		return cachedDirectoryTree;
	}

	private String findCommonPrefix(Set<String> paths) {
		String first = paths.iterator().next();
		String prefix = first;
		for (String path : paths) {
			while (!path.startsWith(prefix)) {
				int lastSep = prefix.lastIndexOf('/');
				if (lastSep <= 0) return "";
				prefix = prefix.substring(0, lastSep);
			}
		}
		return prefix;
	}

	/**
	 * Resolve a file path, translating Windows paths to WSL paths if needed.
	 * E.g., "C:/Users/foo" → "/mnt/c/Users/foo" when running on WSL/Linux.
	 */
	public static File resolveFile(String path) {
		File file = new File(path);
		if (file.exists()) return file;

		// Try WSL translation: "C:/path" or "C:\path" → "/mnt/c/path"
		if (path.length() >= 2 && Character.isLetter(path.charAt(0))
				&& (path.charAt(1) == ':')) {
			String driveLetter = path.substring(0, 1).toLowerCase();
			String rest = path.substring(2).replace('\\', '/');
			File wslFile = new File("/mnt/" + driveLetter + rest);
			if (wslFile.exists()) return wslFile;
		}

		return file; // return original even if not found
	}

	// --- Internal helpers ---

	private LuceneIndex deserializeIndex(File serFile) throws Exception {
		try (FileInputStream fis = new FileInputStream(serFile);
			 BufferedInputStream bis = new BufferedInputStream(fis);
			 ObjectInputStream ois = new ObjectInputStream(bis)) {
			return (LuceneIndex) ois.readObject();
		}
	}

	private String readIndexName(File indexDir) {
		File nameFile = new File(indexDir, "index-name.txt");
		if (!nameFile.exists()) return null;
		try {
			List<String> lines = Files.readAllLines(nameFile.toPath(), StandardCharsets.UTF_8);
			if (!lines.isEmpty() && !lines.get(0).isBlank()) {
				return lines.get(0).trim();
			}
		} catch (Exception e) { /* ignore */ }
		return null;
	}

	static String extractPathFromUid(String uid) {
		if (uid == null) return null;
		int idx = uid.indexOf("://");
		if (idx >= 0) {
			return uid.substring(idx + 3);
		}
		return uid;
	}

	// --- Data classes ---

	static class LoadedIndex {
		File directory;
		String name;
		String rootPath;
		boolean isEmail;
		DirectoryReader reader;
		LuceneIndex luceneIndex;
		int documentCount;
	}

	public static class SearchResult {
		public String uid;
		public String filename;
		public String path;
		public String title;
		public String authors;
		public String type;
		public long sizeInKb;
		public int score;
		public String lastModified;
		public boolean isEmail;
		public String parserName;
		// Populated by SnippetGenerator
		public String excerpt;
		public Integer pageNumber;
		public Integer pageCount;
	}

	public static class PaginatedSearchResult {
		public List<SearchResult> results;
		public int totalHits;
		public int page;
		public int totalPages;
		public int pageSize;
		public Query parsedQuery;
	}

	public static class IndexInfo {
		public String name;
		public String rootPath;
		public int documentCount;
		public boolean isEmailIndex;
	}
}
