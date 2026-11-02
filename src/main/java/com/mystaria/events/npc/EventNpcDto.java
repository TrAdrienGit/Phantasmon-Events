package com.mystaria.events.npc;

import java.util.List;
import java.util.UUID;

/**
 * An event's trainer NPC as the backend shows it ({@code GET /events/npcs}): {@code open} whether its event is open
 * (only admins see the others), {@code teamSpecies} for admins only.
 */
public record EventNpcDto(UUID uuid, String event, boolean open, String name, String skin, double x, double y, double z,
		float yaw, String ai, String formatId, int teamSize, List<String> teamSpecies) {
}
