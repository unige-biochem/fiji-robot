package ch.epfl.biop.scijava.ui.robot.widgets;

import ch.epfl.biop.scijava.ui.robot.core.EventRecorder;
import ch.epfl.biop.scijava.ui.robot.core.Step;
import ch.epfl.biop.scijava.ui.robot.core.Timings;
import ch.epfl.biop.scijava.ui.robot.core.Ui;

import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

/**
 * Drives a SciJava command's input-harvester dialog via {@link Ui}, mimicking
 * {@link CommandService#run(Class, boolean, Object...)} ergonomics.
 *
 * Pass alternating {@code "fieldName", value} pairs the same way you would to
 * {@code cs.run(...)}; this class reflects each {@code @Parameter}'s
 * {@link Parameter#label() label} (falling back to the field name) to locate the
 * corresponding widget in the dialog, then visibly fills it.
 *
 * <p>Supported parameter types in this module:</p>
 * <ul>
 *   <li>{@link Boolean} → {@link JCheckBox}.</li>
 *   <li>{@link Number} (any boxed numeric type) → the {@link JFormattedTextField}
 *       editor inside the SciJava {@code JSpinner} (plain / slider / scrollbar).</li>
 *   <li>{@link String} → {@link JTextField}, or — by container shape — a
 *       {@link JComboBox} (choices, default / list-box styles) or a group of
 *       {@link JRadioButton}s (radio styles).</li>
 *   <li>{@link File} → Browse + file-chooser flow; {@code File[]} → the
 *       {@code Add files…} chooser flow.</li>
 * </ul>
 *
 * <p>Widgets that depend on BigDataViewer / bigdataviewer-playground (the source
 * {@code JTree} widgets, the sorted-list drag widget, and the
 * {@code BdvHandle[]}/{@code BvvHandle[]} multi-select {@code JList}) live in the
 * BDV binding module, not here, so this module stays free of those dependencies.
 * The binding plugs them in through the {@link WidgetDriver} extension point
 * (see {@link #registerDriver}); every registered driver is consulted before the
 * built-in ladder below.</p>
 */
public class Harvester {

	/**
	 * Binding-contributed widget drivers, consulted (in registration order)
	 * before the built-in type ladder in {@link #fillWidget}. Copy-on-write so
	 * registration from a binding's static initializer is safe against a
	 * concurrent dialog drive.
	 */
	private static final List<WidgetDriver> DRIVERS = new CopyOnWriteArrayList<>();

	/**
	 * Register a {@link WidgetDriver} so {@link #fillWidget} consults it before
	 * its built-in widget ladder. Idempotent: a driver already registered (by
	 * identity) is not added twice, so a binding can call this from a static
	 * initializer without guarding. Intended for binding modules (e.g. the BDV
	 * source / handle-list widgets); core never registers any driver itself.
	 */
	public static void registerDriver(WidgetDriver driver) {
		if (driver == null) throw new IllegalArgumentException("driver must not be null");
		if (!DRIVERS.contains(driver)) DRIVERS.add(driver);
	}

	/** Remove a previously {@linkplain #registerDriver registered} driver. Returns whether it was present. */
	public static boolean unregisterDriver(WidgetDriver driver) {
		return DRIVERS.remove(driver);
	}

	/** The registered drivers, in registration order (unmodifiable snapshot). */
	public static List<WidgetDriver> registeredDrivers() {
		return Collections.unmodifiableList(new java.util.ArrayList<>(DRIVERS));
	}

