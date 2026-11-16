package com.mystaria.events.trahison.world;

import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.ResultsBoard;

/**
 * Where the results board stands, as pure arithmetic (no Minecraft class), shared by the in-world renderer and the editor.
 * {@code facing} (north, east, south, west) is the side the readers stand on, so the board's normal is that direction and
 * the readers look the opposite way; their right hand is {@code (fz, -fx)} (a board facing south has its width growing
 * towards +x, one facing north towards -x). {@code position} is the lowest block on the readers' left. In hub
 * coordinates, which are this world's blocks shifted (no turn): see {@link HubBlocks}.
 */
public final class BoardGeometry {

	private BoardGeometry() {
	}

	/** {@code {fx, fz}} of a facing, or null when unknown. */
	public static int[] normal(String facing) {
		if (facing == null) {
			return null;
		}
		return switch (facing) {
			case "north" -> new int[] { 0, -1 };
			case "east" -> new int[] { 1, 0 };
			case "south" -> new int[] { 0, 1 };
			case "west" -> new int[] { -1, 0 };
			default -> null;
		};
	}

	/** The readers' right hand {@code {rx, rz}}, or null when the facing is unknown. */
	public static int[] right(String facing) {
		int[] n = normal(facing);
		return n == null ? null : new int[] { n[1], -n[0] };
	}

	/** The yaw of a block that faces this way, as {@code Direction.toYRot} gives it (south 0, west 90, north 180, east 270). */
	public static float yaw(String facing) {
		return switch (facing == null ? "" : facing) {
			case "west" -> 90f;
			case "north" -> 180f;
			case "east" -> 270f;
			default -> 0f;
		};
	}

	/** The blocks the board spans (both corners included), in the coordinates of its position, or null when incomplete. */
	public static Box blocks(ResultsBoard board) {
		if (board == null || !board.complete() || right(board.facing) == null) {
			return null;
		}
		int[] r = right(board.facing);
		int x2 = board.position.x + r[0] * (board.width - 1);
		int z2 = board.position.z + r[1] * (board.width - 1);
		return new Box(Math.min(board.position.x, x2), board.position.y, Math.min(board.position.z, z2),
				Math.max(board.position.x, x2), board.position.y + board.height - 1, Math.max(board.position.z, z2));
	}

	/**
	 * The board's face as a plane in block coordinates ({@code x, z} of its lower-left corner on the readers' side), for the
	 * block position {@code (blockX, blockZ)} of the board's lowest-left block (already in this world's coordinates).
	 */
	public static double[] faceCorner(String facing, int blockX, int blockZ) {
		int[] n = normal(facing);
		int[] r = right(facing);
		double x = n[0] == 0 ? (r[0] > 0 ? blockX : blockX + 1) : (n[0] > 0 ? blockX + 1 : blockX);
		double z = n[1] == 0 ? (r[1] > 0 ? blockZ : blockZ + 1) : (n[1] > 0 ? blockZ + 1 : blockZ);
		return new double[] { x, z };
	}

	/**
	 * Fits a board to a box of blocks (hub coordinates): the face on the {@code facing} side of the box, the width along the
	 * readers' right, the height from the box's bottom. Returns a new board with the given facing.
	 */
	public static ResultsBoard fit(Box box, String facing) {
		int[] n = normal(facing);
		int[] r = right(facing);
		ResultsBoard board = new ResultsBoard();
		board.facing = facing;
		int x = n[0] == 0 ? (r[0] > 0 ? box.minX : box.maxX) : (n[0] > 0 ? box.maxX : box.minX);
		int z = n[1] == 0 ? (r[1] > 0 ? box.minZ : box.maxZ) : (n[1] > 0 ? box.maxZ : box.minZ);
		board.position = new com.mystaria.events.trahison.edit.LayoutDto.Pos(x, box.minY, z);
		board.width = r[0] != 0 ? box.maxX - box.minX + 1 : box.maxZ - box.minZ + 1;
		board.height = box.maxY - box.minY + 1;
		return board;
	}
}
