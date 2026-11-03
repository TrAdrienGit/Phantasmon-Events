package com.mystaria.events.npc;

import java.util.List;
import java.util.UUID;

/**
 * An event's trainer NPC as the backend shows it ({@code GET /events/npcs}): {@code open} whether its event is open
 * (only admins see the others), {@code teamSpecies} for admins only. {@code hub}: the hub it belongs to (Adrien
 * 2026-11-03), then {@code x, y, z, yaw} are hub coordinates and it stands in each of that hub's zones; null: a place
 * in this world. {@code aiDebug}: its AI explains each decision in the chat of the player battling it (admins only).
 */
public record EventNpcDto(UUID uuid, String event, boolean open, String name, String skin, double x, double y, double z,
		float yaw, String ai, String formatId, int teamSize, List<String> teamSpecies, String hub, boolean aiDebug) {
}
