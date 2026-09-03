package ch.unige.biochem.fiji.robot.core;

import ch.unige.biochem.fiji.robot.CmdExecutor;
import ch.unige.biochem.fiji.robot.groovy.GroovyScript;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static ch.unige.biochem.fiji.robot.Launchers.programmaticLauncher;
import static ch.unige.biochem.fiji.robot.Resolutions.fromDialog;
import static ch.unige.biochem.fiji.robot.Resolutions.programmatic;
import static org.junit.Assert.assertTrue;

/**
 * Headless test of the {@link GroovyScript} adapter that embeds a
 * {@code CmdExecutor}'s headless reproduction into {@code timeline.json}: a
 * {@code launch()} inside an open {@link Step} auto-records its {@code cs.run(...)}
 * body under that step, and {@code File} inputs hoist into the shared
 * {@code script.preamble} once for the whole demo. A step with no command run
 * stays visualization-only.
 */
public class RecordingScriptAdapterTest {

	private Context context;
	private File sessionDir;

	@Plugin(type = Command.class)
	public static class SaveBlur implements Command {
		@Parameter double sigma;
		@Parameter File output;
		@Override public void run() {}
	}

	@Before
	public void setUp() {
		ScreenRecorder.ENABLED = false;
		Screenshotter.ENABLED = false;
		Timeline.ENABLED = true;
		Step.AUTO_SNAP_END = false;
		Step.AUTO_SNAP_MOMENTS = false;
		context = new Context(CommandService.class);
		sessionDir = Assets.session("ScriptAdapterTest");
		GroovyScript.uninstall();
		new GroovyScript().install();
	}

	@After
	public void tearDown() {
		GroovyScript.uninstall();
		if (context != null) context.dispose();
		// See RecordingTimelineTest.tearDown — don't leak an open step into the
		// next test class sharing this JVM.
		if (Step.currentName() != null) Step.end();
	}

	@Test
	public void launchInsideStep_embedsBody_andHoistsFileToPreamble() throws Exception {
		Step.begin("save-blur", "We blur and save.");
		CmdExecutor.of(context, SaveBlur.class)
				.preSet("sigma", programmatic(2.0d, "A 2-pixel radius."))
				.withLauncher(programmaticLauncher())
				.postSet("output", fromDialog(new File("results/out.tif")))
				.launch();
		Step.end();

		// A step with no command run → no body → visualization-only.
		Step.begin("look", "We just look at the result.");
		Step.end();

		File f = new File(sessionDir, Timeline.FILENAME);
		String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		System.out.println("=== timeline.json (ScriptAdapterTest) ===\n" + json);

		// Shared preamble: hoisted #@File + #@CommandService cs + the command import.
		assertTrue(json, json.contains("\"script\": {"));
		assertTrue(json, json.contains("#@File"));
		assertTrue(json, json.contains("#@CommandService cs"));
		assertTrue(json, json.contains("import " + SaveBlur.class.getName()));

		// Per-step body for the actionable step: a cs.run referencing the hoisted file.
		assertTrue(json, json.contains("cs.run(SaveBlur.class, true,"));
		// sigma is not the last arg, so its line carries a trailing comma before the comment.
		assertTrue(json, json.contains("\\\"sigma\\\", 2.0d,  // A 2-pixel radius."));
		assertTrue(json, json.contains("\\\"output\\\", file1"));

		// The command-free step is explicitly visualization-only.
		assertTrue(json, json.contains(Timeline.VISUALIZATION_ONLY_NOTE));
	}
}
