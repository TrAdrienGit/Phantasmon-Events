package com.mystaria.events.trahison.room;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.hud.HudSuppression;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.room.RoomView.ItemView;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The item bar (T3.3): a column at the left edge, vertically centred (the bottom of the screen belongs to the hotbar and the
 * bracelet widget, the centre to the crosshair), listing what the player carries in the room he is in. The item he holds (selected
 * with the cycle key, or in the inventory screen) is lit and named: a click on an object of the world then uses it. Nothing here
 * is clickable and nothing is kept: it shows {@link Inventory#mine()}. Hidden with F1, under a screen, and without items.
 * Client thread.
 */
public final class ItemBar {

	private static final int CELL = 22;
	private static final int GAP = 2;
	private static final int MAX = 9;

	private ItemBar() {
	}

	static void register() {
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		View view = GameView.current();
		boolean trial = com.mystaria.events.trahison.edit.RoomTry.active();
		if (mc.screen != null || !trial && (view == null || !view.hasGame()) || !TrahisonUi.hudVisible() || HudSuppression.hidden()
				|| com.mystaria.events.trahison.minigame.MiniGameScreen.isOpen()) {
			return;
		}
		List<ItemView> items = Inventory.mine();
		if (items.isEmpty()) {
			return;
		}
		int shown = Math.min(MAX, items.size());
		int h = shown * (CELL + GAP) - GAP + 8;
		int w = CELL + 8;
		int x = TrahisonUi.SAFE_MARGIN;
		int y = (g.guiHeight() - h) / 2;
		TrahisonUi.panel(g, x, y, w, h);
		String held = Inventory.held();
		for (int i = 0; i < shown; i++) {
			ItemView item = items.get(i);
			int cy = y + 4 + i * (CELL + GAP);
			boolean on = item.id().equals(held);
			TrahisonUi.well(g, x + 4, cy, CELL, CELL);
			if (on) {
				MenuUi.outline(g, x + 3, cy - 1, x + 4 + CELL + 1, cy + CELL + 1, TrahisonPalette.ACCENT);
			}
			Inventory.drawIcon(g, item.id(), x + 6, cy + 2, CELL - 4);
		}
		if (items.size() > shown) {
			MenuUi.text(g, Component.literal("+" + (items.size() - shown)), x + 6, y + h + 3, 40, TrahisonPalette.TEXT_MUTED);
		}
		if (held != null) {
			int tx = x + w + 6;
			int ty = y + h / 2 - 12;
			Component name = Inventory.name(held);
			int tw = MenuUi.width(name) + 12;
			TrahisonUi.panel(g, tx, ty, tw, 24, TrahisonPalette.ACCENT);
			MenuUi.text(g, name, tx + 6, ty + 8, tw - 8, TrahisonPalette.TEXT);
		}
		Component hint = Component.translatable("phantasmon_events.trahison.itembar.hint", Inventory.KEY.getTranslatedKeyMessage(),
				Inventory.CYCLE_KEY.getTranslatedKeyMessage());
		MenuUi.text(g, hint, x, y - 12, 160, TrahisonPalette.TEXT_MUTED);
	}
}
