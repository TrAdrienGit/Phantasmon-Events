package com.mystaria.events.trahison.minigame.widget;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphics;

import com.mystaria.events.trahison.minigame.MiniGameSounds;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;

/**
 * A sliding piece: a handle dragged along a straight track, snapping to {@code stops} evenly spaced positions
 * (0..stops-1). The widget's rectangle is the track; the piece is {@code pieceLength} long. Clicks on the track
 * move the piece to the nearest stop. {@link #onMove} reports the stop reached; {@link #setStop} syncs.
 */
public final class SlidingPiece extends Widget {

	private final boolean horizontal;
	private final int stops;
	private final int pieceLength;
	private int stop;
	/** Displayed position along the track, in stop units (continuous while dragged or easing). */
	private float shown;
	private long lastRender;
	private boolean dragging;
	private float grab;
	private IntConsumer onMove = s -> { };
	private int tone = TrahisonPalette.EDGE;
	private long touchedAt;

	public SlidingPiece(boolean horizontal, int stops, int pieceLength) {
		this.horizontal = horizontal;
		this.stops = Math.max(2, stops);
		this.pieceLength = pieceLength;
	}

	public SlidingPiece onMove(IntConsumer onMove) {
		this.onMove = onMove;
		return this;
	}

	public SlidingPiece tone(int tone) {
		this.tone = tone;
		return this;
	}

	public int stop() {
		return stop;
	}

	public void touch() {
		touchedAt = System.currentTimeMillis();
	}

	public void setStop(int s) {
		stop = Math.max(0, Math.min(stops - 1, s));
		if (!dragging) {
			// shown eases toward it in render
		}
	}

	private int trackLength() {
		return (horizontal ? w : h) - pieceLength - 2;
	}

	/** Pixel offset along the track of a continuous position. */
	private int offsetOf(float pos) {
		return 1 + Math.round(pos / (stops - 1) * trackLength());
	}

	private boolean onPiece(double mx, double my) {
		int off = offsetOf(shown);
		return horizontal ? TrahisonUi.inside(mx, my, x + off, y, pieceLength, h) : TrahisonUi.inside(mx, my, x, y + off, w, pieceLength);
	}

	private float posAt(double mx, double my, float grabPx) {
		double along = (horizontal ? mx - x : my - y) - grabPx - 1;
		return (float) Math.max(0, Math.min(stops - 1, along / trackLength() * (stops - 1)));
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (!enabled || button != 0 || !TrahisonUi.inside(mx, my, x, y, w, h)) {
			return false;
		}
		if (onPiece(mx, my)) {
			grab = (float) ((horizontal ? mx - x : my - y) - offsetOf(shown));
		} else {
			grab = pieceLength / 2f;
		}
		dragging = true;
		MiniGameSounds.click();
		reach(posAt(mx, my, grab));
		return true;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (!dragging) {
			return false;
		}
		reach(posAt(mx, my, grab));
		return true;
	}

	/** Follows the pointer; each stop crossed clicks and is reported. */
	private void reach(float pos) {
		shown = pos;
		int nearest = Math.round(pos);
		if (nearest != stop) {
			int dir = nearest > stop ? 1 : -1;
			while (stop != nearest) {
				stop += dir;
				MiniGameSounds.detent(1.0f + 0.04f * stop);
				onMove.accept(stop);
			}
		}
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (!dragging) {
			return false;
		}
		dragging = false;
		MiniGameSounds.clunk();
		return true;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my) {
		long now = System.currentTimeMillis();
		long dt = lastRender == 0 ? 0 : now - lastRender;
		lastRender = now;
		if (!dragging) {
			shown = approach(shown, stop, dt, 50f);
		}
		boolean hot = enabled && (dragging || onPiece(mx, my));
		boolean touched = now - touchedAt < 700;
		TrahisonUi.well(g, x, y, w, h);
		// Stop marks inside the track.
		for (int i = 0; i < stops; i++) {
			int c = offsetOf(i) + pieceLength / 2;
			if (horizontal) {
				g.fill(x + c, y + 3, x + c + 1, y + h - 3, i == stop ? TrahisonPalette.ACCENT_DIM : TrahisonPalette.DOTS);
			} else {
				g.fill(x + 3, y + c, x + w - 3, y + c + 1, i == stop ? TrahisonPalette.ACCENT_DIM : TrahisonPalette.DOTS);
			}
		}
		int off = offsetOf(shown);
		int px = horizontal ? x + off : x + 1;
		int py = horizontal ? y + 1 : y + off;
		int pw = horizontal ? pieceLength : w - 2;
		int ph = horizontal ? h - 2 : pieceLength;
		int top = !enabled ? TrahisonPalette.PANEL : touched ? TrahisonPalette.ACCENT : dragging ? tone : hot ? TrahisonPalette.EDGE_LIGHT : TrahisonPalette.EDGE;
		g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, TrahisonPalette.BORDER);
		g.fillGradient(px, py, px + pw, py + ph, top, TrahisonPalette.PANEL_RAISED);
		if (dragging) {
			com.mystaria.phantasmon.client.gui.menu.MenuUi.sunken(g, px, py, px + pw, py + ph);
		} else {
			com.mystaria.phantasmon.client.gui.menu.MenuUi.raised(g, px, py, px + pw, py + ph);
		}
		// Grip lines.
		for (int i = -1; i <= 1; i++) {
			if (horizontal) {
				g.fill(px + pw / 2 + i * 3, py + 3, px + pw / 2 + i * 3 + 1, py + ph - 3, TrahisonPalette.BORDER);
			} else {
				g.fill(px + 3, py + ph / 2 + i * 3, px + pw - 3, py + ph / 2 + i * 3 + 1, TrahisonPalette.BORDER);
			}
		}
	}
}
