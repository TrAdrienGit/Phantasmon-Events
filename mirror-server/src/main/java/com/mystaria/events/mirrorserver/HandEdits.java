package com.mystaria.events.mirrorserver;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The blocks an <b>operator</b> changes by hand on this server (placed, used, broken), as the solo copy's
 * {@code MirrorEdits} did on the integrated server: in a mirror copy they become ghost blocks, shown in the source zone.
 * Only the operator's own actions count — flowing water, growing grass, the copy's own writes and other players never
 * do. Server thread only.
 */
public final class HandEdits {

	/** A block changed by hand: where, and the level it is in. */
	public record Edit(ServerLevel level, BlockPos pos) {
	}

	private static int depth;
	private static boolean counting;
	private static ServerLevel level;
	private static final Set<BlockPos> TOUCHED = new LinkedHashSet<>();
	private static Consumer<Edit> listener = edit -> { };

	private HandEdits() {
	}

	static void listen(Consumer<Edit> onEdit) {
		listener = onEdit;
	}

	/** A player's action starts; only an operator's is counted. */
	public static void begin(ServerPlayer player) {
		if (depth++ == 0) {
			counting = player != null && player.server.getPlayerList().isOp(player.getGameProfile());
		}
	}

	/** The action ends: its blocks are read now (signs and block entity data set during it included). */
	public static void end() {
		if (--depth > 0) {
			return;
		}
		depth = 0;
		if (counting && level != null) {
			for (BlockPos pos : TOUCHED) {
				listener.accept(new Edit(level, pos));
			}
		}
		TOUCHED.clear();
		level = null;
		counting = false;
	}

	/** A block was set; kept if an operator's action is running. */
	public static void onSetBlock(Level atLevel, BlockPos pos) {
		if (depth > 0 && counting && atLevel instanceof ServerLevel serverLevel) {
			if (level != serverLevel) {
				TOUCHED.clear();
				level = serverLevel;
			}
			TOUCHED.add(pos.immutable());
		}
	}
}
