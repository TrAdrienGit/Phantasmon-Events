package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures.TestClock;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.CodeBreaker;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Keypad;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.PickupSet;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.RingDial;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ThreeDials;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Wiring;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The keypad and the object hunt played through {@link RoomService} (slots, clues, the inventory) and checked by
 * {@link RoomValidator}: the whole path a group follows, and what the validator says about slot roles.
 */
class NewMechanismsRoomTest {

	static final MechanismRegistry REGISTRY = new MechanismRegistry(
			List.of(new DebugButton(), new ThreeDials(), new RingDial(), new Keypad(), new PickupSet(),
					new Wiring(), new CodeBreaker()));

	static ObjectNode hunt() {
		ObjectNode p = Json.object();
		p.put("spots", 3);
		p.put("filled", 2);
		p.put("document", "Notes of the keeper\nThe code is on the wall.");
		return p;
	}

	/** objects (pickup_set, 3 spots) -> keypad (code on the wall) -> exit button. */
	static RoomDefinition room() {
		return new RoomDefinition("hunt_room", "Hunt", "tester", 1, 3, 100,
				List.of(RoomFixtures.slot("a", "pickup", 5, 1, 2),
						RoomFixtures.slot("b", "pickup", 6, 1, 2), RoomFixtures.slot("c", "pickup", 7, 1, 2),
						RoomFixtures.slot("desk", "document", 9, 1, 2), RoomFixtures.slot("pad", "keypad", 10, 1, 4),
						RoomFixtures.slot("wall", "wall_clue", 20, 2, 0), RoomFixtures.slot("exit", "exit", 40, 0, 20)),
				List.of(new Step("hunt", "pickup_set", hunt(), Map.of(), List.of(),
								List.of(new Give("spot_1", "a", null), new Give("spot_2", "b", null), new Give("spot_3", "c", null),
										new Give("document", "desk", null)),
								List.of()),
						new Step("code", "keypad", null, Map.of("keypad", "pad"), List.of("hunt"),
								List.of(new Give("code", "wall", null)), List.of()),
						new Step("out", "debug_button", null, Map.of("exit", "exit"), List.of("code"), List.of(), List.of())),
				null);
	}

	@Test
	void theDefinitionIsValid() {
		assertEquals(List.of(), RoomValidator.validate(room(), REGISTRY::find));
	}

	@Test
	void aGroupPlaysTheHuntThenTheKeypad() {
		RoomService rooms = new RoomService(REGISTRY, new TestClock());
		rooms.start("hub", 0, List.of(0, 1), room(), 123L);
		// Only the spots the seed filled are active; the document shows from the start.
		JsonNode view = rooms.roomView("hub", 0, ViewerKind.PLAYER);
		List<String> active = new java.util.ArrayList<>();
		view.get("slots").forEach(s -> active.add(s.get("id").asString()));
		assertTrue(active.contains("desk"), active.toString());
		long spotsShown = active.stream().filter(id -> List.of("a", "b", "c").contains(id)).count();
		assertEquals(2, spotsShown, active.toString());
		assertFalse(active.contains("pad"), "the keypad's step is not open yet");
		assertFalse(Json.write(view).contains("hunt_secret"));
		// Pick everything up: that is enough (D32), the last pick up solves the step. A solved step is not notified again.
		List<String> spots = active.stream().filter(id -> List.of("a", "b", "c").contains(id)).toList();
		for (JsonNode step : rooms.roomView("hub", 0, ViewerKind.PLAYER).get("steps")) {
			if (step.get("id").asString().equals("hunt")) {
				assertEquals(0, step.get("progress").get("done").asInt()); // D61: the HUD counter rides on the step
				assertEquals(2, step.get("progress").get("total").asInt());
			}
		}
		ActionResult first = rooms.apply("hub", 0, 0, new RoomAction.PickUp(spots.get(0)));
		assertEquals(1, first.pickedItems().size());
		assertFalse(first.stepSolved());
		for (JsonNode step : rooms.roomView("hub", 0, ViewerKind.PLAYER).get("steps")) {
			if (step.get("id").asString().equals("hunt")) {
				assertEquals(1, step.get("progress").get("done").asInt());
			}
		}
		assertFalse(rooms.find("hub", 0).orElseThrow().steps.get("hunt").solved);
		ActionResult last = rooms.apply("hub", 0, 0, new RoomAction.PickUp(spots.get(1)));
		assertEquals(1, last.pickedItems().size());
		assertTrue(last.stepSolved());
		assertEquals("hunt", last.step());
		assertEquals(2, rooms.roomView("hub", 0, ViewerKind.PLAYER).get("inventory").size(), "the objects stay in the bag");
		// The keypad: its code is the clue shown on the wall; the puzzle view never holds it.
		JsonNode after = rooms.roomView("hub", 0, ViewerKind.PLAYER);
		String code = null;
		for (JsonNode slot : after.get("slots")) {
			if (slot.get("id").asString().equals("wall")) {
				code = slot.get("shows").get(0).get("clue").get("value").asString();
			}
		}
		assertTrue(code != null && code.length() == 4, String.valueOf(code));
		assertFalse(Json.write(rooms.puzzleView("hub", 0, "code", ViewerKind.PLAYER)).contains("\"" + code + "\""));
		for (char c : code.toCharArray()) {
			rooms.apply("hub", 0, 1, new RoomAction.PuzzleInput("code", key(String.valueOf(c))));
		}
		assertTrue(rooms.apply("hub", 0, 1, new RoomAction.PuzzleInput("code", key("enter"))).stepSolved());
	}

