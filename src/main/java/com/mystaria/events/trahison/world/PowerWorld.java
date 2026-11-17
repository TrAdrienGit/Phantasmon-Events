package com.mystaria.events.trahison.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.mystaria.events.hub.MirrorGhosts;
import com.mystaria.events.trahison.hud.Announcer;
import com.mystaria.events.trahison.minigame.MiniGameSounds;
import com.mystaria.events.trahison.minigame.PuzzleScreens;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.room.Powers;
import com.mystaria.events.trahison.room.RoomView;
import com.mystaria.events.trahison.room.RoomView.PowerStep;
import com.mystaria.events.trahison.room.Rooms;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;
import com.mystaria.phantasmon.client.hub.HubAvatars;

/**
 * What the {@code power_spot} steps do in the world (T8.3). The backend decides who may act and how far each step got; this class only
 * shows it, from the room messages, and never invents a state:
 * <ul>
 * <li>{@code STRENGTH}: a boulder of fake blocks at the start of the slot's long side, one cell further for each push done;</li>
 * <li>{@code CUT}: the slot's whole box filled with fake leaves until the step is solved;</li>
 * <li>{@code SURF}: the slot's box walled with invisible barriers for everybody except the trainers the backend lets act: the water is
 * crossed by them only;</li>
 * <li>{@code FLASH}: no block, a dark veil ({@link #veil}) drawn by the HUD while a player stands in the zone out of the bearer's light.</li>
 * </ul>
 * Blocks are client-only through {@link MirrorGhosts#putLocal} (owner {@value #OWNER}), like the doors: only air is filled, a chunk that is not
 * loaded waits. Players and the game master see them; ghosts and spectators walk through. Client thread.
 */
public final class PowerWorld {

	public static final String OWNER = "trahison_power";
	private static final int RETRY_TICKS = 10;
	private static final int FADE_BLOCKS = 2;
	private static final int VEIL_ALPHA = 0xD0;
	/** How far around the zone the dark reaches, horizontally and up. */
	private static final int ZONE_MARGIN = 2;
	private static final int ZONE_HEIGHT = 3;

	private static final BlockState BOULDER = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
	private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

	/** The blocks we want, and the ones that already stand, by position. */
	private static Map<Long, BlockState> wanted = new HashMap<>();
	private static final Map<Long, BlockState> APPLIED = new HashMap<>();
	private static ClientLevel lastLevel;
	private static int lastRevision = -1;
	private static HubBlocks lastBlocks;
	private static int ticks;

