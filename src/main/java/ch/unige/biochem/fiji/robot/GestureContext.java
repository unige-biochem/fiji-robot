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
