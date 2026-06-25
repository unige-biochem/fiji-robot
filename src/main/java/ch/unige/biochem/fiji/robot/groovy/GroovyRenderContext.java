package ch.unige.biochem.fiji.robot.groovy;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-render accumulator threaded through one {@code GroovyRender} pass. It
 * collects the cross-cutting bits a single {@code cs.run(...)} line can't carry
 * on its own — {@code import} statements and hoisted SciJava script parameters
 * ({@code #@File}, {@code #@SomeService}) — and dedupes them, so the renderer
 * can emit a coherent preamble above the call.
 *
 * <p><b>File hoisting.</b> A {@code File} value is not inlined as
 * {@code new File("…")}; it is hoisted to a top-level {@code #@File} parameter
 * via {@link #hoistFile(File)}, deduped by absolute path so the same physical
 * file referenced by several inputs (or several commands) becomes one editable
 * parameter. A viewer re-running the script then gets a file-picker pre-filled
 * with the demo's path but trivially re-pointable to their own data.</p>
 *
 * <p><b>Service hoisting.</b> A resolution that needs a service to re-acquire
 * its object headlessly (e.g. the active-BDV lookup needs an
 * {@code ObjectService}) declares it via
 * {@link #requireScriptParam(String, String)}; the renderer emits the matching
 * {@code #@…} line once.</p>
 */
public final class GroovyRenderContext {

	private final Set<String> imports = new LinkedHashSet<>();
	/** Absolute path → hoisted {@code #@File} variable name. */
	private final Map<String, String> fileParams = new LinkedHashMap<>();
	/** Variable name → full {@code #@<Type> <name>} directive line. */
	private final Map<String, String> scriptParams = new LinkedHashMap<>();
	private int fileCounter = 0;

	/** Add an {@code import}; duplicates and {@code java.lang.*} are dropped. */
	public void addImport(String fqn) {
		if (fqn == null || fqn.startsWith("java.lang.")) return;
		imports.add(fqn);
	}

	/**
	 * Hoist {@code file} to a {@code #@File} script parameter and return the
	 * variable name to reference it by. Deduped by absolute path: the same file
	 * hoisted twice yields one parameter and the same name.
	 */
	public String hoistFile(File file) {
		String path = file.getAbsolutePath();
		String name = fileParams.get(path);
		if (name == null) {
			name = "file" + (++fileCounter);
			fileParams.put(path, name);
		}
		return name;
	}

	/**
	 * Declare a SciJava script parameter (e.g. {@code requireScriptParam(
	 * "ObjectService", "objectService")} → {@code #@ObjectService objectService})
	 * and return its variable name. Deduped by name; declaring the same name
	 * twice is harmless.
	 */
	public String requireScriptParam(String type, String name) {
		scriptParams.putIfAbsent(name, "#@" + type + " " + name);
		return name;
	}

	/** {@code true} when there is at least one hoisted {@code #@…} parameter. */
	public boolean hasScriptParams() {
		return !fileParams.isEmpty() || !scriptParams.isEmpty();
	}

	/** The {@code #@…} directive lines, file params first, then service params, in declaration order. */
	public List<String> directives() {
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, String> e : fileParams.entrySet()) {
			out.add("#@File(label=\"" + e.getValue() + "\", value=\""
					+ escape(e.getKey()) + "\") " + e.getValue());
		}
		out.addAll(scriptParams.values());
		return out;
	}

	/** The collected imports, in insertion order. */
	public Set<String> imports() {
		return imports;
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}