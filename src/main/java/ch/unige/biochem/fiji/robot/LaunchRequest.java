package ch.unige.biochem.fiji.robot;

import org.scijava.Context;
import org.scijava.command.Command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fully-assembled recipe handed to a {@link Launcher} at {@code launch()}
 * time: the command to run, the SciJava {@link Context} to run it in, and the
 * inputs the builder collected — kept available both as one merged flat view
 * (for the headless projections) and split into their two phases (for the
 * visible launchers).
 *
 * <p>{@link #inputs()} is the union of the {@code preSet(...)} resolutions and
 * the {@code postSet(...)} dialog resolutions, in declaration order
 * (pre-sets first, then dialog inputs) — this is what the headless
 * {@code programmaticLauncher()} passes to {@code cs.run(...)} and what the
 * Groovy renderer emits. A launcher that contributes its own inputs (e.g. a
 * source-tree launcher contributing {@code "sources"}) has them folded into this
 * map. {@link #narrations()} carries the subtitle text for the subset of inputs
 * that declared one.</p>
 *
 * <p>A <em>visible</em> launcher needs the two phases kept apart instead: it runs
 * the pre-set gestures ({@link #runPreSetGestures()}) to establish ambient state,
 * triggers the command, then drives <em>only</em> the dialog inputs
 * ({@link #dialogArgs()} / {@link #dialogNarrations()}) through the harvester.
 * Launcher-contributed inputs belong to neither phase and appear only in the
 * merged view.</p>
 *
 * <p>Immutable; the builder hands a fresh instance to the launcher and keeps no
 * reference to its internals.</p>
 */
public final class LaunchRequest {

	private final Context context;
	private final Class<? extends Command> command;
	private final Map<String, Object> inputs;
	private final Map<String, String> narrations;
	private final Map<String, PreSetResolution> preSets;
	private final Map<String, DialogResolution> dialogs;

	LaunchRequest(Context context,
				  Class<? extends Command> command,
				  Map<String, Object> inputs,
				  Map<String, String> narrations,
				  Map<String, PreSetResolution> preSets,
				  Map<String, DialogResolution> dialogs) {
		this.context = context;
		this.command = command;
		this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
		this.narrations = Collections.unmodifiableMap(new LinkedHashMap<>(narrations));
		this.preSets = Collections.unmodifiableMap(new LinkedHashMap<>(preSets));
		this.dialogs = Collections.unmodifiableMap(new LinkedHashMap<>(dialogs));
	}

	public Context context() { return context; }

	public Class<? extends Command> command() { return command; }

	/** Merged name → value inputs, in declaration order. Unmodifiable. */
	public Map<String, Object> inputs() { return inputs; }

	/** Name → narration for the inputs that declared one. Unmodifiable. */
	public Map<String, String> narrations() { return narrations; }

	/**
	 * The pre-set resolutions, in declaration order. Unmodifiable. These are
	 * established <em>before</em> the command launches; the visible launchers
	 * read this to run their {@link Gesture}s (see {@link #runPreSetGestures()}).
	 */
	public Map<String, PreSetResolution> preSetResolutions() { return preSets; }

	/**
	 * The dialog resolutions, in declaration order. Unmodifiable. A visible
	 * launcher drives these — and only these — through the harvester after the
	 * command has launched.
	 */
	public Map<String, DialogResolution> dialogResolutions() { return dialogs; }

	/**
	 * The inputs flattened to the alternating {@code name, value, name, value,
	 * ...} array shape expected by
	 * {@link org.scijava.command.CommandService#run(Class, boolean, Object...)}.
	 */
	public Object[] flatInputs() {
		return flatten(inputs);
	}

	/**
	 * The <em>dialog</em> inputs flattened to the alternating
	 * {@code name, value, ...} shape a visible launcher passes to
	 * {@code Harvester.runOpenDialog(...)}. Excludes pre-set and
	 * launcher-contributed inputs, which never appear as harvester widgets.
	 */
	public Object[] dialogArgs() {
		Map<String, Object> values = new LinkedHashMap<>();
		for (Map.Entry<String, DialogResolution> e : dialogs.entrySet()) {
			values.put(e.getKey(), e.getValue().value());
		}
		return flatten(values);
	}

	/** Name → narration for the dialog inputs that declared one. Unmodifiable. */
	public Map<String, String> dialogNarrations() {
		Map<String, String> out = new LinkedHashMap<>();
		for (Map.Entry<String, DialogResolution> e : dialogs.entrySet()) {
			String n = e.getValue().narration();
			if (n != null) out.put(e.getKey(), n);
		}
		return Collections.unmodifiableMap(out);
	}

	/**
	 * Run the pre-launch {@link Gesture} of every pre-set resolution that
	 * implements one, in declaration order. Resolutions without a gesture are
	 * skipped. A visible launcher calls this before it triggers the command, so
	 * the ambient UI state (active window, …) is in place by the time the
	 * command's preprocessor chain runs.
	 */
	public void runPreSetGestures() {
		for (Map.Entry<String, PreSetResolution> e : preSets.entrySet()) {
			PreSetResolution r = e.getValue();
			if (r instanceof Gesture) {
				((Gesture) r).perform(new GestureContext(context, command, e.getKey()));
			}
		}
	}

	private static Object[] flatten(Map<String, Object> map) {
		Object[] out = new Object[map.size() * 2];
		int i = 0;
		for (Map.Entry<String, Object> e : map.entrySet()) {
			out[i++] = e.getKey();
			out[i++] = e.getValue();
		}
		return out;
	}
}
