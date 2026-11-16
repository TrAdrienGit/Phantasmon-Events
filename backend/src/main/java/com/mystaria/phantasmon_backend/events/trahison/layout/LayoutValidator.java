package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.GameColor;

/**
 * Checks a plan. Pure, no Spring. Two levels:
 * <ul>
 * <li>structure, always: ids, known types, well-formed boxes, rounds and colours in range, no two zones overlapping;</li>
 * <li>completeness, unless {@code draft}: everything the game needs is there and every reference resolves.</li>
 * </ul>
 * Every problem is an {@link Issue} with a code {@code ERROR_TRAHISON_LAYOUT_*}; all are reported at once.
 */
public final class LayoutValidator {

	public static final Set<String> ZONE_TYPES = Set.of("hall", "corridor", "doors", "room_slot", "vote", "cabin", "final", "alcove");
	public static final Set<String> DOOR_TYPES = Set.of("color", "room_exit", "cabin", "final", "plain");
	public static final Set<String> SIDES = Set.of("pair", "solo");
	public static final Set<String> ORIENTATIONS = Set.of("north", "east", "south", "west");
	public static final int ROUNDS = 3;
	public static final int SLOTS_PER_ROUND = 3;

	public static final String BAD_ID = "ERROR_TRAHISON_LAYOUT_BAD_ID";
	public static final String DUPLICATE_ID = "ERROR_TRAHISON_LAYOUT_DUPLICATE_ID";
	public static final String BAD_TYPE = "ERROR_TRAHISON_LAYOUT_BAD_TYPE";
	public static final String BAD_BOX = "ERROR_TRAHISON_LAYOUT_BAD_BOX";
	public static final String BAD_ROUND = "ERROR_TRAHISON_LAYOUT_BAD_ROUND";
	public static final String BAD_COLORS = "ERROR_TRAHISON_LAYOUT_BAD_COLORS";
	public static final String ZONE_OVERLAP = "ERROR_TRAHISON_LAYOUT_ZONE_OVERLAP";
	public static final String MISSING_FIELD = "ERROR_TRAHISON_LAYOUT_MISSING_FIELD";
	public static final String BAD_REFERENCE = "ERROR_TRAHISON_LAYOUT_BAD_REFERENCE";
	public static final String ROUND_DOORS = "ERROR_TRAHISON_LAYOUT_ROUND_DOORS";
	public static final String CABINS = "ERROR_TRAHISON_LAYOUT_CABINS";
	public static final String ROOM_SLOTS = "ERROR_TRAHISON_LAYOUT_ROOM_SLOTS";
	public static final String FINAL_DOOR = "ERROR_TRAHISON_LAYOUT_FINAL_DOOR";

	private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

	/**
	 * One problem. {@code ids}: the elements concerned (zone ids, door ids, ...); {@code info}: a short machine-readable
	 * complement ({@code field=zone_a}, {@code round=2}, {@code missing=red_green}, ...) or null.
	 */
	public record Issue(String code, List<String> ids, String info) {
	}

	private LayoutValidator() {
	}

	/** The id of a colour pair, as a cabin's {@code serves}: {@code red_green}, {@code red_blue} or {@code green_blue}. */
	public static String pairId(Door door) {
		return door.first().name().toLowerCase(Locale.ROOT) + "_" + door.second().name().toLowerCase(Locale.ROOT);
	}

	/** The colour pair of a colour door, empty when its {@code colors} are not two different colours. */
	public static java.util.Optional<Door> doorPair(LayoutDoor door) {
		if (door.colors().size() != 2) {
			return java.util.Optional.empty();
		}
		try {
			return java.util.Optional.of(Door.of(color(door.colors().get(0)), color(door.colors().get(1))));
		} catch (IllegalArgumentException | NullPointerException ex) {
			return java.util.Optional.empty();
		}
	}

	private static GameColor color(String name) {
		return GameColor.valueOf(name.toUpperCase(Locale.ROOT));
	}

	public static List<Issue> validate(LayoutContent layout, boolean draft) {
		List<Issue> issues = new ArrayList<>();
		structure(layout, issues);
		if (!draft) {
			completeness(layout, issues);
		}
		return issues;
	}

	// ---------------------------------------------------------------- structure

