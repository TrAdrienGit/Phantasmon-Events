package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D228: the hiding place of the free time is drawn by the seed among the room's candidates. */
class RoomSecretsTest {

	private static Slot slot(String id, String kind) {
		return new Slot(id, kind, new Pos(1, 0, 1), "north", null);
	}

	private static RoomDefinition room(String legacy, Slot... slots) {
		return new RoomDefinition("r", "R", "a", 1, 1, 1, List.of(slots), List.of(), legacy);
	}

	@Test
	void everySecretSlotIsACandidateAndOldRoomsStayValid() {
		RoomDefinition many = room(null, slot("b", "secret"), slot("a", "secret"), slot("c", "wall_clue"), slot("d", "secret"));
		assertEquals(List.of("a", "b", "d"), RoomSecrets.candidates(many));
		RoomDefinition legacy = room("c", slot("c", "wall_clue"), slot("x", "wall_clue"));
		assertEquals(List.of("c"), RoomSecrets.candidates(legacy)); // the legacy field still names one
		assertTrue(RoomSecrets.draw(room(null, slot("x", "wall_clue")), 7).isEmpty());
		assertTrue(RoomValidator.validate(legacy, null).stream().noneMatch(i -> i.code().equals(RoomValidator.UNKNOWN_SLOT)));
	}

	@Test
	void theDrawIsSeededStableAndIndependentOfTheAuthoringOrder() {
		RoomDefinition one = room(null, slot("a", "secret"), slot("b", "secret"), slot("c", "secret"));
		RoomDefinition other = room(null, slot("c", "secret"), slot("a", "secret"), slot("b", "secret"));
		Set<String> seen = new HashSet<>();
		for (long seed = 0; seed < 60; seed++) {
			String drawn = RoomSecrets.draw(one, seed).orElseThrow();
			assertEquals(drawn, RoomSecrets.draw(one, seed).orElseThrow());
			assertEquals(drawn, RoomSecrets.draw(other, seed).orElseThrow());
			seen.add(drawn);
		}
		assertEquals(Set.of("a", "b", "c"), seen, "the seed really moves the hiding place");
	}

	@Test
	void onlyTheDrawnCandidateYieldsTheSecret() {
		RoomDefinition def = room(null, slot("a", "secret"), slot("b", "secret"), slot("c", "secret"));
		for (long seed = 0; seed < 20; seed++) {
			RoomInstance inst = new RoomInstance("hub", 1, 0, def, seed, List.of(0, 1, 2));
			assertEquals(RoomSecrets.draw(def, seed).orElseThrow(), inst.activeSecret);
			List<String> yielding = new ArrayList<>();
			for (String id : List.of("a", "b", "c", "nope")) {
				if (inst.yieldsSecret(id)) {
					yielding.add(id);
				}
			}
			assertEquals(List.of(inst.activeSecret), yielding);
		}
		RoomInstance none = new RoomInstance("hub", 1, 0, room(null, slot("x", "wall_clue")), 1, List.of(0));
		assertNull(none.activeSecret);
		assertFalse(none.yieldsSecret("x"));
		assertFalse(none.yieldsSecret(null));
	}

	@Test
	void theGameMasterViewNamesTheLiveOneAndPlayersNeverSeeIt() {
		RoomDefinition def = room(null, slot("a", "secret"), slot("b", "secret"));
		RoomInstance inst = new RoomInstance("hub", 1, 0, def, 3, List.of(0));
		inst.startedAt = java.time.Instant.EPOCH;
		inst.lastProgressAt = java.time.Instant.EPOCH;
		var gm = RoomViews.room(inst, com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind.GAME_MASTER);
		assertEquals(inst.activeSecret, gm.get("secret").get("active").asString());
		assertEquals(2, gm.get("secret").get("candidates").size());
		var player = RoomViews.room(inst, com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind.PLAYER);
		assertFalse(player.has("secret"));
		assertFalse(player.toString().contains("\"active\""));
	}

	@Test
	void aSingleCandidateWarnsButNeverRefuses() {
		var one = RoomSecrets.candidates(room(null, slot("a", "secret")));
		assertEquals(1, one.size());
		assertEquals(List.of(RoomValidator.FEW_SECRET_CANDIDATES), RoomValidator.warnings(room(null, slot("a", "secret"))).stream().map(i -> i.code()).toList());
		assertTrue(RoomValidator.warnings(room(null, slot("a", "secret"), slot("b", "secret"))).isEmpty());
		assertTrue(RoomValidator.warnings(room(null, slot("a", "wall_clue"))).isEmpty(), "no secret at all is fine");
	}
}
