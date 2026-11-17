package com.mystaria.phantasmon_backend.events.trahison.game;

/** A player who started untying another one (T7.2): the rope comes off when he asks again after the waiting time. */
public record Untying(int by, long startedMs) {
}
