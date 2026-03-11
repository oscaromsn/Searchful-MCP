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

import java.io.CharConversionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.odf.OpenDocumentParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import com.google.common.io.Closeables;

/**
 * @author Tran Nam Quang
 */
abstract class OpenOfficeParser extends FileParser {
	
	public static final class OpenOfficeWriterParser extends OpenOfficeParser {
		public OpenOfficeWriterParser() {
			super(Msg.filetype_odt.get(), "odt", "ott");
		}
	}
	
	public static final class OpenOfficeCalcParser extends OpenOfficeParser {
		public OpenOfficeCalcParser() {
			super(Msg.filetype_ods.get(), "ods", "ots");
		}
	}
	
	public static final class OpenOfficeDrawParser extends OpenOfficeParser {
		public OpenOfficeDrawParser() {
			super(Msg.filetype_odg.get(), "odg", "otg");
		}
	}
	
	public static final class OpenOfficeImpressParser extends OpenOfficeParser {
		public OpenOfficeImpressParser() {
			super(Msg.filetype_odp.get(), "odp", "otp");
		}
	}
	
	private static final Collection<String> types = MediaType.Col.application("zip");
	
	private final String typeLabel;
	private final Collection<String> extensions;
	
	private OpenOfficeParser(@NotNull String typeLabel, @NotNull String... extensions) {
		this.typeLabel = typeLabel;
		this.extensions = Arrays.asList(extensions);
	}
	
	protected final Collection<String> getExtensions() {
		return extensions;
	}
	
	public final String getTypeLabel() {
		return typeLabel;
	}
	
	protected final Collection<String> getTypes() {
		return types;
	}
	
	@Override
	protected final ParseResult parse(	@NotNull File file,
										@NotNull ParseContext context)
			throws ParseException {
		OpenDocumentParser tikaParser;
		try {
			tikaParser = new OpenDocumentParser();
		}
		catch (NoClassDefFoundError e) {
			/*
			 * Bug #2077: On some Java installations (especially Java 9+ with
			 * module system restrictions or incomplete classpaths), Tika's
			 * OpenDocumentParser initialization fails with NoClassDefFoundError
			 * for Apache Commons IO classes like CloseShieldInputStream.
			 * This occurs even when the classes exist in the classpath, likely
			 * due to Java module system classloading issues.
			 *
			 * Re-throw as ParseException to allow indexing to continue for
			 * other files.
			 */
			throw new ParseException(e);
		}
		BodyContentHandler contentHandler = new BodyContentHandler(-1);
		Metadata metadata = new Metadata();
		TikaInputStream in = null;
		try {
			/*
			 * Bug #1224 and others: Feeding the input file directly as an
			 * InputStream into the Tika parser will make the latter use a
			 * ZipInputStream, which crashes on some OpenDocument email
			 * attachments in Outlook PST files. As a workaround, make Tika use
			 * ZipFile instead by wrapping the input file in a TikaInputStream.
			 * See: https://sourceforge.net/p/docfetcher/bugs/1224/
			 *
			 * Note that we're using Paths.get(...) instead of the simpler
			 * file.toPath(), since the latter is not implemented for TFile
			 * objects.
			 */
			in = TikaInputStream.get(Paths.get(file.getPath()));
			tikaParser.parse(in, contentHandler, metadata, ParseService.tikaContext());
			ParseResult result = new ParseResult(contentHandler.toString())
				.setTitle(metadata.get(TikaCoreProperties.TITLE))
				.addAuthor(metadata.get(TikaCoreProperties.CREATOR))
				.addMiscMetadata(metadata.get(TikaCoreProperties.DESCRIPTION))
				.addMiscMetadata(metadata.get(TikaCoreProperties.TRANSITION_SUBJECT_TO_OO_SUBJECT))
				.addMiscMetadata(metadata.get(TikaCoreProperties.KEYWORDS));
			for (String name : metadata.names()) {
				if (name.startsWith("custom:")) {
					result.addMiscMetadata(metadata.get(name));
				}
			}
			return result;
		}
		catch (java.util.zip.ZipError e) {
			/*
			 * java.util.zip.ZipError is an Error (not Exception) that occurs
			 * at the native level when the JVM's ZIP library encounters corrupted
			 * or invalid ZIP entries. OpenDocument files (.odt, .ods, .odp, etc.)
			 * are ZIP files, so corrupted OpenDocument files can trigger this error
			 * when Tika tries to parse them. Since this is an Error rather than an
			 * Exception, it bypasses normal exception handling.
			 *
			 * Bug report:
			 * https://sourceforge.net/p/docfetcher/bugs/2188/
			 */
			String msg = "The file appears to be corrupted or invalid. "
					+ "Try opening it in LibreOffice to verify if it's a valid document.";
			throw new ParseException(msg, e);
		}
		catch (CharConversionException e) {
			if (matchesPasswordError(e)) {
				throw new ParseException(Msg.doc_pw_protected.get());
			}
			else {
				throw new ParseException(e);
			}
		}
		catch (IOException | SAXException | TikaException e) {
			throw new ParseException(e);
		}
		catch (RuntimeException e) {
			/*
			 * Tika throws an IllegalArgumentException if we give it an ordinary
			 * zip archive rather than an OpenDocument file.
			 */
			throw new ParseException(e);
		}
		finally {
			Closeables.closeQuietly(in);
		}
	}
	
	private static boolean matchesPasswordError(@Nullable Exception e) {
		/*
		 * Expected error messages:
		 * - "Invalid byte 1 of 1-byte UTF-8 sequence."
		 * - "Invalid byte 2 of 2-byte UTF-8 sequence."
		 * - "Invalid byte 2 of 3-byte UTF-8 sequence."
		 * - etc.
		 */
		//return e != null && e instanceof MalformedByteSequenceException;
		// MalformedByteSequenceException is internal API, so using
		// CharConversionException instead:
		return e != null && e instanceof CharConversionException;
	}

}
