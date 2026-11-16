package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.List;

/**
 * What a colour door of the plan says about its threshold during DOORS (a rule, so the backend computes it; sent in the
 * views as {@code door_hints}). {@code missing}: what the players at the threshold still lack, for the closest way to open
 * the door; {@code altMissing}: another way, as close as the first, or null; both empty when the door is ready.
 * {@code incompatible}: the bracelets would satisfy the door, but not with the doors already taken.
 */
public record DoorHint(String door, int index, List<String> colors, int present, boolean ready, boolean incompatible,
		List<Need> missing, List<Need> altMissing) {

	/** One bracelet kind (colour name, {@code PAIR} or {@code SOLO}) a threshold lacks. */
	public record Need(String color, String status) {
	}

	public DoorHint {
		colors = List.copyOf(colors);
		missing = List.copyOf(missing);
		altMissing = altMissing == null ? null : List.copyOf(altMissing);
	}
}
