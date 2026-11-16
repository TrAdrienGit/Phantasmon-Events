package com.mystaria.events.trahison.minigame;

import net.minecraft.client.gui.GuiGraphics;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;

/**
 * Effects shared by every mini-game screen, drawn in 2D from the palette: the unlock animation (a padlock whose
 * shackle opens, a glow ring, a burst of particles), the error shake and flash, and the colour of each player. All
 * time-driven: callers pass elapsed progress, never a frame count.
 */
public final class MiniGameFx {

	/** Duration of the unlock animation before the screen closes. */
	public static final long SUCCESS_MS = 800;
	/** Duration of the error shake and flash. */
	public static final long ERROR_MS = 200;

	/**
	 * One colour per player index, from the palette only: the three game colours (light, then base) and the accent.
	 * Nine distinct entries for nine players; the name tag always accompanies it, so colour is never the only signal.
	 */
	private static final int[] PLAYER_COLORS = {
			TrahisonPalette.RED_LIGHT, TrahisonPalette.GREEN_LIGHT, TrahisonPalette.BLUE_LIGHT, TrahisonPalette.ACCENT,
			TrahisonPalette.RED, TrahisonPalette.GREEN, TrahisonPalette.BLUE, TrahisonPalette.ACCENT_DIM, TrahisonPalette.ACCENT_HOT };

	private MiniGameFx() {
	}

	/** The stable colour of the player with this index. */
	public static int playerColor(int index) {
		return PLAYER_COLORS[Math.floorMod(index, PLAYER_COLORS.length)];
	}

	/** Horizontal offset of the error shake {@code ageMs} after the error (0 when over). */
	public static int shakeOffset(long ageMs) {
		if (ageMs < 0 || ageMs >= ERROR_MS) {
			return 0;
		}
		float t = ageMs / (float) ERROR_MS;
		return Math.round((float) Math.sin(t * Math.PI * 6) * 4f * (1f - t));
	}

	/** Danger flash over the frame {@code x, y, w, h}, fading out over {@link #ERROR_MS}. */
	public static void errorFlash(GuiGraphics g, int x, int y, int w, int h, long ageMs) {
		if (ageMs < 0 || ageMs >= ERROR_MS) {
			return;
		}
		float fade = 1f - ageMs / (float) ERROR_MS;
		int a = (int) (0xA0 * fade);
		g.fill(x, y, x + w, y + h, TrahisonUi.withAlpha(TrahisonPalette.DANGER, (int) (0x28 * fade)));
		for (int i = 0; i < 2; i++) {
			g.fill(x - 1 - i, y - 1 - i, x + w + 1 + i, y - i, TrahisonUi.withAlpha(TrahisonPalette.DANGER, a));
			g.fill(x - 1 - i, y + h + i, x + w + 1 + i, y + h + 1 + i, TrahisonUi.withAlpha(TrahisonPalette.DANGER, a));
			g.fill(x - 1 - i, y - i, x - i, y + h + i, TrahisonUi.withAlpha(TrahisonPalette.DANGER, a));
			g.fill(x + w + i, y - i, x + w + 1 + i, y + h + i, TrahisonUi.withAlpha(TrahisonPalette.DANGER, a));
		}
	}

