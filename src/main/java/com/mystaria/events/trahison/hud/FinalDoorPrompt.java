package com.mystaria.events.trahison.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.Trahison;
import com.mystaria.events.trahison.minigame.MiniGameScreen;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.screen.TrahisonScreen;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.world.FinalDoorFlow;
import com.mystaria.events.trahison.world.FinalDoorFlow.Prompt;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The one-line prompt of the final door (T2.5), just above the bracelet widget like the cabins' one (charter, section 8:
 * nothing at the centre of the screen): go to the pupitre, open it, go through while the door is open, or why the door does not
 * take you. Nothing is clickable. Hidden with F1, under the debug screen and while a game screen is open. Client thread.
 */
public final class FinalDoorPrompt {

	private static final String T = "phantasmon_events.trahison.";
	/** Bottom of the bracelet widget (58) plus its height (36) plus a gap. */
	private static final int BOTTOM = 58 + 36 + 6;
	private static final int H = 16;

	private FinalDoorPrompt() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static final int[] CALLS = { 5, 10, 30 };
	/** Seconds left at the previous frame, to say each call of the final countdown once (client display only). */
	private static int lastSeconds = Integer.MAX_VALUE;

	/** D18: the hostess calls the final countdown for everyone, at 30, 10 and 5 seconds. */
	private static void announceCountdown() {
		if (GameView.finalDoor() == null) {
			lastSeconds = Integer.MAX_VALUE;
			return;
		}
		int seconds = GameView.finalDoorSeconds();
		for (int mark : CALLS) {
			if (seconds <= mark && lastSeconds > mark) {
				Announcer.say(Component.translatable(T + "say.final_countdown", Math.max(1, seconds)));
				break;
			}
		}
		lastSeconds = seconds;
	}

	private static void render(GuiGraphics g) {
		announceCountdown();
		Minecraft mc = Minecraft.getInstance();
		if (!TrahisonUi.hudVisible() || mc.screen instanceof TrahisonScreen || HudSuppression.hidden() || MiniGameScreen.isOpen()) {
			return;
		}
		Prompt prompt = FinalDoorFlow.prompt();
		if (prompt == Prompt.NONE) {
			return;
		}
		Component key = Trahison.CABIN_KEY.getTranslatedKeyMessage();
		int seconds = GameView.finalDoorSeconds();
		Component text = switch (prompt) {
			case GO -> Component.translatable(T + "final.prompt.go");
			case AT_PUPITRE -> Component.translatable(T + "final.prompt.pupitre", key);
			case CANNOT -> Component.translatable(T + "final.prompt.cannot");
			case OPEN_FOR_ME -> Component.translatable(T + "final.prompt.open_for_me", seconds);
			case OPEN_NOT_ME -> Component.translatable(T + "final.prompt.open_not_me", seconds);
			case COUNTDOWN -> Component.translatable(T + "final.prompt.countdown", seconds);
			default -> Component.empty();
		};
		int color = switch (prompt) {
			case AT_PUPITRE, GO -> TrahisonPalette.ACCENT;
			case OPEN_FOR_ME -> TrahisonPalette.SUCCESS;
			case CANNOT, OPEN_NOT_ME -> TrahisonPalette.DANGER;
			case COUNTDOWN -> TrahisonPalette.ACCENT;
			default -> TrahisonPalette.TEXT_SECONDARY;
		};
		int w = Math.min(g.guiWidth() - 2 * TrahisonUi.SAFE_MARGIN, TrahisonUi.width(text) + 20);
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - BOTTOM - H;
		TrahisonUi.panel(g, x, y, w, H);
		if (prompt == Prompt.AT_PUPITRE || prompt == Prompt.GO || prompt == Prompt.OPEN_FOR_ME) {
			g.fill(x + 1, y + 2, x + 3, y + H - 2, color);
		}
		MenuUi.text(g, text, x + 10, y + 4, w - 14, color);
	}
}
