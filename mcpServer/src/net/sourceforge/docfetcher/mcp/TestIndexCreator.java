package net.sourceforge.docfetcher.mcp;

import net.sourceforge.docfetcher.model.Fields;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LegacyLongField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.nio.file.Path;

/**
 * Creates a test Lucene index with sample documents matching DocFetcher's schema.
 * Usage: java TestIndexCreator <output-directory>
 */
public class TestIndexCreator {

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("Usage: TestIndexCreator <output-directory>");
			System.exit(1);
		}

		File indexDir = new File(args[0], "test-index_1234567890");
		indexDir.mkdirs();

		StandardAnalyzer analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET);
		IndexWriterConfig config = new IndexWriterConfig(analyzer);

		try (FSDirectory dir = FSDirectory.open(indexDir.toPath());
			 IndexWriter writer = new IndexWriter(dir, config)) {

			// Document 1: A PDF about machine learning
			addDocument(writer, "file:///home/user/docs/ml-intro.pdf",
				"ml-intro.pdf", "pdf", "Introduction to Machine Learning",
				"John Smith", 2048000, // ~2MB
				"Machine learning is a subset of artificial intelligence that focuses on " +
				"building systems that learn from data. Supervised learning uses labeled " +
				"training data to make predictions. Deep learning uses neural networks " +
				"with many layers to extract features from data.",
				"PdfParser", System.currentTimeMillis() - 86400000);

			// Document 2: A Word doc about project planning
			addDocument(writer, "file:///home/user/docs/project-plan.docx",
				"project-plan.docx", "docx", "Q1 2024 Project Plan",
				"Jane Doe", 512000, // ~500KB
				"Project plan for first quarter. Key milestones include the alpha " +
				"release in February and beta testing in March. Resource allocation " +
				"requires three senior developers and two QA engineers.",
				"MSWord2007Parser", System.currentTimeMillis() - 172800000);

			// Document 3: A text file with code documentation
			addDocument(writer, "file:///home/user/code/README.txt",
				"README.txt", "txt", "Project README",
				"Dev Team", 8192, // ~8KB
				"This project implements a search engine using Lucene. To build, " +
				"run ./mill compile. For testing, use ./mill test. The main entry " +
				"point is in Main.java. Configuration is stored in program-conf.txt.",
				"TextParser", System.currentTimeMillis() - 3600000);

			// Document 4: An HTML page about data science
			addDocument(writer, "file:///home/user/web/data-science.html",
				"data-science.html", "html", "Data Science Overview",
				"Alice Johnson", 32768, // ~32KB
				"Data science combines statistics, programming, and domain expertise " +
				"to extract insights from data. Python and R are popular languages. " +
				"Machine learning and deep learning are key tools in the data " +
				"scientist's toolkit.",
				"HtmlParser", System.currentTimeMillis() - 604800000);

			// Document 5: A small PDF
			addDocument(writer, "file:///home/user/docs/memo.pdf",
				"memo.pdf", "pdf", "Team Meeting Notes",
				"Bob Wilson", 4096, // ~4KB
				"Meeting notes from the weekly standup. Topics discussed: sprint " +
				"velocity, code review process, and upcoming release schedule. " +
				"Action items assigned to each team member.",
				"PdfParser", System.currentTimeMillis() - 43200000);

			writer.commit();
		}

		System.out.println("Created test index at: " + indexDir.getAbsolutePath());
		System.out.println("Index contains 5 test documents.");
	}

	private static void addDocument(IndexWriter writer, String uid, String filename,
			String type, String title, String author, long sizeBytes,
			String content, String parserName, long lastModified) throws Exception {
		Document doc = new Document();
		doc.add(Fields.UID.create(uid));
		doc.add(Fields.FILENAME.create(filename));
		doc.add(Fields.TYPE.create(type));
		doc.add(Fields.TITLE.create(title));
		doc.add(Fields.AUTHOR.create(author));
		doc.add(new LegacyLongField(Fields.SIZE.key(), sizeBytes, Field.Store.YES));
		doc.add(Fields.PARSER.create(parserName));
		doc.add(Fields.LAST_MODIFIED.create(String.valueOf(lastModified)));
		doc.add(Fields.createContent(content, false));
		writer.addDocument(doc);
	}
}
