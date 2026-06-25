package ch.epfl.biop.scijava.ui.robot.widgets;

import ch.epfl.biop.scijava.ui.robot.core.Timings;
import ch.epfl.biop.scijava.ui.robot.core.Ui;

import javax.swing.JList;
import javax.swing.ListModel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;

/**
 * Robot-driven primitives for a flat {@link JList}.
 *
 * <p>Pure Swing — no SciJava / BigDataViewer coupling. A binding whose harvester
 * widget renders a flat multi-select list (e.g. the bigdataviewer-playground
 * {@code SwingBdvHandleListWidget} / {@code SwingBvvHandleListWidget}) drives it
 * through here: rows are matched by {@code String.valueOf(model.getElementAt(i))}
 * on the live list model, so the binding only needs the display strings.</p>
 */
public final class Lists {

	private Lists() {}

	/**
	 * Select the rows named in {@code names} on a flat multi-select
	 * {@link JList}: plain-click the first match (replaces any prior selection),
	 * then Ctrl+click each subsequent match (additive selection under
	 * {@link javax.swing.ListSelectionModel#MULTIPLE_INTERVAL_SELECTION}).
	 *
	 * <p>Each name is matched against {@code String.valueOf(model.getElementAt(i))}
	 * on the live list model. Indices are resolved up front, so a typo throws —
	 * with the available items listed — before any visible action, avoiding a
	 * half-driven list on a recorded video.</p>
	 *
	 * <p>Click order does not determine output order: a SciJava list widget's
	 * {@code ListSelectionListener} pushes {@code getSelectedValuesList()} (which
	 * returns selected items in ascending index order) into the model after every
	 * click.</p>
	 */
	public static void selectByNames(JList<?> list, String[] names) {
		if (!list.isShowing()) {
			throw new IllegalStateException("JList is not on screen");
		}
		ListModel<?> model = list.getModel();
		int[] indices = new int[names.length];
		for (int n = 0; n < names.length; n++) {
			int found = -1;
			for (int i = 0; i < model.getSize(); i++) {
				if (names[n].equals(String.valueOf(model.getElementAt(i)))) {
					found = i;
					break;
				}
			}
			if (found < 0) {
				StringBuilder available = new StringBuilder();
				for (int i = 0; i < model.getSize(); i++) {
					if (i > 0) available.append(", ");
					available.append('"').append(model.getElementAt(i)).append('"');
				}
				throw new IllegalArgumentException("No item '" + names[n] + "' in JList. Available: ["
						+ available + "]");
			}
			indices[n] = found;
		}

		java.awt.Robot r = Ui.robot();
		Point listLoc = list.getLocationOnScreen();
		for (int k = 0; k < indices.length; k++) {
			Rectangle bounds = list.getCellBounds(indices[k], indices[k]);
			int x = listLoc.x + bounds.x + bounds.width / 2;
			int y = listLoc.y + bounds.y + bounds.height / 2;
			Ui.moveTo(x, y);
			Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
			if (k == 0) {
				Ui.click();
			} else {
				// Ctrl+click → toggle into the existing selection. A plain click
				// would replace it and we'd end up with only the last row.
				r.keyPress(KeyEvent.VK_CONTROL);
				r.delay(Timings.KEY_HOLD_MS);
				Ui.click();
				r.keyRelease(KeyEvent.VK_CONTROL);
			}
			Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
		}
	}
}