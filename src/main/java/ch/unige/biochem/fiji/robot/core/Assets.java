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
package ch.unige.biochem.fiji.robot.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Per-demo output session. Pick a name once at the top of a demo's
 * {@code main} via {@link #session(String)}; {@link Screenshotter},
 * {@link ScreenRecorder} and {@link Step} all write inside the resulting
 * directory.
 *
 * <p>Default root is {@code target/video-assets/} (under the Maven build dir,
 * so {@code mvn clean} wipes it — which is what we want during iteration).
 * Override {@link #ROOT} before {@link #session(String)} to write somewhere
 * persistent.</p>
 */
public class Assets {

	public static File ROOT = new File("target/video-assets");

	private static File currentDir;
	private static String currentDemo;

	/**
	 * Start (or reset) a session for {@code demoName}. Creates the directory
	 * and clears any prior {@code timeline.json} so re-running the demo gives a
	 * fresh transcript. Does not delete existing PNGs / MP4s — those are
	 * overwritten as the counters rewrite the same NN-name.* slots.
	 */
	public static File session(String demoName) {
		currentDemo = demoName;
		currentDir = new File(ROOT, demoName);
		if (!currentDir.exists() && !currentDir.mkdirs()) {
			throw new RuntimeException("Could not create assets dir " + currentDir);
		}
		File timeline = new File(currentDir, Timeline.FILENAME);
		if (timeline.exists()) {
			try {
				Files.delete(timeline.toPath());
			} catch (IOException e) {
				throw new RuntimeException("Could not clear " + timeline, e);
			}
		}
		Screenshotter.resetCounter();
		ScreenRecorder.resetCounter();
		Timeline.onSessionStart();
		return currentDir;
	}

	/** Current session directory; auto-creates a {@code "default"} session if none was opened. */
	public static File dir() {
		if (currentDir == null) session("default");
		return currentDir;
	}

	public static String demoName() { return currentDemo; }
}
