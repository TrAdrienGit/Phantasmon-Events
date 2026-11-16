package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.Objects;

/** The colour and status given to one bracelet for one round. */
public record Placement(GameColor color, Status status) {
	public Placement {
		Objects.requireNonNull(color);
		Objects.requireNonNull(status);
	}
}
