package com.mystaria.events.trahison.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Door;
import com.mystaria.events.trahison.edit.LayoutDto.Slot;
import com.mystaria.events.trahison.edit.LayoutDto.Zone;
import com.mystaria.events.trahison.net.MapData;

/**
 * What the map draws, worked out from the plan the client already holds (T11): the floors, and for one floor the rooms and
 * doors seen from above, in hub blocks. Pure (no Minecraft class), so it is tested alone. The rule for floors is the backend's
 * ({@code layout.Floor}): an element is on every floor whose slice of heights its box touches, so a staircase shows on both;
 * a plan that declares no floor has one that holds everything.
 */
public final class MapModel {

	/** Edge of a room's place in blocks (the plan's {@code room_slot}); see {@code edit.EditKinds#SLOT_SIDE}. */
	public static final int PLACE_SIDE = 48;
	/** Id of the floor of a plan that declares none. */
	public static final String IMPLICIT_FLOOR = "main";

	/** A floor: {@code name} null for the implicit one (the screen words it). */
	public record FloorInfo(String id, String name, int yMin, int yMax) {
	}

	/** A rectangle of blocks, corners included (hub x and z). */
	public record Rect(int minX, int minZ, int maxX, int maxZ) {
		public boolean contains(double x, double z) {
			return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
		}

		public Rect union(Rect o) {
			return new Rect(Math.min(minX, o.minX), Math.min(minZ, o.minZ), Math.max(maxX, o.maxX), Math.max(maxZ, o.maxZ));
		}

		public int width() {
			return maxX - minX + 1;
		}

		public int depth() {
			return maxZ - minZ + 1;
		}
	}

	/** A zone on a floor. {@code label}: the room's name, else the place's, else null (the screen words it by type). */
	public record RoomShape(String zone, String type, List<Rect> rects, String label, String place) {
		/** Whether a hatching can apply: only the rooms of the rounds are explored one by one. */
		public boolean isRoom() {
			return "room_slot".equals(type);
		}

		public Rect bounds() {
			Rect all = rects.get(0);
			for (Rect r : rects) {
				all = all.union(r);
			}
			return all;
		}

		public boolean contains(double x, double z) {
			return rects.stream().anyMatch(r -> r.contains(x, z));
		}
	}

	public record DoorShape(String id, String type, Rect rect, List<String> colors) {
	}

	public record FloorMap(List<RoomShape> rooms, List<DoorShape> doors, Rect bounds) {
		public boolean isEmpty() {
			return rooms.isEmpty() && doors.isEmpty();
		}
	}

	private MapModel() {
	}

	/** The floors of the plan in order of height (lowest first), or the single implicit one. */
	public static List<FloorInfo> floors(LayoutDto plan) {
		List<FloorInfo> out = new ArrayList<>();
		for (LayoutDto.Floor f : plan.floors) {
			if (f.id != null && f.yMin != null && f.yMax != null && f.yMin <= f.yMax) {
				out.add(new FloorInfo(f.id, f.name == null || f.name.isBlank() ? f.id : f.name, f.yMin, f.yMax));
			}
		}
		if (out.isEmpty()) {
			return List.of(new FloorInfo(IMPLICIT_FLOOR, null, Integer.MIN_VALUE, Integer.MAX_VALUE));
		}
		out.sort(java.util.Comparator.comparingInt(FloorInfo::yMin));
		return List.copyOf(out);
	}

	/** Whether a box of heights {@code minY..maxY} touches this floor. */
	public static boolean touches(FloorInfo floor, int minY, int maxY) {
		return minY <= floor.yMax() && floor.yMin() <= maxY;
	}

	/** The index of the floor a point at height {@code y} stands on, or -1 when it is above or below all of them. */
	public static int floorAt(List<FloorInfo> floors, double y) {
		int block = (int) Math.floor(y);
		for (int i = 0; i < floors.size(); i++) {
			if (touches(floors.get(i), block, block)) {
				return i;
			}
		}
		return -1;
	}

