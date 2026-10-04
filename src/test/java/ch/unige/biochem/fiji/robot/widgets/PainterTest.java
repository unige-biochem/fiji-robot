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

import org.junit.Test;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless test of {@link Painter}: components without a window are painted
 * offscreen, so no display is needed.
 */
public class PainterTest {

	private static JPanel panel(Color left, Color right) {
		JPanel p = new JPanel(null);
		JPanel a = new JPanel();
		a.setBackground(left);
		a.setBounds(0, 0, 50, 40);
		JPanel b = new JPanel();
		b.setBackground(right);
		b.setBounds(50, 0, 50, 40);
		p.add(a);
		p.add(b);
		p.setSize(100, 40);
		return p;
	}

	@Test
	public void paintsScaledAndCropped() {
		JPanel p = panel(Color.RED, Color.BLUE);
		BufferedImage whole = Painter.paint(p, 2);
		assertEquals(200, whole.getWidth());
		assertEquals(80, whole.getHeight());
		assertEquals(Color.RED.getRGB(), whole.getRGB(10, 10));
		assertEquals(Color.BLUE.getRGB(), whole.getRGB(190, 70));

		BufferedImage right = Painter.paint(p, new Rectangle(50, 0, 50, 40), 1);
		assertEquals(50, right.getWidth());
		assertEquals(Color.BLUE.getRGB(), right.getRGB(0, 0));
		assertEquals(Color.BLUE.getRGB(), right.getRGB(49, 39));
	}

	@Test
	public void comparesPictures() {
		BufferedImage a = Painter.paint(panel(Color.RED, Color.BLUE), 1);
		assertTrue(Painter.sameImage(a, Painter.paint(panel(Color.RED, Color.BLUE), 1)));
		assertFalse(Painter.sameImage(a, Painter.paint(panel(Color.RED, Color.GREEN), 1)));
		assertTrue(Painter.sameImage(a, Painter.paintWhenStable(panel(Color.RED, Color.BLUE), 1, 300, 5000)));
	}

	@Test
	public void paintsAClosedMenu() {
		JMenuBar bar = new JMenuBar();
		JMenu file = new JMenu("File");
		JMenu export = new JMenu("Export");
		JMenuItem open = new JMenuItem("Open...");
		file.add(open);
		file.add(export);
		export.add(new JMenuItem("As PNG"));
		bar.add(file);

		BufferedImage menu = Painter.paintMenu(bar, 1, "File");
		BufferedImage highlighted = Painter.paintMenu(bar, 1, "File", "Open...");
		assertEquals(file.getPopupMenu().getPreferredSize().width, menu.getWidth());
		assertFalse("the last item of the path is highlighted", Painter.sameImage(menu, highlighted));
		assertFalse("the highlight is removed afterwards", open.getModel().isArmed());
		assertTrue(Painter.paintMenu(bar, 2, "File", "Export").getHeight() > 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void listsTheItemsOfAWrongPath() {
		JMenuBar bar = new JMenuBar();
		bar.add(new JMenu("File"));
		Painter.paintMenu(bar, 1, "Edit");
	}
}
