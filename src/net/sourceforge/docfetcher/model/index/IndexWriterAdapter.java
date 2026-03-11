/*******************************************************************************
 * Copyright (c) 2010, 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model.index;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

import net.sourceforge.docfetcher.gui.ManualLocator;
import net.sourceforge.docfetcher.model.Fields;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.util.CheckedOutOfMemoryError;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.VisibleForPackageGroup;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;

import com.google.common.io.Closeables;

/**
 * Wrapper for Lucene's IndexWriter that adds some functionality.
 * 
 * @author Tran Nam Quang
 */
@VisibleForPackageGroup
public final class IndexWriterAdapter implements Closeable {

	public static final Term idTerm = new Term(Fields.UID.key());

	private static final String EXTERNAL_MODIFICATION_MSG =
			"Index corrupted by external process (e.g., antivirus software). " +
			"Please exclude the index folder from antivirus scans, " +
			"then rebuild the index.";

	private static String getIndexCorruptionMsg() {
		String url = ManualLocator.getManualSubpageUrl("Memory_Limit.html");
		if (url == null) {
			return "Index corruption detected. This is usually caused by running out of " +
				"memory during a previous indexing operation.";
		}
		return "Index corruption detected. This is usually caused by running out of " +
				"memory during a previous indexing operation. Please see the following file " +
				"for instructions on increasing the memory limit, then rebuild the index.\n" + url;
	}

	@NotNull private IndexWriter writer;

	public IndexWriterAdapter(@NotNull Directory luceneDir) throws IOException {
		try {
			IndexWriterConfig config
					= new IndexWriterConfig(IndexRegistry.getAnalyzer());
			writer = new IndexWriter(luceneDir, config);
		}
		catch (CorruptIndexException e) {
			throw new IOException(getIndexCorruptionMsg(), e);
		}
		catch (NoSuchFileException e) {
			// Bug #2281: Missing segment files, typically caused by OOME during
			// a previous indexing session that left orphaned file references
			throw new IOException(getIndexCorruptionMsg(), e);
		}
	}

	// may throw OutOfMemoryError
	public void add(@NotNull Document document) throws IOException,
			CheckedOutOfMemoryError {
		try {
			writer.addDocument(document);
		}
		catch (OutOfMemoryError e) {
			reopenWriterAndThrow(e);
		}
		catch (CorruptIndexException e) {
			throw new IOException(getIndexCorruptionMsg(), e);
		}
		catch (NoSuchFileException e) {
			// Bug #2281: Missing segment files, typically caused by OOME during
			// a previous indexing session that left orphaned file references
			throw new IOException(getIndexCorruptionMsg(), e);
		}
		catch (AlreadyClosedException e) {
			// Check if the cause is CorruptIndexException or NoSuchFileException
			Throwable cause = e.getCause();
			if (cause instanceof CorruptIndexException || cause instanceof NoSuchFileException) {
				throw new IOException(getIndexCorruptionMsg(), e);
			} else {
				throw new IOException(EXTERNAL_MODIFICATION_MSG, e);
			}
		}
		catch (IllegalStateException e) {
			if (e.getMessage().contains("OutOfMemoryError")) {
				reopenWriterAndThrow(e);
			} else {
				throw e;
			}
		}
	}

	// may throw OutOfMemoryError
	public void update(@NotNull String uid, @NotNull Document document)
			throws IOException, CheckedOutOfMemoryError {
		try {
			writer.updateDocument(new Term(idTerm.field(), uid), document);
		}
		catch (OutOfMemoryError e) {
			reopenWriterAndThrow(e);
		}
		catch (CorruptIndexException e) {
			throw new IOException(getIndexCorruptionMsg(), e);
		}
		catch (NoSuchFileException e) {
			// Bug #2281: Missing segment files, typically caused by OOME during
			// a previous indexing session that left orphaned file references
			throw new IOException(getIndexCorruptionMsg(), e);
		}
		catch (AlreadyClosedException e) {
			// Check if the cause is CorruptIndexException or NoSuchFileException
			Throwable cause = e.getCause();
			if (cause instanceof CorruptIndexException || cause instanceof NoSuchFileException) {
				throw new IOException(getIndexCorruptionMsg(), e);
			} else {
				throw new IOException(EXTERNAL_MODIFICATION_MSG, e);
			}
		}
		catch (IllegalStateException e) {
			if (e.getMessage().contains("OutOfMemoryError")) {
				reopenWriterAndThrow(e);
			} else {
				throw e;
			}
		}
	}
	
	private void reopenWriterAndThrow(@NotNull Throwable t)
			throws IOException, CheckedOutOfMemoryError {
		/*
		 * According to the IndexWriter javadoc, we're supposed to immediately
		 * close the IndexWriter if IndexWriter.addDocument(...) or
		 * IndexWriter.updateDocument(...) hit OutOfMemoryErrors.
		 */
		Directory indexDir = writer.getDirectory();
		Closeables.closeQuietly(writer);
		IndexWriterConfig config
				= new IndexWriterConfig(IndexRegistry.getAnalyzer());
		writer = new IndexWriter(indexDir, config);
		throw new CheckedOutOfMemoryError(t);
	}

	public void delete(@NotNull String uid) throws IOException {
		writer.deleteDocuments(new Term(idTerm.field(),uid));
	}
	
	public void close() throws IOException {
		try {
			writer.close();
		}
		catch (AssertionError e) {
			/*
			 * On certain filesystems (e.g., CIFS, network mounts), Lucene's
			 * fsync operation on directories may fail with an IOException,
			 * which Lucene then wraps in an AssertionError with the message:
			 * "On Linux and MacOSX fsyncing a directory should not throw
			 * IOException, we just don't want to rely on that in production
			 * (undocumented)."
			 *
			 * Since directory fsync is a best-effort operation for metadata
			 * durability and not critical for index integrity, we can safely
			 * ignore this error. The index will still be valid even if the
			 * directory metadata wasn't synced.
			 */
			String msg = e.getMessage();
			if (msg != null && msg.contains("fsyncing a directory")) {
				// Ignore the error - directory fsync is best-effort
			} else {
				// Re-throw if it's a different kind of AssertionError
				throw e;
			}
		}
	}

}
