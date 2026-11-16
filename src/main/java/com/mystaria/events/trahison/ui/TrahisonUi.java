package com.mystaria.events.trahison.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;

import com.cobblemon.mod.common.client.render.RenderHelperKt;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Static drawing helpers of the betrayal game's screens, in plain GUI pixels, laid out like Showtime's menu and
 * Phantasmon's {@link MenuUi}: one-pixel black outlines, lit and shaded edges, Cobblemon's {@code uniform} font. The
 * relief, text and hit-test primitives are {@link MenuUi}'s own; only the colours (see {@link TrahisonPalette}) and the
 * game-specific shapes live here. Nothing in this class knows a game rule: callers pass what to show. Client thread.
 */
public final class TrahisonUi {

	/** Height of one text line in the menu font. */
	public static final int LINE = 10;
	/** Base spacing unit; paddings and gaps are multiples of it. */
	public static final int GRID = 4;
	/** Standard control height. */
	public static final int BUTTON_H = 18;
	/** Safe margin kept free around HUD elements and stages. */
	public static final int SAFE_MARGIN = 6;
	/** Typewriter speed of the announcer lines, in characters per second. */
	public static final float TYPEWRITER_CPS = 40f;

	/** The three game colours with their tones and their non-colour symbol. */
	public enum GameColor {
		RED(TrahisonPalette.RED, TrahisonPalette.RED_LIGHT, TrahisonPalette.RED_DARK, Glyph.TRIANGLE),
		GREEN(TrahisonPalette.GREEN, TrahisonPalette.GREEN_LIGHT, TrahisonPalette.GREEN_DARK, Glyph.CIRCLE),
		BLUE(TrahisonPalette.BLUE, TrahisonPalette.BLUE_LIGHT, TrahisonPalette.BLUE_DARK, Glyph.SQUARE);

		public final int base;
		public final int light;
		public final int dark;
		public final Glyph glyph;

		GameColor(int base, int light, int dark, Glyph glyph) {
			this.base = base;
			this.light = light;
			this.dark = dark;
			this.glyph = glyph;
		}
	}

	/**
	 * The wire colours of the wiring puzzle: the three game colours (same tones, same symbols) plus yellow (diamond) and white
	 * (ring), which exist for wires only (charte-ui.md section 3). Names are the ids the backend sends ({@code red}...
	 * {@code white}).
	 */
	public enum WireColor {
		RED(GameColor.RED),
		GREEN(GameColor.GREEN),
		BLUE(GameColor.BLUE),
		YELLOW(TrahisonPalette.YELLOW, TrahisonPalette.YELLOW_LIGHT, TrahisonPalette.YELLOW_DARK, Glyph.DIAMOND),
		WHITE(TrahisonPalette.WHITE, TrahisonPalette.WHITE_LIGHT, TrahisonPalette.WHITE_DARK, Glyph.RING);

		public final int base;
		public final int light;
		public final int dark;
		public final Glyph glyph;

		WireColor(GameColor game) {
			this(game.base, game.light, game.dark, game.glyph);
		}

		WireColor(int base, int light, int dark, Glyph glyph) {
			this.base = base;
			this.light = light;
			this.dark = dark;
			this.glyph = glyph;
		}

