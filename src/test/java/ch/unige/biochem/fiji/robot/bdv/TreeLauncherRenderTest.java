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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Plugin;

import static ch.unige.biochem.fiji.robot.bdv.command.BdvLaunchers.treeLauncher;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless test of the tree launcher's {@code "sources"} contribution — the part
 * of the source-tree launch that must survive into the Groovy reproduction even
 * though the visible dialog has no sources widget.
 *
 * <p>No GUI, no bdv-playground runtime: {@code renderGroovy()} only exercises
 * {@code Launcher.contributedInputs(...)} and the renderer, so this runs anywhere
 * and pins the single / multi / root behaviour cheaply.</p>
 */
public class TreeLauncherRenderTest {

	private Context context;

	@Before
	public void setUp() {
		context = new Context(CommandService.class);
	}

	@After
	public void tearDown() {
		if (context != null) context.dispose();
	}

	@Test
	public void singlePath_contributesSourcesString() {
		String script = CmdExecutor.of(context, NoOpCommand.class)
				.withLauncher(treeLauncher("Other Sources"))
				.renderGroovy();
		assertTrue(script, script.contains("\"sources\", \"Other Sources\""));
	}

	@Test
	public void multiPath_contributesSourcesStringArray() {
		String script = CmdExecutor.of(context, NoOpCommand.class)
				.withLauncher(treeLauncher("alpha", "beta"))
				.renderGroovy();
		assertTrue(script, script.contains("\"sources\", new String[]{\"alpha\", \"beta\"}"));
	}

	@Test
	public void rootLaunch_contributesNoSources() {
		String script = CmdExecutor.of(context, NoOpCommand.class)
				.withLauncher(treeLauncher())
				.renderGroovy();
		assertFalse("root launch must not inject a sources input", script.contains("\"sources\""));
	}

	@Plugin(type = Command.class)
	public static class NoOpCommand implements Command {
		@Override public void run() {}
	}
}
