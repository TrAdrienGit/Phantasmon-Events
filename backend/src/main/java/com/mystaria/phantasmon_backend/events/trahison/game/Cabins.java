package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.Optional;

import com.mystaria.phantasmon_backend.events.trahison.layout.Cabin;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutValidator;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;

/** Which cabin belongs to whom (T2.4): pure lookups in the plan. */
final class Cabins {

	private Cabins() {
	}

	/** The cabin that serves the colour pair of {@code door} on the pair side or the solo side. */
	static Optional<Cabin> of(LayoutContent plan, Door door, boolean pairSide) {
		String serves = LayoutValidator.pairId(door);
		String side = pairSide ? "pair" : "solo";
		return plan.cabins().stream().filter(c -> serves.equals(c.serves()) && side.equals(c.side())).findFirst();
	}

	/** The cabin whose inside is this zone, if any. */
	static Optional<Cabin> ofZone(LayoutContent plan, String zone) {
		if (zone == null) {
			return Optional.empty();
		}
		return plan.cabins().stream().filter(c -> zone.equals(c.zone())).findFirst();
	}
}