		/** The colour of a backend id, or null when unknown. */
		public static WireColor of(String name) {
			if (name == null) {
				return null;
			}
			try {
				return valueOf(name.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	/** Symbols drawn with rectangles, so colour is never the only signal. */
	public enum Glyph { TRIANGLE, CIRCLE, SQUARE, DIAMOND, RING, ALLY, BETRAY }

	public enum ButtonState { NORMAL, HOVER, PRESSED, DISABLED }

	public enum ButtonKind { DEFAULT, PRIMARY, DANGER }

	private TrahisonUi() {
	}

	// ---------------------------------------------------------------- layout and motion

	/** A rectangle {@code {x, y, w, h}} of at most {@code w x h} centred in the screen, never beyond the safe margins. */
	public static int[] stage(int screenW, int screenH, int w, int h) {
		int sw = Math.min(w, screenW - 2 * SAFE_MARGIN);
		int sh = Math.min(h, screenH - 2 * SAFE_MARGIN);
		return new int[] { (screenW - sw) / 2, (screenH - sh) / 2, sw, sh };
	}

	/** Number of characters of a typed line shown after {@code elapsedMs}. */
	public static int typewriter(int length, long elapsedMs) {
		return Math.min(length, Math.max(0, (int) (elapsedMs * TYPEWRITER_CPS / 1000f)));
	}

	/** Ease-out cubic, for things that appear. {@code t} is clamped to 0..1. */
	public static float easeOut(float t) {
		float k = 1f - Math.max(0f, Math.min(1f, t));
		return 1f - k * k * k;
	}

	/** Progress of an animation of {@code durationMs} that started at {@code startMs} (a {@code System.currentTimeMillis()}). */
	public static float progress(long startMs, long durationMs) {
		return Math.max(0f, Math.min(1f, (System.currentTimeMillis() - startMs) / (float) durationMs));
	}

	/** A slow 0..1..0 pulse with the given period, for waiting or urgent states. */
	public static float pulse(long periodMs) {
		return 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() % periodMs / (double) periodMs * Math.PI * 2);
	}

	public static int lerp(float t, int from, int to) {
		return FastColor.ARGB32.lerp(t, from, to);
	}

	/** {@code color} with its alpha replaced by {@code alpha} (0..255). */
	public static int withAlpha(int color, int alpha) {
		return (alpha & 0xFF) << 24 | color & 0xFFFFFF;
	}

	// ---------------------------------------------------------------- surfaces

	/** Dimmed backdrop of a modal window or a full-screen stage. */
	public static void veil(GuiGraphics g, int screenW, int screenH) {
		g.fill(0, 0, screenW, screenH, TrahisonPalette.VEIL);
	}

	/** A fine dot grid over a background, so large areas are not flat. */
	public static void dots(GuiGraphics g, int x1, int y1, int x2, int y2) {
		for (int y = y1 + 6; y < y2 - 2; y += 12) {
			for (int x = x1 + 6; x < x2 - 2; x += 12) {
				g.fill(x, y, x + 1, y + 1, TrahisonPalette.DOTS);
			}
		}
	}

	/** A raised panel: black outline, lit and shaded edges. {@code tint} colours the inner top line (0 for none). */
	public static void panel(GuiGraphics g, int x, int y, int w, int h, int tint) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TrahisonPalette.BORDER);
		g.fill(x, y, x + w, y + h, TrahisonPalette.PANEL);
		MenuUi.raised(g, x, y, x + w, y + h);
		if (tint != 0) {
			g.fill(x + 1, y + 1, x + w - 1, y + 2, tint);
		}
	}

	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		panel(g, x, y, w, h, 0);
	}

