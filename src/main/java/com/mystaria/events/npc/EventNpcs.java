package com.mystaria.events.npc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * The events' trainer NPCs of the current server and dimension (Adrien 2026-11-02), kept as client-only entities:
 * listed again on joining, on changing dimension, every 30 s and whenever the backend says they changed
 * ({@code EventsNpcsChanged}). Players see those of open events; admins all of them (closed ones greyed). A click
 * (use) on one opens the editor for an admin, the challenge for a player. Client thread.
 */
public final class EventNpcs {

	private static final Logger LOG = LoggerFactory.getLogger(EventNpcs.class);
	private static final int REFRESH_TICKS = 600;

	/** Shown NPCs by place: {@code <npc uuid>} for a world NPC, {@code <npc uuid>@<anchor uuid>} for a hub's. */
	private static final Map<String, EventNpcEntity> SHOWN = new HashMap<>();
	/** The last list, placed again when this world's hub anchors change. */
	private static EventNpcDto[] last = new EventNpcDto[0];
	/** Skins by account name, kept for the session; an empty Optional while loading or if there is none. */
	private static final Map<String, Optional<PlayerSkin>> SKINS = new ConcurrentHashMap<>();
	private static ClientLevel level;
	private static int ticks;
	private static boolean refreshing;

	private EventNpcs() {
	}

	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if (type.equals("EventsNpcsChanged")) {
				refresh();
			}
		});
		// Cobblemon's wheel (hold R), as on a player (Adrien 2026-11-02): battle it with the Ghost team or the
		// Cobblemon party; admins also edit it.
		PhantasmonApi.registerWheelTarget(entity -> entity instanceof EventNpcEntity, entity -> {
			EventNpcDto dto = ((EventNpcEntity) entity).npc();
			List<com.mystaria.phantasmon.client.wheel.GhostWheelOptions.Entry> entries = new java.util.ArrayList<>();
			entries.add(new com.mystaria.phantasmon.client.wheel.GhostWheelOptions.Entry("NORTHWEST", "battle", "phantasmon_events.npc.with_ghost", 0xFF5078,
					() -> battle(dto, false)));
			entries.add(new com.mystaria.phantasmon.client.wheel.GhostWheelOptions.Entry("NORTHEAST", "battle", "phantasmon_events.npc.with_cobblemon", 0xFFB040,
					() -> battle(dto, true)));
			if (PhantasmonApi.isAdmin()) {
				entries.add(new com.mystaria.phantasmon.client.wheel.GhostWheelOptions.Entry("EAST", "held_item", "phantasmon_events.npc.edit", 0x80D0FF,
						() -> Minecraft.getInstance().setScreen(new NpcEditScreen(dto))));
			}
			com.mystaria.phantasmon.client.wheel.GhostWheelOptions.openCustom(net.minecraft.network.chat.Component.literal(dto.name()), entries);
		});
		// A right click: the editor for an admin; never sent to the Minecraft server, which knows nothing of it.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() || !(entity instanceof EventNpcEntity npc)) {
				return InteractionResult.PASS;
			}
			if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && PhantasmonApi.isAdmin()) {
				Minecraft mc = Minecraft.getInstance();
				mc.tell(() -> mc.setScreen(new NpcEditScreen(npc.npc())));
			}
			return InteractionResult.FAIL;
		});
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != level) {
			clear();
			level = mc.level;
			ticks = REFRESH_TICKS - 40; // soon after joining
		}
		if (level == null || !PhantasmonApi.isAuthenticated()) {
			return;
		}
		if (++ticks >= REFRESH_TICKS) {
			refresh();
		} else if (ticks % 40 == 0) {
			show(last); // anchors listed meanwhile: hub NPCs take their places
		}
	}

	public static void refresh() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || refreshing || !PhantasmonApi.isAuthenticated()) {
			return;
		}
		ticks = 0;
		refreshing = true;
		String server = PhantasmonApi.serverFingerprint();
		String dimension = mc.level.dimension().location().toString();
		ClientLevel asked = mc.level;
		PhantasmonApi.get("/events/npcs?server=" + encode(server) + "&dimension=" + encode(dimension), EventNpcDto[].class)
				.whenComplete((npcs, error) -> mc.execute(() -> {
					refreshing = false;
					if (error != null) {
						LOG.debug("Event NPCs not listed", error);
					} else if (asked == mc.level) {
						show(npcs);
					}
				}));
	}

	/** A place of an NPC in this world: {@code key} in {@link #SHOWN}, position and facing. */
	private record Place(String key, EventNpcDto npc, double x, double y, double z, float yaw) {
	}

	/**
	 * Each NPC's places here: a world NPC where it was posed; a hub's NPC (Adrien 2026-11-03) in each of that hub's
	 * zones in this world — source, copy or ordinary anchor — from its hub coordinates.
	 */
	private static List<Place> places(EventNpcDto[] npcs) {
		List<Place> places = new java.util.ArrayList<>();
		List<com.mystaria.phantasmon.client.hub.HubAnchorDto> anchors = PhantasmonApi.anchorsHere();
		for (EventNpcDto npc : npcs) {
			if (npc.hub() == null) {
				places.add(new Place(npc.uuid().toString(), npc, npc.x(), npc.y(), npc.z(), npc.yaw()));
				continue;
			}
			for (com.mystaria.phantasmon.client.hub.HubAnchorDto anchor : anchors == null ? List.<com.mystaria.phantasmon.client.hub.HubAnchorDto>of() : anchors) {
				if (!anchor.hub().equalsIgnoreCase(npc.hub())) {
					continue;
				}
				com.mystaria.phantasmon.client.hub.HubCoordinates hub = anchor.coordinates();
				double[] local = hub.toLocal(npc.x(), npc.z());
				places.add(new Place(npc.uuid() + "@" + anchor.uuid(), npc, local[0], hub.originY() + npc.y(), local[1],
						hub.toLocalYaw(npc.yaw())));
			}
		}
		return places;
	}

	private static void show(EventNpcDto[] npcs) {
		last = npcs;
		if (level == null) {
			return;
		}
		Map<String, Place> wanted = new HashMap<>();
		for (Place place : places(npcs)) {
			wanted.put(place.key(), place);
		}
		SHOWN.entrySet().removeIf(entry -> {
			Place now = wanted.get(entry.getKey());
			EventNpcEntity entity = entry.getValue();
			// Gone, or renamed / reskinned (the profile and skin are fixed at creation).
			if (now == null || !now.npc().name().equals(entity.npc().name())
					|| !java.util.Objects.equals(now.npc().skin(), entity.npc().skin())) {
				entity.discard();
				return true;
			}
			entity.update(now.npc());
			entity.place(now.x(), now.y(), now.z(), now.yaw());
			return false;
		});
		Set<UUID> ownUuidTaken = new java.util.HashSet<>();
		SHOWN.values().forEach(entity -> ownUuidTaken.add(entity.getUUID()));
		for (Place place : wanted.values()) {
			if (SHOWN.containsKey(place.key())) {
				continue;
			}
			EventNpcDto npc = place.npc();
			// The NPC's own UUID for one of its places (the battle intro finds it by it), a derived one for the others.
			UUID entityUuid = ownUuidTaken.add(npc.uuid()) ? npc.uuid()
					: UUID.nameUUIDFromBytes(("phantasmon-event-npc:" + place.key()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			String skinName = npc.skin() != null ? npc.skin() : npc.name().replace(' ', '_');
			EventNpcEntity entity = new EventNpcEntity(level, npc, entityUuid, () -> skin(skinName));
			entity.place(place.x(), place.y(), place.z(), place.yaw());
			level.addEntity(entity);
			SHOWN.put(place.key(), entity);
		}
	}

	private static void battle(EventNpcDto npc, boolean cobblemon) {
		if (!PhantasmonApi.startNpcBattle(npc.uuid(), cobblemon) && Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"phantasmon_events.npc.no_party").withStyle(net.minecraft.ChatFormatting.RED), false);
		}
	}

	/** The skin of a Minecraft account, by name: fetched once from Mojang; null meanwhile (default skin). */
	private static PlayerSkin skin(String name) {
		Optional<PlayerSkin> known = SKINS.get(name);
		if (known != null) {
			return known.orElse(null);
		}
		SKINS.put(name, Optional.empty());
		SkullBlockEntity.fetchGameProfile(name).thenCompose(profile -> profile.isPresent()
				? Minecraft.getInstance().getSkinManager().getOrLoad(profile.get())
				: java.util.concurrent.CompletableFuture.<PlayerSkin>completedFuture(null))
				.whenComplete((skin, error) -> {
					if (skin != null) {
						SKINS.put(name, Optional.of(skin));
					}
				});
		return null;
	}

	public static void clear() {
		for (Entity entity : SHOWN.values()) {
			entity.discard();
		}
		SHOWN.clear();
		last = new EventNpcDto[0];
	}

	/** The NPC entity nearest the player within {@code range}, or null. */
	static EventNpcEntity nearest(double range) {
		Minecraft mc = Minecraft.getInstance();
		EventNpcEntity best = null;
		double bestDistance = range * range;
		for (EventNpcEntity entity : SHOWN.values()) {
			double distance = mc.player == null ? Double.MAX_VALUE : entity.distanceToSqr(mc.player);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = entity;
			}
		}
		return best;
	}

	private static String encode(String value) {
		return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
	}
}
