package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.Widget;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code battle_puzzle}: the tactical duel. Two teams (the group's on the left, the opponent's on the right) with their models
 * and hit points, the attacks of the active Pokemon as buttons (a type symbol, the type's name and the power, never a game name), the
 * reserve Pokemon as buttons to switch to, the attack the opponent will use next, the turn counter, and the part of the type table
 * the duel needs. The backend plays the duel (the screen holds no rule): a click sends the gesture and the next view shows the board;
 * a failed attempt puts the starting position back and counts a fail. The winning sequence is never here.
 *
 * <p>
 * Gesture {@code {move: i}} or {@code {switch: j}}. View {@code {player, enemy, player_hp, enemy_hp, player_active, enemy_active,
 * enemy_next_move, turns, turn, fails, solved, table}}; events {@code turn}, {@code fail}, {@code unlock} with {@code log} lines
 * ({@code P hit 20}). Offline: a fixed board, the buttons only click (the duel is the backend's).
 */
public final class BattlePuzzleScreen extends MiniGameScreen {

	private static final int HEADER = 12;
	private static final int ACTIONS_H = 22;

	private record Move(String type, int power, boolean first) {
	}

	private record Mon(String species, List<String> types, int hp, List<Move> moves) {
	}

	/** A button of the screen, either an attack ({@code move >= 0}) or a switch ({@code swap >= 0}). */
	private final class ActionButton extends Widget {
		private final int move;
		private final int swap;
		private boolean down;

		ActionButton(int move, int swap) {
			this.move = move;
			this.swap = swap;
		}

		private boolean usable() {
			if (isSolved() || playerHp.isEmpty()) {
				return false;
			}
			int target = reserve(swap);
			if (move >= 0) {
				return player.size() > playerActive && move < player.get(playerActive).moves().size();
			}
			return target >= 0 && target < playerHp.size() && playerHp.get(target) > 0;
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (button != 0 || !hovered(mx, my) || !usable()) {
				return false;
			}
			down = true;
			MiniGameSounds.click();
			return true;
		}

		@Override
		public boolean mouseReleased(double mx, double my, int button) {
			if (!down) {
				return false;
			}
			down = false;
			if (hovered(mx, my)) {
				sendGesture(Map.of(move >= 0 ? "move" : "switch", move >= 0 ? move : reserve(swap)));
			}
			return true;
		}

		@Override
		public void render(GuiGraphics g, int mx, int my) {
			boolean ok = usable() && enabled;
			boolean hot = hovered(mx, my) && ok;
			if (move >= 0) {
				Mon mon = player.size() > playerActive ? player.get(playerActive) : null;
				Move m = mon != null && move < mon.moves().size() ? mon.moves().get(move) : null;
				if (m != null) {
					TrahisonUi.button(g, x, y, w, h, Component.empty(), ButtonKind.PRIMARY, TrahisonUi.stateOf(ok, hot, down && hot));
					int ty = y + (h - 8) / 2 + (down && hot ? 1 : 0);
					Glyphs.draw(g, PokemonUi.typeGlyph(m.type()), x + 8, y + h / 2f, 10, 0, ok ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_DISABLED);
					Component label = Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.attack", PokemonUi.typeName(m.type()), m.power());
					TrahisonUi.textEllipsis(g, label, x + 16, ty, w - 18 - (m.first() ? 10 : 0), ok ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_DISABLED);
					if (m.first()) {
						TrahisonUi.glyph(g, TrahisonUi.Glyph.TRIANGLE, x + w - 11, y + h / 2 - 3, 7, TrahisonPalette.ACCENT_HOT);
					}
				}
			} else {
				int target = reserve(swap);
				if (target < 0 || target >= player.size()) {
					return;
				}
				Mon mon = player.get(target);
				Component label = Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.switch",
						PokemonUi.speciesName(mon.species()), target < playerHp.size() ? playerHp.get(target) : 0);
				TrahisonUi.button(g, x, y, w, h, label, ButtonKind.DEFAULT, TrahisonUi.stateOf(ok, hot, down && hot));
			}
		}

		@Override
		public Component tooltip(double mx, double my) {
			if (!hovered(mx, my) || move < 0 || player.size() <= playerActive || move >= player.get(playerActive).moves().size()) {
				return null;
			}
			return player.get(playerActive).moves().get(move).first() ? Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.first") : null;
		}
	}

	/** The team index of the k-th reserve of the player's team (every Pokemon but the active one), or -1. */
	private int reserve(int k) {
		int seen = 0;
		for (int i = 0; i < player.size(); i++) {
			if (i != playerActive) {
				if (seen == k) {
					return i;
				}
				seen++;
			}
		}
		return -1;
	}

	private final List<Mon> player = new ArrayList<>();
	private final List<Mon> enemy = new ArrayList<>();
	private final List<Integer> playerHp = new ArrayList<>();
	private final List<Integer> enemyHp = new ArrayList<>();
	private final List<ActionButton> moveButtons = new ArrayList<>();
	private final List<ActionButton> switchButtons = new ArrayList<>();
	private final List<Component> log = new ArrayList<>();
	private final List<String> tableLines = new ArrayList<>();
	private int playerActive;
	private int enemyActive;
	private int enemyNext;
	private int turns = 3;
	private int turn;
	private int fails = -1;

	public BattlePuzzleScreen(String step, boolean offline) {
		super("battle_puzzle", step, offline);
		for (int i = 0; i < 4; i++) {
			moveButtons.add(add(new ActionButton(i, -1)));
		}
		for (int i = 0; i < 3; i++) {
			switchButtons.add(add(new ActionButton(-1, i)));
		}
		if (offline) {
			offlineBoard();
		}
	}

	private void offlineBoard() {
		player.add(new Mon("pikachu", List.of("electric"), 40, List.of(new Move("electric", 25, false), new Move("normal", 20, false), new Move("fire", 15, true))));
		player.add(new Mon("charmander", List.of("fire"), 30, List.of(new Move("fire", 30, false), new Move("normal", 15, false), new Move("grass", 10, false))));
		enemy.add(new Mon("squirtle", List.of("water"), 40, List.of(new Move("water", 20, false), new Move("normal", 15, false), new Move("ice", 10, false))));
		enemy.add(new Mon("bulbasaur", List.of("grass", "poison"), 30, List.of(new Move("grass", 25, false), new Move("poison", 15, false), new Move("normal", 10, false))));
		playerHp.addAll(List.of(40, 30));
		enemyHp.addAll(List.of(40, 30));
		enemyNext = 0;
		turns = 3;
		turn = 1;
		tableLines.add("fire: grass 2, ice 2, water 1/2");
		tableLines.add("water: fire 2, grass 1/2");
		tableLines.add("electric: water 2, grass 1/2");
		fails = 0;
	}

	// ---------------------------------------------------------------- layout

	private boolean small() {
		return contentH < 140;
	}

	private int sideW() {
		return Math.max(80, Math.min(128, (contentW - 90) / 2));
	}

	private int bodyTop() {
		return contentY + HEADER + 2;
	}

	private int actionsY() {
		return contentY + contentH - ACTIONS_H - 4;
	}

	@Override
	protected void layout() {
		int ay = actionsY();
		int n = 4;
		int gap = 4;
		int bw = (contentW - 16 - (n - 1) * gap) / n;
		for (int i = 0; i < n; i++) {
			moveButtons.get(i).at(contentX + 8 + i * (bw + gap), ay, bw, ACTIONS_H);
		}
		// the reserve chips sit under the player's active Pokemon
		int top = bodyTop() + 40 + 2;
		for (int i = 0; i < switchButtons.size(); i++) {
			switchButtons.get(i).at(contentX + 6, top + i * 14, sideW(), 13);
		}
	}

	// ---------------------------------------------------------------- sync

	private static Mon mon(Map<String, Object> m) {
		List<String> types = new ArrayList<>();
		for (Object t : PuzzleScreens.asList(m.get("types"))) {
			types.add(String.valueOf(t));
		}
		List<Move> moves = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(m.get("moves"))) {
			Map<String, Object> mv = PuzzleScreens.asMap(o);
			moves.add(new Move(PuzzleScreens.asString(mv.get("type"), "normal"), PuzzleScreens.asInt(mv.get("power"), 0), Boolean.TRUE.equals(mv.get("priority"))));
		}
		return new Mon(PuzzleScreens.asString(m.get("species"), ""), types, PuzzleScreens.asInt(m.get("hp"), 0), moves);
	}

	private static void ints(Object list, List<Integer> out) {
		out.clear();
		for (Object o : PuzzleScreens.asList(list)) {
			out.add(PuzzleScreens.asInt(o, 0));
		}
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		player.clear();
		enemy.clear();
		for (Object o : PuzzleScreens.asList(view.get("player"))) {
			player.add(mon(PuzzleScreens.asMap(o)));
		}
		for (Object o : PuzzleScreens.asList(view.get("enemy"))) {
			enemy.add(mon(PuzzleScreens.asMap(o)));
		}
		ints(view.get("player_hp"), playerHp);
		ints(view.get("enemy_hp"), enemyHp);
		playerActive = PuzzleScreens.asInt(view.get("player_active"), 0);
		enemyActive = PuzzleScreens.asInt(view.get("enemy_active"), 0);
		enemyNext = PuzzleScreens.asInt(view.get("enemy_next_move"), 0);
		turns = PuzzleScreens.asInt(view.get("turns"), turns);
		turn = PuzzleScreens.asInt(view.get("turn"), 0) + 1;
		int f = PuzzleScreens.asInt(view.get("fails"), 0);
		if (fails >= 0 && f > fails) {
			fail();
		}
		fails = f;
		tableLines.clear();
		for (Map.Entry<String, Object> row : PuzzleScreens.asMap(view.get("table")).entrySet()) {
			StringBuilder sb = new StringBuilder(PokemonUi.typeName(row.getKey()).getString()).append(": ");
			boolean any = false;
			for (Map.Entry<String, Object> cell : PuzzleScreens.asMap(row.getValue()).entrySet()) {
				double e = cell.getValue() instanceof Number n ? n.doubleValue() : 1.0;
				sb.append(any ? ", " : "").append(PokemonUi.typeName(cell.getKey()).getString()).append(' ')
						.append(e >= 2 ? "x2" : e == 0 ? "x0" : "x1/2");
				any = true;
			}
			if (any) {
				tableLines.add(sb.toString());
			}
		}
		selectLayout();
	}

	private void selectLayout() {
		if (contentW > 0) {
			layout();
		}
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		for (Map<String, Object> e : events) {
			readLog(e);
		}
	}

	private void readLog(Map<String, Object> event) {
		List<Object> lines = PuzzleScreens.asList(event.get("log"));
		if (lines.isEmpty()) {
			return;
		}
		log.clear();
		for (Object o : lines) {
			String[] t = String.valueOf(o).split(" ");
			if (t.length >= 2) {
				log.add(Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.log." + t[0].toLowerCase(java.util.Locale.ROOT) + "_" + t[1],
						t.length > 2 ? t[2] : ""));
			}
		}
	}

	@Override
	protected void simulateRemote(int player) {
		remoteTouch(player, moveButtons.get((int) (System.currentTimeMillis() / 3200 % 3)).centerX() + 14, actionsY() + 4);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		int sw = sideW();
		int top = bodyTop();
		// header: the turn counter and the fails
		Component turnLabel = Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.turn", Math.min(turn, turns), turns);
		MenuUi.centered(g, turnLabel, contentX + contentW / 2, contentY + 3, contentW - 16, TrahisonPalette.ACCENT_HOT);
		if (fails > 0) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.fails", fails), contentX + 8, contentY + 3, 90, TrahisonPalette.TEXT_MUTED);
		}
		drawSide(g, player, playerHp, playerActive, contentX + 6, top, sw, false);
		drawSide(g, enemy, enemyHp, enemyActive, contentX + contentW - 6 - sw, top, sw, true);
		// middle: the opponent's next attack, the table, the last log
		int mx0 = contentX + 6 + sw + 6;
		int mw = contentW - 12 - 2 * sw - 12;
		int my0 = top;
		if (mw >= 50 && enemy.size() > enemyActive && enemyNext < enemy.get(enemyActive).moves().size()) {
			Move m = enemy.get(enemyActive).moves().get(enemyNext);
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.enemy_next"), mx0, my0, mw, TrahisonPalette.TEXT_MUTED);
			Glyphs.draw(g, PokemonUi.typeGlyph(m.type()), mx0 + 5, my0 + 17, 10, 0, TrahisonPalette.BETRAY);
			TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.attack", PokemonUi.typeName(m.type()), m.power()),
					mx0 + 13, my0 + 12, mw - 13, TrahisonPalette.TEXT);
			my0 += 26;
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.table"), mx0, my0, mw, TrahisonPalette.TEXT_MUTED);
			my0 += 10;
			int room = (actionsY() - 4 - my0) / 10;
			int shown = small() ? Math.min(room, tableLines.size()) : Math.min(Math.max(0, room - 2), tableLines.size());
			for (int i = 0; i < shown; i++) {
				TrahisonUi.textEllipsis(g, Component.literal(tableLines.get(i)), mx0, my0 + i * 10, mw, TrahisonPalette.TEXT_SECONDARY);
			}
			my0 += shown * 10;
			if (!small() && !log.isEmpty()) {
				for (int i = 0; i < Math.min(2, log.size()) && my0 + 10 * (i + 1) < actionsY(); i++) {
					TrahisonUi.textEllipsis(g, log.get(log.size() - 1 - i), mx0, my0 + i * 10, mw, TrahisonPalette.ACCENT);
				}
			}
		}
	}

	private void drawSide(GuiGraphics g, List<Mon> team, List<Integer> hp, int active, int x, int y, int w, boolean enemySide) {
		if (team.isEmpty() || active >= team.size()) {
			return;
		}
		Mon m = team.get(active);
		int max = Math.max(1, m.hp());
		int cur = active < hp.size() ? hp.get(active) : 0;
		int edge = enemySide ? TrahisonPalette.BETRAY_DARK : TrahisonPalette.ALLY_DARK;
		g.fill(x, y, x + w, y + 40, edge);
		g.fill(x + 1, y + 1, x + w - 1, y + 39, TrahisonPalette.PANEL);
		PokemonUi.model(g, m.species(), x + 21, y + 20, 34);
		int tx = x + 42;
		TrahisonUi.textEllipsis(g, PokemonUi.speciesName(m.species()), tx, y + 3, w - 46, TrahisonPalette.TEXT);
		StringBuilder types = new StringBuilder();
		for (String t : m.types()) {
			types.append(types.length() > 0 ? " / " : "").append(PokemonUi.typeName(t).getString());
		}
		TrahisonUi.textEllipsis(g, Component.literal(types.toString()), tx, y + 13, w - 46, TrahisonPalette.TEXT_SECONDARY);
		TrahisonUi.progressBar(g, tx, y + 25, w - 46, 5, cur / (float) max, cur * 3 > max ? TrahisonPalette.SUCCESS : TrahisonPalette.DANGER);
		MenuUi.text(g, Component.literal(cur + " / " + max), tx, y + 31, w - 46, TrahisonPalette.TEXT_MUTED);
		if (enemySide) {
			int ry = y + 42;
			for (int i = 0; i < team.size(); i++) {
				if (i == active) {
					continue;
				}
				int h = i < hp.size() ? hp.get(i) : 0;
				g.fill(x, ry, x + w, ry + 13, TrahisonPalette.BORDER);
				g.fill(x + 1, ry + 1, x + w - 1, ry + 12, TrahisonPalette.PANEL_SUNKEN);
				TrahisonUi.textEllipsis(g, Component.translatable("phantasmon_events.trahison.minigame.battle_puzzle.switch", PokemonUi.speciesName(team.get(i).species()), h),
						x + 4, ry + 3, w - 8, h > 0 ? TrahisonPalette.TEXT_SECONDARY : TrahisonPalette.TEXT_DISABLED);
				ry += 14;
			}
		}
	}
}
