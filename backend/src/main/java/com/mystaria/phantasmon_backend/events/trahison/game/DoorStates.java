package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutContent;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutDoor;
import com.mystaria.phantasmon_backend.events.trahison.layout.LayoutZone;
import com.mystaria.phantasmon_backend.events.trahison.rules.Door;
import com.mystaria.phantasmon_backend.events.trahison.rules.GameColor;

/**
 * Which door of the plan is open, as a pure function of the stage and of the room progress (Documentation/trahison,
 * protocole.md "doors_state"). The defaults:
 * <ul>
 *   <li>{@code color}: closed, except during DOORS while the bracelets at its threshold open it for a few seconds (T2.3);</li>
 *   <li>{@code room_exit}: during ROOMS, closed until the room of its group is solved; open in any other stage;</li>
 *   <li>{@code cabin}: open, closed (but open for the players its group lets through) while a group holds its cabin (D4, D5);</li>
 *   <li>{@code final}: closed, except for the players who read 9 while its window is open (T2.5: closed for everybody else);</li>
 *   <li>{@code plain}: open;</li>
 *   <li>after the game, every door is open.</li>
 * </ul>
 * Which group a {@code room_exit} door belongs to: the plan does not say yet (T4 draws the places of the rooms), so the
 * group is the one whose colour door leads to the same room zone; a door with no such link falls back to the order of
 * the plan: the i-th {@code room_exit} door is the room place {@code i % 3} (as {@link Door#ALL}) of round {@code i / 3 + 1}.
 */
public final class DoorStates {

	private DoorStates() {
	}

	/** Round and group (index in {@link Door#ALL}) a {@code room_exit} door belongs to. */
	record ExitOf(int round, int group) {
	}

	/**
	 * @param roomSolved whether the room of each group of the current round is solved (length 3, or null: none)
	 * @param overrides  the game master's decisions ({@code door id -> open}); they win over the defaults
	 */
	public static Map<String, DoorState> compute(LayoutContent layout, Phase phase, int round, boolean[] roomSolved,
			Map<String, Boolean> overrides) {
		return compute(layout, phase, round, roomSolved, overrides, Set.of());
	}

	/**
	 * @param thresholdOpen the colour doors open for a few seconds because their threshold is satisfied (T2.3); only
	 *                      during DOORS
	 */
	public static Map<String, DoorState> compute(LayoutContent layout, Phase phase, int round, boolean[] roomSolved,
			Map<String, Boolean> overrides, Set<String> thresholdOpen) {
		return compute(layout, phase, round, roomSolved, overrides, thresholdOpen, Set.of());
	}

	/**
	 * @param closedCabins the ids of the cabins a group holds (T2.4): their {@code cabin} door is closed for everyone
	 */
	public static Map<String, DoorState> compute(LayoutContent layout, Phase phase, int round, boolean[] roomSolved,
			Map<String, Boolean> overrides, Set<String> thresholdOpen, Set<String> closedCabins) {
		Map<String, Set<Integer>> held = new LinkedHashMap<>();
		closedCabins.forEach(id -> held.put(id, Set.of()));
		return compute(layout, phase, round, roomSolved, overrides, thresholdOpen, held);
	}

	/**
	 * @param heldCabins the cabins a group holds (D4, D5), each with the players it is still open for: the door of a held
	 *                   cabin is closed for everybody else (a cabin that is waiting for the partner of a pair, or whose
	 *                   group has voted and walks out)
	 */
	public static Map<String, DoorState> compute(LayoutContent layout, Phase phase, int round, boolean[] roomSolved,
			Map<String, Boolean> overrides, Set<String> thresholdOpen, Map<String, Set<Integer>> heldCabins) {
		return compute(layout, phase, round, roomSolved, overrides, thresholdOpen, heldCabins, Set.of());
	}

