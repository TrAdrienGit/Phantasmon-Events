package com.mystaria.events.trahison.world;

import com.mystaria.phantasmon.client.hub.HubAnchorDto;
import com.mystaria.phantasmon.client.hub.HubCoordinates;

/**
 * Where a hub block stands in this world: the single mapping the plan, the editor, the zones and the fake blocks share
 * (Documentation/trahison/architecture.md section 6). It is the mirror system's own: a mirror hub's terrain is never turned
 * and its centre block is {@code floor(origin)} ({@code MirrorTerrain.Zone.of}, {@code MirrorGhosts}), so a block of the
 * plan {@code (hx, hz)} is the world block {@code (floor(originX) + hx, floor(originZ) + hz)}, whatever the anchor's yaw;
 * {@code y} is the world's. Pure: no Minecraft class.
 */
public record HubBlocks(int originX, int originZ) {

	public static HubBlocks of(HubAnchorDto anchor) {
		return of(anchor.coordinates());
	}

	public static HubBlocks of(HubCoordinates anchor) {
		return new HubBlocks((int) Math.floor(anchor.originX()), (int) Math.floor(anchor.originZ()));
	}

	public int worldX(int hubX) {
		return originX + hubX;
	}

	public int worldZ(int hubZ) {
		return originZ + hubZ;
	}

	public int hubX(int worldX) {
		return worldX - originX;
	}

	public int hubZ(int worldZ) {
		return worldZ - originZ;
	}

	/** A position of this world (fractional) in hub coordinates, still fractional: its block is the floor of each. */
	public double hubX(double worldX) {
		return worldX - originX;
	}

	public double hubZ(double worldZ) {
		return worldZ - originZ;
	}
}
