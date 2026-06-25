package ch.unige.biochem.fiji.robot.core;

import ch.unige.biochem.fiji.robot.groovy.GroovyRender;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runnable, headless worked example of what a recorded demo emits — without a
 * display, ffmpeg or a real Fiji. Run it with:
 *
 * <pre>mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=ch.unige.biochem.fiji.robot.core.RecordingLayerDemo</pre>
 *
 * or straight from an IDE. It opens an {@link Assets} session, brackets two
 * narrated {@link Step}s (simulating the gestures a visible run would emit), and
 * wires {@link Timeline}'s embedded reproduction {@code script} to
 * {@link GroovyRender}. The result — printed to stdout and written to
 * {@code target/video-assets/RecordingLayerDemo/timeline.json} — is the exact
 * artifact the downstream video pipeline consumes: chapter steps, narration
 * sub-steps, gesture events, and the headless Groovy equivalent.
 */
public class RecordingLayerDemo {

	/** A stand-in command so the embedded script has something real to render. */
	@Plugin(type = Command.class)
	public static class GaussianBlur implements Command {
		@Parameter double sigma;
		@Parameter(label = "Result name") String name;
		@Override public void run() { /* demo only */ }
	}

	public static void main(String[] args) throws Exception {
		// Pure-timeline mode: no ffmpeg, no PNGs, no screen — just timeline.json.
		ScreenRecorder.ENABLED = false;
		Screenshotter.ENABLED = false;
		Step.AUTO_SNAP_END = false;
		Step.AUTO_SNAP_MOMENTS = false;

		File dir = Assets.session("RecordingLayerDemo");

		// Per-step headless reproduction snippets, rendered by GroovyRender — the
		// same projector the CmdExecutor uses. The recording layer stays unaware
		// of how the script is produced: it only sees a ScriptSource.
		Map<String, Object> inputs = new LinkedHashMap<>();
		inputs.put("sigma", 2.0d);
		inputs.put("name", "blurred");
		String blurBody = GroovyRender.renderRun(GaussianBlur.class, inputs,
				Collections.singletonMap("sigma", "We pick a 2-pixel radius."));

		Map<String, String> bodies = new LinkedHashMap<>();
		bodies.put("apply-blur", blurBody);
		// "inspect-result" has no headless equivalent — it's visualization-only.

		Timeline.scriptSource = new Timeline.ScriptSource() {
			@Override public String preamble() {
				return "#@CommandService cs\n\nimport " + GaussianBlur.class.getName();
			}
			@Override public String bodyForSlug(String slug) {
				return bodies.get(slug);
			}
		};

		Timeline.setIntro("Gaussian blur in Fiji",
				"Run a command from the search bar and inspect the result.",
				Arrays.asList("Search bar", "Harvester dialog", "Result window"));

		Step.begin("apply-blur", "We run Gaussian Blur from the search bar.");
		Step.say("Typing 'gaussian blur' into the search bar.");
		Timeline.mouseClickAt(140, 70, Collections.emptyList());   // click search result
		Step.say("Setting sigma to 2 in the dialog.");
		Timeline.mouseClickAt(360, 240, Collections.emptyList());  // focus the field
		Timeline.keyPressAt("2", Collections.emptyList());
		Step.say("Clicking OK to run.");
		Timeline.mouseClickAt(420, 320, Collections.emptyList());  // OK button
		Step.end();

		Step.begin("inspect-result", "We zoom into the result to compare with the original.");
		Timeline.mouseWheelAt(700, 400, -3, Collections.emptyList()); // zoom in
		Step.end();

		Timeline.setOutro("That's the whole workflow — try it on your own image.",
				Collections.singletonList(new CommandRef(
						"Gaussian Blur", "1.54", "https://imagej.net/ij/")),
				Arrays.asList("https://imagej.net/", "https://forum.image.sc/"));

		File timeline = new File(dir, Timeline.FILENAME);
		String json = new String(Files.readAllBytes(timeline.toPath()), StandardCharsets.UTF_8);
		System.out.println("Wrote " + timeline.getAbsolutePath() + "\n");
		System.out.println(json);
	}
}