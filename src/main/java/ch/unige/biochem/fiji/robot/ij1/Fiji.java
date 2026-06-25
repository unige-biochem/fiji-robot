package ch.unige.biochem.fiji.robot.ij1;

import ch.unige.biochem.fiji.robot.core.Inspector;
import ch.unige.biochem.fiji.robot.core.Step;
import ch.unige.biochem.fiji.robot.core.Timings;
import ch.unige.biochem.fiji.robot.core.Ui;

import ij.IJ;

import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Point;
import java.util.Map;

/**
 * Visible actions on the <em>already-running</em> ImageJ1 main frame.
 *
 * <p>This is the IJ1 half of {@code scijava-ui-robot}. It deliberately does not
 * boot Fiji — the main code drives whatever legacy frame {@link IJ#getInstance()}
 * exposes; spinning one up (the {@code new ImageJ().ui().showUI()} dance, which
 * needs the heavyweight {@code net.imagej:imagej} gateway) is a test concern.</p>
 *
 * <p>The only IJ1 entry point used so far is {@link #searchAndRun(String)} — the
 * trigger gesture behind {@link Ij1Launchers#searchLauncher(String)}.</p>
 */
public final class Fiji {

	private Fiji() {}

	/**
	 * Runs a Fiji command via the legacy search bar in the IJ main frame:
	 * focus the frame → move to the search bar → type the query → Enter (which
	 * runs the top match). The search bar is located by reflecting the IJ frame's
	 * component tree for the {@code LegacySearchBar} class (matched by name, so no
	 * compile dependency on imagej-legacy's internals).
	 *
	 * @param query a short, unambiguous prefix of the command's display name,
	 *              e.g. {@code "robot dem"}
	 */
	public static void searchAndRun(String query) {
		Frame ij = IJ.getInstance();
		if (ij == null) throw new IllegalStateException("IJ main frame not available");

		Point center = findSearchBar(ij).screenCenter();
		if (center == null) {
			throw new IllegalStateException("LegacySearchBar has no screen position — "
					+ "is the IJ frame realized and on screen?");
		}
		SwingUtilities.invokeLater(() -> { ij.toFront(); ij.requestFocus(); });
		Ui.rawPause(150);
		Ui.moveTo(center);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
		Ui.type(query);
		Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
		// Tutorial PNG: search query typed, cursor on the bar, just before Enter.
		Step.snapMoment("search");
		Ui.enter();
	}

	private static Inspector.ComponentInfo findSearchBar(Frame frame) {
		Map<String, Inspector.ComponentInfo> tree = Inspector.inspect(frame);
		for (Inspector.ComponentInfo info : tree.values()) {
			if (info.className != null && info.className.contains("LegacySearchBar")) {
				return info;
			}
		}
		throw new IllegalStateException("LegacySearchBar not found in IJ main frame");
	}

	/**
	 * Polls {@link IJ#getInstance()} for up to {@code timeoutMs} ms, returning the
	 * IJ main frame as soon as it is showing. Useful right after a UI boot.
	 */
	public static Frame waitForIJFrame(long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			ij.ImageJ ij1 = IJ.getInstance();
			if (ij1 != null && ij1.isShowing()) return ij1;
			Ui.rawPause(Timings.FRAME_POLL_INTERVAL_MS);
		}
		return null;
	}
}