	static ObjectNode key(String key) {
		ObjectNode in = Json.object();
		in.put("key", key);
		return in;
	}

	/** wiring (scheme on the wall) -> code_breaker (nothing to find) -> exit button. */
	static RoomDefinition terminalsRoom() {
		return new RoomDefinition("terminals_room", "Terminals", "tester", 1, 3, 100,
				List.of(RoomFixtures.slot("panel", "terminal", 2, 1, 2), RoomFixtures.slot("wall", "wall_clue", 20, 2, 0),
						RoomFixtures.slot("console", "terminal", 10, 1, 4), RoomFixtures.slot("exit", "exit", 40, 0, 20)),
				List.of(new Step("wires", "wiring", null, Map.of("panel", "panel"), List.of(), List.of(new Give("scheme", "wall", null)), List.of()),
						new Step("breaker", "code_breaker", null, Map.of("terminal", "console"), List.of("wires"), List.of(), List.of()),
						new Step("out", "debug_button", null, Map.of("exit", "exit"), List.of("breaker"), List.of(), List.of())),
				null);
	}

	@Test
	void aGroupPlaysTheWiringThenTheCodeBreaker() {
		assertEquals(List.of(), RoomValidator.validate(terminalsRoom(), REGISTRY::find));
		RoomService rooms = new RoomService(REGISTRY, new TestClock());
		rooms.start("hub", 0, List.of(0, 1), terminalsRoom(), 99L);
		JsonNode view = rooms.roomView("hub", 0, ViewerKind.PLAYER);
		List<String> scheme = new java.util.ArrayList<>();
		for (JsonNode slot : view.get("slots")) {
			if (slot.get("id").asString().equals("wall")) {
				slot.get("shows").get(0).get("clue").get("value").forEach(v -> scheme.add(v.asString()));
			}
		}
		assertEquals(4, scheme.size());
		JsonNode puzzle = rooms.puzzleView("hub", 0, "wires", ViewerKind.PLAYER);
		assertFalse(Json.write(puzzle).contains("scheme"));
		List<String> right = new java.util.ArrayList<>();
		puzzle.get("view").get("right").forEach(v -> right.add(v.asString()));
		boolean[] used = new boolean[4];
		ActionResult last = null;
		for (int left = 0; left < 4; left++) {
			int target = -1;
			for (int j = 0; j < 4 && target < 0; j++) {
				if (!used[j] && right.get(j).equals(scheme.get(left))) {
					target = j;
				}
			}
			used[target] = true;
			ObjectNode in = Json.object();
			in.put("op", "connect");
			in.put("left", left);
			in.put("right", target);
			last = rooms.apply("hub", 0, left % 2, new RoomAction.PuzzleInput("wires", in));
		}
		assertTrue(last.stepSolved());
		// The terminal opens: a logic puzzle with no clue. Typing the whole alphabet order never leaks the combination.
		JsonNode terminal = rooms.puzzleView("hub", 0, "breaker", ViewerKind.PLAYER);
		assertFalse(Json.write(terminal).contains("\"code\""));
		for (String k : new String[] { "0", "1", "2", "3", "enter" }) {
			rooms.apply("hub", 0, 1, new RoomAction.PuzzleInput("breaker", key(k)));
		}
		assertEquals(1, rooms.puzzleView("hub", 0, "breaker", ViewerKind.PLAYER).get("view").get("history").size());
	}

