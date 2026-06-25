package ch.epfl.biop.scijava.ui.robot.bdv;

import ch.epfl.biop.scijava.ui.robot.CmdExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Plugin;

import static ch.epfl.biop.scijava.ui.robot.bdv.command.BdvLaunchers.treeLauncher;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless test of the tree launcher's {@code "sources"} contribution — the part
 * of the source-tree launch that must survive into the Groovy reproduction even
 * though the visible dialog has no sources widget.
 *
 * <p>No GUI, no bdv-playground runtime: {@code renderGroovy()} only exercises
 * {@code Launcher.contributedInputs(...)} and the renderer, so this runs anywhere
 * and pins the single / multi / root behaviour cheaply.</p>
 */
public class TreeLauncherRenderTest {

	private Context context;

	@Before
	public void setUp() {
		context = new Context(CommandService.class);
	}

	@After
	public void tearDown() {
		if (context != null) context.dispose();
	}

	@Test
	public void singlePath_contributesSourcesString() {
		String script = CmdExecutor.of(context, NoOpCommand.class)
				.withLauncher(treeLauncher("Other Sources"))
				.renderGroovy();
		assertTrue(script, script.contains("\"sources\", \"Other Sources\""));
	}

	@Test
	public void multiPath_contributesSourcesStringArray() {
		String script = CmdExecutor.of(context, NoOpCommand.class)
				.withLauncher(treeLauncher("alpha", "beta"))
				.renderGroovy();
		assertTrue(script, script.contains("\"sources\", new String[]{\"alpha\", \"beta\"}"));
	}

	@Test
	public void rootLaunch_contributesNoSources() {
		String script = CmdExecutor.of(context, NoOpCommand.class)
				.withLauncher(treeLauncher())
				.renderGroovy();
		assertFalse("root launch must not inject a sources input", script.contains("\"sources\""));
	}

	@Plugin(type = Command.class)
	public static class NoOpCommand implements Command {
		@Override public void run() {}
	}
}
