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

import javax.imageio.ImageIO;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Java-side screenshot capture for tutorial assets, built on
 * {@link java.awt.Robot#createScreenCapture(Rectangle)}.
 *
 * <p>PNGs land in {@link Assets#dir()} as {@code "NNN-name.png"} where NNN is
 * an auto-incrementing counter — so the alphabetical sort matches capture
 * order.</p>
 *
 * <p>Toggle off via {@link #ENABLED} for fast iteration. When disabled, every
 * method is a no-op that returns {@code null}.</p>
 */
public class Screenshotter {

	public static boolean ENABLED = true;

	private static int counter = 0;

	/** Capture the bounds of the given frame (includes its native title bar). */
	public static File ofFrame(Frame frame, String name) {
		return ofRegion(frame.getBounds(), name);
	}

	/** Capture a screen-absolute rectangle. */
	public static File ofRegion(Rectangle region, String name) {
		if (!ENABLED) return null;
		BufferedImage img = Ui.robot().createScreenCapture(region);
		return write(img, name);
	}

	/** Capture the whole target screen (per {@link Ui#targetScreenBounds()}). */
	public static File ofTargetScreen(String name) {
		return ofRegion(Ui.targetScreenBounds(), name);
	}

	/**
	 * Capture the recording region (per {@link Ui#recordingBoundsLogical()}) —
	 * same crop as the screen-recorded video, so the PNG and the MP4 show the
	 * same content with no bottom-strip popup.
	 */
	public static File ofRecordingArea(String name) {
		return ofRegion(Ui.recordingBoundsLogical(), name);
	}

	private static File write(BufferedImage img, String name) {
		String safe = name.replaceAll("[^A-Za-z0-9._-]+", "_");
		String fileName = String.format("%03d-%s.png", ++counter, safe);
		File out = new File(Assets.dir(), fileName);
		try {
			ImageIO.write(img, "png", out);
		} catch (IOException e) {
			throw new RuntimeException("Could not write screenshot " + out, e);
		}
		return out;
	}

	static void resetCounter() { counter = 0; }
}
