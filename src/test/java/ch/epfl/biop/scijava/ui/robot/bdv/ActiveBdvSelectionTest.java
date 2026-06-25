package ch.epfl.biop.scijava.ui.robot.bdv;

import ch.epfl.biop.scijava.ui.robot.CmdExecutor;
import ch.epfl.biop.scijava.ui.robot.core.Ui;

import bdv.util.BdvHandle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;

import java.awt.Frame;

import static ch.epfl.biop.scijava.ui.robot.Resolutions.fromDialog;
import static ch.epfl.biop.scijava.ui.robot.bdv.command.BdvLaunchers.treeLauncher;
import static ch.epfl.biop.scijava.ui.robot.bdv.command.BdvResolutions.selectActiveBdv;
import static org.junit.Assert.assertEquals;

/**
 * Visible end-to-end test of the BDV window selector — the BDV analog of the ij1
 * {@code SearchLauncherTest}, and the one bdv test that exercises
 * {@code selectActiveBdv}'s <em>gesture</em>.
 *
 * <p>The flow you watch on screen:</p>
 * <ol>
 *   <li>two BDV windows open ("BDV alpha", "BDV beta"); alpha is made active;</li>
 *   <li>the cursor travels to the "BDV beta" title bar and clicks it — that is
 *       {@code selectActiveBdv("BDV beta")}'s pre-launch gesture, run by
 *       {@code treeLauncher} via {@code LaunchRequest.runPreSetGestures()};</li>
 *   <li>the cursor moves to the "BDV Sources" tree, right-clicks the root, and
 *       walks the popup to the command;</li>
 *   <li>the harvester drives the command's dialog.</li>
 * </ol>
 *
 * <p>The assertion proves the gesture's effect survived: even though focus then
 * moved to the tree, the command's {@code @Parameter BdvHandle} is resolved by
 * {@code ActiveBdvPreprocessor} to "BDV beta" (via the {@code LAST_ACTIVE_BDVH}
 * the activation cached), not "BDV alpha".</p>
 *
 * <p><b>Local only</b> — boots Fiji + bdv-playground and drives the screen; see
 * {@link BdvTestFiji}. Run one GUI test class per JVM.</p>
 */
public class ActiveBdvSelectionTest {

	private static Context context;

	@BeforeClass
	public static void setUpClass() {
		context = BdvTestFiji.boot();
		// Tree on the left, BDV windows on the right (placed in the test) — so the
		// Robot's tree right-click never lands on a BDV window.
		Frame sources = BdvTestFiji.showSourcesFrame(context);
		Ui.placeFrame(sources);
	}

	@AfterClass
	public static void tearDownClass() {
		BdvTestFiji.shutdown();
	}

	@Test
	public void selectActiveBdv_gestureSurvivesIntoPreprocessor_underTreeLaunch() {
		// Stacked vertically on the right (420x320 each), clear of each other and
		// of the source tree on the left — so each title-bar click is unobscured.
		BdvHandle alpha = BdvTestFiji.newBdv("BDV alpha", 760, 60);
		BdvTestFiji.newBdv("BDV beta", 760, 440);
		// Make alpha active first, so picking beta is a real switch the gesture
		// has to perform (not just "whatever was created last").
		Ui.runOnEdt(() -> BdvHandleHelper.activateWindow(alpha));
		Ui.rawPause(400);

		RobotEchoActiveBdvCommand.reset();

		CmdExecutor.of(context, RobotEchoActiveBdvCommand.class)
				.preSet("bdvh", selectActiveBdv("BDV beta"))   // visible window grab
				.withLauncher(treeLauncher())                  // right-click the source tree
				.postSet("factor", fromDialog(7, "We set the factor."))
				.launch();

		assertEquals("the selected BDV should survive into ActiveBdvPreprocessor",
				"BDV beta", RobotEchoActiveBdvCommand.seenTitle);
		assertEquals("the dialog input should be driven by the harvester",
				7, RobotEchoActiveBdvCommand.seenFactor);
	}

	/**
	 * A source-tree action command (auto-registered as a {@link BdvPlaygroundActionCommand}
	 * under the BDV-Playground root menu). Its {@code BdvHandle} is filled by
	 * {@code ActiveBdvPreprocessor} from the active window; {@code factor} comes
	 * from the dialog. Records both for the assertions.
	 */
	@Plugin(type = BdvPlaygroundActionCommand.class,
			menuPath = BdvPgMenus.RootMenu + "Robot Tests>Robot Echo Active Bdv")
	public static class RobotEchoActiveBdvCommand implements BdvPlaygroundActionCommand {
		static volatile String seenTitle;
		static volatile int seenFactor;

		static void reset() { seenTitle = null; seenFactor = -1; }

		@Parameter BdvHandle bdvh;
		@Parameter(label = "Factor") int factor;

		@Override public void run() {
			seenTitle = (bdvh == null) ? null : BdvHandleHelper.getWindowTitle(bdvh);
			seenFactor = factor;
		}
	}
}
