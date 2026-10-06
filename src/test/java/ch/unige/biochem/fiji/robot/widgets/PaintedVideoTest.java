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

import ch.unige.biochem.fiji.robot.core.Assets;
import ch.unige.biochem.fiji.robot.core.ScreenRecorder;
import ch.unige.biochem.fiji.robot.core.Screenshotter;
import ch.unige.biochem.fiji.robot.core.Step;
import ch.unige.biochem.fiji.robot.core.Timeline;
import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A window filmed whole on a desktop, a menu chosen in its menu bar, and the gestures written to the timeline on
 * the clock of the film: the painted equivalent of a screen recording for a video. Needs a display (the menus
 * open as on screen), not the real mouse.
 */
public class PaintedVideoTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private JFrame frame;

	@After
	public void tearDown() throws Exception {
		Timeline.clock = Timeline.WALL_CLOCK;
		Timeline.recordingBounds = null;
		Assets.ROOT = new File("target/video-assets");
		if (frame != null) SwingUtilities.invokeAndWait(frame::dispose);
	}

	@Test
	public void filmsAMenuCascadeOnADesktop() throws Exception {
		Assume.assumeFalse("needs a display", GraphicsEnvironment.isHeadless());
		ScreenRecorder.ENABLED = false;
		Screenshotter.ENABLED = false;
		Timeline.ENABLED = true;
		Step.AUTO_SNAP_END = false;
		Assets.ROOT = tmp.newFolder("assets");
		File dir = Assets.session("PaintedVideo");

		int[] chosen = { 0 };
		SwingUtilities.invokeAndWait(() -> {
			frame = new JFrame("Demo");
			JMenuBar bar = new JMenuBar();
			JMenu file = new JMenu("File");
			JMenu sub = new JMenu("Import");
			JMenuItem item = new JMenuItem("Demo slices");
			item.addActionListener(e -> chosen[0]++);
			sub.add(new JMenuItem("Other"));
			sub.add(item);
			file.add(new JMenuItem("Open"));
			file.add(sub);
			bar.add(file);
			frame.setJMenuBar(bar);
			JPanel content = new JPanel();
			content.setBackground(Color.DARK_GRAY);
			content.setPreferredSize(new Dimension(400, 300));
			frame.setContentPane(content);
			frame.pack();
			frame.setLocation(50, 50);
			frame.setVisible(true);
		});
		Thread.sleep(300);

		Film film = new Film(tmp.newFolder("frames"));
		Color violet = new Color(0x8C16DB);
		// a 600 x 400 desktop, the window's root pane at 100, 60
		StopMotion camera = new StopMotion(film, frame.getRootPane(), new Rectangle(-100, -60, 600, 400), 1)
				.onDesktop(violet, "Demo");
		Timeline.clock = camera::time;
		Timeline.recordingBounds = new Rectangle(0, 0, 600, 400);
		VirtualPointer pointer = camera.pointer(frame.getRootPane(), new Point(300, 200));

		Step.begin("menu", "We import the demo slices.");
		camera.shoot(500);
		pointer.chooseInMenuBar(frame.getJMenuBar(), "File", "Import", "Demo slices");
		camera.shoot(500);
		Step.say("Then we drag in the window.");
		pointer.moveTo(200, 150, 200);
		pointer.drag(MouseEvent.BUTTON1, 250, 180, 200);
		Step.end();
		SwingUtilities.invokeAndWait(() -> { });

		assertEquals(1, chosen[0]);
		BufferedImage first = ImageIO.read(film.frameFiles().get(0));
		assertEquals(600, first.getWidth());
		assertEquals(violet.getRGB(), first.getRGB(5, 5)); // the desktop
		assertEquals(Color.DARK_GRAY.getRGB(), first.getRGB(150, 250)); // the content of the window
		// a frame with the cascade open: the submenu's popup over the content
		boolean submenuShown = false;
		for (File f : film.frameFiles()) {
			BufferedImage img = ImageIO.read(f);
			if (img.getRGB(235, 140) != Color.DARK_GRAY.getRGB() && img.getRGB(235, 140) != violet.getRGB()) {
				submenuShown = true;
			}
		}
		assertTrue("the menus are painted where they open", submenuShown);

		String json = new String(Files.readAllBytes(new File(dir, Timeline.FILENAME).toPath()), StandardCharsets.UTF_8);
		assertTrue(json, json.contains("\"recordingBounds\": {\"x\":0,\"y\":0,\"w\":600,\"h\":400}"));
		assertTrue(json, json.contains("\"type\":\"mouse.click\""));
		assertTrue(json, json.contains("\"type\":\"mouse.drag\""));
		assertTrue(json, json.contains("\"text\":\"Then we drag in the window.\""));
		assertFalse("no wall clock in a painted timeline", json.contains("startedAt"));
		// the click and drag are where the frames show the pointer
		assertTrue(json, json.contains("\"x\":300,\"y\":210,\"endX\":350,\"endY\":240"));
		// the drag lasts its 200 ms of moves and the frame of its press (160 ms): released 360 ms after its press
		java.util.regex.Matcher drag = java.util.regex.Pattern
				.compile("\"type\":\"mouse.drag\",\"tMs\":([0-9]+),.*?\"endMs\":([0-9]+)").matcher(json);
		assertTrue(json, drag.find());
		assertEquals(json, 360, Long.parseLong(drag.group(2)) - Long.parseLong(drag.group(1)));
		// recorded once: the synthetic events are hidden from the recorder of the real mouse
		assertEquals(json, 1, json.split("\"type\":\"mouse.drag\"", -1).length - 1);
		// the menu of the bar opened, the item chosen
		assertEquals(json, 2, json.split("\"type\":\"mouse.click\"", -1).length - 1);
	}

	@Test
	public void fillsADialogShownInTheFilm() throws Exception {
		Assume.assumeFalse("needs a display", GraphicsEnvironment.isHeadless());
		Timeline.ENABLED = false;
		javax.swing.JDialog[] dialog = { null };
		javax.swing.JComboBox<String> combo = new javax.swing.JComboBox<>(new String[] { "mouse", "rat" });
		javax.swing.JCheckBox check = new javax.swing.JCheckBox();
		javax.swing.JFormattedTextField number = new javax.swing.JFormattedTextField(java.text.NumberFormat.getIntegerInstance());
		number.setValue(10);
		number.setColumns(6);
		SwingUtilities.invokeAndWait(() -> {
			frame = new JFrame("Main");
			JPanel content = new JPanel();
			content.setBackground(Color.DARK_GRAY);
			content.setPreferredSize(new Dimension(500, 400));
			frame.setContentPane(content);
			frame.pack();
			frame.setLocation(50, 50);
			frame.setVisible(true);
			// as a SciJava harvester: each label, then the container of its widget
			dialog[0] = new javax.swing.JDialog(frame, "Settings", false);
			JPanel inputs = new JPanel(new java.awt.GridLayout(0, 2));
			inputs.add(new javax.swing.JLabel("Model"));
			inputs.add(wrap(combo));
			inputs.add(new javax.swing.JLabel("Ensemble"));
			inputs.add(wrap(check));
			inputs.add(new javax.swing.JLabel("Size"));
			inputs.add(wrap(number));
			JPanel all = new JPanel(new java.awt.BorderLayout());
			all.add(inputs, java.awt.BorderLayout.CENTER);
			all.add(new javax.swing.JButton("OK"), java.awt.BorderLayout.SOUTH);
			dialog[0].setContentPane(all);
			dialog[0].pack();
			// far from where the film shows it
			dialog[0].setLocation(900, 600);
			dialog[0].setVisible(true);
		});
		Thread.sleep(300);

		Film film = new Film(tmp.newFolder("frames"));
		Color violet = new Color(0x8C16DB);
		StopMotion camera = new StopMotion(film, frame.getRootPane(), new Rectangle(-50, -50, 600, 500), 1)
				.onDesktop(violet, "Main");
		VirtualPointer pointer = camera.pointer(frame.getRootPane(), new Point(400, 350));
		camera.showWindow(dialog[0], new Point(100, 80));
		new PaintedDialog(dialog[0], pointer).set("Model", "rat").set("Ensemble", true).set("Size", "25");
		SwingUtilities.invokeAndWait(() -> { });

		assertEquals("rat", combo.getSelectedItem());
		assertTrue(check.isSelected());
		assertEquals(25L, ((Number) number.getValue()).longValue());
		// a frame shows the list of the combo box, under the combo box as the film shows it
		Point listAt = new Point();
		SwingUtilities.invokeAndWait(() -> {
			Point c = SwingUtilities.convertPoint(combo, 10, combo.getHeight() + 8, dialog[0].getRootPane());
			listAt.setLocation(c.x + 100 + 50, c.y + 80 + 50); // the dialog at 100, 80; the desktop from -50, -50
		});
		boolean listShown = false;
		for (File f : film.frameFiles()) {
			BufferedImage img = ImageIO.read(f);
			int rgb = img.getRGB(listAt.x, listAt.y);
			if (rgb != Color.DARK_GRAY.getRGB() && rgb != violet.getRGB()) listShown = true;
		}
		assertTrue("the list of the combo box is painted", listShown);
		SwingUtilities.invokeAndWait(dialog[0]::dispose);
	}

	private static JPanel wrap(java.awt.Component c) {
		JPanel p = new JPanel();
		p.add(c);
		return p;
	}
}
