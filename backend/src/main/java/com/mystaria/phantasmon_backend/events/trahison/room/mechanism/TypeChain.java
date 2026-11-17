package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Levers marked with a Pokemon type, to pull in the order "who beats whom": the first lever's type is super effective on the
 * second's, which is on the third's, and so on. The order is unique: among the types in play, those are the <em>only</em>
 * super-effective relations. A wrong lever puts every lever back up and counts an error. Played in the world (slots
 * {@code lever_1..lever_5}, role = lever index); the type of each lever is a {@link ClueType#TEXT} clue {@code mark_i} (a type id)
 * for the lever's marking, and {@code type_sheet} ({@link ClueType#DOCUMENT}) is the hint: "Fire beats Grass, Ice..." for the types
 * in play.
 *
 * <h2>Parameters</h2> {@code count} 3..5 (default 4): number of levers.
 * <h2>Generation</h2> Randomised search of a chain with the unique-order property; the tests check that 1000 seeds find one.
 */
@Component
public class TypeChain implements Mechanism {

	public static final String ID = "type_chain";
	public static final String LEVER_PREFIX = "lever_";
	public static final String MARK_PREFIX = "mark_";
	public static final String SHEET_ROLE = "type_sheet";
	static final int MIN_COUNT = 3;
	static final int MAX_COUNT = 5;

	private final PokemonCatalog catalog;

	@Autowired
	public TypeChain(PokemonCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public String id() {
		return ID;
	}

	static int count(JsonNode params) {
		return CodeEntry.intParam(params, "count", 4, MIN_COUNT, MAX_COUNT);
	}

	@Override
	public int difficulty(JsonNode params) {
		return count(params) == 3 ? 1 : count(params) == 4 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 60 + 30 * count(params);
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		List<String> roles = new ArrayList<>();
		for (int i = 1; i <= count(params); i++) {
			roles.add(MARK_PREFIX + i);
		}
		roles.add(SHEET_ROLE);
		return roles;
	}

	@Override
	public List<SlotRole> slotRoles() {
		List<SlotRole> roles = new ArrayList<>();
		for (int i = 1; i <= MAX_COUNT; i++) {
			roles.add(SlotRole.of(LEVER_PREFIX + i, "lever"));
		}
		return roles;
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(new MechanismParam("count", "int", MIN_COUNT, MAX_COUNT, "4", "Number of levers (bind lever_1..lever_count)"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		CodeEntry.checkInt(params, "count", MIN_COUNT, MAX_COUNT, problems);
		return problems;
	}

	@Override
	public List<String> validateStep(Step step) {
		List<String> problems = new ArrayList<>();
		int n = count(step.params());
		for (int i = 1; i <= n; i++) {
			String role = LEVER_PREFIX + i;
			if (!step.slots().containsKey(role)) {
				problems.add("missing=slot:" + role);
			}
			String mark = MARK_PREFIX + i;
			if (step.gives().stream().noneMatch(g -> mark.equals(g.clue()) && Give.OPEN.equals(g.when()))) {
				problems.add("missing=" + mark + ";when=open");
			}
		}
		if (step.gives().stream().noneMatch(g -> SHEET_ROLE.equals(g.clue()) && Give.OPEN.equals(g.when()))) {
			problems.add("missing=" + SHEET_ROLE + ";when=open");
		}
		return problems;
	}

	private boolean beats(String a, String b) {
		return catalog.efficacy(a, b) > 1.0;
	}

	/** A chain {@code t1 > t2 > ... > tn} whose only super-effective relations (either way) are the n-1 consecutive ones; empty if none. */
	private Optional<List<String>> chain(RandomGenerator rng, int n) {
		List<String> types = new ArrayList<>(catalog.types());
		Collections.shuffle(types, rng);
		return extend(new ArrayList<>(), types, n);
	}

	private Optional<List<String>> extend(List<String> path, List<String> candidates, int n) {
		if (path.size() == n) {
			return Optional.of(List.copyOf(path));
		}
		for (String t : candidates) {
			if (path.contains(t) || !fits(path, t)) {
				continue;
			}
			path.add(t);
			Optional<List<String>> found = extend(path, candidates, n);
			if (found.isPresent()) {
				return found;
			}
			path.remove(path.size() - 1);
		}
		return Optional.empty();
	}

	private boolean fits(List<String> path, String t) {
		for (int i = 0; i < path.size(); i++) {
			String p = path.get(i);
			boolean last = i == path.size() - 1;
			if (last ? !beats(p, t) || beats(t, p) : beats(p, t) || beats(t, p)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		int n = count(params);
		List<String> chain = chain(rng, n).orElseThrow(() -> new IllegalStateException("no type chain of " + n));
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			slots.add(i);
		}
		Collections.shuffle(slots, rng); // slots.get(k) = the lever that holds the k-th type of the chain
		String[] marks = new String[n];
		ArrayNode order = Json.array();
		for (int k = 0; k < n; k++) {
			marks[slots.get(k)] = chain.get(k);
			order.add(slots.get(k));
		}
		ArrayNode types = Json.array();
		List<Clue> clues = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			types.add(marks[i]);
			clues.add(Clue.text(MARK_PREFIX + (i + 1), marks[i]));
		}
		StringBuilder sheet = new StringBuilder();
		List<String> inPlay = new ArrayList<>(chain);
		Collections.shuffle(inPlay, rng);
		for (String a : inPlay) {
			List<String> victims = new ArrayList<>();
			for (String b : inPlay) {
				if (beats(a, b)) {
					victims.add(b);
				}
			}
			sheet.append(a).append(" > ").append(victims.isEmpty() ? "-" : String.join(", ", victims)).append('\n');
		}
		clues.add(Clue.document(SHEET_ROLE, List.of(sheet.toString().strip())));
		ObjectNode data = Json.object();
		data.set("types", types);
		data.set("order", order);
		data.put("progress", 0);
		data.put("wrong", 0);
		data.put("solved", false);
		return new MechanismState(data, clues);
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		return InputResult.unchanged(state);
	}

	/** A lever pulled: {@code role} is {@code lever_k}. The next lever of the chain advances it, any other resets it. */
	@Override
	public InputResult onUse(MechanismState state, int player, String role) {
		ObjectNode old = state.data();
		if (old.path("solved").asBoolean(false) || !role.startsWith(LEVER_PREFIX)) {
			return InputResult.unchanged(state);
		}
		int lever;
		try {
			lever = Integer.parseInt(role.substring(LEVER_PREFIX.length())) - 1;
		} catch (NumberFormatException e) {
			return InputResult.unchanged(state);
		}
		int n = old.get("order").size();
		if (lever < 0 || lever >= n) {
			return InputResult.unchanged(state);
		}
		ObjectNode data = state.copyData();
		int progress = old.path("progress").asInt();
		ObjectNode event = Json.object();
		event.put("lever", lever);
		boolean solved = false;
		if (old.get("order").get(progress).asInt() == lever) {
			progress++;
			data.put("progress", progress);
			solved = progress == n;
			data.put("solved", solved);
			event.put("type", solved ? "unlock" : "pull");
		} else {
			data.put("progress", 0);
			data.put("wrong", old.path("wrong").asInt() + 1);
			event.put("type", "wrong");
		}
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.set("types", d.get("types").deepCopy());
		view.put("progress", d.path("progress").asInt());
		view.put("wrong", d.path("wrong").asInt());
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.set("order", d.get("order").deepCopy());
		}
		return view;
	}
}
