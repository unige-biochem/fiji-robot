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
 * One command input, resolved by some mechanism.
 *
 * <p>An {@code InputResolution} bundles everything the different
 * <em>projections</em> of a command run need to know about a single
 * {@code @Parameter}, so that each input kind owns its own behaviour rather
 * than scattering it across the executor, the script renderer and the
 * recorder:</p>
 *
 * <ul>
 *   <li><b>programmatic projection</b> — {@link #value()} is the concrete value
 *       passed to {@code CommandService.run(cmd, true, name, value, ...)};</li>
 *   <li><b>script projection</b> — {@link #value()} is rendered to a headless
 *       Groovy literal by {@code GroovyRender};</li>
 *   <li><b>timeline projection</b> — {@link #narration()} is the subtitle text
 *       fired when this input is set.</li>
 * </ul>
 *
 * <p>The <em>visible</em> projection (a {@code java.awt.Robot} gesture that
 * drives the real widget / window) is intentionally absent from this interface
 * for now: the first increment of the library establishes the builder grammar
 * and the programmatic + script projections. A gesture hook will be added as a
 * separate capability interface so resolvers that can be driven visibly opt in
 * without forcing every resolver to implement a Robot gesture.</p>
 *
 * <p>The two sub-interfaces {@link PreSetResolution} and
 * {@link DialogResolution} carry no extra methods — they exist purely so the
 * builder grammar can keep "resolved before the command launches" distinct
 * from "harvested from the command's dialog after it launches" at compile
 * time.</p>
 */
public interface InputResolution {

	/**
	 * The value this input resolves to in programmatic mode — exactly what
	 * would be passed as the value half of a {@code "name", value} pair to
	 * {@link org.scijava.command.CommandService#run}.
	 */
	Object value();

	/**
	 * Optional narration describing this input, fired as a subtitle when the
	 * input is set. {@code null} means "set this silently" — used for
	 * boilerplate parameters a tutorial doesn't talk about.
	 */
	String narration();
}
