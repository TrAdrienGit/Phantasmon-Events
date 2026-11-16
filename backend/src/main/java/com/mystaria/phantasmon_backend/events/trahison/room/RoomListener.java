package com.mystaria.phantasmon_backend.events.trahison.room;

/**
 * Told by {@link RoomService} after each change, <b>with the instance locked</b>: build the per-recipient views at once
 * with {@link RoomViews}, be quick, and never call back into {@link RoomService} from another thread. An exception is
 * logged and ignored.
 */
public interface RoomListener {

	/** The room changed (started, reset, a step opened or solved, an item picked up, the exit opened). */
	default void roomChanged(RoomInstance room) {
	}

	/** A puzzle has news (a gesture, a screen opened, a reset). */
	default void puzzleChanged(RoomInstance room, PuzzleUpdate update) {
	}
}
