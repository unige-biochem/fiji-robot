package ch.unige.biochem.fiji.robot.core;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.Locale;

/**
 * Reusable helpers for Robot-driven UI automation built on top of {@link Robot}.
 *
 * Conventions:
 *   - All public coordinates are screen-absolute (already include the target
 *     screen's origin). Use {@link #framePos(int, int)} to express a point as
 *     "(dx, dy) from the default frame origin on the target screen".
 *   - Defaults are mutable {@code public static} fields so a script can tweak
 *     them at the top of {@code main} without touching the helper.
 */
public class Ui {

	// ===== Tweakable defaults =====================================================

	/** Index into {@link GraphicsEnvironment#getScreenDevices()}. 0 = primary. */
	public static int DEFAULT_SCREEN = 0;

	/** Top-left position of the GUI frame, RELATIVE to the target screen's origin. */
	public static int DEFAULT_FRAME_X = 20;
	public static int DEFAULT_FRAME_Y = 20;

	/**
	 * Horizontal offset from a window's left edge, in pixels, where a title-bar
	 * activation click lands (window selectors like {@code selectActiveImage} /
	 * {@code selectActiveBdv}). Aimed at the left of the title bar — the same value
	 * as the dialog drag grab — so the click stays clear of the right-side window
	 * buttons, the resize edges, and the menu bar below the title. Tunable.
	 */
	public static int TITLE_BAR_GRAB_X = 10;

	/**
	 * When {@code true}, the visible run paths force the JVM's number-formatting
	 * locale to use {@code '.'} as the decimal separator before a harvester dialog
	 * is built (see {@link #useDotDecimalSeparator()}). The Robot types numbers via
	 * {@code String.valueOf(...)}, which always emits {@code '.'}; without this, a
	 * JVM whose locale uses {@code ','} (fr / de / …) would reject the pasted value
	 * in the harvester's {@code JFormattedTextField}. Default on. Set to
	 * {@code false} to leave the locale untouched.
	 */
	public static boolean FORCE_DOT_DECIMAL = true;

	/**
	 * Fast-iteration switch. When {@code true}, methods that exist purely for
	 * visible motion — {@link #dragFrame}, {@link #resizeFrame},
	 * {@link #minimizeWithMouse}, {@link #pause(long)} — short-circuit to their
	 * programmatic Swing/AWT equivalents. Low-level Robot primitives
	 * ({@link #click}, {@link #drag}, {@link #moveTo}, {@link #mouseWheel},
	 * {@link #type}, {@link #paste}, {@link #key}) are deliberately <em>not</em>
	 * short-circuited — the widget drivers rely on them.
	 */
	public static boolean FAST_MODE = false;

	// Timings (durations, holds, polling) live in {@link Timings}.

	// ===== Robot singleton ========================================================

	private static Robot robot;

	public static Robot robot() {
		if (robot == null) {
			try {
				robot = new Robot();
				robot.setAutoDelay(0);
			} catch (AWTException e) {
				throw new RuntimeException("Cannot create Robot", e);
			}
		}
		return robot;
	}

	/**
	 * Forces number (and date) formatting to use {@code '.'} as the decimal
	 * separator, by setting the default {@link Locale.Category#FORMAT} locale to
	 * {@link Locale#US}. Leaves the {@code DISPLAY} category (UI language) alone.
	 *
	 * <p>Must run <em>before</em> the harvester dialog is built, because SciJava's
	 * numeric {@code JFormattedTextField} captures its formatter from the default
	 * locale at construction. The visible run paths call this for you when
	 * {@link #FORCE_DOT_DECIMAL} is set; call it directly (e.g. once at app
	 * startup) if you drive dialogs another way.</p>
	 */
	public static void useDotDecimalSeparator() {
		Locale.setDefault(Locale.Category.FORMAT, Locale.US);
	}

	// ===== Screen handling ========================================================

	/** All screens, in the order returned by the local graphics environment. */
	public static GraphicsDevice[] screens() {
		return GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
	}

