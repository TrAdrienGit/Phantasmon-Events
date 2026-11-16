package com.mystaria.phantasmon_backend.events.trahison.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Clue;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ClueType;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Mechanism;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Lossless JSON form of a {@link RoomInstance}, kept inside the game's state so a running room survives a restart. It
 * embeds the whole {@link RoomDefinition} (editing the room bank later does not change a room being played), the seed,
 * the mechanisms' states (solutions included) and the times. Never sent to a client.
 */
final class RoomSnapshot {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private RoomSnapshot() {
	}

	/** Call with the instance locked. */
	static ObjectNode write(RoomInstance room) {
		ObjectNode out = MAPPER.createObjectNode();
		out.put("hub", room.hub);
		out.put("group", room.group);
		out.put("seed", room.seed);
		ArrayNode members = out.putArray("members");
		room.members.forEach(members::add);
		out.set("definition", MAPPER.valueToTree(room.definition));
		putTime(out, "startedAt", room.startedAt);
		putTime(out, "lastProgressAt", room.lastProgressAt);
		putTime(out, "finishedAt", room.finishedAt);
		out.put("exitOpen", room.exitOpen);
		ArrayNode inventory = out.putArray("inventory");
		room.inventory.forEach(inventory::add);
		ArrayNode picked = out.putArray("picked");
		room.picked.forEach(picked::add);
		ObjectNode steps = out.putObject("steps");
		for (StepRuntime s : room.steps.values()) {
			ObjectNode n = steps.putObject(s.step.id());
			n.put("open", s.open);
			n.put("solved", s.solved);
			putTime(n, "openedAt", s.openedAt);
			putTime(n, "solvedAt", s.solvedAt);
			n.put("hintsGiven", s.hintsGiven);
			n.set("data", s.state.data().deepCopy());
			ArrayNode clues = n.putArray("clues");
			s.state.clues().forEach(c -> clues.add(c.toJson()));
		}
		return out;
	}

	static RoomInstance read(JsonNode in, MechanismRegistry registry) {
		RoomDefinition definition = MAPPER.treeToValue(in.get("definition"), RoomDefinition.class);
		List<Integer> members = new ArrayList<>();
		in.get("members").forEach(m -> members.add(m.asInt()));
		RoomInstance room = new RoomInstance(in.get("hub").asString(), in.get("group").asInt(), definition,
				in.get("seed").asLong(), members);
		room.startedAt = time(in, "startedAt");
		room.lastProgressAt = time(in, "lastProgressAt");
		room.finishedAt = time(in, "finishedAt");
		room.exitOpen = in.path("exitOpen").asBoolean(false);
		in.path("inventory").forEach(i -> room.inventory.add(i.asString()));
		in.path("picked").forEach(i -> room.picked.add(i.asString()));
		JsonNode steps = in.get("steps");
		for (Step step : definition.steps()) {
			Mechanism mechanism = registry.get(step.mechanism());
			JsonNode n = steps.get(step.id());
			List<Clue> clues = new ArrayList<>();
			n.path("clues").forEach(c -> clues.add(new Clue(c.get("role").asString(),
					ClueType.valueOf(c.get("type").asString().toUpperCase(java.util.Locale.ROOT)), c.get("value").deepCopy())));
			StepRuntime s = new StepRuntime(step, mechanism, new MechanismState((ObjectNode) n.get("data").deepCopy(), clues));
			s.open = n.path("open").asBoolean(false);
			s.solved = n.path("solved").asBoolean(false);
			s.openedAt = time(n, "openedAt");
			s.solvedAt = time(n, "solvedAt");
			s.hintsGiven = n.path("hintsGiven").asInt(0);
			room.steps.put(step.id(), s);
		}
		return room;
	}

	private static void putTime(ObjectNode node, String field, Instant value) {
		if (value == null) {
			node.putNull(field);
		} else {
			node.put(field, value.toString());
		}
	}

	private static Instant time(JsonNode node, String field) {
		JsonNode v = node.path(field);
		return v.isMissingNode() || v.isNull() ? null : Instant.parse(v.asString());
	}
}
