package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomValidatorTest {

	static final com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry REGISTRY = RoomFixtures.registry();

	static List<String> codes(RoomDefinition room) {
		return RoomValidator.validate(room, REGISTRY::find).stream().map(RoomValidator.Issue::code).toList();
	}

	static RoomDefinition with(UnaryOperator<List<Step>> steps, UnaryOperator<List<Slot>> slots) {
		RoomDefinition c = RoomFixtures.chain();
		return new RoomDefinition(c.id(), c.name(), c.author(), 1, 0, 0, slots.apply(c.slots()), steps.apply(c.steps()), null);
	}

	static List<Step> replace(List<Step> steps, int i, Step s) {
		var copy = new java.util.ArrayList<>(steps);
		copy.set(i, s);
		return copy;
	}

	@Test
	void theFakeChainIsValid() {
		assertEquals(List.of(), codes(RoomFixtures.chain()));
	}

	@Test
	void duplicateStepId() {
		var r = with(s -> List.of(s.get(0), s.get(0)), UnaryOperator.identity());
		assertTrue(codes(r).contains(RoomValidator.DUPLICATE_ID));
	}

	@Test
	void needsMustExist() {
		var r = with(s -> replace(s, 1, RoomFixtures.step("s2", "three_dials", Map.of("dials", "dials"), List.of("ghost"), List.of())),
				UnaryOperator.identity());
		assertTrue(codes(r).contains(RoomValidator.UNKNOWN_STEP));
	}

	@Test
	void cycleIsFound() {
		var r = with(s -> List.of(
				RoomFixtures.step("s1", "debug_button", Map.of("press", "btn"), List.of("s3"), List.of()),
				s.get(1), s.get(2)), UnaryOperator.identity());
		assertTrue(codes(r).contains(RoomValidator.CYCLE), codes(r).toString());
	}

	@Test
	void aStepThatLeadsNowhereIsUnreachable() {
		var r = with(s -> List.of(s.get(0), s.get(1), s.get(2),
				RoomFixtures.step("dead", "debug_button", Map.of("press", "btn"), List.of("s1"), List.of())),
				UnaryOperator.identity());
		// "dead" is the last step and becomes the implicit exit only when no step has the exit role: here s3 has it.
		assertTrue(codes(r).contains(RoomValidator.UNREACHABLE), codes(r).toString());
	}

	@Test
	void noExitSlotAndNoExitRole() {
		var r = with(s -> List.of(RoomFixtures.step("s1", "debug_button", Map.of("press", "btn"), List.of(), List.of())),
				sl -> List.of(sl.get(0)));
		assertTrue(codes(r).contains(RoomValidator.NO_EXIT));
	}

	@Test
	void theLastStepOpensTheExitWhenNoStepHasTheRole() {
		var r = with(s -> List.of(s.get(0), s.get(1), RoomFixtures.step("s3", "debug_button", Map.of("press", "btn"), List.of("s2"), List.of())),
				UnaryOperator.identity());
		assertEquals(List.of(), codes(r), "an exit slot exists and the last step leads to it");
	}

	@Test
	void exitRoleNeedsAnExitSlot() {
		var r = with(s -> replace(s, 2, RoomFixtures.step("s3", "debug_button", Map.of("exit", "btn"), List.of("s2"), List.of())),
				UnaryOperator.identity());
		assertTrue(codes(r).contains(RoomValidator.BAD_KIND));
	}

	@Test
	void unknownSlotsAndMechanismsAndGives() {
		var r = with(s -> List.of(
				RoomFixtures.step("s1", "nope", Map.of("press", "missing"), List.of(), List.of(new Give("x", "missing", "later"))),
				s.get(1), s.get(2)), UnaryOperator.identity());
		var c = codes(r);
		assertTrue(c.contains(RoomValidator.UNKNOWN_MECHANISM));
		assertTrue(c.contains(RoomValidator.UNKNOWN_SLOT));
		assertTrue(c.contains(RoomValidator.BAD_GIVE));
	}

	@Test
	void clueRoleMustBelongToTheMechanism() {
		var r = with(s -> replace(s, 1, RoomFixtures.step("s2", "three_dials", Map.of("dials", "dials"), List.of("s1"),
				List.of(new Give("secret_word", "wall", null)))), UnaryOperator.identity());
		assertTrue(codes(r).contains(RoomValidator.BAD_GIVE));
	}

	@Test
	void slotsMustBeInsideThePlace() {
		var out = with(s -> s, sl -> {
			var copy = new java.util.ArrayList<>(sl);
			copy.add(new Slot("far", "wall_clue", new Pos(48, 0, 0), "north", null));
			copy.add(new Slot("high", "wall_clue", new Pos(0, 24, 0), "north", null));
			copy.add(new Slot("wide", "wall_clue", new Pos(40, 0, 0), "north", new Size(9, 1, 1)));
			copy.add(new Slot("edge", "wall_clue", new Pos(47, 23, 47), "north", new Size(1, 1, 1)));
			copy.add(new Slot("neg", "wall_clue", new Pos(-1, 0, 0), "north", null));
			return copy;
		});
		long bad = RoomValidator.validate(out, REGISTRY::find).stream().filter(i -> i.code().equals(RoomValidator.OUT_OF_BOUNDS)).count();
		assertEquals(4, bad, "the slot at 47,23,47 fits");
	}

	@Test
	void kindOrientationHintsAndIds() {
		var r = with(s -> replace(s, 0, new Step("s1", "debug_button", null, Map.of("press", "btn"), null, null,
				List.of("a", "b", "c", "d"))), sl -> {
					var copy = new java.util.ArrayList<>(sl);
					copy.add(new Slot("odd", "toaster", new Pos(1, 1, 1), "up", null));
					copy.add(new Slot("bad id!", "terminal", new Pos(1, 1, 1), "north", null));
					return copy;
				});
		var c = codes(r);
		assertTrue(c.contains(RoomValidator.BAD_HINTS));
		assertTrue(c.contains(RoomValidator.BAD_KIND));
		assertTrue(c.contains(RoomValidator.BAD_ORIENTATION));
		assertTrue(c.contains(RoomValidator.BAD_ID));
	}

	@Test
	void jsonIsSnakeCaseAndRoundTrips() {
		var mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
		String json = mapper.writeValueAsString(RoomFixtures.chain());
		assertTrue(json.contains("\"expected_seconds\":100"), json);
		assertTrue(json.contains("\"needs\":[\"s1\"]"), json);
		RoomDefinition back = mapper.readValue(json, RoomDefinition.class);
		assertEquals(RoomFixtures.chain().steps().size(), back.steps().size());
		assertEquals(List.of(), codes(back));
		// A hand-written definition with only the fields an editor must send.
		RoomDefinition minimal = mapper.readValue("""
				{"id":"m","name":"M","slots":[{"id":"e","kind":"exit","position":{"x":1,"y":0,"z":1}},
				 {"id":"b","kind":"terminal","position":{"x":2,"y":0,"z":2},"size":{"x":2,"y":2,"z":1},"orientation":"east"}],
				 "steps":[{"id":"a","mechanism":"debug_button","slots":{"press":"b"},"gives":[{"clue":"x","slot":"e"}]}]}
				""", RoomDefinition.class);
		assertEquals("open", minimal.steps().get(0).gives().get(0).when());
		assertEquals(List.of(RoomValidator.BAD_GIVE), codes(minimal));
	}
}
