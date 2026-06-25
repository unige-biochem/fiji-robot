package ch.unige.biochem.fiji.robot;

import org.scijava.Context;
import org.scijava.command.Command;

/**
 * The ambient information a {@link Gesture} needs when it runs: the SciJava
 * {@link Context} the command runs in, the command class, and the name of the
 * input this gesture resolves.
 *
 * <p>Built fresh per input by {@link LaunchRequest#runPreSetGestures()} and
 * handed to {@link Gesture#perform(GestureContext)}. Immutable.</p>
 */
public final class GestureContext {

	private final Context context;
	private final Class<? extends Command> command;
	private final String inputName;

	public GestureContext(Context context, Class<? extends Command> command, String inputName) {
		this.context = context;
		this.command = command;
		this.inputName = inputName;
	}

	/** The SciJava context this run executes in. */
	public Context context() { return context; }

	/** The command being launched. */
	public Class<? extends Command> command() { return command; }

	/** The {@code @Parameter} name this gesture resolves. */
	public String inputName() { return inputName; }
}