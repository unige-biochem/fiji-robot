package ch.unige.biochem.fiji.robot.widgets;

import ch.unige.biochem.fiji.robot.core.Ui;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import java.awt.Dialog;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

/**
 * End-to-end tests for every widget {@link Harvester} can drive in this module.
 * Boots a SciJava context with the Swing UI shown once in {@link #setUpClass()};
 * each {@code @Test} drives one harvester dialog via {@code java.awt.Robot} and
 * asserts the echoed output.
 *
 * <p><b>GUI tests — run locally, not in headless CI.</b> They synthesize real
 * mouse / keyboard events and need a visible display; on a headless box the
 * Robot can't operate and the Swing harvester won't appear. Each widget has a
 * companion {@code public static} nested {@code Demo} command so fixture and
 * driver stay co-located.</p>
 *
 * <p>Ported from the original tutorial-video toolkit's {@code WidgetsTest},
 * minus the BigDataViewer / bigdataviewer-playground cases (source-tree widgets,
 * {@code BdvHandle[]} / {@code BvvHandle[]} multi-select) — those belong to the
 * BDV binding module.</p>
 */
public class HarvesterWidgetsTest {

	private static Context context;

	@BeforeClass
	public static void setUpClass() {
		context = new Context();
		// Show the Swing UI so the SciJava input harvester renders dialogs the
		// Robot can drive. Local display required — see class javadoc.
		context.service(UIService.class).showUI();
	}

	@AfterClass
	public static void tearDownClass() {
		if (context != null) context.dispose();
	}

	/**
	 * Belt-and-braces cleanup so a failed test mid-dialog doesn't leave a
	 * harvester open and pollute the next {@code @Test}.
	 */
	@After
	public void closeLeftoverDialogs() {
		for (Window w : Window.getWindows()) {
			if (w instanceof Dialog && w.isShowing()) {
				w.dispose();
			}
		}
	}

	// ─── String → JTextField ────────────────────────────────────────────────

	@Test
	public void testString() throws Exception {
		String name = "hello robot";
		Future<CommandModule> future = Harvester.run(context, StringDemo.class, "name", name);
		assertEquals("name=" + name, future.get().getOutput("result"));
	}

	@Plugin(type = Command.class)
	public static class StringDemo implements Command {
		@Parameter(label = "Name") String name = "";
		@Parameter(type = ItemIO.OUTPUT) String result;
		@Override public void run() { result = "name=" + name; }
	}

	// ─── Boolean → JCheckBox ────────────────────────────────────────────────

	@Test
	public void testBoolean() throws Exception {
		Future<CommandModule> future = Harvester.run(context, BooleanDemo.class, "doIt", true);
		assertEquals("doIt=true", future.get().getOutput("result"));
	}

	@Plugin(type = Command.class)
	public static class BooleanDemo implements Command {
		@Parameter(label = "Do it?") boolean doIt = false;
		@Parameter(type = ItemIO.OUTPUT) String result;
		@Override public void run() { result = "doIt=" + doIt; }
	}

	// ─── File → Browse + chooser ────────────────────────────────────────────

	@Test
	public void testFile() throws Exception {
		File tmp = makeTmp("widgets-file-", ".txt", "hi");
		Future<CommandModule> future = Harvester.run(context, FileDemo.class, "file", tmp);
		assertEquals(tmp, future.get().getInput("file"));
	}

	@Plugin(type = Command.class)
	public static class FileDemo implements Command {
		@Parameter boolean extra;
		@Parameter(label = "A file") File file;
		@Override public void run() {}
	}

	// ─── File[] → Add files chooser flow ────────────────────────────────────

	@Test
	public void testFileList() throws Exception {
		File[] tmps = new File[] {
				makeTmp("widgets-list-a-", ".txt", "a"),
				makeTmp("widgets-list-b-", ".txt", "b"),
				makeTmp("widgets-list-c-", ".txt", "c"),
		};
		Future<CommandModule> future = Harvester.run(context, FileListDemo.class, "files", tmps);
		File[] out = (File[]) future.get().getInput("files");
		assertEquals(tmps.length, out.length);
	}

	@Plugin(type = Command.class)
	public static class FileListDemo implements Command {
		@Parameter boolean extra;
		@Parameter(label = "Many files") File[] files;
		@Override public void run() {}
	}

	// ─── Number → JSpinner / slider / scrollbar (all share the spinner editor) ─

	@Test
	public void testNumber() throws Exception {
		int iterations = 25;
		int blockSize = 128;
		int threshold = 73;
		int threads = 8;
		float regularization = 0.005f;
		double sigma = 2.5;

		Future<CommandModule> future = Harvester.run(context, NumberDemo.class,
				"iterations", iterations,
				"blockSize", blockSize,
				"threshold", threshold,
				"threads", threads,
				"regularization", regularization,
				"sigma", sigma);

		String expected = "iterations=" + iterations
				+ ", blockSize=" + blockSize
				+ ", threshold=" + threshold
				+ ", threads=" + threads
				+ ", regularization=" + regularization
				+ ", sigma=" + sigma;
		assertEquals(expected, future.get().getOutput("result"));
	}

