package com.mystaria.phantasmon_backend.events.trahison.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.Duel;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.BattlePuzzle;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Tests of {@link BattlePuzzle} and {@link Duel} (T8.4). */
class BattlePuzzleTest {

	final PokemonCatalog catalog = new PokemonCatalog();
	final BattlePuzzle puzzle = new BattlePuzzle(catalog);

	static ObjectNode params(int turns, int mons, int moves) {
		ObjectNode p = Json.object();
		p.put("turns", turns);
		p.put("mons", mons);
		p.put("moves", moves);
		return p;
	}

	static ObjectNode act(String key, int value) {
		ObjectNode in = Json.object();
		in.put(key, value);
		return in;
	}

	Duel duelOf(MechanismState s) {
		return new Duel(catalog, Duel.teamFromJson(s.data().get("player")), Duel.teamFromJson(s.data().get("enemy")));
	}

	@Test
	void thousandPositionsHaveExactlyOneWinningSequence() {
		for (long seed = 0; seed < 1000; seed++) {
			int turns = 2 + (int) (seed % 3);
			MechanismState s = puzzle.generate(new SplittableRandom(seed), params(turns, 1 + (int) (seed % 3), 2 + (int) (seed % 3)));
			Duel duel = duelOf(s);
			assertEquals(1, duel.countWinning(turns, 5), "seed " + seed);
			assertEquals(turns, duel.firstWinning(turns).size(), "seed " + seed);
			assertEquals(0, duel.countWinning(turns - 1, 5), "no quicker win, seed " + seed);
		}
	}

	@Test
	void theOpponentAlwaysAnswersTheSameWay() {
		MechanismState s = puzzle.generate(new SplittableRandom(3), params(3, 2, 3));
		Duel a = duelOf(s);
		Duel b = duelOf(s);
		for (int turn = 0; turn < 3 && a.status() == Duel.Status.ONGOING; turn++) {
			Duel.Action first = a.actions().get(0);
			assertEquals(a.play(first), b.play(first));
			assertEquals(a.enemyMoveIndex(), b.enemyMoveIndex());
		}
	}

	@Test
	void theWinningSequenceSolvesAndAFailureResets() {
		for (long seed = 0; seed < 50; seed++) {
			MechanismState s = puzzle.generate(new SplittableRandom(seed), params(3, 2, 3));
			List<Duel.Action> win = duelOf(s).firstWinning(3);
			// a different first action cannot win: after the turns run out, the position is back
			MechanismState cur = s;
			Duel.Action wrongStart = duelOf(s).actions().stream().filter(a -> !a.equals(win.get(0))).findFirst().orElseThrow();
			InputResult r = puzzle.onInput(cur, 0, wrongStart.move() >= 0 ? act("move", wrongStart.move()) : act("switch", wrongStart.swap()));
			assertFalse(r.solved(), "a wrong first action cannot win, seed " + seed);
			cur = r.state();
			while (r.events().get(0).get("type").asString().equals("turn")) { // keep playing the first legal action until the attempt ends
				Duel replay = new Duel(catalog, Duel.teamFromJson(cur.data().get("player")), Duel.teamFromJson(cur.data().get("enemy")));
				for (JsonNode h : cur.data().get("history")) {
					replay.play(h.has("move") ? Duel.Action.attack(h.get("move").asInt()) : Duel.Action.switchTo(h.get("switch").asInt()));
				}
				Duel.Action a = replay.actions().get(0);
				r = puzzle.onInput(cur, 0, a.move() >= 0 ? act("move", a.move()) : act("switch", a.swap()));
				cur = r.state();
			}
			if (!r.solved()) {
				assertEquals(0, cur.data().get("history").size());
				assertEquals(1, cur.data().get("fails").asInt());
			}
			// the real solution
			cur = s;
			InputResult last = null;
			for (Duel.Action a : win) {
				last = puzzle.onInput(cur, 0, a.move() >= 0 ? act("move", a.move()) : act("switch", a.swap()));
				cur = last.state();
			}
			assertTrue(last.solved(), "seed " + seed);
			JsonNode playerView = puzzle.view(s, ViewerKind.PLAYER);
			assertFalse(playerView.has("answer"));
			assertTrue(puzzle.view(s, ViewerKind.GAME_MASTER).has("answer"));
		}
	}
}
