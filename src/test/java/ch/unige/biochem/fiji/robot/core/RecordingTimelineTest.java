package ch.unige.biochem.fiji.robot.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless exercise of the recording layer ({@link Step} / {@link Timeline}).
 *
 * <p>With {@link ScreenRecorder} and {@link Screenshotter} disabled and only
 * {@link Timeline} enabled, a demo produces a complete, deterministic
 * {@code timeline.json} — no ffmpeg, no display capture, no PNGs. That decoupled
 * mode is what makes the recording layer unit-testable: this test drives a
 * two-step "demo" exactly as a real script would (begin / say / end, intro /
 * outro) and asserts the resulting JSON shape. The gesture events that a visible
 * run emits from {@link Ui} / {@link EventRecorder} are simulated here via the
 * package-private {@code Timeline.*At} entry points so no Robot / screen is
 * needed.</p>
 *
 * <p>Lives in the {@code core} package so it can call those package-private
 * gesture entry points directly.</p>
 */
public class RecordingTimelineTest {

	private File sessionDir;

	@Before
	public void setUp() {
		// Pure-timeline mode: no ffmpeg, no PNGs, no AWT global listener.
		ScreenRecorder.ENABLED = false;
		Screenshotter.ENABLED = false;
		Timeline.ENABLED = true;
		Timeline.scriptSource = null;
		Step.AUTO_SNAP_END = false;
		Step.AUTO_SNAP_MOMENTS = false;
		sessionDir = Assets.session("RecordingLayerTest");
	}

	@After
	public void tearDown() {
		Timeline.scriptSource = null;
	}

	private String runTwoStepDemo() throws Exception {
		Timeline.setIntro("BDV Playground tour",
				"Open sources, run a command, inspect the result.",
				Arrays.asList("Source tree", "Harvester dialog"));

		Step.begin("open-tree", "We open the BigDataViewer-Playground source tree.");
		Step.say("Typing the command name into the search bar.");
		// Gestures a visible run would emit from Ui.click()/Ui.drag()/etc.
		Timeline.mouseClickAt(120, 80, Collections.emptyList());
		Step.say("Dragging the tree below the main window.");
		Timeline.mouseDragAt(200, 200, 200, 600, 40,
				Collections.singletonList("shift"), null);
		Step.end();

		Step.begin("set-timepoint", "We step the viewer to a later timepoint.");
		Timeline.keyPressAt("Right", Collections.singletonList("ctrl"));
		Step.end();

		Timeline.setOutro("Thanks for watching!",
				Collections.singletonList(new CommandRef(
						"Show Sources",
						"0.21.0",
						"https://github.com/bigdataviewer/bigdataviewer-playground")),
				Collections.singletonList("https://imagej.net/plugins/bdvplayground"));

		File f = new File(sessionDir, Timeline.FILENAME);
		assertTrue("timeline.json should exist", f.exists());
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
	}

	@Test
	public void timeline_capturesSteps_comments_events_introOutro() throws Exception {
		String json = runTwoStepDemo();
		System.out.println("=== timeline.json (RecordingLayerTest) ===\n" + json);

		// Envelope
		assertTrue(json, json.contains("\"version\": 4"));
		assertTrue(json, json.contains("\"session\": \"RecordingLayerTest\""));

		// Intro
		assertTrue(json, json.contains("\"title\": \"BDV Playground tour\""));
		assertTrue(json, json.contains("\"Source tree\""));

		// Steps: deterministic clip names, in lockstep with the step counter.
		assertTrue(json, json.contains("\"slug\": \"open-tree\""));
		assertTrue(json, json.contains("\"clip\": \"001-open-tree.mp4\""));
		assertTrue(json, json.contains("\"slug\": \"set-timepoint\""));
		assertTrue(json, json.contains("\"clip\": \"002-set-timepoint.mp4\""));

		// Narration sub-steps
		assertTrue(json, json.contains("Typing the command name into the search bar."));
		assertTrue(json, json.contains("Dragging the tree below the main window."));

		// Events: a click, a drag (with endX/endY + points + modifier), a key press.
		assertTrue(json, json.contains("\"type\":\"mouse.click\""));
		assertTrue(json, json.contains("\"type\":\"mouse.drag\""));
		assertTrue(json, json.contains("\"endX\":200,\"endY\":600"));
		assertTrue(json, json.contains("\"points\":40"));
		assertTrue(json, json.contains("\"type\":\"key.press\""));
		assertTrue(json, json.contains("\"key\":\"Right\""));
		assertTrue(json, json.contains("\"modifiers\":[\"ctrl\"]"));

		// Outro
		assertTrue(json, json.contains("\"closing\": \"Thanks for watching!\""));
		assertTrue(json, json.contains("\"name\":\"Show Sources\""));
		assertTrue(json, json.contains("imagej.net/plugins/bdvplayground"));

		// No script block unless a ScriptSource is registered.
		assertFalse("script block should be absent without a ScriptSource",
				json.contains("\"script\":"));
	}

	@Test
	public void scriptSource_embedsPreamble_andPerStepBody() throws Exception {
		Timeline.scriptSource = new Timeline.ScriptSource() {
			@Override public String preamble() {
				return "#@CommandService cs\n\nimport sc.fiji.Show";
			}
			@Override public String bodyForSlug(String slug) {
				// "open-tree" is actionable; "set-timepoint" is visualization-only.
				return "open-tree".equals(slug) ? "cs.run(Show.class, true).get()" : null;
			}
		};

		String json = runTwoStepDemo();

		assertTrue(json, json.contains("\"script\": {"));
		assertTrue(json, json.contains("#@CommandService cs"));
		assertTrue(json, json.contains("cs.run(Show.class, true).get()"));
		// The visualization-only step carries the explicit note, not a body.
		assertTrue(json, json.contains(Timeline.VISUALIZATION_ONLY_NOTE));
	}

	@Test
	public void timelineDisabled_writesNothing() {
		Timeline.ENABLED = false;
		try {
			Assets.session("DisabledTest");
			Step.begin("noop", "nothing recorded");
			Step.end();
			File f = new File(Assets.dir(), Timeline.FILENAME);
			assertFalse("no timeline.json when Timeline is disabled", f.exists());
		} finally {
			Timeline.ENABLED = true;
		}
	}

	/**
	 * The widget drivers call {@link Step#say} unconditionally during any visible
	 * command run, recorded or not — so it must be a safe no-op outside an open
	 * step (regression guard: it used to throw, breaking the GUI launcher tests).
	 */
	@Test
	public void say_outsideAnyStep_isNoOp() {
		// No Step.begin — must not throw.
		Step.say("nobody is recording this");
	}
}