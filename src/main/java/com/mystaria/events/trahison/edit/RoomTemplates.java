package com.mystaria.events.trahison.edit;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mystaria.events.trahison.edit.RoomDto.StepDto;

/**
 * The nine room templates ("create from a sheet", Documentation/salles/*.md): one JSON per sheet in the mod's resources
 * ({@code assets/phantasmon_events/trahison/templates/<id>.json}), in the room definition format plus two editor-only fields per
 * slot, {@code hint} (the zone and the purpose, in French) and {@code placed} (false: the position is only a proposal the builder
 * confirms). The chain, the parameters, the clues and the hints of the steps are filled in; only the blocks are left to build.
 * Pure: no Minecraft class.
 */
final class RoomTemplates {

	/** The nine ids, in the order of the sheets' README (round 1, 2, 3). */
	static final List<String> IDS = List.of("archives", "serre", "dortoir", "salle_des_machines", "infirmerie", "bal", "observatoire",
			"chapelle", "crypte");

	private static final String PATH = "/assets/phantasmon_events/trahison/templates/";
	private static final Gson GSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

	/** A template read: the room, the hint of each slot, and the slots whose position still has to be confirmed. */
	record Loaded(RoomDto room, Map<String, String> hints, Set<String> unplaced) {
	}

	private RoomTemplates() {
	}

	static boolean known(String id) {
		return IDS.contains(id);
	}

	/** Reads a template from the mod's resources. */
	static Loaded load(String id) throws IOException {
		if (!known(id)) {
			throw new IOException("unknown template " + id);
		}
		try (InputStream in = RoomTemplates.class.getResourceAsStream(PATH + id + ".json")) {
			if (in == null) {
				throw new IOException("missing template resource " + id);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return parse(GSON.fromJson(reader, JsonObject.class));
			}
		}
	}

	static Loaded parse(JsonObject json) {
		Map<String, String> hints = new HashMap<>();
		Set<String> unplaced = new LinkedHashSet<>();
		if (json.has("slots") && json.get("slots").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("slots")) {
				JsonObject slot = element.getAsJsonObject();
				String id = slot.get("id").getAsString();
				if (slot.has("hint") && !slot.get("hint").isJsonNull()) {
					hints.put(id, slot.get("hint").getAsString());
				}
				if (slot.has("placed") && !slot.get("placed").getAsBoolean()) {
					unplaced.add(id);
				}
			}
		}
		RoomDto room = GSON.fromJson(json, RoomDto.class).normalized();
		return new Loaded(room, hints, unplaced);
	}

	/**
	 * The slots still to place, in the order of the chain: the slots of the first step (its roles, then where its clues show),
	 * then the next step's, and last the ones no step uses (the hiding places).
	 */
	static List<String> order(RoomDto room, Set<String> unplaced) {
		Set<String> out = new LinkedHashSet<>();
		for (StepDto step : room.steps) {
			List<String> used = new ArrayList<>(step.slots.values());
			step.gives.forEach(give -> used.add(give.slot));
			for (String id : used) {
				if (unplaced.contains(id)) {
					out.add(id);
				}
			}
		}
		for (RoomDto.SlotDto slot : room.slots) {
			if (unplaced.contains(slot.id)) {
				out.add(slot.id);
			}
		}
		return new ArrayList<>(out);
	}
}
