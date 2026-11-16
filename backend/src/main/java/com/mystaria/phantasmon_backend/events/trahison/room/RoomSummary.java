package com.mystaria.phantasmon_backend.events.trahison.room;

import java.time.Instant;

/** One line of the room list. */
public record RoomSummary(String id, String name, String author, int version, int difficulty, int expectedSeconds,
		int stepCount, int slotCount, boolean hasSchematic, Instant updatedAt) {
}
