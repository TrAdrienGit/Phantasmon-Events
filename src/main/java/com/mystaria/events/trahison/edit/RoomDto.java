package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * A room definition as the backend stores it ({@code /events/trahison/rooms/{id}}, Documentation/trahison/protocole.md
 * section 4.3, backend {@code RoomDefinition}). Plain mutable classes read and written by Gson (camelCase fields,
 * snake_case JSON); slot positions are relative to the template's canonical minimum corner ({@link RoomTemplate}).
 * Nothing here is a game rule: the backend validates.
 */
public final class RoomDto {

	public String id;
	public String name;
	public String author;
	/** Set by the server on save: the version, and the sums of the steps' difficulty and expected time. */
	public int version;
	public int difficulty;
	public int expectedSeconds;
	public List<SlotDto> slots = new ArrayList<>();
	public List<StepDto> steps = new ArrayList<>();
	/** The id of the slot holding the room's hidden item for the free time, or null. */
	public String secret;

	public static final class P {
		public int x;
		public int y;
		public int z;

		public P() {
		}

		public P(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	public static final class SlotDto {
		public String id;
		public String kind;
		public P position;
		public String orientation;
		public P size;
	}

	public static final class StepDto {
		public String id;
		public String mechanism;
		public JsonObject params = new JsonObject();
		/** Role of the mechanism -> slot id. */
		public Map<String, String> slots = new LinkedHashMap<>();
		public List<String> needs = new ArrayList<>();
		public List<GiveDto> gives = new ArrayList<>();
		public List<String> hints = new ArrayList<>();
	}

	public static final class GiveDto {
		public String clue;
		public String slot;
		/** {@code open} (default) or {@code solved}. */
		public String when;
	}

	/** Fills what a Gson read leaves null (an older or hand-written definition). */
	RoomDto normalized() {
		slots = slots == null ? new ArrayList<>() : slots;
		steps = steps == null ? new ArrayList<>() : steps;
		for (SlotDto slot : slots) {
			slot.position = slot.position == null ? new P() : slot.position;
			slot.size = slot.size == null ? new P(1, 1, 1) : slot.size;
			slot.orientation = slot.orientation == null ? "north" : slot.orientation;
		}
		for (StepDto step : steps) {
			step.params = step.params == null ? new JsonObject() : step.params;
			step.slots = step.slots == null ? new LinkedHashMap<>() : new LinkedHashMap<>(step.slots);
			step.needs = step.needs == null ? new ArrayList<>() : new ArrayList<>(step.needs);
			step.gives = step.gives == null ? new ArrayList<>() : new ArrayList<>(step.gives);
			step.hints = step.hints == null ? new ArrayList<>() : new ArrayList<>(step.hints);
			for (GiveDto give : step.gives) {
				give.when = give.when == null ? "open" : give.when;
			}
		}
		return this;
	}

	/** One line of {@code GET /events/trahison/rooms}. */
	public static final class Summary {
		public String id;
		public String name;
		public String author;
		public int version;
		public int difficulty;
		public int expectedSeconds;
		public int stepCount;
		public int slotCount;
		public boolean hasSchematic;
		public String updatedAt;
	}
}
