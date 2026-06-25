package ch.unige.biochem.fiji.robot;

/**
 * An input resolved <em>before</em> the command launches — i.e. pre-set on the
 * module so that, by the time the command's preprocessor chain runs, the input
 * is already satisfied (and any matching preprocessor / the harvester skips
 * it).
 *
 * <p>In a future visible-mode increment, a {@code PreSetResolution} that mirrors
 * a SciJava preprocessor (e.g. the active-image or active-BDV preprocessor) will
 * also carry a pre-launch gesture that establishes the ambient UI state the real
 * preprocessor reads — selecting the right window before {@code CommandService
 * .run} is called. The ambient state set before launch survives into the
 * preprocessor chain, which is why no in-chain machinery is needed.</p>
 *
 * <p>Accepted only by {@code CmdExecutor.PreLaunch#preSet}.</p>
 */
public interface PreSetResolution extends InputResolution {
}
