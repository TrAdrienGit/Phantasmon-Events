package com.mystaria.events.trahison.minigame.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonUi;

/**
 * A control of a mini-game screen. Immediate mode like the rest of the mod's screens: {@link #render} draws it in the
 * state the mouse implies; the screen forwards mouse events and remembers the widget that took a click to send it the
 * drag and the release. All coordinates are GUI pixels. {@link #enabled} is false while the screen is locked
 * (solved, closing): a disabled widget ignores input and draws flat.
 */
public abstract class Widget {

	protected int x;
	protected int y;
	protected int w;
	protected int h;
	public boolean enabled = true;

	/** Places the widget; returns it for chaining. */
	public <T extends Widget> T at(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		@SuppressWarnings("unchecked")
		T self = (T) this;
		return self;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int w() {
		return w;
	}

	public int h() {
		return h;
	}

	/** Centre of the widget, where remote cursors are pointed. */
	public int centerX() {
		return x + w / 2;
	}

	public int centerY() {
		return y + h / 2;
	}

	public boolean hovered(double mx, double my) {
		return enabled && TrahisonUi.inside(mx, my, x, y, w, h);
	}

	public abstract void render(GuiGraphics g, int mx, int my);

	/** Returns true if the widget took the click (it then receives the drag and the release). */
	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return false;
	}

	public boolean mouseReleased(double mx, double my, int button) {
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double delta) {
		return false;
	}

	/** Text shown in a tooltip while hovered, or null. */
	public Component tooltip(double mx, double my) {
		return null;
	}

	/** Shortest signed distance from {@code from} to {@code to} on a ring of {@code count} positions. */
	public static int ringDelta(int from, int to, int count) {
		int d = Math.floorMod(to - from, count);
		return d > count / 2 ? d - count : d;
	}

	/** Exponential approach of {@code current} to {@code target}, independent of the frame rate ({@code dtMs} elapsed). */
	protected static float approach(float current, float target, long dtMs, float tauMs) {
		float k = 1f - (float) Math.exp(-Math.max(0, dtMs) / tauMs);
		return current + (target - current) * k;
	}
}
