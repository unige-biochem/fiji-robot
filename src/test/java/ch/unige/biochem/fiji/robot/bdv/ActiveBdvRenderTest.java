package ch.unige.biochem.fiji.robot.bdv;

import ch.unige.biochem.fiji.robot.CmdExecutor;

import bdv.util.BdvHandle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.unige.biochem.fiji.robot.Launchers.programmaticLauncher;
import static ch.unige.biochem.fiji.robot.bdv.command.BdvResolutions.selectActiveBdv;
import static org.junit.Assert.assertTrue;

/**
 * Headless test of the object-valued Groovy projection (TODO #7) for the BDV
 * binding: {@code selectActiveBdv(title)} renders the carried window-title spec
 * as a by-title lookup over a hoisted {@code ObjectService}, not its resolved
 * {@code BdvHandle} (which can't be turned back into the selecting title).
 *
 * <p>As with the IJ1 counterpart, no BDV is running here — rendering succeeds
 * only because the renderer never calls {@code value()} on a
 * {@code GroovyRenderable}.</p>
 */
public class ActiveBdvRenderTest {

	private Context context;

	@Before
	public void setUp() {
		context = new Context(CommandService.class);
	}

	@After
	public void tearDown() {
		if (context != null) context.dispose();
	}

	@Plugin(type = Command.class)
	public static class CenterOnBdv implements Command {
		@Parameter BdvHandle bdvh;
		@Override public void run() {}
	}

	@Test
	public void selectActiveBdv_rendersTitleLookup_withNoBdvRunning() {
		String script = CmdExecutor.of(context, CenterOnBdv.class)
				.preSet("bdvh", selectActiveBdv("BDV alpha"))
				.withLauncher(programmaticLauncher())
				.renderGroovy();

		System.out.println("=== selectActiveBdv render ===\n" + script);

		// Service hoisted as a script parameter, types imported.
		assertTrue(script, script.contains("#@ObjectService objectService"));
		assertTrue(script, script.contains("import bdv.util.BdvHandle"));
		assertTrue(script, script.contains("import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper"));
		// The carried title spec is what's rendered, via a by-title find.
		assertTrue(script, script.contains(
				"objectService.getObjects(BdvHandle.class).find"));
		assertTrue(script, script.contains("getWindowTitle(it) == \"BDV alpha\""));
	}
}
