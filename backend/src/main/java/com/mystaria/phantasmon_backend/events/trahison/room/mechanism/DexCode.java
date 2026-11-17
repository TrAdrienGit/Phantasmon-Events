package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog.Species;
import com.mystaria.phantasmon_backend.events.trahison.room.Give;
import com.mystaria.phantasmon_backend.events.trahison.room.Step;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Statues or portraits of well-known Pokemon; the code is made of their Pokedex numbers, each on three digits, in the
 * order of the portraits. The portraits are clues {@code portrait_1}..{@code portrait_n} ({@link ClueType#TEXT}, value: the
 * Cobblemon species id, drawn by the client with the species model), and a {@code dex_page} document (a damaged
 * Pokedex page: {@code number name} for the portrait species and some more, shuffled) is the hint for players who do not
 * know the numbers. The players type the code on a terminal of the step.
 *
 * <h2>Parameters</h2>
 * <ul><li>{@code count} 2..3 (default 2): number of portraits, so 6 or 9 digits.</li></ul>
 * <h2>Gesture</h2> {@code {"code": "025133"}} (see {@link CodeEntry}).
 * <h2>Generation</h2> Species drawn from the catalogue's famous list, distinct. Always solvable: the number of each portrait is in the
 * Pokedex page.
 */
@Component
public class DexCode implements Mechanism {

	public static final String ID = "dex_code";
	public static final String PORTRAIT_PREFIX = "portrait_";
	public static final String PAGE_ROLE = "dex_page";
	public static final String TERMINAL_ROLE = "terminal";
	static final int MIN_COUNT = 2;
	static final int MAX_COUNT = 3;
	static final int DECOYS = 4;

	private final PokemonCatalog catalog;

	@Autowired
	public DexCode(PokemonCatalog catalog) {
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
		return count(params) == 2 ? 1 : 2;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		return 60 + 40 * count(params);
	}

	@Override
	public List<String> clueRoles(JsonNode params) {
		List<String> roles = new ArrayList<>();
		for (int i = 1; i <= count(params); i++) {
			roles.add(PORTRAIT_PREFIX + i);
		}
		roles.add(PAGE_ROLE);
		return roles;
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(TERMINAL_ROLE, "keypad", "terminal"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(new MechanismParam("count", "int", MIN_COUNT, MAX_COUNT, String.valueOf(MIN_COUNT),
				"Number of portraits; the code is their Pokedex numbers on three digits each"));
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
		for (String role : clueRoles(step.params())) {
			if (step.gives().stream().noneMatch(g -> role.equals(g.clue()) && Give.OPEN.equals(g.when()))) {
				problems.add("missing=" + role + ";when=open");
			}
		}
		if (step.slots().isEmpty()) {
			problems.add("missing=slot:" + TERMINAL_ROLE);
		}
		return problems;
	}

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		int n = count(params);
		List<Species> pool = new ArrayList<>(catalog.famous());
		Collections.shuffle(pool, rng);
		List<Species> picked = new ArrayList<>(pool.subList(0, n));
		List<Species> page = new ArrayList<>(pool.subList(0, n + DECOYS));
		Collections.shuffle(page, rng);
		StringBuilder code = new StringBuilder();
		List<Clue> clues = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			code.append("%03d".formatted(picked.get(i).dex()));
			clues.add(Clue.text(PORTRAIT_PREFIX + (i + 1), picked.get(i).id()));
		}
		StringBuilder text = new StringBuilder();
		for (Species s : page) {
			text.append("%03d %s\n".formatted(s.dex(), s.name()));
		}
		clues.add(Clue.document(PAGE_ROLE, List.of(text.toString().strip())));
		ObjectNode data = Json.object();
		data.put("code", code.toString());
		data.put("wrong", 0);
		data.put("solved", false);
		return new MechanismState(data, clues);
	}

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		return CodeEntry.submit(state, input, MAX_COUNT * 3);
	}

	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode view = CodeEntry.view(state, viewer);
		view.put("length", state.data().path("code").asString("").length());
		return view;
	}
}
