package com.mystaria.phantasmon_backend.events.trahison.room;

/** A help text given to the group: the step it is about, its rank (1 vague to 3 direct) and the text. */
public record Hint(String step, int rank, String text) {
}