	/**
	 * Launches {@code cmdClass} via the {@link CommandService} from
	 * {@code context}, waits for the harvester dialog, fills the named
	 * parameters via Robot, clicks OK, and returns the {@link Future} from the
	 * underlying {@code cs.run} — so you can {@code .get().getOutput("name")} on
	 * the result.
	 *
	 * @param context     the SciJava context whose {@link CommandService} runs the command
	 * @param cmdClass    the command class to run
	 * @param namedInputs alternating field-name / value pairs, e.g.
	 *                    {@code "doIt", true, "name", "hello"}
	 */
	public static <C extends Command> Future<CommandModule> run(Context context, Class<C> cmdClass,
															   Object... namedInputs) {
		// Make the harvester's numeric fields parse the '.' the Robot types,
		// regardless of the JVM's locale (see Ui.useDotDecimalSeparator). Must
		// precede cs.run, which builds the dialog (and its formatters).
		if (Ui.FORCE_DOT_DECIMAL) Ui.useDotDecimalSeparator();
		Map<String, Object> values = parseValues(namedInputs);
		CommandService cs = context.service(CommandService.class);
		Future<CommandModule> future = cs.run(cmdClass, true);
		driveDialog(cmdClass, values, Collections.emptyMap());
		return future;
	}

	public static <C extends Command> void runOpenDialog(Class<C> cmdClass, Object... namedInputs) {
		runOpenDialog(cmdClass, Collections.emptyMap(), namedInputs);
	}

	/**
	 * Same as {@link #runOpenDialog(Class, Object...)} but with a per-parameter
	 * narration map: just before each widget is driven, if {@code narrations}
	 * contains an entry keyed by that parameter's field name, {@link Step#say}
	 * fires with the narration text.
	 *
	 * <p>Drives an <em>already-open</em> dialog — the caller is responsible for
	 * having launched the command. Use {@link #run(Context, Class, Object...)}
	 * to launch and drive in one call.</p>
	 */
	public static <C extends Command> void runOpenDialog(Class<C> cmdClass,
														 Map<String, String> narrations,
														 Object... namedInputs) {
		Map<String, Object> values = parseValues(namedInputs);
		driveDialog(cmdClass, values, narrations);
	}

	private static Map<String, Object> parseValues(Object[] namedInputs) {
		if (namedInputs.length % 2 != 0) {
			throw new IllegalArgumentException("namedInputs must be alternating name/value pairs");
		}
		Map<String, Object> values = new LinkedHashMap<>();
		for (int i = 0; i < namedInputs.length; i += 2) {
			values.put((String) namedInputs[i], namedInputs[i + 1]);
		}
		return values;
	}

	/**
	 * True when a parameter value should be skipped entirely in Robot mode — the
	 * widget is left at its harvester default. Covers {@code null} and any empty
	 * array <em>except</em> {@code File[]} (an empty {@code File[]} is a
	 * deliberate "click Clear list" gesture).
	 */
	private static boolean isRobotNoOpValue(Object value) {
		if (value == null) return true;
		if (value instanceof File[]) return false;
		if (value.getClass().isArray()) {
			return java.lang.reflect.Array.getLength(value) == 0;
		}
		return false;
	}

