package ch.unige.biochem.fiji.robot.bdv;

import ch.unige.biochem.fiji.robot.bdv.view.Bdv;
import ch.unige.biochem.fiji.robot.core.Ui;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.service.SourceServices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Visible end-to-end tests for {@link Bdv} — the robot primitives that drive the
 * controls living <em>inside</em> a BigDataViewer window: the split-panel expand
 * arrow ({@link Bdv#setCardPanelExpanded}), the "Sources" card row selection
 * ({@link Bdv#selectSourceInCard}), the display-range spinners
 * ({@link Bdv#setDisplayRange}) and the timepoint slider ({@link Bdv#setTimepoint}).
 *
 * <p>One BDV window is booted with three procedural Voronoi sources
 * ({@link BdvTestSources}) shown in it, so the Sources card has real rows. Effects
 * are read back from the live model — {@code SplitPanel.isCollapsed()} for the card
 * panel, and each source's {@link ConverterSetup} display range for the
 * select-then-range gesture — rather than from anything the robot typed, so a test
 * passing means the gesture truly reached the widget.</p>
 *
 * <p>The Voronoi sources are single-timepoint, so the viewer has no timepoint
 * slider: {@link Bdv#setTimepoint} is exercised through its
 * {@link IllegalStateException} guard rather than a real drag.</p>
 *
 * <p><b>Local only</b> — boots Fiji + bdv-playground and drives the screen with
 * {@code java.awt.Robot}; see {@link BdvTestFiji}. Run one GUI test class per
 * JVM.</p>
 */
public class BdvViewTest {

	private static Context context;
	private static SourceAndConverter<?>[] sources;
	private static BdvHandle bdv;

	@BeforeClass
	public static void setUpClass() {
		context = BdvTestFiji.boot();
		// Three cheap procedural sources, shown in one window so the Sources card
		// lists three rows to select among.
		sources = BdvTestSources.registerVoronoi(context, 3);
		bdv = BdvTestFiji.newBdv("BDV view", 100, 100);
		// A roomy window so the card panel, source table and range spinners are all
		// reachable by the robot once the panel is expanded.
		java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(bdv.getViewerPanel());
		if (w != null) Ui.runOnEdt(() -> {
			w.setSize(820, 660);
			w.setLocation(100, 100);
		});
		Ui.rawPause(200);
		SourceServices.getBdvDisplayService().show(bdv, sources);
		Ui.rawPause(500);
	}

	@AfterClass
	public static void tearDownClass() {
		BdvTestFiji.shutdown();
	}

	/**
	 * Each test starts from a known state: card panel expanded. Tests that need
	 * it collapsed do so themselves; this just removes ordering coupling.
	 */
	@Before
	public void expandCard() {
		Bdv.setCardPanelExpanded(bdv, true);
		Ui.rawPause(200);
	}

	// ─── setCardPanelExpanded — collapse / expand the split panel ──────────────

	@Test
	public void testSetCardPanelExpanded() {
		assertFalse("@Before should leave the card panel expanded",
				bdv.getSplitPanel().isCollapsed());

		Bdv.setCardPanelExpanded(bdv, false);
		Ui.rawPause(200);
		assertTrue("clicking the one-touch arrow should collapse the card panel",
				bdv.getSplitPanel().isCollapsed());

		Bdv.setCardPanelExpanded(bdv, true);
		Ui.rawPause(200);
		assertFalse("clicking it again should expand the card panel",
				bdv.getSplitPanel().isCollapsed());
	}

	/** Already in the desired state → no-op, no toggle. */
	@Test
	public void testSetCardPanelExpandedIsIdempotent() {
		assertFalse(bdv.getSplitPanel().isCollapsed());
		Bdv.setCardPanelExpanded(bdv, true); // already expanded
		Ui.rawPause(200);
		assertFalse("expanding an already-expanded panel must not collapse it",
				bdv.getSplitPanel().isCollapsed());
	}

	// ─── selectSourceInCard + setDisplayRange — the two coupled gestures ───────

	@Test
	public void testSelectSourceThenSetDisplayRange() {
		// Pick the middle source so a wrong-row click (off-by-one) would be caught.
		SourceAndConverter<?> target = sources[1];
		String name = BdvTestSources.leafName(target);

		double min = 12, max = 234;
		Bdv.selectSourceInCard(bdv, name);
		Ui.rawPause(200);
		Bdv.setDisplayRange(bdv, min, max);
		Ui.rawPause(300);

		ConverterSetup cs = converterSetup(target);
		assertEquals("selected source min should follow the spinner", min, cs.getDisplayRangeMin(), 1e-6);
		assertEquals("selected source max should follow the spinner", max, cs.getDisplayRangeMax(), 1e-6);

		// The range edit must apply only to the selected source: the others keep
		// whatever they had (proves the row click landed on the right source).
		ConverterSetup other = converterSetup(sources[0]);
		boolean otherUntouched = other.getDisplayRangeMin() != min || other.getDisplayRangeMax() != max;
		assertTrue("setDisplayRange should not have touched the unselected source", otherUntouched);
	}

	@Test
	public void testSelectUnknownSourceThrows() {
		try {
			Bdv.selectSourceInCard(bdv, "no-such-source");
			fail("expected IllegalArgumentException for an unknown source name");
		}
		catch (IllegalArgumentException expected) {
			assertTrue("message should list the available source names",
					expected.getMessage().contains("available"));
		}
	}

	@Test
	public void testSelectSourceWhenCollapsedThrows() {
		Bdv.setCardPanelExpanded(bdv, false);
		Ui.rawPause(200);
		try {
			Bdv.selectSourceInCard(bdv, BdvTestSources.leafName(sources[0]));
			fail("expected IllegalStateException when the split panel is collapsed");
		}
		catch (IllegalStateException expected) {
			assertTrue("message should point at setCardPanelExpanded",
					expected.getMessage().contains("setCardPanelExpanded"));
		}
		finally {
			Bdv.setCardPanelExpanded(bdv, true);
			Ui.rawPause(200);
		}
	}

	// ─── setTimepoint — guard path (single-timepoint dataset, no slider) ───────

	@Test
	public void testSetTimepointThrowsWithoutSlider() {
		try {
			Bdv.setTimepoint(bdv, 1);
			fail("expected IllegalStateException — Voronoi sources are single-timepoint");
		}
		catch (IllegalStateException expected) {
			assertTrue("message should explain the missing slider",
					expected.getMessage().contains("timepoint slider"));
		}
	}

	// ─── helpers ───────────────────────────────────────────────────────────────

	private static ConverterSetup converterSetup(SourceAndConverter<?> sac) {
		return context.service(SourceService.class).getConverterSetup(sac);
	}
}