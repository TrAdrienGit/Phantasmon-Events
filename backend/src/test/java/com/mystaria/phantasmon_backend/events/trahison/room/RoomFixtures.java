package com.mystaria.phantasmon_backend.events.trahison.room;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Clue;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Mechanism;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ThreeDials;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

final class RoomFixtures {

	static final class TestClock extends Clock {
		Instant now = Instant.parse("2026-11-16T20:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	/** Using its slot solves it and it produces an item clue {@code brass_key} (role {@code key}). */
	static final class KeyBox implements Mechanism {
		@Override
		public String id() {
			return "key_box";
		}

		@Override
		public int difficulty(JsonNode params) {
			return 2;
		}

		@Override
		public int expectedSeconds(JsonNode params) {
			return 30;
		}

		@Override
		public List<String> clueRoles(JsonNode params) {
			return List.of("key");
		}

		@Override
		public MechanismState generate(RandomGenerator rng, JsonNode params) {
			return new MechanismState(Json.object(), List.of(Clue.item("key", "brass_key")));
		}

		@Override
		public InputResult onInput(MechanismState state, int player, JsonNode input) {
			return InputResult.unchanged(state);
		}

		@Override
		public JsonNode view(MechanismState state, ViewerKind viewer) {
			return Json.object();
		}
	}

	static MechanismRegistry registry() {
		return new MechanismRegistry(List.of(new DebugButton(), new ThreeDials(), new KeyBox()));
	}

	static Slot slot(String id, String kind, int x, int y, int z) {
		return new Slot(id, kind, new Pos(x, y, z), "north", null);
	}

	static Step step(String id, String mechanism, Map<String, String> slots, List<String> needs, List<Give> gives) {
		return new Step(id, mechanism, null, slots, needs, gives, List.of("vague", "less vague", "direct"));
	}

	/** press (debug_button) -> dials (three_dials, clue on the wall) -> exit button. */
	static RoomDefinition chain() {
		return new RoomDefinition("test_room", "Test room", "tester", 1, 3, 100,
				List.of(slot("btn", "terminal", 2, 1, 2), slot("dials", "terminal", 10, 1, 4), slot("wall", "wall_clue", 20, 2, 0),
						slot("exit", "exit", 40, 0, 20)),
				List.of(step("s1", "debug_button", Map.of("press", "btn"), List.of(), List.of()),
						step("s2", "three_dials", Map.of("dials", "dials"), List.of("s1"),
								List.of(new Give("code", "wall", null))),
						step("s3", "debug_button", Map.of("exit", "exit"), List.of("s2"), List.of())),
				null);
	}
}
