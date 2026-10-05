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
package ch.unige.biochem.fiji.robot.widgets;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Frames of an animation, each shown for its own duration, encoded by ffmpeg into a GIF or an MP4.
 *
 * <p>The frames are pictures, not a screen recording: whoever adds them decides how long each one lasts, so the
 * animation plays at the speed of the story, whatever the time the program took to reach each frame (a stop-motion
 * film, see {@link StopMotion}). Frames are kept as PNG files in a working folder, not in memory.</p>
 */
public final class Film {

	/** The ffmpeg executable: a path, or a name on the {@code PATH}. */
	public static String FFMPEG = System.getProperty("ffmpeg", "ffmpeg");

	private final File folder;
	private final List<File> frames = new ArrayList<>();
	private final List<Integer> durations = new ArrayList<>();
	private int width = -1, height = -1;

	/** A film whose frames go to {@code folder}: emptied of its frames first. */
	public Film(File folder) {
		this.folder = folder;
		if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Cannot create " + folder);
		File[] old = folder.listFiles((dir, name) -> name.matches("frame-\\d+\\.png"));
		if (old != null) for (File f : old) f.delete();
	}

	/** Adds a frame, shown for {@code durationMs}. All frames must have the size of the first. */
	public void add(BufferedImage frame, int durationMs) {
		if (durationMs <= 0) throw new IllegalArgumentException("A frame lasts more than 0 ms: " + durationMs);
		if (width < 0) {
			width = frame.getWidth();
			height = frame.getHeight();
		}
		else if (frame.getWidth() != width || frame.getHeight() != height) {
			throw new IllegalArgumentException("Frame of " + frame.getWidth() + " x " + frame.getHeight()
					+ " in a film of " + width + " x " + height);
		}
		File file = new File(folder, String.format(Locale.ROOT, "frame-%05d.png", frames.size()));
		Painter.write(frame, file);
		frames.add(file);
		durations.add(durationMs);
	}

	/** Shows the last frame {@code ms} longer. */
	public void extend(int ms) {
		if (frames.isEmpty()) throw new IllegalStateException("No frame to extend");
		int last = durations.size() - 1;
		durations.set(last, durations.get(last) + ms);
	}

	public int frameCount() {
		return frames.size();
	}

	/** Total duration, in milliseconds. */
	public int duration() {
		return durations.stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Writes an animated GIF that loops, with one palette for the whole film (computed from the frames). GIF delays
	 * are in hundredths of a second: durations are rounded to them.
	 */
	public File writeGif(File gif) {
		return encode(gif, "split[a][b];[a]palettegen=stats_mode=full:reserve_transparent=0[p];"
				+ "[b][p]paletteuse=dither=none:diff_mode=rectangle", "-loop", "0");
	}

	/** Writes an H.264 MP4 at {@code fps} frames per second (frames are repeated to keep their durations). */
	public File writeMp4(File mp4, int fps) {
		return encode(mp4, "pad=ceil(iw/2)*2:ceil(ih/2)*2,fps=" + fps + ",format=yuv420p",
				"-c:v", "libx264", "-preset", "slow", "-crf", "20", "-movflags", "+faststart");
	}

	private File encode(File out, String filter, String... options) {
		if (frames.isEmpty()) throw new IllegalStateException("No frame");
		File list = new File(folder, "frames.ffconcat");
		try (PrintWriter w = new PrintWriter(list, StandardCharsets.UTF_8.name())) {
			w.println("ffconcat version 1.0");
			for (int i = 0; i < frames.size(); i++) {
				w.println("file '" + frames.get(i).getName() + "'");
				w.println(String.format(Locale.ROOT, "duration %.3f", durations.get(i) / 1000.0));
			}
			// the concat demuxer ignores the duration of the last entry: repeat it
			w.println("file '" + frames.get(frames.size() - 1).getName() + "'");
		}
		catch (IOException e) {
			throw new RuntimeException("Cannot write " + list, e);
		}
		File parent = out.getAbsoluteFile().getParentFile();
		if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Cannot create " + parent);
		List<String> cmd = new ArrayList<>();
		cmd.add(FFMPEG);
		cmd.add("-v");
		cmd.add("error");
		cmd.add("-y");
		cmd.add("-f");
		cmd.add("concat");
		cmd.add("-i");
		cmd.add(list.getName());
		cmd.add("-vf");
		cmd.add(filter);
		cmd.add("-fps_mode");
		cmd.add(out.getName().toLowerCase(Locale.ROOT).endsWith(".gif") ? "vfr" : "cfr");
		for (String o : options) cmd.add(o);
		cmd.add(out.getAbsolutePath());
		run(cmd);
		return out;
	}

	private void run(List<String> cmd) {
		try {
			Process p = new ProcessBuilder(cmd).directory(folder).redirectErrorStream(true).start();
			byte[] log = p.getInputStream().readAllBytes();
			int status = p.waitFor();
			if (status != 0) {
				throw new IllegalStateException("ffmpeg failed (" + status + "): " + String.join(" ", cmd) + "\n"
						+ new String(log, StandardCharsets.UTF_8));
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Could not run ffmpeg ('" + FFMPEG + "'): set -Dffmpeg=<path>", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	/** The frame files, in order. */
	public List<File> frameFiles() {
		return new ArrayList<>(frames);
	}
}
