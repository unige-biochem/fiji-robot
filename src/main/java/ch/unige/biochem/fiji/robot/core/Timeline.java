package ch.unige.biochem.fiji.robot.core;

/**
 * Sink for the machine-readable timeline of every visible gesture.
 *
 * <p><b>Placeholder.</b> In the original tutorial-video toolkit this writes a
 * {@code timeline.json} recording (one entry per click / drag / wheel, with
 * timing relative to each clip). That recording layer is not part of this first
 * increment of {@code scijava-ui-robot}; the methods are kept here as no-ops so
 * the {@link Ui} gesture primitives can call them exactly as they do in the
 * original — when the recording layer is ported, only this class is fleshed
 * out, not its call sites.</p>
 */
public final class Timeline {

	private Timeline() {}

	public static void mouseClick() {}

	public static void mouseDoubleClick() {}

	public static void mouseRightClick() {}

	public static void mouseWheel(int notches) {}

	public static void mouseDrag(int startX, int startY, int endX, int endY) {}
}
