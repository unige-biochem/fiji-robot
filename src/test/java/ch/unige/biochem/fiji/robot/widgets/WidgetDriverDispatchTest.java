package ch.unige.biochem.fiji.robot.widgets;

import org.junit.After;
import org.junit.Test;

import java.awt.Container;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Headless test pinning the {@link WidgetDriver} extension-point contract — the
 * seam the BDV binding plugs its source / sorted / handle-list widgets into.
 *
 * <p>No display required: the recording driver below never touches the screen,
 * and the fall-through assertion relies on the built-in ladder throwing while
 * <em>locating</em> a widget (before any Robot motion). So this runs in headless
 * CI, unlike the widget end-to-end tests.</p>
 */
public class WidgetDriverDispatchTest {

	/** A driver that records what it was asked and optionally claims the value. */
	private static final class RecordingDriver implements WidgetDriver {
		final boolean claim;
		int matchCalls = 0;
		Object filledValue = null;

		RecordingDriver(boolean claim) { this.claim = claim; }

		@Override
		public boolean matches(Container inputContainer, Object value) {
			matchCalls++;
			return claim;
		}

		@Override
		public void fill(Container inputContainer, Object value) {
			filledValue = value;
		}
	}

	/** Remove anything a test registered, so the global registry can't leak between tests. */
	@After
	public void clearDrivers() {
		for (WidgetDriver d : Harvester.registeredDrivers()) {
			Harvester.unregisterDriver(d);
		}
	}

	@Test
	public void matchingDriverIsConsultedFirstAndWins() {
		RecordingDriver driver = new RecordingDriver(true);
		Harvester.registerDriver(driver);

		// A Boolean value would normally take the built-in JCheckBox path. The
		// matching driver must intercept it before that — and the empty container
		// proves the built-in path was NOT taken (it would throw "No JCheckBox").
		Object value = Boolean.TRUE;
		Harvester.fillWidget(new Container(), value);

		assertEquals("driver should have been consulted", 1, driver.matchCalls);
		assertSame("matching driver should have filled the widget", value, driver.filledValue);
	}

	@Test
	public void nonMatchingDriverFallsThroughToBuiltInLadder() {
		RecordingDriver driver = new RecordingDriver(false);
		Harvester.registerDriver(driver);

		// Driver declines → built-in ladder runs. A Boolean against an empty
		// container has no JCheckBox to find, so the built-in path throws while
		// locating the widget — proving the fall-through reached it.
		try {
			Harvester.fillWidget(new Container(), Boolean.TRUE);
			fail("expected the built-in ladder to throw when no checkbox is present");
		} catch (IllegalStateException expected) {
			// built-in JCheckBox lookup failed, as designed
		}
		assertEquals("declining driver should still have been consulted", 1, driver.matchCalls);
		assertEquals("declining driver must not fill", null, driver.filledValue);
	}

	@Test
	public void registrationIsIdempotentByIdentityAndOrdered() {
		RecordingDriver first = new RecordingDriver(false);
		RecordingDriver second = new RecordingDriver(true);
		Harvester.registerDriver(first);
		Harvester.registerDriver(second);
		Harvester.registerDriver(first); // duplicate by identity — must be ignored

		List<WidgetDriver> drivers = Harvester.registeredDrivers();
		assertEquals("duplicate identity registration should be ignored", 2, drivers.size());
		assertSame("registration order preserved", first, drivers.get(0));
		assertSame("registration order preserved", second, drivers.get(1));

		// Both consulted in order; the first declines, the second claims.
		Harvester.fillWidget(new Container(), "anything");
		assertEquals(1, first.matchCalls);
		assertEquals(1, second.matchCalls);
		assertSame("anything", second.filledValue);
	}

	@Test
	public void unregisterRemovesTheDriver() {
		RecordingDriver driver = new RecordingDriver(true);
		Harvester.registerDriver(driver);
		assertTrue(Harvester.unregisterDriver(driver));
		assertFalse("second removal reports absent", Harvester.unregisterDriver(driver));
		assertTrue("registry empty after removal", Harvester.registeredDrivers().isEmpty());
	}
}
