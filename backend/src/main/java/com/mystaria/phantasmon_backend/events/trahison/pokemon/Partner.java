package com.mystaria.phantasmon_backend.events.trahison.pokemon;

/** The partner Pokemon of one member of a room's group: a Cobblemon species id and the power it holds. Public to the whole group. */
public record Partner(int player, String species, Power power) {
}
