package ch.epfl.biop.scijava.ui.robot.groovy;

import org.scijava.command.Command;

import java.io.File;
import java.util.Map;

/**
 * Renders a command run as a self-contained, runnable Groovy snippet — the
 * "headless reproduction" projection of a {@code CmdExecutor} plan.
 *
 * <p>The output mirrors what a viewer of a tutorial recording can paste into
 * Fiji's script editor to re-run the same command without a UI:</p>
 *
 * <pre>
 * import com.example.MyCommand
 *
 * cs.run(MyCommand.class, true,
 *     "a", 2,
 *     "op", "sum"  // We name the operation.
 * ).get()
 * </pre>
 *
 * <p>This is a deliberately small first cut: it renders primitive literals,
 * strings, {@code String[]} and {@code File}/{@code File[]}. The richer
 * behaviour of the original demo toolkit — hoisting {@code File} values to
 * {@code #@File} script parameters, and pluggable per-resolution rendering for
 * object-valued inputs such as {@code BdvHandle} (a title lookup) — will layer
 * on once those resolution kinds exist.</p>
 */
public final class GroovyRender {

	private GroovyRender() {}

	/**
	 * Render a complete {@code cs.run(...)} snippet for {@code command} with the
	 * given ordered inputs. {@code narrations} (name → text) are emitted as
	 * trailing {@code // ...} comments on the matching argument line.
	 */
	public static String renderRun(Class<? extends Command> command,
								   Map<String, Object> inputs,
								   Map<String, String> narrations) {
		StringBuilder sb = new StringBuilder();
		sb.append("import ").append(command.getName()).append('\n');
		if (containsFile(inputs)) sb.append("import java.io.File\n");
		sb.append('\n');

		sb.append("cs.run(").append(command.getSimpleName()).append(".class, true");
		if (!inputs.isEmpty()) sb.append(",\n");

		int idx = 0;
		int count = inputs.size();
		for (Map.Entry<String, Object> e : inputs.entrySet()) {
			boolean last = (++idx == count);
			String name = e.getKey();
			// Order matters: the comma must precede any `//` so it isn't
			// swallowed by Groovy's end-of-line comment.
			sb.append("    \"").append(name).append("\", ").append(literal(e.getValue()));
			if (!last) sb.append(',');
			String narration = narrations.get(name);
			if (narration != null) sb.append("  // ").append(oneLine(narration));
			sb.append('\n');
		}
		sb.append(").get()");
		return sb.toString();
	}

	/**
	 * Render a single value as a Groovy literal suitable for splicing into a
	 * {@code cs.run(...)} argument list.
	 */
	public static String literal(Object value) {
		if (value == null) return "null";
		if (value instanceof String) return "\"" + escape((String) value) + "\"";
		if (value instanceof Boolean) return value.toString();
		if (value instanceof Integer) return value.toString();
		if (value instanceof Long) return value + "L";
		if (value instanceof Float) return value + "f";
		if (value instanceof Double) return value + "d";
		if (value instanceof File) {
			return "new File(\"" + escape(((File) value).getAbsolutePath()) + "\")";
		}
		if (value instanceof File[]) {
			File[] arr = (File[]) value;
			StringBuilder sb = new StringBuilder("new File[]{");
			for (int i = 0; i < arr.length; i++) {
				if (i > 0) sb.append(", ");
				sb.append("new File(\"").append(escape(arr[i].getAbsolutePath())).append("\")");
			}
			return sb.append('}').toString();
		}
		if (value instanceof String[]) {
			String[] arr = (String[]) value;
			StringBuilder sb = new StringBuilder("new String[]{");
			for (int i = 0; i < arr.length; i++) {
				if (i > 0) sb.append(", ");
				sb.append('"').append(escape(arr[i])).append('"');
			}
			return sb.append('}').toString();
		}
		// Fallback: toString() with a marker so a reader knows to revisit.
		return value + " /* TODO: review unsupported type "
				+ value.getClass().getSimpleName() + " */";
	}

	private static boolean containsFile(Map<String, Object> inputs) {
		for (Object v : inputs.values()) {
			if (v instanceof File || v instanceof File[]) return true;
		}
		return false;
	}

	private static String oneLine(String s) {
		return s.replace('\n', ' ').replace('\r', ' ').trim();
	}

	private static String escape(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder(s.length() + 2);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '\\': sb.append("\\\\"); break;
				case '"':  sb.append("\\\""); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:   sb.append(c);
			}
		}
		return sb.toString();
	}
}
