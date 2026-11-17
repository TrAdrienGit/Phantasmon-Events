package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog.Species;
import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pedestals of well-known Pokemon to put in order, lightest first (or smallest first with {@code by = height}). The
 * players swap two pedestals at a time until the row is sorted. The clue {@code scale_sheet} ({@link ClueType#DOCUMENT}) lists
 * the measures ({@code Name 6.0 kg}), the hint for players who do not know them; {@code pedestal_1..n} ({@link ClueType#TEXT})
 * hold the species at the start, for the statues of the world.
 *
 * <h2>Parameters</h2>
 * <ul><li>{@code count} 3..5 (default 4); {@code by} {@code weight} (default) or {@code height}.</li></ul>
 * <h2>Gesture</h2> {@code {"swap": [i, j]}}, two different positions, counted from 0.
 * <h2>Generation</h2> Famous species with pairwise different measures, shuffled so the start is never sorted. Always solvable: any
 * order is reached by swaps, and the sheet gives the measures.
 */
@Component
public class WeighIn implements Mechanism {

	public static final String ID = "weigh_in";
	public static final String PEDESTAL_PREFIX = "pedestal_";
	public static final String SHEET_ROLE = "scale_sheet";
	public static final String ROW_ROLE = "row";
	static final int MIN_COUNT = 3;
	static final int MAX_COUNT = 5;

	private final PokemonCatalog catalog;

	@Autowired
	public WeighIn(PokemonCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public String id() {
		return ID;
	}

	static int count(JsonNode params) {
		return CodeEntry.intParam(params, "count", 4, MIN_COUNT, MAX_COUNT);
	}

	static boolean byHeight(JsonNode params) {
		return params != null && "height".equals(params.path("by").asString(""));
	}

	@Override
	public int difficulty(JsonNode params) {
		return count(params) == 3 ? 1 : count(params) == 4 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 45 + 25 * count(params);
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		List<String> roles = new ArrayList<>();
		for (int i = 1; i <= count(params); i++) {
			roles.add(PEDESTAL_PREFIX + i);
		}
		roles.add(SHEET_ROLE);
		return roles;
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(ROW_ROLE, "terminal", "keypad"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("count", "int", MIN_COUNT, MAX_COUNT, "4", "Number of pedestals to sort"),
				new MechanismParam("by", "text", 0, 0, "weight", "weight or height"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		CodeEntry.checkInt(params, "count", MIN_COUNT, MAX_COUNT, problems);
		if (params != null && params.has("by") && !List.of("weight", "height").contains(params.get("by").asString(""))) {
			problems.add("by must be weight or height");
		}
		return problems;
	}

	@Override
	public List<String> validateStep(Step step) {
		List<String> problems = new ArrayList<>();
		if (step.gives().stream().noneMatch(g -> SHEET_ROLE.equals(g.clue()) && Give.OPEN.equals(g.when()))) {
			problems.add("missing=" + SHEET_ROLE + ";when=open");
		}
		if (step.slots().isEmpty()) {
			problems.add("missing=slot:" + ROW_ROLE);
		}
		return problems;
	}

	private static int measure(Species s, boolean height) {
		return height ? s.height() : s.weight();
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		int n = count(params);
		boolean height = byHeight(params);
		List<Species> pool = new ArrayList<>(catalog.famous());
		Collections.shuffle(pool, rng);
		List<Species> picked = new ArrayList<>();
		for (Species s : pool) {
			if (picked.size() < n && picked.stream().noneMatch(p -> measure(p, height) == measure(s, height))) {
				picked.add(s);
			}
		}
		List<Species> sorted = new ArrayList<>(picked);
		sorted.sort(Comparator.comparingInt(s -> measure(s, height)));
		List<Species> start = new ArrayList<>(sorted);
		do {
			Collections.shuffle(start, rng);
		} while (start.equals(sorted));
		List<Clue> clues = new ArrayList<>();
		ArrayNode order = Json.array();
		ArrayNode answer = Json.array();
		for (int i = 0; i < n; i++) {
			clues.add(Clue.text(PEDESTAL_PREFIX + (i + 1), start.get(i).id()));
			order.add(start.get(i).id());
			answer.add(sorted.get(i).id());
		}
		StringBuilder sheet = new StringBuilder();
		List<Species> listed = new ArrayList<>(picked);
		Collections.shuffle(listed, rng);
		for (Species s : listed) {
			sheet.append(s.name()).append(' ');
			sheet.append(height ? "%.1f m".formatted(s.height() / 10.0) : "%.1f kg".formatted(s.weight() / 10.0)).append('\n');
		}
		clues.add(Clue.document(SHEET_ROLE, List.of(sheet.toString().strip())));
		ObjectNode data = Json.object();
		data.set("order", order);
		data.set("answer", answer);
		data.put("swaps", 0);
		data.put("solved", false);
		return new MechanismState(data, clues);
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		JsonNode swap = input == null ? null : input.get("swap");
		ObjectNode old = state.data();
		int n = old.get("order").size();
		if (swap == null || !swap.isArray() || swap.size() != 2 || !swap.get(0).isInt() || !swap.get(1).isInt()) {
			throw new IllegalArgumentException("swap: two positions");
		}
		int i = swap.get(0).asInt();
		int j = swap.get(1).asInt();
		if (i < 0 || j < 0 || i >= n || j >= n || i == j) {
			throw new IllegalArgumentException("swap: two different positions 0.." + (n - 1));
		}
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		ObjectNode data = state.copyData();
		ArrayNode order = (ArrayNode) data.get("order");
		JsonNode a = order.get(i);
		JsonNode b = order.get(j);
		order.set(i, b);
		order.set(j, a);
		data.put("swaps", old.path("swaps").asInt() + 1);
		boolean solved = order.equals(data.get("answer"));
		data.put("solved", solved);
		ObjectNode event = Json.object();
		event.put("type", "swap");
		event.put("i", i);
		event.put("j", j);
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.set("order", d.get("order").deepCopy());
		view.put("swaps", d.path("swaps").asInt());
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("answer", d.get("answer").deepCopy());
		}
		return view;
	}
}