	@Test
	void theValidatorAsksForTheWiringScheme() {
		RoomDefinition bad = new RoomDefinition("bad2", "Bad", "t", 1, 1, 1,
				List.of(RoomFixtures.slot("panel", "terminal", 2, 1, 1), RoomFixtures.slot("exit", "exit", 40, 0, 20)),
				List.of(new Step("w", "wiring", null, Map.of("panel", "panel"), List.of(), List.of(), List.of()),
						new Step("out", "debug_button", null, Map.of("exit", "exit"), List.of("w"), List.of(), List.of())),
				null);
		var issues = RoomValidator.validate(bad, REGISTRY::find);
		assertTrue(issues.stream().anyMatch(i -> i.code().equals(RoomValidator.BAD_GIVE) && i.info().equals("missing=scheme;when=open")), issues.toString());
	}

	@Test
	void theValidatorChecksRolesAndKinds() {
		// A keypad step on a lever, a role the mechanism does not know, and a keypad step whose code is never shown.
		RoomDefinition bad = new RoomDefinition("bad", "Bad", "t", 1, 1, 1,
				List.of(RoomFixtures.slot("lever", "lever", 1, 1, 1), RoomFixtures.slot("pad", "keypad", 2, 1, 1),
						RoomFixtures.slot("exit", "exit", 40, 0, 20)),
				List.of(new Step("k1", "keypad", null, Map.of("keypad", "lever"), List.of(), List.of(new Give("code", "pad", null)), List.of()),
						new Step("k2", "keypad", null, Map.of("dials", "pad"), List.of("k1"), List.of(new Give("code", "pad", null)), List.of()),
						new Step("k3", "keypad", null, Map.of("exit", "exit"), List.of("k2"), List.of(), List.of())),
				null);
		var issues = RoomValidator.validate(bad, REGISTRY::find);
		assertTrue(issues.stream().anyMatch(i -> i.code().equals(RoomValidator.BAD_KIND) && i.ids().equals(List.of("k1", "lever"))
				&& i.info().equals("role=keypad;needs=keypad|terminal")), issues.toString());
		assertTrue(issues.stream().anyMatch(i -> i.code().equals(RoomValidator.BAD_KIND) && i.ids().equals(List.of("k2", "pad"))
				&& i.info().equals("role=dials;unknown")), issues.toString());
		assertTrue(issues.stream().anyMatch(i -> i.code().equals(RoomValidator.BAD_GIVE) && i.ids().equals(List.of("k3"))
				&& i.info().equals("missing=code;when=open")), issues.toString());
		assertEquals(3, issues.size(), issues.toString());
	}

	@Test
	void everyMechanismDeclaresItsSlotRoles() {
		assertEquals(List.of("press"), new DebugButton().slotRoles().stream().map(r -> r.role()).toList());
		assertEquals(List.of("dials"), new ThreeDials().slotRoles().stream().map(r -> r.role()).toList());
		assertEquals(List.of("terminal"), new RingDial().slotRoles().stream().map(r -> r.role()).toList());
		for (var m : REGISTRY.all()) {
			for (var r : m.slotRoles()) {
				assertTrue(RoomValidator.KINDS.containsAll(r.kinds()), m.id() + " " + r);
				assertFalse(r.kinds().contains("exit"), "exit is the implicit role's kind");
			}
		}
	}
}
