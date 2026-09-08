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
