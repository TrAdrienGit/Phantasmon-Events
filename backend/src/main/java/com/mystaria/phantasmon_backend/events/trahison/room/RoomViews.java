package com.mystaria.phantasmon_backend.events.trahison.room;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Clue;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ClueType;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.ViewerKind;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The only way a room's state leaves the backend (protocole.md: {@code EventsTrahisonRoom} and
 * {@code EventsTrahisonPuzzle}). A viewer is a group member, a spectator or a game master; the first two get the same
 * content, which never holds a solution: only the clues the room really shows, and each mechanism's own view.
 * Call with the instance locked.
 */
public final class RoomViews {

	private RoomViews() {
	}

	/** {@code EventsTrahisonRoom}: steps done, the active slots with what each shows, the inventory. */
	public static ObjectNode room(RoomInstance room, ViewerKind viewer) {
		ObjectNode out = Json.object();
		out.put("hub", room.hub);
		out.put("round", room.round);
		out.put("group", room.group);
		out.put("room", room.definition.id());
		out.put("name", room.definition.name());
		out.put("version", room.definition.version());
		ArrayNode members = out.putArray("members");
		room.members.forEach(members::add);
		out.put("started_at", room.startedAt.toString());
		out.put("last_progress_at", room.lastProgressAt.toString());
		if (room.finishedAt == null) {
			out.putNull("finished_at");
		} else {
			out.put("finished_at", room.finishedAt.toString());
		}
		out.put("exit_open", room.exitOpen);
		out.put("steps_done", room.stepsDone());
		out.put("steps_total", room.steps.size());
		ArrayNode steps = out.putArray("steps");
		for (StepRuntime s : room.steps.values()) {
			ObjectNode n = steps.addObject();
			n.put("id", s.step.id());
			n.put("open", s.open);
			n.put("solved", s.solved);
			ArrayNode given = n.putArray("hints"); // the help texts the group was given, vaguest first (T3.6)
			for (int h = 0; h < s.hintsGiven && h < s.step.hints().size(); h++) {
				given.add(s.step.hints().get(h));
			}
			if (s.open && !s.solved) {
				s.mechanism.progress(s.state).ifPresent(p -> { // a counter for the HUD (D61)
					ObjectNode progress = n.putObject("progress");
					progress.put("done", p[0]);
					progress.put("total", p[1]);
				});
			}
			s.mechanism.world(s.state).ifPresent(w -> n.set("world", withBound(room, s, w))); // what the world around the slots shows (T8.3)
			if (viewer == ViewerKind.GAME_MASTER) {
				n.put("mechanism", s.step.mechanism());
				n.put("hints_given", s.hintsGiven);
			}
		}
		ArrayNode partners = out.putArray("partners"); // T8.3: public, every client draws them next to their trainer
		for (var p : room.partners) {
			ObjectNode n = partners.addObject();
			n.put("player", p.player());
			n.put("species", p.species());
			n.put("power", p.power().name());
		}
		out.put("ghost_partner", com.mystaria.phantasmon_backend.events.trahison.pokemon.Partners.GHOST_SPECIES);
		ArrayNode slots = out.putArray("slots");
		for (Slot slot : room.definition.slots()) {
			ArrayNode shows = shows(room, slot);
			if (shows.isEmpty()) {
				continue;
			}
			ObjectNode n = slots.addObject();
			n.put("id", slot.id());
			n.put("kind", slot.kind());
			Pos p = slot.position();
			ObjectNode pos = n.putObject("position");
			pos.put("x", p.x());
			pos.put("y", p.y());
			pos.put("z", p.z());
			n.put("orientation", slot.orientation());
			ObjectNode size = n.putObject("size");
			size.put("x", slot.size().x());
			size.put("y", slot.size().y());
			size.put("z", slot.size().z());
			n.set("shows", shows);
		}
		ArrayNode inventory = out.putArray("inventory");
		room.inventory.forEach(inventory::add);
		ArrayNode items = out.putArray("items");
		for (int i = 0; i < room.inventory.size(); i++) {
			ObjectNode item = items.addObject();
			item.put("id", room.inventory.get(i));
			item.put("holder", i < room.holders.size() ? room.holders.get(i) : -1);
		}
		RoomPlace place = room.place;
		if (place == null) {
			out.putNull("place");
		} else {
			ObjectNode p = out.putObject("place");
			p.put("id", place.id());
			ObjectNode origin = p.putObject("origin");
			origin.put("x", place.origin().x());
			origin.put("y", place.origin().y());
			origin.put("z", place.origin().z());
			p.put("orientation", place.orientation());
		}
		return out;
	}

