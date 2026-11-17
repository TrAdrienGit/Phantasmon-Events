package com.mystaria.phantasmon_backend.events.trahison.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Clue;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DexCode;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.TypeChain;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.UnownText;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.WeighIn;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Tests of the Pokemon mechanisms of T8.2 (dex_code, unown_text, weigh_in, type_chain): 1000 generations each solve. */
class PokemonMechanismsTest {

	final PokemonCatalog catalog = new PokemonCatalog();
	final DexCode dex = new DexCode(catalog);
	final UnownText unown = new UnownText();
	final WeighIn weigh = new WeighIn(catalog);
	final TypeChain chain = new TypeChain(catalog);

	static ObjectNode params(String name, int value) {
		ObjectNode p = Json.object();
		p.put(name, value);
		return p;
	}

	static JsonNode code(String code) {
		ObjectNode in = Json.object();
		in.put("code", code);
		return in;
	}

	static Clue clue(MechanismState s, String role) {
		return s.clues().stream().filter(c -> c.role().equals(role)).findFirst().orElseThrow();
	}

	@Test
	void dexCodeSolvesFromItsPortraitsAndPage() {
		for (long seed = 0; seed < 1000; seed++) {
			int n = 2 + (int) (seed % 2);
			MechanismState s = dex.generate(new SplittableRandom(seed), params("count", n));
			StringBuilder code = new StringBuilder();
			Set<String> ids = new HashSet<>();
			String page = clue(s, "dex_page").value().get(0).asString();
			for (int i = 1; i <= n; i++) {
				String id = clue(s, "portrait_" + i).value().asString();
				assertTrue(ids.add(id));
				PokemonCatalog.Species sp = catalog.byId(id).orElseThrow();
				String line = "%03d %s".formatted(sp.dex(), sp.name());
				assertTrue(page.contains(line), "the page names the portrait species");
				code.append("%03d".formatted(sp.dex()));
			}
			assertEquals(code.toString(), s.data().get("code").asString());
			InputResult wrong = dex.onInput(s, 0, code("000000"));
			assertFalse(wrong.solved());
			assertEquals(1, wrong.state().data().get("wrong").asInt());
			assertTrue(dex.onInput(wrong.state(), 0, code(code.toString())).solved());
			assertFalse(dex.view(s, ViewerKind.PLAYER).has("code"));
			assertTrue(dex.view(s, ViewerKind.GAME_MASTER).has("code"));
		}
		assertThrows(IllegalArgumentException.class, () -> dex.onInput(dex.generate(new SplittableRandom(1), null), 0, code("12x")));
	}

	@Test
	void unownTextSpellsItsCode() {
		for (long seed = 0; seed < 1000; seed++) {
			int n = 3 + (int) (seed % 2);
			MechanismState s = unown.generate(new SplittableRandom(seed), params("length", n));
			String text = clue(s, "wall_text").value().asString();
			assertTrue(text.matches("[A-Z ]+"), "only letters and spaces have a glyph");
			String code = s.data().get("code").asString();
			StringBuilder spelled = new StringBuilder();
			for (char c : code.toCharArray()) {
				spelled.append(UnownText.DIGIT_WORDS[c - '0']).append(' ');
			}
			assertTrue(text.contains(spelled.toString().strip()));
			assertEquals(n, code.length());
			assertTrue(unown.onInput(s, 0, code(code)).solved());
			assertEquals(26, clue(s, "alphabet").value().get(0).asString().replaceAll("\\s", "").length());
		}
	}

	@Test
	void weighInSortsBySwaps() {
		for (long seed = 0; seed < 1000; seed++) {
			ObjectNode p = params("count", 3 + (int) (seed % 3));
			p.put("by", seed % 2 == 0 ? "weight" : "height");
			MechanismState s = weigh.generate(new SplittableRandom(seed), p);
			JsonNode answer = s.data().get("answer");
			assertFalse(s.data().get("order").equals(answer), "never starts sorted");
			MechanismState cur = s;
			boolean solved = false;
			for (int i = 0; i < answer.size() && !solved; i++) { // selection sort with swaps
				int at = -1;
				for (int j = 0; j < answer.size(); j++) {
					if (cur.data().get("order").get(j).equals(answer.get(i))) {
						at = j;
					}
				}
				if (at != i) {
					ObjectNode in = Json.object();
					in.putArray("swap").add(i).add(at);
					InputResult r = weigh.onInput(cur, 0, in);
					cur = r.state();
					solved = r.solved();
				}
			}
			assertTrue(cur.data().get("solved").asBoolean(), "seed " + seed);
			double prev = -1;
			boolean height = seed % 2 != 0;
			for (JsonNode id : answer) {
				PokemonCatalog.Species sp = catalog.byId(id.asString()).orElseThrow();
				int m = height ? sp.height() : sp.weight();
				assertTrue(m > prev, "strictly increasing measures");
				prev = m;
			}
		}
	}

	@Test
	void typeChainHasOneOrderAndSolves() {
		for (long seed = 0; seed < 1000; seed++) {
			int n = 3 + (int) (seed % 3);
			MechanismState s = chain.generate(new SplittableRandom(seed), params("count", n));
			JsonNode types = s.data().get("types");
			JsonNode order = s.data().get("order");
			// the only super effective relations among the levers are the consecutive ones of the order
			int edges = 0;
			for (int a = 0; a < n; a++) {
				for (int b = 0; b < n; b++) {
					if (a != b && catalog.efficacy(types.get(a).asString(), types.get(b).asString()) > 1.0) {
						edges++;
					}
				}
			}
			assertEquals(n - 1, edges, "seed " + seed);
			for (int k = 0; k + 1 < n; k++) {
				assertTrue(catalog.efficacy(types.get(order.get(k).asInt()).asString(), types.get(order.get(k + 1).asInt()).asString()) > 1.0);
			}
			// a wrong lever resets, the right order solves
			MechanismState cur = chain.onUse(s, 0, "lever_" + (order.get(0).asInt() + 1)).state();
			int wrongLever = order.get(2).asInt();
			InputResult wrong = chain.onUse(cur, 1, "lever_" + (wrongLever + 1));
			assertEquals(0, wrong.state().data().get("progress").asInt());
			assertEquals(1, wrong.state().data().get("wrong").asInt());
			cur = s;
			InputResult last = null;
			for (JsonNode lever : order) {
				last = chain.onUse(cur, 0, "lever_" + (lever.asInt() + 1));
				cur = last.state();
			}
			assertTrue(last.solved());
		}
	}

	@Test
	void sameSeedSameDraw() {
		assertEquals(Json.write(chain.generate(new SplittableRandom(7), null).data()),
				Json.write(chain.generate(new SplittableRandom(7), null).data()));
		assertEquals(List.of("portrait_1", "portrait_2", "dex_page"), dex.clueRoles(null));
	}
}
