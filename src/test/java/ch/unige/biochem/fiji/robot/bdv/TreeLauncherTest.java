/*-
 * #%L
 * A visible, recordable driver for SciJava commands — drives input
 * 		harvesting (and, later, java.awt.Robot gestures) so command runs can be
 * 		reproduced and recorded for tutorial videos.
 * %%
 * Copyright (C) 2026 University of Geneva, Department of Biochemistry
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package ch.unige.biochem.fiji.robot.bdv;

import ch.unige.biochem.fiji.robot.CmdExecutor;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import static ch.unige.biochem.fiji.robot.Resolutions.fromDialog;
import static ch.unige.biochem.fiji.robot.bdv.command.BdvLaunchers.treeLauncher;
import static org.junit.Assert.assertEquals;

/**
 * End-to-end bdv test of the visible source-tree launcher — the BDV spike, the
 * tree analog of the ij1 search spike.
 *
 * <p>It right-clicks the source tree's root, walks the context menu to a test
 * command, and lets the harvester drive the command's dialog. Running it proves
 * the two genuinely-new visible mechanics of {@code treeLauncher}: the
 * {@code Tree} right-click reaches the BDV-Playground source popup, and
 * {@code Popup} walks it to the command's menu path. The command's
 * {@code "sources"} contribution (the other half of the launcher) is pinned
 * headlessly by {@link TreeLauncherRenderTest}, so here a no-sources command is
 * enough to exercise the gesture chain.</p>
 *
 * <p>The command is launched through bdv-playground's own popup action
 * (which calls {@code cs.run}), so it records what it received in a static field
 * for the assertion.</p>
 *
 * <p><b>Local only</b> — boots Fiji + bdv-playground, opens the BDV Sources
 * frame, and drives the tree / popup / dialog with {@code java.awt.Robot}; see
 * {@link BdvTestFiji}.</p>
 */
public class TreeLauncherTest {

	private static Context context;

	@BeforeClass
	public static void setUpClass() {
		context = BdvTestFiji.boot();
		BdvTestFiji.showSourcesFrame(context);
	}

	@AfterClass
	public static void tearDownClass() {
		BdvTestFiji.shutdown();
	}

	@Test
	public void treeLauncher_walksSourcePopup_andDrivesDialog() {
		RobotEchoFactorCommand.reset();

		CmdExecutor.of(context, RobotEchoFactorCommand.class)
				.withLauncher(treeLauncher())   // right-click the "Sources" root
				.postSet("factor", fromDialog(7, "We set the factor."))
				.launch();

		assertEquals("dialog input should be driven after the popup walk",
				7, RobotEchoFactorCommand.seenFactor);
	}

	/**
	 * A source-tree action command (auto-registered because it implements
	 * {@link BdvPlaygroundActionCommand} under the BDV-Playground root menu).
	 * Takes no sources — the tree gesture only needs to reach and fire it — and
	 * records the harvested {@code factor}.
	 */
	@Plugin(type = BdvPlaygroundActionCommand.class,
			menuPath = BdvPgMenus.RootMenu + "Robot Tests>Robot Echo Factor")
	public static class RobotEchoFactorCommand implements BdvPlaygroundActionCommand {
		static volatile int seenFactor;

		static void reset() { seenFactor = -1; }

		@Parameter(label = "Factor") int factor;

		@Override public void run() { seenFactor = factor; }
	}
}