	private static void structure(LayoutContent layout, List<Issue> issues) {
		ids("zone", layout.zones().stream().map(LayoutZone::id).toList(), issues);
		ids("door", layout.doors().stream().map(LayoutDoor::id).toList(), issues);
		ids("room_slot", layout.roomSlots().stream().map(RoomSlot::id).toList(), issues);
		ids("cabin", layout.cabins().stream().map(Cabin::id).toList(), issues);
		ids("hiding_spot", layout.hidingSpots().stream().map(HidingSpot::id).toList(), issues);

		for (LayoutZone zone : layout.zones()) {
			if (zone.type() != null && !ZONE_TYPES.contains(zone.type())) {
				issues.add(new Issue(BAD_TYPE, l(zone.id()), "zone:" + zone.type()));
			}
			for (Box box : zone.boxes()) {
				if (box == null || !box.isValid()) {
					issues.add(new Issue(BAD_BOX, l(zone.id()), "zone"));
				}
			}
		}
		for (int i = 0; i < layout.zones().size(); i++) {
			for (int j = i + 1; j < layout.zones().size(); j++) {
				if (overlap(layout.zones().get(i), layout.zones().get(j))) {
					issues.add(new Issue(ZONE_OVERLAP, l(layout.zones().get(i).id(), layout.zones().get(j).id()), null));
				}
			}
		}
		for (LayoutDoor door : layout.doors()) {
			if (door.type() != null && !DOOR_TYPES.contains(door.type())) {
				issues.add(new Issue(BAD_TYPE, l(door.id()), "door:" + door.type()));
			}
			if (door.block() != null && !door.block().isValid()) {
				issues.add(new Issue(BAD_BOX, l(door.id()), "door"));
			}
			if (door.round() != null && (door.round() < 1 || door.round() > ROUNDS)) {
				issues.add(new Issue(BAD_ROUND, l(door.id()), "round=" + door.round()));
			}
			if (!door.colors().isEmpty() && doorPair(door).isEmpty()) {
				issues.add(new Issue(BAD_COLORS, l(door.id()), null));
			}
		}
		for (RoomSlot slot : layout.roomSlots()) {
			if (slot.round() != null && (slot.round() < 1 || slot.round() > ROUNDS)) {
				issues.add(new Issue(BAD_ROUND, l(slot.id()), "round=" + slot.round()));
			}
			if (slot.orientation() != null && !ORIENTATIONS.contains(slot.orientation())) {
				issues.add(new Issue(BAD_TYPE, l(slot.id()), "orientation:" + slot.orientation()));
			}
		}
		for (Cabin cabin : layout.cabins()) {
			if (cabin.side() != null && !SIDES.contains(cabin.side())) {
				issues.add(new Issue(BAD_TYPE, l(cabin.id()), "side:" + cabin.side()));
			}
			if (cabin.serves() != null && !Door.ALL.stream().map(LayoutValidator::pairId).toList().contains(cabin.serves())) {
				issues.add(new Issue(BAD_TYPE, l(cabin.id()), "serves:" + cabin.serves()));
			}
		}
	}

	private static void ids(String kind, List<String> ids, List<Issue> issues) {
		Set<String> seen = new HashSet<>();
		Set<String> reported = new HashSet<>();
		for (String id : ids) {
			if (id == null || !ID.matcher(id).matches()) {
				issues.add(new Issue(BAD_ID, id == null ? l() : List.of(id), kind));
			} else if (!seen.add(id) && reported.add(id)) {
				issues.add(new Issue(DUPLICATE_ID, l(id), kind));
			}
		}
	}

	/** An id list that tolerates null ids (a bad element). */
	private static List<String> l(String... ids) {
		return java.util.Arrays.stream(ids).filter(java.util.Objects::nonNull).toList();
	}

