package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

/**
 * Describes one parameter a mechanism reads from a step's {@code params}, for the room editor. {@code type} is
 * {@code int} (between {@code min} and {@code max}), {@code bool} or {@code text} (min and max are then 0);
 * {@code def} is the default as text.
 */
public record MechanismParam(String name, String type, int min, int max, String def, String description) {
}
