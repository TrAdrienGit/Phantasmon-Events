package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.EnumMap;
import java.util.Map;

import com.mystaria.phantasmon_backend.events.trahison.rules.Scoring;

/** What the organiser can tune before a game. Plain data, serialized with the game. */
public class Settings {

	/** Seconds per stage; a stage missing from the map uses its default. */
	public Map<Phase, Integer> durations = new EnumMap<>(Phase.class);
	public Mode mode = Mode.AUTO;
	/** Points added (a negative number) to a living player who passed no door when the doors close. */
	public int noDoorPenalty = Scoring.NO_DOOR_PENALTY;

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

	public static Settings fast(int seconds) {
		return new Settings().allDurations(seconds);
	}
}
