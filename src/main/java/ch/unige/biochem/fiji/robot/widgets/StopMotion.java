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

import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * A camera filming a region of a component frame by frame into a {@link Film}: each {@link #shoot} paints the
 * region (never grabs the screen), draws the {@link VirtualPointer} and the keys held on it, and adds the picture
 * with the duration it should last.
 *
 * <p>Nothing depends on the real mouse, the keyboard or the visible screen: the actions are synthetic events
 * ({@link VirtualPointer}), the pointer is drawn. The program may take any time to reach the next frame (a viewer
 * rendering progressively, a registration running): the film plays at the pace of the durations.</p>
 */
public final class StopMotion {

	private final Film film;
	private final Component painted;
	private final Rectangle region;
	private final double scale;
	private Supplier<BufferedImage> scene;
	private VirtualPointer pointer;
	private String keys;
	private String caption;

	/**
	 * Films {@code region} of {@code painted} (in its coordinates), {@code scale} times bigger, painted as is: see
	 * {@link #setScene} to wait for a component that renders asynchronously.
	 */
	public StopMotion(Film film, Component painted, Rectangle region, double scale) {
		this.film = film;
		this.painted = painted;
		this.region = new Rectangle(region);
		this.scale = scale;
		this.scene = () -> Painter.paint(painted, this.region, scale);
	}

	/**
	 * How a frame is painted: a picture of the region, at the scale, once ready (for instance
	 * {@link Painter#paintWhenStable}).
	 */
	public StopMotion setScene(Supplier<BufferedImage> scene) {
		this.scene = scene;
		return this;
	}

	public Component painted() {
		return painted;
	}

	public Rectangle region() {
		return new Rectangle(region);
	}

	public double scale() {
		return scale;
	}

	public Film film() {
		return film;
	}

	/** A pointer acting on {@code target} (a descendant of the painted component, or itself), drawn by this camera. */
	public VirtualPointer pointer(Component target, Point start) {
		pointer = new VirtualPointer(target, this, start);
		return pointer;
	}

	/** Shows keys held or typed (as {@code "Shift"}, {@code "Ctrl + A"}) at the bottom left of the frames, or none. */
	public void setKeys(String keys) {
		this.keys = keys;
	}

	/** A caption at the top of the frames, or none. */
	public void setCaption(String caption) {
		this.caption = caption;
	}

	/** Paints a frame and adds it to the film for {@code durationMs}. */
	public BufferedImage shoot(int durationMs) {
		BufferedImage img = scene.get();
		decorate(img);
		film.add(img, durationMs);
		return img;
	}

	/** Shows the last frame {@code ms} longer, without painting: nothing changes meanwhile. */
	public void hold(int ms) {
		film.extend(ms);
	}

	/** A window shown in the film over the painted component, where the film puts it, not where the screen has it. */
	public static final class Floating {
		public final Window window;
		public final JRootPane root;
		/** Top left corner of its root pane, in the coordinates of the painted component. */
		public final Point at;

		Floating(Window window, Point at) {
			if (!(window instanceof RootPaneContainer)) throw new IllegalArgumentException("Not a Swing window: " + window);
			this.window = window;
			this.root = ((RootPaneContainer) window).getRootPane();
			this.at = new Point(at);
		}
	}

	private final List<Floating> floating = new CopyOnWriteArrayList<>();

	/** Height of the title bar drawn above a window shown in the film, in pixels of the painted component. */
	public static int TITLE_BAR_HEIGHT = 26;

	/**
	 * Shows a window (a dialog opened by an action) in the film, its root pane at {@code at} in the coordinates of
	 * the painted component, under a drawn title bar: a window of its own is not a part of the painted component,
	 * and where the screen puts it does not matter. The {@link VirtualPointer} acts on it where the film shows it.
	 * Shown until it closes or {@link #hideWindow}.
	 */
	public void showWindow(Window window, Point at) {
		hideWindow(window);
		floating.add(new Floating(window, at));
	}

	public void hideWindow(Window window) {
		floating.removeIf(f -> f.window == window);
	}

	/** The topmost window shown at this point of the painted component, or null. */
	Floating floatingAt(Point inPainted) {
		for (int i = floating.size() - 1; i >= 0; i--) {
			Floating f = floating.get(i);
			if (!f.window.isShowing()) continue;
			if (new Rectangle(f.at, f.root.getSize()).contains(inPainted)) return f;
		}
		return null;
	}

	/** The shown window a component is part of, or null. */
	Floating floatingOf(Component c) {
		for (Floating f : floating) {
			if (f.window.isShowing() && SwingUtilities.isDescendingFrom(c, f.root)) return f;
		}
		return null;
	}

	private void paintWindows(Graphics2D g) {
		for (Floating f : floating) {
			boolean[] showing = { false };
			String[] title = { "" };
			try {
				SwingUtilities.invokeAndWait(() -> {
					showing[0] = f.window.isShowing();
					if (f.window instanceof Dialog) title[0] = ((Dialog) f.window).getTitle();
					if (f.window instanceof Frame) title[0] = ((Frame) f.window).getTitle();
				});
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
			if (!showing[0]) continue;
			BufferedImage content = Painter.paint(f.root, scale);
			int x = (int) Math.round((f.at.x - region.x) * scale), y = (int) Math.round((f.at.y - region.y) * scale);
			int bar = (int) Math.round(TITLE_BAR_HEIGHT * scale);
			int w = content.getWidth(), h = content.getHeight();
			g.setColor(new Color(0, 0, 0, 60));
			g.fillRect(x + 4, y - bar + 4, w, h + bar); // shadow
			g.setColor(new Color(238, 238, 238));
			g.fillRect(x, y - bar, w, bar);
			g.setColor(new Color(40, 40, 40));
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.round(13 * scale)));
			FontMetrics fm = g.getFontMetrics();
			Shape clip = g.getClip();
			g.clipRect(x, y - bar, w - (int) (6 * scale), bar);
			g.drawString(title[0] == null ? "" : title[0], x + (int) (10 * scale), y - bar + (bar + fm.getAscent() - fm.getDescent()) / 2);
			g.setClip(clip);
			g.drawImage(content, x, y, null);
			g.setColor(new Color(150, 150, 150));
			g.drawRect(x - 1, y - bar - 1, w + 1, h + bar + 1);
		}
	}

	/**
	 * The popup menu shown, if it opened on the painted component (a context menu): a popup is a window of its own
	 * or a layer of the window, not a part of the component.
	 */
	public static JPopupMenu shownPopup(Component on) {
		MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
		if (path.length == 0 || !(path[0] instanceof JPopupMenu)) return null;
		JPopupMenu popup = (JPopupMenu) path[0];
		Component invoker = popup.getInvoker();
		if (!popup.isShowing() || invoker == null) return null;
		if (invoker != on && !SwingUtilities.isDescendingFrom(invoker, on) && !SwingUtilities.isDescendingFrom(on, invoker)) {
			return null;
		}
		return popup;
	}

	private void paintPopup(Graphics2D g) {
		JPopupMenu[] popup = { null };
		Point[] at = { null };
		try {
			SwingUtilities.invokeAndWait(() -> {
				popup[0] = shownPopup(painted);
				if (popup[0] == null) return;
				Point p = popup[0].getLocationOnScreen();
				Point o = painted.getLocationOnScreen();
				at[0] = new Point(p.x - o.x, p.y - o.y);
			});
		}
		catch (Exception e) {
			throw new RuntimeException("Could not find the popup menu", e);
		}
		if (popup[0] == null) return;
		BufferedImage menu = Painter.paint(popup[0], scale);
		g.drawImage(menu, (int) Math.round((at[0].x - region.x) * scale), (int) Math.round((at[0].y - region.y) * scale), null);
	}

	private void decorate(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		try {
			paintWindows(g);
			paintPopup(g);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			if (caption != null) drawLabel(g, caption, img.getWidth() / 2, 10, true);
			if (keys != null) drawLabel(g, keys, 10, img.getHeight() - 10, false);
			if (pointer != null) {
				Point p = SwingUtilities.convertPoint(pointer.target(), pointer.position(), painted);
				double x = (p.x - region.x) * scale, y = (p.y - region.y) * scale;
				drawPointer(g, x, y, pointer.pressed());
			}
		}
		finally {
			g.dispose();
		}
	}

	/** Size of the drawn pointer, in pixels of the film. */
	public static double POINTER_SIZE = 1.0;

	/** The arrow of a mouse pointer, its tip at 0,0, 20 pixels high. */
	private static final Polygon ARROW = new Polygon(
			new int[] { 0, 0, 4, 7, 10, 7, 12 },
			new int[] { 0, 17, 13, 20, 19, 12, 12 }, 7);

	private static void drawPointer(Graphics2D g, double x, double y, boolean pressed) {
		if (pressed) {
			double r = 14 * POINTER_SIZE;
			g.setColor(new Color(255, 220, 0, 110));
			g.fill(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
			g.setColor(new Color(255, 220, 0, 220));
			g.setStroke(new BasicStroke(2f));
			g.draw(new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
		}
		AffineTransform saved = g.getTransform();
		g.translate(x, y);
		g.scale(POINTER_SIZE, POINTER_SIZE);
		g.setColor(Color.WHITE);
		g.fillPolygon(ARROW);
		g.setColor(Color.BLACK);
		g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawPolygon(ARROW);
		g.setTransform(saved);
	}

	private static void drawLabel(Graphics2D g, String text, int x, int y, boolean topCentered) {
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		FontMetrics fm = g.getFontMetrics();
		int pad = 8;
		int w = fm.stringWidth(text) + 2 * pad, h = fm.getHeight() + pad;
		int bx = topCentered ? x - w / 2 : x;
		int by = topCentered ? y : y - h;
		g.setColor(new Color(30, 30, 30, 210));
		g.fillRoundRect(bx, by, w, h, 10, 10);
		g.setColor(new Color(255, 255, 255, 230));
		g.drawRoundRect(bx, by, w, h, 10, 10);
		g.drawString(text, bx + pad, by + pad / 2 + fm.getAscent());
	}
}
