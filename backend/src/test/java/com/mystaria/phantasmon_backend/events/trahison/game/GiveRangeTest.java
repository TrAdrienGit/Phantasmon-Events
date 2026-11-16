package com.mystaria.phantasmon_backend.events.trahison.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiveRangeTest {

	private static PlayerState at(double x, double y, double z) {
		PlayerState p = new PlayerState();
		p.x = x;
		p.y = y;
		p.z = z;
		return p;
	}

	@Test
	void withinFourBlocksAndTheToleranceIsNear() {
		assertTrue(GiveRange.near(at(0, 64, 0), at(3, 64, 0)));
		assertTrue(GiveRange.near(at(0, 64, 0), at(4.9, 64, 0)), "tolerance for an old position");
		assertTrue(GiveRange.near(at(0, 64, 0), at(3, 64, 4)));
	}

	@Test
	void beyondTheLimitIsFar() {
		assertFalse(GiveRange.near(at(0, 64, 0), at(5.1, 64, 0)));
		assertFalse(GiveRange.near(at(0, 64, 0), at(0, 70, 0)));
	}

	@Test
	void withoutPositionsTheSameKnownZoneIsEnough() {
		PlayerState a = new PlayerState();
		PlayerState b = new PlayerState();
		assertFalse(GiveRange.near(a, b), "neither position nor zone: not near");
		a.zone = "room_a";
		b.zone = "room_a";
		assertTrue(GiveRange.near(a, b));
		b.zone = "room_b";
		assertFalse(GiveRange.near(a, b));
	}
}
