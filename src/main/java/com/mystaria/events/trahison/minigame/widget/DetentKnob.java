package com.mystaria.events.trahison.minigame.widget;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.MiniGameSounds;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;

/**
 * A wheel with detents: a knob that clicks from one value (0..count-1) to the next. Drag around its centre, scroll, or
 * use the two chevrons. The value is shown in the big font in its centre. {@link #setValue} (a sync from the backend)
 * animates the knob along the shortest way; the player's own gesture calls the {@link #onTurn} callback with +1 or -1
 * for each detent crossed, after having already turned the knob (optimistic).
 */
public final class DetentKnob extends Widget {

	private final int count;
	private int value;
	private float target;
	private float shown;
	private long lastRender;
	private boolean dragging;
	private double lastAngle;
	private double accumulated;
	private IntConsumer onTurn = d -> { };
	/** 0 for none, else an ARGB glow around the knob (e.g. SUCCESS when unlocked). */
	private int glow;
	private long touchedAt;

	public DetentKnob(int count) {
		this.count = count;
	}

	public DetentKnob onTurn(IntConsumer onTurn) {
		this.onTurn = onTurn;
		return this;
	}

	public int value() {
		return value;
	}

	public void glow(int color) {
		this.glow = color;
	}

	/** Brief highlight of the rim, used when someone else touches this knob. */
	public void touch() {
		touchedAt = System.currentTimeMillis();
	}

	private float step() {
		return 360f / count;
	}

	private int radius() {
		return Math.min(w, h) / 2;
	}

	/** Moves the knob to {@code v} (modulo count) without calling the callback, turning the short way round. */
	public void setValue(int v) {
		int nv = Math.floorMod(v, count);
		target += ringDelta(value, nv, count) * step();
		value = nv;
	}

	/** One detent in direction {@code dir} (+1 clockwise, -1 counter-clockwise): moves, clicks, reports. */
	public void turn(int dir) {
		if (!enabled) {
			return;
		}
		value = Math.floorMod(value + dir, count);
		target += dir * step();
		MiniGameSounds.detent(0.9f + 0.07f * value);
		onTurn.accept(dir);
	}

	private boolean onChevron(double mx, double my, boolean up) {
		int cy = up ? y - 13 : y + h + 3;
		return TrahisonUi.inside(mx, my, centerX() - 9, cy, 18, 10);
	}

	private double angleOf(double mx, double my) {
		return Math.toDegrees(Math.atan2(my - centerY(), mx - centerX()));
	}

	@Override
	public boolean hovered(double mx, double my) {
		double dx = mx - centerX();
		double dy = my - centerY();
		return enabled && dx * dx + dy * dy <= (radius() + 3.0) * (radius() + 3.0);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!enabled || button != 0) {
			return false;
		}
		if (onChevron(mx, my, true)) {
			MiniGameSounds.click();
			turn(1);
			return true;
		}
		if (onChevron(mx, my, false)) {
			MiniGameSounds.click();
			turn(-1);
			return true;
		}
		if (hovered(mx, my)) {
			dragging = true;
			lastAngle = angleOf(mx, my);
			accumulated = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (!dragging) {
			return false;
		}
		double a = angleOf(mx, my);
		double d = a - lastAngle;
		while (d > 180) {
			d -= 360;
		}
		while (d < -180) {
			d += 360;
		}
		lastAngle = a;
		accumulated += d;
		while (accumulated >= step()) {
			accumulated -= step();
			turn(1);
		}
		while (accumulated <= -step()) {
			accumulated += step();
			turn(-1);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		if (!hovered(mx, my) || delta == 0) {
			return false;
		}
		turn(delta > 0 ? 1 : -1);
		return true;
	}

	@Override
	public Component tooltip(double mx, double my) {
		return null;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my) {
		long now = System.currentTimeMillis();
		long dt = lastRender == 0 ? 0 : now - lastRender;
		lastRender = now;
		shown = approach(shown, target, dt, 55f);
		if (Math.abs(shown - target) < 0.2f) {
			shown = target;
		}
		boolean hot = hovered(mx, my);
		boolean touched = now - touchedAt < 700;
		int cx = centerX();
		int cy = centerY();
		int r = radius();

		if (glow != 0) {
			TrahisonUi.disc(g, cx, cy, r + 8, TrahisonUi.withAlpha(glow, 0x30));
			TrahisonUi.disc(g, cx, cy, r + 5, TrahisonUi.withAlpha(glow, 0x40));
		}

		// Fixed ticks around the knob: the one the marker points at is lit.
		for (int i = 0; i < count; i++) {
			boolean active = i == value;
			TrahisonUi.rotatedFill(g, cx, cy, i * step(), -1, -(r + (active ? 8 : 6)), 1, -(r + 3),
					active ? TrahisonPalette.ACCENT : TrahisonPalette.EDGE);
		}

		// Body: black outline, lit rim, raised face, sunken numeral well.
		int rim = !enabled ? TrahisonPalette.PANEL_RAISED
				: dragging ? TrahisonPalette.ACCENT_DIM
				: touched ? TrahisonPalette.ACCENT
				: hot ? TrahisonPalette.EDGE_LIGHT : TrahisonPalette.EDGE;
		TrahisonUi.disc(g, cx, cy, r + 1, TrahisonPalette.BORDER);
		TrahisonUi.disc(g, cx, cy, r, rim);
		TrahisonUi.disc(g, cx, cy, r - 3, dragging ? TrahisonPalette.PANEL : TrahisonPalette.EDGE_LIGHT);
		TrahisonUi.disc(g, cx + 1, cy + 1, r - 4, dragging ? TrahisonPalette.PANEL_SUNKEN : TrahisonPalette.PANEL_RAISED);
		// Knurling on the rim turns with the knob so that the motion reads.
		for (int i = 0; i < 18; i++) {
			TrahisonUi.rotatedFill(g, cx, cy, shown + i * 20f, 0, -(r - 1), 1, -(r - 3), TrahisonPalette.BORDER);
		}
		TrahisonUi.disc(g, cx, cy, r - 10, TrahisonPalette.BORDER);
		TrahisonUi.disc(g, cx, cy, r - 11, TrahisonPalette.PANEL_SUNKEN);
		// Marker: a bright wedge on the face, pointing at a tick.
		TrahisonUi.rotatedFill(g, cx, cy, shown, -2, -(r - 3), 2, -(r - 9), enabled ? TrahisonPalette.ACCENT_HOT : TrahisonPalette.TEXT_DISABLED);

		Component number = Component.literal(Integer.toString(value));
		int nw = TrahisonUi.width(number) * 2;
		TrahisonUi.bigText(g, number, cx - nw / 2, cy - 7, 2f, enabled ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_DISABLED);

		chevron(g, cx, y - 12, true, enabled && onChevron(mx, my, true));
		chevron(g, cx, y + h + 4, false, enabled && onChevron(mx, my, false));
	}

	private void chevron(GuiGraphics g, int cx, int top, boolean up, boolean hot) {
		int color = !enabled ? TrahisonPalette.TEXT_DISABLED : hot ? TrahisonPalette.ACCENT_HOT : TrahisonPalette.TEXT_MUTED;
		for (int i = 0; i < 4; i++) {
			int row = up ? top + 1 + i : top + 5 - i;
			g.fill(cx - 1 - i * 2 + 0, row, cx + 1 + i * 2, row + 1, color);
		}
	}
}
