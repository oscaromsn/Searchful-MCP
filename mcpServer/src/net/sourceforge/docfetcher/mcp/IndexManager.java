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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
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
import java.util.List;

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
	 * Search across all loaded indexes.
	 */
	public List<SearchResult> search(
			String queryStr, int maxResults,
			List<String> fileTypes, Long minSizeKb, Long maxSizeKb) throws Exception {
		if (searcher == null) {
			return List.of();
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

		BooleanQuery finalQuery = bqBuilder.build();

		// Execute search
		TopDocs topDocs = searcher.search(finalQuery, maxResults);

		// Convert results
		List<SearchResult> results = new ArrayList<>();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

		for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
			Document doc = searcher.doc(scoreDoc.doc);
			SearchResult result = new SearchResult();

			result.uid = doc.get(Fields.UID.key());
			result.filename = doc.get(Fields.FILENAME.key());
			result.type = doc.get(Fields.TYPE.key());
			result.title = doc.get(Fields.TITLE.key());
			result.authors = doc.get(Fields.AUTHOR.key());

			// Parse path from UID
			result.path = extractPathFromUid(result.uid);
			result.isEmail = result.uid != null && result.uid.startsWith("outlook://");

			// Size
			String sizeStr = doc.get(Fields.SIZE.key());
			if (sizeStr != null) {
				try {
					result.sizeInKb = Long.parseLong(sizeStr) / 1024;
				} catch (NumberFormatException e) {
					result.sizeInKb = 0;
				}
			}

			// Score (normalize to 0-100)
			float maxScore = topDocs.getMaxScore();
			result.score = maxScore > 0 ? Math.round(scoreDoc.score / maxScore * 100) : 0;

			// Last modified
			String lastModStr = doc.get(Fields.LAST_MODIFIED.key());
			if (lastModStr != null) {
				try {
					long millis = Long.parseLong(lastModStr);
					if (millis > 0) {
						result.lastModified = dateFormat.format(new Date(millis));
					}
				} catch (NumberFormatException e) { /* ignore */ }
			}

			// Email-specific fields
			if (result.isEmail) {
				String subject = doc.get(Fields.SUBJECT.key());
				if (subject != null && !subject.isEmpty()) {
					result.title = subject;
				}
				String sender = doc.get(Fields.SENDER.key());
				if (sender != null && !sender.isEmpty()) {
					result.authors = sender;
				}
				String dateStr = doc.get(Fields.DATE.key());
				if (dateStr != null) {
					try {
						long millis = Long.parseLong(dateStr);
						if (millis > 0) {
							result.lastModified = dateFormat.format(new Date(millis));
						}
					} catch (NumberFormatException e) { /* ignore */ }
				}
			}

			if (result.filename == null) result.filename = "";
			if (result.type == null) result.type = "";
			if (result.path == null) result.path = "";

			results.add(result);
		}

		return results;
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

	public int getIndexCount() {
		return loadedIndexes.size();
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
	}

	public static class IndexInfo {
		public String name;
		public String rootPath;
		public int documentCount;
		public boolean isEmailIndex;
	}
}
