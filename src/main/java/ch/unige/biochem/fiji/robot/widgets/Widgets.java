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

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

/**
 * Small Swing component-tree utilities shared by {@link Harvester} and by
 * out-of-package {@link WidgetDriver} implementations (e.g. the BDV binding).
 *
 * <p>Pure Swing — no SciJava, no BigDataViewer coupling — so a binding can use
 * these to inspect a harvester input container without re-implementing the
 * recursive walk.</p>
 */
public final class Widgets {

	private Widgets() {}

	/** Recursively collect every component of {@code type} under {@code root}, in pre-order. */
	public static <T extends Component> List<T> findAll(Container root, Class<T> type) {
		List<T> out = new ArrayList<>();
		collect(root, type, out);
		return out;
	}

	/**
	 * First component of {@code type} under {@code root}, or {@code null} if
	 * none. Pre-order — the same order {@link #findAll} yields.
	 */
	public static <T extends Component> T firstOrNull(Container root, Class<T> type) {
		List<T> all = findAll(root, type);
		return all.isEmpty() ? null : all.get(0);
	}

	/** True iff at least one component of {@code type} exists under {@code root}. */
	public static boolean contains(Container root, Class<? extends Component> type) {
		return firstOrNull(root, type) != null;
	}

	private static <T extends Component> void collect(Container c, Class<T> type, List<T> out) {
		for (Component child : c.getComponents()) {
			if (type.isInstance(child)) out.add(type.cast(child));
			if (child instanceof Container) collect((Container) child, type, out);
		}
	}
}
