package ch.unige.biochem.fiji.robot;

/**
 * The <em>visible</em> projection of an {@link InputResolution}: a Robot gesture
 * that establishes, in the real UI, the state this input represents.
 *
 * <p>This is a <strong>capability</strong>, not part of {@link InputResolution}.
 * A resolution implements it only if it can be driven visibly; resolutions that
 * are purely value-bearing (a plain {@code programmatic(2)}) do not. Keeping it
 * separate is what lets the headless projections ({@code value()} → programmatic
 * run, {@code value()} → Groovy literal) stay oblivious to whether a given
 * resolution also knows how to act on screen.</p>
 *
 * <p>Two kinds of resolution opt in:</p>
 * <ul>
 *   <li>a {@link PreSetResolution} mirroring a SciJava preprocessor (e.g. the
 *       active-image preprocessor) performs a <em>pre-launch</em> gesture that
 *       establishes the ambient UI state — selecting the right window before the
 *       command fires, so the real preprocessor reads it during the run;</li>
 *   <li>a {@link DialogResolution} (a later increment) drives the actual Swing
 *       widget the harvester shows.</li>
 * </ul>
 *
 * <p>A visible {@link Launcher} (e.g. the search-bar launcher) runs the pre-set
 * gestures via {@link LaunchRequest#runPreSetGestures()} before it triggers the
 * command; the headless {@code programmaticLauncher()} never invokes them — in
 * that mode the input is satisfied by {@code value()} alone.</p>
 */
public interface Gesture {

	/**
	 * Perform this resolution's visible action.
	 *
	 * @param context the run this gesture is part of (SciJava context, command
	 *                class, and the input name the gesture resolves)
	 */
	void perform(GestureContext context);
}