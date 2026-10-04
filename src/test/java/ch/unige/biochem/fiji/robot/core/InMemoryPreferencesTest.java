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
package ch.unige.biochem.fiji.robot.core;

import org.junit.Test;

import java.util.prefs.Preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Headless test of the in-memory preference nodes (not of the JVM-wide installation). */
public class InMemoryPreferencesTest {

	@Test
	public void storesAndRemoves() throws Exception {
		Preferences root = new InMemoryPreferences().userRoot();
		Preferences node = root.node("org/scijava/test");
		node.put("sigma", "2.0");
		assertEquals("2.0", root.node("org/scijava/test").get("sigma", null));
		assertEquals(1, root.node("org/scijava").childrenNames().length);
		node.removeNode();
		assertNull(root.node("org/scijava/test").get("sigma", null));
	}
}
