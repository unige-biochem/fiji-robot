package ch.epfl.biop.scijava.ui.robot.bdv;

import ch.epfl.biop.scijava.ui.robot.LaunchRequest;
import ch.epfl.biop.scijava.ui.robot.Launcher;
import ch.epfl.biop.scijava.ui.robot.core.Timings;
import ch.epfl.biop.scijava.ui.robot.core.Ui;
import ch.epfl.biop.scijava.ui.robot.widgets.Harvester;
import ch.epfl.biop.scijava.ui.robot.widgets.Popup;
import ch.epfl.biop.scijava.ui.robot.widgets.Tree;

import org.scijava.Context;
import org.scijava.MenuPath;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.module.Module;
import sc.fiji.bdvpg.scijava.service.SourceService;

import javax.swing.JTree;
import java.util.Collections;
import java.util.Map;

/**
 * Factory methods for the BigDataViewer-bound {@link Launcher} kinds — the BDV
 * mirror of {@code Ij1Launchers}.
 *
 * <p>{@link #treeLauncher} launches a command the way a user does from the
 * BDV-Playground "BDV Sources" frame: right-click a source-tree node, walk the
 * context menu to the command, then let the harvester drive the dialog. Like the
 * IJ1 search launcher it is visible-only — choosing it <em>is</em> choosing the
 * mode. The "BDV Sources" frame must already be open (its {@code JTree} is what
 * gets driven).</p>
 *
 * <p>Right-clicking a specific node is, by bdv-playground convention, how the
 * command's {@code "sources"} parameter gets filled — so the tree launcher
 * {@linkplain Launcher#contributedInputs contributes} that {@code "sources"}
 * value (the tree path, resolved headlessly by bdv-playground's
 * {@code StringToSourceArray} / {@code StringArrayToSourceArray} converters).
 * That keeps the Groovy reproduction faithful even though the visible dialog has
 * no {@code "sources"} widget.</p>
 */
public final class BdvLaunchers {

	private BdvLaunchers() {}

	/** The root node of the BDV-Playground source tree; paths are relative to it. */
	private static final String SOURCE_TREE_ROOT = "Sources";

	/** The parameter a source-tree right-click conventionally fills. */
	private static final String SOURCES_PARAM = "sources";

	/**
	 * Launch via a right-click on the source-tree <em>root</em> ("Sources"),
	 * walking the context menu to the command. For commands that don't consume a
	 * {@code "sources"} parameter (e.g. view-sync, title-set) — no sources input
	 * is contributed.
	 */
	public static Launcher treeLauncher() {
		return new TreeLauncher(new String[0]);
	}

	/**
	 * Launch by right-clicking the node at {@code sourcePath} in the "BDV Sources"
	 * tree, walking the context menu to the command, then driving the dialog.
	 *
	 * <p>{@code sourcePath} is {@code ">"}-delimited and <em>relative to the
	 * "Sources" root</em> (e.g. {@code "Other Sources"} or
	 * {@code "my-dataset>channel 0"}); segments must match the tree node labels
	 * exactly. The same path is contributed as the command's {@code "sources"}
	 * input for the headless reproduction.</p>
	 */
	public static Launcher treeLauncher(String sourcePath) {
		if (sourcePath == null || sourcePath.isEmpty()) {
			throw new IllegalArgumentException("source path must not be null or empty");
		}
		return new TreeLauncher(new String[] { sourcePath });
	}

	/**
	 * Multi-select variant: Ctrl+click each of {@code sourcePaths} in the tree,
	 * then right-click in place so the command operates on the whole selection.
	 * The full array is contributed as the {@code "sources"} input (a
	 * {@code String[]}, resolved by bdv-playground's {@code StringArrayToSourceArray}).
	 *
	 * <p>Each path is relative to the "Sources" root, same as
	 * {@link #treeLauncher(String)}.</p>
	 */
	public static Launcher treeLauncher(String... sourcePaths) {
		if (sourcePaths == null || sourcePaths.length == 0) {
			throw new IllegalArgumentException("treeLauncher requires at least one source path");
		}
		for (String p : sourcePaths) {
			if (p == null || p.isEmpty()) {
				throw new IllegalArgumentException("source paths must not be null or empty");
			}
		}
		return new TreeLauncher(sourcePaths.clone());
	}

