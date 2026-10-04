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
package ch.unige.biochem.fiji.robot.bdv;

import ch.unige.biochem.fiji.robot.bdv.view.BdvCards;

import bdv.ui.CardPanel;
import org.junit.Test;

import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Headless test of {@link BdvCards} on a card panel outside any window. */
public class BdvCardsTest {

	private static CardPanel panel() {
		CardPanel panel = new CardPanel();
		for (String title : new String[] { "First", "Second", "Third" }) {
			JLabel content = new JLabel(title + " content");
			content.setPreferredSize(new Dimension(200, 100));
			panel.addCard(title, content, true);
		}
		panel.getComponent().setSize(300, panel.getComponent().getPreferredSize().height);
		return panel;
	}

	@Test
	public void listsAndExpandsCards() {
		CardPanel panel = panel();
		assertEquals(Arrays.asList("First", "Second", "Third"), BdvCards.keys(panel));
		BdvCards.expandOnly(panel, "Second");
		assertFalse(panel.isCardExpanded("First"));
		assertTrue(panel.isCardExpanded("Second"));
		assertFalse(panel.isCardExpanded("Third"));
	}

	@Test
	public void paintsOneCardWhole() {
		CardPanel panel = panel();
		BdvCards.expandOnly(panel, "Second");
		BufferedImage card = BdvCards.paintCard(panel, "Second", 2);
		assertEquals(2 * BdvCards.card(panel, "Second").getHeight(), card.getHeight());
		assertTrue("the expanded card is taller than a header",
				BdvCards.card(panel, "Second").getHeight() > BdvCards.card(panel, "First").getHeight() + 50);
	}

	@Test(expected = IllegalArgumentException.class)
	public void namesTheCardsOfAWrongKey() {
		BdvCards.card(panel(), "Fourth");
	}
}
