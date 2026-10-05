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

import ch.unige.biochem.fiji.robot.core.Timeline;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.JFormattedTextField;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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
		this.keyTarget = target;
		this.camera = camera;
		this.position = new Point(start);
		send(MouseEvent.MOUSE_MOVED, 0, 0); // and the entered event
	}

	private Component keyTarget; // where the keys go: the target, or the component set

	/**
	 * Sends the key events to this component instead of the target: the target of a film of a whole window is its
	 * root pane, the keys belong to a component inside (a viewer).
	 */
	public VirtualPointer setKeyTarget(Component keyTarget) {
		this.keyTarget = keyTarget;
		return this;
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
			if (button != 0 && i < steps) {
				Point f = filmPosition();
				dragPath.add(new int[] { f.x, f.y });
			}
			if (button == 0) camera.shootMove(STEP_MS);
			else camera.shoot(STEP_MS);
		}
	}

	// The press under way, for the timeline: when, where on the frames, and where the pointer went since
	private long pressTime;
	private Point pressAt;
	private final List<int[]> dragPath = new ArrayList<>();

	/** Where the pointer is on the frames of the film, in their pixels. */
	public Point filmPosition() {
		return camera.filmPoint(target, position);
	}

	/** The modifier keys held, as the timeline names them. */
	private List<String> modifierNames() {
		List<String> names = new ArrayList<>();
		if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) names.add("shift");
		if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) names.add("ctrl");
		if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) names.add("alt");
		if ((modifiers & InputEvent.META_DOWN_MASK) != 0) names.add("meta");
		return names;
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
		pressTime = Timeline.now();
		pressAt = filmPosition();
		dragPath.clear();
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
		Point releaseAt = filmPosition();
		if (releaseAt.equals(pressAt)) {
			Timeline.recordClick(b == MouseEvent.BUTTON3 ? "mouse.right_click" : "mouse.click", pressTime,
					pressAt.x, pressAt.y, modifierNames());
		}
		else {
			Timeline.recordDrag(pressTime, pressAt.x, pressAt.y, releaseAt.x, releaseAt.y, modifierNames(),
					new ArrayList<>(dragPath));
		}
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
		glideTo(center[0], moveMs, () -> MenuSelectionManager.defaultManager().setSelectedPath(
				new MenuElement[] { (JPopupMenu) item[0].getParent(), item[0] }));
		clickMenuItem(item[0]);
	}

	/**
	 * Moves the pointer to a point of the target in {@code moveMs} without sending it events (over a menu, which
	 * the selection opens and highlights); {@code atEnd} runs on the event thread before the last frame.
	 */
	private void glideTo(Point to, int moveMs, Runnable atEnd) {
		int steps = Math.max(1, Math.round(moveMs / (float) STEP_MS));
		int x0 = position.x, y0 = position.y;
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			double e = t * t * (3 - 2 * t);
			position.setLocation((int) Math.round(x0 + (to.x - x0) * e), (int) Math.round(y0 + (to.y - y0) * e));
			if (i == steps && atEnd != null) onEdt(atEnd);
			if (i == steps && atEnd != null) camera.shoot(STEP_MS);
			else camera.shootMove(STEP_MS);
		}
	}

	/**
	 * Clicks a highlighted menu item: shown highlighted a moment, then the menus close and the item acts, as a
	 * real click. Queued, not waited for: an item that opens a modal dialog must not block the film.
	 */
	private void clickMenuItem(JMenuItem item) {
		camera.shoot(CLICK_MS * 3);
		Point f = filmPosition();
		Timeline.recordClick("mouse.click", Timeline.now(), f.x, f.y, modifierNames());
		onEdt(() -> MenuSelectionManager.defaultManager().clearSelectedPath());
		SwingUtilities.invokeLater(() -> item.doClick(0));
		onEdt(() -> { }); // the click happened before the next frame
	}

	/** How long the pointer takes from a menu to the next one of a path. */
	public static int MENU_MOVE_MS = 450;

	/**
	 * Chooses an item of a menu bar (of the target's window, or of a window the camera shows) the way a user does:
	 * the pointer goes to the menu, which opens, then down the submenus of the {@code path} (texts of the items,
	 * or their start), and clicks the last one. The menus open and highlight by the menu selection, not by
	 * synthetic events (which menus follow unreliably), and the camera paints them where they open.
	 */
	public void chooseInMenuBar(JMenuBar bar, String... path) {
		if (path.length < 2) throw new IllegalArgumentException("A menu and an item at least: " + String.join(" > ", path));
		List<MenuElement> selection = new ArrayList<>();
		selection.add(bar);
		JMenu menu = null;
		for (int i = 0; i < path.length; i++) {
			String text = path[i];
			JMenuItem[] found = { null };
			JMenu parent = menu;
			onEdt(() -> found[0] = parent == null ? menuOfBar(bar, text) : itemOf(parent, text));
			JMenuItem item = found[0];
			if (item == null) throw new IllegalArgumentException("No '" + text + "' in " + String.join(" > ", path));
			// the menus of the bar are pointed at their middle, the items of a popup near their left end
			Point[] to = { null };
			onEdt(() -> to[0] = locate(item, parent == null ? item.getWidth() / 2 : 30, item.getHeight() / 2));
			if (parent != null) {
				// along the item of the parent menu first, then down: the pointer stays on the open menus
				Point here = position();
				if (Math.abs(to[0].x - here.x) > 40) glideTo(new Point(to[0].x, here.y), MENU_MOVE_MS / 2, null);
			}
			selection.add(item);
			boolean last = i == path.length - 1;
			if (!last) {
				if (!(item instanceof JMenu)) throw new IllegalArgumentException("'" + text + "' is not a menu");
				selection.add(((JMenu) item).getPopupMenu());
			}
			MenuElement[] sel = selection.toArray(new MenuElement[0]);
			glideTo(to[0], MENU_MOVE_MS, () -> MenuSelectionManager.defaultManager().setSelectedPath(sel));
			if (!last) {
				camera.shoot(CLICK_MS * 2);
				menu = (JMenu) item;
			}
			else {
				clickMenuItem(item);
			}
		}
	}

	private static JMenu menuOfBar(JMenuBar bar, String text) {
		for (int i = 0; i < bar.getMenuCount(); i++) {
			JMenu m = bar.getMenu(i);
			if (m != null && m.getText() != null && m.getText().startsWith(text)) return m;
		}
		return null;
	}

	private static JMenuItem itemOf(JMenu menu, String text) {
		for (Component c : menu.getMenuComponents()) {
			if (c instanceof JMenuItem && ((JMenuItem) c).getText() != null
					&& ((JMenuItem) c).getText().startsWith(text)) return (JMenuItem) c;
		}
		return null;
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
		Point f = filmPosition();
		Timeline.recordKey(Timeline.now(), KeyEvent.getKeyText(keyCode), f.x, f.y, modifierNames());
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

	/** Moves to a point of a component in {@code moveMs}, and clicks it with the left button. */
	public void clickOn(Component c, int x, int y, int moveMs) {
		if (c instanceof JTextComponent) steadyCaret((JTextComponent) c);
		Point p = locate(c, x, y);
		moveTo(p.x, p.y, moveMs);
		click(MouseEvent.BUTTON1);
	}

	/**
	 * Chooses an item of a combo box as a user does: a click opens its list, the pointer goes to the item, which
	 * highlights, and a click chooses it. The list is painted where it opens next to the combo box, as the film
	 * shows the combo box (see {@link StopMotion#showPopup}).
	 *
	 * @param text the item, as the combo box shows it, or its start
	 */
	public void chooseInCombo(JComboBox<?> combo, String text, int moveMs) {
		int[] index = { -1 };
		List<String> items = new ArrayList<>();
		onEdt(() -> {
			for (int i = 0; i < combo.getItemCount(); i++) {
				String s = String.valueOf(combo.getItemAt(i));
				items.add(s);
				if (index[0] < 0 && s.startsWith(text)) index[0] = i;
			}
		});
		if (index[0] < 0) throw new IllegalArgumentException("No item '" + text + "' in the combo box: " + items);
		clickOn(combo, combo.getWidth() - 12, combo.getHeight() / 2, moveMs);
		JPopupMenu[] popup = { null };
		JList<?>[] list = { null };
		onEdt(() -> {
			if (!combo.isPopupVisible()) combo.showPopup();
			Object child = combo.getUI().getAccessibleChild(combo, 0);
			if (!(child instanceof ComboPopup) || !(child instanceof JPopupMenu)) {
				throw new IllegalStateException("No popup list for " + combo + ": " + child);
			}
			popup[0] = (JPopupMenu) child;
			list[0] = ((ComboPopup) child).getList();
			list[0].ensureIndexIsVisible(index[0]);
		});
		camera.showPopup(popup[0], combo);
		camera.shoot(CLICK_MS * 2);
		// where the item is, in the coordinates of the combo box (the list opens next to it on the screen)
		Point[] item = { null };
		onEdt(() -> {
			Rectangle cell = list[0].getCellBounds(index[0], index[0]);
			item[0] = SwingUtilities.convertPoint(list[0], cell.x + 30, cell.y + cell.height / 2, combo);
		});
		Point to = locate(combo, item[0].x, item[0].y);
		glideTo(to, moveMs, () -> list[0].setSelectedIndex(index[0]));
		camera.shoot(CLICK_MS * 2);
		Point f = filmPosition();
		Timeline.recordClick("mouse.click", Timeline.now(), f.x, f.y, modifierNames());
		onEdt(() -> {
			combo.setSelectedIndex(index[0]);
			combo.hidePopup();
		});
		camera.hidePopup(popup[0]);
		camera.shoot(CLICK_MS);
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

	/**
	 * Turns the mouse wheel where the pointer is, {@code notches} clicks (negative: away from the user, which zooms
	 * in a viewer), a frame of {@code msPerNotch} after each.
	 */
	public void wheel(int notches, int msPerNotch) {
		int step = notches < 0 ? -1 : 1;
		for (int i = 0; i < Math.abs(notches); i++) {
			int mods = modifiers;
			Point p = new Point(position);
			SwingUtilities.invokeLater(() -> {
				Object[] hit = hit(p);
				Component receiver = (Component) hit[0];
				Point at = (Point) hit[1];
				Point screen = new Point(at);
				if (receiver.isShowing()) SwingUtilities.convertPointToScreen(screen, receiver);
				receiver.dispatchEvent(new MouseWheelEvent(receiver, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
						mods, at.x, at.y, screen.x, screen.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, step));
			});
			camera.shoot(msPerNotch);
		}
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
			KeyEvent e = new KeyEvent(keyTarget, id, System.currentTimeMillis(), mods, keyCode, c);
			for (KeyEventDispatcher d : keyDispatchers) d.dispatchKeyEvent(e);
			for (KeyListener l : keyTarget.getKeyListeners()) {
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
