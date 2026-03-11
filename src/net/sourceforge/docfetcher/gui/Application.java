/*******************************************************************************
 * Copyright (c) 2010, 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *	Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.index.MergePolicy;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import com.drew.lang.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.io.LineReader;
import com.google.common.io.Resources;

import net.sourceforge.docfetcher.Main;
import net.sourceforge.docfetcher.Py4jHandler;
import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.Msg;
import net.sourceforge.docfetcher.enums.ProgramConf;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.enums.SystemConf;
import net.sourceforge.docfetcher.gui.StatusBar.StatusBarPart;
import net.sourceforge.docfetcher.gui.filter.FileTypePanel;
import net.sourceforge.docfetcher.gui.filter.FilesizePanel;
import net.sourceforge.docfetcher.gui.filter.IndexPanel;
import net.sourceforge.docfetcher.gui.filter.ToolBarForm;
import net.sourceforge.docfetcher.gui.filter.TwoFormExpander;
import net.sourceforge.docfetcher.gui.filter.TwoFormExpander.MaximizedControl;
import net.sourceforge.docfetcher.gui.pref.PrefDialog;
import net.sourceforge.docfetcher.gui.preview.PreviewPanel;
import net.sourceforge.docfetcher.model.Cancelable;
import net.sourceforge.docfetcher.model.Daemon;
import net.sourceforge.docfetcher.model.FolderWatcher;
import net.sourceforge.docfetcher.model.IndexLoadingProblems;
import net.sourceforge.docfetcher.model.IndexLoadingProblems.CorruptedIndex;
import net.sourceforge.docfetcher.model.IndexLoadingProblems.OverflowIndex;
import net.sourceforge.docfetcher.model.IndexRegistry;
import net.sourceforge.docfetcher.model.LuceneIndex;
import net.sourceforge.docfetcher.model.index.IndexingQueue;
import net.sourceforge.docfetcher.model.index.Task.CancelAction;
import net.sourceforge.docfetcher.model.index.Task.CancelHandler;
import net.sourceforge.docfetcher.model.index.Task.IndexAction;
import net.sourceforge.docfetcher.model.parse.ParseService;
import net.sourceforge.docfetcher.model.parse.Parser;
import net.sourceforge.docfetcher.model.search.ResultDocument;
import net.sourceforge.docfetcher.util.AppUtil;
import net.sourceforge.docfetcher.util.CharsetDetectorHelper;
import net.sourceforge.docfetcher.util.ConfLoader;
import net.sourceforge.docfetcher.util.ConfLoader.Loadable;
import net.sourceforge.docfetcher.util.Event;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.UtilGui;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.collect.AlphanumComparator;
import net.sourceforge.docfetcher.util.collect.ListMap;
import net.sourceforge.docfetcher.util.gui.CocoaUIEnhancer;
import net.sourceforge.docfetcher.util.gui.FormDataFactory;
import net.sourceforge.docfetcher.util.gui.LazyImageCache;
import net.sourceforge.docfetcher.util.gui.dialog.InfoDialog;
import net.sourceforge.docfetcher.util.gui.dialog.ListConfirmDialog;
import net.sourceforge.docfetcher.util.gui.dialog.MultipleChoiceDialog;

public final class Application {

	// TODO post-release-1.1: review visibility of all DocFetcher classes

	/** The widths of the sashes in pixels */
	private static final int sashWidth = 5;

	private static volatile IndexRegistry indexRegistry;
	private static volatile FolderWatcher folderWatcher;
	@Nullable private static HotkeyHandler hotkeyHandler;
	private static File programConfFile;

	private static FilesizePanel filesizePanel;
	private static FileTypePanel fileTypePanel;
	private static IndexPanel indexPanel;

	private static volatile Shell shell;
	private static ThreePanelForm threePanelForm;
	private static SearchBar searchBar;
	private static ResultPanel resultPanel;
	private static PreviewPanel previewPanel;
	private static volatile StatusBarPart indexingStatus;
	private static SystemTrayHider systemTrayHider;
	private static StatusBar statusBar;
	@Nullable private static HintOverlay docfetcherProTip;
	private static boolean systemTrayShutdown = false;
	private static File settingsConfFile;
	
	private static boolean indexRegistryLoaded = false; // should only be accessed from SWT thread
	private static Runnable clearIndexLoadingMsg; // should only be accessed from SWT thread

	private Application() {
		throw new UnsupportedOperationException();
	}

	public static void main(String[] args) {
		/*
		 * Bug #3553412: Starting with Java 7, calling Arrays.sort can cause an
		 * IllegalArgumentException with the error message
		 * "Comparison method violates its general contract!". In DocFetcher,
		 * this happened on a PDF file with PDFBox 1.7.0. For background, see
		 * http://stackoverflow.com/questions/7849539/comparison-method
		 * -violates-its-general-contract-java-7-only
		 */
		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
		
		/*
		 * Load system constants; this should be the very first thing to do.
		 * We'll first try to load from the jar (normal use case), then from a
		 * file (we're inside the IDE).
		 */
		String systemConfName = "system-conf.txt";
		String systemConfPath = "dev/system-conf.txt";
		boolean success = ConfLoader.loadFromStreamOrFile(
			Main.class, SystemConf.class, systemConfName, systemConfPath);
		if (!success) {
			/*
			 * This is pretty bad, just give up here. Note that we cannot use
			 * the stacktrace utility methods at this point because we haven't
			 * loaded the constants in the utility class yet.
			 */
			Util.printErr("Couldn't find resource: " + systemConfName);
			System.exit(1);
		}

		AppUtil.Const.PROGRAM_NAME.set(SystemConf.Str.ProgramName.get());
		AppUtil.Const.PROGRAM_VERSION.set(SystemConf.Str.ProgramVersion.get());
		AppUtil.Const.PROGRAM_BUILD_DATE.set(SystemConf.Str.BuildDate.get());
		AppUtil.Const.USER_DIR_PATH.set(Util.USER_DIR_PATH);
		AppUtil.Const.IS_PORTABLE.set(SystemConf.Bool.IsPortable.get());
		AppUtil.Const.IS_DEVELOPMENT_VERSION.set(SystemConf.Bool.IsDevelopmentVersion.get());

		Msg.loadFromDisk();
		Msg.setCheckEnabled(false);
		AppUtil.Messages.system_error.set(Msg.system_error.get());
		AppUtil.Messages.confirm_operation.set(Msg.confirm_operation.get());
		AppUtil.Messages.invalid_operation.set(Msg.invalid_operation.get());
		AppUtil.Messages.program_died_stacktrace_written.set(Msg.report_bug.get());
		AppUtil.Messages.program_running_launch_another.set(Msg.program_running_launch_another.get());
		AppUtil.Messages.ok.set(Msg.ok.get());
		AppUtil.Messages.cancel.set(Msg.cancel.get());
		Msg.setCheckEnabled(true);
		AppUtil.Messages.checkInitialized();

		// Path overrides
		File confPathOverride = null;
		File swtPathOverride = null;
		try {
			File pathsFile;

			// For non-portable macOS version, use ~/.docfetcher/paths.txt
			// (inside app bundle is not writable due to code signing)
			if (Util.IS_MAC_OS_X && !AppUtil.isPortable()) {
				File appDataDir = AppUtil.getAppDataDir();
				pathsFile = new File(appDataDir, "paths.txt");

				// Copy from app bundle if it doesn't exist yet
				if (!pathsFile.exists()) {
					File bundlePathsFile = new File("../Resources/misc", "paths.txt");
					if (bundlePathsFile.exists()) {
						Files.copy(bundlePathsFile, pathsFile);
					}
				}
			} else {
				pathsFile = new File("misc", "paths.txt");
			}

			Properties pathProps = CharsetDetectorHelper.load(pathsFile);
			confPathOverride = toFile(pathProps, "settings");
			IndexRegistry.indexPathOverride = toFile(pathProps, "indexes");
			swtPathOverride = toFile(pathProps, "swt");
		}
		catch (IOException e1) {
			// Ignore
			if (!SystemConf.Bool.IsDevelopmentVersion.get()) {
				e1.printStackTrace();
			}
		}

		/* This method call must be placed *before* any SWT access. This means
		 * it must be placed *before* the loadSettingsConf call further below,
		 * as the latter will access SWT via the SWT.CTRL and SWT.F8 constants
		 * to set the default value for the global hotkey. */
		configureSwtLibraryPath(swtPathOverride);

		// Load program configuration and preferences
		programConfFile = loadProgramConf(confPathOverride);
		settingsConfFile = loadSettingsConf(confPathOverride);
		
		// Update indexes in headless mode
		if (args.length >= 1 && args[0].equals("--update-indexes")) {
			loadIndexRegistryHeadless(getIndexParentDir(IndexRegistry.indexPathOverride));
			return;
		}

		// Check single instance
		if (ProgramConf.Bool.CheckSingleInstance.get() && !AppUtil.checkSingleInstance())
			return;
		
		checkMultipleDocFetcherJars();

		// Determine shell title
		String shellTitle;
		if (SystemConf.Bool.IsDevelopmentVersion.get())
			shellTitle = SystemConf.Str.ProgramName.get();
		else
			shellTitle = ProgramConf.Str.AppName.get();

		// Load index registry; create display and shell
		Display.setAppName(shellTitle); // must be called *before* the display is created
		Display display = new Display();
		AppUtil.setDisplay(display);
		shell = new Shell(display);
		loadIndexRegistry(shell, getIndexParentDir(IndexRegistry.indexPathOverride));

		// Load images
		LazyImageCache lazyImageCache = new LazyImageCache(
			display, AppUtil.getImageDir());
		Img.initialize(lazyImageCache);
		lazyImageCache.reportMissingFiles(
			shell, Img.class, Msg.missing_image_files.get());

		// Set shell icons, must be done *after* loading the images
		shell.setImages(new Image[] {
			Img.DOCFETCHER_16.get(),
			Img.DOCFETCHER_24.get(),
			Img.DOCFETCHER_32.get(),
			Img.DOCFETCHER_48.get(),
			Img.DOCFETCHER_64.get(),
			Img.DOCFETCHER_128.get()});

		// Set default uncaught exception handler
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, final Throwable e) {
				handleCrash(e);
			}
		});

		SettingsConf.ShellBounds.MainWindow.bind(shell);
		SettingsConf.Bool.MainShellMaximized.bindMaximized(shell);
		shell.setLayout(new FormLayout());
		shell.setText(shellTitle);

		initCocoaMenu(display);
		initSystemTrayHider();
		initThreePanelForm();
		initStatusBar();
		initHotkey();
		initGlobalKeys(display);
		
		if (SettingsConf.Bool.ShowDocFetcherProTip.get()) {
			showDocFetcherProTip();
		}
		
		new SearchQueue(
			searchBar, filesizePanel, fileTypePanel, indexPanel, resultPanel,
			statusBar);

		FormDataFactory fdf = FormDataFactory.getInstance();
		fdf.bottom().left().right().applyTo(statusBar);
		fdf.top().bottom(statusBar).applyTo(threePanelForm);

		// Move focus to search text field
		searchBar.setFocus();

		// Try to show the manual in the embedded browser
		boolean showManualHint = true;
		if (SettingsConf.Bool.ShowManualOnStartup.get()
				&& SettingsConf.Bool.ShowPreviewPanel.get()) {
			File file = ManualLocator.getManualFile();
			if (file == null) {
				showManualHint = false;
				/*
				 * In the development version, don't show the following warning
				 * message, as the manual under dist/help may not have been
				 * generated yet.
				 */
				if (!SystemConf.Bool.IsDevelopmentVersion.get()) {
					String msg = Msg.file_not_found.get() + "\n" + SystemConf.Str.ProgramName.get() + "_Manual.html";
					AppUtil.showError(msg, true, true);
				}
			}
			else if (previewPanel.setHtmlFile(file)) {
				showManualHint = false;
			}
		}
		if (showManualHint) {
			String msg = Msg.press_f1_for_help.get();
			statusBar.getLeftPart().setContents(Img.HELP.get(), msg);
		}

		// Open Py4j Gateway Server
		if(ProgramConf.Bool.PythonApiEnabled.get())
            Py4jHandler.openGatewayServer();

		shell.addShellListener(new ShellAdapter() {
			public void shellClosed(final ShellEvent e) {
				handleShellClosed(e);
			}
		});

		shell.open();
		while (!shell.isDisposed()) {
			try {
				if (!display.readAndDispatch())
					display.sleep();
			}
			catch (Throwable t) {
				handleCrash(t);
			}
		}

		// Close Py4j Gateway Server
		if(ProgramConf.Bool.PythonApiEnabled.get())
			Py4jHandler.shutdownGatewayServer();

		/*
		 * Do not set this to null; the index registry loading thread must be
		 * able to see that the display was disposed.
		 */
		display.dispose();
		saveSettingsConfFile();
	}
	
	@Nullable
	private static File toFile(@NotNull Properties props, @NotNull String key) {
		String value = props.getProperty(key);
		if (value == null)
			return null;
		Pattern homePattern = Pattern.compile("\\$\\{user\\.home}(?:[\\\\/](.*))?");
		Matcher m = homePattern.matcher(value);
		if (m.matches()) {
			if (m.group(1) == null || m.group(1).trim().isEmpty())
				return new File(Util.USER_HOME_PATH);
			return new File(Util.USER_HOME_PATH, m.group(1));
		}
		return Util.getCanonicalFile(value);
	}
	
	private static void handleCrash(@NotNull Throwable t) {
		// Filter out benign SWTException caused by race condition between widget
		// disposal and Windows accessibility queries. This can happen when
		// accessibility features (e.g. screen readers) try to query widget
		// properties after the widget has been disposed. Only filter if the
		// exception is coming from the accessibility callback path.
		// See bug #2395.
		if (t instanceof SWTException) {
			SWTException swtEx = (SWTException) t;
			if (swtEx.code == SWT.ERROR_WIDGET_DISPOSED ||
				(swtEx.getMessage() != null && swtEx.getMessage().contains("Widget is disposed"))) {
				// Check if this is from accessibility callbacks
				String stackTrace = Throwables.getStackTraceAsString(t);
				if (stackTrace.contains("org.eclipse.swt.accessibility.Accessible")) {
					// Silently ignore - this is a benign accessibility race condition
					return;
				}
			}

			// Filter out benign SWTException caused by GTK/SWT library bug during
			// widget disposal. This manifests as "g_object_get_qdata returned unexpected
			// index value" error on Linux systems with certain GTK themes. This is a
			// known issue in Eclipse SWT's GTK implementation that occurs during
			// shell/widget disposal and is outside DocFetcher's control.
			// See bug #2369.
			if (swtEx.getMessage() != null && swtEx.getMessage().contains("g_object_get_qdata")) {
				String stackTrace = Throwables.getStackTraceAsString(t);
				// Only filter if this is happening during widget disposal/deregistration
				if (stackTrace.contains("Display.removeWidget") ||
					stackTrace.contains("Widget.deregister") ||
					stackTrace.contains("Widget.releaseWidget")) {
					// Silently ignore - this is a benign GTK/SWT library bug
					return;
				}
			}
		}

		for (OutOfMemoryError e : Iterables.filter(Throwables.getCausalChain(t), OutOfMemoryError.class)) {
			UtilGui.showOutOfMemoryMessage(shell, e);
			return;
		}
		if (t instanceof MergePolicy.MergeException) {
			String msg = t.getMessage();
			msg += "\n\nPlease ensure no other programs are accessing or locking the index files.";
			AppUtil.showError(msg, true, false);
			return;
		}
		AppUtil.showStackTrace(t);
	}

	private static void saveSettingsConfFile() {
		/*
		 * Try to save the settings. This may not be possible, for example when
		 * the user has burned the program onto a CD-ROM.
		 */
		if (ProgramConf.Bool.SaveSettings.get() && settingsConfFile.canWrite()) {
			try {
				String comment = SettingsConf.loadHeaderComment();
				ConfLoader.save(settingsConfFile, SettingsConf.class, comment);
			}
			catch (IOException e) {
				boolean displayExists = (Display.getCurrent() != null);
				handleConfigurationIOException(e, settingsConfFile, displayExists);
			}
		}
	}
	
	/**
	 * Checks for multiple loaded DocFetcher jars. This should be called before
	 * creating the display.
	 */
	private static void checkMultipleDocFetcherJars() {
		if (SystemConf.Bool.IsDevelopmentVersion.get())
			return;
		if (!(AppUtil.isPortable() || Util.IS_WINDOWS))
			return;
		Pattern p = Pattern.compile("net\\.sourceforge\\.docfetcher.*\\.jar");
		List<File> dfJars = new LinkedList<File>();
		for (File jarFile : Util.listFiles(new File("lib")))
			if (p.matcher(jarFile.getName()).matches())
				dfJars.add(jarFile);
		if (dfJars.size() == 1)
			return;
		assert !dfJars.isEmpty();
		String msg = Msg.multiple_docfetcher_jars.format(Util.join("\n", dfJars));
		AppUtil.showErrorOnStart(msg, false);
	}

	private static void initGlobalKeys(@NotNull Display display) {
		/*
		 * This filter must be added to SWT.KeyDown rather than SWT.KeyUp,
		 * otherwise we won't be able to prevent the events from propagating
		 * further.
		 */
		display.addFilter(SWT.KeyDown, new Listener() {
			public void handleEvent(org.eclipse.swt.widgets.Event e) {
				// Disable global keys when the main shell is inactive
				if (Display.getCurrent().getActiveShell() != shell)
					return;

				e.doit = false;
				int m = e.stateMask;
				int k = e.keyCode;

				if (k == SWT.F1) {
					showManual();

					// Clear "Press F1" help message from status bar
					String msg = Msg.press_f1_for_help.get();
					StatusBarPart statusBarPart = statusBar.getLeftPart();
					if (msg.equals(statusBarPart.getText()))
						statusBarPart.setContents(null, "");
				}
				/* Do not swallow the Shift + F shortcut, otherwise it will be
				 * impossible to type a capital F in the search field. */
				else if (m == SWT.MOD1 && k == 'f') {
					searchBar.setFocus();
				}
				else {
					e.doit = true;
				}
			}
		});
	}
	
	@NotNull
	private static File getIndexParentDir(@Nullable File pathOverride) {
		File indexParentDir;
		if (SystemConf.Bool.IsDevelopmentVersion.get()) {
			indexParentDir = new File("bin/indexes");
		}
		else if (pathOverride != null && !pathOverride.isFile()) {
			pathOverride.mkdirs();
			indexParentDir = pathOverride;
		}
		else {
			File appDataDir = AppUtil.getAppDataDir();
			if (SystemConf.Bool.IsPortable.get())
				indexParentDir = new File(appDataDir, "indexes");
			else
				indexParentDir = appDataDir;
		}
		indexParentDir.mkdirs();
		return indexParentDir;
	}

	private static void loadIndexRegistry(@NotNull final Shell mainShell, @NotNull File indexParentDir) {
		UtilGui.assertSwtThread();
		final Display display = mainShell.getDisplay();

		int cacheCapacity = ProgramConf.Int.UnpackCacheCapacity.get();
		int reporterCapacity = ProgramConf.Int.MaxLinesInProgressPanel.get();
		indexRegistry = new IndexRegistry(
			indexParentDir, cacheCapacity, reporterCapacity);
		final Daemon daemon = new Daemon(indexRegistry);
		IndexingQueue queue = indexRegistry.getQueue();

		queue.evtWorkerThreadTerminated.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				daemon.writeIndexesToFile();
			}
		});

		/*
		 * Remove indexing hint from the status bar when the task
		 * queue has been emptied. This covers those situations
		 * where the indexing dialog has been minimized to the
		 * status bar and the last task in the queue has just been
		 * completed.
		 */
		queue.evtQueueEmpty.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				/*
				 * Bug #3485598: The indexing status widget can be null at this
				 * point. Possible explanation: An indexing update was issued by
				 * the DocFetcher daemon and finished before the GUI was fully
				 * initialized.
				 */
				if (indexingStatus == null)
					return;
				UtilGui.runAsyncExec(indexingStatus.getControl(), new Runnable() {
					public void run() {
						indexingStatus.setVisible(false);
					}
				});
			}
		});

		new Thread(Application.class.getName() + " (load index registry)") {
			public void run() {
				try {
					final IndexLoadingProblems loadingProblems = indexRegistry.load(new Cancelable() {
						public boolean isCanceled() {
							return display.isDisposed();
						}
					});
					
					// Program may have been shut down while it was loading the indexes
					if (display.isDisposed())
						return;

					/*
					 * Install folder watches on the user's document folders.
					 *
					 * This should be done *after* the index registry is loaded:
					 * The index registry will try to install its own folder
					 * watch during loading, and if we set up this folder
					 * watcher before loading the registry, we might take up all
					 * the allowed watches, so that there's none left for the
					 * registry.
					 */
					folderWatcher = new FolderWatcher(indexRegistry);

					// Show error message when watch limit is reached
					folderWatcher.evtWatchLimitError.add(new Event.Listener<String>() {
						public void update(final String eventData) {
							UtilGui.runAsyncExec(mainShell, new Runnable() {
								public void run() {
									InfoDialog dialog = new InfoDialog(mainShell);
									dialog.setTitle(Msg.system_error.get());
									dialog.setImage(SWT.ICON_ERROR);
									dialog.setText(eventData);
									dialog.open();
								}
							});
						}
					});

					// Must be called *after* the indexes have been loaded
					daemon.enqueueUpdateTasks();
					
					// Confirm deletion of obsolete files inside the index
					// folder
					if (ProgramConf.Bool.ReportObsoleteIndexFiles.get()
							&& !loadingProblems.getObsoleteFiles().isEmpty()) {
						UtilGui.runSyncExec(mainShell, new Runnable() {
							public void run() {
								reportObsoleteIndexFiles(
									mainShell,
									indexRegistry.getIndexParentDir(),
									loadingProblems.getObsoleteFiles());
							}
						});
					}

					// Show error messages if some indexes couldn't be loaded
					if (!loadingProblems.getCorruptedIndexes().isEmpty()) {
						StringBuilder msg = new StringBuilder(Msg.corrupted_indexes.get());
						for (CorruptedIndex index : loadingProblems.getCorruptedIndexes()) {
							msg.append("\n\n");
							String indexName = index.index.getRootFolder().getDisplayName();
							String errorMsg = index.ioException.getMessage();
							msg.append(Msg.index.format(indexName));
							msg.append("\n");
							msg.append(Msg.error.format(errorMsg));
						}
						AppUtil.showError(msg.toString(), true, false);
					}
					if (!loadingProblems.getOverflowIndexes().isEmpty()) {
						StringBuilder msg = new StringBuilder(Msg.folder_hierarchy_too_deep_on_loading.get());
						msg.append("\n");
						for (OverflowIndex index : loadingProblems.getOverflowIndexes()) {
							msg.append("\n");
							msg.append(index.file.getName());
						}
						AppUtil.showError(msg.toString(), true, false);
					}
				}
				catch (IOException e) {
					if (display.isDisposed())
						AppUtil.showStackTraceInOwnDisplay(e);
					else
						AppUtil.showStackTrace(e);
				}
				finally {
					UtilGui.runAsyncExec(mainShell, new Runnable() {
						public void run() {
							indexRegistryLoaded = true;
							if (clearIndexLoadingMsg != null)
								clearIndexLoadingMsg.run();
						}
					});
				}
			}
		}.start();
	}
	
	private static void loadIndexRegistryHeadless(@NotNull File indexParentDir) {
		int cacheCapacity = ProgramConf.Int.UnpackCacheCapacity.get();
		int reporterCapacity = ProgramConf.Int.MaxLinesInProgressPanel.get();
		indexRegistry = new IndexRegistry(
			indexParentDir, cacheCapacity, reporterCapacity);
		
		try {
			indexRegistry.load(Cancelable.nullCancelable);
			final IndexingQueue queue = indexRegistry.getQueue();
			
			queue.evtQueueEmpty.add(new Event.Listener<Void>() {
				public void update(Void eventData) {
					indexRegistry.getSearcher().shutdown();
					queue.shutdown(new CancelHandler() {
						public CancelAction cancel() {
							return CancelAction.KEEP;
						}
					});
				}
			});
			
			for (LuceneIndex index : indexRegistry.getIndexes())
				queue.addTask(index, IndexAction.UPDATE);
		}
		catch (IOException e) {
			Util.printErr(e);
		}
	}
	
	private static void reportObsoleteIndexFiles(	@NotNull Shell mainShell,
	                                             	@NotNull File indexDir,
													@NotNull List<File> filesToDelete) {
		ListConfirmDialog dialog = new ListConfirmDialog(mainShell, SWT.ICON_INFORMATION);
		dialog.setTitle(Msg.confirm_operation.get());
		dialog.setText(Msg.delete_obsolete_index_files.format(indexDir.getPath()));
		dialog.setButtonLabels(Msg.delete_bt.get(), Msg.keep.get());
		
		filesToDelete = new ArrayList<File>(filesToDelete);
		Collections.sort(filesToDelete, new Comparator<File>() {
			public int compare(File o1, File o2) {
				boolean d1 = o1.isDirectory();
				boolean d2 = o2.isDirectory();
				if (d1 && !d2)
					return -1;
				if (!d1 && d2)
					return 1;
				return AlphanumComparator.ignoreCaseInstance.compare(o1.getName(), o2.getName());
			}
		});
		
		for (File file : filesToDelete) {
			Image img = (file.isDirectory() ? Img.FOLDER : Img.FILE).get();
			dialog.addItem(img, file.getName());
		}
		
		dialog.evtLinkClicked.add(new Event.Listener<String>() {
			public void update(String eventData) {
				UtilGui.launch(eventData);
			}
		});
		
		if (dialog.open()) {
			for (File file : filesToDelete) {
				try {
					Util.deleteRecursively(file);
				}
				catch (IOException e) {
					Util.printErr(e);
				}
			}
		}
	}

	/**
	 * Handles IOException during configuration file loading/creation with.
	 */
	private static void handleConfigurationIOException(@NotNull IOException e, @NotNull File confFile, boolean displayExists) {
		// Check for permission errors by testing if the directory exists but is
		// not writable. Use a reliable write test instead of canWrite() which
		// is unreliable on Windows with UAC-protected directories.
		File parentDir = Util.getParentFile(confFile);
		boolean isPermissionError = parentDir != null &&
			parentDir.exists() &&
			!AppUtil.canWriteToDirectory(parentDir);

		if (isPermissionError && SystemConf.Bool.IsPortable.get()) {
			String programDir = new File(Util.USER_DIR_PATH).getAbsolutePath();
			StringBuilder msg = new StringBuilder();
			msg.append("The portable version of " + SystemConf.Str.ProgramName.get() + " cannot write to the following location:\n\n");
			msg.append(confFile.getAbsolutePath() + "\n\n");
			msg.append("This is likely because the program is installed in a protected system directory:\n\n");
			msg.append(programDir + "\n\n");
			msg.append("Possible solutions:\n\n");
			msg.append("1. Use the official installer instead of the portable version. The installer will place files in the appropriate user directories. (Recommended)\n\n");
			msg.append("2. Move the portable version to a writable location such as your Desktop, Documents folder, or a folder in your user directory.\n\n");
			msg.append("3. Run the program as Administrator. (Not recommended for security reasons.)\n\n");
			msg.append("Technical details: " + e.getClass().getSimpleName() + ": " + e.getMessage());

			AppUtil.showErrorOnStart(msg.toString(), false);
		}
		else {
			// Bug #2410: Use the Display state passed from caller to choose appropriate error display method
			if (displayExists) {
				// Display is active, use it to show the error
				AppUtil.showStackTrace(e);
			} else {
				// No display (called after dispose), create own display for error
				AppUtil.showStackTraceInOwnDisplay(e);
			}
		}
	}

	private static File loadProgramConf(@Nullable File pathOverride) {
		AppUtil.checkConstInitialized();
		AppUtil.ensureNoDisplay();

		File confFile;
		if (SystemConf.Bool.IsDevelopmentVersion.get()) {
			confFile = new File("dist/program-conf.txt");
		}
		else if (pathOverride != null && !pathOverride.isFile()) {
			pathOverride.mkdirs();
			confFile = new File(pathOverride, "program-conf.txt");
		}
		else {
			File appDataDir = AppUtil.getAppDataDir();
			confFile = new File(appDataDir, "conf/program-conf.txt");
		}

		try {
			List<Loadable> notLoaded = ConfLoader.load(
				confFile, ProgramConf.class, false);
			if (!notLoaded.isEmpty()) {
				if (SystemConf.Bool.IsDevelopmentVersion.get()) {
					List<String> entryNames = new ArrayList<>(notLoaded.size());
					for (Loadable entry : notLoaded)
						entryNames.add("  " + entry.name());
					String msg = Msg.entries_missing.format(confFile.getName());
					msg += "\n" + Joiner.on("\n").join(entryNames);
					AppUtil.showErrorOnStart(msg, false);
				}
				else {
					regenerateConfFile(confFile, ProgramConf.class);
				}
			}
		}
		catch (FileNotFoundException e) {
			regenerateConfFile(confFile, ProgramConf.class);
		}
		catch (IOException e) {
			handleConfigurationIOException(e, confFile, false); // No display yet
		}
		return confFile;
	}

	/**
	 * Regenerates the given conf file from an internal template, replacing
	 * values in the template with values loaded from the given container class
	 * if possible.
	 * <p>
	 * Note: In case of the non-portable version, the program-conf.txt file will
	 * be missing when the program is started for the first time.
	 */
	private static void regenerateConfFile(	File confFile,
											Class<?> containerClass) {
		Pattern linePat = Pattern.compile("(\\w+)\\s*=.*");
		try {
			URL url = Resources.getResource(Main.class, confFile.getName());
			LineReader lineReader = new LineReader(
				new StringReader(Resources.toString(url, Charsets.UTF_8))
			);
			List<String> outLines = new ArrayList<>();
			while (true) {
				String line = lineReader.readLine();
				if (line == null) {
					break;
				}
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					outLines.add(line);
					continue;
				}
				Matcher mat = linePat.matcher(line);
				if (!mat.matches()) {
					outLines.add(line);
					continue;
				}
				String key = mat.group(1);
				String value = ConfLoader.getRawValue(key, containerClass);
				if (value != null) {
					if (value.trim().isEmpty()) {
						outLines.add(String.format("%s =", key));
					}
					else {
						outLines.add(String.format("%s = %s", key, value));
					}
				}
				else {
					outLines.add(line);
				}
			}
			String outStr = Util.join(Util.LS, outLines);
			Util.getParentFile(confFile).mkdirs();
			Files.write(outStr, confFile, Charsets.UTF_8);
		}
		catch (IOException e1) {
			handleConfigurationIOException(e1, confFile, false);
		}
		catch (Exception e1) {
			AppUtil.showStackTraceInOwnDisplay(e1);
		}
	}
	
	private static File loadSettingsConf(@Nullable File pathOverride) {
		AppUtil.checkConstInitialized();
		AppUtil.ensureNoDisplay();

		File confFile;
		if (SystemConf.Bool.IsDevelopmentVersion.get()) {
			confFile = new File("bin/settings-conf.txt");
		}
		else if (pathOverride != null && !pathOverride.isFile()) {
			pathOverride.mkdirs();
			confFile = new File(pathOverride, "settings-conf.txt");
		}
		else {
			File appDataDir = AppUtil.getAppDataDir();
			confFile = new File(appDataDir, "conf/settings-conf.txt");
		}

		try {
			ConfLoader.load(confFile, SettingsConf.class, true);
		}
		catch (IOException e) {
			handleConfigurationIOException(e, confFile, false); // No display yet
		}
		return confFile;
	}

	private static Control createLeftPanel(Composite parent) {
		final Composite comp = new Composite(parent, SWT.NONE);
		comp.setLayout(new FormLayout());

		ToolBarForm filesizeForm = new ToolBarForm(comp) {
			protected Control createToolBar(Composite parent) {
				final Label item = new Label(parent, SWT.NONE);
				Image image = SettingsConf.Bool.FilesizeFilterMaximized.get()
					? Img.MINIMIZE.get()
					: Img.MAXIMIZE.get();
				item.setImage(image);
				item.addMouseListener(new MouseAdapter() {
					public void mouseUp(MouseEvent e) {
						boolean isVisible = !isContentsVisible();
						setContentsVisible(isVisible);
						Image image = isVisible
							? Img.MINIMIZE.get()
							: Img.MAXIMIZE.get();
						item.setImage(image);
						comp.layout();
						SettingsConf.Bool.FilesizeFilterMaximized.set(isVisible);
					}
				});
				item.setCursor(item.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
				UtilGui.addMouseHighlighter(item);
				return item;
			}

			protected Control createContents(Composite parent) {
				filesizePanel = new FilesizePanel(parent);
				return filesizePanel.getControl();
			}
		};
		filesizeForm.setText(Msg.min_max_filesize.get());
		filesizeForm.setContentsVisible(SettingsConf.Bool.FilesizeFilterMaximized.get());

		final TwoFormExpander expander = new TwoFormExpander(comp) {
			protected Control createFirstContents(Composite parent) {
				// TODO websearch: Load parser states from file, save parser states to file?
				List<Parser> parsers = ParseService.getParsers();
				ListMap<Parser, Boolean> map = ListMap.create(parsers.size());
				for (Parser parser : parsers)
					map.add(parser, true);
				fileTypePanel = new FileTypePanel(parent, map);
				return fileTypePanel.getControl();
			}
			protected Control createSecondContents(Composite parent) {
				indexPanel = new IndexPanel(parent, indexRegistry);
				indexPanel.evtIndexingDialogMinimized.add(new Event.Listener<Rectangle>() {
					public void update(Rectangle eventData) {
						moveIndexingDialogToStatusBar(eventData);
					}
				});
				return indexPanel.getControl();
			}
			protected void onMaximizationChanged() {
				// Save maximization states
				MaximizedControl maxControl = getMaximizedControl();
				boolean topMax = maxControl == MaximizedControl.TOP;
				boolean bottomMax = maxControl == MaximizedControl.BOTTOM;
				SettingsConf.Bool.TypesFilterMaximized.set(topMax);
				SettingsConf.Bool.LocationFilterMaximized.set(bottomMax);
			}
		};
		expander.setTopText(Msg.document_types.get());
		if (indexRegistryLoaded) {
			expander.setBottomText(Msg.search_scope.get());
		}
		else {
			expander.setBottomText(Msg.search_scope.get() + " (" + Msg.loading.get() + ")");
			clearIndexLoadingMsg = new Runnable() {
				public void run() {
					UtilGui.assertSwtThread();
					expander.setBottomText(Msg.search_scope.get());
				}
			};
		}
		expander.setSashWidth(sashWidth);
		
		// Restore sash weights and maximization states
		expander.setSashWeights(SettingsConf.IntArray.FilterSash.get());
		if (SettingsConf.Bool.TypesFilterMaximized.get())
			expander.setMaximizedControl(MaximizedControl.TOP);
		if (SettingsConf.Bool.LocationFilterMaximized.get())
			expander.setMaximizedControl(MaximizedControl.BOTTOM);

		// Save sash weights
		expander.getFirstControl().addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				// Run in asyncExec to make sure both controls have been resized
				UtilGui.runAsyncExec(expander, new Runnable() {
					public void run() {
						MaximizedControl maxControl = expander.getMaximizedControl();
						if (maxControl != MaximizedControl.NONE)
							return;
						int[] weights = expander.getSashWeights();
						SettingsConf.IntArray.FilterSash.set(weights);
					}
				});
			}
		});

		FormDataFactory fdf = FormDataFactory.getInstance();
		fdf.margin(0).left().top().right().applyTo(filesizeForm);
		fdf.top(filesizeForm, 5).bottom().applyTo(expander);

		return comp;
	}

	private static Control createRightTopPanel(Composite parent) {
		Composite comp = new Composite(parent, SWT.NONE);
		searchBar = new SearchBar(comp, programConfFile);
		searchBar.evtOKClicked.add(new Event.Listener<Void> () {
		    public void update(Void eventData) {
		    	saveSettingsConfFile();
		    }
		});
		resultPanel = new ResultPanel(comp);

		comp.setLayout(new FormLayout());
		FormDataFactory fdf = FormDataFactory.getInstance();
		fdf.margin(0).top().left().right().applyTo(searchBar.getControl());
		fdf.top(searchBar.getControl()).bottom().applyTo(resultPanel.getControl());

		searchBar.evtHideInSystemTray.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				systemTrayHider.hide();
			}
		});

		searchBar.evtOpenManual.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				showManual();
			}
		});

		resultPanel.evtSelection.add(new Event.Listener<List<ResultDocument>>() {
			public void update(List<ResultDocument> eventData) {
				if (!eventData.isEmpty())
					previewPanel.setPreview(eventData.get(0));
			}
		});

		resultPanel.evtHideInSystemTray.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				systemTrayHider.hide();
			}
		});

		return comp;
	}

	private static void moveIndexingDialogToStatusBar(@NotNull Rectangle src) {
		indexingStatus.setVisible(true);
		Rectangle dest = indexingStatus.getBounds();
		dest = shell.getDisplay().map(shell, null, dest);
		MovingBox movingBox = new MovingBox(shell, src, dest, 0.2, 40);
		movingBox.start();
	}

	/*
	 * Sets up system tray hiding.
	 */
	private static void initSystemTrayHider() {
		systemTrayHider = new SystemTrayHider(shell);

		final ResultDocument[] lastDoc = new ResultDocument[1];

		systemTrayHider.evtHiding.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				/*
				 * If DocFetcher is sent to the system tray while being
				 * maximized and showing a big file on the preview panel, one
				 * would experience an annoying delay once the program returns
				 * from the system tray. The workaround is to clear the preview
				 * panel before going to the system tray and reset it when we
				 * come back.
				 */
				lastDoc[0] = previewPanel.clear();
			}
		});

		systemTrayHider.evtRestored.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				if (lastDoc[0] != null) {
					previewPanel.setPreview(lastDoc[0]);
					lastDoc[0] = null;
				}
				searchBar.setFocus();
			}
		});

		systemTrayHider.evtShutdown.add(new Event.Listener<Void>() {
			public void update(Void eventData) {
				systemTrayShutdown = true;
				shell.close();
			}
		});
	}

	private static void initCocoaMenu(@NotNull Display display) {
		if (!Util.IS_MAC_OS_X)
			return;

		CocoaUIEnhancer cocoaUIEnhancer = new CocoaUIEnhancer(ProgramConf.Str.AppName.get());
		cocoaUIEnhancer.hookApplicationMenu(display, new Listener() {
			public void handleEvent(org.eclipse.swt.widgets.Event event) {
				shell.close(); // Bug #942
			}
		}, new Runnable() {
			public void run() {
				// TODO post-release-1.1: Show an about dialog? Or maybe open a manual page?
				String name = SystemConf.Str.ProgramName.get();
				String version = SystemConf.Str.ProgramVersion.get();
				AppUtil.showInfo(name + " " + version);
			}
		}, new Runnable() {
			public void run() {
				PrefDialog prefDialog = new PrefDialog(shell, programConfFile);
				prefDialog.evtOKClicked.add(new Event.Listener<Void> () {
				    public void update(Void eventData) {
				    	saveSettingsConfFile();
				    }
				});
				prefDialog.open();
			}
		});
	}

	@NotNull
	private static void initThreePanelForm() {
		int filterPanelWidth = SettingsConf.Int.FilterPanelWidth.get();
		threePanelForm = new ThreePanelForm(shell, filterPanelWidth) {
			protected Control createFirstControl(Composite parent) {
				return createLeftPanel(parent);
			}
			protected Control createFirstSubControl(Composite parent) {
				return createRightTopPanel(parent);
			}
			protected Control createSecondSubControl(Composite parent) {
				previewPanel = new PreviewPanel(parent);
				previewPanel.evtHideInSystemTray.add(new Event.Listener<Void>() {
					public void update(Void eventData) {
						systemTrayHider.hide();
					}
				});
				previewPanel.evtSaveSettings.add(new Event.Listener<Void>() {
					@Override
					public void update(Void eventData) {
						saveSettingsConfFile();
					}
				});
				return previewPanel;
			}
		};

		threePanelForm.setSashWidth(sashWidth);
		threePanelForm.setSubSashWidth(sashWidth);

		// Restore visibility of filter panel and preview panel
		threePanelForm.setFirstControlVisible(SettingsConf.Bool.ShowFilterPanel.get());
		threePanelForm.setSecondSubControlVisible(SettingsConf.Bool.ShowPreviewPanel.get());

		// Restore orientation and weights of right sash
		boolean isVertical = SettingsConf.Bool.ShowPreviewPanelAtBottom.get();
		threePanelForm.setVertical(isVertical);
		threePanelForm.setSubSashWeights(getRightSashWeights(isVertical));

		// Store visibility of filter panel
		threePanelForm.evtFirstControlShown.add(new Event.Listener<Boolean>() {
			public void update(Boolean eventData) {
				SettingsConf.Bool.ShowFilterPanel.set(eventData);
			}
		});

		// Store width of filter panel
		final Control leftControl = threePanelForm.getFirstControl();
		leftControl.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				if (leftControl.isVisible()) {
					int width = leftControl.getSize().x;
					SettingsConf.Int.FilterPanelWidth.set(width);
				}
			}
		});

		final boolean[] ignoreControlResize = { false };

		// Store weights of right sash
		Control topRightControl = threePanelForm.getFirstSubControl();
		ControlListener rightControlListener = new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				if (!previewPanel.isVisible() || ignoreControlResize[0])
					return;
				int[] weights = threePanelForm.getSubSashWeights();
				if (threePanelForm.isVertical())
					SettingsConf.IntArray.RightSashVertical.set(weights);
				else
					SettingsConf.IntArray.RightSashHorizontal.set(weights);
			}
		};
		topRightControl.addControlListener(rightControlListener);
		previewPanel.addControlListener(rightControlListener);

		// Store visibility of preview panel
		threePanelForm.evtSecondSubControlShown.add(new Event.Listener<Boolean>() {
			public void update(Boolean eventData) {
				SettingsConf.Bool.ShowPreviewPanel.set(eventData);
			}
		});

		/*
		 * Temporarily deactivate storing the sash weights during sash
		 * orientation changes. Without this, we'd set the same sash weights for
		 * both orientations.
		 */
		threePanelForm.evtSubOrientationChanging.add(new Event.Listener<Boolean>() {
			public void update(Boolean isVertical) {
				ignoreControlResize[0] = true;
			}
		});

		// Store orientation of right sash; update weights
		threePanelForm.evtSubOrientationChanged.add(new Event.Listener<Boolean>() {
			public void update(Boolean isVertical) {
				SettingsConf.Bool.ShowPreviewPanelAtBottom.set(isVertical);
				threePanelForm.setSubSashWeights(getRightSashWeights(isVertical));
				ignoreControlResize[0] = false;
			}
		});
	}

	@NotNull
	private static int[] getRightSashWeights(boolean isVertical) {
		return isVertical
			? SettingsConf.IntArray.RightSashVertical.get()
			: SettingsConf.IntArray.RightSashHorizontal.get();
	}

	private static void initStatusBar() {
		statusBar = new StatusBar(shell) {
			public List<StatusBarPart> createRightParts(StatusBar statusBar) {
				indexingStatus = new StatusBarPart(statusBar, true);
				indexingStatus.setContents(Img.INDEXING.get(), Msg.indexing.get());
				indexingStatus.setVisible(false);

				indexPanel.evtIndexingDialogOpened.add(new Event.Listener<Void>() {
					public void update(Void eventData) {
						indexingStatus.setVisible(false);
					}
				});

				indexingStatus.evtClicked.add(new Event.Listener<Void>() {
					public void update(Void eventData) {
						indexPanel.openIndexingDialog();
					}
				});
				
				StatusBarPart proLink = new StatusBarPart(statusBar, false);
				proLink.setContents(null, "<a>" + Msg.try_docfetcher_pro.get() + "</a>");
				proLink.evtLinkClicked.add(new Event.Listener<Void> () {
					@Override
					public void update(Void eventData) {
						UtilGui.launch("https://docfetcherpro.com/comparison/");
						if (docfetcherProTip != null) {
							docfetcherProTip.shell.close();
						}
					}
				});

				List<StatusBarPart> parts = new ArrayList<StatusBarPart>(2);
				parts.add(indexingStatus);
				parts.add(proLink);
				return parts;
			}
		};
	}
	
	private static void showDocFetcherProTip() {
		String text = Msg.docfetcher_pro_tip.get();
		String linkText = Msg.dont_show_msg_again.get();
		final HintOverlay overlay = new HintOverlay(shell, text, linkText);
		
		overlay.evtLinkClicked.add(new Event.Listener<Void>() {
			@Override
			public void update(Void eventData) {
				overlay.shell.close();
			}
		});
		overlay.shell.addShellListener(new ShellAdapter() {
			@Override
			public void shellClosed(ShellEvent e) {
				/*
				 * This code is not run if the overlay is closed due to the
				 * parent shell being closed.
				 */
				SettingsConf.Bool.ShowDocFetcherProTip.set(false);
				docfetcherProTip = null;
			}
		});
		overlay.shell.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				// Prevent closing the overlay by pressing the escape key
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					e.doit = false;
				}
			}
		});
		
		final Runnable setLocation = new Runnable() {
			@Override
			public void run() {
				Point overlaySize = overlay.shell.getSize();
				List<StatusBarPart> parts = statusBar.getRightParts();
				if (parts.isEmpty()) {
					return;
				}
				Control link = parts.get(parts.size() - 1).getControl();
				Rectangle b = link.getBounds();
				b = link.getDisplay().map(link.getParent(), null, b);
				Point pt = new Point(b.x + b.width, b.y);
				Point pt2 = new Point(
					pt.x - overlaySize.x - 5, pt.y - overlaySize.y - 10);
				overlay.shell.setLocation(pt2);
			}
		};
		
		shell.getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				int width = Util.IS_WINDOWS ? 300 : 350;
				Point size = overlay.shell.computeSize(width, SWT.DEFAULT);
				overlay.shell.setSize(size);
				setLocation.run();
				overlay.open();
			}
		});
		
		final ControlAdapter listener = new ControlAdapter() {
			@Override
			public void controlMoved(ControlEvent e) {
				setLocation.run();
			}
			@Override
			public void controlResized(ControlEvent e) {
				/*
				 * With the asyncExec call below, this direct call is not
				 * necessary on Linux, but it is necessary on Windows. Without
				 * it, the overlay won't relocate until the parent shell has
				 * stopped resizing. Not tested whether thsi call is necessary
				 * on macOS.
				 */
				setLocation.run();
				/*
				 * This asyncExec is necessary for reacting to parent shell
				 * maximization and un-maximization. On Linux, for some reason
				 * it also makes the reaction to parent shell resizing immediate
				 * instead of "wobbly".
				 */
				shell.getDisplay().asyncExec(setLocation);
			}
		};
		shell.addControlListener(listener);
		/*
		 * Detach the listener once the overlay is disposed, otherwise we may
		 * crash by trying to access a disposed widget.
		 */
		overlay.shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				shell.removeControlListener(listener);
			}
		});
		
		docfetcherProTip = overlay;
	}

	private static void handleShellClosed(@NotNull ShellEvent e) {
		if (SettingsConf.Bool.CloseToTray.get() && !systemTrayShutdown && !Util.IS_UBUNTU_UNITY) {
			e.doit = false;
			systemTrayHider.hide();
		} else {
			e.doit = indexRegistry.getQueue().shutdown(new CancelHandler() {
				public CancelAction cancel() {
					return confirmExit();
				}
			});
			if (!e.doit)
				return;

			// Clear search history
			if (SettingsConf.Bool.ClearSearchHistoryOnExit.get())
				SettingsConf.StrList.SearchHistory.set();

			/*
			 * Note: The getSearcher() call below will block until the searcher
			 * is available. If we run this inside the GUI thread, we won't let
			 * go of the GUI lock, causing the program to deadlock when the user
			 * tries to close the program before all indexes have been loaded.
			 */
			new Thread() {
				public void run() {
					/*
					 * The folder watcher will be null if the program is shut
					 * down while loading the indexes
					 */
					if (folderWatcher != null)
						folderWatcher.shutdown();

					if (hotkeyHandler != null)
						hotkeyHandler.shutdown();

					indexRegistry.getSearcher().shutdown();
				}
			}.start();
		}
	}

	private static void initHotkey() {
		/*
		 * On Windows, the hotkey is disabled by default due to various known
		 * issues:
		 * 
		 * Thread (in German) about the program refusing to launch, with no
		 * error messages and running on Java 64-bit:
		 * https://sourceforge.net/p/docfetcher/discussion/702424/thread/458c45e0cf/
		 * 
		 * One of many bug reports where the program crashes with Java 32-bit:
		 * https://sourceforge.net/p/docfetcher/bugs/1514/
		 */
		if (!ProgramConf.Bool.HotkeyEnabled.get()) {
			return;
		}
		
		/*
		 * As registering the hotkey is prone to crashing the VM, we'll
		 * temporarily disable the hotkey before attempting to register it, so
		 * that if the VM does crash, the hotkey will be disabled the next time.
		 */
		
		boolean wasHotkeyEnabled = SettingsConf.Bool.HotkeyEnabled.get();
		SettingsConf.Bool.HotkeyEnabled.set(false);
		saveSettingsConfFile();
		
		try {
			hotkeyHandler = new HotkeyHandler();
		}
		catch (UnsupportedOperationException e) {
			return; // Hotkey not supported on OS X
		}
		catch (Throwable e) {
			Util.printErr(e);
			return;
		}
		
		if (wasHotkeyEnabled) {
			int[] hotkey = SettingsConf.IntArray.Hotkey.get();
			boolean success = hotkeyHandler.registerHotkey(hotkey[0], hotkey[1]); // might crash the VM
			if (!success) {
				handleHotkeyConflict(hotkey);
			}
		}
		
		/*
		 * This used to be in a finally clause, but apparently even if the VM
		 * aborts due to failed hotkey registration, the finally clause is still
		 * executed, which is not what we want. Relevant forum thread (in
		 * German):
		 * https://sourceforge.net/p/docfetcher/discussion/702424/thread/458c45e0cf/?limit=25#2055
		 */
		SettingsConf.Bool.HotkeyEnabled.set(wasHotkeyEnabled);
//		saveSettingsConfFile();
		
		SettingsConf.IntArray.Hotkey.evtChanged.add(new Event.Listener<int[]>() {
			public void update(int[] eventData) {
				hotkeyHandler.unregisterHotkey();
				
				if (SettingsConf.Bool.HotkeyEnabled.get()) {
					boolean success = hotkeyHandler.registerHotkey(eventData[0], eventData[1]);
					if (!success) {
						handleHotkeyConflict(eventData);
					}
				}
			}
		});
		
		SettingsConf.Bool.HotkeyEnabled.evtChanged.add(new Event.Listener<Boolean> () {
			public void update(Boolean eventData) {
				if (eventData) {
					// Ignore hotkey conflict
					int[] hotkey = SettingsConf.IntArray.Hotkey.get();
					hotkeyHandler.registerHotkey(hotkey[0], hotkey[1]);
				}
				else {
					hotkeyHandler.unregisterHotkey();
				}
			}
		});

		hotkeyHandler.evtHotkeyPressed.add(new Event.Listener<Void> () {
			public void update(Void eventData) {
				UtilGui.runSyncExec(shell, new Runnable() {
					public void run() {
						if (systemTrayHider.isHidden()) {
							systemTrayHider.restore();
						}
						else {
							shell.setMinimized(false);
							shell.setVisible(true);
							shell.forceActive();
							searchBar.setFocus();
						}
					}
				});
			}
		});
	}
	
	private static void handleHotkeyConflict(int[] hotkey) {
		String key = UtilGui.toString(hotkey);
		AppUtil.showError(Msg.hotkey_in_use.format(key), false, true);

		/*
		 * Don't open preferences dialog when the hotkey conflict occurs
		 * at startup.
		 */
		if (shell.isVisible()) {
			PrefDialog prefDialog = new PrefDialog(shell, programConfFile);
			prefDialog.evtOKClicked.add(new Event.Listener<Void> () {
			    public void update(Void eventData) {
			    	saveSettingsConfFile();
			    }
			});
			prefDialog.open();
		}
	}

	@Nullable
	private static CancelAction confirmExit() {
		MultipleChoiceDialog<CancelAction> dialog = new MultipleChoiceDialog<CancelAction>(shell);
		dialog.setTitle(Msg.abort_indexing.get());
		dialog.setText(Msg.keep_partial_index_on_exit.get());
		if (shell.getDisplay().getDismissalAlignment() == SWT.LEFT) {
			dialog.addButton(Msg.keep.get(), CancelAction.KEEP);
			dialog.addButton(Msg.discard.get(), CancelAction.DISCARD);
			dialog.addButton(Msg.dont_exit.get(), null);
		} else {
			dialog.addButton(Msg.dont_exit.get(), null);
			dialog.addButton(Msg.discard.get(), CancelAction.DISCARD);
			dialog.addButton(Msg.keep.get(), CancelAction.KEEP);
		}
		return dialog.open();
	}

	private static void showManual() {
		File file = ManualLocator.getManualFile();
		if (file != null) {
			if (previewPanel.setHtmlFile(file))
				threePanelForm.setSecondSubControlVisible(true);
			else
				UtilGui.launch(file);
		}
		else {
			String msg = Msg.file_not_found.get() + "\n" + SystemConf.Str.ProgramName.get() + "_Manual.html";
			AppUtil.showError(msg, true, true);
		}
	}

	/**
	 * Configures the SWT library path to unpack native libraries into a
	 * specific location. This must be called before any Display/SWT classes are
	 * loaded to ensure portable versions don't leave native libraries in the
	 * system.
	 *
	 * @param pathOverride Optional override directory from paths.txt. If null,
	 *                     uses default location based on portable/non-portable
	 *                     mode.
	 */
	private static void configureSwtLibraryPath(@Nullable File pathOverride) {
		final File swtLibDir;
		if (pathOverride != null) {
			swtLibDir = pathOverride;
		} else if (AppUtil.isPortable()) {
            swtLibDir = new File(AppUtil.getAppDataDir(), "lib/swt");
		} else {
            swtLibDir = new File(AppUtil.getAppDataDir(), "swt");
		}

		// Create directory if it doesn't exist - SWT won't recognize the path
		// otherwise
		swtLibDir.mkdirs();

		// Determine target directory: use swtLibDir if writable, otherwise fall
		// back to temp
		File targetDir;
		if (swtLibDir.isDirectory() && swtLibDir.canWrite()) {
			targetDir = swtLibDir;
		} else {
			targetDir = new File(System.getProperty("java.io.tmpdir"));
		}

		unpackSwtNativeLibs(targetDir);
		try {
			System.setProperty("swt.library.path", targetDir.getCanonicalPath());
		} catch (IOException e) {
			System.setProperty("swt.library.path", targetDir.getAbsolutePath());
		}
	}

	/**
	 * Unpacks SWT native libraries from the SWT jar to the target directory.
	 *
	 * @param targetDir The directory where native libraries should be unpacked
	 */
	private static void unpackSwtNativeLibs(@NotNull File targetDir) {
		File libDir = Util.IS_MAC_OS_X && !AppUtil.isPortable() ?
				new File("../Resources/lib") :
				new File("lib");
		if (!libDir.isDirectory()) {
			return;
		}
		
		// Find the SWT jar
		File swtJar = null;
		File[] jarFiles = libDir.listFiles();
		if (jarFiles != null) {
			for (File jar : jarFiles) {
				String name = jar.getName();
				if (name.startsWith("swt-") && name.endsWith(".jar")) {
					swtJar = jar;
					break;
				}
			}
		}

		if (swtJar == null) {
			return;
		}

		// Determine the native library extension based on platform
		String nativeExt;
		if (Util.IS_WINDOWS) {
			nativeExt = ".dll";
		} else if (Util.IS_LINUX) {
			nativeExt = ".so";
		} else if (Util.IS_MAC_OS_X) {
			nativeExt = ".jnilib";
		} else {
			return; // Unsupported platform
		}

		// Unpack native libraries from the jar
		JarFile jar = null;
		try {
			jar = new JarFile(swtJar);
			java.util.Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				String name = entry.getName();
				// Only extract files at the top level that have the correct extension
				if (!name.contains("/") && name.endsWith(nativeExt)) {
					File targetFile = new File(targetDir, name);
					if (!targetFile.exists() || targetFile.length() != entry.getSize()) {
						unpackJarEntry(jar, entry, targetFile);
					}
				}
			}
		} catch (IOException e) {
			// Silently ignore errors during unpacking
			if (SystemConf.Bool.IsDevelopmentVersion.get()) {
				e.printStackTrace();
			}
		} finally {
			if (jar != null) {
				try {
					jar.close();
				} catch (IOException e) {
					// Ignore
				}
			}
		}
	}

	/**
	 * Unpacks a single entry from a JAR file to a target file.
	 *
	 * @param jar The JAR file
	 * @param entry The entry to unpack
	 * @param targetFile The target file
	 */
	private static void unpackJarEntry(
		@NotNull JarFile jar,
		@NotNull JarEntry entry,
		@NotNull File targetFile
	) throws IOException {
        try (
			InputStream in = jar.getInputStream(entry);
			FileOutputStream out = new FileOutputStream(targetFile)
		) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            // Make the library executable on Unix systems (like SWT does)
            if (!Util.IS_WINDOWS) {
                targetFile.setExecutable(true);
            }
        }
    }

	// Public method for Python API
	@NotNull
	public static IndexRegistry getIndexRegistry() {
		return indexRegistry;
	}

}
