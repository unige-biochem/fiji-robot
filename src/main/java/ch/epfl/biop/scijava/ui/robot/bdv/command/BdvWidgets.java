package ch.epfl.biop.scijava.ui.robot.bdv.command;

import ch.epfl.biop.scijava.ui.robot.core.Timings;
import ch.epfl.biop.scijava.ui.robot.core.Ui;
import ch.epfl.biop.scijava.ui.robot.widgets.Harvester;
import ch.epfl.biop.scijava.ui.robot.widgets.Lists;
import ch.epfl.biop.scijava.ui.robot.widgets.Tree;
import ch.epfl.biop.scijava.ui.robot.widgets.WidgetDriver;
import ch.epfl.biop.scijava.ui.robot.widgets.Widgets;

import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.Container;
import java.awt.Point;

/**
 * The BigDataViewer-Playground harvester widget drivers — the part of the BDV
 * binding that teaches {@link Harvester} to fill the source-related parameter
 * widgets it can't handle on its own.
 *
 * <p>Three widget shapes, contributed as {@link WidgetDriver}s through the core
 * extension point (decision #8 — core never imports BDV; these drivers do, and
 * even they touch only Swing, since the SciJava widgets are plain {@code JTree} /
 * {@code JList} regardless of the {@code SourceAndConverter} / {@code BdvHandle}
 * payload):</p>
 *
 * <ul>
 *   <li><b>Source tree</b> — bdv-playground {@code SwingSourceWidget}
 *       ({@code SourceAndConverter}) and {@code SwingSourceListWidget}
 *       ({@code SourceAndConverter[]}). A {@code JTree} with no companion
 *       {@code JList}. The {@code String} value is a {@code ">"}-delimited tree
 *       path (including the {@code "Sources"} root). Aiming at a leaf selects one
 *       source; aiming at a parent selects every descendant source (the widget's
 *       own {@code getValue()} recursion) — that's the multi-source case.</li>
 *   <li><b>Sorted source list</b> — {@code SwingSourceSortedListWidget}, the
 *       {@code style="sorted"} variant of the {@code SourceAndConverter[]}
 *       widget. A {@code JTree} paired with a destination {@code JList}. Navigates
 *       the tree to the {@code String} path, then drags the matching row onto the
 *       list.</li>
 *   <li><b>Handle list</b> — {@code SwingBdvHandleListWidget} /
 *       {@code SwingBvvHandleListWidget}, which list every registered
 *       {@code BdvHandle} / {@code BvvHandle} for multi-selection. A flat
 *       {@code JList} with no {@code JTree}. The {@code String[]} value names the
 *       rows (window titles); driven by click + Ctrl+click.</li>
 * </ul>
 *
 * <p>Call {@link #register()} once before driving a dialog that contains any of
 * these widgets (the BDV launchers do this for you; a test driving
 * {@link Harvester#run} directly should call it in setup). Registration is
 * idempotent.</p>
 */
public final class BdvWidgets {

	private BdvWidgets() {}

	private static final WidgetDriver SOURCE_TREE = new SourceTreeDriver();
	private static final WidgetDriver SORTED_LIST = new SourceSortedListDriver();
	private static final WidgetDriver HANDLE_LIST = new HandleListDriver();

	/**
	 * Register the BDV source-widget drivers on {@link Harvester} so its dialog
	 * driving recognizes the bdv-playground source / sorted / handle-list
	 * widgets. Idempotent — the singletons are deduplicated by identity, so
	 * repeated calls (e.g. one per launch) add nothing.
	 */
	public static synchronized void register() {
		Harvester.registerDriver(SOURCE_TREE);
		Harvester.registerDriver(SORTED_LIST);
		Harvester.registerDriver(HANDLE_LIST);
	}

	/** Undo {@link #register()} — mainly for test isolation. */
	public static synchronized void unregister() {
		Harvester.unregisterDriver(SOURCE_TREE);
		Harvester.unregisterDriver(SORTED_LIST);
		Harvester.unregisterDriver(HANDLE_LIST);
	}

	// ===== Source JTree (single source / parent = all descendants) ==============

	/**
	 * {@code SwingSourceWidget} / {@code SwingSourceListWidget}: a {@code JTree}
	 * with no companion {@code JList}. The {@code String} value is the full
	 * {@code ">"}-path; {@link Tree#selectPath} navigates and single-clicks it.
	 */
	private static final class SourceTreeDriver implements WidgetDriver {
		@Override
		public boolean matches(Container inputContainer, Object value) {
			return value instanceof String
					&& Widgets.contains(inputContainer, JTree.class)
					&& !Widgets.contains(inputContainer, JList.class);
		}

		@Override
		public void fill(Container inputContainer, Object value) {
			JTree tree = Widgets.firstOrNull(inputContainer, JTree.class);
			Tree.selectPath(tree, String.valueOf(value));
		}
	}

	// ===== Sorted source list (JTree drag → JList) ==============================

	/**
	 * {@code SwingSourceSortedListWidget}: a source {@code JTree} paired with a
	 * destination {@code JList} in the same input container. Navigates the tree to
	 * the {@code String} path (expanding ancestors), then drags the matching row
	 * onto the centre of the list.
	 *
	 * <p>The tree has {@code setDragEnabled(true)} and a
	 * {@code SourceServiceTreeTransferHandler}, so a normal Swing DnD gesture
	 * (press → smooth move → release) triggers the transfer; the list's
	 * {@code JListTransferHandler} accepts the drop and a {@code ListDataListener}
	 * pushes the new value into the SciJava model. Aiming at a leaf adds one
	 * source; aiming at a parent adds every descendant. Single path only; the
	 * list isn't cleared first.</p>
	 */
	private static final class SourceSortedListDriver implements WidgetDriver {
		@Override
		public boolean matches(Container inputContainer, Object value) {
			return value instanceof String
					&& Widgets.contains(inputContainer, JTree.class)
					&& Widgets.contains(inputContainer, JList.class);
		}

		@Override
		public void fill(Container inputContainer, Object value) {
			JTree tree = Widgets.firstOrNull(inputContainer, JTree.class);
			JList<?> list = Widgets.firstOrNull(inputContainer, JList.class);
			TreePath target = Tree.navigateAndExpand(tree, String.valueOf(value));
			Point src = Tree.rowScreenCenter(tree, target);
			if (!list.isShowing()) {
				throw new IllegalStateException("Sorted-list drop target JList is not on screen");
			}
			Point listLoc = list.getLocationOnScreen();
			int dstX = listLoc.x + list.getWidth() / 2;
			int dstY = listLoc.y + list.getHeight() / 2;
			Ui.drag(src.x, src.y, dstX, dstY);
			Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
		}
	}

	// ===== Handle list (flat JList multi-select) ================================

	/**
	 * {@code SwingBdvHandleListWidget} / {@code SwingBvvHandleListWidget}: a flat
	 * {@code JList} with no {@code JTree}. The {@code String[]} value names the
	 * rows (BDV / BVV window titles); {@link Lists#selectByNames} click +
	 * Ctrl+clicks them.
	 */
	private static final class HandleListDriver implements WidgetDriver {
		@Override
		public boolean matches(Container inputContainer, Object value) {
			return value instanceof String[]
					&& Widgets.contains(inputContainer, JList.class)
					&& !Widgets.contains(inputContainer, JTree.class);
		}

		@Override
		public void fill(Container inputContainer, Object value) {
			JList<?> list = Widgets.firstOrNull(inputContainer, JList.class);
			Lists.selectByNames(list, (String[]) value);
		}
	}
}
