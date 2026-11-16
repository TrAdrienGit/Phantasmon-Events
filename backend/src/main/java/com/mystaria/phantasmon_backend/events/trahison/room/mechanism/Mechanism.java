package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.List;
import java.util.random.RandomGenerator;

import tools.jackson.databind.JsonNode;

/**
 * The backend half of a puzzle (architecture.md section 7). A Spring bean per mechanism, collected by
 * {@link MechanismRegistry}. Everything is deterministic for a given generator: the same seed draws the same solution.
 * <p>
 * Contract: {@link #view} never contains the solution except for {@link ViewerKind#GAME_MASTER}; {@link #onInput} and
 * {@link #onUse} do not modify the state they receive; a malformed gesture raises {@link IllegalArgumentException}
 * (the service turns it into {@code ERROR_TRAHISON_BAD_INPUT}).
 */
public interface Mechanism {

	/** Stable id used in room definitions ({@code ring_dial}, {@code keypad}...). */
	String id();

	/** 1 to 3. */
	int difficulty(JsonNode params);

	/** The time a group is expected to need, in seconds, before measures correct it. */
	int expectedSeconds(JsonNode params);

	/** The roles of the clues {@link #generate} produces, so a room definition can be checked. */
	default List<String> clueRoles(JsonNode params) {
		return List.of();
	}

	/** Problems with the parameters of a definition (empty when fine); each a short machine-readable text. */
	default List<String> validateParams(JsonNode params) {
		return List.of();
	}

	/** Draws the solution and the clues. */
	MechanismState generate(RandomGenerator rng, JsonNode params);

	/** A gesture sent by a player of the group from the puzzle's screen. */
	InputResult onInput(MechanismState state, int player, JsonNode input);

	/** A player used one of the step's slots in the world ({@code role} is the role the slot has in the step). */
	default InputResult onUse(MechanismState state, int player, String role) {
		return InputResult.unchanged(state);
	}

	/** What a client may see of the state: the current positions, never the solution. */
	JsonNode view(MechanismState state, ViewerKind viewer);
}
