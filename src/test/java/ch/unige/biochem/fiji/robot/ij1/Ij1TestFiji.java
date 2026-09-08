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
package ch.unige.biochem.fiji.robot.ij1;

import ch.unige.biochem.fiji.robot.core.Ui;

import ij.IJ;
import ij.ImagePlus;
import net.imagej.ImageJ;
import org.scijava.Context;

import java.awt.Frame;

/**
 * Boots one real Fiji UI for the ij1 GUI tests and keeps it for the whole JVM.
 *
 * <p>ImageJ1 is effectively a singleton ({@link IJ#getInstance()} is static), so
 * the two ij1 test classes must share one boot rather than each spinning up their
 * own. {@link #boot()} is idempotent — the first call shows the legacy UI (with
 * its search bar), later calls return the same instance.</p>
 *
 * <p><b>Local only.</b> Like {@code HarvesterWidgetsTest}, these tests need a
 * visible display and synthesize real input events; they are not meant for
 * headless CI.</p>
 */
final class Ij1TestFiji {

	private Ij1TestFiji() {}

	private static ImageJ ij;

	/** Boots the legacy Fiji UI once and returns the gateway. Idempotent. */
	static synchronized ImageJ boot() {
		if (ij != null) return ij;
		ImageJ instance = new ImageJ();
		// showUI() brings up the legacy ImageJ1 main frame (imagej-legacy is the
		// default UI), which carries the LegacySearchBar the search launcher drives.
		instance.ui().showUI();
		Frame frame = Fiji.waitForIJFrame(10_000);
		if (frame == null) {
			throw new IllegalStateException("ImageJ main frame did not appear within 10s");
		}
		Ui.placeFrame(frame);
		Ui.rawPause(500);
		ij = instance;
		return ij;
	}

	static Context context() {
		return boot().getContext();
	}

	/**
	 * Closes the Fiji UI and disposes the SciJava context. Call from a test's
	 * {@code @AfterClass} so the run does not leave Fiji windows open. Resets the
	 * singleton so a later {@link #boot()} (a different test class in a fresh JVM)
	 * starts clean.
	 */
	static synchronized void shutdown() {
		if (ij == null) return;
		Context c = ij.getContext();
		try {
			Ui.runOnEdt(() -> {
				for (java.awt.Window w : java.awt.Window.getWindows()) w.dispose();
			});
		}
		catch (Exception ignored) { /* best-effort window cleanup */ }
		c.dispose();
		ij = null;
	}

	/**
	 * Creates a ramp image, shows it with the given title, and waits for its
	 * window so it can be activated / found by title. Returns the {@link ImagePlus}.
	 */
	static ImagePlus showImage(String title) {
		ImagePlus imp = IJ.createImage(title, "8-bit ramp", 64, 64, 1);
		imp.show();
		// Give the ImageWindow a beat to realize before tests touch it.
		long deadline = System.currentTimeMillis() + 5_000;
		while ((imp.getWindow() == null || !imp.getWindow().isShowing())
				&& System.currentTimeMillis() < deadline) {
			Ui.rawPause(50);
		}
		return imp;
	}
}
