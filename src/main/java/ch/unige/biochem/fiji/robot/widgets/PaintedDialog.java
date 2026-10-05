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

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A SciJava dialog (an input harvester) filled in a film, as a user fills it: the {@link VirtualPointer} clicks
 * its fields and types, opens its combo boxes and chooses, checks its boxes, clicks its buttons. The dialog is a
 * window shown in the film ({@link StopMotion#showWindow}); its inputs are found by the label the dialog shows,
 * as {@link Harvester} finds them. The painted twin of {@link Harvester}, which drives the real mouse.
 */
public final class PaintedDialog {

	/** Duration of a move of the pointer to a widget. */
	public static int MOVE_MS = 700;
	/** Duration of a typed character. */
	public static int CHAR_MS = 90;

	private final Window dialog;
	private final VirtualPointer pointer;

	public PaintedDialog(Window dialog, VirtualPointer pointer) {
		if (!(dialog instanceof Dialog)) throw new IllegalArgumentException("Not a dialog: " + dialog);
		this.dialog = dialog;
		this.pointer = pointer;
	}

	public Window window() {
		return dialog;
	}

	/**
	 * The container of the widgets of an input, by the label the dialog shows: ignoring case, the spaces around it
	 * and HTML tags. As SciJava's harvester lays a dialog out, the label is followed by the container of its
	 * widgets.
	 */
	public Container input(String label) {
		String wanted = normalized(label);
		List<JLabel> labels = Widgets.findAll((Container) dialog, JLabel.class);
		Container c = onEdt(() -> {
			for (JLabel l : labels) {
				if (!wanted.equals(normalized(l.getText()))) continue;
				Container parent = l.getParent();
				Component[] siblings = parent.getComponents();
				for (int i = Arrays.asList(siblings).indexOf(l) + 1; i < siblings.length; i++) {
					if (siblings[i] instanceof JLabel) break; // the next input
					if (siblings[i] instanceof Container) return (Container) siblings[i];
				}
			}
			return null;
		});
		if (c == null) {
			throw new IllegalArgumentException("No input '" + label + "' in the dialog. Labels: " + labels.stream()
					.map(l -> "'" + normalized(l.getText()) + "'").collect(Collectors.joining(", ")));
		}
		return c;
	}

	/** Whether the dialog has an input of this label (a dialog may hide inputs, or not have them in a version). */
	public boolean has(String label) {
		String wanted = normalized(label);
		return Widgets.findAll((Container) dialog, JLabel.class).stream().anyMatch(l -> wanted.equals(normalized(l.getText())));
	}

	private static String normalized(String text) {
		return text == null ? "" : text.replaceAll("<[^>]*>", "").replace(' ', ' ').trim().toLowerCase();
	}

	/**
	 * Sets an input as a user does, by the widget it has: a text (a text field, or the item of a combo box), a
	 * number (the field of a spinner: {@code value} as it is typed, as {@code "20"}), a check box ({@code true} or
	 * {@code false}). Nothing is done if the widget already shows the value.
	 */
	public PaintedDialog set(String label, Object value) {
		Container c = input(label);
		List<JComboBox> combos = Widgets.findAll(c, JComboBox.class);
		List<JCheckBox> checks = Widgets.findAll(c, JCheckBox.class);
		List<JFormattedTextField> numbers = Widgets.findAll(c, JFormattedTextField.class);
		List<JTextField> texts = Widgets.findAll(c, JTextField.class);
		if (value instanceof Boolean) {
			if (checks.isEmpty()) throw new IllegalArgumentException("'" + label + "' is not a check box");
			JCheckBox box = checks.get(0);
			if (onEdt(box::isSelected) != (Boolean) value) {
				pointer.clickOn(box, Harvester.CHECKBOX_CLICK_OFFSET_X, box.getHeight() / 2, MOVE_MS);
			}
		}
		else if (!combos.isEmpty()) {
			JComboBox<?> combo = combos.get(0);
			String shown = onEdt(() -> String.valueOf(combo.getSelectedItem()));
			if (!shown.startsWith(String.valueOf(value))) pointer.chooseInCombo(combo, String.valueOf(value), MOVE_MS);
		}
		else if (!numbers.isEmpty()) {
			type(numbers.get(0), String.valueOf(value));
		}
		else if (!texts.isEmpty()) {
			type(texts.get(0), String.valueOf(value));
		}
		else {
			throw new IllegalArgumentException("No widget to set for '" + label + "'");
		}
		return this;
	}

	private void type(JTextField field, String text) {
		if (text.equals(onEdt(field::getText))) return;
		pointer.clickOn(field, MOVE_MS);
		pointer.typeInto(field, text, CHAR_MS);
	}

	/** Clicks a button of the dialog by its text ({@code "OK"}, or a button input as {@code "Coronal"}). */
	public PaintedDialog click(String text) {
		List<AbstractButton> buttons = Widgets.findAll((Container) dialog, AbstractButton.class);
		AbstractButton b = buttons.stream().filter(x -> text.equals(x.getText())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("No button '" + text + "' in the dialog: "
						+ buttons.stream().map(AbstractButton::getText).collect(Collectors.toList())));
		pointer.clickOn(b, MOVE_MS);
		return this;
	}

	/** Clicks {@code OK}. */
	public void ok() {
		JButton ok = Widgets.findAll((Container) dialog, JButton.class).stream()
				.filter(b -> "OK".equals(b.getText())).findFirst()
				.orElseThrow(() -> new IllegalStateException("No OK button in the dialog"));
		pointer.clickOn(ok, MOVE_MS);
	}

	private static <T> T onEdt(java.util.function.Supplier<T> s) {
		if (SwingUtilities.isEventDispatchThread()) return s.get();
		Object[] r = { null };
		try {
			SwingUtilities.invokeAndWait(() -> r[0] = s.get());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
		catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException) throw (RuntimeException) e.getCause();
			throw new RuntimeException(e.getCause());
		}
		@SuppressWarnings("unchecked") T t = (T) r[0];
		return t;
	}
}
