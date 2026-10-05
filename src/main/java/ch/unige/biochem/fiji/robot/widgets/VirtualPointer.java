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

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.JFormattedTextField;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * A mouse and a keyboard that exist only for one component: synthetic AWT events sent to it, never through the
 * operating system, so neither the real pointer nor the focus nor the visible screen matter. A {@link StopMotion}
 * films each step and draws the pointer.
 *
 * <p>Mouse events go through {@link Component#dispatchEvent}, as AWT delivers them. Key events go straight to the
 * key listeners of the component: dispatched, they would be redirected to the focus owner. That suits components
 * listening to the raw events (BigDataViewer's behaviours), not Swing key bindings.</p>
 */
public final class VirtualPointer {

	/** Duration of a frame while the pointer moves: 25 frames per second. */
	public static int STEP_MS = 40;

	/** How long a press or a release is shown. */
	public static int CLICK_MS = 160;

	private final Component target;
	private final StopMotion camera;
	private final Point position;
	private int button = 0; // the button held, 0 if none
	private int modifiers = 0; // the modifier keys held, as InputEvent.*_DOWN_MASK
	private String heldLabel; // how the frames show them
	private final List<KeyEventDispatcher> keyDispatchers = new ArrayList<>();

	VirtualPointer(Component target, StopMotion camera, Point start) {
		this.target = target;
		this.camera = camera;
		this.position = new Point(start);
		send(MouseEvent.MOUSE_MOVED, 0, 0); // and the entered event
	}

	/**
	 * Shows the key events to this dispatcher too, before the key listeners of the target, as the keyboard focus
	 * manager shows real ones to its dispatchers: for a component that tracks the keys held through a global
	 * dispatcher (BigDataViewer, see {@code BdvViewer.keyEventDispatcher}).
	 */
	public VirtualPointer addKeyEventDispatcher(KeyEventDispatcher dispatcher) {
		keyDispatchers.add(dispatcher);
		return this;
	}

	public Component target() {
		return target;
	}

	/** Where the pointer is, in the coordinates of the target. */
	public Point position() {
		return new Point(position);
	}

	public boolean pressed() {
		return button != 0;
	}

	/**
	 * Moves the pointer to x, y in {@code durationMs}, easing in and out, a frame every {@link #STEP_MS}: a move, or
	 * a drag while a button is held.
	 */
	public void moveTo(int x, int y, int durationMs) {
		int steps = Math.max(1, Math.round(durationMs / (float) STEP_MS));
		int x0 = position.x, y0 = position.y;
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			double e = t * t * (3 - 2 * t); // smoothstep
			position.setLocation((int) Math.round(x0 + (x - x0) * e), (int) Math.round(y0 + (y - y0) * e));
			send(button == 0 ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_DRAGGED, 0, 0);
			camera.shoot(STEP_MS);
		}
	}

	/** Moves to x, y without a frame (the pointer jumps): the target still gets the move. */
	public void jumpTo(int x, int y) {
		position.setLocation(x, y);
		send(button == 0 ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_DRAGGED, 0, 0);
	}

	/** Presses a button ({@link MouseEvent#BUTTON1}...), with the modifier keys held. */
	public void press(int mouseButton) {
		if (button != 0) throw new IllegalStateException("A button is already pressed");
		button = mouseButton;
		send(MouseEvent.MOUSE_PRESSED, 1, mouseButton);
		camera.shoot(CLICK_MS);
	}

	/** Releases the button pressed; a click if the pointer did not move since the press is up to the caller. */
	public void release() {
		release(false);
	}

	private void release(boolean click) {
		if (button == 0) throw new IllegalStateException("No button pressed");
		int b = button;
		button = 0;
		send(MouseEvent.MOUSE_RELEASED, 1, b);
		if (click) send(MouseEvent.MOUSE_CLICKED, 1, b);
		camera.shoot(CLICK_MS);
	}

	/** Presses and releases a button where the pointer is. */
	public void click(int mouseButton) {
		press(mouseButton);
		release(true);
	}

	/**
	 * Chooses an item of the popup menu shown on the target (a context menu opened by a right click): the pointer
	 * moves to it in {@code moveMs}, the item is highlighted, then clicked, closing the menu as a real click does.
	 *
	 * @param text the text of the item, or its start
	 */
	public void choose(String text, int moveMs) {
		JMenuItem[] item = { null };
		Point[] center = { null };
		List<String> names = new ArrayList<>();
		onEdt(() -> {
			JPopupMenu popup = StopMotion.shownPopup(target);
			if (popup == null) return;
			for (Component c : popup.getComponents()) {
				if (!(c instanceof JMenuItem)) continue;
				JMenuItem i = (JMenuItem) c;
				names.add(i.getText());
				if (item[0] == null && i.getText() != null && i.getText().startsWith(text)) item[0] = i;
			}
			if (item[0] != null) {
				center[0] = SwingUtilities.convertPoint(item[0], 20, item[0].getHeight() / 2, target);
			}
		});
		if (names.isEmpty()) throw new IllegalStateException("No popup menu shown on " + target);
		if (item[0] == null) throw new IllegalArgumentException("No item '" + text + "' in the popup menu: " + names);
		// the menu is not the target: the pointer moves without events
		int steps = Math.max(1, Math.round(moveMs / (float) STEP_MS));
		int x0 = position.x, y0 = position.y;
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			double e = t * t * (3 - 2 * t);
			position.setLocation((int) Math.round(x0 + (center[0].x - x0) * e), (int) Math.round(y0 + (center[0].y - y0) * e));
			if (i == steps) onEdt(() -> MenuSelectionManager.defaultManager().setSelectedPath(
					new MenuElement[] { (JPopupMenu) item[0].getParent(), item[0] }));
			camera.shoot(STEP_MS);
		}
		camera.shoot(CLICK_MS * 3);
		onEdt(() -> {
			MenuSelectionManager.defaultManager().clearSelectedPath();
			item[0].doClick(0);
		});
	}

	/** Drags with a button from where the pointer is to x, y. */
	public void drag(int mouseButton, int x, int y, int durationMs) {
		press(mouseButton);
		moveTo(x, y, durationMs);
		release();
	}

	/**
	 * Holds modifier keys ({@link InputEvent#SHIFT_DOWN_MASK}, {@link InputEvent#CTRL_DOWN_MASK}), shown on the
	 * frames as {@code label}: the following mouse events carry them.
	 */
	public void holdKeys(int modifierMask, String label) {
		for (int code : keyCodes(modifierMask & ~modifiers)) {
			modifiers |= maskOf(code);
			key(KeyEvent.KEY_PRESSED, code, KeyEvent.CHAR_UNDEFINED);
		}
		heldLabel = label;
		camera.setKeys(label);
	}

	/** Releases the modifier keys held. */
	public void releaseKeys() {
		for (int code : keyCodes(modifiers)) {
			modifiers &= ~maskOf(code);
			key(KeyEvent.KEY_RELEASED, code, KeyEvent.CHAR_UNDEFINED);
		}
		heldLabel = null;
		camera.setKeys(null);
	}

	/**
	 * Types a key with the modifier keys held, shown on the frames as {@code label} for {@code shownMs} (a frame is
	 * shot after the key).
	 */
	public void type(int keyCode, String label, int shownMs) {
		camera.setKeys(label);
		key(KeyEvent.KEY_PRESSED, keyCode, KeyEvent.CHAR_UNDEFINED);
		key(KeyEvent.KEY_RELEASED, keyCode, KeyEvent.CHAR_UNDEFINED);
		camera.shoot(shownMs);
		camera.setKeys(heldLabel);
	}

	/**
	 * Moves to the middle of a component (of the target, or of a window the camera shows, see
	 * {@link StopMotion#showWindow}) in {@code moveMs}, and clicks it with the left button.
	 */
	public void clickOn(Component c, int moveMs) {
		if (c instanceof JTextComponent) steadyCaret((JTextComponent) c);
		Point p = locate(c, c.getWidth() / 2, c.getHeight() / 2);
		moveTo(p.x, p.y, moveMs);
		click(MouseEvent.BUTTON1);
	}

	/** A caret that blinks makes the frames depend on time: it stays on. */
	private static void steadyCaret(JTextComponent field) {
		onEdt(() -> {
			if (field.getCaret() != null) field.getCaret().setBlinkRate(0);
		});
	}

	/** Where a point of a component is, in the coordinates of the target (through the windows the camera shows). */
	public Point locate(Component c, int x, int y) {
		Point[] p = { null };
		onEdt(() -> {
			StopMotion.Floating f = camera.floatingOf(c);
			if (f != null) {
				Point inRoot = SwingUtilities.convertPoint(c, x, y, f.root);
				Point inPainted = new Point(inRoot.x + f.at.x, inRoot.y + f.at.y);
				p[0] = SwingUtilities.convertPoint(camera.painted(), inPainted, target);
			}
			else {
				p[0] = SwingUtilities.convertPoint(c, x, y, target);
			}
		});
		return p[0];
	}

	/**
	 * Types text into a field, replacing its content, a character every {@code msPerChar} of the film; a formatted
	 * field (a number of a spinner) commits its value at the end, as when the field loses the focus.
	 */
	public void typeInto(JTextComponent field, String text, int msPerChar) {
		steadyCaret(field);
		onEdt(field::selectAll);
		camera.shoot(CLICK_MS * 2);
		for (int i = 1; i <= text.length(); i++) {
			String typed = text.substring(0, i);
			onEdt(() -> field.setText(typed));
			camera.shoot(msPerChar);
		}
		if (field instanceof JFormattedTextField) {
			onEdt(() -> {
				try {
					((JFormattedTextField) field).commitEdit();
				}
				catch (ParseException e) {
					throw new IllegalArgumentException("Not a valid value for " + field + ": " + text, e);
				}
			});
		}
	}

	/**
	 * Sends a mouse event as AWT does: to the deepest component under the pointer (in a window the camera shows, if
	 * one is there), or, while a button is held, to the component pressed. Queued on the event thread, not waited
	 * for: a click that opens a modal dialog must not block the film; the next frame, painted on the event thread,
	 * comes after it.
	 */
	private void send(int id, int clickCount, int eventButton) {
		int mods = modifiers | (button == 0 ? 0 : buttonMask(button));
		Point p = new Point(position);
		SwingUtilities.invokeLater(() -> {
			Component receiver;
			Point at;
			if (grabbed != null && id != MouseEvent.MOUSE_MOVED) {
				receiver = grabbed;
				at = toComponent(p, receiver);
			}
			else {
				Object[] hit = hit(p);
				receiver = (Component) hit[0];
				at = (Point) hit[1];
			}
			if (id == MouseEvent.MOUSE_MOVED || id == MouseEvent.MOUSE_PRESSED) hover(receiver, p, mods);
			if (id == MouseEvent.MOUSE_PRESSED) grabbed = receiver;
			if (id == MouseEvent.MOUSE_RELEASED) grabbed = null;
			dispatch(receiver, id, at, mods, clickCount, eventButton);
		});
	}

	private Component grabbed; // the component pressed, which gets the drags and the release (event thread only)
	private Component hovered; // the component under the pointer (event thread only)

	/** The deepest component under a point of the target, and the point in its coordinates. */
	private Object[] hit(Point p) {
		Component painted = camera.painted();
		Point inPainted = SwingUtilities.convertPoint(target, p, painted);
		StopMotion.Floating f = camera.floatingAt(inPainted);
		Container root;
		Point q;
		if (f != null) {
			root = f.root;
			q = new Point(inPainted.x - f.at.x, inPainted.y - f.at.y);
		}
		else if (target instanceof Container) {
			root = (Container) target;
			q = p;
		}
		else {
			return new Object[] { target, p };
		}
		Component c = SwingUtilities.getDeepestComponentAt(root, q.x, q.y);
		if (c == null) c = root;
		return new Object[] { c, SwingUtilities.convertPoint(root, q, c) };
	}

	/** Sends the exit and enter events a real pointer causes, moving from a component to another. */
	private void hover(Component receiver, Point p, int mods) {
		if (receiver == hovered) return;
		if (hovered != null) dispatch(hovered, MouseEvent.MOUSE_EXITED, toComponent(p, hovered), mods, 0, 0);
		hovered = receiver;
		dispatch(receiver, MouseEvent.MOUSE_ENTERED, toComponent(p, receiver), mods, 0, 0);
	}

	/** A point of the target in the coordinates of a component, which may be in a window the camera shows. */
	private Point toComponent(Point p, Component c) {
		StopMotion.Floating f = camera.floatingOf(c);
		if (f == null) return SwingUtilities.convertPoint(target, p, c);
		Point inPainted = SwingUtilities.convertPoint(target, p, camera.painted());
		return SwingUtilities.convertPoint(f.root, inPainted.x - f.at.x, inPainted.y - f.at.y, c);
	}

	private static void dispatch(Component receiver, int id, Point at, int mods, int clickCount, int eventButton) {
		Point screen = new Point(at);
		if (receiver.isShowing()) SwingUtilities.convertPointToScreen(screen, receiver);
		receiver.dispatchEvent(new MouseEvent(receiver, id, System.currentTimeMillis(), mods, at.x, at.y, screen.x,
				screen.y, clickCount, id == MouseEvent.MOUSE_RELEASED && eventButton == MouseEvent.BUTTON3, eventButton));
	}

	private void key(int id, int keyCode, char c) {
		int mods = modifiers;
		onEdt(() -> {
			KeyEvent e = new KeyEvent(target, id, System.currentTimeMillis(), mods, keyCode, c);
			for (KeyEventDispatcher d : keyDispatchers) d.dispatchKeyEvent(e);
			for (KeyListener l : target.getKeyListeners()) {
				if (id == KeyEvent.KEY_PRESSED) l.keyPressed(e);
				else l.keyReleased(e);
			}
		});
	}

	private static int buttonMask(int b) {
		switch (b) {
			case MouseEvent.BUTTON1: return InputEvent.BUTTON1_DOWN_MASK;
			case MouseEvent.BUTTON2: return InputEvent.BUTTON2_DOWN_MASK;
			case MouseEvent.BUTTON3: return InputEvent.BUTTON3_DOWN_MASK;
			default: throw new IllegalArgumentException("Button " + b);
		}
	}

	private static List<Integer> keyCodes(int mask) {
		List<Integer> codes = new ArrayList<>();
		if ((mask & InputEvent.SHIFT_DOWN_MASK) != 0) codes.add(KeyEvent.VK_SHIFT);
		if ((mask & InputEvent.CTRL_DOWN_MASK) != 0) codes.add(KeyEvent.VK_CONTROL);
		if ((mask & InputEvent.ALT_DOWN_MASK) != 0) codes.add(KeyEvent.VK_ALT);
		if ((mask & InputEvent.META_DOWN_MASK) != 0) codes.add(KeyEvent.VK_META);
		return codes;
	}

	private static int maskOf(int keyCode) {
		switch (keyCode) {
			case KeyEvent.VK_SHIFT: return InputEvent.SHIFT_DOWN_MASK;
			case KeyEvent.VK_CONTROL: return InputEvent.CTRL_DOWN_MASK;
			case KeyEvent.VK_ALT: return InputEvent.ALT_DOWN_MASK;
			case KeyEvent.VK_META: return InputEvent.META_DOWN_MASK;
			default: throw new IllegalArgumentException("Not a modifier: " + keyCode);
		}
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
			if (cause instanceof Error) throw (Error) cause;
			throw new RuntimeException(cause);
		}
	}
}
