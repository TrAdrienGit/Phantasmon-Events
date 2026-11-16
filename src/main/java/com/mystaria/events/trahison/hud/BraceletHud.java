package com.mystaria.events.trahison.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Bracelet;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.screen.TrahisonScreen;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The game's HUD (charte-ui.md section 8): at the bottom centre, above the health and hunger rows, the player's bracelet,
 * the step's name and round, and the countdown. Nothing is drawn at the centre of the screen, nothing is clickable. A
 * ghost sees the same widget tinted {@code GHOST}; a spectator or the game master gets a compact strip (step and timer
 * only). Hidden without a game, with F1, under the debug screen, and while a screen of the game is open
 * ({@link HudSuppression}, {@link TrahisonScreen}). Also draws the {@link Announcer} (unless a game screen draws it
 * itself). Client thread.
 */
public final class BraceletHud {

	private static final String T = "phantasmon_events.trahison.";
	/** Distance from the screen's bottom to the widget's bottom: clears the hotbar, armour, health and hunger rows. */
	private static final int BOTTOM = 58;
	private static final int FULL_H = 36;
	private static final int STRIP_H = 24;
	private static final int STRIP_W = 172;
	private static final int INFO_W = 124;
	private static final float SMALL_SCALE = 0.6f;
	private static final int SMALL_W = Math.round(TrahisonUi.BRACELET_W * SMALL_SCALE);
	private static final long FLASH_MS = 400;
	private static final long DELTA_MS = 600;

