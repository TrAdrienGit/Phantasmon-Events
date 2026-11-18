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
	/**
	 * The round the room belongs to (1 to 3), 0 for a room played outside a game's rounds. Rooms of earlier rounds stay in the
	 * hub (D8: the free time visits every room opened so far), so a room is addressed by (round, group).
	 */
	public final int round;
	/** Group index of the round (0..2, as the doors). */
	public final int group;
	public final RoomDefinition definition;
	public final long seed;
	/**
	 * D228: the one candidate hiding place (slot id) of the free time this room plays, drawn from {@link #seed}; null when the
	 * room declares none. The other candidates are inert. A replay (D8) keeps it, so the object hidden there stays reachable; the
	 * snapshot stores it for that reason (an old snapshot without it recomputes it from the seed).
	 */
	public String activeSecret;
	/** Player indices of the group: only they may act. */
	public final List<Integer> members;
	/** In definition order. */
	public final Map<String, StepRuntime> steps = new LinkedHashMap<>();
	/** Item ids the group carries (one shared bag), in pick-up order. */
	public final List<String> inventory = new ArrayList<>();
	/**
	 * Parallel to {@link #inventory}: the player index who carries each item, -1 when nobody in particular (a bag restored
	 * from before holders existed). Everyone in the group sees the whole bag; only the holder gives, uses or combines an
	 * item (a holder of -1: any member).
	 */
	public final List<Integer> holders = new ArrayList<>();
	/** The partner Pokemon of each member (T8.3), drawn when the room is built; public to the group. */
	public final List<com.mystaria.phantasmon_backend.events.trahison.pokemon.Partner> partners = new ArrayList<>();
	/**
	 * An admin's try of the room editor (never a game room): one player, cooperative mechanisms accept a single player, no
	 * telemetry. Set only by {@link RoomService#startTrial}.
	 */
	public boolean trial;
	/** Where the room stands in the hub (null when the plan had no place for it). */
	public RoomPlace place;
	/** {@code stepId/clueRole} of the item clues already picked up. */
	public final Set<String> picked = new HashSet<>();
	public Instant startedAt;
	/** The last time something moved forward (a step solved, an item picked up); the hint timer starts from it. */
	public Instant lastProgressAt;
	/** When the last step was solved; null until then. */
	public Instant finishedAt;
	/** The exit is open (every step solved). */
	public boolean exitOpen;
	/** The {@link #lastProgressAt} the automatic hint counter {@link #autoHints} was counted from (T3.6). */
	public Instant autoHintMark;
	/** Automatic hints given since {@link #autoHintMark}. */
	public int autoHints;
	/** How many times the room was put back to a fresh state in the free time (D8); 0 for the round's own play. */
	public int replays;

	RoomInstance(String hub, int round, int group, RoomDefinition definition, long seed, List<Integer> members) {
		this.hub = hub;
		this.round = round;
		this.group = group;
		this.definition = definition;
		this.seed = seed;
		this.activeSecret = RoomSecrets.draw(definition, seed).orElse(null);
		this.members = List.copyOf(members);
	}

	/** D228: whether searching this slot yields the room's secret: only the drawn candidate does. */
	public boolean yieldsSecret(String slotId) {
		return slotId != null && slotId.equals(activeSecret);
	}

	/** Puts an item in the bag, carried by {@code holder}. */
	void addItem(String item, int holder) {
		inventory.add(item);
		holders.add(holder);
	}

	/** Index in the bag of the first copy of {@code item} that {@code player} may handle, or -1. */
	int indexOfHeld(String item, int player) {
		return indexOfHeld(item, player, -1);
	}

	/** As {@link #indexOfHeld(String, int)}, ignoring the entry at {@code skip}. */
	int indexOfHeld(String item, int player, int skip) {
		for (int i = 0; i < inventory.size(); i++) {
			if (i == skip) {
				continue;
			}
			int h = i < holders.size() ? holders.get(i) : -1;
			if (inventory.get(i).equals(item) && (h == player || h < 0)) {
				return i;
			}
		}
		return -1;
	}

	void removeItemAt(int index) {
		inventory.remove(index);
		if (index < holders.size()) {
			holders.remove(index);
		}
	}

	public int stepsDone() {
		int n = 0;
		for (StepRuntime s : steps.values()) {
			n += s.solved ? 1 : 0;
		}
		return n;
	}
}