	/** Wait for the active dialog, fill each named parameter, click OK. */
	private static <C extends Command> void driveDialog(Class<C> cmdClass, Map<String, Object> values,
														Map<String, String> narrations) {
		Map<String, String> fieldToLabel = labelsFor(cmdClass);

		Dialog dialog = Ui.waitForActiveDialog(Timings.FRAME_WAIT_FOR_DIALOG_MS);
		if (dialog == null) {
			// No other parameters - we're done
			return;
		}

		Ui.dragFrame(dialog, dialog.getX(), 40);
		Ui.rawPause(1000);

		// Let the dialog finish realizing before we touch screen positions.
		Ui.pause(Timings.PAUSE_AFTER_FRAME_PLACEMENT_MS);

		for (Map.Entry<String, Object> e : values.entrySet()) {
			String fieldName = e.getKey();
			Object value = e.getValue();
			// Robot-mode skip: callers often pass null / empty arrays in
			// programmatic mode purely to satisfy "all inputs supplied". Driving
			// the dialog for such values would just leave the widget at its
			// default — so skip. File[] is the one exception (empty = Clear).
			if (isRobotNoOpValue(value)) {
				System.out.println("[Harvester] " + fieldName + " skipped (null or empty array)");
				continue;
			}
			String label = fieldToLabel.get(fieldName);
			if (label == null) {
				throw new IllegalArgumentException("No @Parameter found for field '"
						+ fieldName + "' on " + cmdClass.getSimpleName());
			}
			Container inputContainer = findInputContainerForLabel(dialog, label);
			if (inputContainer == null) {
				throw new IllegalStateException("No input container found for parameter '"
						+ fieldName + "' (label: '" + label + "')");
			}
			System.out.println("[Harvester] " + fieldName + " (" + label + ") <- " + value);
			// No-op when the harvester panel isn't wrapped in a JScrollPane.
			// When it is, brings the widget to the top of the viewport — must
			// happen before the hover below so the label isn't off-screen.
			Ui.scrollIntoView(inputContainer);
			// Per-parameter narration. When provided, hover the cursor onto the
			// field's JLabel first so the subtitle plays while the cursor is
			// already pointing at the right row.
			String narration = narrations.get(fieldName);
			if (narration != null) {
				JLabel jl = findLabel(dialog, label);
				Component hoverTarget = (jl != null && jl.isShowing()) ? jl : inputContainer;
				if (hoverTarget.isShowing()) {
					Point hp = hoverTarget.getLocationOnScreen();
					Ui.moveTo(hp.x + hoverTarget.getWidth() / 2,
							  hp.y + hoverTarget.getHeight() / 2, 100, 30);

				}
				Ui.rawPause(1500);
				Step.say(narration);
				Ui.rawPause(500);
			}
			fillWidget(inputContainer, value);
			// Breathing room before the next field's narration + cursor travel.
			Ui.pause(Timings.PAUSE_AFTER_FIELD_MS);
		}
		Ui.pause(1000);
		clickOk(dialog);
	}

	// ===== Reflection: field name -> dialog label ================================

	private static Map<String, String> labelsFor(Class<?> cls) {
		Map<String, String> map = new HashMap<>();
		for (Field f : cls.getDeclaredFields()) {
			Parameter p = f.getAnnotation(Parameter.class);
			if (p == null) continue;
			String label = p.label();
			if (label == null || label.isEmpty()) label = f.getName();
			map.put(f.getName(), label);
		}
		return map;
	}

	// ===== Widget location =======================================================

	/**
	 * Locates the input <em>container</em> bound to a SciJava parameter label
	 * inside the harvester dialog. SwingInputHarvester emits a {@link JLabel}
	 * with the parameter's label text, followed by a sibling {@link Container}
	 * that holds the actual widget(s); we return that sibling container so the
	 * dispatcher can pick the right component based on the value's Java type.
	 */
	static Container findInputContainerForLabel(Dialog dialog, String label) {
		JLabel jl = findLabel(dialog, label);
		if (jl == null) return null;
		Container parent = jl.getParent();
		if (parent == null) return null;
		Component[] siblings = parent.getComponents();
		int idx = -1;
		for (int i = 0; i < siblings.length; i++) {
			if (siblings[i] == jl) { idx = i; break; }
		}
		if (idx < 0) return null;
		for (int i = idx + 1; i < siblings.length; i++) {
			Component c = siblings[i];
			if (c instanceof JLabel) break; // hit the next parameter — stop searching
			if (c instanceof Container) return (Container) c;
		}
		return null;
	}

	/**
	 * Case-insensitive label match: SciJava's harvester sometimes
	 * auto-capitalises the first letter of a parameter label, which makes a
	 * strict {@code equals} miss the row. Labels are unique within a single
	 * harvester panel, so case-insensitive is safe.
	 */
	private static JLabel findLabel(Container root, String text) {
		for (Component c : root.getComponents()) {
			if (c instanceof JLabel) {
				JLabel l = (JLabel) c;
				if (text.equalsIgnoreCase(l.getText())) return l;
			}
			if (c instanceof Container) {
				JLabel found = findLabel((Container) c, text);
				if (found != null) return found;
			}
		}
		return null;
	}

