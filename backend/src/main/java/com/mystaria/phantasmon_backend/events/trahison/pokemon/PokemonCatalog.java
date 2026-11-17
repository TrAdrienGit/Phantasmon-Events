package com.mystaria.phantasmon_backend.events.trahison.pokemon;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-only Pokemon data for the Cobblemon puzzles (T8.1): dex number, types, weight, height and evolution line of the
 * species of generations 1 to 3, the type efficacy table, and the list of species everybody knows. Loaded once from
 * {@code trahison/pokemon_catalog.json} (written by {@code tools/pokemon_data.py} from Cobblemon's data). Weight is in
 * hectograms and height in decimetres, as Cobblemon stores them.
 */
@Component
public class PokemonCatalog {

	/** One species. {@code id} is Cobblemon's species id (lower case), also the key of its model. */
	public record Species(int dex, String id, String name, List<String> types, int weight, int height,
			List<String> evolvesTo, String evolvesFrom, List<String> line) {
	}

	private final Map<String, Species> byId = new LinkedHashMap<>();
	private final Map<Integer, Species> byDex = new LinkedHashMap<>();
	private final List<String> types = new ArrayList<>();
	private final Map<String, Map<String, Double>> chart = new LinkedHashMap<>();
	private final List<Species> famous = new ArrayList<>();

	public PokemonCatalog() {
		try (InputStream in = new ClassPathResource("trahison/pokemon_catalog.json").getInputStream()) {
			JsonNode root = new ObjectMapper().readTree(in);
			for (JsonNode t : root.get("types")) {
				types.add(t.asString());
			}
			for (Map.Entry<String, JsonNode> a : root.get("chart").properties()) {
				Map<String, Double> row = new LinkedHashMap<>();
				for (Map.Entry<String, JsonNode> d : a.getValue().properties()) {
					row.put(d.getKey(), d.getValue().asDouble());
				}
				chart.put(a.getKey(), row);
			}
			for (JsonNode s : root.get("species")) {
				Species sp = new Species(s.get("dex").asInt(), s.get("id").asString(), s.get("name").asString(), strings(s.get("types")),
						s.get("weight").asInt(), s.get("height").asInt(), strings(s.get("evolvesTo")),
						s.path("evolvesFrom").isString() ? s.get("evolvesFrom").asString() : null, strings(s.get("line")));
				byId.put(sp.id(), sp);
				byDex.put(sp.dex(), sp);
			}
			for (JsonNode id : root.get("famous")) {
				famous.add(byId.get(id.asString()));
			}
		} catch (IOException e) {
			throw new IllegalStateException("pokemon_catalog.json cannot be read", e);
		}
	}

	private static List<String> strings(JsonNode array) {
		List<String> out = new ArrayList<>();
		for (JsonNode n : array) {
			out.add(n.asString());
		}
		return List.copyOf(out);
	}

	public List<Species> all() {
		return List.copyOf(byId.values());
	}

	public Optional<Species> byId(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	public Optional<Species> byDex(int dex) {
		return Optional.ofNullable(byDex.get(dex));
	}

	/** The species almost everybody knows (so a puzzle does not need an expert). */
	public List<Species> famous() {
		return List.copyOf(famous);
	}

	/** The 18 type ids, alphabetical. */
	public List<String> types() {
		return List.copyOf(types);
	}

	/** Damage multiplier of an attack of type {@code attacker} on a defender of one type: 2, 1, 0.5 or 0. */
	public double efficacy(String attacker, String defender) {
		return chart.getOrDefault(attacker, Map.of()).getOrDefault(defender, 1.0);
	}

	/** The multiplier against a defender with one or two types (the product). */
	public double efficacy(String attacker, List<String> defenderTypes) {
		double m = 1.0;
		for (String t : defenderTypes) {
			m *= efficacy(attacker, t);
		}
		return m;
	}
}
