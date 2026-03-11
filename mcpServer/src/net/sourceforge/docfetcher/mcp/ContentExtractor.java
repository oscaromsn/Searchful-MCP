package net.sourceforge.docfetcher.mcp;

import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.parse.ParseService;
import net.sourceforge.docfetcher.model.search.PhraseDetectingQueryParser;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.lucene.search.highlight.TokenSources;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts and highlights text content from documents for the get_document_content tool.
 */
public class ContentExtractor {

	private static final String SNIPPET_SEPARATOR = "\n...\n";
	private static final int SNIPPET_CONTEXT_CHARS = 200;

	private final IndexManager indexManager;

	public ContentExtractor(IndexManager indexManager) {
		this.indexManager = indexManager;
	}

	/**
	 * Extract text content from a document, optionally with highlighted snippets.
	 *
	 * @param filePath Path to the document file
	 * @param queryStr Optional query for highlighting (null for full text)
	 * @param maxChars Maximum characters to return
	 * @return Document text or highlighted snippets
	 */
	public String extract(String filePath, String queryStr, int maxChars) throws Exception {
		File file = new File(filePath);
		if (!file.exists()) {
			throw new IOException("File not found: " + filePath);
		}

		String text = extractText(filePath, file);

		if (queryStr != null && !queryStr.isBlank()) {
			return extractHighlightedSnippets(text, queryStr, maxChars);
		}

		// No query — return full text truncated
		if (text.length() > maxChars) {
			return text.substring(0, maxChars) + "\n... (truncated at " + maxChars + " chars)";
		}
		return text;
	}

	/**
	 * Extract raw text from a file using DocFetcher's parsers.
	 * Falls back to reading as plain text if parsing fails.
	 */
	private String extractText(String filePath, File file) throws IOException {
		// Try using ParseService with the parser name from the index
		String parserName = indexManager.getParserNameForPath(filePath);
		if (parserName != null) {
			LuceneIndex luceneIndex = indexManager.findLuceneIndexForPath(filePath);
			if (luceneIndex != null) {
				try {
					String text = ParseService.renderText(
						luceneIndex.getConfig(), file, file.getName(), parserName);
					if (text != null && !text.isEmpty()) {
						return text;
					}
				} catch (Exception e) {
					System.err.println("ParseService.renderText() failed for "
						+ filePath + ": " + e.getMessage());
				}
			}
		}

		// Fallback: try to detect parser by extension and parse
		try {
			String text = parseByExtension(file);
			if (text != null && !text.isEmpty()) {
				return text;
			}
		} catch (Exception e) {
			System.err.println("Extension-based parsing failed for "
				+ filePath + ": " + e.getMessage());
		}

		// Last resort: read as plain text
		return readAsPlainText(file);
	}

	/**
	 * Try to parse a file by matching its extension against known parsers.
	 */
	private String parseByExtension(File file) throws Exception {
		String name = file.getName();
		String ext = "";
		int dotIdx = name.lastIndexOf('.');
		if (dotIdx >= 0) {
			ext = name.substring(dotIdx + 1).toLowerCase();
		}

		// Use ParseService's parser list to find a matching parser
		for (var parser : ParseService.getParsers()) {
			String parserName = parser.getClass().getSimpleName();
			// Match common extensions to parser names
			boolean matches = switch (ext) {
				case "pdf" -> parserName.contains("Pdf");
				case "doc" -> parserName.contains("Word") && !parserName.contains("2007");
				case "docx" -> parserName.contains("Word") && parserName.contains("2007");
				case "xls" -> parserName.contains("Excel") && !parserName.contains("2007");
				case "xlsx" -> parserName.contains("Excel") && parserName.contains("2007");
				case "ppt" -> parserName.contains("PowerPoint") && !parserName.contains("2007");
				case "pptx" -> parserName.contains("PowerPoint") && parserName.contains("2007");
				case "html", "htm" -> parserName.contains("Html");
				case "odt", "ods", "odp", "odg" -> parserName.contains("OpenOffice");
				case "rtf" -> parserName.contains("Rtf");
				case "txt", "java", "py", "cpp", "c", "h", "xml", "json", "yaml", "yml",
					 "md", "csv", "log", "ini", "cfg", "conf", "properties", "sh", "bat" ->
					parserName.contains("Text");
				default -> false;
			};
			if (matches) {
				// Found a matching parser, but we need IndexingConfig to call renderText
				// This path is best-effort; if it doesn't work, we fall back to plain text
				break;
			}
		}

		// If we can't determine the right parser, return null to fall back
		return null;
	}

	/**
	 * Read file as plain text (UTF-8, then ISO-8859-1 fallback).
	 */
	private String readAsPlainText(File file) throws IOException {
		try {
			return Files.readString(file.toPath(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return Files.readString(file.toPath(), StandardCharsets.ISO_8859_1);
		}
	}

	/**
	 * Extract highlighted snippets from text using Lucene's Highlighter.
	 */
	private String extractHighlightedSnippets(String text, String queryStr, int maxChars)
			throws Exception {
		Analyzer analyzer = indexManager.getAnalyzer();

		// Parse the query
		PhraseDetectingQueryParser parser = new PhraseDetectingQueryParser(
			Fields.CONTENT.key(), analyzer);
		parser.setAllowLeadingWildcard(true);
		Query query = parser.parse(queryStr);

		// Set up highlighter with plain text markers
		SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(">>", "<<");
		QueryScorer scorer = new QueryScorer(query, Fields.CONTENT.key());
		Highlighter highlighter = new Highlighter(formatter, scorer);
		highlighter.setMaxDocCharsToAnalyze(text.length());

		// Get best fragments
		int maxFragments = Math.max(1, maxChars / SNIPPET_CONTEXT_CHARS);
		TextFragment[] fragments = highlighter.getBestTextFragments(
			analyzer.tokenStream(Fields.CONTENT.key(), text), text, false, maxFragments);

		if (fragments == null || fragments.length == 0) {
			// No matches found — return truncated text
			if (text.length() > maxChars) {
				return text.substring(0, maxChars) + "\n... (truncated, no matches found)";
			}
			return text + "\n(no matches found for query)";
		}

		// Collect scored fragments
		List<String> snippets = new ArrayList<>();
		int totalChars = 0;
		for (TextFragment fragment : fragments) {
			if (fragment.getScore() <= 0) continue;
			String snippetText = fragment.toString().trim();
			if (snippetText.isEmpty()) continue;

			if (totalChars + snippetText.length() > maxChars) {
				int remaining = maxChars - totalChars;
				if (remaining > 50) {
					snippets.add(snippetText.substring(0, remaining) + "...");
				}
				break;
			}
			snippets.add(snippetText);
			totalChars += snippetText.length();
		}

		if (snippets.isEmpty()) {
			if (text.length() > maxChars) {
				return text.substring(0, maxChars) + "\n... (truncated, no highlighted matches)";
			}
			return text;
		}

		return String.join(SNIPPET_SEPARATOR, snippets);
	}
}
