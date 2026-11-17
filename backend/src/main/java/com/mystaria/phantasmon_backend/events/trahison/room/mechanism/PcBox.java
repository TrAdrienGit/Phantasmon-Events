package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * A PC box to tidy by a rule nobody tells: the box holds {@code count} well-known Pokemon in a row, a few of them already in their
 * right place (locked), the others shuffled. The players deduce the rule from the locked ones (the cards show Pokedex number,
 * name, types, weight and height) and swap the free ones until the row follows it. The rule is one of {@link #RULES}; the draw keeps
 * a row where <b>no other rule</b> agrees with the locked Pokemon, so the rule is deducible. The rule is for the game master only.
 *
 * <h2>Parameters</h2> {@code count} 5..8 (default 6) Pokemon; {@code fixed} 2..4 locked (default 3, at most {@code count - 2}).
 * <h2>Gesture</h2> {@code {"swap": [i, j]}}, two different positions, neither locked.
 */
@Component
public class PcBox implements Mechanism {

	public static final String ID = "pc_box";
	public static final String TERMINAL_ROLE = "terminal";
	static final int MIN_COUNT = 5;
	static final int MAX_COUNT = 8;
	/** The rules the box may follow. */
	public static final List<String> RULES = List.of("dex", "dex_desc", "weight", "weight_desc", "height", "name");
	static final int MAX_TRIES = 5000;

	private final PokemonCatalog catalog;

	@Autowired
	public PcBox(PokemonCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public String id() {
		return ID;
	}

	static int count(JsonNode params) {
		return CodeEntry.intParam(params, "count", 6, MIN_COUNT, MAX_COUNT);
	}

	static int fixed(JsonNode params) {
		return Math.min(CodeEntry.intParam(params, "fixed", 3, 2, 4), count(params) - 2);
	}

	@Override
	public int difficulty(JsonNode params) {
		return count(params) <= 6 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 60 + 25 * count(params);
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(TERMINAL_ROLE, "terminal", "keypad"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("count", "int", MIN_COUNT, MAX_COUNT, "6", "Pokemon in the box"),
				new MechanismParam("fixed", "int", 2, 4, "3", "Pokemon already in place (the clue to the rule)"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		CodeEntry.checkInt(params, "count", MIN_COUNT, MAX_COUNT, problems);
		CodeEntry.checkInt(params, "fixed", 2, 4, problems);
		return problems;
	}

	@Override
	public List<String> validateStep(com.mystaria.phantasmon_backend.events.trahison.room.Step step) {
		return step.slots().isEmpty() ? List.of("missing=slot:" + TERMINAL_ROLE) : List.of();
	}

	/** The species sorted by a rule. */
	List<Species> sorted(List<Species> species, String rule) {
		Comparator<Species> c = switch (rule) {
			case "dex" -> Comparator.comparingInt(Species::dex);
			case "dex_desc" -> Comparator.comparingInt(Species::dex).reversed();
			case "weight" -> Comparator.comparingInt(Species::weight);
			case "weight_desc" -> Comparator.comparingInt(Species::weight).reversed();
			case "height" -> Comparator.comparingInt(Species::height);
			case "name" -> Comparator.comparing(Species::name);
			default -> throw new IllegalArgumentException("unknown rule " + rule);
		};
		return species.stream().sorted(c).toList();
	}

	private static boolean distinctKeys(List<Species> s) {
		return s.stream().map(Species::weight).distinct().count() == s.size() && s.stream().map(Species::height).distinct().count() == s.size();
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		int n = count(params);
		int fixedCount = fixed(params);
		for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
			List<Species> pool = new ArrayList<>(catalog.famous());
			Collections.shuffle(pool, rng);
			List<Species> picked = pool.subList(0, n);
			if (!distinctKeys(picked)) {
				continue;
			}
			String rule = RULES.get(rng.nextInt(RULES.size()));
			List<Species> answer = sorted(picked, rule);
			List<Integer> positions = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				positions.add(i);
			}
			Collections.shuffle(positions, rng);
			Set<Integer> fixedAt = new HashSet<>(positions.subList(0, fixedCount));
			if (rivalAgrees(picked, rule, answer, fixedAt)) {
				continue;
			}
			List<Species> free = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				if (!fixedAt.contains(i)) {
					free.add(answer.get(i));
				}
			}
			List<Species> shuffled = new ArrayList<>(free);
			do {
				Collections.shuffle(shuffled, rng);
			} while (shuffled.equals(free));
			ArrayNode order = Json.array();
			ArrayNode answerNode = Json.array();
			ArrayNode fixedNode = Json.array();
			int f = 0;
			for (int i = 0; i < n; i++) {
				answerNode.add(answer.get(i).id());
				if (fixedAt.contains(i)) {
					order.add(answer.get(i).id());
				} else {
					order.add(shuffled.get(f++).id());
				}
			}
			positions.subList(0, fixedCount).stream().sorted().forEach(fixedNode::add);
			ObjectNode data = Json.object();
			data.set("order", order);
			data.set("answer", answerNode);
			data.set("fixed", fixedNode);
			data.put("rule", rule);
			data.put("swaps", 0);
			data.put("solved", false);
			return new MechanismState(data, List.of());
		}
		throw new IllegalStateException("no pc box with a deducible rule");
	}

	/** Whether another rule would put the same Pokemon at the locked positions (then the locked ones do not tell the rules apart). */
	private boolean rivalAgrees(List<Species> picked, String rule, List<Species> answer, Set<Integer> fixedAt) {
		for (String other : RULES) {
			if (other.equals(rule)) {
				continue;
			}
			List<Species> alt = sorted(picked, other);
			boolean agrees = true;
			for (int i : fixedAt) {
				if (!alt.get(i).id().equals(answer.get(i).id())) {
					agrees = false;
					break;
				}
			}
			if (agrees) {
				return true;
			}
		}
		return false;
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
		for (JsonNode f : old.get("fixed")) {
			if (f.asInt() == i || f.asInt() == j) {
				throw new IllegalArgumentException("swap: a locked position");
			}
		}
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		ObjectNode data = state.copyData();
		ArrayNode order = (ArrayNode) data.get("order");
		JsonNode a = order.get(i);
		order.set(i, order.get(j));
		order.set(j, a);
		data.put("swaps", old.path("swaps").asInt() + 1);
		boolean solved = order.equals(data.get("answer"));
		data.put("solved", solved);
		ObjectNode event = Json.object();
		event.put("type", solved ? "unlock" : "swap");
		event.put("i", i);
		event.put("j", j);
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		ArrayNode cards = view.putArray("order");
		for (JsonNode id : d.get("order")) {
			Species s = catalog.byId(id.asString()).orElseThrow();
			ObjectNode c = cards.addObject();
			c.put("species", s.id());
			c.put("name", s.name());
			c.put("dex", s.dex());
			c.put("weight", s.weight());
			c.put("height", s.height());
			ArrayNode types = c.putArray("types");
			s.types().forEach(types::add);
		}
		view.set("fixed", d.get("fixed").deepCopy());
		view.put("swaps", d.path("swaps").asInt());
		view.put("solved", d.path("solved").asBoolean(false));
		if (viewer == ViewerKind.GAME_MASTER) {
			view.put("rule", d.path("rule").asString());
			view.set("answer", d.get("answer").deepCopy());
		}
		return view;
	}

	/** For tests: the rules, by id, that agree with the locked Pokemon of a state. */
	public List<String> consistentRules(MechanismState state) {
		List<Species> all = new ArrayList<>();
		for (JsonNode id : state.data().get("answer")) {
			all.add(catalog.byId(id.asString()).orElseThrow());
		}
		List<String> out = new ArrayList<>();
		for (String r : RULES) {
			List<Species> alt = sorted(all, r);
			boolean ok = true;
			for (JsonNode f : state.data().get("fixed")) {
				ok &= alt.get(f.asInt()).id().equals(state.data().get("answer").get(f.asInt()).asString());
			}
			if (ok) {
				out.add(r);
			}
		}
		return out;
	}
}
