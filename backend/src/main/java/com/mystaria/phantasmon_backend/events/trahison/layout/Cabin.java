package com.mystaria.phantasmon_backend.events.trahison.layout;

/**
 * A vote cabin. The vote room is shared by the three rounds, and each vote is a colour pair of doors against a solo, so a
 * cabin serves a colour pair ({@code serves}: {@code red_green}, {@code red_blue} or {@code green_blue}) on one
 * {@code side} ({@code pair} or {@code solo}): 3 x 2 = 6 cabins. {@code zone} is its inside (a zone of type
 * {@code cabin}); {@code door} its door (a door of type {@code cabin}).
 */
public record Cabin(String id, String serves, String side, String zone, String door) {
}
