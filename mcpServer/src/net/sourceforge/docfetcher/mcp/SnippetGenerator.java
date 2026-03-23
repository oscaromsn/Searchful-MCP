package net.sourceforge.docfetcher.mcp;

import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.model.parse.PageHandler;
import net.sourceforge.docfetcher.model.parse.PagingPdfParser;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TextFragment;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.util.List;

/**
 * Generates highlighted excerpts for search results.
 * For PDFs, also determines the page number of the first match.
 */
public class SnippetGenerator {

	private static final int SNIPPET_CHAR_SIZE = 150;
	private static final int MAX_FRAGMENTS = 2;
	private static final int MAX_TEXT_FOR_SNIPPET = 100_000;

	private final ContentExtractor contentExtractor;
	private final Analyzer analyzer;

	public SnippetGenerator(ContentExtractor contentExtractor, Analyzer analyzer) {
		this.contentExtractor = contentExtractor;
		this.analyzer = analyzer;
	}

	/**
	 * Enrich search results with excerpts and (for PDFs) page numbers.
	 */
	public void enrichResults(List<IndexManager.SearchResult> results, Query query) {
		for (IndexManager.SearchResult result : results) {
			try {
				enrichSingleResult(result, query);
			} catch (Exception e) {
				System.err.println("Snippet generation failed for "
					+ result.path + ": " + e.getMessage());
			}
		}
	}

	private void enrichSingleResult(IndexManager.SearchResult result, Query query) throws Exception {
		File file = IndexManager.resolveFile(result.path);
		if (!file.exists()) return;

		if ("pdf".equals(result.type)) {
			enrichPdfResult(result, query, file);
		} else {
			enrichNonPdfResult(result, query, file);
		}
	}

	private void enrichPdfResult(IndexManager.SearchResult result, Query query, File file) throws Exception {
		// Get page count
		try (PDDocument doc = PDDocument.load(file)) {
			result.pageCount = doc.getNumberOfPages();
		}

		// Use PagingPdfParser to find the first page with a match
		SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(">>", "<<");
		QueryScorer scorer = new QueryScorer(query, Fields.CONTENT.key());
		Highlighter highlighter = new Highlighter(formatter, scorer);

		int[] currentPage = {0};
		String[] bestExcerpt = {null};
		int[] matchPage = {0};

		try {
			new PagingPdfParser(file, pageText -> {
				currentPage[0]++;

				if (pageText == null || pageText.isBlank()) return false;

				try {
					// Cap text per page to avoid excessive analysis
					String text = pageText.length() > MAX_TEXT_FOR_SNIPPET
						? pageText.substring(0, MAX_TEXT_FOR_SNIPPET) : pageText;

					highlighter.setMaxDocCharsToAnalyze(text.length());
					TextFragment[] fragments = highlighter.getBestTextFragments(
						analyzer.tokenStream(Fields.CONTENT.key(), text),
						text, false, MAX_FRAGMENTS);

					if (fragments != null) {
						StringBuilder sb = new StringBuilder();
						for (TextFragment frag : fragments) {
							if (frag.getScore() > 0) {
								if (!sb.isEmpty()) sb.append(" ... ");
								sb.append(frag.toString().trim());
							}
						}
						if (!sb.isEmpty()) {
							bestExcerpt[0] = sb.toString();
							matchPage[0] = currentPage[0];
							return true; // stop — found first match
						}
					}
				} catch (Exception e) {
					// Continue to next page
				}
				return false;
			}).run();
		} catch (Exception e) {
			System.err.println("PagingPdfParser failed for " + file.getName() + ": " + e.getMessage());
		}

		if (bestExcerpt[0] != null) {
			result.excerpt = truncateExcerpt(bestExcerpt[0]);
			result.pageNumber = matchPage[0];
		}
	}

	private void enrichNonPdfResult(IndexManager.SearchResult result, Query query, File file) throws Exception {
		String text = contentExtractor.extractText(result.path, file);
		if (text == null || text.isBlank()) return;

		if (text.length() > MAX_TEXT_FOR_SNIPPET) {
			text = text.substring(0, MAX_TEXT_FOR_SNIPPET);
		}

		SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(">>", "<<");
		QueryScorer scorer = new QueryScorer(query, Fields.CONTENT.key());
		Highlighter highlighter = new Highlighter(formatter, scorer);
		highlighter.setMaxDocCharsToAnalyze(text.length());

		TextFragment[] fragments = highlighter.getBestTextFragments(
			analyzer.tokenStream(Fields.CONTENT.key(), text),
			text, false, MAX_FRAGMENTS);

		if (fragments != null) {
			StringBuilder sb = new StringBuilder();
			for (TextFragment frag : fragments) {
				if (frag.getScore() > 0) {
					if (!sb.isEmpty()) sb.append(" ... ");
					sb.append(frag.toString().trim());
				}
			}
			if (!sb.isEmpty()) {
				result.excerpt = truncateExcerpt(sb.toString());
			}
		}
	}

	private String truncateExcerpt(String excerpt) {
		// Clean OCR noise from excerpt
		excerpt = ContentExtractor.cleanOcrNoise(excerpt);
		int maxLen = SNIPPET_CHAR_SIZE * MAX_FRAGMENTS + 20; // allow some margin
		if (excerpt.length() > maxLen) {
			return excerpt.substring(0, maxLen) + "...";
		}
		return excerpt;
	}
}