	private static boolean overlap(LayoutZone a, LayoutZone b) {
		for (Box x : a.boxes()) {
			for (Box y : b.boxes()) {
				if (x != null && y != null && x.isValid() && y.isValid() && x.overlaps(y)) {
					return true;
				}
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- completeness

	private static void completeness(LayoutContent layout, List<Issue> issues) {
		Map<String, LayoutZone> zones = new HashMap<>();
		layout.zones().forEach(z -> zones.putIfAbsent(z.id(), z));
		Map<String, LayoutDoor> doors = new HashMap<>();
		layout.doors().forEach(d -> doors.putIfAbsent(d.id(), d));

		for (LayoutZone zone : layout.zones()) {
			if (zone.type() == null) {
				issues.add(new Issue(MISSING_FIELD, l(zone.id()), "field=type"));
			}
			if (zone.boxes().isEmpty()) {
				issues.add(new Issue(MISSING_FIELD, l(zone.id()), "field=boxes"));
			}
		}
		for (LayoutDoor door : layout.doors()) {
			if (door.type() == null) {
				issues.add(new Issue(MISSING_FIELD, l(door.id()), "field=type"));
			}
			if (door.block() == null) {
				issues.add(new Issue(MISSING_FIELD, l(door.id()), "field=block"));
			}
			link(door, door.zoneA(), "zone_a", zones, issues);
			link(door, door.zoneB(), "zone_b", zones, issues);
			if ("color".equals(door.type())) {
				if (door.round() == null) {
					issues.add(new Issue(MISSING_FIELD, l(door.id()), "field=round"));
				}
				if (door.colors().isEmpty()) {
					issues.add(new Issue(MISSING_FIELD, l(door.id()), "field=colors"));
				}
			}
		}
		for (RoomSlot slot : layout.roomSlots()) {
			if (slot.round() == null) {
				issues.add(new Issue(MISSING_FIELD, l(slot.id()), "field=round"));
			}
			if (slot.origin() == null) {
				issues.add(new Issue(MISSING_FIELD, l(slot.id()), "field=origin"));
			}
			if (slot.orientation() == null) {
				issues.add(new Issue(MISSING_FIELD, l(slot.id()), "field=orientation"));
			}
		}
		for (HidingSpot spot : layout.hidingSpots()) {
			if (spot.position() == null) {
				issues.add(new Issue(MISSING_FIELD, l(spot.id()), "field=position"));
			}
		}
		for (Cabin cabin : layout.cabins()) {
			if (cabin.serves() == null) {
				issues.add(new Issue(MISSING_FIELD, l(cabin.id()), "field=serves"));
			}
			if (cabin.side() == null) {
				issues.add(new Issue(MISSING_FIELD, l(cabin.id()), "field=side"));
			}
			cabinRef(cabin, cabin.zone(), "zone", zones.get(cabin.zone()) == null ? null : zones.get(cabin.zone()).type(), issues);
			cabinRef(cabin, cabin.door(), "door", doors.get(cabin.door()) == null ? null : doors.get(cabin.door()).type(), issues);
		}

		roundDoors(layout, issues);
		cabins(layout, issues);
		roomSlots(layout, issues);
		long finals = layout.doors().stream().filter(d -> "final".equals(d.type())).count();
		if (finals != 1) {
			issues.add(new Issue(FINAL_DOOR, l(), "count=" + finals));
		}
	}

	private static void link(LayoutDoor door, String zoneId, String field, Map<String, LayoutZone> zones, List<Issue> issues) {
		if (zoneId == null || zoneId.isBlank()) {
			issues.add(new Issue(MISSING_FIELD, l(door.id()), "field=" + field));
		} else if (!zones.containsKey(zoneId)) {
			issues.add(new Issue(BAD_REFERENCE, l(door.id(), zoneId), "field=" + field));
		}
	}

	/** A cabin's zone or door: must exist and be of type {@code cabin}. {@code actualType}: null when it does not exist. */
	private static void cabinRef(Cabin cabin, String ref, String field, String actualType, List<Issue> issues) {
		if (ref == null || ref.isBlank()) {
			issues.add(new Issue(MISSING_FIELD, l(cabin.id()), "field=" + field));
		} else if (!"cabin".equals(actualType)) {
			issues.add(new Issue(BAD_REFERENCE, l(cabin.id(), ref), "field=" + field));
		}
	}

	/** Per round: 3 colour doors, one for each of the three colour pairs. */
	private static void roundDoors(LayoutContent layout, List<Issue> issues) {
		for (int round = 1; round <= ROUNDS; round++) {
			final int r = round;
			List<LayoutDoor> inRound = layout.doors().stream()
					.filter(d -> "color".equals(d.type()) && d.round() != null && d.round() == r).toList();
			Map<String, List<String>> byPair = new HashMap<>();
			for (LayoutDoor door : inRound) {
				doorPair(door).ifPresent(pair -> byPair.computeIfAbsent(pairId(pair), k -> new ArrayList<>()).add(door.id()));
			}
			boolean ok = inRound.size() == Door.ALL.size();
			StringBuilder missing = new StringBuilder();
			for (Door pair : Door.ALL) {
				List<String> found = byPair.getOrDefault(pairId(pair), List.of());
				if (found.size() != 1) {
					ok = false;
				}
				if (found.isEmpty()) {
					missing.append(missing.isEmpty() ? "" : ",").append(pairId(pair));
				}
			}
			if (!ok) {
				issues.add(new Issue(ROUND_DOORS, inRound.stream().map(LayoutDoor::id).toList(),
						"round=" + round + (missing.isEmpty() ? "" : ";missing=" + missing)));
			}
		}
	}

	/** Six cabins: each colour pair has one cabin on the pair side and one on the solo side. */
	private static void cabins(LayoutContent layout, List<Issue> issues) {
		Map<String, List<String>> bySlot = new HashMap<>();
		for (Cabin cabin : layout.cabins()) {
			bySlot.computeIfAbsent(cabin.serves() + "/" + cabin.side(), k -> new ArrayList<>()).add(cabin.id());
		}
		StringBuilder missing = new StringBuilder();
		boolean ok = layout.cabins().size() == 2 * Door.ALL.size();
		for (Door pair : Door.ALL) {
			for (String side : List.of("pair", "solo")) {
				String key = pairId(pair) + "/" + side;
				List<String> found = bySlot.getOrDefault(key, List.of());
				if (found.size() != 1) {
					ok = false;
				}
				if (found.isEmpty()) {
					missing.append(missing.isEmpty() ? "" : ",").append(key);
				}
			}
		}
		if (!ok) {
			issues.add(new Issue(CABINS, layout.cabins().stream().map(Cabin::id).toList(),
					"count=" + layout.cabins().size() + (missing.isEmpty() ? "" : ";missing=" + missing)));
		}
	}

	/** Nine places: three for each round. */
	private static void roomSlots(LayoutContent layout, List<Issue> issues) {
		for (int round = 1; round <= ROUNDS; round++) {
			final int r = round;
			long count = layout.roomSlots().stream().filter(s -> s.round() != null && s.round() == r).count();
			if (count != SLOTS_PER_ROUND) {
				issues.add(new Issue(ROOM_SLOTS, layout.roomSlots().stream().filter(s -> s.round() != null && s.round() == r)
						.map(RoomSlot::id).toList(), "round=" + round + ";count=" + count));
			}
		}
	}
}
