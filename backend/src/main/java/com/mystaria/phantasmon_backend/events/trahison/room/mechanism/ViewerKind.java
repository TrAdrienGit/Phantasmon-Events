package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

/** Who a view is built for. Only the game master may ever see a solution. */
public enum ViewerKind {
	/** A member of the group playing the room. */
	PLAYER,
	/** A ghost or a spectator watching the room. */
	SPECTATOR,
	/** The game master. */
	GAME_MASTER
}
