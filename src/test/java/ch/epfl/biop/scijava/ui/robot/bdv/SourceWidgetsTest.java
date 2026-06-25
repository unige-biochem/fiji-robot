package ch.epfl.biop.scijava.ui.robot.bdv;

import ch.epfl.biop.scijava.ui.robot.bdv.command.BdvWidgets;
import ch.epfl.biop.scijava.ui.robot.widgets.Harvester;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;

import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end tests for the BDV harvester source widgets — the bdv-playground
 * cases dropped from the core {@code HarvesterWidgetsTest} and re-added here as
 * {@link BdvWidgets} drivers: single-source selection, multi-source selection
 * (parent node → all descendants), the {@code style="sorted"} drag variant, and
 * the {@code BdvHandle[]} multi-select handle list.
 *
 * <p>Example sources are procedural ({@link BdvTestSources}) — no Bio-Formats,
 * no download. Expected paths and counts are read back from the live source tree
 * rather than hardcoded, so the tests don't depend on bdv-playground's exact tree
 * layout for a plainly-registered source.</p>
 *
 * <p><b>Local only</b> — boots Fiji + bdv-playground and drives the screen with
 * {@code java.awt.Robot}; see {@link BdvTestFiji}. Run one GUI test class per
 * JVM.</p>
 */
public class SourceWidgetsTest {

	private static Context context;
	private static SourceAndConverter<?>[] sources;

	private static final String BDV_ALPHA = "BDV alpha";
	private static final String BDV_BETA = "BDV beta";

	@BeforeClass
	public static void setUpClass() {
		context = BdvTestFiji.boot();
		// Teach the harvester the BDV source / sorted / handle-list widgets.
		BdvWidgets.register();
		// Three cheap procedural sources to select among.
		sources = BdvTestSources.registerVoronoi(context, 3);
		// Two empty BDV windows for the handle-list test, parked on the right so
		// they never sit under the harvester dialog (dragged to the top edge).
		BdvTestFiji.newBdv(BDV_ALPHA, 820, 80);
		BdvTestFiji.newBdv(BDV_BETA, 820, 440);
	}

	@AfterClass
	public static void tearDownClass() {
		BdvWidgets.unregister();
		BdvTestFiji.shutdown();
	}

	// ─── SourceAndConverter → SwingSourceWidget (single-source JTree) ──────────

	@Test
	public void testSourceWidget() throws Exception {
		String name = BdvTestSources.leafName(sources[0]);
		String path = BdvTestSources.pathToLeaf(context, name);

		Future<CommandModule> future = Harvester.run(context, SourceDemo.class, "source", path);
		assertEquals("single-source widget should select exactly the leaf source",
				name, future.get().getOutput("result"));
	}

	@Plugin(type = Command.class)
	public static class SourceDemo implements Command {
		@Parameter(label = "Source") SourceAndConverter<?> source;
		@Parameter(type = ItemIO.OUTPUT) String result;
		@Override public void run() {
			result = source == null ? "null" : source.getSpimSource().getName();
		}
	}

	// ─── SourceAndConverter[] → SwingSourceListWidget (parent = all descendants) ─

	@Test
	public void testSourceListWidget() throws Exception {
		String ancestor = commonAncestorOfAllSources();
		int expected = BdvTestSources.leafCountUnder(context, ancestor);
		assertTrue("multi-source fixture should expose more than one source under the parent",
				expected > 1);

		Future<CommandModule> future = Harvester.run(context, SourceListDemo.class, "sources", ancestor);
		assertEquals("parent-node selection should yield every descendant source",
				expected, (int) (Integer) future.get().getOutput("count"));
	}

	@Plugin(type = Command.class)
	public static class SourceListDemo implements Command {
		@Parameter(label = "Sources") SourceAndConverter<?>[] sources;
		@Parameter(type = ItemIO.OUTPUT) int count;
		@Override public void run() { count = sources == null ? 0 : sources.length; }
	}

	// ─── SourceAndConverter[] style="sorted" → SwingSourceSortedListWidget ─────

	@Test
	public void testSourceSortedListWidget() throws Exception {
		String ancestor = commonAncestorOfAllSources();
		int expected = BdvTestSources.leafCountUnder(context, ancestor);

		Future<CommandModule> future = Harvester.run(context, SourceSortedListDemo.class, "sources", ancestor);
		assertEquals("dragging the parent node onto the sorted list should add every descendant",
				expected, (int) (Integer) future.get().getOutput("count"));
	}

	@Plugin(type = Command.class)
	public static class SourceSortedListDemo implements Command {
		@Parameter(label = "Sorted sources", style = "sorted") SourceAndConverter<?>[] sources;
		@Parameter(type = ItemIO.OUTPUT) int count;
		@Override public void run() { count = sources == null ? 0 : sources.length; }
	}

	// ─── BdvHandle[] → SwingBdvHandleListWidget (flat JList multi-select) ──────

	@Test
	public void testBdvHandleList() throws Exception {
		String[] names = new String[] { BDV_ALPHA, BDV_BETA };
		Future<CommandModule> future = Harvester.run(context, BdvHandleListDemo.class, "bdvs", names);
		CommandModule module = future.get();
		assertEquals("both BDV windows should be selected", 2, (int) (Integer) module.getOutput("count"));
		// getSelectedValuesList() returns ascending index order = ObjectService
		// registration order (alpha registered first), so titles are deterministic.
		assertEquals(BDV_ALPHA + "," + BDV_BETA, module.getOutput("titles"));
	}

	@Plugin(type = Command.class)
	public static class BdvHandleListDemo implements Command {
		@Parameter(label = "BDVs") BdvHandle[] bdvs;
		@Parameter(type = ItemIO.OUTPUT) int count;
		@Parameter(type = ItemIO.OUTPUT) String titles;
		@Override public void run() {
			count = bdvs == null ? 0 : bdvs.length;
			if (bdvs == null) { titles = ""; return; }
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < bdvs.length; i++) {
				if (i > 0) sb.append(",");
				sb.append(BdvHandleHelper.getWindowTitle(bdvs[i]));
			}
			titles = sb.toString();
		}
	}

	// ─── helpers ──────────────────────────────────────────────────────────────

	private static String commonAncestorOfAllSources() {
		String[] names = new String[sources.length];
		for (int i = 0; i < sources.length; i++) names[i] = BdvTestSources.leafName(sources[i]);
		return BdvTestSources.commonAncestorPath(context, names);
	}
}
