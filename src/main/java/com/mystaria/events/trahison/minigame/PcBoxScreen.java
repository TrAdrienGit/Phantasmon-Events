package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.SpeciesCard;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code pc_box}: a PC box to tidy by a rule nobody states. The padlocked cards are already where they belong: they tell
 * the rule (Pokedex number, weight, height or name, up or down); the others are swapped by clicking one then another until the whole
 * box follows it. Every card carries what the rule can be about; the detail line under the grid repeats it for the card under the
 * mouse, for small screens.
 *
 * <p>
 * Gesture {@code {swap: [i, j]}}, never on a locked card. View {@code {order: [{species, name, dex, types, weight, height}], fixed, swaps,
 * solved}}. Offline: a six card box sorted by Pokedex number locally.
 */
public final class PcBoxScreen extends MiniGameScreen {

	private static final int GAP = 6;
	private static final List<String> OFFLINE_ANSWER = List.of("charmander", "pikachu", "onix", "eevee", "snorlax", "mew");

	private final List<Map<String, Object>> items = new ArrayList<>();
	private final Set<Integer> fixed = new HashSet<>();
	private final List<SpeciesCard> cards = new ArrayList<>();
	private int swaps;
	private int selected = -1;

	public PcBoxScreen(String step, boolean offline) {
		super("pc_box", step, offline);
		if (offline) {
			for (String s : List.of("charmander", "eevee", "onix", "snorlax", "pikachu", "mew")) {
				items.add(offlineItem(s));
			}
			fixed.addAll(List.of(0, 2, 5));
			rebuild();
		}
	}

