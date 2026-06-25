package ch.unige.biochem.fiji.robot;

/**
 * An input harvested from the command's dialog <em>after</em> it launches.
 *
 * <p>In programmatic mode a {@code DialogResolution} behaves exactly like a
 * {@link PreSetResolution}: its {@link #value()} is set directly on the module,
 * so the input harvester finds nothing left to ask for and no dialog appears.
 * The two kinds only diverge in visible mode, where a {@code DialogResolution}
 * drives the real Swing widget the harvester shows (a later increment), while a
 * {@code PreSetResolution} establishes ambient state before launch.</p>
 *
 * <p>Accepted only by {@code CmdExecutor.PostLaunch#postSet}.</p>
 */
public interface DialogResolution extends InputResolution {
}
