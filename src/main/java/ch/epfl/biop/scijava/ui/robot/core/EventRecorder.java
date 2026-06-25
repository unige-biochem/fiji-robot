package ch.epfl.biop.scijava.ui.robot.core;

/**
 * Captures the human operator's real mouse / key activity during manual phases.
 *
 * <p><b>Placeholder.</b> In the original toolkit this installs a global AWT
 * listener and forwards human gestures to {@link Timeline}, using
 * {@link #suppress(boolean)} to bracket Robot-synthesized events so they aren't
 * double-counted. That capture layer is not part of this first increment;
 * {@link #suppress(boolean)} is a no-op here so the {@link Ui} primitives can
 * bracket their native calls exactly as in the original. When the capture layer
 * is ported, only this class changes.</p>
 */
public final class EventRecorder {

	private EventRecorder() {}

	/** No-op placeholder — see class javadoc. */
	public static void suppress(boolean suppressed) {}
}
