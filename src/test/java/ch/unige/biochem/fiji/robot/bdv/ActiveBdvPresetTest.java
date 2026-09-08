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

import bdv.util.BdvHandle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.module.Module;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;

import static ch.unige.biochem.fiji.robot.Launchers.programmaticLauncher;
import static ch.unige.biochem.fiji.robot.bdv.command.BdvResolutions.selectActiveBdv;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * bdv test of the value-bearing half of {@code selectActiveBdv(...)}: in the
 * {@code programmaticLauncher()} projection, the resolution looks the
 * {@link BdvHandle} up by window title and passes it straight to the command.
 *
 * <p>Two BDV windows are opened so the lookup is genuinely <em>title-targeted</em>
 * (not just "whichever is active") — the command must receive the one named in
 * {@code selectActiveBdv}, not the other. The visible half (the gesture that
 * makes a window active so {@code ActiveBdvPreprocessor} reads it) is the BDV
 * analog of the ij1 search spike; this test covers the headless value path.</p>
 *
 * <p><b>Local only</b> — boots Fiji + bdv-playground and opens BDV windows; see
 * {@link BdvTestFiji}.</p>
 */
public class ActiveBdvPresetTest {

	private static Context context;

	@BeforeClass
	public static void setUpClass() {
		context = BdvTestFiji.boot();
	}

	@AfterClass
	public static void tearDownClass() {
		BdvTestFiji.shutdown();
	}

	@Test
	public void selectActiveBdv_resolvesByTitle_andRunsProgrammatically() {
		BdvTestFiji.newBdv("BDV alpha");
		BdvTestFiji.newBdv("BDV beta");

		Module module = CmdExecutor.of(context, BdvTitleEchoCommand.class)
				.preSet("bdvh", selectActiveBdv("BDV beta"))
				.withLauncher(programmaticLauncher())
				.launch();

		assertNotNull("programmatic launcher should return the completed module", module);
		assertEquals("BDV beta", module.getOutput("title"));
	}

	/** Echoes the window title of whatever {@link BdvHandle} it is handed. */
	@Plugin(type = Command.class)
	public static class BdvTitleEchoCommand implements Command {
		@Parameter BdvHandle bdvh;
		@Parameter(type = ItemIO.OUTPUT) String title;
		@Override public void run() { title = BdvHandleHelper.getWindowTitle(bdvh); }
	}
}
