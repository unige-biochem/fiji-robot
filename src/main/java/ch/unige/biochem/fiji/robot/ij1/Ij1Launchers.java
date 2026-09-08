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
package ch.unige.biochem.fiji.robot.ij1;

import ch.unige.biochem.fiji.robot.Launcher;
import ch.unige.biochem.fiji.robot.LaunchRequest;
import ch.unige.biochem.fiji.robot.core.Ui;
import ch.unige.biochem.fiji.robot.widgets.Harvester;

import org.scijava.module.Module;

/**
 * Factory methods for the IJ1-bound {@link Launcher} kinds — the ImageJ1 mirror
 * of the core {@code Launchers} factory.
 *
 * <p>Where {@code programmaticLauncher()} runs the command headlessly,
 * {@link #searchLauncher(String)} runs it the way a user does: it types the query
 * into Fiji's legacy search bar and lets the harvester dialog drive the remaining
 * inputs. Choosing the launcher <em>is</em> choosing the mode — there is no global
 * visible/programmatic switch.</p>
 */
public final class Ij1Launchers {

	private Ij1Launchers() {}

	/**
	 * Launches the command via Fiji's legacy search bar, then drives its dialog.
	 *
	 * <p>The launch sequence is the visible two-phase model end to end:</p>
	 * <ol>
	 *   <li>{@linkplain LaunchRequest#runPreSetGestures() run the pre-set gestures}
	 *       — e.g. {@code selectActiveImage(...)} activates its window, so the
	 *       command's {@code LegacyImagePreprocessor} reads it during the run;</li>
	 *   <li>{@linkplain Fiji#searchAndRun(String) type the query and press Enter}
	 *       — the single trigger;</li>
	 *   <li>{@linkplain Harvester#runOpenDialog drive only the dialog inputs}
	 *       through the harvester, then click OK.</li>
	 * </ol>
	 *
	 * <p>Returns {@code null}: the legacy search path drops the command's
	 * {@code Future}, so outputs are not recoverable. Use
	 * {@code programmaticLauncher()} when you need the completed module.</p>
	 *
	 * @param query a short, unambiguous prefix of the command's display name
	 */
	public static Launcher searchLauncher(String query) {
		if (query == null || query.isEmpty()) {
			throw new IllegalArgumentException("search query must not be null or empty");
		}
		return new SearchLauncher(query);
	}

	private static final class SearchLauncher implements Launcher {
		private final String query;

		SearchLauncher(String query) {
			this.query = query;
		}

		@Override
		public Module launch(LaunchRequest request) {
			if (Ui.FORCE_DOT_DECIMAL) Ui.useDotDecimalSeparator();
			request.runPreSetGestures();
			Fiji.searchAndRun(query);
			Harvester.runOpenDialog(request.command(), request.dialogNarrations(),
					request.dialogArgs());
			return null;
		}
	}
}
