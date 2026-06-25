package ch.unige.biochem.fiji.robot;

import ch.unige.biochem.fiji.robot.groovy.GroovyRenderContext;

/**
 * Opt-in capability for an {@link InputResolution} that knows how to render
 * <em>itself</em> as a Groovy expression — the script-projection counterpart of
 * the {@link Gesture} capability (which is the visible projection).
 *
 * <p><b>Why a resolution renders itself rather than its {@link #value()}.</b>
 * For object-valued inputs the mapping from the original specification to the
 * resolved value is <em>not</em> reversible. A selector like
 * {@code "Dataset>Channel>0"} resolves to a {@code SourceAndConverter[]}, a
 * window title resolves to an {@code ImagePlus} / {@code BdvHandle} — but the
 * resolved object keeps no record of the selector that produced it, and many
 * selectors map to the same object. So the script projection cannot recover a
 * faithful, runnable literal from {@link #value()}; it must carry the original
 * specification (the selector, the title) from construction and render
 * <em>that</em>. A resolution implementing this interface does exactly that.</p>
 *
 * <p>Resolutions that do not implement this interface fall back to
 * {@code GroovyRender.literal(value())} — correct for primitives, strings,
 * {@code File}s and arrays thereof, where the value <em>is</em> a faithful
 * literal. Implement this only when the value is not self-describing.</p>
 *
 * <p>Crucially, the renderer calls {@link #renderGroovy} <em>instead of</em>
 * {@link #value()} — so rendering a plan never forces the live object to exist.
 * A plan that selects the active image renders without an image open.</p>
 */
public interface GroovyRenderable {

	/**
	 * This input as a Groovy expression to splice into a {@code cs.run(...)}
	 * argument list. The expression may reference imports and hoisted script
	 * parameters declared through {@code ctx} (e.g.
	 * {@code ctx.addImport("ij.WindowManager")},
	 * {@code ctx.requireScriptParam("ObjectService", "objectService")}).
	 *
	 * @param ctx the per-render accumulator for imports and hoisted parameters
	 * @return the Groovy expression (never {@code null})
	 */
	String renderGroovy(GroovyRenderContext ctx);
}