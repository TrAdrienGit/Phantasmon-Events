package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mystaria.phantasmon_backend.events.trahison.room.RoomFixtures.TestClock;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T3.6: the automatic hints (10, 18, 25 minutes without progress) and the step times sent to the telemetry. */
class RoomHintsTest {

	static final String HUB = "hub";
	static final long[] DELAYS = RoomService.DEFAULT_HINT_DELAYS;

	TestClock clock;
	RoomService rooms;

	@BeforeEach
	void setUp() {
		clock = new TestClock();
		rooms = new RoomService(RoomFixtures.registry(), clock);
	}

	List<String> hintsInView(ViewerKind kind) {
		List<String> out = new ArrayList<>();
		for (JsonNode step : rooms.roomView(HUB, 0, kind).get("steps")) {
			step.get("hints").forEach(h -> out.add(h.asString()));
		}
		return out;
	}

	@Test
	void theDefaultDelaysAreTenEighteenAndTwentyFiveMinutes() {
		assertArrayEquals(new long[] { 600, 1080, 1500 }, DELAYS);
	}

	@Test
	void aHintComesAtEachDelayWithoutProgressAndTheViewCarriesIt() {
		rooms.start(HUB, 0, List.of(0, 1), RoomFixtures.chain(), 5L);
		clock.now = clock.now.plusSeconds(599);
		assertTrue(rooms.tickHints(DELAYS).isEmpty(), "not yet");
		assertTrue(hintsInView(ViewerKind.PLAYER).isEmpty());
		clock.now = clock.now.plusSeconds(1);
		List<RoomService.GivenHint> first = rooms.tickHints(DELAYS);
		assertEquals(1, first.size());
		assertEquals("vague", first.get(0).hint().text());
		assertEquals(List.of("vague"), hintsInView(ViewerKind.PLAYER));
		assertTrue(rooms.tickHints(DELAYS).isEmpty(), "once per delay");
		clock.now = clock.now.plusSeconds(480); // 1080 s
		assertEquals("less vague", rooms.tickHints(DELAYS).get(0).hint().text());
		clock.now = clock.now.plusSeconds(420); // 1500 s
		RoomService.GivenHint third = rooms.tickHints(DELAYS).get(0);
		assertEquals("direct", third.hint().text());
		assertEquals(3, third.hint().rank());
		clock.now = clock.now.plusSeconds(3600);
		assertTrue(rooms.tickHints(DELAYS).isEmpty(), "up to three hints");
		assertEquals(List.of("vague", "less vague", "direct"), hintsInView(ViewerKind.SPECTATOR));
	}

	@Test
	void progressStartsTheSeriesAgain() {
		rooms.start(HUB, 0, List.of(0, 1), RoomFixtures.chain(), 5L);
		clock.now = clock.now.plusSeconds(600);
		assertEquals(1, rooms.tickHints(DELAYS).size());
		// the group presses the button: s1 is solved, s2 opens (it has hints of its own)
		rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"));
		clock.now = clock.now.plusSeconds(599);
		assertTrue(rooms.tickHints(DELAYS).isEmpty(), "the delay counts from the progress");
		clock.now = clock.now.plusSeconds(1);
		List<RoomService.GivenHint> given = rooms.tickHints(DELAYS);
		assertEquals(1, given.size());
		assertEquals("s2", given.get(0).hint().step());
		assertEquals("vague", given.get(0).hint().text());
	}

	@Test
	void thePauseStopsTheCountdownAndItsTimeIsAddedBack() {
		rooms.start(HUB, 0, List.of(0, 1), RoomFixtures.chain(), 5L);
		clock.now = clock.now.plusSeconds(500);
		rooms.pauseHints(HUB);
		clock.now = clock.now.plusSeconds(3000); // a long pause
		assertTrue(rooms.tickHints(DELAYS).isEmpty(), "no hint while paused");
		rooms.resumeHints(HUB);
		assertTrue(rooms.tickHints(DELAYS).isEmpty(), "500 s of play so far, not 3500");
		clock.now = clock.now.plusSeconds(99);
		assertTrue(rooms.tickHints(DELAYS).isEmpty());
		clock.now = clock.now.plusSeconds(1); // 600 s of play
		assertEquals(1, rooms.tickHints(DELAYS).size());
		// a pause after a hint does not repeat it
		rooms.pauseHints(HUB);
		clock.now = clock.now.plusSeconds(100);
		rooms.resumeHints(HUB);
		assertTrue(rooms.tickHints(DELAYS).isEmpty());
	}

	@Test
	void aFinishedRoomOrAReplayGetsNoHint() {
		rooms.start(HUB, 0, List.of(0, 1), RoomFixtures.chain(), 5L);
		rooms.forceSolve(HUB, 0);
		clock.now = clock.now.plusSeconds(5000);
		assertTrue(rooms.tickHints(DELAYS).isEmpty());
	}

	@Test
	void theTickerReadsMinutesAndFallsBackOnJunk() {
		assertArrayEquals(new long[] { 60, 120 }, RoomHintTicker.parse("1, 2"));
		assertArrayEquals(new long[] { 60, 120, 180 }, RoomHintTicker.parse("3,2,1,9"));
		assertArrayEquals(DELAYS, RoomHintTicker.parse("soon"));
		assertArrayEquals(DELAYS, RoomHintTicker.parse(""));
	}

	@Test
	void aSolvedStepIsMeasuredWithItsTimeHintsAndGroupSize() {
		List<String> seen = new ArrayList<>();
		rooms.setStepTimeSink((room, version, step, mechanism, params, seconds, hints, group) ->
				seen.add(room + "/" + step + "/" + mechanism + "/" + seconds + "/" + hints + "/" + group));
		rooms.start(HUB, 0, List.of(0, 1, 2), RoomFixtures.chain(), 5L);
		clock.now = clock.now.plusSeconds(42);
		rooms.giveHint(HUB, 0);
		rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"));
		assertEquals(List.of("test_room/s1/debug_button/42/1/3"), seen);
		// forcing a room (debug, bots, end of ROOMS) is not a measure
		rooms.forceSolve(HUB, 0);
		assertEquals(1, seen.size());
	}

	@Test
	void aReplayIsNotMeasured() {
		List<String> seen = new ArrayList<>();
		rooms.setStepTimeSink((room, version, step, mechanism, params, seconds, hints, group) -> seen.add(step));
		rooms.start(HUB, 0, List.of(0, 1, 2), RoomFixtures.chain(), 5L);
		rooms.forceSolve(HUB, 0);
		rooms.apply(HUB, 0, 0, new RoomAction.Replay(), true);
		rooms.apply(HUB, 0, 0, new RoomAction.UseSlot("btn"), true);
		assertTrue(seen.isEmpty(), seen.toString());
	}
}
