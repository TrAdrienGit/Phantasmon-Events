package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.List;
import java.util.Optional;

/**
 * A room as authored in the editor (architecture.md section 7), stored as JSON in {@code trahison_rooms.data}; the
 * blocks are a separate file. {@code version}, {@code difficulty} and {@code expectedSeconds} are set by the server on
 * save (the version counts saves; difficulty and time are the sums over the steps). {@code secret} is the id of the
 * slot holding the room's hidden item for the free time (may be null). {@code combines} are the room's recipes (item a +
 * item b = item result), none by default.
 */
public record RoomDefinition(String id, String name, String author, Integer version, Integer difficulty, Integer expectedSeconds,
		List<Slot> slots, List<Step> steps, String secret, List<Combine> combines) {

	public RoomDefinition {
		version = version == null ? 0 : version;
		difficulty = difficulty == null ? 0 : difficulty;
		expectedSeconds = expectedSeconds == null ? 0 : expectedSeconds;
		slots = slots == null ? List.of() : List.copyOf(slots);
		steps = steps == null ? List.of() : List.copyOf(steps);
		combines = combines == null ? List.of() : List.copyOf(combines);
	}

	/** A definition without recipes. */
	public RoomDefinition(String id, String name, String author, Integer version, Integer difficulty, Integer expectedSeconds,
			List<Slot> slots, List<Step> steps, String secret) {
		this(id, name, author, version, difficulty, expectedSeconds, slots, steps, secret, null);
	}

	public Optional<Slot> slot(String slotId) {
		return slots.stream().filter(s -> s.id().equals(slotId)).findFirst();
	}

	/** The recipe of these two items (in either order), if the room has one. */
	public Optional<Combine> combine(String x, String y) {
		return combines.stream().filter(c -> c.matches(x, y)).findFirst();
	}

	public Optional<Step> step(String stepId) {
		return steps.stream().filter(s -> s.id().equals(stepId)).findFirst();
	}

	/** A copy with the server-computed fields. */
	public RoomDefinition withComputed(int newVersion, int newDifficulty, int newExpectedSeconds) {
		return new RoomDefinition(id, name, author, newVersion, newDifficulty, newExpectedSeconds, slots, steps, secret, combines);
	}

	public RoomDefinition withId(String newId) {
		return new RoomDefinition(newId, name, author, version, difficulty, expectedSeconds, slots, steps, secret, combines);
	}
}
