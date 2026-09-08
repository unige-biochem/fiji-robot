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
 * An input resolved <em>before</em> the command launches — i.e. pre-set on the
 * module so that, by the time the command's preprocessor chain runs, the input
 * is already satisfied (and any matching preprocessor / the harvester skips
 * it).
 *
 * <p>In a future visible-mode increment, a {@code PreSetResolution} that mirrors
 * a SciJava preprocessor (e.g. the active-image or active-BDV preprocessor) will
 * also carry a pre-launch gesture that establishes the ambient UI state the real
 * preprocessor reads — selecting the right window before {@code CommandService
 * .run} is called. The ambient state set before launch survives into the
 * preprocessor chain, which is why no in-chain machinery is needed.</p>
 *
 * <p>Accepted only by {@code CmdExecutor.PreLaunch#preSet}.</p>
 */
public interface PreSetResolution extends InputResolution {
}
