package ch.unige.biochem.fiji.robot.bdv;

import ch.unige.biochem.fiji.robot.core.Ui;

import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.Context;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.source.importer.VoronoiSourceCreator;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Cheap, offline example sources for the BDV widget tests, plus path/structure
 * queries against the live source tree.
 *
 * <p><b>No Bio-Formats, no download, no file.</b> bigdataviewer-playground ships
 * {@link VoronoiSourceCreator}, a procedural {@code SourceAndConverter} supplier;
 * a tiny 64³ Voronoi label image is computed lazily, so registering a handful is
 * near-instant and works on a headless build machine (the <em>creation</em> is
 * headless; only the widget <em>driving</em> needs a display). Distinct point
 * counts give each source a distinct name, hence a distinct tree-leaf label.</p>
 *
 * <p>Tests don't hardcode tree paths (bdv-playground's exact tree layout for a
 * plainly-registered source is an implementation detail). Instead they register
 * the sources, then read the path / leaf-count back from the live model with
 * {@link #pathToLeaf} / {@link #commonAncestorPath} / {@link #leafCountUnder} —
 * the same "derive the expectation from the model" approach the original
 * {@code WidgetsTest} used.</p>
 */
final class BdvTestSources {

	private BdvTestSources() {}

	/** The source-tree root label; every discovered path starts here. */
	static final String ROOT = "Sources";

	/**
	 * Create {@code n} procedural Voronoi sources with distinct names and
	 * register them in the {@link SourceService}, returning them in creation
	 * order. Each uses a different point count so its name (and tree leaf) is
	 * unique.
	 */
	static SourceAndConverter<?>[] registerVoronoi(Context context, int n) {
		SourceService ss = context.service(SourceService.class);
		SourceAndConverter<?>[] sacs = new SourceAndConverter[n];
		for (int i = 0; i < n; i++) {
			SourceAndConverter<FloatType> sac =
					new VoronoiSourceCreator(new long[] { 64, 64, 64 }, 50 + i, false).get();
			ss.register(sac);
			sacs[i] = sac;
		}
		// Let the tree model catch up with the registrations before paths are read.
		Ui.rawPause(500);
		return sacs;
	}

	/** The leaf label bdv-playground shows for {@code sac} (its source name). */
	static String leafName(SourceAndConverter<?> sac) {
		return sac.getSpimSource().getName();
	}

	/**
	 * Full {@code ">"}-delimited path (from {@link #ROOT}) to the first tree leaf
	 * whose label equals {@code leafName}. Throws with the available leaves if no
	 * such leaf exists.
	 */
	static String pathToLeaf(Context context, String leafName) {
		DefaultMutableTreeNode root = root(context);
		List<String> trail = new ArrayList<>();
		List<String> found = new ArrayList<>();
		if (!dfsLeaf(root, leafName, trail, found)) {
			throw new IllegalStateException("No tree leaf named '" + leafName + "'. Leaves: "
					+ allLeaves(root));
		}
		return String.join(">", found);
	}

	/**
	 * The deepest tree node that is an ancestor of <em>all</em> {@code leafNames}
	 * — i.e. the longest shared path prefix. Selecting this node in a
	 * source-list widget yields every source beneath it, which is the
	 * multi-source case under test.
	 */
	static String commonAncestorPath(Context context, String... leafNames) {
		String[][] parts = new String[leafNames.length][];
		for (int i = 0; i < leafNames.length; i++) {
			parts[i] = pathToLeaf(context, leafNames[i]).split(">");
		}
		List<String> common = new ArrayList<>();
		int col = 0;
		outer:
		while (true) {
			String seg = null;
			for (String[] p : parts) {
				// Stop one short of the leaf itself, so the ancestor is never the leaf.
				if (col >= p.length - 1) break outer;
				if (seg == null) seg = p[col];
				else if (!seg.equals(p[col])) break outer;
			}
			common.add(seg);
			col++;
		}
		if (common.isEmpty()) {
			throw new IllegalStateException("leaves share no common ancestor: "
					+ String.join(", ", leafNames));
		}
		return String.join(">", common);
	}

	/** Number of leaves under the node at {@code nodePath} (a {@code ">"} path from {@link #ROOT}). */
	static int leafCountUnder(Context context, String nodePath) {
		return countLeaves(nodeAt(root(context), nodePath.split(">")));
	}

	// ===== model walking ========================================================

	private static DefaultMutableTreeNode root(Context context) {
		SourceService ss = context.service(SourceService.class);
		TreeModel model = ss.tree().getTreeModel();
		DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
		if (root == null) throw new IllegalStateException("source tree has no root");
		return root;
	}

	/** DFS for a leaf named {@code leafName}; on success {@code found} holds the root→leaf labels. */
	private static boolean dfsLeaf(DefaultMutableTreeNode node, String leafName,
								   List<String> trail, List<String> found) {
		trail.add(node.toString());
		if (node.isLeaf()) {
			if (leafName.equals(node.toString())) {
				found.addAll(trail);
				trail.remove(trail.size() - 1);
				return true;
			}
		} else {
			for (int i = 0; i < node.getChildCount(); i++) {
				if (dfsLeaf((DefaultMutableTreeNode) node.getChildAt(i), leafName, trail, found)) {
					trail.remove(trail.size() - 1);
					return true;
				}
			}
		}
		trail.remove(trail.size() - 1);
		return false;
	}

	private static DefaultMutableTreeNode nodeAt(DefaultMutableTreeNode root, String[] parts) {
		if (!parts[0].equals(root.toString())) {
			throw new IllegalArgumentException("Tree root mismatch: expected '" + parts[0]
					+ "', got '" + root + "'");
		}
		DefaultMutableTreeNode node = root;
		for (int i = 1; i < parts.length; i++) {
			DefaultMutableTreeNode child = null;
			for (int c = 0; c < node.getChildCount(); c++) {
				DefaultMutableTreeNode cand = (DefaultMutableTreeNode) node.getChildAt(c);
				if (parts[i].equals(cand.toString())) { child = cand; break; }
			}
			if (child == null) {
				throw new IllegalArgumentException("Tree path mismatch at segment '" + parts[i]
						+ "' under '" + node + "'");
			}
			node = child;
		}
		return node;
	}

	private static int countLeaves(DefaultMutableTreeNode node) {
		if (node.isLeaf()) return 1;
		int n = 0;
		for (int i = 0; i < node.getChildCount(); i++) {
			n += countLeaves((DefaultMutableTreeNode) node.getChildAt(i));
		}
		return n;
	}

	private static List<String> allLeaves(DefaultMutableTreeNode node) {
		List<String> out = new ArrayList<>();
		collectLeaves(node, out);
		return out;
	}

	private static void collectLeaves(DefaultMutableTreeNode node, List<String> out) {
		if (node.isLeaf()) { out.add(node.toString()); return; }
		for (int i = 0; i < node.getChildCount(); i++) {
			collectLeaves((DefaultMutableTreeNode) node.getChildAt(i), out);
		}
	}
}
