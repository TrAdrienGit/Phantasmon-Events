package com.mystaria.events.trahison.edit;

import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Pos;
import com.mystaria.events.trahison.world.HubBlocks;
import com.mystaria.phantasmon.client.hub.HubCoordinates;

/**
 * Block positions between this world and hub coordinates (Documentation/trahison/architecture.md section 6: {@code x}
 * and {@code z} of the hub, {@code y} of the world). The mapping is {@link HubBlocks}', the one the mirror hubs use for
 * their terrain and ghost blocks ({@code floor} of the anchor's origin, no rotation), so a box saved in the editor and
 * the fake blocks and zones computed from it fall on exactly the same blocks. Every conversion is an exact bijection on
 * blocks. Pure: no Minecraft class.
 */
public final class HubGrid {

	private final HubBlocks blocks;

	public HubGrid(HubCoordinates anchor) {
		this.blocks = HubBlocks.of(anchor);
	}

	/** A box of local blocks (both corners included) as a hub box. */
	public Box toHub(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int ax = blocks.hubX(minX);
		int bx = blocks.hubX(maxX);
		int az = blocks.hubZ(minZ);
		int bz = blocks.hubZ(maxZ);
		return new Box(Math.min(ax, bx), Math.min(minY, maxY), Math.min(az, bz), Math.max(ax, bx), Math.max(minY, maxY), Math.max(az, bz));
	}

	/** A hub box as local blocks (both corners included). */
	public Box toLocal(Box box) {
		int ax = blocks.worldX(box.minX);
		int bx = blocks.worldX(box.maxX);
		int az = blocks.worldZ(box.minZ);
		int bz = blocks.worldZ(box.maxZ);
		return new Box(Math.min(ax, bx), box.minY, Math.min(az, bz), Math.max(ax, bx), box.maxY, Math.max(az, bz));
	}

	/** One local block as a hub block. */
	public Pos toHub(int x, int y, int z) {
		return new Pos(blocks.hubX(x), y, blocks.hubZ(z));
	}

	/** One hub block as a local block. */
	public Pos toLocal(Pos pos) {
		return new Pos(blocks.worldX(pos.x), pos.y, blocks.worldZ(pos.z));
	}
}