	/** The place of the plan whose 48 x 48 footprint holds the centre of this zone's first box, or null. */
	public static Slot placeOf(LayoutDto plan, Zone zone) {
		if (zone.boxes.isEmpty() || zone.boxes.get(0) == null) {
			return null;
		}
		Box b = zone.boxes.get(0);
		double cx = (b.minX + b.maxX + 1) / 2.0;
		double cz = (b.minZ + b.maxZ + 1) / 2.0;
		for (Slot slot : plan.roomSlots) {
			if (slot.origin != null && cx >= slot.origin.x && cx < slot.origin.x + PLACE_SIDE && cz >= slot.origin.z
					&& cz < slot.origin.z + PLACE_SIDE) {
				return slot;
			}
		}
		return null;
	}

	/**
	 * The rooms and doors of one floor. {@code names}: the room of each place ({@link MapData#rooms()}). A room's label is the
	 * drawn room's name, else the place's id; other zones have none (the screen words them by their type).
	 */
	public static FloorMap build(LayoutDto plan, FloorInfo floor, Map<String, MapData.Room> names) {
		List<RoomShape> rooms = new ArrayList<>();
		for (Zone zone : plan.zones) {
			List<Rect> rects = new ArrayList<>();
			for (Box box : zone.boxes) {
				if (box != null && touches(floor, box.minY, box.maxY)) {
					rects.add(new Rect(box.minX, box.minZ, box.maxX, box.maxZ));
				}
			}
			if (rects.isEmpty()) {
				continue;
			}
			String label = null;
			String placeId = null;
			if ("room_slot".equals(zone.type)) {
				Slot place = placeOf(plan, zone);
				if (place != null) {
					placeId = place.id;
					MapData.Room room = names.get(place.id);
					label = room != null ? room.name() : place.id;
				}
			}
			rooms.add(new RoomShape(zone.id, zone.type, List.copyOf(rects), label, placeId));
		}
		List<DoorShape> doors = new ArrayList<>();
		for (Door door : plan.doors) {
			Box b = door.block;
			if (b != null && touches(floor, b.minY, b.maxY)) {
				doors.add(new DoorShape(door.id, door.type, new Rect(b.minX, b.minZ, b.maxX, b.maxZ), List.copyOf(door.colors)));
			}
		}
		Rect bounds = null;
		for (RoomShape r : rooms) {
			bounds = bounds == null ? r.bounds() : bounds.union(r.bounds());
		}
		for (DoorShape d : doors) {
			bounds = bounds == null ? d.rect() : bounds.union(d.rect());
		}
		return new FloorMap(List.copyOf(rooms), List.copyOf(doors), bounds);
	}

	// ---------------------------------------------------------------- annotations

	/** One stroke: a pen colour (index into the screen's inks) and its points, flat {@code x0, z0, x1, z1, ...} in hub blocks. */
	public static final class Stroke {
		public final int color;
		public final List<Double> points = new ArrayList<>();

		public Stroke(int color) {
			this.color = color;
		}

		public int pointCount() {
			return points.size() / 2;
		}

		public void add(double x, double z) {
			points.add(x);
			points.add(z);
		}

		/** Whether any segment of the stroke passes within {@code radius} blocks of the point. */
		public boolean near(double x, double z, double radius) {
			if (points.size() == 2) {
				return Math.hypot(points.get(0) - x, points.get(1) - z) <= radius;
			}
			for (int i = 0; i + 3 < points.size(); i += 2) {
				if (distanceToSegment(x, z, points.get(i), points.get(i + 1), points.get(i + 2), points.get(i + 3)) <= radius) {
					return true;
				}
			}
			return false;
		}
	}

	/** Distance from a point to the segment {@code a-b}. */
	public static double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
		double dx = bx - ax;
		double dz = bz - az;
		double len2 = dx * dx + dz * dz;
		double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2));
		return Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
	}

	/** Removes every stroke that passes within {@code radius} blocks of the point; returns how many went. */
	public static int erase(List<Stroke> strokes, double x, double z, double radius) {
		int before = strokes.size();
		strokes.removeIf(s -> s.near(x, z, radius));
		return before - strokes.size();
	}

	public static int pointCount(List<Stroke> strokes) {
		return strokes.stream().mapToInt(Stroke::pointCount).sum();
	}
}
