package com.mystaria.events.trahison.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the latest view says for the map (T11.2): the zones this player explored ({@code viewer.explored}; {@code null} when he
 * sees every room), the name of the room of each place of the plan ({@code map_rooms}) and, for those who watch only, the
 * cursors of the living players ({@code map_cursors}). Kept beside {@link GameView.View}, fed by {@link GameView#update}.
 * Nothing here is a rule: the backend decides what each viewer may know. Client thread.
 */
public final class MapData {

	/** The room of a place: its name, round and group (door index). */
	public record Room(String name, int round, int group) {
	}

	/** A player's cursor as the backend last knew it (hub coordinates); only watchers receive any. */
	public record Cursor(int index, String zone, double x, double y, double z) {
	}

	private static Set<String> explored = Set.of();
	private static boolean exploredAll;
	private static Map<String, Room> rooms = Map.of();
	private static List<Cursor> cursors = List.of();

	private MapData() {
	}

	/** Whether this viewer sees every room (game master, spectator, ghost): no hatching. */
	public static boolean exploredAll() {
		return exploredAll;
	}

	/** Whether the viewer has been in this zone of the plan (always true when he sees everything). */
	public static boolean explored(String zone) {
		return exploredAll || explored.contains(zone);
	}

	/** The rooms by place id (the place the plan calls {@code room_slot}). */
	public static Map<String, Room> rooms() {
		return rooms;
	}

	public static List<Cursor> cursors() {
		return cursors;
	}

	public static void clear() {
		explored = Set.of();
		exploredAll = false;
		rooms = Map.of();
		cursors = List.of();
	}

	/** Reads a received {@code EventsTrahisonView}. */
	static void update(Map<String, Object> data) {
		if (data.get("viewer") instanceof Map<?, ?> viewer) {
			exploredAll = Boolean.TRUE.equals(viewer.get("explored_all"));
			Set<String> zones = new java.util.LinkedHashSet<>();
			if (viewer.get("explored") instanceof List<?> list) {
				list.forEach(z -> zones.add(String.valueOf(z)));
			}
			explored = Set.copyOf(zones);
		} else {
			exploredAll = false;
			explored = Set.of();
		}
		Map<String, Room> found = new LinkedHashMap<>();
		if (data.get("map_rooms") instanceof Map<?, ?> byPlace) {
			byPlace.forEach((place, value) -> {
				if (value instanceof Map<?, ?> room) {
					found.put(String.valueOf(place), new Room(room.get("name") == null ? String.valueOf(place) : String.valueOf(room.get("name")),
							room.get("round") instanceof Number r ? r.intValue() : 0, room.get("group") instanceof Number g ? g.intValue() : 0));
				}
			});
		}
		rooms = Map.copyOf(found);
		List<Cursor> found2 = new ArrayList<>();
		if (data.get("map_cursors") instanceof List<?> list) {
			for (Object o : list) {
				if (o instanceof Map<?, ?> c && c.get("index") instanceof Number i && c.get("x") instanceof Number x && c.get("z") instanceof Number z) {
					found2.add(new Cursor(i.intValue(), c.get("zone") == null ? "" : String.valueOf(c.get("zone")), x.doubleValue(),
							c.get("y") instanceof Number y ? y.doubleValue() : 0, z.doubleValue()));
				}
			}
		}
		cursors = List.copyOf(found2);
	}
}
