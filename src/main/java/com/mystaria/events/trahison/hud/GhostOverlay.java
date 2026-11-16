package com.mystaria.events.trahison.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * T6.3: what a ghost sees of himself: a light grey veil over the whole screen and the reminder "you are a ghost" at the top
 * (nothing at the centre). Only for a dead player of a running game; spectators and game masters get neither. Client thread.
 */
public final class GhostOverlay {

	/** A light grey, barely opaque: the world stays readable. */
	private static final int VEIL = 0x30808080;

	private GhostOverlay() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics g) {
		View v = GameView.current();
		if (!TrahisonUi.hudVisible() || v == null || !v.hasGame() || v.viewer().kind() != ViewerKind.GHOST || v.phase() == GameView.Phase.ENDED
				|| Minecraft.getInstance().screen != null) {
			return;
		}
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), VEIL);
		Component text = Component.translatable("phantasmon_events.trahison.ghost.reminder");
		int w = Math.min(g.guiWidth() - 2 * TrahisonUi.SAFE_MARGIN, TrahisonUi.width(text) + 20);
		int x = (g.guiWidth() - w) / 2;
		TrahisonUi.panel(g, x, 8, w, 16, TrahisonPalette.GHOST);
		MenuUi.text(g, text, x + 10, 12, w - 14, TrahisonPalette.TEXT);
	}
}