	/**
	 * T8.3: a mechanism played in the world needs the geometry of its slots even before the step opens (an obstacle stands there from the
	 * start): {@code bound} maps each role of the step to its slot (id, position, size, orientation, in the room's own frame). Never a secret.
	 */
	private static JsonNode withBound(RoomInstance room, StepRuntime s, JsonNode world) {
		if (!(world instanceof ObjectNode w)) {
			return world;
		}
		ObjectNode bound = w.putObject("bound");
		for (var role : s.step.slots().entrySet()) {
			room.definition.slot(role.getValue()).ifPresent(slot -> {
				ObjectNode b = bound.putObject(role.getKey());
				b.put("id", slot.id());
				ObjectNode pos = b.putObject("position");
				pos.put("x", slot.position().x());
				pos.put("y", slot.position().y());
				pos.put("z", slot.position().z());
				ObjectNode size = b.putObject("size");
				size.put("x", slot.size().x());
				size.put("y", slot.size().y());
				size.put("z", slot.size().z());
				b.put("orientation", slot.orientation());
			});
		}
		return w;
	}

	/** What one slot shows now: the puzzles bound to it, the clues given to it, the exit. Empty: the slot is inactive. */
	static ArrayNode shows(RoomInstance room, Slot slot) {
		ArrayNode shows = Json.array();
		if ("exit".equals(slot.kind())) {
			ObjectNode e = shows.addObject();
			e.put("type", "exit");
			e.put("open", room.exitOpen);
		}
		for (StepRuntime s : room.steps.values()) {
			if (s.open || s.solved) {
				for (var role : s.step.slots().entrySet()) {
					if (role.getValue().equals(slot.id())) {
						ObjectNode n = shows.addObject();
						n.put("type", "puzzle");
						n.put("step", s.step.id());
						n.put("role", role.getKey());
						n.put("mechanism", s.step.mechanism());
						n.put("solved", s.solved);
					}
				}
			}
			for (Give g : s.step.gives()) {
				if (!g.slot().equals(slot.id()) || !(g.whenSolved() ? s.solved : s.open || s.solved)) {
					continue;
				}
				for (Clue clue : s.state.clues()) {
					if (!clue.role().equals(g.clue())) {
						continue;
					}
					if (clue.type() == ClueType.ITEM && room.picked.contains(pickKey(s.step.id(), clue.role()))) {
						continue;
					}
					ObjectNode n = shows.addObject();
					n.put("type", "clue");
					n.put("step", s.step.id());
					n.set("clue", clue.toJson());
				}
			}
		}
		return shows;
	}

	static String pickKey(String stepId, String clueRole) {
		return stepId + "/" + clueRole;
	}

	/** {@code EventsTrahisonPuzzle}: the mechanism's view of a step (never the solution) and the last gesture. */
	public static ObjectNode puzzle(RoomInstance room, PuzzleUpdate update, ViewerKind viewer) {
		StepRuntime s = room.steps.get(update.step());
		ObjectNode out = Json.object();
		out.put("hub", room.hub);
		out.put("round", room.round);
		out.put("group", room.group);
		out.put("step", s.step.id());
		out.put("mechanism", s.step.mechanism());
		out.put("reason", update.reason());
		out.put("solved", s.solved);
		out.set("view", s.mechanism.view(s.state, viewer));
		if (update.player() == null || update.events().isEmpty()) {
			out.putNull("last");
		} else {
			ObjectNode last = out.putObject("last");
			last.put("player", update.player());
			ArrayNode events = last.putArray("events");
			update.events().forEach(events::add);
		}
		return out;
	}
}
