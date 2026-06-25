package ch.epfl.biop.scijava.ui.robot.ij1;

import ch.epfl.biop.scijava.ui.robot.CmdExecutor;

import ij.ImagePlus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.module.Module;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.epfl.biop.scijava.ui.robot.Launchers.programmaticLauncher;
import static ch.epfl.biop.scijava.ui.robot.ij1.Ij1Resolutions.selectActiveImage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * ij1 test of the value-bearing half of {@code selectActiveImage(...)}: in the
 * {@code programmaticLauncher()} projection, the resolution's {@code value()}
 * looks the {@link ImagePlus} up by title and passes it straight to the command,
 * so the run reads the active image without any preprocessor in play.
 *
 * <p>This is the simpler of the two ij1 tests — it exercises the value-bearing
 * half of the resolution. The visible half (the gesture establishing the active
 * image so {@code LegacyImagePreprocessor} reads it) is covered by
 * {@link SearchLauncherTest}.</p>
 *
 * <p><b>Local only</b> — it boots a real Fiji and needs an image window; see
 * {@link Ij1TestFiji}.</p>
 */
public class ActiveImagePresetTest {

	private static Context context;

	@BeforeClass
	public static void setUpClass() {
		context = Ij1TestFiji.context();
	}

	@AfterClass
	public static void tearDownClass() {
		Ij1TestFiji.shutdown();
	}

	@Test
	public void selectActiveImage_resolvesByTitle_andRunsProgrammatically() {
		Ij1TestFiji.showImage("preset-ramp");

		Module module = CmdExecutor.of(context, ImageTitleEchoCommand.class)
				.preSet("imp", selectActiveImage("preset-ramp"))
				.withLauncher(programmaticLauncher())
				.launch();

		assertNotNull("programmatic launcher should return the completed module", module);
		assertEquals("preset-ramp", module.getOutput("title"));
	}

	/** Echoes the title of whatever {@link ImagePlus} it is handed. */
	@Plugin(type = Command.class)
	public static class ImageTitleEchoCommand implements Command {
		@Parameter ImagePlus imp;
		@Parameter(type = ItemIO.OUTPUT) String title;
		@Override public void run() { title = imp.getTitle(); }
	}
}
