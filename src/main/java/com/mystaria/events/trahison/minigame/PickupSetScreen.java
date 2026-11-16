package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.room.Inventory;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code pickup_set}: the deposit. The objects hidden in the room are picked up in the world and brought back
 * here with the inventory's "use on" (an object the step expects is consumed; any other stays in the bag). This screen is
 * only a counter: it opens when a player uses the deposit slot with empty hands, shows how many objects are back out of
 * how many were hidden, and which ones. It sends no gesture.
 *
 * <p>
 * View: {@code {total, delivered: [id...], solved}} (the game master's view adds {@code required} and {@code filled}; this
 * screen never shows them). Event {@code {type: "deposit", item}}. Offline, the screen fakes three objects arriving one
 * after the other.
 */
public final class PickupSetScreen extends MiniGameScreen {

	private static final List<String> OFFLINE_ITEMS = List.of("fuse", "gear", "lens");
	private static final int ICON = 28;
	private static final int ICON_GAP = 8;

	private int total;
	private final List<String> delivered = new ArrayList<>();
	private boolean known;

	public PickupSetScreen(String step, boolean offline) {
		super("pickup_set", step, offline);
		if (offline) {
			total = OFFLINE_ITEMS.size();
			known = true;
		}
	}

	@Override
	protected void layout() {
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		int newTotal = Math.max(0, PuzzleScreens.asInt(view.get("total"), total));
		List<String> now = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("delivered"))) {
			now.add(String.valueOf(o));
		}
		if (known && now.size() > delivered.size() && !isSolved()) {
			MiniGameSounds.clunk();
		}
		total = newTotal;
		delivered.clear();
		delivered.addAll(now);
		known = true;
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		remoteTouch(player, contentX + contentW / 2 + 40, contentY + 30);
	}

	@Override
	protected void simulateRemote(int player) {
		if (delivered.size() < OFFLINE_ITEMS.size()) {
			delivered.add(OFFLINE_ITEMS.get(delivered.size()));
			MiniGameSounds.clunk();
			remoteTouch(player, contentX + contentW / 2 + 40, contentY + 30);
			if (delivered.size() == OFFLINE_ITEMS.size()) {
				succeed();
			}
		}
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		int cx = contentX + contentW / 2;
		Component count = Component.translatable("phantasmon_events.trahison.minigame.pickup_set.count", delivered.size(), total);
		TrahisonUi.bigText(g, count, cx - TrahisonUi.width(count), contentY + 16, 2f,
				isSolved() ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT_HOT);
		int barW = Math.min(220, contentW - 40);
		TrahisonUi.progressBar(g, cx - barW / 2, contentY + 40, barW, 8, total == 0 ? 0f : delivered.size() / (float) total,
				isSolved() ? TrahisonPalette.SUCCESS : TrahisonPalette.ACCENT);
		// One square per object of the set: the delivered ones show their picture, the others stay empty.
		int slots = Math.max(1, total);
		int perRow = Math.max(1, Math.min(slots, (contentW - 24 + ICON_GAP) / (ICON + ICON_GAP)));
		int rows = (slots + perRow - 1) / perRow;
		int top = contentY + 62;
		for (int i = 0; i < slots; i++) {
			int col = i % perRow;
			int inRow = Math.min(perRow, slots - (i / perRow) * perRow);
			int rowW = inRow * ICON + (inRow - 1) * ICON_GAP;
			int x = cx - rowW / 2 + col * (ICON + ICON_GAP);
			int y = top + (i / perRow) * (ICON + ICON_GAP);
			boolean filled = i < delivered.size();
			g.fill(x - 1, y - 1, x + ICON + 1, y + ICON + 1, filled ? TrahisonPalette.SUCCESS_DARK : TrahisonPalette.BORDER);
			g.fill(x, y, x + ICON, y + ICON, TrahisonPalette.PANEL_SUNKEN);
			if (filled) {
				Inventory.drawIcon(g, delivered.get(i), x + 2, y + 2, ICON - 4);
			} else {
				g.fill(x + ICON / 2 - 3, y + ICON / 2 - 1, x + ICON / 2 + 3, y + ICON / 2 + 1, TrahisonPalette.TEXT_DISABLED);
			}
		}
		Component hint = Component.translatable("phantasmon_events.trahison.minigame.pickup_set.hint");
		int hintY = top + rows * (ICON + ICON_GAP) + 4;
		MenuUi.paragraph(g, hint, contentX + 16, Math.min(hintY, contentY + contentH - 24), contentW - 32, TrahisonPalette.TEXT_MUTED);
	}
}
