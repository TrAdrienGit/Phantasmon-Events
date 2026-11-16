package com.mystaria.events.trahison.world;

import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/**
 * The body of a dead player (T6.2), or the mannequin of a disconnected one (T6.4): a player model added to this client's level only, lying down, at the skin of the dead
 * player, where he fell. The Minecraft server never hears of it. It never moves, cannot be hurt, and is pickable so the
 * crosshair can aim at it ({@link Bodies}).
 */
public final class BodyEntity extends RemotePlayer {

	private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID = new java.util.concurrent.atomic.AtomicInteger(-4_000_000);

	private final int player;
	private final Supplier<PlayerSkin> skin;
	private final UUID account;
	/** T6.4: a standing stand-in for a disconnected player, not a body. */
	private final boolean mannequin;

	BodyEntity(ClientLevel level, int player, UUID account, String name, boolean mannequin, Supplier<PlayerSkin> skin) {
		super(level, new GameProfile(UUID.nameUUIDFromBytes(("phantasmon-trahison-body:" + account).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				name.length() <= 16 ? name : name.substring(0, 16)));
		setUUID(getGameProfile().getId());
		setId(NEXT_ID.getAndDecrement()); // client-only, far from the server's ids
		this.player = player;
		this.account = account;
		this.skin = skin;
		this.mannequin = mannequin;
		getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
		setPose(mannequin ? Pose.STANDING : Pose.SLEEPING);
	}

	/** The seat of the dead player. */
	public int player() {
		return player;
	}

	public UUID account() {
		return account;
	}

	void place(double x, double y, double z, float yaw) {
		moveTo(x, y, z, yaw, 0);
		yBodyRot = yaw;
		yHeadRot = yaw;
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public PlayerSkin getSkin() {
		PlayerSkin loaded = skin.get();
		return loaded != null ? loaded : DefaultPlayerSkin.get(account);
	}

	public boolean mannequin() {
		return mannequin;
	}

	/** Keeps its pose (lying for a body, standing for a mannequin): the player pose logic would change it. */
	@Override
	protected void updatePlayerPose() {
		setPose(mannequin ? Pose.STANDING : Pose.SLEEPING);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable(mannequin ? "phantasmon_events.trahison.away.name" : "phantasmon_events.trahison.body.name",
				getGameProfile().getName());
	}

	/** A mannequin carries its mark under the name; a body has no name tag. */
	@Override
	public boolean shouldShowName() {
		return mannequin;
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
		setDeltaMovement(Vec3.ZERO);
	}
}
