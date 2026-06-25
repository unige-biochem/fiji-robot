package ch.unige.biochem.fiji.robot.ij1;

import ch.unige.biochem.fiji.robot.CmdExecutor;

import ij.ImagePlus;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.unige.biochem.fiji.robot.Resolutions.fromDialog;
import static ch.unige.biochem.fiji.robot.ij1.Ij1Resolutions.selectActiveImage;
import static ch.unige.biochem.fiji.robot.ij1.Ij1Launchers.searchLauncher;
import static org.junit.Assert.assertEquals;

/**
 * End-to-end ij1 test of the visible two-phase model — the spike that validates
 * the whole design decision behind {@code preSet}.
 *
 * <p>It builds a plan with a {@code selectActiveImage(...)} pre-set and a
 * {@code fromDialog(...)} dialog input, launched via {@link #searchLauncher}.
 * Running it proves three things at once:</p>
 * <ol>
 *   <li>the pre-launch gesture activates the named image window;</li>
 *   <li>that ambient state <em>survives into the run</em> — the command's
 *       {@code @Parameter ImagePlus} is resolved by ImageJ's
 *       {@code LegacyImagePreprocessor} from the active image, even though the
 *       value is never passed programmatically;</li>
 *   <li>the harvester then drives only the dialog input ({@code factor}).</li>
 * </ol>
 *
 * <p>The search path drops the command's {@code Future}, so the command records
 * what it received in static fields for the assertions.</p>
 *
 * <p><b>Local only</b> — boots a real Fiji, types into the search bar, and drives
 * a dialog with {@code java.awt.Robot}; see {@link Ij1TestFiji}.</p>
 */
public class SearchLauncherTest {

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
	public void searchLauncher_activeImageSurvives_andDialogDriven() {
		Ij1TestFiji.showImage("search-ramp");
		RobotEchoActiveImageCommand.reset();

		CmdExecutor.of(context, RobotEchoActiveImageCommand.class)
				.preSet("imp", selectActiveImage("search-ramp"))
				.withLauncher(searchLauncher("Robot Echo Active Image"))
				.postSet("factor", fromDialog(7, "We set the factor."))
				.launch();

		assertEquals("active image should survive into LegacyImagePreprocessor",
				"search-ramp", RobotEchoActiveImageCommand.seenTitle);
		assertEquals("dialog input should be driven by the harvester",
				7, RobotEchoActiveImageCommand.seenFactor);
	}

	/**
	 * Unique menu name so the legacy search bar's top match is unambiguous.
	 * Records what it received: the active image's title (via the legacy
	 * preprocessor) and the harvested {@code factor}.
	 */
	@Plugin(type = Command.class, menuPath = "Plugins>Robot Tests>Robot Echo Active Image")
	public static class RobotEchoActiveImageCommand implements Command {
		static volatile String seenTitle;
		static volatile int seenFactor;

		static void reset() { seenTitle = null; seenFactor = -1; }

		@Parameter ImagePlus imp;
		@Parameter(label = "Factor") int factor;

		@Override public void run() {
			seenTitle = imp == null ? null : imp.getTitle();
			seenFactor = factor;
		}
	}
}