	private static Map<String, Object> offlineItem(String s) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("species", s);
		switch (s) {
			case "charmander" -> fill(m, "Charmander", 4, 85, 6, "fire");
			case "pikachu" -> fill(m, "Pikachu", 25, 60, 4, "electric");
			case "onix" -> fill(m, "Onix", 95, 2100, 88, "rock", "ground");
			case "eevee" -> fill(m, "Eevee", 133, 65, 3, "normal");
			case "snorlax" -> fill(m, "Snorlax", 143, 4600, 21, "normal");
			default -> fill(m, "Mew", 151, 40, 4, "psychic");
		}
		return m;
	}

	private static void fill(Map<String, Object> m, String name, int dex, int weight, int height, String... types) {
		m.put("name", name);
		m.put("dex", (double) dex);
		m.put("weight", (double) weight);
		m.put("height", (double) height);
		m.put("types", List.of(types));
	}

	private void rebuild() {
		clearWidgets();
		cards.clear();
		for (int i = 0; i < items.size(); i++) {
			final int at = i;
			cards.add(add(new SpeciesCard(() -> pick(at))));
		}
		refreshCards();
		if (contentW > 0) {
			layout();
		}
	}

	private boolean tall() {
		return !cards.isEmpty() && cards.get(0).h() >= 70;
	}

	private void refreshCards() {
		boolean tall = tall();
		for (int i = 0; i < cards.size(); i++) {
			Map<String, Object> m = items.get(i);
			List<Component> lines = new ArrayList<>();
			lines.add(PokemonUi.speciesName(PuzzleScreens.asString(m.get("species"), "")));
			lines.add(Component.literal("No. %03d".formatted(PuzzleScreens.asInt(m.get("dex"), 0))));
			if (tall) {
				lines.add(types(m));
				lines.add(Component.literal(PokemonUi.weight(PuzzleScreens.asInt(m.get("weight"), 0)).getString() + "  "
						+ PokemonUi.height(PuzzleScreens.asInt(m.get("height"), 0)).getString()));
			}
			cards.get(i).species(PuzzleScreens.asString(m.get("species"), "")).lines(lines).selected(i == selected).locked(fixed.contains(i));
		}
	}

	private static Component types(Map<String, Object> m) {
		StringBuilder sb = new StringBuilder();
		for (Object t : PuzzleScreens.asList(m.get("types"))) {
			if (sb.length() > 0) {
				sb.append(" / ");
			}
			sb.append(PokemonUi.typeName(String.valueOf(t)).getString());
		}
		return Component.literal(sb.toString());
	}

	private void pick(int i) {
		if (fixed.contains(i)) {
			return;
		}
		if (selected < 0) {
			selected = i;
		} else if (selected == i) {
			selected = -1;
		} else {
			int j = selected;
			selected = -1;
			Map<String, Object> t = items.get(i);
			items.set(i, items.get(j));
			items.set(j, t);
			swaps++;
			sendGesture(Map.of("swap", List.of(j, i)));
			if (offline()) {
				List<String> now = new ArrayList<>();
				items.forEach(m -> now.add(PuzzleScreens.asString(m.get("species"), "")));
				if (now.equals(OFFLINE_ANSWER)) {
					succeed();
				}
			}
		}
		refreshCards();
	}

	private int cols() {
		return items.size() <= 4 ? Math.max(1, items.size()) : (items.size() + 1) / 2;
	}

	@Override
	protected void layout() {
		int n = cards.size();
		if (n == 0) {
			return;
		}
		int cols = cols();
		int rows = (n + cols - 1) / cols;
		int cw = Math.min(96, (contentW - 16 - (cols - 1) * GAP) / cols);
		int ch = Math.min(rows == 1 ? 120 : 84, (contentH - 8 - 14 - (rows - 1) * GAP) / rows);
		int left = contentX + (contentW - (cols * cw + (cols - 1) * GAP)) / 2;
		int top = contentY + 4;
		for (int i = 0; i < n; i++) {
			cards.get(i).at(left + (i % cols) * (cw + GAP), top + (i / cols) * (ch + GAP), cw, ch);
		}
		refreshCards();
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		List<Map<String, Object>> next = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("order"))) {
			next.add(PuzzleScreens.asMap(o));
		}
		fixed.clear();
		for (Object o : PuzzleScreens.asList(view.get("fixed"))) {
			fixed.add(PuzzleScreens.asInt(o, -1));
		}
		swaps = PuzzleScreens.asInt(view.get("swaps"), swaps);
		boolean resize = next.size() != cards.size();
		items.clear();
		items.addAll(next);
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
			for (String key : new String[] { "i", "j" }) {
				int k = PuzzleScreens.asInt(e.get(key), -1);
				if (k >= 0 && k < cards.size()) {
					cards.get(k).flash(500);
					remoteTouch(player, cards.get(k).centerX() + 14, cards.get(k).centerY() - 8);
				}
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		for (int k = 0; k < cards.size(); k++) {
			if (!fixed.contains(k) && System.currentTimeMillis() / 3200 % cards.size() == k) {
				cards.get(k).flash(400);
				remoteTouch(player, cards.get(k).centerX() + 14, cards.get(k).centerY() - 8);
			}
		}
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		int y = contentY + contentH - 12;
		int shown = selected;
		for (int i = 0; i < cards.size() && shown < 0; i++) {
			if (cards.get(i).hovered(mx, my) || TrahisonUi.inside(mx, my, cards.get(i).x(), cards.get(i).y(), cards.get(i).w(), cards.get(i).h())) {
				shown = i;
			}
		}
		if (shown >= 0 && shown < items.size()) {
			Map<String, Object> m = items.get(shown);
			Component detail = Component.literal("%s  No. %03d  %s  %s  %s".formatted(PokemonUi.speciesName(PuzzleScreens.asString(m.get("species"), "")).getString(),
					PuzzleScreens.asInt(m.get("dex"), 0), types(m).getString(), PokemonUi.weight(PuzzleScreens.asInt(m.get("weight"), 0)).getString(),
					PokemonUi.height(PuzzleScreens.asInt(m.get("height"), 0)).getString()));
			MenuUi.text(g, detail, contentX + 8, y, contentW - 120, TrahisonPalette.TEXT_SECONDARY);
		}
		Component count = Component.translatable("phantasmon_events.trahison.minigame.pc_box.swaps", swaps);
		MenuUi.text(g, count, contentX + contentW - 8 - Math.min(100, TrahisonUi.width(count)), y, 100, TrahisonPalette.TEXT_MUTED);
	}
}