	/** Target screen ({@link #DEFAULT_SCREEN}, clamped to what's available). */
	public static GraphicsDevice targetScreen() {
		GraphicsDevice[] all = screens();
		int idx = Math.max(0, Math.min(DEFAULT_SCREEN, all.length - 1));
		return all[idx];
	}

	/** Bounds of the target screen in absolute virtual-desktop coordinates. */
	public static Rectangle targetScreenBounds() {
		return targetScreen().getDefaultConfiguration().getBounds();
	}

	// ===== Frame placement ========================================================

	/** Default frame origin in absolute screen coordinates (target screen + offset). */
	public static Point defaultFrameOrigin() {
		Rectangle b = targetScreenBounds();
		return new Point(b.x + DEFAULT_FRAME_X, b.y + DEFAULT_FRAME_Y);
	}

	/** Resolves a frame-relative point ({@code dx, dy}) to absolute screen coords. */
	public static Point framePos(int dx, int dy) {
		Point o = defaultFrameOrigin();
		return new Point(o.x + dx, o.y + dy);
	}

	/**
	 * Returns the first visible {@link Frame} whose title contains
	 * {@code titleSubstring} (case-insensitive), or {@code null}.
	 */
	public static Frame findFrame(String titleSubstring) {
		String needle = titleSubstring.toLowerCase();
		for (Frame f : Frame.getFrames()) {
			String title = f.getTitle();
			if (f.isVisible() && title != null && title.toLowerCase().contains(needle)) {
				return f;
			}
		}
		return null;
	}

