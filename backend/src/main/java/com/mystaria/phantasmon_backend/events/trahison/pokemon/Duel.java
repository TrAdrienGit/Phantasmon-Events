package com.mystaria.phantasmon_backend.events.trahison.pokemon;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The rules of the tactical duel of the {@code battle_puzzle} step (solutions-techniques.md S9): pure, deterministic, no luck.
 * <ul>
 * <li>Each side has 1 to 3 Pokemon with hit points, a speed and 2 to 4 attacks (a type, a fixed power, sometimes
 * <em>priority</em>). The first Pokemon of a side is active; when the active one faints the next living one of its team
 * (in team order) comes in.</li>
 * <li>Damage = power x efficacy of the attack type on the target's types (x2, x1, x0.5, x0, and x4 / x0.25 for two types),
 * rounded down.</li>
 * <li>Each turn the player picks an attack of the active Pokemon or switches to another living one. A switch happens
 * first. Then the faster side hits first (a priority attack goes before a normal one; equal speed: the player). A
 * Pokemon that fainted before its turn does not act.</li>
 * <li>The opponent follows a fixed, displayed rule: <b>it always uses the attack that hurts the player's active Pokemon
 * most</b> (the first listed on a tie).</li>
 * </ul>
 */
public final class Duel {

	public record Move(String type, int power, boolean priority) {
	}

	public record Mon(String species, List<String> types, int maxHp, int speed, List<Move> moves) {
	}

	/** A player action: {@code move >= 0} an attack index, otherwise {@code swap >= 0} the team index to switch to. */
	public record Action(int move, int swap) {
		public static Action attack(int i) {
			return new Action(i, -1);
		}

		public static Action switchTo(int i) {
			return new Action(-1, i);
		}
	}

	public enum Status {
		ONGOING, WON, LOST
	}

	private final PokemonCatalog catalog;
	private final List<Mon> player;
	private final List<Mon> enemy;
	private final int[] playerHp;
	private final int[] enemyHp;
	private int playerActive;
	private int enemyActive;

	public Duel(PokemonCatalog catalog, List<Mon> player, List<Mon> enemy) {
		this(catalog, player, enemy, hps(player), hps(enemy), 0, 0);
	}

	private Duel(PokemonCatalog catalog, List<Mon> player, List<Mon> enemy, int[] playerHp, int[] enemyHp, int playerActive,
			int enemyActive) {
		this.catalog = catalog;
		this.player = player;
		this.enemy = enemy;
		this.playerHp = playerHp;
		this.enemyHp = enemyHp;
		this.playerActive = playerActive;
		this.enemyActive = enemyActive;
	}

	private static int[] hps(List<Mon> team) {
		int[] hp = new int[team.size()];
		for (int i = 0; i < hp.length; i++) {
			hp[i] = team.get(i).maxHp();
		}
		return hp;
	}

	public Duel copy() {
		return new Duel(catalog, player, enemy, playerHp.clone(), enemyHp.clone(), playerActive, enemyActive);
	}

	public List<Mon> player() {
		return player;
	}

	public List<Mon> enemy() {
		return enemy;
	}

	public int[] playerHp() {
		return playerHp.clone();
	}

	public int[] enemyHp() {
		return enemyHp.clone();
	}

	public int playerActive() {
		return playerActive;
	}

	public int enemyActive() {
		return enemyActive;
	}

	public Status status() {
		if (alive(enemyHp) == 0) {
			return Status.WON;
		}
		return alive(playerHp) == 0 ? Status.LOST : Status.ONGOING;
	}

	private static int alive(int[] hp) {
		int n = 0;
		for (int h : hp) {
			if (h > 0) {
				n++;
			}
		}
		return n;
	}

	/** Every action the player may pick now: the active Pokemon's attacks, then a switch to each other living one. */
	public List<Action> actions() {
		List<Action> out = new ArrayList<>();
		for (int i = 0; i < player.get(playerActive).moves().size(); i++) {
			out.add(Action.attack(i));
		}
		for (int j = 0; j < player.size(); j++) {
			if (j != playerActive && playerHp[j] > 0) {
				out.add(Action.switchTo(j));
			}
		}
		return out;
	}

	public boolean legal(Action a) {
		return actions().contains(a);
	}

	/** Damage of {@code m} on a target with these types. */
	public int damage(Move m, List<String> targetTypes) {
		return (int) Math.floor(m.power() * catalog.efficacy(m.type(), targetTypes));
	}

	/** The opponent's rule: the attack that hurts the player's active Pokemon most, the first on a tie. */
	public int enemyMoveIndex() {
		List<Move> moves = enemy.get(enemyActive).moves();
		List<String> target = player.get(playerActive).types();
		int best = 0;
		for (int i = 1; i < moves.size(); i++) {
			if (damage(moves.get(i), target) > damage(moves.get(best), target)) {
				best = i;
			}
		}
		return best;
	}

