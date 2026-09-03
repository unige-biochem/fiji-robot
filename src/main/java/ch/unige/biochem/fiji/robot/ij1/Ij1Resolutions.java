package ch.unige.biochem.fiji.robot.ij1;

import ch.unige.biochem.fiji.robot.Gesture;
import ch.unige.biochem.fiji.robot.GestureContext;
import ch.unige.biochem.fiji.robot.GroovyRenderable;
import ch.unige.biochem.fiji.robot.PreSetResolution;
import ch.unige.biochem.fiji.robot.core.Timings;
import ch.unige.biochem.fiji.robot.core.Ui;
import ch.unige.biochem.fiji.robot.groovy.GroovyRenderContext;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;

import java.awt.Insets;
import java.awt.Point;

/**
 * Factory methods for the IJ1-bound {@link PreSetResolution} kinds — the
 * ImageJ1 mirror of the core {@code Resolutions} factory. Static-import at the
 * call site:
 *
 * <pre>
 * import static ch.unige.biochem.fiji.robot.ij1.Ij1Resolutions.*;
 *
 * CmdExecutor.of(context, MyImageCommand.class)
 *     .preSet("imp", selectActiveImage("blobs.gif"))
 *     .withLauncher(searchLauncher("my command"))
 *     .postSet("radius", fromDialog(3.0, "We set the radius."))
 *     .launch();
 * </pre>
 */
public final class Ij1Resolutions {

	private Ij1Resolutions() {}

	/**
	 * A {@code @Parameter ImagePlus} (or {@code Dataset}) input satisfied by the
	 * currently-active legacy image, identified by window title. With no narration.
	 *
	 * @see #selectActiveImage(String, String)
	 */
	public static PreSetResolution selectActiveImage(String title) {
		return new ActiveImageResolution(title, null);
	}

	/**
	 * A {@code @Parameter ImagePlus} input satisfied by the active legacy image,
	 * identified by window title, with a narration subtitle.
	 *
	 * <p>This is the IJ1 incarnation of the two-phase model. In the headless
	 * ({@code programmaticLauncher()}) projection, {@link PreSetResolution#value()} resolves the
	 * {@link ImagePlus} by title and it is passed straight to {@code cs.run(...)}.
	 * In the visible ({@code searchLauncher(...)}) projection, the value is
	 * <em>not</em> passed; instead the {@linkplain Gesture pre-launch gesture}
	 * activates the named window so ImageJ's {@code LegacyImagePreprocessor} reads
	 * it during the run — exactly the ambient-state-before-launch mechanism the
	 * design rests on.</p>
	 *
	 * @param title     the image window title, e.g. {@code "blobs.gif"}
	 * @param narration subtitle text fired when this input is established, or
	 *                  {@code null} to set it silently
	 */
	public static PreSetResolution selectActiveImage(String title, String narration) {
		return new ActiveImageResolution(title, narration);
	}

	/**
	 * Resolves the active legacy image by window title. Value-bearing for the
	 * headless run; a {@link Gesture} for the visible run.
	 */
	private static final class ActiveImageResolution
			implements PreSetResolution, Gesture, GroovyRenderable {

		private final String title;
		private final String narration;

		ActiveImageResolution(String title, String narration) {
			if (title == null) throw new IllegalArgumentException("image title must not be null");
			this.title = title;
			this.narration = narration;
		}

		@Override
		public Object value() {
			ImagePlus imp = WindowManager.getImage(title);
			if (imp == null) {
				throw new IllegalStateException("No open image titled '" + title
						+ "'. Open it before launching.");
			}
			return imp;
		}

		@Override
		public String narration() { return narration; }

		/**
		 * Render the carried window-title spec, not {@link #value()}: an
		 * {@code ImagePlus} can't be turned back into the title that selected it.
		 * Emits {@code WindowManager.getImage("title")} — a deterministic,
		 * runnable lookup of the same window the demo picked. (Switch to
		 * {@code IJ.getImage()} here if "whatever image is active" is the intended
		 * reproduction instead.)
		 */
		@Override
		public String renderGroovy(GroovyRenderContext ctx) {
			ctx.addImport("ij.WindowManager");
			return "WindowManager.getImage(\"" + escape(title) + "\")";
		}

		@Override
		public void perform(GestureContext context) {
			ImagePlus imp = WindowManager.getImage(title);
			if (imp == null) {
				throw new IllegalStateException("No open image titled '" + title
						+ "' to activate.");
			}
			ImageWindow win = imp.getWindow();
			// Visible activation: click the LEFT of the window's title bar so the
			// gesture reads as a deliberate "pick this image" on a recording while
			// staying clear of the window buttons, resize edges and menu bar.
			if (win != null && win.isShowing()) {
				Insets insets = win.getInsets();
				int titleBarHeight = Math.max(insets.top, 24);
				Point loc = win.getLocationOnScreen();
				Ui.moveTo(loc.x + Ui.TITLE_BAR_GRAB_X, loc.y + titleBarHeight / 2);
				Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
				Ui.click();
				Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
			}
			// Correctness guarantee, independent of whether the click landed:
			// make this the legacy current image so LegacyImagePreprocessor
			// resolves the command's image parameter to it during the run.
			Ui.runOnEdt(() -> WindowManager.setCurrentWindow(win));
		}

		private static String escape(String s) {
			return s.replace("\\", "\\\\").replace("\"", "\\\"");
		}
	}
}