	/**
	 * Polls {@link #findFrame(String)} for up to {@code timeoutMs} ms.
	 * Returns the matching frame as soon as it appears, or {@code null} on timeout.
	 */
	public static Frame waitForFrame(String titleSubstring, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			Frame f = findFrame(titleSubstring);
			if (f != null) return f;
			rawPause(Timings.FRAME_POLL_INTERVAL_MS);
		}
		return null;
	}

	/**
	 * Returns the first visible {@link Dialog} (e.g. SciJava command dialogs,
	 * which are {@link javax.swing.JDialog} — not {@link Frame}) whose title
	 * contains {@code titleSubstring} (case-insensitive), or {@code null}.
	 */
	public static Dialog findDialog(String titleSubstring) {
		String needle = titleSubstring == null ? "" : titleSubstring.toLowerCase();
		for (Window w : Window.getWindows()) {
			if (!(w instanceof Dialog) || !w.isVisible()) continue;
			Dialog d = (Dialog) w;
			String title = d.getTitle();
			if (title != null && title.toLowerCase().contains(needle)) {
				return d;
			}
		}
		return null;
	}

	/** Polls {@link #findDialog(String)} for up to {@code timeoutMs} ms. */
	public static Dialog waitForDialog(String titleSubstring, long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			Dialog d = findDialog(titleSubstring);
			if (d != null) return d;
			rawPause(Timings.FRAME_POLL_INTERVAL_MS);
		}
		return null;
	}

	/**
	 * Polls for the first visible {@link Dialog} regardless of title. Useful
	 * when only one dialog is expected at a time (e.g. a SciJava command's
	 * input harvester) and the title is not predictable.
	 *
	 * @return the visible dialog, or {@code null} on timeout
	 */
	public static Dialog waitForActiveDialog(long timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (Window w : Window.getWindows()) {
				if (w instanceof Dialog && w.isVisible()) return (Dialog) w;
			}
			rawPause(Timings.FRAME_POLL_INTERVAL_MS);
		}
		return null;
	}

	/** Move a frame to {@link #defaultFrameOrigin()} and bring it to front. */
	public static void placeFrame(Frame frame) {
		placeFrame(frame, DEFAULT_FRAME_X, DEFAULT_FRAME_Y);
	}

	/**
	 * Visibly moves the cursor to the minimize button of the given frame's
	 * title bar (Windows-style: leftmost of the three top-right buttons) and
	 * clicks it. Estimated from {@link Frame#getInsets()}; will not work for
	 * borderless frames or non-native LAFs that draw their own title bar.
	 */
	public static void minimizeWithMouse(Frame frame) {
		if (FAST_MODE) {
			runOnEdt(() -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED));
			return;
		}
		Insets insets = frame.getInsets();
		int titleBarHeight = Math.max(insets.top, 24);
		int buttonWidth = (int) (1.5 * titleBarHeight);
		int x = frame.getX() + frame.getWidth() - (int) (1.5 * buttonWidth);
		int y = frame.getY() + titleBarHeight / 2;

		moveTo(x, y);
		pause(Timings.PAUSE_AFTER_MOVE_MS);
		click();
	}

	/**
	 * Visibly moves the cursor to the close (X) button of the given frame's
	 * title bar and clicks it. In {@link #FAST_MODE}, dispatches a
	 * {@link WindowEvent#WINDOW_CLOSING} so any installed close handler fires.
	 */
	public static void closeWithMouse(Frame frame) {
		if (FAST_MODE) {
			runOnEdt(() -> Toolkit.getDefaultToolkit().getSystemEventQueue()
					.postEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
			return;
		}
		Insets insets = frame.getInsets();
		int titleBarHeight = Math.max(insets.top, 24);
		int buttonWidth = (int) (1.5 * titleBarHeight);
		int x = frame.getX() + frame.getWidth() - (int) (0.5 * buttonWidth);
		int y = frame.getY() + titleBarHeight / 2;

		moveTo(x, y);
		pause(Timings.PAUSE_AFTER_MOVE_MS);
		click();
	}

	/**
	 * Visibly drags the given window (Frame or Dialog) by its title bar to a
	 * new position, specified relative to the target screen's top-left (same
	 * convention as {@link #placeFrame(Frame, int, int)}). Requires the native
	 * title bar (Windows-style); will not work for borderless windows.
	 */
	public static void dragFrame(Window window, int dxOnTargetScreen, int dyOnTargetScreen) {
		Rectangle screen = targetScreenBounds();
		int newAbsX = screen.x + dxOnTargetScreen;
		int newAbsY = screen.y + dyOnTargetScreen;

		if (FAST_MODE) {
			runOnEdt(() -> window.setLocation(newAbsX, newAbsY));
			return;
		}

		Insets insets = window.getInsets();
		int titleBarHeight = Math.max(insets.top, 24);
		int grabOffsetX = 10;
		int grabOffsetY = titleBarHeight / 2;

		int startX = window.getX() + grabOffsetX;
		int startY = window.getY() + grabOffsetY;
		int endX   = newAbsX + grabOffsetX;
		int endY   = newAbsY + grabOffsetY;

		drag(startX, startY, endX, endY);
	}

	/**
	 * Visibly resizes the given frame by dragging its bottom-right corner so
	 * the frame ends up at {@code width × height}. Requires native resize
	 * borders.
	 */
	public static void resizeFrame(Frame frame, int width, int height) {
		if (FAST_MODE) {
			runOnEdt(() -> frame.setSize(width, height));
			return;
		}
		// If the current bottom-right corner sits outside the screen's usable
		// area, Robot can't visibly grab it. Programmatically shrink to a size
		// whose corner IS reachable first, then drag from the new corner.
		ensureBottomRightGrabbable(frame);

		int startX = frame.getX() + frame.getWidth() - 1;
		int startY = frame.getY() + frame.getHeight() - 1;
		int endX   = frame.getX() + width - 1;
		int endY   = frame.getY() + height - 1;

		drag(startX, startY, endX, endY);
	}

	/**
	 * Programmatically shrink {@code frame} just enough that its bottom-right
	 * corner lands inside the usable area of its screen (screen bounds minus
	 * the OS taskbar and a small grab margin). No-op when already reachable.
	 */
	private static void ensureBottomRightGrabbable(Frame frame) {
		final int GRAB_EDGE_MARGIN_PX = 5;
		final int MIN_FRAME_DIM_PX = 120;

		GraphicsConfiguration gc = frame.getGraphicsConfiguration();
		if (gc == null) gc = targetScreen().getDefaultConfiguration();
		Rectangle screen = gc.getBounds();
		Insets osInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
		int usableRight = screen.x + screen.width - osInsets.right - GRAB_EDGE_MARGIN_PX;
		int usableBottom = screen.y + screen.height - osInsets.bottom - GRAB_EDGE_MARGIN_PX;

		int currentRight = frame.getX() + frame.getWidth() - 1;
		int currentBottom = frame.getY() + frame.getHeight() - 1;
		if (currentRight <= usableRight && currentBottom <= usableBottom) return;

		int safeW = Math.max(MIN_FRAME_DIM_PX,
				Math.min(frame.getWidth(), usableRight - frame.getX()));
		int safeH = Math.max(MIN_FRAME_DIM_PX,
				Math.min(frame.getHeight(), usableBottom - frame.getY()));
		final int w = safeW, h = safeH;
		runOnEdt(() -> frame.setSize(w, h));
		rawPause(150);
	}

	/**
	 * Press at {@code (startX, startY)}, smooth-drag to {@code (endX, endY)},
	 * release. Triggers Swing DnD when the source component has
	 * {@code setDragEnabled(true)} or an installed {@link javax.swing.TransferHandler}.
	 */
	public static void drag(int startX, int startY, int endX, int endY) {
		Timeline.mouseDrag(startX, startY, endX, endY);
		// Bracket the whole press/move/release with the EventRecorder suppress
		// flag so the synthetic events aren't re-captured as a second drag.
		EventRecorder.suppress(true);
		try {
			moveTo(startX, startY);
			pause(Timings.PAUSE_AFTER_MOVE_MS);
			robot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
			rawPause(Timings.DRAG_GRAB_DELAY_MS);
			moveTo(endX, endY);
			rawPause(Timings.DRAG_GRAB_DELAY_MS);
			robot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			waitForRobotIdle();
		} finally {
			EventRecorder.suppress(false);
		}
	}

	/**
	 * Visibly mouse-wheel-scrolls a {@link JScrollPane} ancestor of
	 * {@code target} so that {@code target} ends up fully inside the viewport,
	 * ideally with its top edge at (or as close as one wheel notch allows to)
	 * the top of the viewport.
	 *
	 * <p>Clamps the scroll target to {@code view.height - viewport.height} (the
	 * scrollbar's max), so a widget near the very bottom of a tall form lands
	 * inside the viewport (visible, not at the top edge) and the subsequent
	 * click still hits it. {@link Robot#waitForIdle()} after each notch keeps
	 * the next {@link JViewport#getViewRect()} read from racing the EDT.
	 * Mouse-wheel only — no programmatic {@link JViewport#setViewPosition}
	 * fallback. No-op when there is no {@link JScrollPane} ancestor or the
	 * target is already fully visible.</p>
	 */
	public static void scrollIntoView(Component target) {
		if (target == null || !target.isShowing()) return;
		JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
		if (SCROLL_DEBUG) {
			Point ts = target.isShowing() ? target.getLocationOnScreen() : null;
			System.out.println("[scrollIntoView] target=" + target.getClass().getSimpleName()
					+ " bounds=" + target.getBounds() + " screen=" + ts
					+ " hasScrollPane=" + (sp != null));
		}
		if (sp == null || !sp.isShowing()) return;
		JViewport viewport = sp.getViewport();
		Component view = viewport == null ? null : viewport.getView();
		if (view == null) return;

		Point inView = SwingUtilities.convertPoint(target, 0, 0, view);
		int widgetTop = inView.y;
		int widgetBottom = widgetTop + target.getHeight();
		Rectangle visible = viewport.getViewRect();
		// Clamp to the scrollbar's reachable range — see the "end-of-form
		// clamp" note in the Javadoc.
		int maxScroll = Math.max(0, view.getHeight() - viewport.getHeight());
		int targetY = Math.max(0, Math.min(widgetTop, maxScroll));
		if (SCROLL_DEBUG) {
			System.out.println("[scrollIntoView] widget y=[" + widgetTop + ".." + widgetBottom + "]"
					+ " view.h=" + view.getHeight() + " vp.h=" + viewport.getHeight()
					+ " maxScroll=" + maxScroll + " currentY=" + visible.y + " targetY=" + targetY);
		}
		boolean fullyVisible = widgetTop >= visible.y && widgetBottom <= visible.y + visible.height;
		if (fullyVisible) return;
		if (targetY == visible.y) return; // nothing to do

		// Wheel events go to the component under the cursor — make sure that's
		// the scroll pane (or one of its children). Skip the move if already in.
		Point spLoc = sp.getLocationOnScreen();
		Rectangle spOnScreen = new Rectangle(spLoc.x, spLoc.y, sp.getWidth(), sp.getHeight());
		Point cursor = MouseInfo.getPointerInfo().getLocation();
		if (!spOnScreen.contains(cursor)) {
			moveTo(spLoc.x + sp.getWidth() / 2, spLoc.y + sp.getHeight() / 2);
			pause(Timings.PAUSE_AFTER_MOVE_MS);
		}

		int direction = (visible.y < targetY) ? +1 : -1;
		int notchesPerStep = Math.max(1, Timings.WHEEL_NOTCHES_PER_STEP);
		int stagnation = 0;
		int ticks = 0;

		for (int i = 0; i < Timings.MAX_WHEEL_TICKS; i++) {
			Rectangle prev = viewport.getViewRect();
			if (direction > 0 && prev.y >= targetY) break;
			if (direction < 0 && prev.y <= targetY) break;

			mouseWheel(direction * notchesPerStep);
			ticks++;
			if (Timings.WHEEL_OS_DISPATCH_MS > 0) {
				robot().delay(Timings.WHEEL_OS_DISPATCH_MS);
			}
			robot().waitForIdle();
			pause(Timings.WHEEL_TICK_PAUSE_MS);

			Rectangle now = viewport.getViewRect();
			if (now.y == prev.y) {
				if (++stagnation >= Timings.WHEEL_STAGNATION_LIMIT) break;
				continue;
			}
			stagnation = 0;

			// Bulk overshoot: back off in single notches until just under the
			// target rather than dumping a whole bundle back. Cannot happen
			// when targetY == maxScroll (the scrollbar can't go past it).
			if (direction > 0 && now.y > targetY) {
				int safety = notchesPerStep + 2;
				while (safety-- > 0 && now.y > targetY) {
					mouseWheel(-1);
					if (Timings.WHEEL_OS_DISPATCH_MS > 0) {
						robot().delay(Timings.WHEEL_OS_DISPATCH_MS);
					}
					robot().waitForIdle();
					Rectangle prev2 = now;
					now = viewport.getViewRect();
					if (now.y == prev2.y) break;
				}
				pause(Timings.WHEEL_TICK_PAUSE_MS);
				break;
			}
		}

		if (SCROLL_DEBUG) {
			Rectangle f = viewport.getViewRect();
			Point ts = target.isShowing() ? target.getLocationOnScreen() : null;
			System.out.println("[scrollIntoView] done ticks=" + ticks
					+ " finalY=" + f.y + " widgetScreen=" + ts
					+ " spScreen=" + spOnScreen);
		}

		// Visible beat between the scroll and the next gesture.
		pause(Timings.PAUSE_AFTER_SCROLL_MS);
	}

	/** Toggle for diagnostic prints inside {@link #scrollIntoView(Component)}. */
	public static boolean SCROLL_DEBUG = false;

	/**
	 * Move a frame to {@code (dxOnTargetScreen, dyOnTargetScreen)} relative to
	 * the target screen's top-left corner, and bring it to front.
	 */
	public static void placeFrame(Frame frame, int dxOnTargetScreen, int dyOnTargetScreen) {
		Rectangle b = targetScreenBounds();
		int absX = b.x + dxOnTargetScreen;
		int absY = b.y + dyOnTargetScreen;
		runOnEdt(() -> {
			frame.setLocation(absX, absY);
			frame.toFront();
			frame.requestFocus();
		});
	}

	/**
	 * Run {@code r} on the EDT and block until it completes. If we're already on
	 * the EDT, run inline to avoid deadlock.
	 */
	public static void runOnEdt(Runnable r) {
		if (SwingUtilities.isEventDispatchThread()) {
			r.run();
			return;
		}
		try {
			SwingUtilities.invokeAndWait(r);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// ===== Mouse motion ===========================================================

	/**
	 * Smoothly moves the cursor to {@code (x, y)} using a sine ease-in / ease-out
	 * profile. The duration is scaled by {@link Timings#GLOBAL_SPEED}.
	 *
	 * @param x absolute screen X
	 * @param y absolute screen Y
	 * @param durationMs total motion duration (before GLOBAL_SPEED scaling)
	 * @param frames number of intermediate steps (NOT scaled)
	 */
	public static void moveTo(int x, int y, long durationMs, int frames) {
		long scaledMs = Timings.scaled(durationMs);
		Point start = MouseInfo.getPointerInfo().getLocation();
		int perFrame = (int) Math.max(1, scaledMs / Math.max(1, frames));
		for (int i = 1; i <= frames; i++) {
			double t = (double) i / frames;
			double eased = 0.5 - 0.5 * Math.cos(Math.PI * t); // sin ease-in-out
			int xi = start.x + (int) Math.round((x - start.x) * eased);
			int yi = start.y + (int) Math.round((y - start.y) * eased);
			robot().mouseMove(xi, yi);
			robot().delay(perFrame);
		}
	}

	/**
	 * Smooth move whose duration is derived from the cursor → target distance
	 * (clamped to [{@link Timings#MOUSE_MOVE_MIN_MS}, {@link Timings#MOUSE_MOVE_MAX_MS}]),
	 * then scaled by {@link Timings#GLOBAL_SPEED}.
	 */
	public static void moveTo(int x, int y) {
		Point start = MouseInfo.getPointerInfo().getLocation();
		long dx = x - start.x;
		long dy = y - start.y;
		double distance = Math.sqrt((double) dx * dx + (double) dy * dy);
		double speed = Math.max(1e-3, Timings.MOUSE_SPEED_PX_PER_MS);
		long duration = (long) (distance / speed);
		duration = Math.max(Timings.MOUSE_MOVE_MIN_MS,
							Math.min(Timings.MOUSE_MOVE_MAX_MS, duration));
		moveTo(x, y, duration, Timings.MOUSE_MOVE_FRAMES);
	}

	/** Convenience: smooth move to a {@link Point}. */
	public static void moveTo(Point p) {
		moveTo(p.x, p.y);
	}

	/** Teleport the cursor (no animation). */
	public static void jumpTo(int x, int y) {
		robot().mouseMove(x, y);
	}

	// ===== Clicks / keys ==========================================================

	public static void click() {
		Timeline.mouseClick();
		EventRecorder.suppress(true);
		try {
			robot().mousePress(InputEvent.BUTTON1_DOWN_MASK);
			robot().delay(Timings.CLICK_HOLD_MS);
			robot().mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			waitForRobotIdle();
		} finally {
			EventRecorder.suppress(false);
		}
	}

	/**
	 * Two quick mouse clicks at the current cursor position. Uses
	 * {@link Timings#DOUBLE_CLICK_HOLD_MS} so the press-to-press interval fits
	 * inside the OS double-click threshold.
	 */
	public static void doubleClick() {
		Timeline.mouseDoubleClick();
		EventRecorder.suppress(true);
		try {
			Robot r = robot();
			r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
			r.delay(Timings.DOUBLE_CLICK_HOLD_MS);
			r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			r.delay(Timings.DOUBLE_CLICK_GAP_MS);
			r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
			r.delay(Timings.DOUBLE_CLICK_HOLD_MS);
			r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
			waitForRobotIdle();
		} finally {
			EventRecorder.suppress(false);
		}
	}

	public static void rightClick() {
		Timeline.mouseRightClick();
		EventRecorder.suppress(true);
		try {
			robot().mousePress(InputEvent.BUTTON3_DOWN_MASK);
			robot().delay(Timings.CLICK_HOLD_MS);
			robot().mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
			waitForRobotIdle();
		} finally {
			EventRecorder.suppress(false);
		}
	}

	/**
	 * Spin the mouse wheel by {@code notches} at the current cursor position.
	 * Positive values scroll DOWN; negative values scroll UP. The event is
	 * dispatched to whichever Swing component is under the cursor.
	 */
	public static void mouseWheel(int notches) {
		Timeline.mouseWheel(notches);
		EventRecorder.suppress(true);
		try {
			robot().mouseWheel(notches);
			waitForRobotIdle();
		} finally {
			EventRecorder.suppress(false);
		}
	}

	/**
	 * Drain pending AWT events so synthetic Robot events are all dispatched
	 * before the caller drops the {@link EventRecorder} suppress flag. No-op on
	 * the EDT, where {@link Robot#waitForIdle()} would throw.
	 */
	private static void waitForRobotIdle() {
		if (SwingUtilities.isEventDispatchThread()) return;
		robot().waitForIdle();
	}

	public static void key(int keyCode) {
		EventRecorder.suppress(true);
		try {
			robot().keyPress(keyCode);
			robot().delay(Timings.KEY_HOLD_MS);
			robot().keyRelease(keyCode);
			waitForRobotIdle();
		} finally {
			EventRecorder.suppress(false);
		}
	}

	public static void escape() {
		key(KeyEvent.VK_ESCAPE);
	}

	public static void enter() {
		key(KeyEvent.VK_ENTER);
	}

	/**
	 * Sets the system clipboard to {@code s} and sends {@code Ctrl+V} — a
	 * layout-independent way to inject any string into the focused text widget.
	 */
	public static void paste(String s) {
		Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(s), null);
		rawPause(50); // give the clipboard a beat to settle before Ctrl+V
		Robot r = robot();
		EventRecorder.suppress(true);
		try {
			r.keyPress(KeyEvent.VK_CONTROL);
			r.delay(Timings.KEY_HOLD_MS);
			r.keyPress(KeyEvent.VK_V);
			r.delay(Timings.KEY_HOLD_MS);
			r.keyRelease(KeyEvent.VK_V);
			r.keyRelease(KeyEvent.VK_CONTROL);
			waitForRobotIdle();
		} finally {
			EventRecorder.suppress(false);
		}
		rawPause(100); // let the paste complete before the next action
	}

	/**
	 * Types an ASCII string. Handles letters (with shift for uppercase),
	 * digits, space, and a small set of common symbols. Throws if a character
	 * has no mapping. Per-character {@link Robot#keyPress} is keyboard-layout
	 * dependent — for paths / non-US text use {@link #paste(String)}.
	 */
	public static void type(String s) {
		Robot r = robot();
		long interChar = Timings.scaled(Timings.TYPE_INTER_CHAR_MS);
		EventRecorder.suppress(true);
		try {
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				int code = keyCodeFor(c);
				boolean shift = needsShift(c);
				if (shift) r.keyPress(KeyEvent.VK_SHIFT);
				r.keyPress(code);
				r.delay(Timings.TYPE_KEY_HOLD_MS);
				r.keyRelease(code);
				if (shift) r.keyRelease(KeyEvent.VK_SHIFT);
				r.delay((int) interChar);
			}
			waitForRobotIdle();
		} finally {
			EventRecorder.suppress(false);
		}
	}

	private static boolean needsShift(char c) {
		if (Character.isUpperCase(c)) return true;
		switch (c) {
			case '!': case '@': case '#': case '$': case '%':
			case '^': case '&': case '*': case '(': case ')':
			case '_': case '+': case '{': case '}': case ':':
			case '"': case '<': case '>': case '?': case '~':
			case '|':
				return true;
			default:
				return false;
		}
	}

	private static int keyCodeFor(char c) {
		if (c >= 'a' && c <= 'z') return KeyEvent.VK_A + (c - 'a');
		if (c >= 'A' && c <= 'Z') return KeyEvent.VK_A + (c - 'A');
		if (c >= '0' && c <= '9') return KeyEvent.VK_0 + (c - '0');
		switch (c) {
			case ' ':  return KeyEvent.VK_SPACE;
			case '.':  return KeyEvent.VK_PERIOD;
			case ',':  return KeyEvent.VK_COMMA;
			case '-':  return KeyEvent.VK_MINUS;
			case '_':  return KeyEvent.VK_MINUS;     // shift handled separately
			case '=':  return KeyEvent.VK_EQUALS;
			case '+':  return KeyEvent.VK_EQUALS;    // shift
			case '/':  return KeyEvent.VK_SLASH;
			case '?':  return KeyEvent.VK_SLASH;     // shift
			case ';':  return KeyEvent.VK_SEMICOLON;
			case ':':  return KeyEvent.VK_SEMICOLON; // shift
			case '\'': return KeyEvent.VK_QUOTE;
			case '"':  return KeyEvent.VK_QUOTE;     // shift
			case '\t': return KeyEvent.VK_TAB;
			case '\n': return KeyEvent.VK_ENTER;
			case '\\': return KeyEvent.VK_BACK_SLASH;
			case '|':  return KeyEvent.VK_BACK_SLASH; // shift
			case '`':  return KeyEvent.VK_BACK_QUOTE;
			case '~':  return KeyEvent.VK_BACK_QUOTE; // shift
			case '[':  return KeyEvent.VK_OPEN_BRACKET;
			case '{':  return KeyEvent.VK_OPEN_BRACKET; // shift
			case ']':  return KeyEvent.VK_CLOSE_BRACKET;
			case '}':  return KeyEvent.VK_CLOSE_BRACKET; // shift
			case '<':  return KeyEvent.VK_COMMA;     // shift
			case '>':  return KeyEvent.VK_PERIOD;    // shift
			// Shifted-digit symbols: emit the digit key with shift.
			case '!':  return KeyEvent.VK_1;         // shift
			case '@':  return KeyEvent.VK_2;         // shift
			case '#':  return KeyEvent.VK_3;         // shift
			case '$':  return KeyEvent.VK_4;         // shift
			case '%':  return KeyEvent.VK_5;         // shift
			case '^':  return KeyEvent.VK_6;         // shift
			case '&':  return KeyEvent.VK_7;         // shift
			case '*':  return KeyEvent.VK_8;         // shift
			case '(':  return KeyEvent.VK_9;         // shift
			case ')':  return KeyEvent.VK_0;         // shift
		}
		throw new IllegalArgumentException("No key mapping for character: '" + c + "' (0x"
				+ Integer.toHexString(c) + ")");
	}

	// ===== Pause ==================================================================

	/**
	 * Blocks the calling thread for {@code ms} milliseconds, scaled by
	 * {@link Timings#GLOBAL_SPEED}. Use {@link #rawPause(long)} for waits that
	 * must NOT be affected by speed (e.g. polling intervals).
	 */
	public static void pause(long ms) {
		if (FAST_MODE) return;
		rawPause(Timings.scaled(ms));
	}

	/** Blocks the calling thread for {@code ms} milliseconds — never scaled. */
	public static void rawPause(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