	/** Recursively collect all components of {@code type} under {@code root}. */
	private static <T extends Component> List<T> findAll(Container root, Class<T> type) {
		return Widgets.findAll(root, type);
	}

	// ===== Widget driving (Robot) ================================================

	/**
	 * Pick a driver based on the value's Java type. The same input container may
	 * hold several widgets (e.g. {@code File} → JTextField + Browse button); the
	 * value type tells us which one the caller wants to drive.
	 */
	static void fillWidget(Container inputContainer, Object value) {
		// Binding-contributed widgets first: a driver that recognizes this
		// container shape + value type (e.g. a BDV source JTree) wins over the
		// built-in ladder. Drivers are shape-specific, so this never shadows a
		// pure-SciJava widget — a checkbox row matches no driver and falls through.
		for (WidgetDriver d : DRIVERS) {
			if (d.matches(inputContainer, value)) {
				d.fill(inputContainer, value);
				return;
			}
		}
		if (value instanceof Boolean) {
			JCheckBox cb = firstOf(inputContainer, JCheckBox.class, "JCheckBox");
			fillCheckBox(cb, (Boolean) value);
		} else if (value instanceof Number) {
			// SciJava renders every numeric @Parameter as a JSpinner whose
			// editor is a JFormattedTextField — true for the plain spinner and
			// for the slider / scrollbar styles too. Drive the formatted text
			// field; the slider / scrollbar updates itself.
			JFormattedTextField field = firstOf(inputContainer,
					JFormattedTextField.class, "JFormattedTextField (numeric spinner editor)");
			fillNumberField(field, (Number) value);
		} else if (value instanceof File[]) {
			AbstractButton addFiles = findAddFilesButton(inputContainer);
			if (addFiles == null) {
				throw new IllegalStateException("No 'Add files…' button found for File[] parameter");
			}
			// Clear any pre-existing entries first so the resulting list contains
			// exactly the files we asked for. Skipped if there's no Clear button.
			AbstractButton clear = findClearListButton(inputContainer);
			if (clear != null) clickClearList(clear);
			fillFileList(addFiles, (File[]) value);
		} else if (value instanceof File) {
			AbstractButton browse = findBrowseButton(inputContainer);
			if (browse == null) {
				throw new IllegalStateException("No Browse button found for File parameter");
			}
			fillFile(browse, (File) value);
		} else {
			// Dispatch by container shape. A SciJava input panel for a String
			// @Parameter contains exactly one of: a JComboBox (choices, default
			// / list-box style), a group of JRadioButtons (radio styles), or a
			// plain JTextField.
			List<JComboBox> combos = findAll(inputContainer, JComboBox.class);
			if (!combos.isEmpty()) {
				fillComboBox(combos.get(0), String.valueOf(value));
				return;
			}
			List<JRadioButton> radios = findAll(inputContainer, JRadioButton.class);
			if (!radios.isEmpty()) {
				fillRadioGroup(radios, String.valueOf(value));
				return;
			}
			JTextField tf = firstOf(inputContainer, JTextField.class, "JTextField");
			fillTextField(tf, String.valueOf(value));
		}
	}

	private static <T extends Component> T firstOf(Container c, Class<T> type, String human) {
		List<T> all = findAll(c, type);
		if (all.isEmpty()) {
			throw new IllegalStateException("No " + human + " found in input container");
		}
		return all.get(0);
	}

	/**
	 * Find the file-chooser-trigger button. Matches any {@link AbstractButton}
	 * whose text contains "browse" (case-insensitive); falls back to the first
	 * button found if no explicit match.
	 */
	private static AbstractButton findBrowseButton(Container root) {
		List<AbstractButton> buttons = findAll(root, AbstractButton.class);
		for (AbstractButton b : buttons) {
			String t = b.getText();
			if (t != null && t.toLowerCase().contains("browse")) return b;
		}
		return buttons.isEmpty() ? null : buttons.get(0);
	}