	@Plugin(type = Command.class)
	public static class NumberDemo implements Command {
		@Parameter(label = "Iterations (unbounded int)") int iterations = 10;
		@Parameter(label = "Block size (bounded int)", min = "1", max = "1024") int blockSize = 256;
		@Parameter(label = "Threshold (slider)", min = "0", max = "100",
				style = NumberWidget.SLIDER_STYLE) int threshold = 50;
		@Parameter(label = "Threads (scroll bar)", min = "1", max = "32",
				style = NumberWidget.SCROLL_BAR_STYLE) int threads = 4;
		@Parameter(label = "Regularization (float)") float regularization = 0.002f;
		@Parameter(label = "Sigma (bounded double)", min = "0.0", max = "10.0") double sigma = 1.5;
		@Parameter(type = ItemIO.OUTPUT) String result;
		@Override public void run() {
			result = "iterations=" + iterations
					+ ", blockSize=" + blockSize
					+ ", threshold=" + threshold
					+ ", threads=" + threads
					+ ", regularization=" + regularization
					+ ", sigma=" + sigma;
		}
	}

	// ─── Choice → JComboBox / JRadioButton group ───────────────────────────

	@Test
	public void testChoice() throws Exception {
		String pixelType = "Float";
		String mode = "Accurate";
		String direction = "Down";
		String quality = "High";

		Future<CommandModule> future = Harvester.run(context, ChoiceDemo.class,
				"pixelType", pixelType,
				"mode", mode,
				"direction", direction,
				"quality", quality);

		String expected = "pixelType=" + pixelType
				+ ", mode=" + mode
				+ ", direction=" + direction
				+ ", quality=" + quality;
		assertEquals(expected, future.get().getOutput("result"));
	}

	@Plugin(type = Command.class)
	public static class ChoiceDemo implements Command {
		@Parameter(label = "Pixel type (default = combo)",
				choices = {"UInt8", "UInt16", "Float", "Double"})
		String pixelType = "UInt16";
		@Parameter(label = "Mode (list box)",
				choices = {"Fast", "Balanced", "Accurate"},
				style = ChoiceWidget.LIST_BOX_STYLE)
		String mode = "Balanced";
		@Parameter(label = "Direction (radio vertical)",
				choices = {"Up", "Down", "Left", "Right"},
				style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE)
		String direction = "Up";
		@Parameter(label = "Quality (radio horizontal)",
				choices = {"Low", "Medium", "High"},
				style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
		String quality = "Medium";
		@Parameter(type = ItemIO.OUTPUT) String result;
		@Override public void run() {
			result = "pixelType=" + pixelType
					+ ", mode=" + mode
					+ ", direction=" + direction
					+ ", quality=" + quality;
		}
	}

	// ─── Long form → harvester wrapped in JScrollPane, scroll required ─────

	/**
	 * Smoke-tests {@link Ui#scrollIntoView}:
	 * a command with enough boolean parameters that {@code SwingInputHarvester}
	 * wraps its panel in a {@link javax.swing.JScrollPane}. Setting widgets at
	 * the top, middle and bottom exercises three regimes — already visible,
	 * needs a partial scroll, needs a further scroll. On an unusually tall
	 * display the dialog may fit without scrolling — the test still passes but
	 * does not exercise the scroll path.
	 */
	@Test
	public void testScrollIntoView() throws Exception {
		Future<CommandModule> future = Harvester.run(context, LongFormDemo.class,
				"b01", true,
				"b15", true,
				"b30", true);
		assertEquals("b01=true b15=true b30=true", future.get().getOutput("result"));
	}

	@Plugin(type = Command.class)
	public static class LongFormDemo implements Command {
		@Parameter(label = "Boolean 01", persist = false) boolean b01;
		@Parameter(label = "Boolean 02") boolean b02;
		@Parameter(label = "Boolean 03") boolean b03;
		@Parameter(label = "Boolean 04") boolean b04;
		@Parameter(label = "Boolean 05") boolean b05;
		@Parameter(label = "Boolean 06") boolean b06;
		@Parameter(label = "Boolean 07") boolean b07;
		@Parameter(label = "Boolean 08") boolean b08;
		@Parameter(label = "Boolean 09") boolean b09;
		@Parameter(label = "Boolean 10") boolean b10;
		@Parameter(label = "Boolean 11") boolean b11;
		@Parameter(label = "Boolean 12") boolean b12;
		@Parameter(label = "Boolean 13") boolean b13;
		@Parameter(label = "Boolean 14") boolean b14;
		@Parameter(label = "Boolean 15", persist = false) boolean b15;
		@Parameter(label = "Boolean 16") boolean b16;
		@Parameter(label = "Boolean 17") boolean b17;
		@Parameter(label = "Boolean 18") boolean b18;
		@Parameter(label = "Boolean 19") boolean b19;
		@Parameter(label = "Boolean 20") boolean b20;
		@Parameter(label = "Boolean 21") boolean b21;
		@Parameter(label = "Boolean 22") boolean b22;
		@Parameter(label = "Boolean 23") boolean b23;
		@Parameter(label = "Boolean 24") boolean b24;
		@Parameter(label = "Boolean 25") boolean b25;
		@Parameter(label = "Boolean 26") boolean b26;
		@Parameter(label = "Boolean 27") boolean b27;
		@Parameter(label = "Boolean 28") boolean b28;
		@Parameter(label = "Boolean 29") boolean b29;
		@Parameter(label = "Boolean 30", persist = false) boolean b30;
		@Parameter(type = ItemIO.OUTPUT) String result;
		@Override public void run() {
			result = "b01=" + b01 + " b15=" + b15 + " b30=" + b30;
		}
	}

	// ─── helpers ────────────────────────────────────────────────────────────

	private static File makeTmp(String prefix, String suffix, String content) throws Exception {
		File f = File.createTempFile(prefix, suffix);
		f.deleteOnExit();
		Files.write(f.toPath(), content.getBytes());
		return f;
	}
}
