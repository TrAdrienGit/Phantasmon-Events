package com.mystaria.events.trahison.edit;

import java.util.List;

import com.mystaria.events.trahison.edit.LayoutDto.Box;

/**
 * The fixed shape of a room's place and the one frame every room is stored in (Documentation/trahison/plan.md section 7,
 * architecture.md section 7). A room is always saved in its <b>canonical orientation</b>; the layout rotates it into
 * a place (architecture section 8), so these constants are the contract with the plan's room slots (their corridors and
 * doors must meet the openings below). Change them here only, and in {@code guide-editeur-salle.md}.
 *
 * <p>Canonical frame, in blocks relative to the template's minimum corner: {@code x} 0..47 (east), {@code y} 0..23 (up),
 * {@code z} 0..47 (south). Layer {@code y = 0} is the floor; the two openings are three blocks wide and three high and
 * must stay <b>air</b> in the built room (the plan closes them with fake blocks, never the room itself).
 *
 * <pre>
 *   north (z = 0)        EXIT   opening x 22..24, y 1..3, on the north edge
 *   +-----------------+
 *   |                 |
 *   |   48 x 48 x 24  |
 *   |                 |
 *   +-----------------+
 *   south (z = 47)       ENTRY  opening x 22..24, y 1..3, on the south edge
 * </pre>
 *
 * <p>The builder's anchor: the block <i>under</i> the player's feet when the editor starts is the floor block at the
 * middle of the entry ({@link #ANCHOR_X}, 0, {@link #ANCHOR_Z}); the player faces into the room, so the room extends in
 * front of them. Pure data and arithmetic: no Minecraft class.
 */
final class RoomTemplate {

	/** Edge lengths: x, y, z. Same as the backend's {@code RoomValidator} (48 x 24 x 48). */
	static final int SIZE_X = 48;
	static final int SIZE_Y = 24;
	static final int SIZE_Z = 48;

	/** The entry: on the south edge, 3 wide and 3 high above the floor. */
	static final Box ENTRY = new Box(22, 1, 47, 24, 3, 47);
	/** The exit: on the north edge, same size, in line with the entry. */
	static final Box EXIT = new Box(22, 1, 0, 24, 3, 0);

	/** The floor block at the middle of the entry, where the builder stands. */
	static final int ANCHOR_X = 23;
	static final int ANCHOR_Z = 47;

	static final List<String> ORIENTATIONS = List.of("north", "east", "south", "west");

	/** The template as a canonical box. */
	static final Box BOX = new Box(0, 0, 0, SIZE_X - 1, SIZE_Y - 1, SIZE_Z - 1);

	private RoomTemplate() {
	}

	/** Whether a canonical box (both corners included) lies inside the template. */
	static boolean inside(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		return minX >= 0 && minY >= 0 && minZ >= 0 && maxX < SIZE_X && maxY < SIZE_Y && maxZ < SIZE_Z && minX <= maxX && minY <= maxY && minZ <= maxZ;
	}

	/** An orientation turned by {@code k} quarter turns clockwise (seen from above); unknown names stay as they are. */
	static String turn(String orientation, int k) {
		int at = ORIENTATIONS.indexOf(orientation);
		return at < 0 ? orientation : ORIENTATIONS.get(Math.floorMod(at + k, 4));
	}

	/**
	 * Where the template stands in a world: its canonical anchor is the block {@code (ax, ay, az)} and it is turned by
	 * {@code k} quarter turns clockwise (0 north, 1 east, 2 south, 3 west: the way the builder faces).
	 */
	record Frame(int ax, int ay, int az, int k) {

		/** A canonical block as a world block. */
		int[] toWorld(int cx, int cy, int cz) {
			int dx = cx - ANCHOR_X;
			int dz = cz - ANCHOR_Z;
			int rx;
			int rz;
			switch (Math.floorMod(k, 4)) {
				case 1 -> {
					rx = -dz;
					rz = dx;
				}
				case 2 -> {
					rx = -dx;
					rz = -dz;
				}
				case 3 -> {
					rx = dz;
					rz = -dx;
				}
				default -> {
					rx = dx;
					rz = dz;
				}
			}
			return new int[] { ax + rx, ay + cy, az + rz };
		}

		/** A world block as a canonical block. */
		int[] toCanonical(int wx, int wy, int wz) {
			int rx = wx - ax;
			int rz = wz - az;
			int dx;
			int dz;
			switch (Math.floorMod(k, 4)) {
				case 1 -> {
					dx = rz;
					dz = -rx;
				}
				case 2 -> {
					dx = -rx;
					dz = -rz;
				}
				case 3 -> {
					dx = -rz;
					dz = rx;
				}
				default -> {
					dx = rx;
					dz = rz;
				}
			}
			return new int[] { ANCHOR_X + dx, wy - ay, ANCHOR_Z + dz };
		}

		/** A canonical box as a world box (both corners included). */
		Box toWorld(Box box) {
			int[] a = toWorld(box.minX, box.minY, box.minZ);
			int[] b = toWorld(box.maxX, box.maxY, box.maxZ);
			return new Box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
					Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
		}

		/** A world box as a canonical box (both corners included). */
		Box toCanonical(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			int[] a = toCanonical(minX, minY, minZ);
			int[] b = toCanonical(maxX, maxY, maxZ);
			return new Box(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
					Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
		}

		/** A canonical orientation as seen in this world. */
		String orientationToWorld(String canonical) {
			return turn(canonical, k);
		}

		/** An orientation of this world as a canonical one. */
		String orientationToCanonical(String world) {
			return turn(world, -k);
		}
	}
}
