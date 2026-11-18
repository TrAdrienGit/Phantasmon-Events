package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mystaria.phantasmon_backend.events.trahison.room.RoomDraw;

/**
 * What the map (T11) needs from a game, per viewer. The map is drawn by the client from the plan it already has; the
 * game only adds what the viewer may know: the rooms he explored, the names of the places, and for those who watch the
 * players' cursors. A player never receives another player's exploration.
 */
public final class MapViews {

	/** A player stops recording new zones past this many (a client could send any id of the plan, but only the plan's). */
	static final int MAX_EXPLORED = 200;

	private MapViews() {
	}

	/** Whether this viewer sees the whole map: game masters (playing or not), spectators and ghosts (D14, D16). */
	public static boolean seesAll(Game g, Views.Kind kind, UUID viewer) {
		return kind != Views.Kind.PLAYER || g.gameMasters.contains(viewer);
	}

	/** The zones this seat has been in; empty for a seat that never moved. */
	public static List<String> exploredOf(Game g, int seat) {
		List<String> zones = g.explored.get(seat);
		return zones == null ? new ArrayList<>() : new ArrayList<>(zones);
	}

	/**
	 * Records that a seat stands in a zone of the plan. Returns whether it is new for him. Nothing is recorded in the lobby
	 * (the rooms are not there yet), for no zone, or for a zone the plan does not have.
	 */
	static boolean visit(Game g, int seat, String zone, boolean knownZone) {
		if (g.phase == Phase.LOBBY || zone == null || !knownZone) {
			return false;
		}
		List<String> zones = g.explored.computeIfAbsent(seat, k -> new ArrayList<>());
		if (zones.contains(zone) || zones.size() >= MAX_EXPLORED) {
			return false;
		}
		zones.add(zone);
		return true;
	}

	/** The viewer's own exploration (null: he sees every room, the client draws no hatching). */
	public static List<String> viewerExplored(Game g, Views.Kind kind, UUID viewer) {
		if (seesAll(g, kind, viewer)) {
			return null;
		}
		return exploredOf(g, g.indexOf(viewer));
	}

	/**
	 * The room of each place of the plan, by place id: its name, round and group. Everybody learns every name as soon
	 * as the draw is made (D151), started rounds or not; {@code seesAll} no longer matters and is kept for the callers.
	 */
	public static Map<String, Object> rooms(Game g, boolean seesAll) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (g.draw == null) {
			return out;
		}
		for (RoomDraw.Pick p : g.draw.picks()) {
			if (p.place() == null) {
				continue;
			}
			Map<String, Object> room = new LinkedHashMap<>();
			room.put("name", p.name());
			room.put("round", p.round());
			room.put("group", p.group());
			out.put(p.place(), room);
		}
		return out;
	}

	/**
	 * The cursors of the living players, for those who see everything and nobody else (D14, D16). Positions come from the
	 * players' clients; a player who never reported one has none.
	 */
	public static List<Object> cursors(Game g, boolean seesAll) {
		List<Object> out = new ArrayList<>();
		if (!seesAll || g.phase == Phase.LOBBY) {
			return out;
		}
		for (int i = 0; i < g.players.size(); i++) {
			PlayerState p = g.players.get(i);
			if (p.alive && !p.bot && p.x != null && p.z != null) {
				out.add(Views.orderedMap("index", i, "zone", p.zone, "x", p.x, "y", p.y, "z", p.z));
			}
		}
		return out;
	}
}
