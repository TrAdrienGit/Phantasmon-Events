package com.mystaria.events.trahison.minigame;

import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;

/**
 * The look of a mini-game screen's background, drawn entirely in code from the palette: an old, abandoned complex.
 * Layers, back to front: a deep gradient; wall-panel seams with rivets; a few damp stains; film grain with faint
 * scanlines (one small noise texture generated once and tiled); a slow, barely visible light band drifting down; a
 * vignette. Nothing flickers faster than the charter allows.
 */
public final class Backdrop {

	private static final int TILE = 128;
	private static final ResourceLocation GRAIN = ResourceLocation.fromNamespaceAndPath("phantasmon_events", "trahison/minigame_grain");
	private static boolean grainReady;
	private static boolean grainFailed;

	private Backdrop() {
	}

	/** Draws the whole background over the screen {@code w x h} (GUI pixels). */
	public static void render(GuiGraphics g, int w, int h) {
		g.fillGradient(0, 0, w, h, TrahisonPalette.BG, TrahisonPalette.BG_DEEP);
		panelSeams(g, w, h);
		stains(g, w, h);
		grain(g, w, h);
		lightBand(g, w, h);
		vignette(g, w, h);
	}

	/** Wall panels: seams every 64 pixels, a rivet at each crossing, offset rows like bricks. */
	private static void panelSeams(GuiGraphics g, int w, int h) {
		int seam = TrahisonUi.withAlpha(TrahisonPalette.DOTS, 0xB0);
		int shade = TrahisonUi.withAlpha(TrahisonPalette.BORDER, 0x60);
		int rivet = TrahisonUi.withAlpha(TrahisonPalette.EDGE, 0x50);
		for (int y = 0; y < h; y += 64) {
			g.fill(0, y, w, y + 1, seam);
			g.fill(0, y + 1, w, y + 2, shade);
		}
		int row = 0;
		for (int y = 0; y < h; y += 64, row++) {
			int offset = row % 2 == 0 ? 0 : 48;
			for (int x = -offset + 96; x < w; x += 96) {
				if (x < 0) {
					continue;
				}
				g.fill(x, y, x + 1, Math.min(h, y + 64), seam);
				g.fill(x + 1, y, x + 2, Math.min(h, y + 64), shade);
				g.fill(x - 2, y + 4, x, y + 6, rivet);
				g.fill(x + 3, y + 4, x + 5, y + 6, rivet);
			}
		}
	}

	/** A few dark, soft damp stains, from stacked translucent discs; positions follow the screen so they stay put. */
	private static void stains(GuiGraphics g, int w, int h) {
		Random rnd = new Random(0x7A11);
		for (int i = 0; i < 4; i++) {
			int cx = (int) (rnd.nextDouble() * w);
			int cy = (int) (rnd.nextDouble() * h);
			int r = 36 + rnd.nextInt(40);
			for (int ring = 0; ring < 7; ring++) {
				TrahisonUi.disc(g, cx, cy, r - ring * r / 8, TrahisonUi.withAlpha(TrahisonPalette.BG_DEEP, 0x16));
			}
		}
	}

	private static void ensureGrain() {
		if (grainReady || grainFailed) {
			return;
		}
		try {
			NativeImage image = new NativeImage(TILE, TILE, false);
			Random rnd = new Random(0xC0FFEE);
			for (int y = 0; y < TILE; y++) {
				for (int x = 0; x < TILE; x++) {
					double v = rnd.nextDouble();
					int argb = 0;
					if (v > 0.94) {
						argb = 0x14FFFFFF;
					} else if (v < 0.30) {
						argb = 0x34000000;
					}
					if (y % 4 == 0) {
						// Faint scanline: darkens whatever the noise left.
						argb = Math.min(0x50, ((argb >>> 24) & 0xFF) + 0x16) << 24;
					}
					// NativeImage wants ABGR.
					int a = argb >>> 24;
					int r = (argb >> 16) & 0xFF;
					int gr = (argb >> 8) & 0xFF;
					int b = argb & 0xFF;
					image.setPixelRGBA(x, y, a << 24 | b << 16 | gr << 8 | r);
				}
			}
			Minecraft.getInstance().getTextureManager().register(GRAIN, new DynamicTexture(image));
			grainReady = true;
		} catch (Throwable t) {
			grainFailed = true;
		}
	}

	private static void grain(GuiGraphics g, int w, int h) {
		ensureGrain();
		if (!grainReady) {
			return;
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		for (int y = 0; y < h; y += TILE) {
			for (int x = 0; x < w; x += TILE) {
				int tw = Math.min(TILE, w - x);
				int th = Math.min(TILE, h - y);
				g.blit(GRAIN, x, y, 0, 0f, 0f, tw, th, TILE, TILE);
			}
		}
	}

	/** A pale horizontal band drifting down once every nine seconds: a failing light somewhere out of view. */
	private static void lightBand(GuiGraphics g, int w, int h) {
		long period = 9000;
		float t = System.currentTimeMillis() % period / (float) period;
		int cy = (int) (-20 + (h + 40) * t);
		for (int i = -8; i <= 8; i++) {
			int a = (int) (0x08 * (1f - Math.abs(i) / 9f));
			g.fill(0, cy + i * 2, w, cy + i * 2 + 2, TrahisonUi.withAlpha(TrahisonPalette.ACCENT, a));
		}
	}

	/** Darkened edges, stepped in eight bands per side. */
	private static void vignette(GuiGraphics g, int w, int h) {
		int bandW = Math.max(6, w / 16);
		int bandH = Math.max(6, h / 12);
		for (int i = 0; i < 8; i++) {
			int a = (int) (0x58 * Math.pow(1 - i / 8.0, 2));
			int color = TrahisonUi.withAlpha(TrahisonPalette.BG_DEEP, a);
			int dx = i * bandW / 8;
			int dy = i * bandH / 8;
			// Four edges of the i-th frame (outermost is darkest).
			g.fill(dx, dy, w - dx, dy + Math.max(1, bandH / 8), color);
			g.fill(dx, h - dy - Math.max(1, bandH / 8), w - dx, h - dy, color);
			g.fill(dx, dy, dx + Math.max(1, bandW / 8), h - dy, color);
			g.fill(w - dx - Math.max(1, bandW / 8), dy, w - dx, h - dy, color);
		}
	}
}
