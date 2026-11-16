package com.mystaria.phantasmon_backend.events.trahison.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A room being played by one group: the definition, the seed, the state of every step, the open steps, the room's shared
 * inventory and the times. Fields are written only by {@link RoomService}, under {@code synchronized (instance)}; a
 * {@link RoomListener} is called with the lock held.
 */
public final class RoomInstance {
	public final String hub;
	/** Group index of the round (0..2, as the doors). */
	public final int group;
	public final RoomDefinition definition;
	public final long seed;
	/** Player indices of the group: only they may act. */
	public final List<Integer> members;
	/** In definition order. */
	public final Map<String, StepRuntime> steps = new LinkedHashMap<>();
	/** Item ids the group carries (one shared bag), in pick-up order. */
	public final List<String> inventory = new ArrayList<>();
	/** {@code stepId/clueRole} of the item clues already picked up. */
	public final Set<String> picked = new HashSet<>();
	public Instant startedAt;
	/** The last time something moved forward (a step solved, an item picked up); the hint timer starts from it. */
	public Instant lastProgressAt;
	/** When the last step was solved; null until then. */
	public Instant finishedAt;
	/** The exit is open (every step solved). */
	public boolean exitOpen;

	RoomInstance(String hub, int group, RoomDefinition definition, long seed, List<Integer> members) {
		this.hub = hub;
		this.group = group;
		this.definition = definition;
		this.seed = seed;
		this.members = List.copyOf(members);
	}

	public int stepsDone() {
		int n = 0;
		for (StepRuntime s : steps.values()) {
			n += s.solved ? 1 : 0;
		}
		return n;
	}
}
