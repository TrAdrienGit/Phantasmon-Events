package com.mystaria.phantasmon_backend.events.trahison.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures.TestClock;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.BattlePuzzle;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.CryMatch;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DexCode;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PcBox;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PowerSpot;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.TypeChain;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.UnownText;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.WeighIn;

import tools.jackson.databind.json.JsonMapper;

/** The Cobblemon trial room of Documentation/trahison/exemples (T8): it stays valid and every seed builds. */
class TrialRoomTest {

	static final Path FILE = Path.of("..", "Documentation", "trahison", "exemples", "salle-essai-cobblemon.json");

	static MechanismRegistry registry() {
		PokemonCatalog catalog = new PokemonCatalog();
		return new MechanismRegistry(List.of(new DebugButton(), new DexCode(catalog), new UnownText(), new WeighIn(catalog), new TypeChain(catalog),
				new CryMatch(catalog), new PcBox(catalog), new BattlePuzzle(catalog), new PowerSpot()));
	}

	@Test
	void theTrialRoomIsValidAndStartsWithEverySeed() throws Exception {
		RoomDefinition room = JsonMapper.builder().build().readValue(Files.readString(FILE), RoomDefinition.class);
		MechanismRegistry registry = registry();
		assertEquals(List.of(), RoomValidator.validate(room, registry::find));
		for (long seed = 0; seed < 30; seed++) {
			RoomService rooms = new RoomService(registry, new TestClock());
			RoomInstance r = rooms.start("hub", 0, List.of(0, 1, 2), room, seed);
			assertEquals(room.steps().size(), r.steps.size());
			assertEquals(3, r.partners.size());
			assertTrue(r.steps.values().stream().filter(s -> s.step.mechanism().equals("power_spot"))
					.allMatch(s -> !s.state.data().path("anyone").asBoolean(true)), "three powers, three members: each has a bearer");
		}
	}
}
