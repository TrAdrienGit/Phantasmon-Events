package com.mystaria.events.trahison.screen;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.hud.BraceletHud;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Bracelet;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.events.trahison.world.FinalDoorFlow;
import com.mystaria.events.trahison.world.FinalDoorFlow.Prompt;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The pupitre in front of the final door (T2.5), in the charter's "window" mode: the player's bracelet, what the pupitre says
 * about it, and one big button that asks for a confirmation (opening the door ends the game: it stays open a few seconds for
 * every bracelet that reads enough, then closes for ever). Once the door is open the screen turns into the countdown: go
 * through. It opens with the cabin key when the player stands in the pupitre's zone, and closes by itself when he walks
 * away or the stage ends; closing it by hand loses nothing. The backend decides who may open it ({@code viewer.
 * can_open_final_door}) and who may pass ({@code doors_state}): no threshold is known here. Client thread.
 */
public final class FinalDoorScreen extends TrahisonScreen {

	private static final String T = "phantasmon_events.trahison.";

	/** The longest countdown seen since the door opened, so the bar empties from full. */
	private int totalSeconds;
	private long pendingAt;

	private FinalDoorScreen() {
		super(Component.translatable(T + "final_pupitre.title"), TrahisonPalette.ACCENT);
	}

	/** Opens the pupitre (the cabin key, standing at it). */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.screen instanceof FinalDoorScreen)) {
			mc.setScreen(new FinalDoorScreen());
		}
	}

	@Override
	protected void drawStage(GuiGraphics g, int mouseX, int mouseY) {
		View v = GameView.orEmpty();
		if (!FinalDoorFlow.atPupitre()) {
			onClose();
			return;
		}
		int[] r = TrahisonUi.stage(width, height, 300, 168);
		int x = r[0];
		int y = r[1];
		int w = r[2];
		int h = r[3];
		TrahisonUi.titledPanel(g, x, y, w, h, title, TrahisonPalette.ACCENT);
		Prompt state = FinalDoorFlow.prompt();
		boolean open = GameView.finalDoor() != null;
		header(g, v, x, y, w, open);

		int ix = x + 8;
		int iw = w - 16;
		int top = y + 28;
		Bracelet own = v.ownBracelet();
		if (own != null) {
			TrahisonUi.bracelet(g, ix, top, own.color(), own.points(), Boolean.TRUE.equals(own.pair()), false);
			if (own.color() != null) {
				Shown.colorPill(g, ix + TrahisonUi.BRACELET_W + 8, top + 5, own.color());
			}
		}
		int textY = top + TrahisonUi.BRACELET_H + 10;
		int used = MenuUi.paragraph(g, Component.translatable(T + "final_pupitre.lead"), ix, textY, iw, TrahisonPalette.TEXT_SECONDARY) + 6;
		int buttonY = y + h - 8 - TrahisonUi.BUTTON_H;
		int statusY = textY + used;
		if (open) {
			countdown(g, v, state, ix, statusY, iw);
		} else {
			boolean can = state == Prompt.AT_PUPITRE;
			Component text = Component.translatable(T + (can ? "final_pupitre.can" : "final_pupitre.cannot"));
			MenuUi.paragraph(g, text, ix, statusY, iw, can ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_MUTED);
		}

		ui.button(g, ix, buttonY, 84, TrahisonUi.BUTTON_H, Component.translatable(T + "votepad.back"), ButtonKind.DEFAULT,
				Component.translatable(T + "votepad.back.tip"), this::onClose);
		boolean canOpen = state == Prompt.AT_PUPITRE && !v.paused() && System.currentTimeMillis() - pendingAt > 3000;
		Component why = state != Prompt.AT_PUPITRE ? Component.translatable(T + "final.need") : v.paused() ? Component.translatable(T + "door.tip.paused") : null;
		ui.button(g, ix + iw - 160, buttonY, 160, TrahisonUi.BUTTON_H, Component.translatable(T + "final_pupitre.open"), ButtonKind.PRIMARY,
				open ? Component.translatable(T + "final_pupitre.already") : why, canOpen && !open ? this::ask : null);
	}

	private void header(GuiGraphics g, View v, int x, int y, int w, boolean open) {
		int right = x + w - 6;
		long remaining = GameView.remainingMs();
		if (remaining >= 0) {
			Component t = BraceletHud.time(remaining);
			int pw = TrahisonUi.width(t) + 10;
			int color = open || remaining < 15_000 ? TrahisonPalette.DANGER : TrahisonPalette.TEXT;
			TrahisonUi.pill(g, right - pw, y + 4, t, TrahisonPalette.PANEL_SUNKEN, color);
			right -= pw + 4;
		}
		Component state = Component.translatable(T + (open ? "final_pupitre.state_open" : "final_pupitre.state_shut"));
		int cw = TrahisonUi.width(state) + 10;
		if (right - cw > x + 80) {
			TrahisonUi.pill(g, right - cw, y + 4, state, open ? TrahisonPalette.SUCCESS_DARK : TrahisonPalette.PANEL_SUNKEN,
					open ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_SECONDARY);
		}
	}

	/** The door is open: how long, and whether it is open for him. */
	private void countdown(GuiGraphics g, View v, Prompt state, int x, int y, int w) {
		int seconds = GameView.finalDoorSeconds();
		totalSeconds = Math.max(totalSeconds, seconds);
		boolean forMe = state == Prompt.OPEN_FOR_ME;
		Component text = Component.translatable(T + (forMe ? "final_pupitre.open_for_me" : "final_pupitre.open_not_me"), seconds);
		int used = MenuUi.paragraph(g, text, x, y, w, forMe ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_MUTED) + 4;
		TrahisonUi.progressBar(g, x, y + used, w, 6, totalSeconds <= 0 ? 0f : Math.min(1f, seconds / (float) totalSeconds),
				forMe ? TrahisonPalette.SUCCESS : TrahisonPalette.DANGER);
	}

	private void ask() {
		modal = new Modal(Component.translatable(T + "final_pupitre.confirm.title"), Component.translatable(T + "final_pupitre.confirm.text"),
				Component.translatable(T + "final_pupitre.confirm.yes"), false, () -> {
					pendingAt = System.currentTimeMillis();
					Messages.openFinalDoor();
				});
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (modal == null && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
				&& FinalDoorFlow.prompt() == Prompt.AT_PUPITRE && GameView.finalDoor() == null && !GameView.orEmpty().paused()) {
			ask();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
