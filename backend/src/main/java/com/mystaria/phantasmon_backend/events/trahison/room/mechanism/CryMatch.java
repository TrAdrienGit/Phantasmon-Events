package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog.Species;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A console that plays cries, to match with silhouettes of well-known Pokemon. The view lists the cries in the order they are heard
 * ({@code cries}: Cobblemon species ids, the client plays the cry of each) and the silhouettes in a different order
 * ({@code cards}). The players pair every cry with a silhouette and confirm; the answer is how many pairs are right, never which.
 * <p>
 * Known leak (decisions.md, D180): the client needs the species ids to play the cries and draw the silhouettes, so a hacked client
 * could read the answer from the view; the screen shows no name and no number.
 *
 * <h2>Parameters</h2>
 * <ul><li>{@code count} 3..4 (default 3): cries to match.</li></ul>
 * <h2>Gesture</h2> {@code {"match": [c0, c1, ...]}}: for each cry, the index of the silhouette chosen; a permutation of 0..count-1.
 * <h2>Generation</h2> Distinct famous species, the two lists shuffled independently. Always solvable: a permutation space of at most
 * 24 and each try says how many pairs are right.
 */
@Component
public class CryMatch implements Mechanism {

	public static final String ID = "cry_match";
	public static final String CONSOLE_ROLE = "console";
	static final int MIN_COUNT = 3;
	static final int MAX_COUNT = 4;

	private final PokemonCatalog catalog;

	@Autowired
	public CryMatch(PokemonCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public String id() {
		return ID;
	}

	static int count(JsonNode params) {
		return CodeEntry.intParam(params, "count", MIN_COUNT, MIN_COUNT, MAX_COUNT);
	}

	@Override
	public int difficulty(JsonNode params) {
		return count(params) == 3 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 60 + 50 * count(params);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(CONSOLE_ROLE, "terminal", "keypad"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(new MechanismParam("count", "int", MIN_COUNT, MAX_COUNT, String.valueOf(MIN_COUNT), "Number of cries to match"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		CodeEntry.checkInt(params, "count", MIN_COUNT, MAX_COUNT, problems);
		return problems;
	}

	@Override
	public List<String> validateStep(com.mystaria.phantasmon_backend.events.trahison.room.Step step) {
		return step.slots().isEmpty() ? List.of("missing=slot:" + CONSOLE_ROLE) : List.of();
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		int n = count(params);
		List<Species> pool = new ArrayList<>(catalog.famous());
		Collections.shuffle(pool, rng);
		List<Species> picked = new ArrayList<>(pool.subList(0, n));
		List<Species> cries = new ArrayList<>(picked);
		List<Species> cards;
		do {
			Collections.shuffle(cries, rng);
			cards = new ArrayList<>(picked);
			Collections.shuffle(cards, rng);
		} while (cries.equals(cards)); // never hand over the identity pairing
		ArrayNode criesNode = Json.array();
		ArrayNode cardsNode = Json.array();
		ArrayNode answer = Json.array();
		for (Species s : cries) {
			criesNode.add(s.id());
			answer.add(cards.indexOf(s));
		}
		cards.forEach(s -> cardsNode.add(s.id()));
		ObjectNode data = Json.object();
		data.set("cries", criesNode);
		data.set("cards", cardsNode);
		data.set("answer", answer);
		data.put("wrong", 0);
		data.put("last_correct", -1);
		data.set("last_match", Json.array());
		data.put("solved", false);
		return new MechanismState(data, List.of());
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		ObjectNode old = state.data();
		int n = old.get("answer").size();
		JsonNode match = input == null ? null : input.get("match");
		if (match == null || !match.isArray() || match.size() != n) {
			throw new IllegalArgumentException("match: one silhouette per cry (" + n + ")");
		}
		Set<Integer> seen = new HashSet<>();
		for (JsonNode m : match) {
			if (!m.isInt() || m.asInt() < 0 || m.asInt() >= n || !seen.add(m.asInt())) {
				throw new IllegalArgumentException("match: a permutation of 0.." + (n - 1));
			}
		}
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		int correct = 0;
		for (int i = 0; i < n; i++) {
			if (match.get(i).asInt() == old.get("answer").get(i).asInt()) {
				correct++;
			}
		}
		boolean solved = correct == n;
		ObjectNode data = state.copyData();
		data.set("last_match", match.deepCopy());
		data.put("last_correct", correct);
		data.put("solved", solved);
		if (!solved) {
			data.put("wrong", old.path("wrong").asInt() + 1);
		}
		ObjectNode event = Json.object();
		event.put("type", solved ? "unlock" : "try");
		event.put("correct", correct);
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.set("cries", d.get("cries").deepCopy());
		view.set("cards", d.get("cards").deepCopy());
		view.put("wrong", d.path("wrong").asInt());
		view.put("last_correct", d.path("last_correct").asInt(-1));
		view.set("last_match", d.get("last_match").deepCopy());
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("answer", d.get("answer").deepCopy());
		}
		return view;
	}
}
