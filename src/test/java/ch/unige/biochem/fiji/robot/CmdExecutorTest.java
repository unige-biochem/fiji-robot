package ch.unige.biochem.fiji.robot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.module.Module;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.unige.biochem.fiji.robot.Launchers.programmaticLauncher;
import static ch.unige.biochem.fiji.robot.Resolutions.fromDialog;
import static ch.unige.biochem.fiji.robot.Resolutions.programmatic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Exercises the two projections of a {@link CmdExecutor} plan that need no GUI:
 * programmatic execution (run the command headless, read its output) and the
 * Groovy-render reproduction. Also covers the builder's runtime validation.
 *
 * <p>The builder's <em>compile-time</em> guarantees — pre-sets before the
 * launcher, exactly one launcher, dialog inputs only after launch, a single
 * terminal {@code launch()} — are enforced by the {@code PreLaunch}/
 * {@code PostLaunch} type-state and so can't (and needn't) be asserted at
 * runtime: an out-of-order call simply doesn't compile.</p>
 */
public class CmdExecutorTest {

	private Context context;

	@Before
	public void setUp() {
		context = new Context(CommandService.class);
	}

	@After
	public void tearDown() {
		if (context != null) context.dispose();
	}

	/** Minimal command: adds two ints and labels the result. */
	@Plugin(type = Command.class)
	public static class AddCommand implements Command {
		@Parameter int a;
		@Parameter int b;
		@Parameter(label = "Operation name") String op;
		@Parameter(type = ItemIO.OUTPUT) String result;

		@Override
		public void run() {
			result = op + "=" + (a + b);
		}
	}

	@Test
	public void programmaticRun_setsEveryInput_andReadsOutput() {
		Module module = CmdExecutor.of(context, AddCommand.class)
				.preSet("a", programmatic(2))
				.withLauncher(programmaticLauncher())
				.postSet("b", fromDialog(3))
				.postSet("op", fromDialog("sum"))
				.launch();

		assertNotNull("programmatic launcher should return the completed module", module);
		assertEquals("sum=5", module.getOutput("result"));
	}

	@Test
	public void renderGroovy_emitsRunnableSnippet_inDeclarationOrder() {
		String script = CmdExecutor.of(context, AddCommand.class)
				.preSet("a", programmatic(2))
				.withLauncher(programmaticLauncher())
				.postSet("op", fromDialog("sum", "We name the operation."))
				.renderGroovy();

		assertTrue(script, script.contains("import " + AddCommand.class.getName()));
		assertTrue(script, script.contains("cs.run(AddCommand.class, true,"));
		assertTrue(script, script.contains("\"a\", 2,"));
		assertTrue(script, script.contains("\"op\", \"sum\"  // We name the operation."));
		assertTrue(script, script.trim().endsWith(").get()"));
		// pre-set 'a' is declared before dialog 'op' — order is preserved.
		assertTrue("inputs should render in declaration order",
				script.indexOf("\"a\"") < script.indexOf("\"op\""));
	}

	@Test
	public void unknownParameterName_throwsEarly() {
		try {
			CmdExecutor.of(context, AddCommand.class).preSet("nope", programmatic(1));
			fail("expected IllegalArgumentException for an unknown @Parameter name");
		}
		catch (IllegalArgumentException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("nope"));
		}
	}

	@Test
	public void settingTheSameInputTwice_throws() {
		try {
			CmdExecutor.of(context, AddCommand.class)
					.preSet("a", programmatic(1))
					.withLauncher(programmaticLauncher())
					.postSet("a", fromDialog(2));
			fail("expected IllegalArgumentException for a duplicate input");
		}
		catch (IllegalArgumentException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("already set"));
		}
	}
}
