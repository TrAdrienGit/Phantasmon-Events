package com.mystaria.events.trahison.world;

import java.util.List;

/**
 * Where a room's slot stands in the hub: the pure arithmetic between a slot's position in the room's own frame and a hub
 * block (Documentation/trahison/protocole.md, "Place d'une salle"). A slot is stored in the room's canonical frame (x east,
 * z south, inside a 48 x 48 square, the room's north side being its exit); the place turns that square by {@code k} quarter
 * turns clockwise seen from above ({@code k} = 0 north, 1 east, 2 south, 3 west: the world side the room's north points to),
 * then puts its minimum corner at the place's {@code origin}. A slot is a box from its position (minimum corner) to
 * position + size, and its orientation is the side it <b>faces</b> (toward the players). Pure: no Minecraft class.
 */
public final class RoomFrame {

	/** The orientation names, clockwise from north. */
	public static final List<String> ORIENTATIONS = List.of("north", "east", "south", "west");
	/** The side of the square a room is stored in. */
	public static final int SQUARE = 48;

	private RoomFrame() {
	}

	/** Quarter turns of an orientation name (north 0 ... west 3); an unknown name counts as north. */
	public static int quarterTurns(String orientation) {
		int at = ORIENTATIONS.indexOf(orientation == null ? "north" : orientation);
		return Math.max(0, at);
	}

	/** An orientation turned by {@code k} quarter turns clockwise. */
	public static String turn(String orientation, int k) {
		return ORIENTATIONS.get(Math.floorMod(quarterTurns(orientation) + k, 4));
	}

	/** The horizontal unit vector {@code {dx, dz}} of an orientation (north is {@code -z}, east {@code +x}). */
	public static int[] direction(String orientation) {
		return switch (quarterTurns(orientation)) {
			case 1 -> new int[] { 1, 0 };
			case 2 -> new int[] { 0, 1 };
			case 3 -> new int[] { -1, 0 };
			default -> new int[] { 0, -1 };
		};
	}

	/** A continuous point {@code {x, z}} of the canonical square turned by {@code k} quarter turns clockwise inside the same square. */
	public static double[] rotate(double x, double z, int k) {
		return switch (Math.floorMod(k, 4)) {
			case 1 -> new double[] { SQUARE - z, x };
			case 2 -> new double[] { SQUARE - x, SQUARE - z };
			case 3 -> new double[] { z, SQUARE - x };
			default -> new double[] { x, z };
		};
	}

	/**
	 * The box of a slot in hub coordinates: {@code {minX, minY, minZ, maxX, maxY, maxZ}}, x and z continuous (hub blocks),
	 * y the world's. {@code origin*}: the place's minimum corner; {@code k}: its quarter turns; the slot's canonical
	 * position and size follow.
	 */
	public static double[] box(int originX, int originY, int originZ, int k, int x, int y, int z, int sx, int sy, int sz) {
		double[] a = rotate(x, z, k);
		double[] b = rotate(x + Math.max(1, sx), z + Math.max(1, sz), k);
		return new double[] {
				originX + Math.min(a[0], b[0]), originY + y, originZ + Math.min(a[1], b[1]),
				originX + Math.max(a[0], b[0]), originY + y + Math.max(1, sy), originZ + Math.max(a[1], b[1]) };
	}

	/** The world side a slot faces: its orientation turned by the place's quarter turns. */
	public static String facing(String slotOrientation, int k) {
		return turn(slotOrientation, k);
	}
}
