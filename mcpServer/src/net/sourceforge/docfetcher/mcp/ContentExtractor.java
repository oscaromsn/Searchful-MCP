package net.sourceforge.docfetcher.mcp;

import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.parse.PageHandler;
import net.sourceforge.docfetcher.model.parse.PagingPdfParser;
import net.sourceforge.docfetcher.model.parse.ParseService;
import net.sourceforge.docfetcher.model.search.PhraseDetectingQueryParser;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TextFragment;

import org.apache.pdfbox.pdmodel.PDDocument;

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
		File file = IndexManager.resolveFile(filePath);
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
	String extractText(String filePath, File file) throws IOException {
		// Try using ParseService with the parser name from the index
		String parserName = indexManager.getParserNameForPath(filePath);
		if (parserName != null) {
			LuceneIndex luceneIndex = indexManager.findLuceneIndexForPath(filePath);
			if (luceneIndex != null) {
				try {
					String text = ParseService.renderText(
						luceneIndex.getConfig(), file, file.getName(), parserName);
					if (text != null && !text.isEmpty()) {
						return cleanOcrNoise(text);
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
		return cleanOcrNoise(readAsPlainText(file));
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

	// --- OCR Noise Cleanup ---

	/**
	 * Remove common OCR garbage from scanned PDF text.
	 * Conservative heuristics to avoid destroying legitimate text.
	 */
	static String cleanOcrNoise(String text) {
		if (text == null || text.isEmpty()) return text;

		String[] lines = text.split("\n");
		StringBuilder sb = new StringBuilder();
		int blankCount = 0;

		for (String line : lines) {
			String trimmed = line.trim();

			if (trimmed.isEmpty()) {
				blankCount++;
				if (blankCount <= 2) sb.append("\n");
				continue;
			}
			blankCount = 0;

			// Count alphanumeric + space characters
			long alnumCount = trimmed.chars()
				.filter(c -> Character.isLetterOrDigit(c) || c == ' ')
				.count();
			double ratio = (double) alnumCount / trimmed.length();

			// Skip short lines with low alphanumeric ratio (OCR noise)
			if (trimmed.length() < 80 && ratio < 0.3) continue;

			// Skip lines with no letters at all (pure symbols/numbers fragments)
			if (trimmed.chars().noneMatch(Character::isLetter)) continue;

			sb.append(line).append("\n");
		}

		return sb.toString().stripTrailing();
	}

	// --- Page-range extraction for PDFs ---

	/**
	 * Extract text from specific pages of a PDF document.
	 */
	public PagedContent extractPages(String filePath, int startPage, int endPage,
			String queryStr, int maxChars) throws Exception {
		File file = IndexManager.resolveFile(filePath);
		if (!file.exists()) {
			throw new IOException("File not found: " + filePath);
		}

		String ext = "";
		int dotIdx = filePath.lastIndexOf('.');
		if (dotIdx >= 0) ext = filePath.substring(dotIdx + 1).toLowerCase();

		if (!"pdf".equals(ext)) {
			// Non-PDF: fall back to full text extraction
			PagedContent pc = new PagedContent();
			pc.totalPages = 0;
			pc.startPage = 0;
			pc.endPage = 0;
			pc.text = "Page-level navigation is only supported for PDF files.\n\n"
				+ extract(filePath, queryStr, maxChars);
			return pc;
		}

		// Get total page count
		int totalPages;
		try (PDDocument doc = PDDocument.load(file)) {
			totalPages = doc.getNumberOfPages();
		}

		// Clamp page range
		startPage = Math.max(1, Math.min(startPage, totalPages));
		endPage = Math.max(startPage, Math.min(endPage, totalPages));

		// Extract pages using PagingPdfParser
		int finalStartPage = startPage;
		int finalEndPage = endPage;
		StringBuilder sb = new StringBuilder();
		int[] currentPage = {0};
		int[] charsWritten = {0};

		// Optional highlighter
		Highlighter highlighter = null;
		Analyzer analyzer = null;
		if (queryStr != null && !queryStr.isBlank()) {
			analyzer = indexManager.getAnalyzer();
			PhraseDetectingQueryParser parser = new PhraseDetectingQueryParser(
				Fields.CONTENT.key(), analyzer);
			parser.setAllowLeadingWildcard(true);
			Query query = parser.parse(queryStr);
			SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(">>", "<<");
			QueryScorer scorer = new QueryScorer(query, Fields.CONTENT.key());
			highlighter = new Highlighter(formatter, scorer);
		}

		final Highlighter hl = highlighter;
		final Analyzer an = analyzer;

		new PagingPdfParser(file, pageText -> {
			currentPage[0]++;

			if (currentPage[0] < finalStartPage) return false; // skip
			if (currentPage[0] > finalEndPage) return true;    // stop

			if (charsWritten[0] >= maxChars) return true;

			sb.append("--- Page ").append(currentPage[0]).append(" ---\n");

			String text = pageText != null ? cleanOcrNoise(pageText.trim()) : "";

			// Apply highlighting if query provided
			if (hl != null && an != null && !text.isEmpty()) {
				try {
					hl.setMaxDocCharsToAnalyze(text.length());
					int maxFragments = Math.max(1, (maxChars - charsWritten[0]) / SNIPPET_CONTEXT_CHARS);
					TextFragment[] fragments = hl.getBestTextFragments(
						an.tokenStream(Fields.CONTENT.key(), text),
						text, true, maxFragments);
					if (fragments != null && fragments.length > 0) {
						StringBuilder highlighted = new StringBuilder();
						for (TextFragment frag : fragments) {
							highlighted.append(frag.toString());
						}
						text = highlighted.toString().trim();
					}
				} catch (Exception e) {
					// Use unhighlighted text
				}
			}

			int remaining = maxChars - charsWritten[0];
			if (text.length() > remaining) {
				sb.append(text, 0, remaining).append("...\n");
				charsWritten[0] = maxChars;
			} else {
				sb.append(text).append("\n\n");
				charsWritten[0] += text.length();
			}

			return currentPage[0] >= finalEndPage || charsWritten[0] >= maxChars;
		}).run();

		PagedContent pc = new PagedContent();
		pc.text = sb.toString();
		pc.startPage = startPage;
		pc.endPage = Math.min(endPage, currentPage[0]);
		pc.totalPages = totalPages;
		return pc;
	}

	public static class PagedContent {
		public String text;
		public int startPage;
		public int endPage;
		public int totalPages;
	}

	// --- Search within document (page-level) ---

	/**
	 * Search within a PDF for pages containing query matches.
	 * Returns only matching pages with highlighted snippets (not full pages).
	 */
	public PageSearchResult searchPages(String filePath, String queryStr,
			int maxChars, int maxMatchingPages) throws Exception {
		File file = IndexManager.resolveFile(filePath);
		if (!file.exists()) {
			throw new IOException("File not found: " + filePath);
		}

		int totalPages;
		try (PDDocument doc = PDDocument.load(file)) {
			totalPages = doc.getNumberOfPages();
		}

		Analyzer analyzer = indexManager.getAnalyzer();
		PhraseDetectingQueryParser parser = new PhraseDetectingQueryParser(
			Fields.CONTENT.key(), analyzer);
		parser.setAllowLeadingWildcard(true);
		Query query = parser.parse(queryStr);

		SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(">>", "<<");
		QueryScorer scorer = new QueryScorer(query, Fields.CONTENT.key());
		Highlighter highlighter = new Highlighter(formatter, scorer);

		StringBuilder sb = new StringBuilder();
		int[] currentPage = {0};
		int[] matchCount = {0};
		int[] charsWritten = {0};
		List<Integer> matchingPageNumbers = new ArrayList<>();

		new PagingPdfParser(file, pageText -> {
			currentPage[0]++;

			if (pageText == null || pageText.isBlank()) return false;
			if (matchCount[0] >= maxMatchingPages) return true;
			if (charsWritten[0] >= maxChars) return true;

			try {
				String text = cleanOcrNoise(pageText.trim());
				if (text.isEmpty()) return false;

				highlighter.setMaxDocCharsToAnalyze(text.length());
				TextFragment[] fragments = highlighter.getBestTextFragments(
					analyzer.tokenStream(Fields.CONTENT.key(), text),
					text, false, 3);

				if (fragments != null) {
					StringBuilder snippetBuilder = new StringBuilder();
					for (TextFragment frag : fragments) {
						if (frag.getScore() > 0) {
							if (!snippetBuilder.isEmpty()) snippetBuilder.append(" ... ");
							snippetBuilder.append(frag.toString().trim());
						}
					}
					if (!snippetBuilder.isEmpty()) {
						String snippet = snippetBuilder.toString();
						sb.append("[p.").append(currentPage[0]).append("] ");
						sb.append(snippet).append("\n\n");
						charsWritten[0] += snippet.length() + 10;
						matchCount[0]++;
						matchingPageNumbers.add(currentPage[0]);
					}
				}
			} catch (Exception e) {
				// Continue to next page
			}

			return matchCount[0] >= maxMatchingPages || charsWritten[0] >= maxChars;
		}).run();

		PageSearchResult result = new PageSearchResult();
		result.totalPages = totalPages;
		result.matchingPages = matchCount[0];
		result.matchingPageNumbers = matchingPageNumbers;
		result.text = sb.toString().stripTrailing();
		return result;
	}

	public static class PageSearchResult {
		public String text;
		public int totalPages;
		public int matchingPages;
		public List<Integer> matchingPageNumbers;
	}
}
