package com.mystaria.events.trahison.edit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The selection tool shared by the editors: two corners (A on left click, B on right click) in this world's blocks.
 * Sneaking picks the block in front of the face that was hit (the air of a doorway or of a room's inner space). Pure
 * state plus the pick; each editor owns one instance and decides what the selection is applied to.
 */
final class Selection {

	/** How far a click reaches to pick a block. */
	static final double REACH = 128.0;

	BlockPos a;
	BlockPos b;

	/** The block a click would pick (sneaking: in front of the hit face), or null when the player aims at nothing. */
	static BlockPos pick(Minecraft mc) {
		HitResult hit = mc.player.pick(REACH, 1.0F, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		return mc.player.isShiftKeyDown() ? block.getBlockPos().relative(block.getDirection()) : block.getBlockPos();
	}

	/** Sets corner A ({@code first}) or B; the other corner follows when it was not set yet. */
	void set(boolean first, BlockPos pos) {
		if (first) {
			a = pos;
			if (b == null) {
				b = pos;
			}
		} else {
			b = pos;
			if (a == null) {
				a = pos;
			}
		}
	}

	/** The selection as {@code {minX, minY, minZ, maxX, maxY, maxZ}}, or null. */
	int[] local() {
		if (a == null) {
			return null;
		}
		BlockPos c = b == null ? a : b;
		return new int[] { Math.min(a.getX(), c.getX()), Math.min(a.getY(), c.getY()), Math.min(a.getZ(), c.getZ()),
				Math.max(a.getX(), c.getX()), Math.max(a.getY(), c.getY()), Math.max(a.getZ(), c.getZ()) };
	}

	void clear() {
		a = null;
		b = null;
	}
}
