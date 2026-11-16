package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.Map;

/** The measured median time of the steps of rooms (T3.6): {@code roomId -> stepId -> median}. */
public interface StepMedians {

	/** At least this many measures before a measured time replaces the estimate. */
	int MIN_MEASURES = 3;

	record Median(int count, int seconds) {
	}

	Map<String, Map<String, Median>> byRoomAndStep();
}
