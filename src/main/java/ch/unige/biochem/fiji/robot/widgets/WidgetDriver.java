package ch.unige.biochem.fiji.robot.widgets;

import java.awt.Container;

/**
 * Extension point for driving harvester widgets that {@link Harvester}'s
 * built-in type ladder doesn't know about.
 *
 * <p>The built-in ladder covers the pure-SciJava widgets — checkbox, numeric
 * spinner, text field, combo / radio choice, {@code File} / {@code File[]}.
 * Widgets contributed by a <em>binding</em> (e.g. the bigdataviewer-playground
 * source {@code JTree} widgets, or the {@code BdvHandle[]} multi-select list)
 * live in their own module so core stays free of those dependencies. A binding
 * registers one {@code WidgetDriver} per such widget via
 * {@link Harvester#registerDriver(WidgetDriver)}; the harvester consults every
 * registered driver <em>before</em> its built-in ladder, so a driver that
 * {@link #matches matches} wins.</p>
 *
 * <p>A driver is matched by the <em>shape</em> of the input container (which
 * Swing widgets it holds) and the Java type of the supplied value — never by
 * any binding type, so registration is harmless for unrelated dialogs (a driver
 * that needs a {@code JTree} simply never matches a checkbox row).</p>
 *
 * <p>Implementations should be stateless and safe to register once for the
 * lifetime of the JVM; the harvester may consult them on any dialog.</p>
 */
public interface WidgetDriver {

	/**
	 * Whether this driver recognizes the widget in {@code inputContainer} for a
	 * value of {@code value}'s type. Must be cheap and side-effect-free — it is
	 * called on every parameter of every driven dialog. Inspect the container
	 * with {@link Widgets#findAll}/{@link Widgets#contains} and switch on
	 * {@code value}'s runtime type.
	 *
	 * @param inputContainer the SciJava input container located for the parameter
	 *                       (the sibling container after the parameter's label)
	 * @param value          the value the caller wants to set
	 */
	boolean matches(Container inputContainer, Object value);

	/**
	 * Visibly drive the widget in {@code inputContainer} to {@code value}. Only
	 * called when {@link #matches} returned {@code true} for the same arguments.
	 * Implementations synthesize real input events (via {@code core.Ui}); throw
	 * a descriptive exception if the value can't be located in the widget.
	 */
	void fill(Container inputContainer, Object value);
}