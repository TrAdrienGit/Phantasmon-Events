package com.mystaria.phantasmon_backend.events.trahison.room;

/** A recipe of a room: the items {@code a} and {@code b} (in either order) combine into {@code result}. */
public record Combine(String a, String b, String result) {

	/** True when this recipe is made of these two items, in either order. */
	public boolean matches(String x, String y) {
		return a.equals(x) && b.equals(y) || a.equals(y) && b.equals(x);
	}
}