	/** A sunken well (lists, text areas, timer tracks). */
	public static void well(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TrahisonPalette.BORDER);
		g.fill(x, y, x + w, y + h, TrahisonPalette.PANEL_SUNKEN);
		MenuUi.sunken(g, x, y, x + w, y + h);
	}

	/** A panel with a 20 px title bar (gradient, accent underline). Content starts at {@code y + 24}. */
	public static void titledPanel(GuiGraphics g, int x, int y, int w, int h, Component title, int accent) {
		panel(g, x, y, w, h);
		g.fillGradient(x + 1, y + 1, x + w - 1, y + 20, TrahisonPalette.EDGE, TrahisonPalette.PANEL_RAISED);
		g.fill(x + 1, y + 20, x + w - 1, y + 21, TrahisonPalette.BORDER);
		g.fill(x + 1, y + 21, x + w - 1, y + 22, accent);
		g.fill(x + 6, y + 7, x + 11, y + 12, accent);
		MenuUi.text(g, title.copy().withStyle(s -> s.withBold(true)), x + 16, y + 6, w - 22, TrahisonPalette.TEXT);
	}

	/** A modal window centred on the screen: veil, titled panel, and the content rectangle {@code {x, y, w, h}}. */
	public static int[] modal(GuiGraphics g, int screenW, int screenH, int w, int h, Component title, int accent) {
		veil(g, screenW, screenH);
		int[] r = stage(screenW, screenH, w, h);
		titledPanel(g, r[0], r[1], r[2], r[3], title, accent);
		return new int[] { r[0] + 8, r[1] + 28, r[2] - 16, r[3] - 36 };
	}

	// ---------------------------------------------------------------- round shapes

	/** A filled disc of radius {@code r} centred on {@code cx, cy}, drawn with one rectangle per row (no texture). */
	public static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy < r; dy++) {
			double d = dy + 0.5;
			int half = (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - d * d)));
			g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
		}
	}

	/** A ring between {@code inner} and {@code outer} radii, centred on {@code cx, cy}. */
	public static void ring(GuiGraphics g, int cx, int cy, int outer, int inner, int color) {
		for (int dy = -outer; dy < outer; dy++) {
			double d = dy + 0.5;
			int ho = (int) Math.round(Math.sqrt(Math.max(0, (double) outer * outer - d * d)));
			if (Math.abs(d) >= inner) {
				g.fill(cx - ho, cy + dy, cx + ho, cy + dy + 1, color);
			} else {
				int hi = (int) Math.round(Math.sqrt((double) inner * inner - d * d));
				g.fill(cx - ho, cy + dy, cx - hi, cy + dy + 1, color);
				g.fill(cx + hi, cy + dy, cx + ho, cy + dy + 1, color);
			}
		}
	}

	/**
	 * A rectangle {@code {x1, y1, x2, y2}} (relative to a centre) rotated by {@code degrees} around {@code cx, cy}.
	 * Clockwise on screen, 0 pointing up when the rectangle sits above the centre (negative y).
	 */
	public static void rotatedFill(GuiGraphics g, int cx, int cy, float degrees, int x1, int y1, int x2, int y2, int color) {
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
		g.fill(x1, y1, x2, y2, color);
		g.pose().popPose();
	}

	// ---------------------------------------------------------------- controls

	/** The state a button is drawn in. */
	public static ButtonState stateOf(boolean enabled, boolean hovered, boolean pressed) {
		return !enabled ? ButtonState.DISABLED : pressed ? ButtonState.PRESSED : hovered ? ButtonState.HOVER : ButtonState.NORMAL;
	}

	/** A raised button; pressed shows it sunken and shifts the label by one pixel. */
	public static void button(GuiGraphics g, int x, int y, int w, int h, Component label, ButtonKind kind, ButtonState state) {
		int top;
		int bottom;
		switch (kind) {
			case PRIMARY -> {
				top = state == ButtonState.HOVER ? TrahisonPalette.ACCENT_HOT : TrahisonPalette.ACCENT;
				bottom = TrahisonPalette.ACCENT_DIM;
			}
			case DANGER -> {
				top = state == ButtonState.HOVER ? TrahisonPalette.DANGER : lerp(0.5f, TrahisonPalette.DANGER, TrahisonPalette.DANGER_DARK);
				bottom = TrahisonPalette.DANGER_DARK;
			}
			default -> {
				top = state == ButtonState.HOVER ? TrahisonPalette.EDGE_LIGHT : TrahisonPalette.EDGE;
				bottom = state == ButtonState.HOVER ? TrahisonPalette.EDGE : TrahisonPalette.PANEL_RAISED;
			}
		}
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TrahisonPalette.BORDER);
		if (state == ButtonState.DISABLED) {
			g.fill(x, y, x + w, y + h, TrahisonPalette.PANEL);
		} else if (state == ButtonState.PRESSED) {
			g.fillGradient(x, y, x + w, y + h, bottom, top);
			MenuUi.sunken(g, x, y, x + w, y + h);
		} else {
			g.fillGradient(x, y, x + w, y + h, top, bottom);
			MenuUi.raised(g, x, y, x + w, y + h);
		}
		int shift = state == ButtonState.PRESSED ? 1 : 0;
		int color = state == ButtonState.DISABLED ? TrahisonPalette.TEXT_DISABLED : kind == ButtonKind.PRIMARY ? TrahisonPalette.BORDER : TrahisonPalette.TEXT;
		MenuUi.centered(g, label, x + w / 2, y + (h - 8) / 2 + 1 + shift, w - 6, color);
	}

	/** A tab: the selected one takes the accent and joins the panel below it. */
	public static void tab(GuiGraphics g, int x, int y, int w, int h, Component label, boolean selected, boolean hovered) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TrahisonPalette.BORDER);
		if (selected) {
			g.fillGradient(x, y, x + w, y + h, TrahisonPalette.ACCENT, TrahisonPalette.ACCENT_DIM);
		} else {
			g.fillGradient(x, y, x + w, y + h, hovered ? TrahisonPalette.EDGE_LIGHT : TrahisonPalette.EDGE, TrahisonPalette.PANEL_RAISED);
		}
		MenuUi.raised(g, x, y, x + w, y + h);
		MenuUi.centered(g, label, x + w / 2, y + (h - 8) / 2 + 1, w - 6, selected ? TrahisonPalette.BORDER : TrahisonPalette.TEXT);
	}

	/** A list row: sunken, lighter when hovered; selected shows an accent bar and gradient. */
	public static void row(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean selected) {
		g.fill(x, y, x + w, y + h, hovered ? TrahisonPalette.PANEL_RAISED : TrahisonPalette.PANEL);
		MenuUi.sunken(g, x, y, x + w, y + h);
		if (selected) {
			MenuUi.gradientRight(g, x + 1, y + 1, x + w - 1, y + h - 1, TrahisonPalette.ACCENT_DIM, TrahisonPalette.PANEL);
			g.fill(x, y, x + 3, y + h, TrahisonPalette.ACCENT);
		}
	}

	/** A small rounded label (status, count, colour name), 12 px high. Returns its width. */
	public static int pill(GuiGraphics g, int x, int y, Component label, int fill, int textColor) {
		int w = MenuUi.width(label) + 10;
		g.fill(x + 1, y, x + w - 1, y + 12, TrahisonPalette.BORDER);
		g.fill(x, y + 1, x + w, y + 11, TrahisonPalette.BORDER);
		g.fill(x + 1, y + 1, x + w - 1, y + 11, fill);
		g.fill(x + 2, y + 1, x + w - 2, y + 2, TrahisonPalette.SHINE);
		MenuUi.text(g, label, x + 5, y + 2, w - 8, textColor);
		return w;
	}

	/** A pill carrying a glyph before its text, for a game colour or a vote. Returns its width. */
	public static int glyphPill(GuiGraphics g, int x, int y, Glyph glyph, Component label, int fill, int glyphColor, int textColor) {
		int w = MenuUi.width(label) + 22;
		g.fill(x + 1, y, x + w - 1, y + 12, TrahisonPalette.BORDER);
		g.fill(x, y + 1, x + w, y + 11, TrahisonPalette.BORDER);
		g.fill(x + 1, y + 1, x + w - 1, y + 11, fill);
		g.fill(x + 2, y + 1, x + w - 2, y + 2, TrahisonPalette.SHINE);
		glyph(g, glyph, x + 4, y + 2, 8, glyphColor);
		MenuUi.text(g, label, x + 16, y + 2, w - 19, textColor);
		return w;
	}

	/** A progress or timer bar: sunken track, gradient fill, {@code fraction} clamped to 0..1. */
	public static void progressBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
		well(g, x, y, w, h);
		int fill = Math.round((w - 2) * Math.max(0f, Math.min(1f, fraction)));
		if (fill > 0) {
			g.fillGradient(x + 1, y + 1, x + 1 + fill, y + h - 1, lerp(0.35f, color, TrahisonPalette.TEXT), color);
			g.fill(x + 1, y + 1, x + 1 + fill, y + 2, TrahisonPalette.SHINE);
		}
	}

	/** A hover tooltip: dark panel following the cursor, kept inside the screen. */
	public static void tooltip(GuiGraphics g, Component text, int mouseX, int mouseY, int screenW, int screenH) {
		int w = Math.min(MenuUi.width(text) + 10, 180);
		int h = MenuUi.paragraphHeight(text, w - 10) + 8;
		int x = Math.min(mouseX + 10, screenW - w - SAFE_MARGIN);
		int y = Math.min(mouseY + 10, screenH - h - SAFE_MARGIN);
		g.pose().pushPose();
		g.pose().translate(0, 0, 400);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TrahisonPalette.BORDER);
		g.fill(x, y, x + w, y + h, TrahisonPalette.BG_DEEP);
		MenuUi.outline(g, x, y, x + w, y + h, TrahisonPalette.EDGE);
		MenuUi.paragraph(g, text, x + 5, y + 4, w - 10, TrahisonPalette.TEXT);
		g.pose().popPose();
	}

	// ---------------------------------------------------------------- game widgets

	/** Width and height of {@link #bracelet}. */
	public static final int BRACELET_W = 64;
	public static final int BRACELET_H = 22;

	/**
	 * The bracelet widget: the player's colour glyph, a points counter, and a pair/solo mark. {@code color} may be null
	 * (not drawn yet), {@code pair} true for a pair, false for solo. {@code ghost} greys it out.
	 */
	public static void bracelet(GuiGraphics g, int x, int y, GameColor color, int points, boolean pair, boolean ghost) {
		int tone = ghost ? TrahisonPalette.GHOST : color == null ? TrahisonPalette.EDGE : color.base;
		int dark = ghost ? TrahisonPalette.EDGE : color == null ? TrahisonPalette.PANEL_RAISED : color.dark;
		g.fill(x - 1, y - 1, x + BRACELET_W + 1, y + BRACELET_H + 1, TrahisonPalette.BORDER);
		g.fillGradient(x, y, x + BRACELET_W, y + BRACELET_H, dark, TrahisonPalette.PANEL);
		MenuUi.raised(g, x, y, x + BRACELET_W, y + BRACELET_H);
		g.fill(x, y, x + 3, y + BRACELET_H, tone);
		if (color != null) {
			glyph(g, color.glyph, x + 7, y + 7, 8, ghost ? TrahisonPalette.GHOST : TrahisonPalette.TEXT);
		}
		bigText(g, Component.literal(Integer.toString(points)), x + 20, y + 4, 2f, TrahisonPalette.TEXT);
		// Pair: two linked dots; solo: one dot.
		int mx = x + BRACELET_W - 14;
		int my = y + BRACELET_H - 8;
		g.fill(mx, my, mx + 4, my + 4, tone);
		if (pair) {
			g.fill(mx + 4, my + 1, mx + 6, my + 3, tone);
			g.fill(mx + 6, my, mx + 10, my + 4, tone);
		}
		if (ghost) {
			g.fill(x, y, x + BRACELET_W, y + BRACELET_H, 0x60101828);
		}
	}

	/** Draws a symbol in a {@code size x size} box, {@code size} 6 to 12. */
	public static void glyph(GuiGraphics g, Glyph glyph, int x, int y, int size, int color) {
		switch (glyph) {
			case SQUARE -> g.fill(x + 1, y + 1, x + size - 1, y + size - 1, color);
			case CIRCLE -> {
				int r = size / 2;
				for (int row = 0; row < size; row++) {
					double dy = row + 0.5 - r;
					int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
					g.fill(x + r - half, y + row, x + r + half, y + row + 1, color);
				}
			}
			case DIAMOND -> {
				int r = size / 2;
				for (int row = 0; row < size; row++) {
					int half = Math.max(1, Math.min(r, (row < r ? row + 1 : size - row)));
					g.fill(x + r - half, y + row, x + r + half, y + row + 1, color);
				}
			}
			case RING -> {
				int r = size / 2;
				int inner = Math.max(1, r - 2);
				for (int row = 0; row < size; row++) {
					double dy = row + 0.5 - r;
					int outerHalf = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
					int innerHalf = Math.abs(dy) < inner ? (int) Math.round(Math.sqrt(Math.max(0, inner * inner - dy * dy))) : 0;
					if (innerHalf <= 0) {
						g.fill(x + r - outerHalf, y + row, x + r + outerHalf, y + row + 1, color);
					} else {
						g.fill(x + r - outerHalf, y + row, x + r - innerHalf, y + row + 1, color);
						g.fill(x + r + innerHalf, y + row, x + r + outerHalf, y + row + 1, color);
					}
				}
			}
			case TRIANGLE -> {
				for (int row = 0; row < size; row++) {
					int half = Math.min(size / 2, 1 + row * size / (2 * size - 2));
					g.fill(x + size / 2 - half, y + row, x + size / 2 + half, y + row + 1, color);
				}
			}
			case ALLY -> {
				int d = Math.max(3, size / 2 - 1);
				g.fill(x, y + (size - d) / 2, x + d, y + (size + d) / 2, color);
				g.fill(x + size - d, y + (size - d) / 2, x + size, y + (size + d) / 2, color);
				g.fill(x + d, y + size / 2 - 1, x + size - d, y + size / 2 + 1, color);
			}
			case BETRAY -> {
				for (int i = 0; i < size; i++) {
					g.fill(x + i, y + i, x + i + 2, y + i + 1, color);
					g.fill(x + size - 1 - i, y + i, x + size + 1 - i, y + i + 1, color);
				}
			}
		}
	}

	/** Draws a game colour's glyph in its base tone, a tiny colour chip for lists. */
	public static void colorGlyph(GuiGraphics g, GameColor color, int x, int y, int size) {
		glyph(g, color.glyph, x, y, size, color.base);
	}

	/** Draws a wire colour's glyph in its base tone. */
	public static void colorGlyph(GuiGraphics g, WireColor color, int x, int y, int size) {
		glyph(g, color.glyph, x, y, size, color.base);
	}

	// ---------------------------------------------------------------- text

	/** Text that ends with an ellipsis when it does not fit {@code maxWidth}, instead of shrinking. */
	public static void textEllipsis(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
		String raw = text.getString();
		if (MenuUi.width(text) <= maxWidth) {
			MenuUi.text(g, text, x, y, maxWidth, color);
			return;
		}
		int end = raw.length();
		while (end > 0 && MenuUi.width(Component.literal(raw.substring(0, end) + "...")) > maxWidth) {
			end--;
		}
		MenuUi.text(g, Component.literal(raw.substring(0, end) + "..."), x, y, maxWidth, color);
	}

	/** Text at an integer {@code scale} (2 for timers and points); never use fractional scales, the font blurs. */
	public static void bigText(GuiGraphics g, Component text, int x, int y, float scale, int color) {
		RenderHelperKt.drawScaledText(g, net.minecraft.resources.ResourceLocation.parse("uniform"), text.copy(), x, y, scale, 1f,
				1000, color, false, true, null, null);
	}

	/** Width of a text in the menu font. */
	public static int width(Component text) {
		return MenuUi.width(text);
	}

	/** Mouse hit test, same convention as {@link MenuUi#inside}. */
	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return MenuUi.inside(mx, my, x, y, w, h);
	}

	/** Whether the game window is in front (used to skip HUD drawing under the debug screen). */
	public static boolean hudVisible() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen();
	}
}
