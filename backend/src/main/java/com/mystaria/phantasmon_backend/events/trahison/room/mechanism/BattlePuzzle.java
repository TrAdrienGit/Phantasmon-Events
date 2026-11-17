package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.Duel;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.Duel.Action;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.Duel.Mon;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.Duel.Move;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.Duel.Status;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog.Species;
import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The tactical duel puzzle (T8.4, solutions-techniques.md S9): a small deterministic duel ({@link Duel}) where the players
 * must find the <b>one</b> sequence of actions that knocks out the opposing team in {@code turns} turns. The backend draws
 * a position and keeps it only when an exhaustive search finds exactly one winning sequence of exactly that length. The
 * state is the starting position plus the actions played in the current attempt; the board is replayed from them. A failed
 * attempt (own team down, or the turns used up without a win) puts the starting position back ({@code fails} counts).
 * Solving gives the clue {@code reward}, a four digit code ({@link ClueType#DIGITS}), shown with {@code when = solved}.
 *
 * <h2>Parameters</h2> {@code turns} 2..4 (default 3), {@code mons} 1..3 per side (default 2), {@code moves} 2..4 attacks per Pokemon
 * (default 3).
 * <h2>Gestures</h2> {@code {"move": i}} an attack of the active Pokemon, or {@code {"switch": j}} a switch to team member j.
 * <h2>View</h2> Both teams (they are not secret), the board now, the turns left, the fails, and the type table rows the
 * screen needs ({@code table}: only the types in play). The winning sequence goes to the game master only.
 */
@Component
public class BattlePuzzle implements Mechanism {

	public static final String ID = "battle_puzzle";
	public static final String REWARD_ROLE = "reward";
	public static final String TERMINAL_ROLE = "arena";
	static final int MAX_TRIES = 20000;
	static final int[] HPS = { 30, 40, 50, 60 };
	static final int[] SPEEDS = { 20, 40, 60, 80, 100 };
	static final int[] POWERS = { 10, 15, 20, 25, 30, 40 };

	private final PokemonCatalog catalog;

	@Autowired
	public BattlePuzzle(PokemonCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public String id() {
		return ID;
	}

	static int turns(JsonNode p) {
		return CodeEntry.intParam(p, "turns", 3, 2, 4);
	}

	static int mons(JsonNode p) {
		return CodeEntry.intParam(p, "mons", 2, 1, 3);
	}

	static int moves(JsonNode p) {
		return CodeEntry.intParam(p, "moves", 3, 2, 4);
	}

	@Override
	public int difficulty(JsonNode params) {
		return Math.min(3, turns(params) - 1);
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 120 + 60 * turns(params);
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		return List.of(REWARD_ROLE);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(TERMINAL_ROLE, "terminal", "keypad"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("turns", "int", 2, 4, "3", "Turns to win in (the one winning sequence has exactly this length)"),
				new MechanismParam("mons", "int", 1, 3, "2", "Pokemon per side"),
				new MechanismParam("moves", "int", 2, 4, "3", "Attacks per Pokemon"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		CodeEntry.checkInt(params, "turns", 2, 4, problems);
		CodeEntry.checkInt(params, "mons", 1, 3, problems);
		CodeEntry.checkInt(params, "moves", 2, 4, problems);
		return problems;
	}

	@Override
	public List<String> validateStep(Step step) {
		List<String> problems = new ArrayList<>();
		if (step.slots().isEmpty()) {
			problems.add("missing=slot:" + TERMINAL_ROLE);
		}
		if (step.gives().stream().noneMatch(g -> REWARD_ROLE.equals(g.clue()) && Give.SOLVED.equals(g.when()))) {
			problems.add("missing=" + REWARD_ROLE + ";when=solved");
		}
		return problems;
	}

	// ---------------------------------------------------------------- generation

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		int turns = turns(params);
		for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
			Duel duel = randomDuel(rng, mons(params), moves(params));
			if (duel.countWinning(turns, 2) != 1) {
				continue;
			}
			List<Action> win = duel.firstWinning(turns);
			if (win.size() != turns) {
				continue; // a quicker win would make the turn count meaningless
			}
			return state(duel, turns, win, rng);
		}
		throw new IllegalStateException("no duel with one winning sequence of " + turns + " turns");
	}

	private Duel randomDuel(RandomGenerator rng, int mons, int moves) {
		List<Species> pool = new ArrayList<>(catalog.famous());
		Collections.shuffle(pool, rng);
		return new Duel(catalog, team(rng, pool.subList(0, mons), moves), team(rng, pool.subList(mons, 2 * mons), moves));
	}

	private List<Mon> team(RandomGenerator rng, List<Species> species, int moves) {
		List<Mon> team = new ArrayList<>();
		for (Species s : species) {
			List<Move> list = new ArrayList<>();
			for (int i = 0; i < moves; i++) {
				list.add(new Move(catalog.types().get(rng.nextInt(catalog.types().size())), POWERS[rng.nextInt(POWERS.length)],
						rng.nextInt(8) == 0));
			}
			team.add(new Mon(s.id(), s.types(), HPS[rng.nextInt(HPS.length)], SPEEDS[rng.nextInt(SPEEDS.length)], list));
		}
		return team;
	}

	private MechanismState state(Duel duel, int turns, List<Action> win, RandomGenerator rng) {
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			code.append((char) ('0' + rng.nextInt(10)));
		}
		ObjectNode data = Json.object();
		data.set("player", Duel.teamToJson(duel.player()));
		data.set("enemy", Duel.teamToJson(duel.enemy()));
		data.put("turns", turns);
		data.set("history", Json.array());
		data.put("fails", 0);
		data.put("solved", false);
		ArrayNode answer = data.putArray("answer");
		for (Action a : win) {
			answer.add(a.move() >= 0 ? "move " + a.move() : "switch " + a.swap());
		}
		return new MechanismState(data, List.of(Clue.digits(REWARD_ROLE, code.toString())));
	}

	// ---------------------------------------------------------------- play

	private Duel replay(ObjectNode data) {
		Duel duel = new Duel(catalog, Duel.teamFromJson(data.get("player")), Duel.teamFromJson(data.get("enemy")));
		for (JsonNode h : data.get("history")) {
			duel.play(h.has("move") ? Action.attack(h.get("move").asInt()) : Action.switchTo(h.get("switch").asInt()));
		}
		return duel;
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		ObjectNode old = state.data();
		boolean isMove = input != null && input.path("move").isInt();
		boolean isSwitch = input != null && input.path("switch").isInt();
		if (isMove == isSwitch) {
			throw new IllegalArgumentException("one of move or switch");
		}
		Action action = isMove ? Action.attack(input.get("move").asInt()) : Action.switchTo(input.get("switch").asInt());
		Duel duel = replay(old);
		if (!duel.legal(action)) {
			throw new IllegalArgumentException("illegal action");
		}
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		List<String> log = duel.play(action);
		ObjectNode data = state.copyData();
		ObjectNode event = Json.object();
		ArrayNode lines = event.putArray("log");
		log.forEach(lines::add);
		int used = old.get("history").size() + 1;
		boolean solved = false;
		if (duel.status() == Status.WON) {
			solved = true;
			data.put("solved", true);
			((ArrayNode) data.get("history")).add(isMove ? Json.object().put("move", action.move()) : Json.object().put("switch", action.swap()));
			event.put("type", "unlock");
		} else if (duel.status() == Status.LOST || used >= old.get("turns").asInt()) {
			data.set("history", Json.array());
			data.put("fails", old.path("fails").asInt() + 1);
			event.put("type", "fail");
		} else {
			((ArrayNode) data.get("history")).add(isMove ? Json.object().put("move", action.move()) : Json.object().put("switch", action.swap()));
			event.put("type", "turn");
		}
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		Duel duel = replay(d);
		ObjectNode view = Json.object();
		view.set("player", d.get("player").deepCopy());
		view.set("enemy", d.get("enemy").deepCopy());
		ArrayNode php = view.putArray("player_hp");
		for (int h : duel.playerHp()) {
			php.add(h);
		}
		ArrayNode ehp = view.putArray("enemy_hp");
		for (int h : duel.enemyHp()) {
			ehp.add(h);
		}
		view.put("player_active", duel.playerActive());
		view.put("enemy_active", duel.enemyActive());
		view.put("enemy_next_move", duel.enemyMoveIndex()); // the displayed rule: the opponent's attack is deducible, so shown
		view.put("turns", d.get("turns").asInt());
		view.put("turn", d.get("history").size());
		view.put("fails", d.path("fails").asInt());
		view.put("solved", d.path("solved").asBoolean(false));
		view.set("table", table(d));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("answer", d.get("answer").deepCopy());
		}
		return view;
	}

	/**
	 * The part of the type table the duel needs: {@code {attack type: {defender type: multiplier}}} for the attack types of both
	 * teams against the types of both teams, only where the multiplier is not 1 (so the client can show who beats whom).
	 */
	private ObjectNode table(ObjectNode d) {
		java.util.Set<String> attacks = new java.util.TreeSet<>();
		java.util.Set<String> defenders = new java.util.TreeSet<>();
		for (String side : List.of("player", "enemy")) {
			for (JsonNode mon : d.get(side)) {
				mon.get("types").forEach(t -> defenders.add(t.asString()));
				mon.get("moves").forEach(m -> attacks.add(m.get("type").asString()));
			}
		}
		ObjectNode table = Json.object();
		for (String a : attacks) {
			ObjectNode row = table.putObject(a);
			for (String t : defenders) {
				double e = catalog.efficacy(a, t);
				if (e != 1.0) {
					row.put(t, e);
				}
			}
		}
		return table;
	}
}
