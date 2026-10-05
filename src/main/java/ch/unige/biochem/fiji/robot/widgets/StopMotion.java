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

import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;
import javax.swing.RootPaneContainer;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
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

	private Film film;
	private long timeBefore; // duration of the films before this one (see cut)
	private final Component painted;
	private final Rectangle region;
	private final double scale;
	private Supplier<BufferedImage> scene;
	private Color desktop; // null: the scene is the region itself
	private String desktopTitle;
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

	/**
	 * Films a whole window on a desktop: {@code painted} is its root pane, the {@link #region} (in the coordinates
	 * of the root pane, beyond its bounds) the desktop, of {@code background} color (the chroma key of a video).
	 * The window is drawn under a title bar, as the windows {@link #showWindow shown}; the scene paints the whole
	 * root pane (default: as is). A {@code null} title: {@code painted} is a window painted with its decorations
	 * (an AWT window printed with its native title bar and menu bar), drawn as is.
	 */
	public StopMotion onDesktop(Color background, String title) {
		this.desktop = background;
		this.desktopTitle = title;
		this.scene = () -> Painter.paint(painted, scale);
		return this;
	}

	/**
	 * Goes on in a new film (the next clip of a video): the pointer, the windows shown, the keys and the caption
	 * stay as they are; {@link #time} goes on.
	 */
	public void cut(Film next) {
		timeBefore += film.duration();
		film = next;
	}

	/**
	 * The time of the film, in milliseconds: the duration of the frames shot so far, the films before a
	 * {@link #cut} included. The clock of a timeline that follows the film ({@code Timeline.clock}).
	 */
	public long time() {
		return timeBefore + film.duration();
	}

	/** Where a point of a component (of the painted one, or a descendant) is on the frames, in their pixels. */
	public Point filmPoint(Component c, Point p) {
		Point inPainted = c == painted ? new Point(p) : SwingUtilities.convertPoint(c, p, painted);
		return new Point((int) Math.round((inPainted.x - region.x) * scale),
				(int) Math.round((inPainted.y - region.y) * scale));
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

	private Supplier<BufferedImage> moveScene; // null: the scene

	/**
	 * How a frame is painted while the pointer only moves (no button held): nothing it does changes the painted
	 * component, so it can be painted faster than the {@link #setScene scene} (for instance without waiting as
	 * long for it to settle). Null: as the other frames.
	 */
	public StopMotion setMoveScene(Supplier<BufferedImage> moveScene) {
		this.moveScene = moveScene;
		return this;
	}

	/** Paints a frame and adds it to the film for {@code durationMs}. */
	public BufferedImage shoot(int durationMs) {
		return shoot(durationMs, scene);
	}

	/** A frame while the pointer only moves, see {@link #setMoveScene}. */
	BufferedImage shootMove(int durationMs) {
		return shoot(durationMs, moveScene != null ? moveScene : scene);
	}

	private BufferedImage shoot(int durationMs, Supplier<BufferedImage> scene) {
		BufferedImage img = scene.get();
		if (desktop != null) img = onDesktop(img);
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
		/** What is painted: the root pane of a Swing window, an AWT window itself (with its decorations). */
		public final Container root;
		/** True for an AWT window: painted with its own title bar, none is drawn. */
		public final boolean decorated;
		/** Top left corner of its root (pane), in the coordinates of the painted component. */
		public final Point at;

		Floating(Window window, Point at) {
			this.window = window;
			this.decorated = !(window instanceof RootPaneContainer);
			this.root = decorated ? window : ((RootPaneContainer) window).getRootPane();
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
	 * Shown until it closes or {@link #hideWindow}. An AWT window (an ImageJ image) is printed with its own title
	 * bar, its top left corner at {@code at}.
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

	/**
	 * Where a point of a component is in the painted component, as the film shows it: through the window shown in
	 * the film it is part of, if any (see {@link #showWindow}). Call on the event thread.
	 */
	Point paintedPoint(Component c, Point p) {
		Floating f = floatingOf(c);
		if (f == null) return SwingUtilities.convertPoint(c, p, painted);
		Point inRoot = SwingUtilities.convertPoint(c, p, f.root);
		return new Point(inRoot.x + f.at.x, inRoot.y + f.at.y);
	}

	// popups opened by a component that is not a menu (the list of a combo box), and the component
	private final List<Object[]> popups = new CopyOnWriteArrayList<>();

	/**
	 * Paints a popup in the film while it shows, placed as it opened next to {@code anchor} (the combo box that
	 * opened it), where the film shows the anchor: the popup of a component of a window shown in the film opens
	 * next to the window on the screen, not in the film. Menus need not be shown: see {@link #shownPopups}.
	 */
	public void showPopup(JPopupMenu popup, Component anchor) {
		hidePopup(popup);
		popups.add(new Object[] { popup, anchor });
	}

	public void hidePopup(JPopupMenu popup) {
		popups.removeIf(p -> p[0] == popup);
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
			if (f.decorated) {
				g.setColor(new Color(0, 0, 0, 60));
				g.fillRect(x + 4, y + 4, content.getWidth(), content.getHeight()); // shadow
				g.drawImage(content, x, y, null);
			}
			else drawWindow(g, content, x, y, title[0], scale);
		}
	}

	/** The picture of the painted root pane put on the desktop, under its title bar. */
	private BufferedImage onDesktop(BufferedImage window) {
		BufferedImage img = new BufferedImage((int) Math.round(region.width * scale),
				(int) Math.round(region.height * scale), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		try {
			g.setColor(desktop);
			g.fillRect(0, 0, img.getWidth(), img.getHeight());
			int x = (int) Math.round(-region.x * scale), y = (int) Math.round(-region.y * scale);
			if (desktopTitle != null) drawWindow(g, window, x, y, desktopTitle, scale);
			else {
				// painted with its own decorations (a window of the operating system, printed): a shadow only
				g.setColor(new Color(0, 0, 0, 60));
				g.fillRect(x + 4, y + 4, window.getWidth(), window.getHeight());
				g.drawImage(window, x, y, null);
			}
		}
		finally {
			g.dispose();
		}
		return img;
	}

	/** A window's content at x, y, under a drawn title bar, with a frame and a shadow. */
	private static void drawWindow(Graphics2D g, BufferedImage content, int x, int y, String title, double scale) {
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
		g.drawString(title == null ? "" : title, x + (int) (10 * scale), y - bar + (bar + fm.getAscent() - fm.getDescent()) / 2);
		g.setClip(clip);
		g.drawImage(content, x, y, null);
		g.setColor(new Color(150, 150, 150));
		g.drawRect(x - 1, y - bar - 1, w + 1, h + bar + 1);
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

	/**
	 * The popup menus shown, if the menu selection started on the painted component: a context menu opened on it,
	 * or the cascade of a menu of its menu bar, from the menu bar down.
	 */
	public static List<JPopupMenu> shownPopups(Component on) {
		List<JPopupMenu> popups = new java.util.ArrayList<>();
		MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
		if (path.length == 0) return popups;
		if (path[0] instanceof JPopupMenu) {
			if (shownPopup(on) == null) return popups;
		}
		else if (path[0] instanceof JMenuBar) {
			JMenuBar bar = (JMenuBar) path[0];
			if (bar != on && !SwingUtilities.isDescendingFrom(bar, on)) return popups;
		}
		else return popups;
		for (MenuElement e : path) {
			if (e instanceof JPopupMenu && ((JPopupMenu) e).isShowing()) popups.add((JPopupMenu) e);
		}
		return popups;
	}

	private void paintPopup(Graphics2D g) {
		List<JPopupMenu> popups = new java.util.ArrayList<>();
		List<Point> at = new java.util.ArrayList<>();
		try {
			SwingUtilities.invokeAndWait(() -> {
				for (JPopupMenu popup : shownPopups(painted)) {
					Point o = painted.getLocationOnScreen();
					Point p = popup.getLocationOnScreen();
					popups.add(popup);
					at.add(new Point(p.x - o.x, p.y - o.y));
				}
				for (Object[] shown : this.popups) {
					JPopupMenu popup = (JPopupMenu) shown[0];
					Component anchor = (Component) shown[1];
					if (!popup.isShowing() || !anchor.isShowing()) continue;
					popups.add(popup);
					at.add(paintedPoint(anchor, SwingUtilities.convertPoint(popup, 0, 0, anchor)));
				}
			});
		}
		catch (Exception e) {
			throw new RuntimeException("Could not find the popup menu", e);
		}
		for (int i = 0; i < popups.size(); i++) {
			BufferedImage menu = Painter.paint(popups.get(i), scale);
			g.drawImage(menu, (int) Math.round((at.get(i).x - region.x) * scale),
					(int) Math.round((at.get(i).y - region.y) * scale), null);
		}
	}

	private void decorate(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		try {
			paintWindows(g);
			paintPopup(g);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			// on a desktop, the labels go on the window filmed, not on the desktop
			Rectangle on = desktop == null ? new Rectangle(0, 0, img.getWidth(), img.getHeight())
					: new Rectangle((int) Math.round(-region.x * scale), (int) Math.round(-region.y * scale),
							(int) Math.round(painted.getWidth() * scale), (int) Math.round(painted.getHeight() * scale));
			if (caption != null) drawLabel(g, caption, on.x + on.width / 2, on.y + 10, true);
			if (keys != null) drawLabel(g, keys, on.x + 10, on.y + on.height - 10, false);
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
