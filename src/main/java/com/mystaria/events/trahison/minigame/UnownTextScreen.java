package com.mystaria.events.trahison.minigame;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code unown_text}: the keypad of the Zarbi wall. A sentence in the Zarbi alphabet spells the code in digit words; the
 * screen shows the sentence the player read on the wall ({@link PokemonUi#read}), drawn with {@link Zarbi}, above the keypad, and the
 * alphabet key stays a document of the room. Gesture and view are the keypad's. Offline: a test sentence.
 */
public final class UnownTextScreen extends KeypadScreen {

	public static final String ROLE = "wall_text";
	private static final String OFFLINE_TEXT = "THE DOOR OPENS WITH ONE TWO THREE";
	private static final int[] SIZES = { 16, 12, 10 };

	public UnownTextScreen(String step, boolean offline) {
		super("unown_text", step, offline);
	}

	@Override
	protected String offlineCode() {
		return "123";
	}

	@Override
	protected boolean compact() {
		return true;
	}

	@Override
	protected int bandHeight() {
		return Math.max(0, contentH - 70);
	}

	private String sentence() {
		return offline() ? OFFLINE_TEXT : PokemonUi.read(ROLE);
	}

	@Override
	protected void drawBand(GuiGraphics g, int x, int y, int w, int h) {
		TrahisonUi.well(g, x - 4, y - 2, w + 8, h - 2);
		String text = sentence();
		if (text == null || text.isBlank()) {
			MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.unown_text.unread"), x + w / 2, y + h / 2 - 6, w - 8, TrahisonPalette.TEXT_MUTED);
			return;
		}
		int size = Zarbi.fit(text, SIZES, w - 8, h - 8);
		int th = Zarbi.height(text, size, w - 8);
		Zarbi.drawText(g, text, x + 4, y + Math.max(2, (h - 4 - th) / 2), w - 8, size, TrahisonPalette.ACCENT_HOT);
	}
}
