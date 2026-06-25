package ch.unige.biochem.fiji.robot;

import org.scijava.command.CommandService;
import org.scijava.module.Module;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Factory methods for the built-in {@link Launcher} kinds.
 *
 * <p>This first increment ships only {@link #programmaticLauncher()} — the
 * null-gesture launcher that runs the command straight through the
 * {@link CommandService}. The visible launchers (search bar, menu bar,
 * source-tree right-click) arrive with the Robot layer; modelling
 * "programmatic" as a launcher of its own keeps the invariant clean — every
 * plan names exactly one launcher, even the headless one.</p>
 */
public final class Launchers {

	private Launchers() {}

	/**
	 * Runs the command via {@code CommandService.run(cmd, true, inputs)} with
	 * every input pre-set, so no harvester dialog is shown. Blocks until the
	 * command finishes and returns the completed module, so outputs are
	 * readable via {@link Module#getOutput(String)}.
	 */
	public static Launcher programmaticLauncher() {
		return new ProgrammaticLauncher();
	}

	private static final class ProgrammaticLauncher implements Launcher {
		@Override
		public Module launch(LaunchRequest request) {
			CommandService cs = request.context().service(CommandService.class);
			Future<? extends Module> future =
					cs.run(request.command(), true, request.flatInputs());
			try {
				return future.get();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(
						"Interrupted while running " + request.command().getSimpleName(), e);
			}
			catch (ExecutionException e) {
				throw new RuntimeException(
						"Failed to run " + request.command().getSimpleName(), e.getCause());
			}
		}
	}
}
