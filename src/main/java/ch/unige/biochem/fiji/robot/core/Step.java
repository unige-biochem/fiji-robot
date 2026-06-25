package ch.unige.biochem.fiji.robot.core;

/**
 * A narrated step of a recorded demo: subtitles ({@link #say}), key-moment
 * screenshots ({@link #snapMoment}), and manual-phase pauses
 * ({@link #waitForUser}).
 *
 * <p><b>Placeholder.</b> The full recording-side {@code Step} (chapter
 * boundaries, subtitle timing, screenshot capture, intro/outro) belongs to the
 * deferred recording layer. These no-op methods exist so the widget drivers can
 * fire narration / snapshot hooks unconditionally — harmless when nothing is
 * being recorded, wired up for real when the recording layer is ported.</p>
 */
public final class Step {

	private Step() {}

	/** Fire a subtitle line. No-op placeholder — see class javadoc. */
	public static void say(String text) {}

	/** Capture a key-moment screenshot. No-op placeholder. */
	public static void snapMoment(String label) {}

	/** Block for a manual phase until the operator continues. No-op placeholder. */
	public static void waitForUser(String message) {}
}
