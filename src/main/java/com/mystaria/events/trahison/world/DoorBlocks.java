package com.mystaria.events.trahison.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Door;

/**
 * The block a closed door is made of, in one place so it can be tuned. Colour doors are split in two halves, one per
 * colour (along the longest horizontal side of the box, or from the top down when the box is a single column); the
 * others are one block each: a room's exit reads as a gray pane, a cabin door as dark wood, the final door as obsidian
 * (imposing), a plain door as stone bricks. All of them are full blocks, because a collision shape with gaps (bars,
 * panes) would let a player through.
 */
public final class DoorBlocks {

	private DoorBlocks() {
	}

	public static final BlockState RED = Blocks.RED_CONCRETE.defaultBlockState();
	public static final BlockState GREEN = Blocks.GREEN_CONCRETE.defaultBlockState();
	public static final BlockState BLUE = Blocks.BLUE_CONCRETE.defaultBlockState();
	public static final BlockState ROOM_EXIT = Blocks.GRAY_STAINED_GLASS.defaultBlockState();
	public static final BlockState CABIN = Blocks.DARK_OAK_PLANKS.defaultBlockState();
	public static final BlockState FINAL = Blocks.OBSIDIAN.defaultBlockState();
	public static final BlockState PLAIN = Blocks.STONE_BRICKS.defaultBlockState();
	/** A door of a kind this client does not know. */
	public static final BlockState UNKNOWN = Blocks.STONE.defaultBlockState();

	/**
	 * The block at a position of the door's box ({@code world}: the box as this world's blocks, both corners included).
	 */
	public static BlockState at(Door door, Box world, int x, int y, int z) {
		String type = door.type == null ? "" : door.type;
		return switch (type) {
			case "color" -> colorAt(door, world, x, y, z);
			case "room_exit" -> ROOM_EXIT;
			case "cabin" -> CABIN;
			case "final" -> FINAL;
			case "plain" -> PLAIN;
			default -> UNKNOWN;
		};
	}

	private static BlockState colorAt(Door door, Box box, int x, int y, int z) {
		if (door.colors == null || door.colors.size() != 2) {
			return UNKNOWN;
		}
		int sx = box.maxX - box.minX + 1;
		int sz = box.maxZ - box.minZ + 1;
		boolean second;
		if (sx >= sz && sx > 1) {
			second = (x - box.minX) * 2 >= sx;
		} else if (sz > 1) {
			second = (z - box.minZ) * 2 >= sz;
		} else {
			second = (y - box.minY) * 2 >= box.maxY - box.minY + 1;
		}
		return named(door.colors.get(second ? 1 : 0));
	}

	private static BlockState named(String color) {
		return switch (color == null ? "" : color.toLowerCase(java.util.Locale.ROOT)) {
			case "red" -> RED;
			case "green" -> GREEN;
			case "blue" -> BLUE;
			default -> UNKNOWN;
		};
	}
}
