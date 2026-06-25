package ch.unige.biochem.fiji.robot.bdv;

import ch.unige.biochem.fiji.robot.core.Timings;
import ch.unige.biochem.fiji.robot.core.Ui;

import bdv.util.BdvHandle;
import net.imagej.ImageJ;
import org.scijava.Context;
import sc.fiji.bdvpg.command.workspace.SourceServiceShowCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;

import java.awt.Frame;

/**
 * Boots one Fiji + BigDataViewer-Playground UI for the bdv GUI tests and keeps
 * it for the whole JVM.
 *
 * <p><b>Local only,</b> like the ij1 tests: needs a visible display and
 * synthesizes real input events. <b>Run one GUI test class per JVM</b> — this and
 * {@code Ij1TestFiji} each boot their own ImageJ gateway, so running both suites
 * in a single surefire fork would clash on the ImageJ singleton.</p>
 */
final class BdvTestFiji {

	private BdvTestFiji() {}

	private static ImageJ ij;

	/** Boots the UI once and forces bdv-playground's services to initialize. Idempotent. */
	static synchronized Context boot() {
		if (ij != null) return ij.getContext();
		ImageJ instance = new ImageJ();
		instance.ui().showUI();
		Context context = instance.getContext();
		// Touch the SourceService so bdv-playground registers itself (this is what
		// makes SourceServices.getContext() / getBdvDisplayService() available and
		// registers the source-tree actions discovered from BdvPlaygroundActionCommand).
		context.service(SourceService.class);
		ij = instance;
		return context;
	}

	/**
	 * Closes the Fiji UI and disposes the SciJava context. Call from a test's
	 * {@code @AfterClass} so the run does not leave Fiji / BDV windows open. Resets
	 * the singleton so a later {@link #boot()} (a different test class in a fresh
	 * JVM) starts clean.
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

	/** Creates a new BDV window and titles it. */
	static BdvHandle newBdv(String title) {
		BdvHandle bdvh = SourceServices.getBdvDisplayService().getNewBdv();
		BdvHandleHelper.setWindowTitle(bdvh, title);
		Ui.rawPause(300);
		return bdvh;
	}

	/**
	 * Creates a titled BDV window, shrinks it and moves it to {@code (x, y)} on
	 * screen, so a test can keep windows from overlapping each other and the source
	 * tree the Robot has to click. The small fixed size makes the placement
	 * predictable regardless of the default BDV window size.
	 */
	static BdvHandle newBdv(String title, int x, int y) {
		BdvHandle bdvh = newBdv(title);
		java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(bdvh.getViewerPanel());
		if (w != null) Ui.runOnEdt(() -> {
			w.setSize(420, 320);
			w.setLocation(x, y);
		});
		Ui.rawPause(200);
		return bdvh;
	}

	/**
	 * Opens the "BDV Sources" tree frame (so its {@code JTree} is on screen for
	 * the tree launcher) and returns it. Runs the show command programmatically —
	 * opening the frame is setup, not the gesture under test.
	 */
	static Frame showSourcesFrame(Context context) {
		try {
			context.service(org.scijava.command.CommandService.class)
					.run(SourceServiceShowCommand.class, true).get();
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to open the BDV Sources frame", e);
		}
		Frame frame = Ui.waitForFrame("BDV Sources", Timings.FRAME_WAIT_TIMEOUT_MS);
		if (frame == null) {
			throw new IllegalStateException("BDV Sources frame did not appear");
		}
		Ui.rawPause(500);
		return frame;
	}
}
