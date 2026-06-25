package ch.unige.biochem.fiji.robot;

/**
 * Factory methods for the built-in {@link InputResolution} kinds.
 *
 * <p>Static-import this class at the call site so a command run reads as a
 * small declarative recipe:</p>
 *
 * <pre>
 * import static ch.epfl.biop.scijava.ui.robot.Resolutions.*;
 *
 * CmdExecutor.of(context, MyCommand.class)
 *     .preSet("a-param", programmatic(value))
 *     .withLauncher(programmaticLauncher())
 *     .postSet("min", fromDialog(0.0, "We set the lower display bound."))
 *     .launch();
 * </pre>
 *
 * <p>This first increment ships the two context-free kinds — {@link
 * #programmatic} (a pre-set value) and {@link #fromDialog} (a value harvested
 * from the dialog). Context-bound kinds that resolve a live object by name at
 * launch time ({@code selectActiveImagePlus}, {@code selectActiveBdv}) arrive
 * with the visible-gesture layer, in their respective binding modules.</p>
 */
public final class Resolutions {

	private Resolutions() {}

	// ===== Pre-set ============================================================

	/** A value pre-set on the module before launch, with no narration. */
	public static PreSetResolution programmatic(Object value) {
		return new SimplePreSet(value, null);
	}

	/** A value pre-set on the module before launch, with a narration subtitle. */
	public static PreSetResolution programmatic(Object value, String narration) {
		return new SimplePreSet(value, narration);
	}

	// ===== Dialog =============================================================

	/** A value harvested from the command's dialog, with no narration. */
	public static DialogResolution fromDialog(Object value) {
		return new SimpleDialog(value, null);
	}

	/** A value harvested from the command's dialog, with a narration subtitle. */
	public static DialogResolution fromDialog(Object value, String narration) {
		return new SimpleDialog(value, narration);
	}

	// ===== Implementations ====================================================

	private static final class SimplePreSet implements PreSetResolution {
		private final Object value;
		private final String narration;

		SimplePreSet(Object value, String narration) {
			this.value = value;
			this.narration = narration;
		}

		@Override public Object value() { return value; }
		@Override public String narration() { return narration; }
	}

	private static final class SimpleDialog implements DialogResolution {
		private final Object value;
		private final String narration;

		SimpleDialog(Object value, String narration) {
			this.value = value;
			this.narration = narration;
		}

		@Override public Object value() { return value; }
		@Override public String narration() { return narration; }
	}
}
