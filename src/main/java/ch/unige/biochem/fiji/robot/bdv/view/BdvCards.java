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
package ch.unige.biochem.fiji.robot.bdv.view;

import ch.unige.biochem.fiji.robot.widgets.Painter;

import bdv.ui.CardPanel;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The cards of a BigDataViewer {@link CardPanel} (right of the viewer,
 * {@code bdvHandle.getCardPanel()}), set programmatically and painted with
 * {@link Painter}: expand only the cards a picture is about, paint one card
 * whole, or the column of cards.
 *
 * <p>A card is painted whole even when the card panel is too short to show it:
 * {@code printAll} paints a component at its own size, whatever its scroll pane
 * shows. Programmatic, unlike {@link Bdv} (Robot gestures): for pictures, and to
 * set the state of a window before a recording.</p>
 */
public final class BdvCards {

	private BdvCards() {}

	/** The keys of the cards, top to bottom (a card added with a title only has its title as key). */
	public static List<Object> keys(CardPanel panel) {
		List<Object> keys = new ArrayList<>();
		for (Component card : container(panel).getComponents()) keys.add(key(card));
		return keys;
	}

	/** The card of this key: its header and its content. */
	public static JComponent card(CardPanel panel, Object key) {
		int index = panel.indexOf(key);
		if (index < 0) throw new IllegalArgumentException("No card '" + key + "'. Cards: " + keys(panel));
		return (JComponent) container(panel).getComponent(index);
	}

	/** Expands these cards, collapses the others, and lays the window out again. */
	public static void expandOnly(CardPanel panel, Object... keys) {
		List<Object> expanded = Arrays.asList(keys);
		for (Object key : keys) card(panel, key); // throws on an unknown key
		onEdt(() -> {
			for (Object key : keys(panel)) panel.setCardExpanded(key, expanded.contains(key));
			layout(panel);
		});
	}

	/** Expands or collapses one card, and lays the window out again. */
	public static void setExpanded(CardPanel panel, Object key, boolean expanded) {
		card(panel, key);
		onEdt(() -> {
			panel.setCardExpanded(key, expanded);
			layout(panel);
		});
	}

	/** Scrolls the card panel so that the card shows, as far as it fits. */
	public static void scrollTo(CardPanel panel, Object key) {
		JComponent card = card(panel, key);
		onEdt(() -> card.scrollRectToVisible(new Rectangle(0, 0, card.getWidth(), card.getHeight())));
	}

	/** Paints one card, header included, {@code scale} times bigger. */
	public static BufferedImage paintCard(CardPanel panel, Object key, double scale) {
		return Painter.paint(card(panel, key), scale);
	}

	/** Paints the column of cards, all of it, {@code scale} times bigger. */
	public static BufferedImage paintCards(CardPanel panel, double scale) {
		return Painter.paint(container(panel), scale);
	}

	private static Container container(CardPanel panel) {
		return panel.getComponent();
	}

	/** {@code Card.getKey()}: the card class is private to {@link CardPanel}. */
	private static Object key(Component card) {
		try {
			Method getKey = card.getClass().getMethod("getKey");
			getKey.setAccessible(true);
			return getKey.invoke(card);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Not a card of a CardPanel: " + card.getClass().getName(), e);
		}
	}

	/** Lays out the window again (or the cards alone, outside a window), so that pictures show the new sizes. */
	private static void layout(CardPanel panel) {
		Container container = container(panel);
		Window window = SwingUtilities.getWindowAncestor(container);
		if (window != null) {
			window.validate();
			return;
		}
		invalidateTree(container);
		container.setSize(container.getWidth(), container.getPreferredSize().height);
		layoutTree(container); // validate() does nothing outside a window
	}

	private static void layoutTree(Component c) {
		if (!(c instanceof Container)) return;
		((Container) c).doLayout();
		for (Component child : ((Container) c).getComponents()) layoutTree(child);
	}

	private static void invalidateTree(Component c) {
		c.invalidate();
		if (c instanceof Container) for (Component child : ((Container) c).getComponents()) invalidateTree(child);
	}

	private static void onEdt(Runnable task) {
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
			return;
		}
		try {
			SwingUtilities.invokeAndWait(task);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
		catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) throw (RuntimeException) cause;
			throw new RuntimeException(cause);
		}
	}
}
