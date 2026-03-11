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

package net.sourceforge.docfetcher;

import net.sourceforge.docfetcher.gui.Application;

/**
 * @author Tran Nam Quang
 */
public final class Main {

	private Main() {
	}

	public static void main(String[] args) {
		/*
		 * This is a minimal entrypoint. The classpath, including the correct SWT jar
		 * and the 'lang' directory, is now fully configured by the external launcher
		 * scripts (e.g., DocFetcher-gtk3.sh).
		 */
		Application.main(args);
	}

}