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

import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandInfo;
import org.scijava.command.CommandService;
import org.scijava.module.Module;
import org.scijava.module.ModuleItem;
import org.scijava.module.ModuleService;
import org.scijava.module.process.PreprocessorPlugin;
import org.scijava.plugin.PluginService;
import org.scijava.ui.swing.widget.SwingInputHarvester;
import org.scijava.ui.swing.widget.SwingInputPanel;
import org.scijava.widget.InputHarvester;
import org.scijava.widget.InputWidget;
import org.scijava.widget.WidgetModel;
import org.scijava.widget.WidgetService;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;

/**
 * Pictures of the dialog of a SciJava command, painted offscreen without
 * running the command: the input panel its harvester would show, in a dialog
 * with OK and Cancel, as Fiji shows it.
 *
 * <p>Unlike an offscreen tool booting an empty context, this runs in a live
 * context with some inputs preset: a dialog whose choices depend on a live
 * object (the regions of an atlas for a registration session, the sources of a
 * viewer) shows its real choices. Persisted values are not restored: the
 * dialog shows the defaults, as for a first use.</p>
 *
 * <p>To lay the dialog out bigger (sharp at 2x), set FlatLaf's
 * {@code flatlaf.uiScale} before the look and feel is installed: painting it
 * bigger would cut the labels, see {@link Painter}.</p>
 */
public final class CommandDialogPainter {

	private CommandDialogPainter() {}

	/**
	 * Paints the dialog of a command.
	 *
	 * @param presets   inputs set before the dialog is built, by name: they are
	 *                  not shown, as when a caller provides them
	 * @param decorated with the title bar of the look and feel (FlatLaf paints
	 *                  its own), else the content only
	 */
	public static BufferedImage paint(Context context, Class<? extends Command> command,
	                                  Map<String, Object> presets, boolean decorated) {
		CommandInfo info = context.service(CommandService.class).getCommand(command);
		if (info == null) throw new IllegalArgumentException("No command " + command.getName());
		Module module = context.service(ModuleService.class).createModule(info);
		presets.forEach((name, value) -> {
			module.setInput(name, value);
			module.resolveInput(name);
		});
		BufferedImage[] img = new BufferedImage[1];
		onEdt(() -> {
			SwingInputHarvester harvester = new SwingInputHarvester();
			context.inject(harvester);
			SwingInputPanel panel = harvester.createInputPanel();
			preprocess(context, module, panel);
			try {
				harvester.buildPanel(panel, module);
			}
			catch (Exception e) {
				throw new RuntimeException("Could not build the dialog of " + info.getTitle(), e);
			}
			img[0] = paintDialog(panel.getComponent(), info.getTitle(), decorated);
		});
		return img[0];
	}

	/**
	 * The module as Fiji has it when the dialog pops up: services injected,
	 * initializers run, persisted values not restored, the harvester not run.
	 */
	private static void preprocess(Context context, Module module, SwingInputPanel panel) {
		for (PreprocessorPlugin p : context.service(PluginService.class).createInstancesOfType(PreprocessorPlugin.class)) {
			if (p instanceof InputHarvester) continue;
			// would restore the values of the last real run
			if (p.getClass().getName().endsWith("LoadInputsPreprocessor")) continue;
			p.process(module);
		}
		// Inputs no widget can show are filled from the context in a real launch:
		// unresolved, they make buildPanel throw "A X is required but none is available"
		WidgetService widgets = context.service(WidgetService.class);
		for (ModuleItem<?> item : module.getInfo().inputs()) {
			if (module.isInputResolved(item.getName())) continue;
			if (hasWidget(widgets, panel, module, item)) continue;
			module.setInput(item.getName(), null);
			module.resolveInput(item.getName());
		}
	}

	private static boolean hasWidget(WidgetService widgets, SwingInputPanel panel, Module module, ModuleItem<?> item) {
		try {
			WidgetModel model = widgets.createModel(panel, module, item, Collections.emptyList());
			InputWidget<?, ?> widget = widgets.create(model);
			return widget != null;
		}
		catch (Throwable t) {
			return false;
		}
	}

	private static BufferedImage paintDialog(JComponent content, String title, boolean decorated) {
		boolean wasDecorated = JDialog.isDefaultLookAndFeelDecorated();
		JDialog.setDefaultLookAndFeelDecorated(decorated);
		try {
			JOptionPane pane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
			JDialog dialog = pane.createDialog(null, title);
			try {
				dialog.setModal(false);
				padLabels(content);
				dialog.pack();
				JComponent target = decorated ? dialog.getRootPane() : (JComponent) dialog.getContentPane();
				// The line of the window border is drawn by the window, not a part of the dialog: keep its room only
				// (as printing does, so that these pictures match the ones painted for printing)
				Border border = target.getBorder();
				if (border != null) target.setBorder(new EmptyBorder(border.getBorderInsets(target)));
				Dimension d = target.getPreferredSize();
				target.setSize(d);
				layoutTree(target);
				return Painter.paint(target, 1);
			}
			finally {
				dialog.dispose();
			}
		}
		finally {
			JDialog.setDefaultLookAndFeelDecorated(wasDecorated);
		}
	}

	/**
	 * MigLayout sizes the label column with a plain font render context, the
	 * text is painted antialiased, a few pixels wider: reserve the slack so that
	 * the longest label keeps its last glyph.
	 */
	private static void padLabels(Component c) {
		if (c instanceof JLabel) {
			JLabel label = (JLabel) c;
			String text = label.getText();
			if (text != null && !text.isEmpty() && !text.startsWith("<html")) {
				Dimension p = label.getPreferredSize();
				label.setPreferredSize(new Dimension(p.width + 8, p.height));
				label.invalidate();
			}
		}
		if (c instanceof Container) {
			Container container = (Container) c;
			for (Component child : container.getComponents()) padLabels(child);
			LayoutManager layout = container.getLayout();
			if (layout instanceof LayoutManager2) ((LayoutManager2) layout).invalidateLayout(container);
			container.invalidate();
		}
	}

	private static void layoutTree(Component c) {
		if (!(c instanceof Container)) return;
		((Container) c).doLayout();
		for (Component child : ((Container) c).getComponents()) layoutTree(child);
	}

	private static void onEdt(Runnable task) {
		if (SwingUtilities.isEventDispatchThread()) {
			task.run();
			return;
		}
		try {
			SwingUtilities.invokeAndWait(task);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
		catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) throw (RuntimeException) cause;
			throw new RuntimeException(cause);
		}
	}
}
