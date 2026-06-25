package ch.unige.biochem.fiji.robot;

import ch.unige.biochem.fiji.robot.groovy.GroovyRender;
import ch.unige.biochem.fiji.robot.groovy.GroovyRenderContext;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.module.Module;
import org.scijava.plugin.Parameter;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A type-state builder that describes a single command run as an ordered recipe,
 * then either executes it or renders its headless Groovy equivalent.
 *
 * <p>The builder grammar is enforced by the compiler, not by runtime checks:</p>
 *
 * <pre>
 * CmdExecutor.of(context, MyCommand.class)   // -&gt; PreLaunch
 *     .preSet("image", selectActiveImage(...))   // zero or more, before launch
 *     .preSet("a", programmatic(2))
 *     .withLauncher(programmaticLauncher())   // exactly one -&gt; PostLaunch
 *     .postSet("min", fromDialog(0.0, "..."))     // zero or more, after launch
 *     .launch();                                  // terminal
 * </pre>
 *
 * <p>Because {@link PreLaunch#withLauncher} returns a different type
 * ({@link PostLaunch}), you cannot call a launcher twice, cannot {@code postSet}
 * before launching, and cannot forget to launch — the temporal order
 * <em>pre-set → launch → dialog</em> is the only well-typed path. This is the
 * "single point of execution" invariant made structural.</p>
 *
 * <p>The same plan feeds two projections: {@link PostLaunch#launch()} runs it,
 * {@link PostLaunch#renderGroovy()} renders the headless reproduction. Further
 * projections (timeline, visible Robot execution) are added without changing
 * this surface — they are additional readers of the same ordered resolutions.</p>
 */
public final class CmdExecutor {

	private CmdExecutor() {}

	/** Begin describing a run of {@code command} in {@code context}. */
	public static <C extends Command> PreLaunch<C> of(Context context, Class<C> command) {
		if (context == null) throw new IllegalArgumentException("context must not be null");
		if (command == null) throw new IllegalArgumentException("command must not be null");
		return new BuilderImpl<>(context, command);
	}

	// ===== Builder states =====================================================

	/** Pre-launch phase: collect pre-set inputs, then name the launcher. */
	public interface PreLaunch<C extends Command> {
		/** Add an input resolved before the command launches. */
		PreLaunch<C> preSet(String name, PreSetResolution resolution);

		/** Name the single launcher and transition to the post-launch phase. */
		PostLaunch<C> withLauncher(Launcher launcher);
	}

	/** Post-launch phase: collect dialog inputs, then launch or render. */
	public interface PostLaunch<C extends Command> {
		/** Add an input harvested from the command's dialog. */
		PostLaunch<C> postSet(String name, DialogResolution resolution);

		/** Execute the plan. Returns the completed module, or {@code null} when
		 *  the launcher cannot recover outputs. */
		Module launch();

		/** Render the headless Groovy reproduction of the plan. Does not run it. */
		String renderGroovy();
	}

	// ===== Implementation =====================================================

	private static final class BuilderImpl<C extends Command>
			implements PreLaunch<C>, PostLaunch<C> {

		private final Context context;
		private final Class<C> command;
		private final Set<String> paramNames;
		private final Map<String, InputResolution> resolutions = new LinkedHashMap<>();
		private Launcher launcher;

		BuilderImpl(Context context, Class<C> command) {
			this.context = context;
			this.command = command;
			this.paramNames = parameterNames(command);
		}

		@Override
		public PreLaunch<C> preSet(String name, PreSetResolution resolution) {
			record(name, resolution);
			return this;
		}

		@Override
		public PostLaunch<C> withLauncher(Launcher launcher) {
			if (launcher == null) throw new IllegalArgumentException("launcher must not be null");
			this.launcher = launcher;
			return this;
		}

		@Override
		public PostLaunch<C> postSet(String name, DialogResolution resolution) {
			record(name, resolution);
			return this;
		}

		@Override
		public Module launch() {
			return launcher.launch(mergedRequest());
		}

		@Override
		public String renderGroovy() {
			GroovyRenderContext ctx = new GroovyRenderContext();
			ctx.addImport(command.getName());
			Map<String, String> argExprs = new LinkedHashMap<>();
			Map<String, String> narrations = new LinkedHashMap<>();

			// Builder inputs, in declaration order. A GroovyRenderable resolution
			// renders its own spec (and never has value() called on it — so a plan
			// that selects the active image renders with no image open); everything
			// else falls back to a literal of its value.
			for (Map.Entry<String, InputResolution> e : resolutions.entrySet()) {
				String name = e.getKey();
				InputResolution r = e.getValue();
				String expr = (r instanceof GroovyRenderable)
						? ((GroovyRenderable) r).renderGroovy(ctx)
						: GroovyRender.literal(r.value(), ctx);
				argExprs.put(name, expr);
				if (r.narration() != null) narrations.put(name, r.narration());
			}

			// Launcher-contributed inputs (e.g. a tree launcher's "sources" path):
			// already plain, self-describing values — rendered as literals. Builder
			// inputs win on a name clash, matching mergedRequest().
			Map<String, Object> contributed = launcher.contributedInputs(renderRequest());
			for (Map.Entry<String, Object> e : contributed.entrySet()) {
				if (!argExprs.containsKey(e.getKey())) {
					argExprs.put(e.getKey(), GroovyRender.literal(e.getValue(), ctx));
				}
			}

			return GroovyRender.assemble(command, argExprs, narrations, ctx);
		}

		// --- internals --------------------------------------------------------

		private void record(String name, InputResolution resolution) {
			if (name == null) throw new IllegalArgumentException("input name must not be null");
			if (resolution == null) {
				throw new IllegalArgumentException("resolution for '" + name + "' must not be null");
			}
			if (!paramNames.contains(name)) {
				throw new IllegalArgumentException("No @Parameter named '" + name + "' on "
						+ command.getSimpleName() + ". Known parameters: " + paramNames);
			}
			if (resolutions.containsKey(name)) {
				throw new IllegalArgumentException("Input '" + name + "' is already set");
			}
			resolutions.put(name, resolution);
		}

		/**
		 * The request both {@link #launch()} and {@link #renderGroovy()} act on:
		 * the builder's resolutions, with the launcher's own contributions (e.g.
		 * a tree launcher's {@code "sources"}) folded in. Explicit builder inputs
		 * win on a name clash. Building it once for both projections is what keeps
		 * "what runs" and "what's rendered" from drifting apart.
		 *
		 * <p>The resolutions are kept in two shapes: a merged {@code name → value}
		 * view (what the headless launcher runs and the renderer emits) and the
		 * pre-set / dialog split (what a visible launcher drives phase by phase).
		 * The split keys off the resolution's static type — {@link PreSetResolution}
		 * vs {@link DialogResolution} — which the builder grammar already
		 * guarantees: pre-sets arrive via {@code preSet(...)}, dialog inputs via
		 * {@code postSet(...)}.</p>
		 */
		private LaunchRequest mergedRequest() {
			Map<String, Object> inputs = new LinkedHashMap<>();
			Map<String, String> narrations = new LinkedHashMap<>();
			Map<String, PreSetResolution> preSets = new LinkedHashMap<>();
			Map<String, DialogResolution> dialogs = new LinkedHashMap<>();
			for (Map.Entry<String, InputResolution> e : resolutions.entrySet()) {
				String name = e.getKey();
				InputResolution r = e.getValue();
				inputs.put(name, r.value());
				String narration = r.narration();
				if (narration != null) narrations.put(name, narration);
				if (r instanceof PreSetResolution) preSets.put(name, (PreSetResolution) r);
				else if (r instanceof DialogResolution) dialogs.put(name, (DialogResolution) r);
			}
			LaunchRequest base =
					new LaunchRequest(context, command, inputs, narrations, preSets, dialogs);
			Map<String, Object> contributed = launcher.contributedInputs(base);
			if (contributed.isEmpty()) return base;
			for (Map.Entry<String, Object> e : contributed.entrySet()) {
				inputs.putIfAbsent(e.getKey(), e.getValue());
			}
			return new LaunchRequest(context, command, inputs, narrations, preSets, dialogs);
		}

		/**
		 * A {@link LaunchRequest} safe to build during rendering: it omits the
		 * values of {@link GroovyRenderable} resolutions, so constructing it never
		 * calls their {@link InputResolution#value()} (which may require live UI
		 * state — an open image, a running BDV). It exists only so the launcher's
		 * {@link Launcher#contributedInputs} can be consulted while rendering; that
		 * contribution depends on the context and command, not on builder input
		 * values, so the omission is harmless.
		 */
		private LaunchRequest renderRequest() {
			Map<String, Object> inputs = new LinkedHashMap<>();
			Map<String, String> narrations = new LinkedHashMap<>();
			Map<String, PreSetResolution> preSets = new LinkedHashMap<>();
			Map<String, DialogResolution> dialogs = new LinkedHashMap<>();
			for (Map.Entry<String, InputResolution> e : resolutions.entrySet()) {
				String name = e.getKey();
				InputResolution r = e.getValue();
				if (!(r instanceof GroovyRenderable)) inputs.put(name, r.value());
				if (r.narration() != null) narrations.put(name, r.narration());
				if (r instanceof PreSetResolution) preSets.put(name, (PreSetResolution) r);
				else if (r instanceof DialogResolution) dialogs.put(name, (DialogResolution) r);
			}
			return new LaunchRequest(context, command, inputs, narrations, preSets, dialogs);
		}
	}

	/** Field names of every {@code @Parameter} on {@code command}, including inherited. */
	private static Set<String> parameterNames(Class<?> command) {
		Set<String> names = new HashSet<>();
		for (Class<?> c = command; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				if (f.isAnnotationPresent(Parameter.class)) names.add(f.getName());
			}
		}
		return names;
	}
}
