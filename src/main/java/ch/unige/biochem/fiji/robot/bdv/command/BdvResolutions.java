package ch.unige.biochem.fiji.robot.bdv.command;

import ch.unige.biochem.fiji.robot.Gesture;
import ch.unige.biochem.fiji.robot.GestureContext;
import ch.unige.biochem.fiji.robot.GroovyRenderable;
import ch.unige.biochem.fiji.robot.PreSetResolution;
import ch.unige.biochem.fiji.robot.core.Timings;
import ch.unige.biochem.fiji.robot.core.Ui;
import ch.unige.biochem.fiji.robot.groovy.GroovyRenderContext;

import bdv.util.BdvHandle;
import org.scijava.Context;
import org.scijava.object.ObjectService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Insets;
import java.awt.Point;
import java.util.List;

/**
 * Factory methods for the BigDataViewer-bound {@link PreSetResolution} kinds —
 * the BDV mirror of {@code Ij1Resolutions}. Static-import at the call site:
 *
 * <pre>
 * import static ch.epfl.biop.scijava.ui.robot.bdv.command.BdvResolutions.*;
 *
 * CmdExecutor.of(context, MyBdvCommand.class)
 *     .preSet("bdvh", selectActiveBdv("BDV alpha"))
 *     .withLauncher(treeLauncher("Other Sources"))
 *     .postSet("adjust", fromDialog(true, "We re-center the view."))
 *     .launch();
 * </pre>
 */
public final class BdvResolutions {

	private BdvResolutions() {}

	/**
	 * A {@code @Parameter BdvHandle} input satisfied by the active BDV window,
	 * identified by window title. With no narration.
	 *
	 * @see #selectActiveBdv(String, String)
	 */
	public static PreSetResolution selectActiveBdv(String title) {
		return new ActiveBdvResolution(title, null);
	}

	/**
	 * A {@code @Parameter BdvHandle} input satisfied by the active BDV window,
	 * identified by title, with a narration subtitle.
	 *
	 * <p>The exact BDV counterpart of {@code Ij1Resolutions.selectActiveImage}.
	 * In the headless ({@code programmaticLauncher()}) projection, {@link #value()}
	 * looks the {@link BdvHandle} up by title and passes it straight to
	 * {@code cs.run(...)}. In a visible projection the value is <em>not</em>
	 * passed; instead the {@linkplain Gesture pre-launch gesture} activates the
	 * named window — which fires bdv-playground's {@code windowActivated} listener
	 * (updating {@code LAST_ACTIVE_BDVH}) so the {@code ActiveBdvPreprocessor}
	 * resolves the input from {@code SourceBdvDisplayService.getActiveBdv()} during
	 * the run.</p>
	 *
	 * @param title     the BDV window title, e.g. {@code "BDV alpha"}
	 * @param narration subtitle text fired when this input is established, or
	 *                  {@code null} to set it silently
	 */
	public static PreSetResolution selectActiveBdv(String title, String narration) {
		return new ActiveBdvResolution(title, narration);
	}

	/**
	 * Resolves a BDV window by title. Value-bearing for the headless run; a
	 * {@link Gesture} for the visible run.
	 */
	private static final class ActiveBdvResolution
			implements PreSetResolution, Gesture, GroovyRenderable {

		private final String title;
		private final String narration;

		ActiveBdvResolution(String title, String narration) {
			if (title == null) throw new IllegalArgumentException("BDV title must not be null");
			this.title = title;
			this.narration = narration;
		}

		@Override
		public Object value() {
			// No GestureContext in the headless projection — fall back to
			// bdv-playground's statically-registered context (the BDV analog of
			// ij1's static WindowManager lookup).
			return require(find(SourceServices.getContext()));
		}

		@Override
		public String narration() { return narration; }

		/**
		 * Render the carried window-title spec, not {@link #value()}: a
		 * {@code BdvHandle} can't be turned back into the title that selected it.
		 * Emits a by-title lookup over the {@code ObjectService} (hoisted as a
		 * script parameter), mirroring {@link #find(Context)} — the same lookup
		 * the headless run does, but as runnable source text rather than a live
		 * object.
		 */
		@Override
		public String renderGroovy(GroovyRenderContext ctx) {
			ctx.addImport("bdv.util.BdvHandle");
			ctx.addImport("sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper");
			String os = ctx.requireScriptParam("ObjectService", "objectService");
			return os + ".getObjects(BdvHandle.class).find { "
					+ "BdvHandleHelper.getWindowTitle(it) == \"" + escape(title) + "\" }";
		}

		@Override
		public void perform(GestureContext context) {
			BdvHandle bdvh = require(find(context.context()));
			JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(bdvh.getViewerPanel());
			// Visible activation: click the LEFT of the window's title bar so the
			// gesture reads as a deliberate "pick this viewer" on a recording while
			// staying clear of the window buttons, resize edges and menu bar.
			if (frame != null && frame.isShowing()) {
				Insets insets = frame.getInsets();
				int titleBarHeight = Math.max(insets.top, 24);
				Point loc = frame.getLocationOnScreen();
				Ui.moveTo(loc.x + Ui.TITLE_BAR_GRAB_X, loc.y + titleBarHeight / 2);
				Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
				Ui.click();
				Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
			}
			// Correctness guarantee, independent of whether the click landed:
			// toFront + requestFocus fires bdv-playground's windowActivated
			// listener, which caches this as LAST_ACTIVE_BDVH for getActiveBdv().
			Ui.runOnEdt(() -> BdvHandleHelper.activateWindow(bdvh));
		}

		/**
		 * The {@link BdvHandle} whose window title equals {@code title}, or
		 * {@code null}. {@code context} is the gesture's context at perform time
		 * and bdv-playground's static context in the value path.
		 */
		private BdvHandle find(Context context) {
			if (context == null) {
				throw new IllegalStateException("No SciJava context available to resolve BDV '"
						+ title + "' — is bigdataviewer-playground running?");
			}
			ObjectService os = context.service(ObjectService.class);
			List<BdvHandle> handles = os.getObjects(BdvHandle.class);
			for (BdvHandle bdvh : handles) {
				if (title.equals(BdvHandleHelper.getWindowTitle(bdvh))) return bdvh;
			}
			return null;
		}

		private BdvHandle require(BdvHandle bdvh) {
			if (bdvh == null) {
				throw new IllegalStateException("No open BDV window titled '" + title + "'.");
			}
			return bdvh;
		}

		private static String escape(String s) {
			return s.replace("\\", "\\\\").replace("\"", "\\\"");
		}
	}
}