	/**
	 * Find the "Add files…" button in a SciJava {@code MutableFileListWidget}.
	 * Matches "add file" (case-insensitive) — distinct from "Add folder
	 * content…". Returns {@code null} if no button matches.
	 */
	private static AbstractButton findAddFilesButton(Container root) {
		List<AbstractButton> buttons = findAll(root, AbstractButton.class);
		for (AbstractButton b : buttons) {
			String t = b.getText();
			if (t != null && t.toLowerCase().contains("add file")) return b;
		}
		return null;
	}

	/**
	 * Find the "Clear list" button in a SciJava {@code MutableFileListWidget}.
	 * Returns {@code null} if no matching button exists.
	 */
	private static AbstractButton findClearListButton(Container root) {
		List<AbstractButton> buttons = findAll(root, AbstractButton.class);
		for (AbstractButton b : buttons) {
			String t = b.getText();
			if (t != null && t.toLowerCase().contains("clear")) return b;
		}
		return null;
	}

	private static void fillCheckBox(JCheckBox cb, boolean target) {
		if (cb.isSelected() == target) return; // already in the desired state
		// The JCheckBox component spans the whole row but the visible square
		// sits on the left — click there so the gesture reads correctly.
		if (!cb.isShowing()) {
			throw new IllegalStateException("JCheckBox is not on screen");
		}
		Point loc = cb.getLocationOnScreen();
		int x = loc.x + CHECKBOX_CLICK_OFFSET_X;
		int y = loc.y + cb.getHeight() / 2;
		Ui.moveTo(x, y);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
	}

	/** Pixels from the JCheckBox's left edge to the centre of the box icon (FlatLaf default ≈ 9). */
	public static int CHECKBOX_CLICK_OFFSET_X = 9;

	/** Pixels from the JRadioButton's left edge to the centre of the dot icon (FlatLaf default ≈ 9). */
	public static int RADIO_CLICK_OFFSET_X = 9;

	/**
	 * Drive a SciJava {@link JComboBox} choice widget: click the combo to open
	 * its popup, locate the popup's backing {@link JList} via the combo's UI
	 * delegate, find the row whose {@code String.valueOf(model.getElementAt(row))}
	 * equals {@code value}, and click it. Always shows the visible gesture (open
	 * popup → click item) — no idempotent short-circuit.
	 */
	private static void fillComboBox(JComboBox<?> combo, String value) {
		Point center = centerOnScreen(combo);
		Ui.moveTo(center);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);

		Object accessible = combo.getUI().getAccessibleChild(combo, 0);
		if (!(accessible instanceof BasicComboPopup)) {
			throw new IllegalStateException("Expected BasicComboPopup from combo UI, got: "
					+ (accessible == null ? "null" : accessible.getClass().getName()));
		}
		JList<?> list = ((BasicComboPopup) accessible).getList();

		long deadline = System.currentTimeMillis() + Timings.FRAME_WAIT_TIMEOUT_MS;
		while (!list.isShowing() && System.currentTimeMillis() < deadline) {
			Ui.rawPause(Timings.FRAME_POLL_INTERVAL_MS);
		}
		if (!list.isShowing()) {
			throw new IllegalStateException("Combo popup did not open in time");
		}

		ListModel<?> model = list.getModel();
		int found = -1;
		for (int i = 0; i < model.getSize(); i++) {
			if (value.equals(String.valueOf(model.getElementAt(i)))) { found = i; break; }
		}
		if (found < 0) {
			StringBuilder available = new StringBuilder();
			for (int i = 0; i < model.getSize(); i++) {
				if (i > 0) available.append(", ");
				available.append('"').append(model.getElementAt(i)).append('"');
			}
			throw new IllegalArgumentException("No item '" + value + "' in combo. Available: ["
					+ available + "]");
		}

