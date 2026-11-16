package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * The fixed plan as the backend stores it ({@code GET /events/trahison/layout/{hub}}, Documentation/trahison/protocole.md
 * section 4). Plain mutable classes read and written by Gson (camelCase fields, snake_case JSON); every position is in
 * hub coordinates. Nothing here is a game rule: the backend validates.
 */
public final class LayoutDto {

	/** The plan's version (0: the hub has none yet) and whether it is an incomplete draft. */
	public int version;
	public boolean draft = true;
	public List<Zone> zones = new ArrayList<>();
	public List<Door> doors = new ArrayList<>();
	public List<Slot> roomSlots = new ArrayList<>();
	public List<Cabin> cabins = new ArrayList<>();
	public List<Spot> hidingSpots = new ArrayList<>();

	/** A box of blocks, both corners included. */
	public static final class Box {
		public int minX;
		public int minY;
		public int minZ;
		public int maxX;
		public int maxY;
		public int maxZ;

		public Box() {
		}

		public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
		}
	}

	public static final class Pos {
		public int x;
		public int y;
		public int z;

		public Pos() {
		}

		public Pos(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	public static final class Zone {
		public String id;
		public String type;
		public List<Box> boxes = new ArrayList<>();
	}

	public static final class Door {
		public String id;
		public String type;
		/** The fake blocks. */
		public Box block;
		public String zoneA;
		public String zoneB;
		/** Colour doors only. */
		public Integer round;
		public List<String> colors = new ArrayList<>();
	}

	public static final class Slot {
		public String id;
		public Integer round;
		public Pos origin;
		public String orientation;
	}

	public static final class Cabin {
		public String id;
		public String serves;
		public String side;
		public String zone;
		public String door;
	}

	public static final class Spot {
		public String id;
		public Pos position;
	}

	/** Gson leaves a list null when the JSON lacks it. */
	public LayoutDto normalized() {
		zones = zones == null ? new ArrayList<>() : zones;
		doors = doors == null ? new ArrayList<>() : doors;
		roomSlots = roomSlots == null ? new ArrayList<>() : roomSlots;
		cabins = cabins == null ? new ArrayList<>() : cabins;
		hidingSpots = hidingSpots == null ? new ArrayList<>() : hidingSpots;
		zones.forEach(z -> z.boxes = z.boxes == null ? new ArrayList<>() : z.boxes);
		doors.forEach(d -> d.colors = d.colors == null ? new ArrayList<>() : d.colors);
		return this;
	}
}
