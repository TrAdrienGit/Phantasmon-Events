package com.mystaria.phantasmon_backend.events.trahison.pokemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PokemonCatalogTest {

	private final PokemonCatalog catalog = new PokemonCatalog();

	@Test
	void knownSpeciesValues() {
		PokemonCatalog.Species pikachu = catalog.byId("pikachu").orElseThrow();
		assertEquals(25, pikachu.dex());
		assertEquals(List.of("electric"), pikachu.types());
		assertEquals(60, pikachu.weight());
		assertEquals(4, pikachu.height());
		assertEquals(List.of("pikachu", "raichu", "pichu"), pikachu.line()); // dex order
		PokemonCatalog.Species bulbasaur = catalog.byDex(1).orElseThrow();
		assertEquals("bulbasaur", bulbasaur.id());
		assertEquals(List.of("grass", "poison"), bulbasaur.types());
		assertEquals(List.of("bulbasaur", "ivysaur", "venusaur"), bulbasaur.line());
		assertEquals(386, catalog.all().size());
		assertTrue(catalog.byDex(387).isEmpty());
	}

	@Test
	void typeChart() {
		assertEquals(18, catalog.types().size());
		assertEquals(2.0, catalog.efficacy("fire", "grass"));
		assertEquals(0.5, catalog.efficacy("fire", "water"));
		assertEquals(0.0, catalog.efficacy("ground", "flying"));
		assertEquals(0.0, catalog.efficacy("normal", "ghost"));
		assertEquals(1.0, catalog.efficacy("normal", "normal"));
		assertEquals(4.0, catalog.efficacy("ice", List.of("dragon", "flying")));
	}

	@Test
	void famousSpeciesExist() {
		assertFalse(catalog.famous().isEmpty());
		assertTrue(catalog.famous().stream().anyMatch(s -> s.id().equals("pikachu")));
		assertTrue(catalog.famous().stream().allMatch(s -> s != null && s.dex() <= 386));
	}
}
