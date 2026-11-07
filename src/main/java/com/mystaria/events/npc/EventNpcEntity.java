package com.mystaria.events.npc;

import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;

/**
 * An event's trainer NPC in the world: a player model added to this client's level only (the Minecraft server never
 * hears of it), standing still where it was posed and turning its head to the nearest player. Its entity UUID is the
 * NPC's own: the battle intro and the camera find it by the opponent's UUID.
 */
public final class EventNpcEntity extends RemotePlayer {

	private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID = new java.util.concurrent.atomic.AtomicInteger(-3_000_000);

	private final Supplier<PlayerSkin> skin;
	private EventNpcDto npc;
	/** Where it stands in this world (a hub's NPC: in one of the hub's zones). */
	private double placeX;
	private double placeY;
	private double placeZ;
	private float placeYaw;

	/** {@code entityUuid}: the NPC's own for its first place here (the battle intro finds it by it), else derived. */
	EventNpcEntity(ClientLevel level, EventNpcDto npc, UUID entityUuid, Supplier<PlayerSkin> skin) {
		super(level, new GameProfile(npc.uuid(), shortName(npc.name())));
		setUUID(entityUuid);
		// Client-only: an id far from the server's, which count up from 0. With the same id as one of its mobs, the
		// NPC vanishes when that mob is removed (seen on a freshly started server, whose ids are still low).
		setId(NEXT_ID.getAndDecrement());
		this.skin = skin;
		this.npc = npc;
		getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
	}

	void place(double x, double y, double z, float yaw) {
		boolean first = placeX == 0 && placeY == 0 && placeZ == 0;
		placeX = x;
		placeY = y;
		placeZ = z;
		placeYaw = yaw;
		if (first) {
			moveTo(x, y, z, yaw, 0);
			yHeadRot = yaw;
			yBodyRot = yaw;
		}
	}

	/** A game profile's name is 16 characters at most. */
	private static String shortName(String name) {
		return name.length() <= 16 ? name : name.substring(0, 16);
	}

	EventNpcDto npc() {
		return npc;
	}

	void update(EventNpcDto updated) {
		npc = updated;
	}

	@Override
	public PlayerSkin getSkin() {
		PlayerSkin loaded = skin.get();
		return loaded != null ? loaded : DefaultPlayerSkin.get(npc.uuid());
	}

	/** Its full name; a closed event's NPC (seen by admins only) is greyed and tagged. */
	@Override
	public Component getDisplayName() {
		return npc.open() ? Component.literal(npc.name())
				: Component.literal(npc.name() + " ").append(Component.translatable("phantasmon_events.npc.closed_tag"))
						.withStyle(net.minecraft.ChatFormatting.GRAY);
	}

	@Override
	public boolean shouldShowName() {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		// Stands still where it was posed.
		setPos(placeX, placeY, placeZ);
		setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		// Looks at the nearest player within 10 blocks and follows them with its eyes — turning slowly, eased, never
		// snapping round (Adrien 2026-11-02); with nobody near, slowly back to where it was posed facing.
		net.minecraft.world.entity.player.Player near = null;
		double best = LOOK_RANGE * LOOK_RANGE;
		for (net.minecraft.world.entity.player.Player player : level().players()) {
			if (player != this && !(player instanceof EventNpcEntity) && !player.isSpectator()) {
				double distance = player.distanceToSqr(this);
				if (distance < best) {
					best = distance;
					near = player;
				}
			}
		}
		float wantHead = placeYaw;
		float wantPitch = 0;
		if (near != null) {
			double dx = near.getX() - getX();
			double dz = near.getZ() - getZ();
			double dy = near.getEyeY() - getEyeY();
			wantHead = (float) Math.toDegrees(Math.atan2(-dx, dz));
			wantPitch = net.minecraft.util.Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -35f, 35f);
		}
		// The body only turns when the head would have to turn more than 45° from it.
		float wantBody = yBodyRot;
		float offset = net.minecraft.util.Mth.wrapDegrees(wantHead - yBodyRot);
		if (Math.abs(offset) > 45) {
			wantBody = wantHead - Math.signum(offset) * 45;
		} else if (near == null) {
			wantBody = placeYaw;
		}
		yRotO = getYRot();
		yBodyRotO = yBodyRot;
		yHeadRotO = yHeadRot;
		xRotO = getXRot();
		yHeadRot = ease(yHeadRot, wantHead, 0.12f, 7f);
		yBodyRot = ease(yBodyRot, wantBody, 0.06f, 3.5f);
		setYRot(yBodyRot);
		setXRot(getXRot() + net.minecraft.util.Mth.clamp((wantPitch - getXRot()) * 0.12f, -4f, 4f));
	}

	private static final double LOOK_RANGE = 10;

	/** A step of {@code rate} of the way to {@code target} (an angle), at most {@code max} degrees: slow in, slow out. */
	private static float ease(float from, float target, float rate, float max) {
		float delta = net.minecraft.util.Mth.wrapDegrees(target - from);
		return from + net.minecraft.util.Mth.clamp(delta * rate, -max, max);
	}
}
