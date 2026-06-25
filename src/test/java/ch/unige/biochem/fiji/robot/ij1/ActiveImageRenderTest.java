package ch.unige.biochem.fiji.robot.ij1;

import ch.unige.biochem.fiji.robot.CmdExecutor;

import ij.ImagePlus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.unige.biochem.fiji.robot.Launchers.programmaticLauncher;
import static ch.unige.biochem.fiji.robot.Resolutions.fromDialog;
import static ch.unige.biochem.fiji.robot.ij1.Ij1Resolutions.selectActiveImage;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless test of the object-valued Groovy projection (TODO #7) for the IJ1
 * binding: {@code selectActiveImage(title)} renders the carried window-title
 * <em>spec</em>, not its resolved {@code ImagePlus} (which can't be turned back
 * into the title that selected it).
 *
 * <p>The decisive property: <b>no image is open here</b>. If rendering went
 * through {@code value()} — {@code WindowManager.getImage(title)} → {@code null}
 * → {@code IllegalStateException} — this test would throw. It passes precisely
 * because a {@code GroovyRenderable} resolution renders from its spec and the
 * renderer never calls {@code value()} on it.</p>
 */
public class ActiveImageRenderTest {

	private Context context;

	@Before
	public void setUp() {
		context = new Context(CommandService.class);
	}

	@After
	public void tearDown() {
		if (context != null) context.dispose();
	}

	/** A command with an image input and a numeric dialog input. */
	@Plugin(type = Command.class)
	public static class BlurImage implements Command {
		@Parameter ImagePlus imp;
		@Parameter double radius;
		@Override public void run() {}
	}

	@Test
	public void selectActiveImage_rendersTitleLookup_withNoImageOpen() {
		String script = CmdExecutor.of(context, BlurImage.class)
				.preSet("imp", selectActiveImage("blobs.gif"))
				.withLauncher(programmaticLauncher())
				.postSet("radius", fromDialog(2.0, "We pick a 2-pixel radius."))
				.renderGroovy();

		System.out.println("=== selectActiveImage render ===\n" + script);

		// The spec is carried and rendered, not the (absent) live object.
		assertTrue(script, script.contains("import ij.WindowManager"));
		assertTrue(script, script.contains("\"imp\", WindowManager.getImage(\"blobs.gif\")"));
		// The ordinary dialog input still renders as a plain literal.
		assertTrue(script, script.contains("\"radius\", 2.0d  // We pick a 2-pixel radius."));
		// No object-valued input means no #@ script-param preamble was needed.
		assertFalse(script, script.contains("#@"));
	}
}
