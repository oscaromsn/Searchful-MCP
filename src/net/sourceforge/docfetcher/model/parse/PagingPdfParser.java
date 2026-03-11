/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model.parse;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.util.CheckedOutOfMemoryError;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import com.google.common.io.Closeables;

/**
 * @author Tran Nam Quang
 */
public final class PagingPdfParser {
	
	private final File file;
	private final PageHandler handler;
	private final StringWriter writer = new StringWriter();

	public PagingPdfParser(File file, PageHandler handler) {
		this.file = file;
		this.handler = handler;
	}
	
	public void run() throws ParseException, CheckedOutOfMemoryError {
		PDDocument doc = null;
		try {
			try {
				doc = PDDocument.load(file);
			}
			catch (InvalidPasswordException e) {
				throw new ParseException(Msg.doc_pw_protected.get());
			}

			PagingStripper stripper = new PagingStripper();
			if (ProgramConf.Bool.PdfPreviewVisualOrder.get()) {
				stripper.setSortByPosition(true);
			}
			stripper.writeText(doc, writer);
		}
		catch (Exception e) {
			if (e instanceof ParseException) {
				throw (ParseException) e;
			} else {
				throw new ParseException(e);
			}
		}
		catch (UnsatisfiedLinkError e) {
			/*
			 * Bug #2342: PDFBox's initialization of PDDocument triggers AWT
			 * component initialization for image/graphics rendering. When AWT
			 * native libraries (libawt_xawt.so on Linux, awt.dll on Windows)
			 * cannot load due to missing dependencies, an UnsatisfiedLinkError
			 * is thrown during PDF preview operations.
			 *
			 * On Linux: Can occur when libawt_xawt.so's dependencies are
			 * missing (e.g., in minimal Java installations or when running
			 * with Java 17 that lacks X11 graphics dependencies).
			 *
			 * On Windows: Can occur when awt.dll's MSVC runtime dependencies
			 * (msvcp120.dll, msvcr120.dll) are missing.
			 *
			 * This is the preview counterpart to bug #2357 (which affected
			 * PDF indexing). Both bugs share the same root cause and are
			 * similar to bugs #2361, #2398, #2404, #2419, and #2441, where
			 * UnsatisfiedLinkError prevented graceful error handling.
			 *
			 * By catching and converting to ParseException, we allow the
			 * preview panel to display an error message rather than crashing
			 * the application.
			 */
			throw new ParseException(e);
		}
		catch (NoClassDefFoundError e) {
			// Check if this error was caused by OutOfMemoryError during class initialization
			Throwable cause = e.getCause();
			if (cause instanceof ExceptionInInitializerError) {
				Throwable rootCause = cause.getCause();
				if (rootCause instanceof OutOfMemoryError) {
					throw new CheckedOutOfMemoryError((OutOfMemoryError) rootCause);
				}
			}
			// If not OOM-related, treat as a general parse exception
			throw new ParseException(e);
		}
		catch (ExceptionInInitializerError e) {
			/*
			 * Bug #2305: PDFBox's initialization of PDDocument can fail with
			 * ExceptionInInitializerError when encountering malformed or
			 * corrupted ICC color profiles in PDF files. This manifests as
			 * a NullPointerException in java.awt.color.ICC_Profile during
			 * static initialization of PDFBox color handling classes.
			 *
			 * Example error chain:
			 * - ExceptionInInitializerError in PDDocument.<clinit>
			 * - Caused by: NullPointerException in ICC_Profile.intFromBigEndian
			 *
			 * This typically occurs when:
			 * 1. PDF contains a malformed ICC color profile
			 * 2. Java's color management system encounters null profile data
			 * 3. PDFBox attempts to initialize color space handling
			 *
			 * We must also check if the root cause is OutOfMemoryError,
			 * which can occur during class initialization and should be
			 * handled specially (similar to bug #2423).
			 */
			Throwable rootCause = e.getCause();
			if (rootCause instanceof OutOfMemoryError) {
				throw new CheckedOutOfMemoryError((OutOfMemoryError) rootCause);
			}
			// If not OOM-related, treat as a general parse exception
			throw new ParseException(e);
		}
		catch (OutOfMemoryError e) {
			throw new CheckedOutOfMemoryError(e);
		}
		finally {
			Closeables.closeQuietly(doc);
		}
	}

	private class PagingStripper extends PDFTextStripper {
		public PagingStripper() throws IOException {
			super();
		}

		protected void endPage(PDPage page) throws IOException {
			StringBuffer buffer = writer.getBuffer();
			boolean stopped = handler.handlePage(buffer.toString());
			buffer.delete(0, buffer.length());
			if (stopped)
				setEndPage(0);
		}
	}

}
