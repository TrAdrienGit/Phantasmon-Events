package com.mystaria.events.trahison.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.mystaria.events.hub.MirrorGhosts;
import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Door;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;

/**
 * The closed doors of the plan as fake blocks (solution S1): for every door the backend says is {@code CLOSED} in the
 * latest view ({@code doors_state}), the door's box of the plan is filled with its block ({@link DoorBlocks}) through
 * {@link MirrorGhosts#putLocal}, owner {@value #OWNER}; an open door's blocks are taken away. The mirror system keeps the
 * blocks against the server's updates and chunk reloads and makes them unbreakable.
 *
 * <p>Only air is filled, never a real block: a position whose real state is not air is left alone, and one in a chunk that
 * is not loaded waits (retried every {@value #RETRY_TICKS} ticks) until it loads. Ghosts and spectators get none (they
 * walk through); everything is taken away when there is no game, when it ended, or when the player leaves the hub.
 * Positions come from the plan through {@link HubBlocks}, the mapping the mirror hubs use. Client thread.
 */
public final class FakeBlocks {

	/** The owner under which the blocks are filed in {@link MirrorGhosts}. */
	public static final String OWNER = "trahison";
	private static final int RETRY_TICKS = 10;

	/** One closed door being kept: its box in this world, and where its blocks stand. */
	private static final class Fill {
		final Door door;
		final Box box;
		/** Positions to fill (air not checked yet, or chunk not loaded). */
		final Set<Long> pending = new HashSet<>();
		final Set<Long> applied = new HashSet<>();

		Fill(Door door, Box box) {
			this.door = door;
			this.box = box;
			for (int x = box.minX; x <= box.maxX; x++) {
				for (int y = box.minY; y <= box.maxY; y++) {
					for (int z = box.minZ; z <= box.maxZ; z++) {
						pending.add(BlockPos.asLong(x, y, z));
					}
				}
			}
		}
	}

	private static final Map<String, Fill> FILLS = new HashMap<>();
	private static ClientLevel lastLevel;
	private static int ticks;
	/** What the fills were computed from: a change of any of them recomputes them. */
	private static View lastView;
	private static LayoutDto lastPlan;
	private static HubBlocks lastBlocks;

	private FakeBlocks() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FakeBlocks::tick);
	}

	/** Whether a closed door of the game stands at this position (for the checks of other features). */
	public static boolean holds(BlockPos pos) {
		return MirrorGhosts.hasLocal(OWNER, pos);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			// A new world: the mirror system dropped our blocks with the old one.
			FILLS.clear();
			lastLevel = level;
			lastView = null;
		}
		if (level == null) {
			return;
		}
		View view = GameView.current();
		HubAnchorDto anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		boolean show = anchor != null && view != null && anchor.hub().equals(Link.hub()) && walls(view);
		LayoutDto plan = show ? Plan.get(view.hub(), view.layoutVersion()) : null;
		if (plan == null) {
			if (!FILLS.isEmpty()) {
				dropAll();
			}
			lastView = null;
			lastPlan = null;
			return;
		}
		HubBlocks blocks = HubBlocks.of(anchor);
		if (view != lastView || plan != lastPlan || !blocks.equals(lastBlocks)) {
			lastView = view;
			lastPlan = plan;
			lastBlocks = blocks;
			sync(view, plan, blocks);
			ticks = 0;
		}
		if (++ticks % RETRY_TICKS == 0 || ticks == 1) {
			fillPending(level);
		}
	}

	/** Who sees the doors: the players and the game master; a ghost or a spectator walks through. */
	private static boolean walls(View view) {
		return view.hasGame() && view.phase() != Phase.ENDED
				&& (view.viewer().kind() == ViewerKind.PLAYER || view.viewer().kind() == ViewerKind.GM);
	}

	/** Takes every block of ours away, whatever the state. */
	private static void dropAll() {
		MirrorGhosts.clearLocal(OWNER);
		FILLS.clear();
	}

	/** Brings the kept fills in line with the doors the view says are closed. */
	private static void sync(View view, LayoutDto plan, HubBlocks blocks) {
		Set<String> closed = new HashSet<>();
		for (Door door : plan.doors) {
			if (door.block != null && Boolean.FALSE.equals(view.doorsOpen().get(door.id))) {
				closed.add(door.id);
			}
		}
		for (String id : Set.copyOf(FILLS.keySet())) {
			Fill fill = FILLS.get(id);
			Box now = closed.contains(id) ? world(plan, id, blocks) : null;
			if (now == null || !same(fill.box, now) || fill.door != door(plan, id)) {
				release(fill);
				FILLS.remove(id);
			}
		}
		for (Door door : plan.doors) {
			if (closed.contains(door.id) && !FILLS.containsKey(door.id)) {
				FILLS.put(door.id, new Fill(door, world(door, blocks)));
			}
		}
	}

	private static void release(Fill fill) {
		for (long key : fill.applied) {
			MirrorGhosts.removeLocal(OWNER, BlockPos.of(key));
		}
	}

	private static void fillPending(ClientLevel level) {
		for (Fill fill : FILLS.values()) {
			if (fill.pending.isEmpty()) {
				continue;
			}
			for (Long key : Set.copyOf(fill.pending)) {
				BlockPos pos = BlockPos.of(key);
				if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
					continue; // not loaded: wait for it
				}
				fill.pending.remove(key);
				if (!MirrorGhosts.realState(level, pos).isAir()) {
					continue; // a real block stands there: never replaced
				}
				BlockState state = DoorBlocks.at(fill.door, fill.box, pos.getX(), pos.getY(), pos.getZ());
				MirrorGhosts.putLocal(OWNER, pos, state);
				fill.applied.add(key);
			}
		}
	}

	// ---------------------------------------------------------------- plan to world

	private static Door door(LayoutDto plan, String id) {
		for (Door door : plan.doors) {
			if (door.id.equals(id)) {
				return door;
			}
		}
		return null;
	}

	private static Box world(LayoutDto plan, String id, HubBlocks blocks) {
		Door door = door(plan, id);
		return door == null || door.block == null ? null : world(door, blocks);
	}

	/** A door's box of the plan (hub blocks) as this world's blocks, both corners included. */
	private static Box world(Door door, HubBlocks blocks) {
		Box b = door.block;
		int ax = blocks.worldX(b.minX);
		int bx = blocks.worldX(b.maxX);
		int az = blocks.worldZ(b.minZ);
		int bz = blocks.worldZ(b.maxZ);
		return new Box(Math.min(ax, bx), Math.min(b.minY, b.maxY), Math.min(az, bz), Math.max(ax, bx), Math.max(b.minY, b.maxY), Math.max(az, bz));
	}

	private static boolean same(Box a, Box b) {
		return a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ && a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ;
	}
}
