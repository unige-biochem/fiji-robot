package ch.unige.biochem.fiji.robot.core;

/**
 * Neutral, framework-agnostic reference to one Fiji/SciJava command that a demo
 * invoked — display name, version, and a URL to its source.
 *
 * <p>Carried into {@code timeline.json}'s {@code outro.commands[]} block. It
 * lives in {@code core/} as a plain DTO so {@link Timeline} can reference it
 * without dragging in any SciJava import — the {@code core/} package stays
 * Fiji-unaware.</p>
 */
public final class CommandRef {

	public final String name;
	public final String version;
	public final String sourceUrl;

	public CommandRef(String name, String version, String sourceUrl) {
		this.name = name;
		this.version = version;
		this.sourceUrl = sourceUrl;
	}
}