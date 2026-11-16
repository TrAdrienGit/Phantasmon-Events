package com.mystaria.events.trahison.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.Trahison;
import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.minigame.MiniGameScreen;
import com.mystaria.events.trahison.screen.TrahisonScreen;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.world.CabinFlow;
import com.mystaria.events.trahison.world.CabinFlow.Prompt;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The one-line prompt of the vote cabins (T2.4), just above the bracelet widget (charter, section 8: nothing at the centre
 * of the screen): go to your cabin, close it, open the pupitre, or why you cannot. Nothing is clickable. Hidden with F1,
 * under the debug screen and while a game screen is open. Client thread.
 */
public final class CabinPrompt {

	private static final String T = "phantasmon_events.trahison.";
	/** Bottom of the bracelet widget (58) plus its height (36) plus a gap. */
	private static final int BOTTOM = 58 + 36 + 6;
	private static final int H = 16;

	private CabinPrompt() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (!TrahisonUi.hudVisible() || mc.screen instanceof TrahisonScreen || HudSuppression.hidden() || MiniGameScreen.isOpen()) {
			return;
		}
		Prompt prompt = CabinFlow.prompt();
		if (prompt == Prompt.NONE) {
			return;
		}
		LayoutDto.Cabin mine = CabinFlow.myCabin();
		Component key = Trahison.CABIN_KEY.getTranslatedKeyMessage();
		Component text = switch (prompt) {
			case GO -> Component.translatable(T + "cabin.prompt.go", mine == null ? "?" : CabinFlow.cabinName(mine));
			case WRONG -> Component.translatable(T + "cabin.prompt.wrong", mine == null ? "?" : CabinFlow.cabinName(mine));
			case CLOSE -> Component.translatable(T + "cabin.prompt.close", key);
			case VOTE -> Component.translatable(T + "cabin.prompt.vote", key);
			case VOTED -> Component.translatable(T + "cabin.prompt.voted", key);
			case TAKEN -> Component.translatable(T + "cabin.prompt.taken");
			default -> Component.empty();
		};
		int color = switch (prompt) {
			case CLOSE, VOTE -> TrahisonPalette.ACCENT;
			case WRONG -> TrahisonPalette.DANGER;
			case VOTED -> TrahisonPalette.SUCCESS;
			default -> TrahisonPalette.TEXT_SECONDARY;
		};
		int w = Math.min(g.guiWidth() - 2 * TrahisonUi.SAFE_MARGIN, TrahisonUi.width(text) + 20);
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - BOTTOM - H;
		TrahisonUi.panel(g, x, y, w, H);
		if (prompt == Prompt.CLOSE || prompt == Prompt.VOTE) {
			g.fill(x + 1, y + 2, x + 3, y + H - 2, TrahisonPalette.ACCENT);
		}
		MenuUi.text(g, text, x + 10, y + 4, w - 14, color);
	}
}
