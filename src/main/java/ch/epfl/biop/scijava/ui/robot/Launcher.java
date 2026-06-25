package ch.epfl.biop.scijava.ui.robot;

import org.scijava.module.Module;

/**
 * The single point at which a command is triggered.
 *
 * <p>A {@code CmdExecutor} plan has exactly one launcher — that invariant is
 * what guarantees "there is one and only one place the command fires". The
 * launcher decides <em>how</em> the command starts:</p>
 *
 * <ul>
 *   <li>programmatically, via {@code CommandService.run} (this increment);</li>
 *   <li>by typing into a search bar, walking a menu, or right-clicking a
 *       source-tree node (later increments / binding modules) — the visible
 *       gestures that also drive the harvester dialog afterwards.</li>
 * </ul>
 *
 * <p>A launcher that launches <em>from</em> a UI object may also resolve one of
 * the command's inputs as a side effect of the launch gesture (a source-tree
 * right-click pre-fills {@code "sources"}); such a launcher contributes those
 * inputs via {@link #contributedInputs(LaunchRequest)} so the executor can fold
 * them into the run without the caller restating them.</p>
 */
public interface Launcher {

	/**
	 * Run the command described by {@code request}.
	 *
	 * @return the completed {@link Module} when outputs are recoverable
	 *         (programmatic launch), or {@code null} when the launch path drops
	 *         them (legacy search bar, menu) — callers that need outputs must
	 *         use a launcher that returns a module.
	 */
	Module launch(LaunchRequest request);

	/**
	 * Inputs this launcher resolves itself as a side effect of its launch
	 * gesture, in addition to those in {@link LaunchRequest#inputs()}. Default:
	 * none. Returned as an ordered {@code name → value} map so the executor can
	 * merge them and the script renderer can emit them.
	 *
	 * @param request the assembled request, in case the contribution depends on
	 *                the context (e.g. resolving a tree path against a service)
	 */
	default java.util.Map<String, Object> contributedInputs(LaunchRequest request) {
		return java.util.Collections.emptyMap();
	}
}