	private BraceletHud() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (!TrahisonUi.hudVisible()) {
			return;
		}
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		View view = GameView.current();
		int top = sh - BOTTOM;
		boolean screenDraws = mc.screen instanceof TrahisonScreen;
		if (!screenDraws && view != null && view.hasGame() && !HudSuppression.hidden() && !com.mystaria.events.trahison.minigame.MiniGameScreen.isOpen()) {
			top = draw(g, view, sw, sh);
		}
		if (!screenDraws) {
			Announcer.render(g, sw, sh, top);
		}
	}

	/** Draws the widget fitting the viewer; returns its top edge. */
	private static int draw(GuiGraphics g, View view, int sw, int sh) {
		boolean strip = view.viewer().kind() == ViewerKind.SPECTATOR || view.viewer().kind() == ViewerKind.GM;
		Bracelet own = strip ? null : view.ownBracelet();
		boolean ghost = view.viewer().kind() == ViewerKind.GHOST;
		java.util.List<Bracelet> carried = strip || ghost ? java.util.List.of() : carriedBracelets(view);
		int carriedW = carried.size() * (SMALL_W + 2);
		int w = strip ? Math.min(STRIP_W, sw - 2 * TrahisonUi.SAFE_MARGIN) : 8 + INFO_W + carriedW + (own != null ? TrahisonUi.BRACELET_W + 8 : 0);
		int h = strip ? STRIP_H : FULL_H;
		int x = (sw - w) / 2;
		int y = sh - BOTTOM - h;
		long remaining = GameView.remainingMs();
		boolean urgent = remaining >= 0 && remaining < 15_000 && !view.paused();
		int tint = ghost ? TrahisonPalette.GHOST : view.viewer().kind() == ViewerKind.GM ? TrahisonPalette.GAME_MASTER : 0;
		TrahisonUi.panel(g, x, y, w, h, tint);
		if (urgent) {
			float pulse = TrahisonUi.pulse(800);
			MenuUi.outline(g, x - 1, y - 1, x + w + 1, y + h + 1, TrahisonUi.lerp(pulse, TrahisonPalette.DANGER_DARK, TrahisonPalette.DANGER));
		}
		int timerColor = view.paused() ? TrahisonPalette.TEXT_MUTED : remaining >= 0 && remaining < 15_000 ? TrahisonPalette.DANGER
				: remaining >= 0 && remaining < 60_000 ? TrahisonPalette.GAME_MASTER : TrahisonPalette.TEXT;
		Component phase = phaseLine(view);
		if (strip) {
			int pillW = 0;
			if (view.paused()) {
				pillW = pausedPill(g, view, x + w - 4, y + 2) + 4;
			}
			TrahisonUi.textEllipsis(g, phase, x + 6, y + 3, w - 12 - pillW - (remaining >= 0 ? TrahisonUi.width(time(remaining)) + 4 : 0), TrahisonPalette.TEXT);
			if (remaining >= 0) {
				Component t = time(remaining);
				MenuUi.text(g, t, x + w - 6 - TrahisonUi.width(t) - (view.paused() ? pillW : 0), y + 3, TrahisonUi.width(t) + 2, timerColor);
				TrahisonUi.progressBar(g, x + 6, y + 15, w - 12, 6, GameView.fraction(), barColor(remaining));
			}
			return y;
		}
		int ix = x + 8;
		if (own != null) {
			TrahisonUi.bracelet(g, ix, y + (h - TrahisonUi.BRACELET_H) / 2, own.color(), own.points(), Boolean.TRUE.equals(own.pair()), ghost);
			pointsAnimation(g, own, ix, y + (h - TrahisonUi.BRACELET_H) / 2);
			ix += TrahisonUi.BRACELET_W + 8;
			// T6.2: the bracelets picked up from bodies, smaller, beside his own, with their colour and points.
			float scale = SMALL_SCALE;
			int cy = y + (h - Math.round(TrahisonUi.BRACELET_H * scale)) / 2;
			for (Bracelet c : carried) {
				g.pose().pushPose();
				g.pose().translate(ix, cy, 0);
				g.pose().scale(scale, scale, 1f);
				TrahisonUi.bracelet(g, 0, 0, c.color(), c.points(), Boolean.TRUE.equals(c.pair()), false);
				g.pose().popPose();
				ix += SMALL_W + 2;
			}
		}
		int right = x + w - 6;
		int pillW = 0;
		if (view.paused()) {
			pillW = pausedPill(g, view, right, y + 3) + 4;
		} else if (ghost) {
			pillW = TrahisonUi.pill(g, right - (TrahisonUi.width(Component.translatable(T + "hud.ghost")) + 10), y + 3, Component.translatable(T + "hud.ghost"),
					TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.GHOST) + 4;
		}
		TrahisonUi.textEllipsis(g, phase, ix, y + 4, right - ix - pillW, TrahisonPalette.TEXT);
		if (remaining >= 0) {
			Component t = time(remaining);
			int tw = TrahisonUi.width(t) * 2;
			TrahisonUi.bigText(g, t, ix, y + 16, 2f, timerColor);
			TrahisonUi.progressBar(g, ix + tw + 6, y + 20, Math.max(8, right - ix - tw - 6), 8, GameView.fraction(), barColor(remaining));
		} else if (view.phase() == GameView.Phase.LOBBY) {
			MenuUi.text(g, Component.translatable(T + "hud.registered", view.players().size()), ix, y + 20, right - ix, TrahisonPalette.TEXT_SECONDARY);
		}
		return y;
	}

	/** The bracelets this player carries from bodies, as the view's list of them. */
	private static java.util.List<Bracelet> carriedBracelets(View view) {
		java.util.List<Bracelet> out = new java.util.ArrayList<>();
		for (int index : GameView.carried()) {
			Bracelet b = view.bracelet(index);
			if (b != null) {
				out.add(b);
			}
		}
		return out;
	}

	private static int barColor(long remaining) {
		return remaining < 15_000 ? TrahisonPalette.DANGER : remaining < 60_000 ? TrahisonPalette.GAME_MASTER : TrahisonPalette.ACCENT;
	}

	/** The paused indicator, right-aligned at {@code right}. Returns its width. */
	private static int pausedPill(GuiGraphics g, View view, int right, int y) {
		Component label = Component.translatable(T + (view.autoPaused() ? "hud.waiting" : "hud.paused"));
		int w = TrahisonUi.width(label) + 10;
		TrahisonUi.pill(g, right - w, y, label, TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER);
		return w;
	}

	/** Step name, then the round (or the final vote loop). */
	public static Component phaseLine(View view) {
		Component phase = Component.translatable(T + "phase." + view.phase().key()).withStyle(s -> s.withBold(true));
		if (view.finalLoop()) {
			return Component.translatable(T + "hud.step_final_loop", phase, Math.max(1, view.finalLoops()));
		}
		if (view.round() > 0 && view.phase() != GameView.Phase.ENDED && view.phase() != GameView.Phase.FINAL) {
			return Component.translatable(T + "hud.step_round", phase, view.round());
		}
		return phase;
	}

	/** {@code mm:ss}, or {@code h:mm:ss} past an hour. */
	public static Component time(long ms) {
		long s = Math.max(0, (ms + 999) / 1000);
		return Component.literal(s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%02d:%02d", s / 60, s % 60));
	}

	/** Flash of the changed points (ACCENT_HOT, 400 ms) and the delta rising 8 px in 600 ms. */
	private static void pointsAnimation(GuiGraphics g, Bracelet own, int bx, int by) {
		long changed = GameView.pointsChangedAt(own.index());
		if (changed == 0) {
			return;
		}
		float flash = TrahisonUi.progress(changed, FLASH_MS);
		if (flash < 1f) {
			TrahisonUi.bigText(g, Component.literal(Integer.toString(own.points())), bx + 20, by + 4, 2f,
					TrahisonUi.lerp(flash, TrahisonPalette.ACCENT_HOT, TrahisonPalette.TEXT));
		}
		float rise = TrahisonUi.progress(changed, DELTA_MS);
		if (rise < 1f) {
			int delta = GameView.lastDelta(own.index());
			Component text = Component.literal((delta > 0 ? "+" : "") + delta);
			int alpha = Math.round((1f - rise * rise) * 255);
			int color = TrahisonUi.withAlpha(delta >= 0 ? TrahisonPalette.SUCCESS : TrahisonPalette.DANGER, alpha);
			MenuUi.text(g, text, bx + TrahisonUi.BRACELET_W - TrahisonUi.width(text) - 2, by - 10 - Math.round(8 * TrahisonUi.easeOut(rise)), 40, color);
		}
	}
}
