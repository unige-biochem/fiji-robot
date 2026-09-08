/*-
 * #%L
 * A visible, recordable driver for SciJava commands — drives input
 * 		harvesting (and, later, java.awt.Robot gestures) so command runs can be
 * 		reproduced and recorded for tutorial videos.
 * %%
 * Copyright (C) 2026 University of Geneva, Department of Biochemistry
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
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
