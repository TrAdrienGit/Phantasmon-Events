package com.mystaria.events.trahison.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Body;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.hub.HubAnchorDto;
import com.mystaria.phantasmon.client.hub.HubAvatars;

/**
 * The bodies of the dead in the world (T6.2). The backend lists, in every view, only the bodies this client may know of
 * (announced, or already seen): each one becomes a lying player model ({@link BodyEntity}) at the place the backend gave
 * (hub coordinates, converted like {@link Zones} does). Nothing here decides anything:
 * <ul>
 *   <li>seeing a body (same zone, under 8 blocks, a clear line of sight) sends {@code body_seen} once, and the backend then
 *   reveals the death to this player;</li>
 *   <li>a right click on a body whose bracelet is still on it sends {@code take_bracelet}; the backend checks the 3 blocks and
 *   the zone, and the next view shows the body without its bracelet.</li>
 *   <li>T6.4: a player whose connection dropped is shown as a standing mannequin, marked, where he stood.</li>
 * </ul>
 * Client thread.
 */
public final class Bodies {

	/** Blocks within which the client reports a body as seen (the backend checks it again). */
	public static final double SEE_RANGE = 8.0;
	private static final int CHECK_TICKS = 10;

	private static final Map<Integer, BodyEntity> SHOWN = new HashMap<>();
	/** T6.4: the standing mannequins of the disconnected players, by seat. */
	private static final Map<Integer, BodyEntity> AWAY = new HashMap<>();
	/** The seats whose body this client already reported (cleared with the game). */
	private static final Set<Integer> REPORTED = new HashSet<>();
	private static String gameId = "";
	private static ClientLevel level;
	private static int ticks;

	private Bodies() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Bodies::tick);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() || !(entity instanceof BodyEntity body)) {
				return InteractionResult.PASS;
			}
			if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && !body.mannequin() && canTake(body)) {
				Messages.takeBracelet(body.player());
			}
			return InteractionResult.FAIL; // a body is never a real entity of the server
		});
	}

	/** The body the crosshair aims at (within reach), or null. */
	public static BodyEntity aimed() {
		Minecraft mc = Minecraft.getInstance();
		Entity target = mc.crosshairPickEntity;
		return target instanceof BodyEntity body ? body : null;
	}

	/** Whether the player may be offered to take the bracelet of this body: a living player, the bracelet still on it. */
	public static boolean canTake(BodyEntity body) {
		if (body.mannequin()) {
			return false;
		}
		View v = GameView.current();
		if (v == null || !v.hasGame() || v.viewer().kind() != ViewerKind.PLAYER || v.paused()) {
			return false;
		}
		for (Body b : GameView.bodies()) {
			if (b.player() == body.player()) {
				return b.braceletOnBody();
			}
		}
		return false;
	}

	private static void tick(Minecraft mc) {
		View v = GameView.current();
		HubAnchorDto anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		if (mc.level != level) {
			clear();
			level = mc.level;
		}
		if (level == null || mc.player == null || v == null || !v.hasGame() || anchor == null || !anchor.hub().equals(Link.hub())
				|| v.phase() == GameView.Phase.LOBBY) {
			if (!SHOWN.isEmpty()) {
				clear();
			}
			return;
		}
		if (!v.gameId().equals(gameId)) {
			clear();
			gameId = v.gameId();
		}
		HubBlocks blocks = HubBlocks.of(anchor);
		Set<Integer> wanted = new HashSet<>();
		for (Body b : GameView.bodies()) {
			wanted.add(b.player());
			BodyEntity entity = SHOWN.get(b.player());
			if (entity == null) {
				GameView.Player dead = v.player(b.player());
				if (dead == null) {
					continue;
				}
				java.util.UUID account = dead.uuid();
				entity = new BodyEntity(level, b.player(), account, dead.name(), false, () -> HubAvatars.skinOf(account));
				entity.place(blocks.originX() + b.x(), b.y(), blocks.originZ() + b.z(), (b.player() * 47) % 360);
				level.addEntity(entity);
				SHOWN.put(b.player(), entity);
			}
		}
		SHOWN.entrySet().removeIf(e -> {
			if (wanted.contains(e.getKey())) {
				return false;
			}
			e.getValue().discard();
			return true;
		});
		showAway(v, blocks);
		if (++ticks % CHECK_TICKS == 0 && v.viewer().kind() == ViewerKind.PLAYER && !v.paused()) {
			reportSeen(mc);
		}
	}

	/** T6.4: a standing mannequin, marked as disconnected, where each dropped player stood; gone when he is back. */
	private static void showAway(View v, HubBlocks blocks) {
		Set<Integer> wanted = new HashSet<>();
		for (GameView.Away a : GameView.away()) {
			wanted.add(a.player());
			if (AWAY.containsKey(a.player())) {
				continue;
			}
			GameView.Player who = v.player(a.player());
			if (who == null) {
				continue;
			}
			java.util.UUID account = who.uuid();
			BodyEntity entity = new BodyEntity(level, a.player(), account, who.name(), true, () -> HubAvatars.skinOf(account));
			entity.place(blocks.originX() + a.x(), a.y(), blocks.originZ() + a.z(), (a.player() * 47) % 360);
			level.addEntity(entity);
			AWAY.put(a.player(), entity);
		}
		AWAY.entrySet().removeIf(e -> {
			if (wanted.contains(e.getKey())) {
				return false;
			}
			e.getValue().discard();
			return true;
		});
	}

	/** Reports once each body in view: close enough and not behind a wall. */
	private static void reportSeen(Minecraft mc) {
		Vec3 eye = mc.player.getEyePosition();
		for (BodyEntity entity : SHOWN.values()) {
			if (REPORTED.contains(entity.player())) {
				continue;
			}
			Vec3 at = entity.position().add(0, 0.2, 0);
			if (eye.distanceTo(at) > SEE_RANGE) {
				continue;
			}
			HitResult clip = level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
			if (clip.getType() == HitResult.Type.MISS) {
				REPORTED.add(entity.player());
				Messages.bodySeen(entity.player());
			}
		}
	}

	private static void clear() {
		SHOWN.values().forEach(Entity::discard);
		SHOWN.clear();
		AWAY.values().forEach(Entity::discard);
		AWAY.clear();
		REPORTED.clear();
		gameId = "";
	}
}