	/**
	 * Plays one turn (the action must be legal). Returns the log of what happened, as short lines such as
	 * {@code "P hit 20"}: {@code P}/{@code E} for who acted, for the client to animate.
	 */
	public List<String> play(Action a) {
		List<String> log = new ArrayList<>();
		if (a.move() < 0) {
			playerActive = a.swap();
			log.add("P switch " + a.swap());
		}
		int pActor = playerActive;
		int eActor = enemyActive;
		int eMove = enemyMoveIndex();
		Move pm = a.move() >= 0 ? player.get(pActor).moves().get(a.move()) : null;
		Move em = enemy.get(eActor).moves().get(eMove);
		boolean playerFirst;
		if (pm == null) {
			playerFirst = true; // a switch is already done: the player's side has nothing left to do
		} else {
			int pp = pm.priority() ? 1 : 0;
			int ep = em.priority() ? 1 : 0;
			playerFirst = pp != ep ? pp > ep : player.get(pActor).speed() >= enemy.get(eActor).speed();
		}
		if (playerFirst) {
			playerActs(pm, pActor, log);
			enemyActs(em, eActor, log);
		} else {
			enemyActs(em, eActor, log);
			playerActs(pm, pActor, log);
		}
		return log;
	}

	private void playerActs(Move m, int actor, List<String> log) {
		if (m == null || playerHp[actor] <= 0 || status() != Status.ONGOING) {
			return;
		}
		int dmg = damage(m, enemy.get(enemyActive).types());
		enemyHp[enemyActive] = Math.max(0, enemyHp[enemyActive] - dmg);
		log.add("P hit " + dmg);
		if (enemyHp[enemyActive] <= 0) {
			log.add("E faint " + enemyActive);
			enemyActive = nextAlive(enemyHp, enemyActive);
		}
	}

	private void enemyActs(Move m, int actor, List<String> log) {
		if (enemyHp[actor] <= 0 || status() != Status.ONGOING) {
			return;
		}
		int dmg = damage(m, player.get(playerActive).types());
		playerHp[playerActive] = Math.max(0, playerHp[playerActive] - dmg);
		log.add("E hit " + dmg);
		if (playerHp[playerActive] <= 0) {
			log.add("P faint " + playerActive);
			playerActive = nextAlive(playerHp, playerActive);
		}
	}

	private static int nextAlive(int[] hp, int current) {
		for (int i = 0; i < hp.length; i++) {
			if (hp[i] > 0) {
				return i;
			}
		}
		return current;
	}

	// ---------------------------------------------------------------- search

	/** The number of action sequences of at most {@code turns} turns that win (a sequence stops at the win); capped at {@code cap}. */
	public int countWinning(int turns, int cap) {
		return count(this, turns, cap, null);
	}

	/** The first winning sequence found, or empty. */
	public List<Action> firstWinning(int turns) {
		List<Action> path = new ArrayList<>();
		count(this, turns, 1, path);
		return path;
	}

	private static int count(Duel d, int turns, int cap, List<Action> witness) {
		if (turns == 0 || d.status() != Status.ONGOING) {
			return 0;
		}
		int total = 0;
		for (Action a : d.actions()) {
			Duel next = d.copy();
			next.play(a);
			if (next.status() == Status.WON) {
				if (witness != null && witness.isEmpty()) {
					witness.add(a);
				}
				total++;
			} else if (next.status() == Status.ONGOING) {
				List<Action> sub = witness == null ? null : new ArrayList<>();
				int n = count(next, turns - 1, cap - total, sub);
				if (n > 0 && witness != null && witness.isEmpty()) {
					witness.add(a);
					witness.addAll(sub);
				}
				total += n;
			}
			if (total >= cap) {
				return total;
			}
		}
		return total;
	}

	// ---------------------------------------------------------------- JSON

	public static ArrayNode teamToJson(List<Mon> team) {
		ArrayNode out = com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json.array();
		for (Mon m : team) {
			ObjectNode o = out.addObject();
			o.put("species", m.species());
			ArrayNode types = o.putArray("types");
			m.types().forEach(types::add);
			o.put("hp", m.maxHp());
			o.put("speed", m.speed());
			ArrayNode moves = o.putArray("moves");
			for (Move mv : m.moves()) {
				ObjectNode mo = moves.addObject();
				mo.put("type", mv.type());
				mo.put("power", mv.power());
				mo.put("priority", mv.priority());
			}
		}
		return out;
	}

	public static List<Mon> teamFromJson(JsonNode array) {
		List<Mon> team = new ArrayList<>();
		for (JsonNode o : array) {
			List<String> types = new ArrayList<>();
			o.get("types").forEach(t -> types.add(t.asString()));
			List<Move> moves = new ArrayList<>();
			for (JsonNode mo : o.get("moves")) {
				moves.add(new Move(mo.get("type").asString(), mo.get("power").asInt(), mo.path("priority").asBoolean(false)));
			}
			team.add(new Mon(o.get("species").asString(), types, o.get("hp").asInt(), o.get("speed").asInt(), moves));
		}
		return team;
	}
}
