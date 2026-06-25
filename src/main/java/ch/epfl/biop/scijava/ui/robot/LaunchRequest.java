package ch.epfl.biop.scijava.ui.robot;

import org.scijava.Context;
import org.scijava.command.Command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The fully-assembled recipe handed to a {@link Launcher} at {@code launch()}
 * time: the command to run, the SciJava {@link Context} to run it in, and the
 * merged, ordered set of inputs the builder collected.
 *
 * <p>{@link #inputs()} is the union of the {@code preSet(...)} resolutions and
 * the {@code postSet(...)} dialog resolutions, in declaration order
 * (pre-sets first, then dialog inputs). A launcher that contributes its own
 * inputs (e.g. a source-tree launcher contributing {@code "sources"}) adds them
 * itself — they are not part of this map. {@link #narrations()} carries the
 * subtitle text for the subset of inputs that declared one.</p>
 *
 * <p>Immutable; the builder hands a fresh instance to the launcher and keeps no
 * reference to its internals.</p>
 */
public final class LaunchRequest {

	private final Context context;
	private final Class<? extends Command> command;
	private final Map<String, Object> inputs;
	private final Map<String, String> narrations;

	LaunchRequest(Context context,
				  Class<? extends Command> command,
				  Map<String, Object> inputs,
				  Map<String, String> narrations) {
		this.context = context;
		this.command = command;
		this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
		this.narrations = Collections.unmodifiableMap(new LinkedHashMap<>(narrations));
	}

	public Context context() { return context; }

	public Class<? extends Command> command() { return command; }

	/** Merged name → value inputs, in declaration order. Unmodifiable. */
	public Map<String, Object> inputs() { return inputs; }

	/** Name → narration for the inputs that declared one. Unmodifiable. */
	public Map<String, String> narrations() { return narrations; }

	/**
	 * The inputs flattened to the alternating {@code name, value, name, value,
	 * ...} array shape expected by
	 * {@link org.scijava.command.CommandService#run(Class, boolean, Object...)}.
	 */
	public Object[] flatInputs() {
		Object[] out = new Object[inputs.size() * 2];
		int i = 0;
		for (Map.Entry<String, Object> e : inputs.entrySet()) {
			out[i++] = e.getKey();
			out[i++] = e.getValue();
		}
		return out;
	}
}