	private static final class TreeLauncher implements Launcher {

		/** Paths relative to the "Sources" root; empty array means the root itself. */
		private final String[] sourcePaths;

		TreeLauncher(String[] sourcePaths) {
			this.sourcePaths = sourcePaths;
		}

		@Override
		public Map<String, Object> contributedInputs(LaunchRequest request) {
			if (sourcePaths.length == 0) return Collections.emptyMap();
			Object value = (sourcePaths.length == 1) ? sourcePaths[0] : sourcePaths.clone();
			return Collections.singletonMap(SOURCES_PARAM, value);
		}

		@Override
		public Module launch(LaunchRequest request) {
			if (Ui.FORCE_DOT_DECIMAL) Ui.useDotDecimalSeparator();
			// Teach the harvester the BDV source / sorted / handle-list widgets so
			// any such parameter in this command's dialog is driveable. Idempotent.
			BdvWidgets.register();
			request.runPreSetGestures();

			JTree jtree = sourceTree(request.context());
			// Bring the source-tree window above any incidental window that may be
			// covering it (e.g. a Fiji/ImageJ log window that popped up on a
			// warning), so the right-click lands on the tree and not on top of it.
			raiseWindow(jtree);
			if (sourcePaths.length <= 1) {
				Tree.rightClickPath(jtree, fullPath(sourcePaths.length == 1 ? sourcePaths[0] : null));
			} else {
				String[] full = new String[sourcePaths.length];
				for (int i = 0; i < sourcePaths.length; i++) full[i] = fullPath(sourcePaths[i]);
				Tree.rightClickPaths(jtree, full);
			}

			Popup.clickPath(popupPath(request.context(), request.command()));
			// Let the command spawn whatever UI it opens (e.g. a BDV viewer)
			// before we drive its dialog.
			Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);

			Harvester.runOpenDialog(request.command(), request.dialogNarrations(),
					request.dialogArgs());
			return null;
		}

		/** Prepend the "Sources" root unless {@code path} is null (root) or already rooted. */
		private static String fullPath(String path) {
			if (path == null) return SOURCE_TREE_ROOT;
			if (path.equals(SOURCE_TREE_ROOT) || path.startsWith(SOURCE_TREE_ROOT + ">")) return path;
			return SOURCE_TREE_ROOT + ">" + path;
		}

		/** Bring the window hosting {@code c} to the front (EDT), then settle. */
		private static void raiseWindow(java.awt.Component c) {
			Ui.runOnEdt(() -> {
				java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(c);
				if (w != null) {
					w.toFront();
					w.requestFocus();
				}
			});
			Ui.rawPause(200);
		}

		private static JTree sourceTree(Context context) {
			SourceService ss = context.service(SourceService.class);
			if (ss == null) {
				throw new IllegalStateException("No SourceService — is bigdataviewer-playground running?");
			}
			return ss.tree().getJTree();
		}

		/**
		 * The command's context-menu path inside the source tree, derived from its
		 * registered {@link MenuPath}: drop the two top-level segments (the
		 * {@code Plugins > BigDataViewer-Playground} prefix that the source-tree
		 * popup does not repeat), keep the rest, ending at the leaf.
		 */
		private static String popupPath(Context context, Class<? extends Command> command) {
			CommandService cs = context.service(CommandService.class);
			MenuPath menuPath = (cs.getCommand(command) == null) ? null
					: cs.getCommand(command).getMenuPath();
			if (menuPath == null || menuPath.isEmpty()) {
				throw new IllegalStateException("Command " + command.getSimpleName()
						+ " has no menu path — cannot locate it in the source-tree popup.");
			}
			StringBuilder sb = new StringBuilder();
			for (int i = 2; i < menuPath.size() - 1; i++) {
				sb.append(menuPath.get(i).getName()).append(">");
			}
			sb.append(menuPath.getLeaf().getName());
			return sb.toString();
		}
	}
}
