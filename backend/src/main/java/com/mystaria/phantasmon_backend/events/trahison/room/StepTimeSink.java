package com.mystaria.phantasmon_backend.events.trahison.room;

import tools.jackson.databind.JsonNode;

/** Receives the measure of a step really solved by a group (T3.6). Called with the room locked: it must be quick and must not throw. */
public interface StepTimeSink {

	void record(String roomId, int roomVersion, String stepId, String mechanism, JsonNode params, int seconds, int hintsGiven,
			int groupSize);
}
