package com.mystaria.events.trahison.edit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D228: the editor warns (never blocks) when a room declares a single hiding place candidate. */
class RoomSecretCandidatesTest {

	private static RoomDto.SlotDto slot(String id, String kind) {
		RoomDto.SlotDto s = new RoomDto.SlotDto();
		s.id = id;
		s.kind = kind;
		s.orientation = "north";
		s.position = new RoomDto.P(1, 1, 1);
		s.size = new RoomDto.P(1, 1, 1);
		return s;
	}

	private static long warnings(RoomDto room) {
		return RoomChecks.check(room).stream().filter(i -> i.code().equals("LOCAL_FEW_SECRET_CANDIDATES")).count();
	}

	@Test
	void oneCandidateWarnsTwoOrNoneDoNot() {
		RoomDto room = new RoomDto();
		room.id = "r";
		room.name = "R";
		room.slots.add(slot("a", "secret"));
		assertEquals(1, warnings(room));
		assertTrue(RoomChecks.check(room).stream().filter(i -> i.code().equals("LOCAL_FEW_SECRET_CANDIDATES")).allMatch(RoomChecks.Issue::warning));
		room.slots.add(slot("b", "secret"));
		assertEquals(0, warnings(room));
		room.slots.clear();
		room.slots.add(slot("c", "wall_clue"));
		assertEquals(0, warnings(room));
		room.secret = "c"; // the legacy flag makes a candidate whatever the kind: one, so it warns
		assertEquals(1, warnings(room));
	}
}
