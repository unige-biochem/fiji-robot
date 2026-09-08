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

/**
 * Factory methods for the built-in {@link InputResolution} kinds.
 *
 * <p>Static-import this class at the call site so a command run reads as a
 * small declarative recipe:</p>
 *
 * <pre>
 * import static ch.unige.biochem.fiji.robot.Resolutions.*;
 *
 * CmdExecutor.of(context, MyCommand.class)
 *     .preSet("a-param", programmatic(value))
 *     .withLauncher(programmaticLauncher())
 *     .postSet("min", fromDialog(0.0, "We set the lower display bound."))
 *     .launch();
 * </pre>
 *
 * <p>This first increment ships the two context-free kinds — {@link
 * #programmatic} (a pre-set value) and {@link #fromDialog} (a value harvested
 * from the dialog). Context-bound kinds that resolve a live object by name at
 * launch time ({@code selectActiveImagePlus}, {@code selectActiveBdv}) arrive
 * with the visible-gesture layer, in their respective binding modules.</p>
 */
public final class Resolutions {

	private Resolutions() {}

	// ===== Pre-set ============================================================

	/** A value pre-set on the module before launch, with no narration. */
	public static PreSetResolution programmatic(Object value) {
		return new SimplePreSet(value, null);
	}

	/** A value pre-set on the module before launch, with a narration subtitle. */
	public static PreSetResolution programmatic(Object value, String narration) {
		return new SimplePreSet(value, narration);
	}

	// ===== Dialog =============================================================

	/** A value harvested from the command's dialog, with no narration. */
	public static DialogResolution fromDialog(Object value) {
		return new SimpleDialog(value, null);
	}

	/** A value harvested from the command's dialog, with a narration subtitle. */
	public static DialogResolution fromDialog(Object value, String narration) {
		return new SimpleDialog(value, narration);
	}

	// ===== Implementations ====================================================

	private static final class SimplePreSet implements PreSetResolution {
		private final Object value;
		private final String narration;

		SimplePreSet(Object value, String narration) {
			this.value = value;
			this.narration = narration;
		}

		@Override public Object value() { return value; }
		@Override public String narration() { return narration; }
	}

	private static final class SimpleDialog implements DialogResolution {
		private final Object value;
		private final String narration;

		SimpleDialog(Object value, String narration) {
			this.value = value;
			this.narration = narration;
		}

		@Override public Object value() { return value; }
		@Override public String narration() { return narration; }
	}
}
