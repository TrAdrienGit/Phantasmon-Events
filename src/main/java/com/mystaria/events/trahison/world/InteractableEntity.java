package com.mystaria.events.trahison.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The invisible, pickable hitbox of one active slot of a room (solution S3), modelled on {@code EventNpcEntity}: an entity
 * added to this client's level only, with an id far from the server's (negative), that the Minecraft server never hears of. It
 * borrows the {@code marker} type (so the game already has a renderer for it: none) and draws nothing itself; the shapes are
 * drawn by {@link Interactables}. It never moves, cannot be hurt and is clicked through {@code UseEntityCallback}, whose
 * result keeps the click from leaving the client.
 */
public final class InteractableEntity extends Entity {

	private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID = new java.util.concurrent.atomic.AtomicInteger(-4_000_000);

	private final String key;
	private final AABB box;

	/** {@code key}: group and slot id; {@code box}: the slot's box in this world. */
	InteractableEntity(Level level, String key, AABB box) {
		super(EntityType.MARKER, level);
		this.key = key;
		this.box = box;
		// Client-only: an id far from the server's, which count up from 0 (see EventNpcEntity).
		setId(NEXT_ID.getAndDecrement());
		noPhysics = true;
		setNoGravity(true);
		setPos((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
		setBoundingBox(box);
	}

	public String key() {
		return key;
	}

	/** The slot's box in this world (the entity's own box, kept exact whatever {@code setPos} does). */
	public AABB slotBox() {
		return box;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected AABB makeBoundingBox() {
		return box != null ? box : super.makeBoundingBox(); // the Entity constructor asks before our field is set
	}

	@Override
	public void tick() {
		// Stands still: nothing to simulate.
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
	public boolean isInvisible() {
		return true;
	}

	@Override
	public boolean shouldRender(double x, double y, double z) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public net.minecraft.world.entity.EntityDimensions getDimensions(Pose pose) {
		if (box == null) {
			return super.getDimensions(pose);
		}
		return net.minecraft.world.entity.EntityDimensions.fixed((float) Math.max(box.getXsize(), box.getZsize()), (float) box.getYsize());
	}
}
