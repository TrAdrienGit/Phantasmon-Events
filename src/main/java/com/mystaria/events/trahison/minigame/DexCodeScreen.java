package com.mystaria.events.trahison.minigame;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code dex_code}: the keypad of the portrait room. The code is the Pokedex numbers of the portraits, three digits each, in
 * the order of the portraits, so the digits are shown in groups of three. The portraits and the damaged Pokedex page are read in the
 * world (reading screen). Gesture and view are the keypad's ({@code {key}}, {@code {length, entry, wrong, solved}}).
 */
public final class DexCodeScreen extends KeypadScreen {

	public DexCodeScreen(String step, boolean offline) {
		super("dex_code", step, offline);
	}

	@Override
	protected String offlineCode() {
		return "025133";
	}

	@Override
	protected boolean compact() {
		return true;
	}

	@Override
	protected int bandHeight() {
		return Math.max(0, Math.min(40, contentH - 76));
	}

	@Override
	protected int groupSize() {
		return 3;
	}

	@Override
	protected void drawBand(GuiGraphics g, int x, int y, int w, int h) {
		if (h < 24) {
			return;
		}
		MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.dex_code.hint"), x + w / 2, y + 2, w, TrahisonPalette.TEXT_SECONDARY);
		MenuUi.centered(g, Component.translatable("phantasmon_events.trahison.minigame.dex_code.format"), x + w / 2, y + 16, w, TrahisonPalette.TEXT_MUTED);
	}
}
