package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures.TestClock;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.DebugButton;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.InputResult;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ThreeLevers;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.TwinScreens;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The room editor's try: an admin's room outside any game ({@link RoomService#startTrial} and friends). */
class RoomTrialTest {

	final TestClock clock = new TestClock();
	final ThreeLevers levers = new ThreeLevers(clock);
	final UUID admin = UUID.randomUUID();
	final UUID other = UUID.randomUUID();
	RoomService rooms;
	List<String> told = new ArrayList<>();
	int telemetry;

	@BeforeEach
	void setUp() {
		rooms = new RoomService(new MechanismRegistry(List.of(new DebugButton(), levers, new TwinScreens())), clock);
		rooms.setStepTimeSink((room, version, step, mechanism, params, seconds, hints, members) -> telemetry++);
		rooms.addListener(new RoomListener() {
			@Override
			public void roomChanged(RoomInstance room) {
				told.add(room.hub);
			}
		});
	}

	static int[] order(ThreeLevers m, MechanismState state) {
		JsonNode o = m.view(state, ViewerKind.GAME_MASTER).get("order");
		return new int[] { o.get(0).asInt(), o.get(1).asInt(), o.get(2).asInt() };
	}

	void solveLevers(RoomInstance room, java.util.function.Consumer<String> use) {
		int[] order = order(levers, room.steps.get("pull").state);
		for (int i = 0; i < 3; i++) {
			use.accept("l" + order[i]);
			clock.now = clock.now.plusMillis(1000);
		}
	}

	@Test
	void startRunsAnIsolatedRoomForOneAdmin() {
		RoomInstance room = rooms.startTrial(admin, ThreeLeversTest.room(), 5L);
		assertTrue(room.trial);
		assertEquals(RoomService.trialHub(admin), room.hub);
		assertEquals(admin, RoomService.trialAdmin(room.hub));
		assertNull(RoomService.trialAdmin("hub"));
		assertNull(RoomService.trialAdmin("try:not-a-uuid"));
		assertEquals(List.of(0), room.members);
		assertTrue(rooms.trialView(admin).get("slots").size() > 0);
		assertEquals(List.of(room.hub), told);
		// no game room was created, nobody else has a trial
		assertTrue(rooms.rooms("hub").isEmpty());
		assertTrue(rooms.findTrial(other).isEmpty());
		assertThrows(RoomException.class, () -> rooms.skipTrial(other));
		assertThrows(RoomException.class, () -> rooms.applyTrial(other, new RoomAction.UseSlot("l1")));
	}

	@Test
	void aSinglePlayerSolvesTheThreeLeversInATrial() {
		RoomInstance room = rooms.startTrial(admin, ThreeLeversTest.room(), 31L);
		solveLevers(room, slot -> rooms.applyTrial(admin, new RoomAction.UseSlot(slot)));
		assertTrue(room.steps.get("pull").solved);
		assertTrue(room.steps.get("out").open);
		rooms.applyTrial(admin, new RoomAction.UseSlot("exit"));
		assertTrue(room.exitOpen);
		assertEquals(0, telemetry, "a trial feeds no telemetry");
		assertThrows(RoomException.class, () -> rooms.applyTrial(admin, new RoomAction.UseSlot("exit")));
	}

	@Test
	void aGameRoomNeverGetsTheSoloFlag() {
		RoomInstance room = rooms.start("hub", 0, List.of(0, 1, 2), ThreeLeversTest.room(), 31L);
		assertFalse(room.trial);
		assertFalse(room.steps.get("pull").state.data().get("solo").asBoolean());
		solveLevers(room, slot -> rooms.apply("hub", 0, 0, new RoomAction.UseSlot(slot)));
		assertFalse(room.steps.get("pull").solved, "a lone player does not solve it in a game");
	}

	@Test
	void twinScreensAcceptOnePlayerOnlyInATrial() {
		TwinScreens twin = new TwinScreens();
		ObjectNode p = Json.object();
		p.put("length", 3);
		p.put("alphabet", 8);
		MechanismState state = twin.generate(new SplittableRandom(3), p);
		JsonNode gm = twin.view(state, ViewerKind.GAME_MASTER);
		List<String> left = new ArrayList<>();
		List<String> right = new ArrayList<>();
		gm.get("left_code").forEach(n -> left.add(n.asString()));
		gm.get("right_code").forEach(n -> right.add(n.asString()));
		// a game: the same player cannot take the second seat
		MechanismState game = twin.onUse(twin.onUse(state, 4, "left").state(), 4, "right").state();
		assertEquals(-1, game.data().get("seats").get("right").asInt());
		// a try: both seats, both entries
		MechanismState s = twin.solo(state);
		s = twin.onUse(s, 0, "left").state();
		s = twin.onUse(s, 0, "right").state();
		assertEquals(0, s.data().get("seats").get("right").asInt());
		InputResult first = twin.onInput(s, 0, entry("left", right));
		InputResult second = twin.onInput(first.state(), 0, entry("right", left));
		assertTrue(second.solved());
	}

	static ObjectNode entry(String seat, List<String> symbols) {
		ObjectNode in = Json.object();
		in.put("seat", seat);
		ArrayNode array = in.putArray("symbols");
		symbols.forEach(array::add);
		return in;
	}

	@Test
	void skipSolvesTheOpenStepsOnly() {
		RoomInstance room = rooms.startTrial(admin, ThreeLeversTest.room(), 7L);
		rooms.skipTrial(admin);
		assertTrue(room.steps.get("pull").solved);
		assertFalse(room.steps.get("out").solved);
		assertTrue(room.steps.get("out").open);
		assertFalse(room.exitOpen);
		rooms.skipTrial(admin);
		assertTrue(room.exitOpen);
		assertNotEquals(null, room.finishedAt);
		rooms.skipTrial(admin); // finished: nothing more
		assertEquals(0, telemetry, "a trial feeds no telemetry");
	}

	@Test
	void resetDrawsAgainWithAnotherSeed() {
		RoomInstance first = rooms.startTrial(admin, ThreeLeversTest.room(), 7L);
		rooms.skipTrial(admin);
		RoomInstance again = rooms.resetTrial(admin, 8L);
		assertNotEquals(first, again);
		assertEquals(8L, again.seed);
		assertTrue(again.trial);
		assertFalse(again.steps.get("pull").solved);
		assertTrue(again.steps.get("pull").open);
		assertEquals(again, rooms.findTrial(admin).orElseThrow());
	}

	@Test
	void trialsAreIndependentOfGamesAndOfEachOther() {
		rooms.start("hub", 0, List.of(0, 1, 2), ThreeLeversTest.room(), 31L);
		RoomInstance mine = rooms.startTrial(admin, ThreeLeversTest.room(), 1L);
		RoomInstance theirs = rooms.startTrial(other, ThreeLeversTest.room(), 2L);
		rooms.skipTrial(admin);
		assertTrue(mine.steps.get("pull").solved);
		assertFalse(theirs.steps.get("pull").solved);
		assertFalse(rooms.find("hub", 0).orElseThrow().steps.get("pull").solved);
		rooms.removeAll("hub"); // a game ending drops its rooms, not the trials
		assertTrue(rooms.findTrial(admin).isPresent() && rooms.findTrial(other).isPresent());
		assertTrue(rooms.stopTrial(admin));
		assertFalse(rooms.stopTrial(admin));
		assertTrue(rooms.findTrial(admin).isEmpty());
		assertTrue(rooms.findTrial(other).isPresent());
	}
}
