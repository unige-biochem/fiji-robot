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

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless test of {@link StopMotion} and {@link VirtualPointer}: synthetic events reach a component without a
 * window, frames are painted with the pointer drawn, and ffmpeg (if on the PATH) encodes them.
 */
public class StopMotionTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void dragsAndFilms() throws Exception {
		JPanel p = new JPanel();
		p.setBackground(Color.DARK_GRAY);
		p.setSize(200, 100);
		List<String> events = new ArrayList<>();
		MouseAdapter mouse = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent e) { events.add("press " + e.getX() + "," + e.getY() + " shift=" + e.isShiftDown()); }
			@Override public void mouseDragged(MouseEvent e) { events.add("drag"); }
			@Override public void mouseReleased(MouseEvent e) { events.add("release " + e.getX() + "," + e.getY()); }
		};
		p.addMouseListener(mouse);
		p.addMouseMotionListener(mouse);
		p.addKeyListener(new KeyAdapter() {
			@Override public void keyPressed(KeyEvent e) { events.add("key " + e.getKeyCode()); }
		});

		Film film = new Film(tmp.newFolder("frames"));
		StopMotion camera = new StopMotion(film, p, new Rectangle(0, 0, 200, 100), 1);
		VirtualPointer pointer = camera.pointer(p, new Point(20, 20));
		pointer.holdKeys(InputEvent.SHIFT_DOWN_MASK, "Shift");
		pointer.drag(MouseEvent.BUTTON1, 150, 80, 400);
		pointer.releaseKeys();
		pointer.type(KeyEvent.VK_A, "Ctrl + A", 500);

		assertEquals("key " + KeyEvent.VK_SHIFT, events.get(0));
		assertEquals("press 20,20 shift=true", events.get(1));
		assertEquals("drag", events.get(2));
		assertEquals("release 150,80", events.get(events.size() - 2));
		assertEquals("key " + KeyEvent.VK_A, events.get(events.size() - 1));
		// press, 10 steps of 40 ms, release, typed key
		assertEquals(13, film.frameCount());
		assertEquals(160 + 400 + 160 + 500, film.duration());

		BufferedImage first = javax.imageio.ImageIO.read(film.frameFiles().get(0));
		// the pointer is drawn white at its tip, below and right of 20,20
		assertEquals(Color.WHITE.getRGB(), first.getRGB(22, 30));
		BufferedImage last = javax.imageio.ImageIO.read(film.frameFiles().get(12));
		assertEquals(Color.DARK_GRAY.getRGB(), last.getRGB(22, 30));
		assertNotEquals(Color.DARK_GRAY.getRGB(), last.getRGB(152, 90));

		Assume.assumeTrue("ffmpeg on the PATH", ffmpeg());
		File gif = film.writeGif(new File(tmp.getRoot(), "drag.gif"));
		assertTrue(gif.length() > 0);
		File mp4 = film.writeMp4(new File(tmp.getRoot(), "drag.mp4"), 25);
		assertTrue(mp4.length() > 0);
	}

	private static boolean ffmpeg() {
		try {
			return new ProcessBuilder(Film.FFMPEG, "-version").redirectErrorStream(true).start().waitFor() == 0;
		}
		catch (Exception e) {
			return false;
		}
	}
}
