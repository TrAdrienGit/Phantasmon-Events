package com.mystaria.events.trahison.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mystaria.events.trahison.minigame.PuzzleScreens;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.room.RoomView;
import com.mystaria.events.trahison.room.RoomView.Partner;
import com.mystaria.events.trahison.room.Rooms;
import com.mystaria.phantasmon.client.ghost.PhantasmonEntities;
import com.mystaria.phantasmon.client.hub.HubAvatars;

/**
 * The partner Pokemon of the group (T8.3, solution S8): for every living member of the local player's room, a Cobblemon entity made by this
 * client only (never sent to the server, never one of the player's own Ghosts) that follows its trainer, so everyone sees everyone's partner. The species
 * comes from the room message ({@code partners}); a ghost gets the Ghost-type partner the message names ({@code ghost_partner}) and no
 * one else sees it. When the species cannot be built (Cobblemon changed, unknown id) nothing is drawn for it and the game goes on: the power
 * is the backend's, not the model's. Modelled on Phantasmon's {@code GhostEntityManager} (read, not called, but for {@link PhantasmonEntities}
 * which keeps the entity out of the player's reach). Client thread.
 */
public final class PartnerEntities {

	/** Far from the server's entity ids and from the Ghosts' and the bodies' ones. */
	private static final AtomicInteger NEXT_ID = new AtomicInteger(-6_000_000);
	private static final String GHOST_KEY = "ghost";
	private static final double BEHIND = 1.2;
	private static final double SIDE = 0.9;
	private static final double SPEED = 0.22;
	private static final double TELEPORT = 12.0;
	private static final double MOVING_DISTANCE = 0.12;

	/** One partner standing in the world. */
	private static final class Standing {
		final PokemonEntity entity;
		final String species;
		final UUID owner;
		boolean moving;

		Standing(PokemonEntity entity, String species, UUID owner) {
			this.entity = entity;
			this.species = species;
			this.owner = owner;
		}
	}

	private static final Map<String, Standing> STANDING = new HashMap<>();
	private static final Set<String> BROKEN = new HashSet<>();
	private static ClientLevel level;

