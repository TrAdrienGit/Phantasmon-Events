package com.mystaria.events.trahison.minigame.widget;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphics;

import com.mystaria.events.trahison.minigame.MiniGameSounds;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;

/**
 * A rotating piece: a flat ring (concentric rings of a lock, a dial plate) turned by dragging it around its centre.
 * It has {@code positions} stops; the ring clicks at each stop it crosses and settles on the nearest one when
 * released. {@link #onStep} reports each stop crossed (+1 or -1); {@link #setPosition} syncs it from the backend.
 */
public final class RotaryRing extends Widget {

	private final int outer;
	private final int inner;
	private final int positions;
	private int position;
	private float target;
	private float shown;
	private long lastRender;
	private boolean dragging;
	private double lastAngle;
	private double accumulated;
	private IntConsumer onStep = d -> { };
	private int tone = TrahisonPalette.EDGE;
	private long touchedAt;

	public RotaryRing(int outer, int inner, int positions) {
		this.outer = outer;
		this.inner = inner;
		this.positions = positions;
	}

	public RotaryRing onStep(IntConsumer onStep) {
		this.onStep = onStep;
		return this;
	}

	/** The ring's body colour (a game colour's dark tone, for instance). */
	public RotaryRing tone(int tone) {
		this.tone = tone;
		return this;
	}

	public int position() {
		return position;
	}

	public void touch() {
		touchedAt = System.currentTimeMillis();
	}

	private float step() {
		return 360f / positions;
	}

	public void setPosition(int p) {
		int np = Math.floorMod(p, positions);
		target += ringDelta(position, np, positions) * step();
		position = np;
	}

	private double angleOf(double mx, double my) {
		return Math.toDegrees(Math.atan2(my - centerY(), mx - centerX()));
	}

	@Override
	public boolean hovered(double mx, double my) {
		double dx = mx - centerX();
		double dy = my - centerY();
		double d2 = dx * dx + dy * dy;
		return enabled && d2 <= outer * (double) outer && d2 >= inner * (double) inner;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!enabled || button != 0 || !hovered(mx, my)) {
			return false;
		}
		dragging = true;
		lastAngle = angleOf(mx, my);
		accumulated = 0;
		MiniGameSounds.click();
		return true;
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
		// The ring follows the pointer smoothly; stops are counted from the accumulated angle.
		target += (float) d;
		while (accumulated >= step() / 2 + 0.01) {
			accumulated -= step();
			position = Math.floorMod(position + 1, positions);
			MiniGameSounds.detent(0.9f + 0.05f * position);
			onStep.accept(1);
		}
		while (accumulated <= -step() / 2 - 0.01) {
			accumulated += step();
			position = Math.floorMod(position - 1, positions);
			MiniGameSounds.detent(0.9f + 0.05f * position);
			onStep.accept(-1);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (!dragging) {
			return false;
		}
		dragging = false;
		accumulated = 0;
		// Settle on the stop: the target snaps to the nearest multiple of the step belonging to the current position.
		float base = Math.round(target / 360f) * 360f;
		float snap = base + position * step();
		while (snap - target > 180f) {
			snap -= 360f;
		}
		while (target - snap > 180f) {
			snap += 360f;
		}
		target = snap;
		MiniGameSounds.clunk();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double delta) {
		if (!hovered(mx, my) || delta == 0) {
			return false;
		}
		int dir = delta > 0 ? 1 : -1;
		position = Math.floorMod(position + dir, positions);
		target += dir * step();
		MiniGameSounds.detent(0.9f + 0.05f * position);
		onStep.accept(dir);
		return true;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my) {
		long now = System.currentTimeMillis();
		long dt = lastRender == 0 ? 0 : now - lastRender;
		lastRender = now;
		// While dragged the ring follows the pointer exactly; otherwise it eases to its target.
		shown = dragging ? target : approach(shown, target, dt, 60f);
		boolean hot = hovered(mx, my);
		boolean touched = now - touchedAt < 700;
		int cx = centerX();
		int cy = centerY();
		int rim = !enabled ? TrahisonPalette.PANEL_RAISED
				: dragging ? TrahisonPalette.ACCENT_DIM
				: touched ? TrahisonPalette.ACCENT
				: hot ? TrahisonPalette.EDGE_LIGHT : TrahisonPalette.EDGE;
		TrahisonUi.ring(g, cx, cy, outer + 1, inner - 1, TrahisonPalette.BORDER);
		TrahisonUi.ring(g, cx, cy, outer, inner, rim);
		TrahisonUi.ring(g, cx, cy, outer - 2, inner + 2, tone);
		// Notches turn with the ring; the first one is brighter so that the position reads.
		int mid = (outer + inner) / 2;
		for (int i = 0; i < positions; i++) {
			boolean first = i == 0;
			TrahisonUi.rotatedFill(g, cx, cy, shown + i * step(), -1, -(outer - 2), 1, -(first ? inner + 2 : mid + 1),
					first ? TrahisonPalette.ACCENT_HOT : TrahisonPalette.BORDER);
		}
	}
}