	/**
	 * The unlock animation over the rectangle {@code x, y, w, h}, centred on {@code cx, cy}. {@code t} runs 0..1 over
	 * {@link #SUCCESS_MS}: the content dims, a padlock appears, its shackle lifts open, a ring of light expands and
	 * particles burst outward.
	 */
	public static void success(GuiGraphics g, int x, int y, int w, int h, int cx, int cy, float t) {
		float dim = Math.min(1f, t * 3f);
		g.fill(x, y, x + w, y + h, TrahisonUi.withAlpha(TrahisonPalette.BG_DEEP, (int) (0xA0 * dim)));

		float open = TrahisonUi.easeOut((t - 0.25f) / 0.3f);
		float burst = Math.max(0f, (t - 0.3f) / 0.7f);

		// Expanding rings of light.
		if (burst > 0f) {
			float e = TrahisonUi.easeOut(burst);
			for (int i = 0; i < 2; i++) {
				float ee = Math.max(0f, e - i * 0.12f);
				int r = 14 + Math.round(Math.max(w, h) * 0.45f * ee);
				int a = (int) (0x90 * (1f - burst) * (1f - i * 0.4f));
				TrahisonUi.ring(g, cx, cy, r, Math.max(1, r - 3), TrahisonUi.withAlpha(TrahisonPalette.SUCCESS, a));
			}
		}

		// Particles: golden-angle fan, deterministic, fading as they travel.
		if (burst > 0f) {
			float e = TrahisonUi.easeOut(burst);
			for (int i = 0; i < 28; i++) {
				double angle = i * 2.39996323;
				float speed = 36f + (i * 37 % 64);
				int px = cx + (int) Math.round(Math.cos(angle) * speed * e * 1.6f);
				int py = cy + (int) Math.round(Math.sin(angle) * speed * e * 1.2f);
				int size = i % 3 == 0 ? 3 : 2;
				int color = i % 3 == 0 ? TrahisonPalette.TEXT : i % 3 == 1 ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT_HOT;
				g.fill(px, py, px + size, py + size, TrahisonUi.withAlpha(color, (int) (0xFF * (1f - burst))));
			}
		}

		padlock(g, cx, cy, open, Math.min(1f, t * 4f));
	}

	/** A padlock centred on {@code cx, cy}; {@code open} 0..1 lifts the shackle off its right leg; {@code alpha} fades it in. */
	private static void padlock(GuiGraphics g, int cx, int cy, float open, float alpha) {
		int a = (int) (0xFF * alpha);
		int lift = Math.round(open * 9);
		int arcCy = cy - 8 - lift;
		// Shackle: upper half ring plus two legs; the right leg stays short and floats once lifted.
		int rOut = 11;
		int rIn = 7;
		int metalLight = TrahisonUi.withAlpha(TrahisonPalette.TEXT_SECONDARY, a);
		int metal = TrahisonUi.withAlpha(TrahisonPalette.EDGE_LIGHT, a);
		int border = TrahisonUi.withAlpha(TrahisonPalette.BORDER, a);
		for (int dy = -rOut - 1; dy < 0; dy++) {
			double d = dy + 0.5;
			int ho = (int) Math.round(Math.sqrt(Math.max(0, (double) (rOut + 1) * (rOut + 1) - d * d)));
			g.fill(cx - ho, arcCy + dy, cx + ho, arcCy + dy + 1, border);
		}
		g.fill(cx - rOut - 1, arcCy, cx - rIn + 1, cy - 3, border);
		g.fill(cx + rIn - 1, arcCy, cx + rOut + 1, arcCy + 3 + (lift == 0 ? 3 : 0), border);
		for (int dy = -rOut; dy < 0; dy++) {
			double d = dy + 0.5;
			int ho = (int) Math.round(Math.sqrt(Math.max(0, (double) rOut * rOut - d * d)));
			int hi = Math.abs(d) < rIn ? (int) Math.round(Math.sqrt((double) rIn * rIn - d * d)) : 0;
			g.fill(cx - ho, arcCy + dy, cx - hi, arcCy + dy + 1, metal);
			g.fill(cx + hi, arcCy + dy, cx + ho, arcCy + dy + 1, metal);
			if (hi == 0) {
				g.fill(cx - ho, arcCy + dy, cx + ho, arcCy + dy + 1, metal);
			}
		}
		g.fill(cx - rOut, arcCy, cx - rIn, cy - 3, metal);
		g.fill(cx - rOut, arcCy, cx - rOut + 1, cy - 3, metalLight);
		g.fill(cx + rIn, arcCy, cx + rOut, arcCy + 2 + (lift == 0 ? 3 : 0), metal);
		// Body.
		g.fill(cx - 17, cy - 4, cx + 17, cy + 20, border);
		g.fillGradient(cx - 16, cy - 3, cx + 16, cy + 19,
				TrahisonUi.withAlpha(TrahisonPalette.SUCCESS, a), TrahisonUi.withAlpha(TrahisonPalette.SUCCESS_DARK, a));
		g.fill(cx - 16, cy - 3, cx + 16, cy - 2, TrahisonUi.withAlpha(TrahisonPalette.SHINE, 0x30));
		g.fill(cx - 2, cy + 4, cx + 2, cy + 8, border);
		g.fill(cx - 1, cy + 8, cx + 1, cy + 14, border);
	}
}
