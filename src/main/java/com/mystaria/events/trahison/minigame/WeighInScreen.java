package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.SpeciesCard;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code weigh_in}: pedestals to put in order, lightest first (or smallest first, the view's {@code by}). Click a pedestal,
 * then another: they swap places. The measures are on the scale sheet of the room (a document), not here.
 *
 * <p>
 * Gesture {@code {swap: [i, j]}}, applied on screen at once. View {@code {order, swaps, solved, by}}; {@code order} is the species ids
 * left to right. Offline: four pedestals sorted by weight locally.
 */
public final class WeighInScreen extends MiniGameScreen {

	private static final List<String> OFFLINE_ORDER = List.of("snorlax", "pikachu", "onix", "eevee");
	private static final List<String> OFFLINE_ANSWER = List.of("pikachu", "eevee", "onix", "snorlax");
	private static final int GAP = 6;

	private final List<String> order = new ArrayList<>();
	private final List<SpeciesCard> cards = new ArrayList<>();
	private String by = "weight";
	private int swaps;
	private int selected = -1;

	public WeighInScreen(String step, boolean offline) {
		super("weigh_in", step, offline);
		if (offline) {
			order.addAll(OFFLINE_ORDER);
			rebuild();
		}
	}

	private void rebuild() {
		clearWidgets();
		cards.clear();
		for (int i = 0; i < order.size(); i++) {
			final int at = i;
			cards.add(add(new SpeciesCard(() -> pick(at))));
		}
		refreshCards();
		if (contentW > 0) {
			layout();
		}
	}

	private void refreshCards() {
		for (int i = 0; i < cards.size(); i++) {
			cards.get(i).species(order.get(i)).selected(i == selected).lines(List.of(PokemonUi.speciesName(order.get(i))));
		}
	}

	private void pick(int i) {
		if (selected < 0) {
			selected = i;
		} else if (selected == i) {
			selected = -1;
		} else {
			int j = selected;
			selected = -1;
			String t = order.get(i);
			order.set(i, order.get(j));
			order.set(j, t);
			swaps++;
			sendGesture(Map.of("swap", List.of(j, i)));
			if (offline() && order.equals(OFFLINE_ANSWER)) {
				succeed();
			}
		}
		refreshCards();
	}

	@Override
	protected void layout() {
		int n = cards.size();
		if (n == 0) {
			return;
		}
		int cw = Math.min(84, (contentW - 16 - (n - 1) * GAP) / n);
		int ch = Math.min(110, contentH - 40);
		int left = contentX + (contentW - (n * cw + (n - 1) * GAP)) / 2;
		int top = contentY + 18 + Math.max(0, (contentH - 40 - ch) / 2);
		for (int i = 0; i < n; i++) {
			cards.get(i).at(left + i * (cw + GAP), top, cw, ch);
		}
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		List<String> next = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("order"))) {
			next.add(String.valueOf(o));
		}
		by = PuzzleScreens.asString(view.get("by"), by);
		swaps = PuzzleScreens.asInt(view.get("swaps"), swaps);
		boolean resize = next.size() != cards.size();
		order.clear();
		order.addAll(next);
		if (resize) {
			selected = -1;
			rebuild();
		} else {
			refreshCards();
		}
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			if ("swap".equals(PuzzleScreens.asString(e.get("type"), ""))) {
				int i = PuzzleScreens.asInt(e.get("i"), -1);
				int j = PuzzleScreens.asInt(e.get("j"), -1);
				for (int k : new int[] { i, j }) {
					if (k >= 0 && k < cards.size()) {
						cards.get(k).flash(500);
						remoteTouch(player, cards.get(k).centerX() + 14, cards.get(k).centerY() - 8);
					}
				}
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		if (!cards.isEmpty()) {
			SpeciesCard c = cards.get((int) (System.currentTimeMillis() / 3200 % cards.size()));
			c.flash(400);
			remoteTouch(player, c.centerX() + 14, c.centerY() - 8);
		}
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		boolean height = "height".equals(by);
		int y = contentY + 4;
		MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.weigh_in." + (height ? "smallest" : "lightest")), contentX + 8, y, 100, TrahisonPalette.TEXT_SECONDARY);
		Component right = Component.translatable("phantasmon_events.trahison.minigame.weigh_in." + (height ? "tallest" : "heaviest"));
		MenuUi.text(g, right, contentX + contentW - 8 - TrahisonUi.width(right), y, 100, TrahisonPalette.TEXT_SECONDARY);
		int ay = y + 4;
		int x1 = contentX + 8 + 70;
		int x2 = contentX + contentW - 8 - 70;
		if (x2 > x1 + 20) {
			g.fill(x1, ay, x2, ay + 1, TrahisonPalette.EDGE);
			g.fill(x2 - 3, ay - 2, x2 - 2, ay + 3, TrahisonPalette.EDGE);
			g.fill(x2 - 2, ay - 1, x2 - 1, ay + 2, TrahisonPalette.EDGE);
		}
		MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.weigh_in.swaps", swaps), contentX + 8, contentY + contentH - 12, 120, TrahisonPalette.TEXT_MUTED);
		if (selected >= 0) {
			Component hint = Component.translatable("phantasmon_events.trahison.minigame.weigh_in.pick_other");
			MenuUi.text(g, hint, contentX + contentW - 8 - Math.min(180, TrahisonUi.width(hint)), contentY + contentH - 12, 180, TrahisonPalette.ACCENT_HOT);
		}
	}
}
