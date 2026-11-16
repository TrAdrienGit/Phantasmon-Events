package com.mystaria.phantasmon_backend.events.trahison.room;

import com.mystaria.phantasmon_backend.events.trahison.layout.Pos;

/**
 * Where a room was put in the hub: the plan's room place it uses ({@code id}), the {@code origin} (its minimum corner, hub
 * coordinates) and the {@code orientation} (the world side the room's own north, its exit side, points to). A slot's
 * position is relative to the room's canonical frame; the client turns it by {@code orientation} inside the 48 x 48 square
 * at {@code origin} (protocole.md, "Place d'une salle"). Sent in {@code EventsTrahisonRoom} so no client has to guess which
 * place a group plays in.
 */
public record RoomPlace(String id, Pos origin, String orientation) {
}
