package com.mystaria.events.trahison.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The scrollbars a screen drew this frame, so the mouse can take and drag them as well as the wheel scrolls them
 * (2026-11-21: the Trahison screens' bars only followed the wheel). A screen calls {@link #begin} at the start of its
 * render, {@link #add} beside each bar it draws, then {@link #click}, {@link #drag} and {@link #release} from its mouse
 * handlers. A press on a bar's track takes the thumb where it was, or brings its middle under the cursor.
 */
public final class DragBars {
	/** How far beside the drawn bar a press still takes it: the bars are 2 px thin. */
	private static final int MARGIN = 3;
	private static final int WIDTH = 2;

	private record Bar(int x, int y, int h, int scroll, int content, int minThumb, IntConsumer set) {
		int thumbH() {
			return Math.min(h, Math.max(minThumb, h * h / content));
		}

		int max() {
			return content - h;
		}
	}

	private final List<Bar> frame = new ArrayList<>();
	private Bar held;
	private double grab;

	public void begin() {
		frame.clear();
	}

	/** A bar drawn at {@code x}, over {@code h} from {@code y}, for {@code content} scrolled by {@code scroll}; {@code set} takes the new scroll. */
	public void add(int x, int y, int h, int scroll, int content, int minThumb, IntConsumer set) {
		if (content > h && h > 0) {
			frame.add(new Bar(x, y, h, scroll, content, minThumb, set));
		}
	}

	public boolean click(double mouseX, double mouseY) {
		for (Bar bar : frame) {
			if (mouseX >= bar.x() - MARGIN && mouseX < bar.x() + WIDTH + MARGIN && mouseY >= bar.y() && mouseY < bar.y() + bar.h()) {
				int thumbH = bar.thumbH();
				int thumbTop = bar.y() + Math.round((bar.h() - thumbH) * (bar.scroll() / (float) bar.max()));
				grab = mouseY >= thumbTop && mouseY < thumbTop + thumbH ? mouseY - thumbTop : thumbH / 2.0;
				held = bar;
				drag(mouseY);
				return true;
			}
		}
		return false;
	}

	public boolean drag(double mouseY) {
		if (held == null) {
			return false;
		}
		int free = held.h() - held.thumbH();
		float t = free <= 0 ? 0 : (float) ((mouseY - grab - held.y()) / free);
		held.set().accept(Math.max(0, Math.min(held.max(), Math.round(t * held.max()))));
		return true;
	}

	public void release() {
		held = null;
	}
}
