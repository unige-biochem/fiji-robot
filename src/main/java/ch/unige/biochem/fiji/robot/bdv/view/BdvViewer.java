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
package ch.unige.biochem.fiji.robot.bdv.view;

import bdv.viewer.ViewerPanel;
import bdv.viewer.animate.OverlayAnimator;
import org.scijava.ui.behaviour.GlobalKeyEventDispatcher;

import javax.swing.SwingUtilities;
import java.awt.KeyEventDispatcher;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * The viewer of a BigDataViewer window, for pictures: waits until it shows no
 * passing animation (the messages at its bottom right, the "Press F1 for help"
 * hint), so that a picture holds only what stays.
 *
 * <p>A message stays still for most of its duration before fading out: a
 * picture taken once the viewer has stopped changing for a few seconds can
 * still hold one. Programmatic, unlike {@link Bdv} (Robot gestures).</p>
 */
public final class BdvViewer {

	private BdvViewer() {}

	/**
	 * Waits until no overlay animator of the viewer needs repainting: messages
	 * gone, transitions done.
	 *
	 * @throws IllegalStateException after {@code timeoutMs}
	 */
	public static void waitForOverlayAnimations(ViewerPanel viewer, long timeoutMs) {
		long end = System.currentTimeMillis() + timeoutMs;
		while (animating(viewer)) {
			if (System.currentTimeMillis() > end) {
				throw new IllegalStateException("The viewer still animates after " + timeoutMs + " ms");
			}
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * The dispatcher through which BigDataViewer's behaviours know the keys held (shift, the A of
	 * {@code ctrl A}): give it to a {@code VirtualPointer} (its {@code addKeyEventDispatcher}), whose synthetic key
	 * events do not go through the keyboard focus manager.
	 */
	public static KeyEventDispatcher keyEventDispatcher() {
		return GlobalKeyEventDispatcher.getInstance();
	}

	/** True while an overlay animator of the viewer needs repainting: a message shows, a transition runs. */
	public static boolean animating(ViewerPanel viewer) {
		boolean[] animating = { false };
		onEdt(() -> {
			for (OverlayAnimator animator : overlayAnimators(viewer)) {
				if (animator.requiresRepaint()) animating[0] = true;
			}
		});
		return animating[0];
	}

	/** {@code ViewerPanel.overlayAnimators}, private: the animators the viewer paints. */
	@SuppressWarnings("unchecked")
	private static List<OverlayAnimator> overlayAnimators(ViewerPanel viewer) {
		try {
			Field field = ViewerPanel.class.getDeclaredField("overlayAnimators");
			field.setAccessible(true);
			return new ArrayList<>((List<OverlayAnimator>) field.get(viewer));
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("This BigDataViewer has no ViewerPanel.overlayAnimators", e);
		}
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
