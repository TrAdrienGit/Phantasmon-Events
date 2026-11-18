package com.mystaria.phantasmon_backend.events.trahison.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.pokemon.PokemonCatalog;
import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures.TestClock;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.BattlePuzzle;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.CodeBreaker;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.CryMatch;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DexCode;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Keypad;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PcBox;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PickupSet;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PowerSpot;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.RingDial;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.RotatingTiles;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.SafeDial;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.SlidingBlocks;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ThreeDials;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ThreeLevers;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.TwinScreens;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.TypeChain;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.UnownText;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.WeighIn;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Wiring;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The nine room templates the client's editor offers ("create from a sheet", mod resources
 * {@code assets/phantasmon_events/trahison/templates}): each one, once every slot counts as placed (the editor-only
 * {@code hint} and {@code placed} fields dropped), is a valid definition with its hiding place candidates (D228), and starts
 * with several seeds.
 */
class RoomTemplatesTest {

	static final Path DIR = Path.of("..", "src", "main", "resources", "assets", "phantasmon_events", "trahison", "templates");
	static final List<String> IDS = List.of("archives", "serre", "dortoir", "salle_des_machines", "infirmerie", "bal", "observatoire",
			"chapelle", "crypte");

	static MechanismRegistry registry() {
		PokemonCatalog catalog = new PokemonCatalog();
		return new MechanismRegistry(List.of(new DebugButton(), new ThreeDials(), new Keypad(), new PickupSet(), new RingDial(),
				new Wiring(), new CodeBreaker(), new ThreeLevers(), new DexCode(catalog), new UnownText(), new WeighIn(catalog),
				new TypeChain(catalog), new BattlePuzzle(catalog), new CryMatch(catalog), new PcBox(catalog), new PowerSpot(),
				new SlidingBlocks(), new RotatingTiles(), new SafeDial(), new TwinScreens()));
	}

	static RoomDefinition load(String id) throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();
		JsonNode tree = mapper.readTree(Files.readString(DIR.resolve(id + ".json")));
		for (JsonNode slot : tree.path("slots")) {
			((ObjectNode) slot).remove("hint");
			((ObjectNode) slot).remove("placed");
		}
		return mapper.treeToValue(tree, RoomDefinition.class);
	}

	@Test
	void everyTemplateIsAValidDefinition() throws Exception {
		MechanismRegistry registry = registry();
		try (Stream<Path> files = Files.list(DIR)) {
			assertEquals(IDS.stream().sorted().toList(), files.map(p -> p.getFileName().toString().replace(".json", "")).sorted().toList());
		}
		for (String id : IDS) {
			RoomDefinition room = load(id);
			assertEquals(id, room.id());
			assertEquals(List.of(), RoomValidator.validate(room, registry::find), id);
			int candidates = RoomSecrets.candidates(room).size();
			assertTrue(candidates == 2 || candidates == 3, id + " declares 2 or 3 hiding places, has " + candidates);
			assertEquals(List.of(), RoomValidator.warnings(room), id);
			for (Step step : room.steps()) {
				assertEquals(3, step.hints().size(), id + "/" + step.id());
			}
		}
	}

	@Test
	void everyTemplateStartsWithSeveralSeeds() throws Exception {
		MechanismRegistry registry = registry();
		for (String id : IDS) {
			RoomDefinition room = load(id);
			for (long seed = 0; seed < 8; seed++) {
				RoomService rooms = new RoomService(registry, new TestClock());
				RoomInstance r = rooms.start("hub", 0, List.of(0, 1, 2), room, seed);
				assertEquals(room.steps().size(), r.steps.size(), id);
			}
		}
	}
}
