package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.List;

import tools.jackson.databind.node.ObjectNode;

/**
 * What a mechanism holds for one step of one room: a JSON document ({@link #data()}: the solution and the progress, so a
 * state can be stored as is) and the clues it produced. States are values: {@link Mechanism#onInput} returns a new one
 * and never modifies the one it got.
 */
public final class MechanismState {

	private final ObjectNode data;
	private final List<Clue> clues;

	public MechanismState(ObjectNode data, List<Clue> clues) {
		this.data = data;
		this.clues = List.copyOf(clues);
	}

	/** The document; callers must treat it as read-only (use {@link #copyData()} to change it). */
	public ObjectNode data() {
		return data;
	}

	public ObjectNode copyData() {
		return data.deepCopy();
	}

	public List<Clue> clues() {
		return clues;
	}

	public MechanismState withData(ObjectNode newData) {
		return new MechanismState(newData, clues);
	}
}