	private PartnerEntities() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PartnerEntities::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.level != level) {
			STANDING.clear(); // the old world took its entities with it
			level = mc.level;
		}
		if (level == null || mc.player == null) {
			return;
		}
		Map<String, String[]> wanted = wanted(mc); // key -> {species, owner uuid}
		for (String key : Set.copyOf(STANDING.keySet())) {
			String[] w = wanted.get(key);
			Standing s = STANDING.get(key);
			if (w == null || !w[0].equals(s.species) || !w[1].equals(s.owner.toString()) || s.entity.isRemoved()) {
				remove(key);
			}
		}
		for (Map.Entry<String, String[]> e : wanted.entrySet()) {
			if (!STANDING.containsKey(e.getKey()) && !BROKEN.contains(e.getValue()[0])) {
				spawn(e.getKey(), e.getValue()[0], UUID.fromString(e.getValue()[1]));
			}
		}
		for (Standing s : STANDING.values()) {
			follow(s);
		}
	}

	/** Who should have a partner now: the living members of the player's room, or the ghost himself. */
	private static Map<String, String[]> wanted(Minecraft mc) {
		Map<String, String[]> out = new HashMap<>();
		View view = GameView.current();
		if (view == null || !view.hasGame() || view.phase() == GameView.Phase.ENDED || view.phase() == GameView.Phase.LOBBY) {
			return out;
		}
		if (view.viewer().kind() == ViewerKind.GHOST) {
			String species = "gastly";
			for (RoomView room : Rooms.all().values()) {
				species = room.ghostPartner();
			}
			out.put(GHOST_KEY, new String[] { species, mc.player.getUUID().toString() });
			return out;
		}
		RoomView room = Rooms.mine();
		if (room == null || view.viewer().kind() != ViewerKind.PLAYER) {
			return out;
		}
		for (int member : room.members()) {
			Partner p = room.partnerOf(member);
			GameView.Player who = view.players().stream().filter(x -> x.index() == member).findFirst().orElse(null);
			PuzzleScreens.Player named = PuzzleScreens.player(member);
			UUID uuid = named == null ? null : named.uuid();
			if (p != null && who != null && who.alive() && !who.bot() && who.connected() && uuid != null) {
				out.put("p" + member, new String[] { p.species(), uuid.toString() });
			}
		}
		return out;
	}

	private static void spawn(String key, String speciesId, UUID owner) {
		Player trainer = HubAvatars.playerOrAvatar(level, owner);
		if (trainer == null) {
			return; // not in this world yet: tried again next tick
		}
		try {
			Species species = PokemonSpecies.INSTANCE.getByName(speciesId);
			if (species == null) {
				BROKEN.add(speciesId);
				return;
			}
			Pokemon pokemon = new Pokemon();
			pokemon.setSpecies(species);
			pokemon.setLevel(10);
			pokemon.updateAspects();
			PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
			entity.setId(NEXT_ID.getAndDecrement());
			entity.setNoAi(true);
			entity.setInvulnerable(true);
			entity.noPhysics = true;
			Vec3 spot = spotOf(trainer);
			entity.setPos(spot.x, spot.y, spot.z);
			entity.setYRot(trainer.getYRot());
			entity.setYBodyRot(trainer.getYRot());
			entity.setYHeadRot(trainer.getYRot());
			entity.getEntityData().set(PokemonEntity.Companion.getASPECTS(), new HashSet<>(pokemon.getAspects()));
			entity.getEntityData().set(PokemonEntity.Companion.getLABEL_LEVEL(), pokemon.getLevel());
			entity.getEntityData().set(PokemonEntity.getSPAWN_DIRECTION(), trainer.getYRot());
			level.addEntity(entity);
			PhantasmonEntities.register(entity);
			STANDING.put(key, new Standing(entity, speciesId, owner));
		} catch (RuntimeException | LinkageError e) {
			BROKEN.add(speciesId); // Cobblemon's API changed: no partner model, the game itself is unaffected
		}
	}

	private static void remove(String key) {
		Standing s = STANDING.remove(key);
		if (s != null && level != null) {
			level.removeEntity(s.entity.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** Where the partner stands: behind and to the right of its trainer. */
	private static Vec3 spotOf(Player trainer) {
		double yaw = Math.toRadians(trainer.getYRot());
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		double rightX = -Math.cos(yaw);
		double rightZ = -Math.sin(yaw);
		return new Vec3(trainer.getX() + -forwardX * BEHIND + rightX * SIDE, trainer.getY(), trainer.getZ() + -forwardZ * BEHIND + rightZ * SIDE);
	}

	private static void follow(Standing s) {
		Player trainer = HubAvatars.playerOrAvatar(level, s.owner);
		if (trainer == null || s.entity.isRemoved()) {
			return;
		}
		Vec3 spot = spotOf(trainer);
		Vec3 at = s.entity.position();
		double dx = spot.x - at.x;
		double dz = spot.z - at.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance > TELEPORT) {
			s.entity.setPos(spot.x, spot.y, spot.z);
			s.moving = false;
		} else {
			s.moving = s.moving ? distance > MOVING_DISTANCE / 2 : distance > MOVING_DISTANCE;
			double step = s.moving ? Math.min(distance, Math.max(0.05, distance * SPEED)) : 0;
			double nx = distance > 1.0e-4 ? at.x + dx / distance * step : at.x;
			double nz = distance > 1.0e-4 ? at.z + dz / distance * step : at.z;
			s.entity.setPos(nx, Mth.lerp(0.35, at.y, spot.y), nz);
			if (s.moving) {
				float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
				s.entity.setYRot(yaw);
				s.entity.setYBodyRot(yaw);
				s.entity.setYHeadRot(yaw);
			}
		}
		s.entity.setDeltaMovement(Vec3.ZERO);
		s.entity.getEntityData().set(PokemonEntity.Companion.getMOVING(), s.moving);
		s.entity.getEntityData().set(PokemonEntity.Companion.getPOSE_TYPE(), s.moving ? PoseType.WALK : PoseType.STAND);
	}

	/** Takes every partner away at once (left the game, new world). */
	public static void clear() {
		for (String key : Set.copyOf(STANDING.keySet())) {
			remove(key);
		}
	}
}
