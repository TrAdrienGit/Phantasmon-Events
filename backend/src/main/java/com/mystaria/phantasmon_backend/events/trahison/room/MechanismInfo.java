package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.List;

import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismParam;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.SlotRole;

/** One entry of {@code GET /events/trahison/mechanisms}: what the room editor needs to offer a mechanism. */
public record MechanismInfo(String id, List<String> clueRoles, List<SlotRole> slotRoles, List<MechanismParam> params, int difficulty, int expectedSeconds) {
}
