package com.mystaria.phantasmon_backend.events.trahison.layout;

/**
 * The place of a room (48 x 48 x 24 blocks): its {@code round} (1 to 3), the {@code origin} its content is relative to
 * (the minimum corner of the place) and the {@code orientation} ({@link LayoutValidator#ORIENTATIONS}: the side the
 * room's entrance faces).
 */
public record RoomSlot(String id, Integer round, Pos origin, String orientation) {
}
