/*-
 * #%L
 * A visible, recordable driver for SciJava commands — drives input
 * 		harvesting (and, later, java.awt.Robot gestures) so command runs can be
 * 		reproduced and recorded for tutorial videos.
 * %%
 * Copyright (C) 2026 University of Geneva, Department of Biochemistry
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package ch.unige.biochem.fiji.robot.core;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * A {@link PreferencesFactory} that keeps the {@link Preferences} in memory,
 * empty at start and gone at exit: a Fiji started with it begins from the
 * default values of every remembered input (SciJava persists the last values
 * of command parameters there), and leaves the user's own preferences untouched.
 * That makes a run reproducible, for pictures or videos, whatever was used
 * before on the machine.
 *
 * <p>Install it before anything reads a preference, on the command line:</p>
 * <pre>-Djava.util.prefs.PreferencesFactory=ch.unige.biochem.fiji.robot.core.InMemoryPreferences</pre>
 * <p>or with {@link #install()} first thing in {@code main}. ImageJ 1's own
 * preferences ({@code IJ_Prefs.txt}, where tools record their paths) are a file,
 * not concerned.</p>
 */
public class InMemoryPreferences implements PreferencesFactory {

	private static final Preferences USER = new Node(null, "");
	private static final Preferences SYSTEM = new Node(null, "");

	/**
	 * Installs this factory, or throws if the preferences are already in use
	 * with another one (the JDK picks its factory once).
	 */
	public static void install() {
		System.setProperty("java.util.prefs.PreferencesFactory", InMemoryPreferences.class.getName());
		if (!isInstalled()) {
			throw new IllegalStateException("The preferences were used before InMemoryPreferences.install(): pass "
					+ "-Djava.util.prefs.PreferencesFactory=" + InMemoryPreferences.class.getName() + " to java");
		}
	}

	/** True if the preferences of this JVM are in memory. */
	public static boolean isInstalled() {
		return Preferences.userRoot() == USER;
	}

	@Override
	public Preferences userRoot() {
		return USER;
	}

	@Override
	public Preferences systemRoot() {
		return SYSTEM;
	}

	private static final class Node extends AbstractPreferences {

		private final Map<String, String> values = new HashMap<>();
		private final Map<String, Node> children = new HashMap<>();

		Node(Node parent, String name) {
			super(parent, name);
		}

		@Override
		protected void putSpi(String key, String value) {
			values.put(key, value);
		}

		@Override
		protected String getSpi(String key) {
			return values.get(key);
		}

		@Override
		protected void removeSpi(String key) {
			values.remove(key);
		}

		@Override
		protected void removeNodeSpi() {
			((Node) parent()).children.remove(name());
		}

		@Override
		protected String[] keysSpi() {
			return values.keySet().toArray(new String[0]);
		}

		@Override
		protected String[] childrenNamesSpi() {
			return children.keySet().toArray(new String[0]);
		}

		@Override
		protected AbstractPreferences childSpi(String name) {
			return children.computeIfAbsent(name, n -> new Node(this, n));
		}

		@Override
		protected void syncSpi() {}

		@Override
		protected void flushSpi() {}
	}
}