	private PowerWorld() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PowerWorld::tick);
	}

	// ---------------------------------------------------------------- messages

	/** Feedback of a {@code power_spot} message: a refusal to the one who asked, a sound for a push or an unlock. */
	public static void onMessage(PuzzleScreens.Msg m) {
		int own = Rooms.ownIndex();
		for (Map<String, Object> e : m.lastEvents()) {
			switch (PuzzleScreens.asString(e.get("type"), "")) {
				case "denied" -> {
					if (m.lastPlayer() == own) {
						Announcer.error(Component.translatable("phantasmon_events.trahison.power.denied", Powers.name(PuzzleScreens.asString(e.get("power"), ""))));
						MiniGameSounds.error();
					}
				}
				case "push" -> MiniGameSounds.clunk();
				case "unlock" -> MiniGameSounds.success();
				default -> {
				}
			}
		}
	}

	// ---------------------------------------------------------------- geometry

	/** The world box of a spot (hub-relative frame of its room, then the plan's place and the anchor), or null when the room has no place. */
	static AABB boxOf(RoomView room, RoomView.Spot spot, HubBlocks blocks) {
		RoomView.Place place = room.place();
		if (place == null || spot == null) {
			return null;
		}
		double[] b = RoomFrame.box(place.x(), place.y(), place.z(), RoomFrame.quarterTurns(place.orientation()), spot.x(), spot.y(), spot.z(), spot.sx(), spot.sy(), spot.sz());
		return new AABB(blocks.originX() + b[0], b[1], blocks.originZ() + b[2], blocks.originX() + b[3], b[4], blocks.originZ() + b[5]);
	}

	private static HubBlocks currentBlocks() {
		HubAnchorDto anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		return anchor != null && anchor.hub().equals(Link.hub()) ? HubBlocks.of(anchor) : null;
	}

	// ---------------------------------------------------------------- the flash veil

	/**
	 * The alpha (0..255) of the dark veil the local player is under: 0 outside every dark zone, when he is the bearer himself, when the
	 * step is done or when nobody holds the power (the group is never left in the dark), growing from the edge of the bearer's light
	 * to full darkness {@value #FADE_BLOCKS} blocks further.
	 */
	public static int veil() {
		Minecraft mc = Minecraft.getInstance();
		View view = GameView.current();
		int own = Rooms.ownIndex();
		HubBlocks blocks = currentBlocks();
		RoomView room = Rooms.current();
		if (mc.player == null || mc.level == null || view == null || !view.hasGame() || blocks == null || room == null || own < 0) {
			return 0;
		}
		int best = 0;
		for (PowerStep step : room.powers()) {
			if (!"FLASH".equals(step.power()) || !step.open() || step.solved() || step.anyone() || step.mayAct(own)) {
				continue;
			}
			AABB zone = boxOf(room, step.spot(), blocks);
			if (zone == null || !zone.inflate(ZONE_MARGIN, 0, ZONE_MARGIN).expandTowards(0, ZONE_HEIGHT, 0).contains(mc.player.position())) {
				continue;
			}
			double nearest = Double.MAX_VALUE;
			for (int holder : step.holders()) {
				PuzzleScreens.Player p = PuzzleScreens.player(holder);
				UUID uuid = p == null ? null : p.uuid();
				Player bearer = uuid == null ? null : HubAvatars.playerOrAvatar(mc.level, uuid);
				if (bearer != null) {
					nearest = Math.min(nearest, bearer.distanceTo(mc.player));
				}
			}
			double over = nearest - step.radius();
			int alpha = over <= 0 ? 0 : (int) Math.min(VEIL_ALPHA, VEIL_ALPHA * over / FADE_BLOCKS);
			best = Math.max(best, alpha);
		}
		return best;
	}

	// ---------------------------------------------------------------- blocks

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			APPLIED.clear();
			wanted = new HashMap<>();
			lastLevel = level;
			lastRevision = -1;
		}
		if (level == null) {
			return;
		}
		View view = GameView.current();
		HubBlocks blocks = currentBlocks();
		boolean show = blocks != null && view != null && view.hasGame() && view.phase() != GameView.Phase.ENDED && view.viewer().kind() == ViewerKind.PLAYER
				&& !Rooms.all().isEmpty();
		if (!show) {
			if (!APPLIED.isEmpty() || !wanted.isEmpty()) {
				MirrorGhosts.clearLocal(OWNER);
				APPLIED.clear();
				wanted = new HashMap<>();
			}
			lastRevision = -1;
			return;
		}
		if (Rooms.revision() != lastRevision || !blocks.equals(lastBlocks)) {
			lastRevision = Rooms.revision();
			lastBlocks = blocks;
			wanted = compute(blocks);
			ticks = 0;
		}
		if (++ticks % RETRY_TICKS == 1) {
			apply(level);
		}
	}

	/** Every block the rooms' power steps want standing now. */
	private static Map<Long, BlockState> compute(HubBlocks blocks) {
		Map<Long, BlockState> out = new HashMap<>();
		int own = Rooms.ownIndex();
		for (RoomView room : Rooms.all().values()) {
			if (!Rooms.currentRound(room)) {
				continue;
			}
			for (PowerStep step : room.powers()) {
				AABB box = boxOf(room, step.spot(), blocks);
				if (box == null) {
					continue;
				}
				int x0 = (int) Math.floor(box.minX + 1.0e-6);
				int y0 = (int) Math.floor(box.minY + 1.0e-6);
				int z0 = (int) Math.floor(box.minZ + 1.0e-6);
				int x1 = (int) Math.ceil(box.maxX - 1.0e-6) - 1;
				int y1 = (int) Math.ceil(box.maxY - 1.0e-6) - 1;
				int z1 = (int) Math.ceil(box.maxZ - 1.0e-6) - 1;
				switch (step.power()) {
					case "STRENGTH" -> {
						boolean alongX = x1 - x0 >= z1 - z0;
						int cells = alongX ? x1 - x0 : z1 - z0;
						int at = Math.min(cells, step.done());
						out.put(BlockPos.asLong(alongX ? x0 + at : x0, y0, alongX ? z0 : z0 + at), BOULDER);
					}
					case "CUT" -> {
						if (!step.solved()) {
							fill(out, x0, y0, z0, x1, y1, z1, LEAVES);
						}
					}
					case "SURF" -> {
						if (!step.mayAct(own)) {
							fill(out, x0, y0, z0, x1, y1, z1, BARRIER);
						}
					}
					default -> {
					}
				}
			}
		}
		return out;
	}

	private static void fill(Map<Long, BlockState> out, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					out.put(BlockPos.asLong(x, y, z), state);
				}
			}
		}
	}

	/** Brings the blocks that stand in line with {@link #wanted}: takes away what is not wanted, puts what is (air only, loaded chunks only). */
	private static void apply(ClientLevel level) {
		for (Long key : Set.copyOf(APPLIED.keySet())) {
			if (!wanted.containsKey(key) || wanted.get(key) != APPLIED.get(key)) {
				MirrorGhosts.removeLocal(OWNER, BlockPos.of(key));
				APPLIED.remove(key);
			}
		}
		Set<Long> todo = new HashSet<>(wanted.keySet());
		todo.removeAll(APPLIED.keySet());
		for (Long key : todo) {
			BlockPos pos = BlockPos.of(key);
			if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
				continue; // not loaded: wait for it
			}
			if (!MirrorGhosts.realState(level, pos).isAir()) {
				continue; // a real block stands there: never replaced
			}
			MirrorGhosts.putLocal(OWNER, pos, wanted.get(key));
			APPLIED.put(key, wanted.get(key));
		}
	}
}
