package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.EnumMap;
import java.util.Map;

/** What the organiser can tune before a game. Plain data, serialized with the game. */
public class Settings {

	/** Seconds per stage; a stage missing from the map uses its default. */
	public Map<Phase, Integer> durations = new EnumMap<>(Phase.class);
	public Mode mode = Mode.AUTO;
	/**
	 * How long the partner of a pair has to join the first member who entered the pair's vote cabin before it locks (D4,
	 * seconds).
	 */
	public int cabinPartnerSeconds = DEFAULT_CABIN_PARTNER_SECONDS;
	public static final int DEFAULT_CABIN_PARTNER_SECONDS = 30;
	/** How long a colour door stays open once the bracelets at its threshold satisfy it (seconds). */
	public int doorOpenSeconds = DEFAULT_DOOR_OPEN_SECONDS;
	public static final int DEFAULT_DOOR_OPEN_SECONDS = 6;
	/** How long the final door stays open once a 9-point bracelet uses the pupitre (T2.5, seconds); then it closes for ever. */
	public int finalDoorSeconds = DEFAULT_FINAL_DOOR_SECONDS;
	public static final int DEFAULT_FINAL_DOOR_SECONDS = 10;
	/**
	 * Whether a human must close his cabin and vote from inside it (T2.4). False: the old free vote, anywhere, for games
	 * played without a built map. Bots are exempt either way.
	 */
	public boolean cabinsRequired = true;
	/**
	 * Id of a room of the bank that every group plays in every round (trials, until the draw of T4); null or unknown: the
	 * built-in fallback room (one button).
	 */
	public String testRoom;

	public static int defaultDuration(Phase phase) {
		return switch (phase) {
			case PROLOGUE -> 600;
			case DOORS -> 300;
			case ROOMS -> 1800;
			case FREE_TIME -> 600;
			case VOTE -> 300;
			case RESULTS -> 180;
			case FINAL -> 600;
			case LOBBY, ENDED -> 0;
		};
	}

	public int durationSeconds(Phase phase) {
		Integer d = durations.get(phase);
		return d != null ? d : defaultDuration(phase);
	}

	/** Test helper: every timed stage lasts {@code seconds}. */
	public Settings allDurations(int seconds) {
		for (Phase p : Phase.values()) {
			if (p.timed()) {
				durations.put(p, seconds);
			}
		}
		return this;
	}

	/** Test helper: the free vote, no cabins needed. */
	public Settings withoutCabins() {
		cabinsRequired = false;
		return this;
	}

	public static Settings fast(int seconds) {
		return new Settings().allDurations(seconds);
	}
}
