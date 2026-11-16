package com.mystaria.phantasmon_backend.events.trahison.room;

import java.time.Instant;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Mechanism;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismState;

/** The running state of one step of a {@link RoomInstance}. Written only by {@link RoomService}. */
public final class StepRuntime {
	public final Step step;
	public final Mechanism mechanism;
	public MechanismState state;
	/** Its {@code needs} are all solved. */
	public boolean open;
	public boolean solved;
	public Instant openedAt;
	public Instant solvedAt;
	public int hintsGiven;

	StepRuntime(Step step, Mechanism mechanism, MechanismState state) {
		this.step = step;
		this.mechanism = mechanism;
		this.state = state;
	}
}
