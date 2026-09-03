package ch.unige.biochem.fiji.robot.core;

import ch.unige.biochem.fiji.robot.CmdExecutor;
import ch.unige.biochem.fiji.robot.groovy.GroovyScript;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static ch.unige.biochem.fiji.robot.Launchers.programmaticLauncher;
import static ch.unige.biochem.fiji.robot.Resolutions.fromDialog;
import static ch.unige.biochem.fiji.robot.Resolutions.programmatic;

/**
 * Runnable, headless worked example of what a recorded demo emits — without a
 * display, ffmpeg or a real Fiji. Run it with:
 *
 * <pre>mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=ch.unige.biochem.fiji.robot.core.RecordingLayerDemo</pre>
 *
 * or straight from an IDE. It installs a {@link GroovyScript} recorder, opens an
 * {@link Assets} session, and brackets two narrated {@link Step}s. Inside a step
 * it runs a command through a {@link CmdExecutor} (headless
 * {@code programmaticLauncher()}) — and that {@code launch()} <em>auto-records</em>
 * the run's {@code cs.run(...)} body into the timeline under the open step, with
 * no extra call in the demo. The gestures a visible run would emit are simulated
 * via the package-private {@code Timeline.*At} entry points.
 *
 * <p>The result — printed to stdout and written to
 * {@code target/video-assets/RecordingLayerDemo/timeline.json} — is the exact
 * artifact the downstream video pipeline consumes: chapter steps, narration
 * sub-steps, gesture events, and the headless Groovy reproduction (shared
 * preamble + per-step bodies, with the command-free step left
 * visualization-only).</p>
 */
public class RecordingLayerDemo {

	/** A stand-in command so the recorded script has something real to render. */
	@Plugin(type = Command.class)
	public static class GaussianBlur implements Command {
		@Parameter double sigma;
		@Parameter(label = "Result name") String name;
		@Parameter File output;
		@Override public void run() { /* demo only */ }
	}

	public static void main(String[] args) throws Exception {
		// Pure-timeline mode: no ffmpeg, no PNGs, no screen — just timeline.json.
		ScreenRecorder.ENABLED = false;
		Screenshotter.ENABLED = false;
		Step.AUTO_SNAP_END = false;
		Step.AUTO_SNAP_MOMENTS = false;

		Context context = new Context(CommandService.class);
		File dir = Assets.session("RecordingLayerDemo");

		// Install the script recorder. From here on, every CmdExecutor.launch()
		// inside an open Step contributes its headless body to timeline.json.
		GroovyScript.uninstall();   // clear any prior run's recorder
		new GroovyScript().install();

		Timeline.setIntro("Gaussian blur in Fiji",
				"Run a command from the search bar and inspect the result.",
				Arrays.asList("Search bar", "Harvester dialog", "Result window"));

		Step.begin("apply-blur", "We run Gaussian Blur from the search bar.");
		Step.say("Typing 'gaussian blur' into the search bar.");
		Timeline.mouseClickAt(140, 70, Collections.emptyList());   // click search result
		Step.say("Setting sigma to 2 and choosing an output file.");
		// Camera focus a visible run would emit when the harvester dialog opens
		// (Ui.focus): the renderer frames this rect until the next focus event.
		Timeline.focusDialogAt("Gaussian Blur", 320, 40, 420, 300);
		Timeline.mouseClickAt(360, 240, Collections.emptyList());  // focus the field
		Timeline.keyPressAt("2", Collections.emptyList());
		Step.say("Clicking OK to run.");
		// The launch auto-records "sigma", "name", "output" as the step's body —
		// the File is hoisted to a #@File parameter in the shared preamble.
		CmdExecutor.of(context, GaussianBlur.class)
				.preSet("sigma", programmatic(2.0d, "We pick a 2-pixel radius."))
				.withLauncher(programmaticLauncher())
				.postSet("name", fromDialog("blurred"))
				// Absolute, neutral path so the committed sample carries no personal
				// home directory — File hoisting uses the absolute path verbatim.
				.postSet("output", fromDialog(new File("/data/blurred.tif")))
				.launch();
		Timeline.mouseClickAt(420, 320, Collections.emptyList());  // OK button
		Timeline.focusClear();                                     // dialog dismissed
		Step.end();

		Step.begin("inspect-result", "We zoom into the result to compare with the original.");
		// The result window appearing (Ui.waitForFrame) re-points the camera.
		Timeline.focusWindowAt("blurred", 500, 120, 640, 520);
		Timeline.mouseWheelAt(700, 400, -3, Collections.emptyList()); // no command → visualization-only
		Step.end();

		Timeline.setOutro("That's the whole workflow — try it on your own image.",
				Collections.singletonList(new CommandRef(
						"Gaussian Blur", "1.54", "https://imagej.net/ij/")),
				Arrays.asList("https://imagej.net/", "https://forum.image.sc/"));

		File timeline = new File(dir, Timeline.FILENAME);
		String json = new String(Files.readAllBytes(timeline.toPath()), StandardCharsets.UTF_8);
		System.out.println("Wrote " + timeline.getAbsolutePath() + "\n");
		System.out.println(json);

		// Refresh the committed sample asset so the repo always carries an
		// up-to-date example of the output. Run from the project root
		// (mvn exec:java does); skipped silently if that directory is unavailable.
		File sample = new File("docs/sample-timeline.json");
		if (sample.getParentFile().isDirectory() || sample.getParentFile().mkdirs()) {
			Files.write(sample.toPath(), json.getBytes(StandardCharsets.UTF_8));
			System.out.println("\nRefreshed " + sample.getPath());
		}

		context.dispose();
	}
}
