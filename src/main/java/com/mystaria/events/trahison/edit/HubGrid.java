package com.mystaria.events.trahison.edit;

import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Pos;
import com.mystaria.phantasmon.client.hub.HubCoordinates;

/**
 * Block positions between this world and hub coordinates (Documentation/trahison/architecture.md section 6: {@code x}
 * and {@code z} of the hub, {@code y} of the world). An anchor's origin is where its builder stood, a fractional position:
 * the grid rounds it to the nearest block corner, so every conversion is an exact bijection on blocks (a box saved and
 * loaded through the same anchor comes back identical) and is at most half a block away from the true hub coordinates.
 * Pure: no Minecraft class.
 */
public final class HubGrid {

	private final HubCoordinates hub;

	public HubGrid(HubCoordinates anchor) {
		this.hub = new HubCoordinates(Math.round(anchor.originX()), anchor.originY(), Math.round(anchor.originZ()), anchor.yaw(),
				anchor.sizeX(), anchor.sizeY(), anchor.sizeZ());
	}

	/** A box of local blocks (both corners included) as a hub box. */
	public Box toHub(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		int[] a = corner(hub.toHub(minX, minZ));
		int[] b = corner(hub.toHub(maxX + 1, maxZ + 1));
		return new Box(Math.min(a[0], b[0]), Math.min(minY, maxY), Math.min(a[1], b[1]),
				Math.max(a[0], b[0]) - 1, Math.max(minY, maxY), Math.max(a[1], b[1]) - 1);
	}

	/** A hub box as local blocks (both corners included). */
	public Box toLocal(Box box) {
		int[] a = corner(hub.toLocal(box.minX, box.minZ));
		int[] b = corner(hub.toLocal(box.maxX + 1, box.maxZ + 1));
		return new Box(Math.min(a[0], b[0]), box.minY, Math.min(a[1], b[1]),
				Math.max(a[0], b[0]) - 1, box.maxY, Math.max(a[1], b[1]) - 1);
	}

	/** One local block as a hub block. */
	public Pos toHub(int x, int y, int z) {
		Box box = toHub(x, y, z, x, y, z);
		return new Pos(box.minX, y, box.minZ);
	}

	/** One hub block as a local block. */
	public Pos toLocal(Pos pos) {
		Box box = toLocal(new Box(pos.x, pos.y, pos.z, pos.x, pos.y, pos.z));
		return new Pos(box.minX, pos.y, box.minZ);
	}

	private static int[] corner(double[] xz) {
		return new int[] { (int) Math.round(xz[0]), (int) Math.round(xz[1]) };
	}
}
