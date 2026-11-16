package com.mystaria.events.trahison.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.MiniGameScreen;
import com.mystaria.events.trahison.screen.TrahisonScreen;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.world.BodyEntity;
import com.mystaria.events.trahison.world.Bodies;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * T6.2: the one-line prompt shown while the crosshair aims at a body whose bracelet is still on it: right click takes it. Drawn
 * above the bracelet widget like the other prompts (nothing at the centre of the screen). Client thread.
 */
public final class BodyPrompt {

	private static final String T = "phantasmon_events.trahison.";
	/** Bracelet widget (58 + 36) plus the room of the other prompts. */
	private static final int BOTTOM = 58 + 36 + 6 + 20;
	private static final int H = 16;

	private BodyPrompt() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (!TrahisonUi.hudVisible() || mc.screen instanceof TrahisonScreen || HudSuppression.hidden() || MiniGameScreen.isOpen()) {
			return;
		}
		BodyEntity body = Bodies.aimed();
		if (body == null || !Bodies.canTake(body)) {
			return;
		}
		Component text = Component.translatable(T + "body.prompt", body.getGameProfile().getName());
		int w = Math.min(g.guiWidth() - 2 * TrahisonUi.SAFE_MARGIN, TrahisonUi.width(text) + 20);
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - BOTTOM - H;
		TrahisonUi.panel(g, x, y, w, H);
		g.fill(x + 1, y + 2, x + 3, y + H - 2, TrahisonPalette.ACCENT);
		MenuUi.text(g, text, x + 10, y + 4, w - 14, TrahisonPalette.ACCENT);
	}
}
