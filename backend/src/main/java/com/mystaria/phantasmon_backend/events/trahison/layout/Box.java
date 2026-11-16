package com.mystaria.phantasmon_backend.events.trahison.layout;

/**
 * An axis-aligned box of blocks, both corners included, in hub coordinates ({@code x} and {@code z} of the hub, {@code y}
 * of the world). A valid box has {@code min <= max} on every axis.
 */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

	public boolean isValid() {
		return minX <= maxX && minY <= maxY && minZ <= maxZ;
	}

	/** Whether the two boxes share at least one block. Touching faces (adjacent blocks) do not overlap. */
	public boolean overlaps(Box other) {
		return minX <= other.maxX && other.minX <= maxX
				&& minY <= other.maxY && other.minY <= maxY
				&& minZ <= other.maxZ && other.minZ <= maxZ;
	}
}
