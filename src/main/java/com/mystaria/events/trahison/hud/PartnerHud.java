package com.mystaria.events.trahison.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.Glyphs;
import com.mystaria.events.trahison.minigame.MiniGameScreen;
import com.mystaria.events.trahison.minigame.PokemonUi;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.room.Powers;
import com.mystaria.events.trahison.room.RoomView;
import com.mystaria.events.trahison.room.RoomView.Partner;
import com.mystaria.events.trahison.room.Rooms;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.world.PowerWorld;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * T8.3: the HUD of the partner Pokemon and of the dark zones. At the top left, a small card with the partner's name and its power (symbol
 * and name, never the colour alone); the dark veil of a {@code FLASH} zone over the whole screen, with the cause told in one line at the
 * top. Hidden with F1, under a screen, without a game and for anybody but a living player. Client thread.
 */
public final class PartnerHud {

	private static final String T = "phantasmon_events.trahison.power.";
	private static final int CARD_H = 16;
	private static final int CARD_W = 124;

	private PartnerHud() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		View view = GameView.current();
		if (!TrahisonUi.hudVisible() || view == null || !view.hasGame() || mc.screen != null || HudSuppression.hidden() || MiniGameScreen.isOpen()) {
			return;
		}
		int own = Rooms.ownIndex();
		RoomView room = Rooms.mine();
		if (own < 0 || room == null) {
			return;
		}
		int veil = PowerWorld.veil();
		if (veil > 0) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), TrahisonUi.withAlpha(TrahisonPalette.BG_DEEP, veil));
			Component line = Component.translatable(T + "dark");
			int w = Math.min(g.guiWidth() - 2 * TrahisonUi.SAFE_MARGIN, TrahisonUi.width(line) + 16);
			int x = (g.guiWidth() - w) / 2;
			TrahisonUi.panel(g, x, TrahisonUi.SAFE_MARGIN, w, 16, TrahisonPalette.YELLOW_DARK);
			MenuUi.text(g, line, x + 8, TrahisonUi.SAFE_MARGIN + 4, w - 12, TrahisonPalette.TEXT);
		}
		Partner p = room.partnerOf(own);
		if (p != null) {
			card(g, p);
		}
	}

	private static void card(GuiGraphics g, Partner p) {
		int x = TrahisonUi.SAFE_MARGIN;
		int y = TrahisonUi.SAFE_MARGIN;
		int w = Math.min(CARD_W, g.guiWidth() - 2 * TrahisonUi.SAFE_MARGIN);
		TrahisonUi.panel(g, x, y, w, CARD_H, TrahisonPalette.PANEL);
		Glyphs.draw(g, PokemonUi.typeGlyph(Powers.type(p.power())), x + 9, y + 8, 10, 0, Powers.color(p.power()));
		Component text = Component.translatable(T + "card", PokemonUi.speciesName(p.species()), Powers.name(p.power()));
		MenuUi.text(g, text, x + 18, y + 4, w - 22, TrahisonPalette.TEXT);
	}
}
