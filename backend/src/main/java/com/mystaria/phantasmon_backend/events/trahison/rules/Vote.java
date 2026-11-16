package com.mystaria.phantasmon_backend.events.trahison.rules;

/** A cabin vote. A missing vote (no vote, tied up, dead bracelet) counts as {@link #ALLY}. */
public enum Vote {
	ALLY, BETRAY
}
