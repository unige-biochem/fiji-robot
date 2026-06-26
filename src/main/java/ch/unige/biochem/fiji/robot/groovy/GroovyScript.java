package ch.unige.biochem.fiji.robot.groovy;

import ch.unige.biochem.fiji.robot.core.Step;
import ch.unige.biochem.fiji.robot.core.Timeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates the headless-equivalent Groovy script of a recorded demo and
 * serves it to {@link Timeline} as a {@link Timeline.ScriptSource}, so the
 * reproduction script is embedded structurally in {@code timeline.json}
 * (top-level {@code script.preamble} + per-step {@code script.body}).
 *
 * <p>It is the adapter between the two halves of the library: a
 * {@code CmdExecutor} run is the unit of script content, and a {@link Step} is
 * the unit of timeline structure. {@link #install()} marks an instance as the
 * {@linkplain #active() active recorder}; {@code CmdExecutor.launch()} then
 * renders each run's {@code cs.run(...)} body into this recorder's shared
 * {@link GroovyRenderContext} (so imports and hoisted {@code #@File} /
 * {@code #@Service} parameters are collected once for the whole demo) and files
 * it under the {@linkplain Step#currentName() open step}. A step with no run
 * recorded against it has no body — {@link Timeline} renders it
 * visualization-only.</p>
 *
 * <p>Unlike the original toolkit's static accumulator, this is instance-based:
 * a demo creates one, installs it, and lets it fall out of scope — there is no
 * global state to reset between sessions. {@link #uninstall()} detaches it (and
 * clears {@link Timeline#scriptSource}).</p>
 *
 * <pre>
 * GroovyScript script = new GroovyScript().install();
 * Step.begin("apply-blur", "We run Gaussian Blur.");
 * CmdExecutor.of(ctx, Blur.class).preSet(...).withLauncher(...).postSet(...).launch();
 * Step.end();   // timeline.json now carries this step's cs.run(...) body
 * </pre>
 */
public final class GroovyScript implements Timeline.ScriptSource {

	private static GroovyScript active;

	private final GroovyRenderContext ctx = new GroovyRenderContext();
	/** Step slug → the bodies recorded against it, in order. */
	private final Map<String, List<String>> bodies = new LinkedHashMap<>();
	/** Bodies recorded with no open step (rare — kept so nothing is silently lost). */
	private final List<String> prelude = new ArrayList<>();

	/** The installed recorder, or {@code null} when none is active. */
	public static GroovyScript active() { return active; }

	/** Make this the active recorder and wire it as {@link Timeline}'s script source. */
	public GroovyScript install() {
		active = this;
		Timeline.scriptSource = this;
		return this;
	}

	/** Detach the active recorder and clear {@link Timeline#scriptSource}. */
	public static void uninstall() {
		active = null;
		Timeline.scriptSource = null;
	}

	/** The shared render context — imports and hoisted {@code #@…} params for the whole demo. */
	public GroovyRenderContext context() { return ctx; }

	/**
	 * Record a rendered {@code cs.run(...)} body against the currently-open
	 * {@link Step}. Called by {@code CmdExecutor.launch()} on the active recorder.
	 * Multiple runs in one step are kept in order and joined by {@link #bodyForSlug}.
	 */
	public void recordBody(String body) {
		if (body == null) return;
		String slug = Step.currentName();
		if (slug == null) { prelude.add(body); return; }
		bodies.computeIfAbsent(slug, s -> new ArrayList<>()).add(body);
	}

	@Override
	public String preamble() {
		return GroovyRender.timelinePreamble(ctx);
	}

	@Override
	public String bodyForSlug(String slug) {
		List<String> b = bodies.get(slug);
		return (b == null || b.isEmpty()) ? null : String.join("\n", b);
	}
}
