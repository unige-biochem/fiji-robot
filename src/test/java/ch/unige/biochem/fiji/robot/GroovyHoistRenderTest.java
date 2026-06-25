package ch.unige.biochem.fiji.robot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

import static ch.unige.biochem.fiji.robot.Launchers.programmaticLauncher;
import static ch.unige.biochem.fiji.robot.Resolutions.fromDialog;
import static ch.unige.biochem.fiji.robot.Resolutions.programmatic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless test of {@code File} hoisting in the Groovy projection (TODO #7):
 * {@code File} inputs are lifted to top-level {@code #@File} script parameters
 * (deduped by absolute path) instead of inlined as {@code new File("…")}, so the
 * same physical file becomes one editable parameter and the script reads as a
 * proper SciJava parameterised script.
 */
public class GroovyHoistRenderTest {

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
	public static class ConvertCommand implements Command {
		@Parameter File input;
		@Parameter File output;
		@Override public void run() {}
	}

	@Test
	public void distinctFiles_hoistToSeparateParams() {
		String script = CmdExecutor.of(context, ConvertCommand.class)
				.preSet("input", programmatic(new File("data/in.tif")))
				.withLauncher(programmaticLauncher())
				.postSet("output", fromDialog(new File("data/out.tif")))
				.renderGroovy();

		System.out.println("=== File hoisting (distinct) ===\n" + script);

		// Two distinct paths → two #@File params; the call becomes a real script.
		assertTrue(script, script.contains("#@CommandService cs"));
		assertEquals("two #@File directives expected", 2, count(script, "#@File"));
		assertTrue(script, script.contains("\"input\", file1"));
		assertTrue(script, script.contains("\"output\", file2"));
		// Files are hoisted, never inlined.
		assertFalse(script, script.contains("new File("));
		// The directive carries the file's path/value.
		assertTrue(script, script.contains("in.tif"));
		assertTrue(script, script.contains("out.tif"));
	}

	@Test
	public void sameFileTwice_hoistsToOneParam() {
		File shared = new File("data/same.tif");
		String script = CmdExecutor.of(context, ConvertCommand.class)
				.preSet("input", programmatic(shared))
				.withLauncher(programmaticLauncher())
				.postSet("output", fromDialog(shared))
				.renderGroovy();

		System.out.println("=== File hoisting (dedup) ===\n" + script);

		// One physical file → one #@File param, referenced by both inputs.
		assertEquals("one #@File directive expected", 1, count(script, "#@File"));
		assertTrue(script, script.contains("\"input\", file1"));
		assertTrue(script, script.contains("\"output\", file1"));
	}

	private static int count(String haystack, String needle) {
		int n = 0, i = 0;
		while ((i = haystack.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
		return n;
	}
}
