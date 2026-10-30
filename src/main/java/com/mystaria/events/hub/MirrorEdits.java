package com.mystaria.events.hub;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks a player changes by hand in a single-player world (placed, used, broken), seen on the integrated server
 * ({@code ServerPlayerGameModeGhostMixin}, {@code LevelGhostEditMixin}): in a mirror hub's copy they become ghost
 * blocks ({@link MirrorCopy}). Only the player's own actions count — flowing water, growing grass and the copy's own
 * writes never do.
 */
public final class MirrorEdits {

	/** A block changed by hand: its new state as a string, {@code ""} for air. */
	record Edit(ResourceKey<Level> dimension, BlockPos pos, String name) {
	}

	private static final Queue<Edit> EDITS = new ConcurrentLinkedQueue<>();
	/** Server thread: depth of the player actions being run, and the blocks they set. */
	private static int depth;
	private static ServerLevel level;
	private static final Set<BlockPos> TOUCHED = new LinkedHashSet<>();

	private MirrorEdits() {
	}

	/** Server thread: a player action starts. */
	public static void begin() {
		depth++;
	}

	/** Server thread: the action ends; its blocks are read now (signs, Templates' data set during it included). */
	public static void end() {
		if (--depth > 0 || TOUCHED.isEmpty()) {
			return;
		}
		depth = 0;
		for (BlockPos pos : TOUCHED) {
			BlockState state = level.getBlockState(pos);
			EDITS.add(new Edit(level.dimension(), pos, state.isAir() ? "" : MirrorTerrain.name(level, pos, state)));
		}
		TOUCHED.clear();
		level = null;
	}

	/** Server thread: a block was set; kept if a player action is running. */
	public static void onSetBlock(Level atLevel, BlockPos pos) {
		if (depth > 0 && atLevel instanceof ServerLevel serverLevel) {
			if (level != serverLevel) {
				TOUCHED.clear();
				level = serverLevel;
			}
			TOUCHED.add(pos.immutable());
		}
	}

	/** Client thread: the edits since the last call. */
	static List<Edit> drain() {
		List<Edit> list = new ArrayList<>();
		for (Edit edit; (edit = EDITS.poll()) != null;) {
			list.add(edit);
		}
		return list;
	}
}
