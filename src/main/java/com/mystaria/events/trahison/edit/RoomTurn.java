package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;

import com.mystaria.phantasmon.client.hub.HubSchematic;

/**
 * Turns a room's blocks by quarter turns (clockwise seen from above, the way {@code RoomFrame} turns a place's square):
 * positions, block entities (they ride on their block) and every state through {@code BlockState.rotate}, so stairs, doors,
 * wall signs, logs and the like face the right way (solutions-techniques.md S11). The positions are pure arithmetic; the
 * states go through a {@link UnaryOperator} so a test can stand in for Minecraft's registries.
 *
 * <p>Position of a block of a box {@code sizeX x sizeZ} after {@code k} turns: 1: {@code (sizeZ-1-z, x)}, 2:
 * {@code (sizeX-1-x, sizeZ-1-z)}, 3: {@code (z, sizeX-1-x)}; the sizes swap for 1 and 3. In a 48 x 48 square this is the
 * continuous {@code RoomFrame.rotate} on block centres.
 */
public final class RoomTurn {

	private RoomTurn() {
	}

	/** The box and its blocks turned {@code k} quarter turns clockwise; {@code states} turns one state tag. */
	public static HubSchematic turn(HubSchematic s, int k, UnaryOperator<CompoundTag> states) {
		int q = Math.floorMod(k, 4);
		if (q == 0) {
			return s;
		}
		boolean swap = q != 2;
		Map<CompoundTag, CompoundTag> cache = new HashMap<>();
		List<HubSchematic.Block> out = new ArrayList<>(s.blocks().size());
		for (HubSchematic.Block b : s.blocks()) {
			int[] p = position(b.x(), b.z(), s.sizeX(), s.sizeZ(), q);
			CompoundTag state = cache.computeIfAbsent(b.state(), key -> states.apply(key.copy()));
			out.add(new HubSchematic.Block(p[0], b.y(), p[1], state, b.blockEntity()));
		}
		return new HubSchematic(swap ? s.sizeZ() : s.sizeX(), s.sizeY(), swap ? s.sizeX() : s.sizeZ(), List.copyOf(out));
	}

	/** One block column of a {@code sizeX x sizeZ} box after {@code q} (1..3) quarter turns. */
	public static int[] position(int x, int z, int sizeX, int sizeZ, int q) {
		return switch (Math.floorMod(q, 4)) {
			case 1 -> new int[] { sizeZ - 1 - z, x };
			case 2 -> new int[] { sizeX - 1 - x, sizeZ - 1 - z };
			case 3 -> new int[] { z, sizeX - 1 - x };
			default -> new int[] { x, z };
		};
	}

	/** Minecraft's own state rotation on {@code {Name, Properties}} tags; a block the registry does not know stays as it is. */
	public static UnaryOperator<CompoundTag> minecraftStates(int k) {
		Rotation rotation = Rotation.values()[Math.floorMod(k, 4)];
		HolderLookup.RegistryLookup<Block> blocks = BuiltInRegistries.BLOCK.asLookup();
		return tag -> {
			ResourceLocation id = ResourceLocation.tryParse(tag.getString("Name"));
			if (id == null || blocks.get(ResourceKey.create(Registries.BLOCK, id)).isEmpty()) {
				return tag;
			}
			return NbtUtils.writeBlockState(NbtUtils.readBlockState(blocks, tag).rotate(rotation));
		};
	}
}
