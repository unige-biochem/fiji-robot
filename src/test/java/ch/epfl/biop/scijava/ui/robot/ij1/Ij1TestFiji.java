package ch.epfl.biop.scijava.ui.robot.ij1;

import ch.epfl.biop.scijava.ui.robot.core.Ui;

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