	/**
	 * @param finalOpenFor the players the final door is open for right now (T2.5): it stays closed for everybody else, each
	 *                     client removes its own fake wall according to the view it gets
	 */
	public static Map<String, DoorState> compute(LayoutContent layout, Phase phase, int round, boolean[] roomSolved,
			Map<String, Boolean> overrides, Set<String> thresholdOpen, Map<String, Set<Integer>> heldCabins, Set<Integer> finalOpenFor) {
		Map<String, Set<Integer>> closedDoors = new HashMap<>();
		for (var cabin : layout.cabins()) {
			if (heldCabins.containsKey(cabin.id()) && cabin.door() != null) {
				closedDoors.put(cabin.door(), heldCabins.get(cabin.id()));
			}
		}
		Map<String, DoorState> out = new LinkedHashMap<>();
		Map<String, ExitOf> exits = exitsOf(layout);
		for (LayoutDoor door : layout.doors()) {
			boolean ended = phase == Phase.ENDED;
			boolean open = ended || switch (door.type() == null ? "" : door.type()) {
				case "room_exit" -> {
					ExitOf exit = exits.get(door.id());
					yield phase != Phase.ROOMS || exit == null || exit.round() != round
						|| roomSolved != null && exit.group() < roomSolved.length && roomSolved[exit.group()];
				}
				case "cabin" -> !closedDoors.containsKey(door.id());
				case "plain" -> true;
				case "color" -> phase == Phase.DOORS && thresholdOpen.contains(door.id());
				default -> false; // final
			};
			Boolean forced = overrides == null ? null : overrides.get(door.id());
			Set<Integer> openFor = forced == null && !ended && "cabin".equals(door.type()) ? closedDoors.get(door.id()) : null;
			if (forced == null && !ended && "final".equals(door.type()) && !finalOpenFor.isEmpty()) {
				openFor = finalOpenFor;
			}
			out.put(door.id(), openFor != null && !open ? new DoorState(false, openFor) : DoorState.of(forced != null ? forced : open));
		}
		return out;
	}

	/** The round and group of every {@code room_exit} door of the plan (see the class comment). */
	static Map<String, ExitOf> exitsOf(LayoutContent layout) {
		Map<String, String> zoneTypes = new HashMap<>();
		for (LayoutZone z : layout.zones()) {
			zoneTypes.put(z.id(), z.type());
		}
		Map<String, ExitOf> out = new HashMap<>();
		int n = 0;
		for (LayoutDoor exit : layout.doors()) {
			if (!"room_exit".equals(exit.type())) {
				continue;
			}
			ExitOf found = null;
			for (LayoutDoor color : layout.doors()) {
				int group = "color".equals(color.type()) && color.round() != null ? groupOf(color.colors()) : -1;
				if (group < 0) {
					continue;
				}
				for (String zone : new String[] { color.zoneA(), color.zoneB() }) {
					if ("room_slot".equals(zoneTypes.get(zone)) && (zone.equals(exit.zoneA()) || zone.equals(exit.zoneB()))) {
						found = new ExitOf(color.round(), group);
					}
				}
			}
			out.put(exit.id(), found != null ? found : new ExitOf(n / 3 + 1, n % 3));
			n++;
		}
		return out;
	}

	/** Index in {@link Door#ALL} of the door with these two colour names, or -1. */
	static int groupOf(List<String> colors) {
		if (colors.size() != 2) {
			return -1;
		}
		try {
			Door door = Door.of(GameColor.valueOf(colors.get(0).toUpperCase(java.util.Locale.ROOT)),
				GameColor.valueOf(colors.get(1).toUpperCase(java.util.Locale.ROOT)));
			return Door.ALL.indexOf(door);
		} catch (IllegalArgumentException ex) {
			return -1;
		}
	}

	/** The ids of the plan's doors, for validating a game master's {@code set_door}. */
	public static Set<String> ids(LayoutContent layout) {
		Set<String> ids = new java.util.LinkedHashSet<>();
		layout.doors().forEach(d -> ids.add(d.id()));
		return ids;
	}
}
