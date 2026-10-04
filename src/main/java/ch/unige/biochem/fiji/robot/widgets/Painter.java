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

import ch.unige.biochem.fiji.robot.core.Ui;

import javax.imageio.ImageIO;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pictures of Swing components <em>as they are drawn</em>, not of the screen:
 * each method paints a component into an image with {@link Component#printAll},
 * on the event dispatch thread.
 *
 * <p>Unlike {@link ch.unige.biochem.fiji.robot.core.Screenshotter} (a
 * {@link java.awt.Robot} screen grab), a painted picture does not depend on the
 * screen: it is right when the window is covered by another application,
 * partly off screen, or the session runs over remote desktop with the screen
 * locked, and it never shows the mouse pointer. Native window decorations are
 * not painted, so a picture is the same on every machine with the same look and
 * feel and fonts.</p>
 *
 * <p>{@code scale} paints the component bigger without laying it out again:
 * borders and shapes stay sharp; images drawn by the component (a viewer's
 * rendered picture) are resampled. Text laid out by Swing (labels, menus,
 * buttons) gets cut: hinted glyphs painted bigger are 1 to 3 % wider than their
 * layout. Paint such components at scale 1, or lay them out bigger (FlatLaf's
 * {@code flatlaf.uiScale}); text drawn freely, as in a viewer overlay, has no
 * such limit.</p>
 *
 * <p>Pure Swing, like the rest of the
 * widget layer: a toolkit-specific picture (a BigDataViewer card) belongs to its
 * binding, built on these primitives.</p>
 */
public final class Painter {

	private Painter() {}

	/** Pause between two paints of {@link #paintWhenStable}. */
	public static long STABLE_INTERVAL_MS = 250;

	/** Paints the whole component, {@code scale} times bigger. */
	public static BufferedImage paint(Component c, double scale) {
		return paint(c, new Rectangle(0, 0, c.getWidth(), c.getHeight()), scale);
	}

	/**
	 * Paints the {@code region} of the component (in its own coordinates, see
	 * {@link #boundsIn}), {@code scale} times bigger.
	 */
	public static BufferedImage paint(Component c, Rectangle region, double scale) {
		if (region.width <= 0 || region.height <= 0) {
			throw new IllegalStateException("Nothing to paint: " + c.getClass().getSimpleName()
					+ " region is " + region.width + " x " + region.height + " (not laid out, or hidden?)");
		}
		BufferedImage img = new BufferedImage((int) Math.round(region.width * scale),
				(int) Math.round(region.height * scale), BufferedImage.TYPE_INT_RGB);
		onEdt(() -> {
			Graphics2D g = img.createGraphics();
			try {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.scale(scale, scale);
				g.translate(-region.x, -region.y);
				g.clipRect(region.x, region.y, region.width, region.height);
				c.printAll(g);
			}
			finally {
				g.dispose();
			}
		});
		return img;
	}

	/**
	 * Paints the component until the picture stops changing: for components
	 * that draw asynchronously and progressively (a BigDataViewer viewer renders
	 * at low resolution first, then refines while data loads).
	 *
	 * @param stableMs  the picture must stay identical for that long
	 * @param timeoutMs gives up after that long
	 * @return the stable picture
	 * @throws IllegalStateException if the picture still changes after
	 *         {@code timeoutMs}: an animation, or rendering that never settles
	 */
	public static BufferedImage paintWhenStable(Component c, Rectangle region, double scale,
	                                            long stableMs, long timeoutMs) {
		long start = System.currentTimeMillis();
		BufferedImage previous = paint(c, region, scale);
		long stableSince = System.currentTimeMillis();
		while (true) {
			Ui.rawPause(STABLE_INTERVAL_MS);
			BufferedImage current = paint(c, region, scale);
			long now = System.currentTimeMillis();
			if (!sameImage(previous, current)) stableSince = now;
			else if (now - stableSince >= stableMs) return current;
			if (now - start > timeoutMs) {
				throw new IllegalStateException("The picture of " + c.getClass().getSimpleName()
						+ " still changes after " + timeoutMs + " ms");
			}
			previous = current;
		}
	}

	/** {@link #paintWhenStable(Component, Rectangle, double, long, long)} of the whole component. */
	public static BufferedImage paintWhenStable(Component c, double scale, long stableMs, long timeoutMs) {
		return paintWhenStable(c, new Rectangle(0, 0, c.getWidth(), c.getHeight()), scale, stableMs, timeoutMs);
	}

	/**
	 * The part of a window a picture shows: its menu bar and content pane, in
	 * the coordinates of its root pane. Leaves out the title bar, native or
	 * painted by the look and feel (FlatLaf), whose colours change with the
	 * window focus.
	 */
	public static Rectangle contentBounds(RootPaneContainer window) {
		JRootPane root = window.getRootPane();
		Rectangle r = boundsIn(window.getContentPane(), root);
		JMenuBar bar = root.getJMenuBar();
		if (bar != null && bar.isVisible()) r = r.union(boundsIn(bar, root));
		return r;
	}

	/** Paints the menu bar and the content of a window, see {@link #contentBounds}. */
	public static BufferedImage paintWindowContent(Window window, double scale) {
		RootPaneContainer w = rootPaneContainer(window);
		return paint(w.getRootPane(), contentBounds(w), scale);
	}

	/** {@link #paintWindowContent} once the picture stops changing, see {@link #paintWhenStable}. */
	public static BufferedImage paintWindowContentWhenStable(Window window, double scale,
	                                                         long stableMs, long timeoutMs) {
		RootPaneContainer w = rootPaneContainer(window);
		return paintWhenStable(w.getRootPane(), contentBounds(w), scale, stableMs, timeoutMs);
	}

	/** The bounds of {@code child} in the coordinates of its {@code ancestor}. */
	public static Rectangle boundsIn(Component child, Component ancestor) {
		if (!SwingUtilities.isDescendingFrom(child, ancestor)) {
			throw new IllegalArgumentException(child.getClass().getSimpleName()
					+ " is not inside " + ancestor.getClass().getSimpleName());
		}
		return SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), ancestor);
	}

	/**
	 * Paints a menu of a menu bar as if it were open, without opening it: a
	 * closed menu is not on screen, so its popup is laid out and painted
	 * offscreen.
	 *
	 * <p>{@code path} goes from a menu of the bar down to a menu or an item,
	 * each segment the exact text of a menu item. The picture is the popup of
	 * the deepest menu of the path; when the path ends on an item, that item is
	 * highlighted, as under the mouse.</p>
	 */
	public static BufferedImage paintMenu(JMenuBar bar, double scale, String... path) {
		if (path.length == 0) throw new IllegalArgumentException("Empty menu path");
		BufferedImage[] img = new BufferedImage[1];
		onEdt(() -> {
			JMenu menu = topMenu(bar, path[0]);
			JMenuItem armed = null;
			for (int i = 1; i < path.length; i++) {
				JMenuItem item = item(menu, path[i], Arrays.asList(path).subList(0, i));
				if (item instanceof JMenu) menu = (JMenu) item;
				else if (i == path.length - 1) armed = item;
				else throw new IllegalArgumentException("'" + path[i] + "' is an item, not a menu, in "
						+ String.join(" > ", path));
			}
			JPopupMenu popup = menu.getPopupMenu();
			boolean wasArmed = armed != null && armed.getModel().isArmed();
			Dimension size = popup.getPreferredSize();
			popup.setSize(size);
			layoutTree(popup);
			if (armed != null) armed.getModel().setArmed(true);
			try {
				img[0] = paint(popup, scale);
			}
			finally {
				if (armed != null) armed.getModel().setArmed(wasArmed);
			}
		});
		return img[0];
	}

	/** Writes a picture as PNG, creating the parent folders. */
	public static File write(BufferedImage img, File file) {
		File parent = file.getAbsoluteFile().getParentFile();
		if (!parent.isDirectory() && !parent.mkdirs()) {
			throw new RuntimeException("Could not create the folder " + parent);
		}
		try {
			ImageIO.write(img, "png", file);
		}
		catch (IOException e) {
			throw new RuntimeException("Could not write " + file, e);
		}
		return file;
	}

	/** True if both pictures have the same size and pixels. */
	public static boolean sameImage(BufferedImage a, BufferedImage b) {
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
		if (a.getRaster().getDataBuffer() instanceof DataBufferInt
				&& b.getRaster().getDataBuffer() instanceof DataBufferInt) {
			return Arrays.equals(((DataBufferInt) a.getRaster().getDataBuffer()).getData(),
					((DataBufferInt) b.getRaster().getDataBuffer()).getData());
		}
		for (int y = 0; y < a.getHeight(); y++)
			for (int x = 0; x < a.getWidth(); x++)
				if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
		return true;
	}

	/** Runs on the event dispatch thread, and throws what the task threw. */
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
			throw new RuntimeException("Interrupted while painting", e);
		}
		catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) throw (RuntimeException) cause;
			if (cause instanceof Error) throw (Error) cause;
			throw new RuntimeException(cause);
		}
	}

	private static RootPaneContainer rootPaneContainer(Window window) {
		if (!(window instanceof RootPaneContainer)) {
			throw new IllegalArgumentException("Not a Swing window: " + window.getClass().getName());
		}
		return (RootPaneContainer) window;
	}

	private static JMenu topMenu(JMenuBar bar, String text) {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < bar.getMenuCount(); i++) {
			JMenu m = bar.getMenu(i);
			if (m == null) continue;
			if (text.equals(m.getText())) return m;
			names.add(m.getText());
		}
		throw new IllegalArgumentException("No menu '" + text + "' in the menu bar. Menus: " + names);
	}

	private static JMenuItem item(JMenu menu, String text, List<String> parents) {
		List<String> names = new ArrayList<>();
		for (Component c : menu.getMenuComponents()) {
			if (!(c instanceof JMenuItem)) continue;
			JMenuItem item = (JMenuItem) c;
			if (text.equals(item.getText())) return item;
			names.add(item.getText());
		}
		throw new IllegalArgumentException("No item '" + text + "' in " + String.join(" > ", parents)
				+ ". Items: " + names);
	}

	/** Lays out a component tree that has no window, so no peer to validate it. */
	private static void layoutTree(Component c) {
		if (!(c instanceof Container)) return;
		Container container = (Container) c;
		container.doLayout();
		for (Component child : container.getComponents()) layoutTree(child);
	}
}
