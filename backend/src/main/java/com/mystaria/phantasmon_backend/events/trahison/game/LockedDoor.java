package com.mystaria.phantasmon_backend.events.trahison.game;

/**
 * A door of the plan closed by the lock item (T7.2): who locked it, and when it opens by itself (epoch ms). A cabin is locked
 * through its door. Kept with the game.
 */
public record LockedDoor(String door, int by, long untilMs) {
}