		Rectangle bounds = list.getCellBounds(found, found);
		Point listLoc = list.getLocationOnScreen();
		int x = listLoc.x + bounds.x + bounds.width / 2;
		int y = listLoc.y + bounds.y + bounds.height / 2;
		Ui.moveTo(x, y);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
	}

	/**
	 * Drive a group of {@link JRadioButton}s rendered for a SciJava
	 * {@code radioButtonHorizontal} / {@code radioButtonVertical} choice style.
	 * Finds the radio whose {@code getText()} equals {@code value} and clicks it.
	 * Idempotent: skips the click if that radio is already selected.
	 */
	private static void fillRadioGroup(List<JRadioButton> radios, String value) {
		JRadioButton target = null;
		for (JRadioButton rb : radios) {
			if (value.equals(rb.getText())) { target = rb; break; }
		}
		if (target == null) {
			StringBuilder available = new StringBuilder();
			for (int i = 0; i < radios.size(); i++) {
				if (i > 0) available.append(", ");
				available.append('"').append(radios.get(i).getText()).append('"');
			}
			throw new IllegalArgumentException("No radio button '" + value + "'. Available: ["
					+ available + "]");
		}
		if (target.isSelected()) return;
		if (!target.isShowing()) {
			throw new IllegalStateException("JRadioButton '" + value + "' is not on screen");
		}
		Point loc = target.getLocationOnScreen();
		int x = loc.x + RADIO_CLICK_OFFSET_X;
		int y = loc.y + target.getHeight() / 2;
		Ui.moveTo(x, y);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
	}

	/**
	 * Drive a SciJava numeric-spinner's {@link JFormattedTextField}: click,
	 * select all, paste {@code String.valueOf(value)}, then Enter.
	 *
	 * <p>Paste (not type) because numeric strings include {@code "."}, which is
	 * keyboard-layout-dependent through {@link java.awt.Robot#keyPress}. Enter at
	 * the end is load-bearing — a {@code JFormattedTextField} only commits on
	 * Enter or focus loss.</p>
	 */
	static void fillNumberField(JFormattedTextField field, Number value) {
		Point center = centerOnScreen(field);
		Ui.moveTo(center);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
		selectAllAndDelete();
		Ui.paste(String.valueOf(value));
		Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
		Ui.enter();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
	}

	/**
	 * Visible sibling of {@link #fillNumberField}: types the value
	 * character-by-character via {@link Ui#type} instead of pasting it in one
	 * shot — used where the digits appearing one at a time read better on a
	 * recorded tutorial. Same Enter-to-commit and locale caveats apply.
	 */
	public static void typeNumberField(JFormattedTextField field, Number value) {
		Point center = centerOnScreen(field);
		Ui.moveTo(center);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
		selectAllAndDelete();
		Ui.type(String.valueOf(value));
		Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
		Ui.enter();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
	}

	private static void fillTextField(JTextField tf, String text) {
		Point center = centerOnScreen(tf);
		Ui.moveTo(center);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
		selectAllAndDelete();
		// Paste rather than type — Robot.keyPress is keyboard-layout-dependent
		// and rejects characters like ':' or '\\' on non-US layouts.
		Ui.paste(text);
		Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
	}

	/** Ctrl+A then Delete — clears whatever the field currently contains. */
	private static void selectAllAndDelete() {
		java.awt.Robot r = Ui.robot();
		EventRecorder.suppress(true);
		try {
			r.keyPress(KeyEvent.VK_CONTROL);
			r.keyPress(KeyEvent.VK_A);
			r.delay(Timings.KEY_HOLD_MS);
			r.keyRelease(KeyEvent.VK_A);
			r.keyRelease(KeyEvent.VK_CONTROL);
			r.delay(Timings.KEY_HOLD_MS);
			r.keyPress(KeyEvent.VK_DELETE);
			r.delay(Timings.KEY_HOLD_MS);
			r.keyRelease(KeyEvent.VK_DELETE);
			r.waitForIdle();
		} finally {
			EventRecorder.suppress(false);
		}
	}

	/**
	 * Click Browse, then drive the resulting file chooser by pasting the folder
	 * path + Enter (navigates into the folder), then typing the file name + Enter
	 * (selects the file and closes the chooser). The mouse is not moved inside
	 * the chooser — focus is on the file-name field by default.
	 */
	private static void fillFile(AbstractButton browse, File file) {
		File abs = file.getAbsoluteFile();
		File dir = abs.getParentFile();
		String dirPath = dir == null ? "" : dir.getAbsolutePath();
		String name = abs.getName();

		Point center = centerOnScreen(browse);
		Ui.moveTo(center);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_FILE_CHOOSER_OPEN_MS);

		if (!dirPath.isEmpty()) {
			Ui.paste(dirPath);
			Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
			Ui.enter();
			Ui.pause(Timings.PAUSE_AFTER_FOLDER_NAV_MS);
			// Enter navigates into the folder but leaves the path text in the
			// file-name field — wipe it before typing the file name.
			selectAllAndDelete();
		}
		// Filename is short and ASCII — type it for the visible cadence.
		Ui.type(name);
		Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
		Ui.enter();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
	}

	/**
	 * Click "Clear list" once. Safe to call on an already-empty list — Swing
	 * routes the click to the disabled-button no-op and Robot still moves the
	 * cursor visibly, which reads as a deliberate reset gesture.
	 */
	private static void clickClearList(AbstractButton clear) {
		Point center = centerOnScreen(clear);
		Ui.moveTo(center);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		Ui.click();
		Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
	}

	/**
	 * For each file: click "Add files…", drive the resulting chooser the same
	 * way as a single-file pick. One chooser cycle per file — reads naturally on
	 * video as the list grows entry by entry.
	 */
	private static void fillFileList(AbstractButton addFiles, File[] files) {
		for (File file : files) {
			File abs = file.getAbsoluteFile();
			File dir = abs.getParentFile();
			String dirPath = dir == null ? "" : dir.getAbsolutePath();
			String name = abs.getName();

			Point center = centerOnScreen(addFiles);
			Ui.moveTo(center);
			Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
			Ui.click();
			Ui.pause(Timings.PAUSE_AFTER_FILE_CHOOSER_OPEN_MS);

			if (!dirPath.isEmpty()) {
				Ui.paste(dirPath);
				Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
				Ui.enter();
				Ui.pause(Timings.PAUSE_AFTER_FOLDER_NAV_MS);
				selectAllAndDelete();
			}
			Ui.type(name);
			Ui.pause(Timings.PAUSE_AFTER_TYPING_MS);
			Ui.enter();
			Ui.pause(Timings.PAUSE_AFTER_CLICK_MS);
		}
	}

	// ===== OK button =============================================================

	private static void clickOk(Dialog dialog) {
		AbstractButton ok = findButton(dialog, "OK");
		if (ok == null) throw new IllegalStateException("No OK button found in dialog");
		Point center = centerOnScreen(ok);
		Ui.moveTo(center);
		Ui.pause(Timings.PAUSE_AFTER_MOVE_MS);
		// Tutorial PNG: dialog fully filled, cursor on OK, just before the click.
		Step.snapMoment("dialog");
		Ui.click();
		// Beat to let the dialog dismiss and the command's immediate UI side
		// effects materialize before control returns to the caller.
		Ui.pause(Timings.PAUSE_AFTER_DIALOG_OK_MS);
	}

	private static AbstractButton findButton(Container root, String text) {
		for (Component c : root.getComponents()) {
			if (c instanceof AbstractButton) {
				AbstractButton b = (AbstractButton) c;
				if (text.equals(b.getText())) return b;
			}
			if (c instanceof Container) {
				AbstractButton found = findButton((Container) c, text);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static Point centerOnScreen(Component c) {
		if (!c.isShowing()) {
			throw new IllegalStateException(c.getClass().getSimpleName() + " is not on screen");
		}
		Point loc = c.getLocationOnScreen();
		return new Point(loc.x + c.getWidth() / 2, loc.y + c.getHeight() / 2);
	}
}
