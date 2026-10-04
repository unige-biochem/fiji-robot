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
package ch.unige.biochem.fiji.robot.widgets;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.awt.image.BufferedImage;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

/**
 * GUI test (a dialog needs a display, even unshown): {@link CommandDialogPainter}
 * paints the dialog of a command without running it, a preset input left out.
 */
public class CommandDialogPainterTest {

	@Plugin(type = Command.class)
	public static class TwoInputs implements Command {
		@Parameter(label = "Shown text")
		String text = "default";

		@Parameter(label = "Hidden preset")
		String preset;

		@Parameter(type = ItemIO.OUTPUT)
		String result;

		@Override
		public void run() {
			result = text + preset;
		}
	}

	private static Context context;

	@BeforeClass
	public static void boot() {
		context = new Context();
	}

	@AfterClass
	public static void dispose() {
		context.dispose();
	}

	@Test
	public void paintsTheDialogWithoutThePresetInput() {
		BufferedImage withPreset = CommandDialogPainter.paint(context, TwoInputs.class,
				Collections.singletonMap("preset", "given"), false);
		BufferedImage withoutPreset = CommandDialogPainter.paint(context, TwoInputs.class,
				Collections.emptyMap(), false);
		assertTrue("a dialog with OK and Cancel", withPreset.getWidth() > 100 && withPreset.getHeight() > 50);
		assertTrue("the preset input has no row", withPreset.getHeight() < withoutPreset.getHeight());
	}
}
