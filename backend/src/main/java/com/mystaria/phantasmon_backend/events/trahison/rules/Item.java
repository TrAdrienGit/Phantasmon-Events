package com.mystaria.phantasmon_backend.events.trahison.rules;

/** The hidden items (plan.md sections 3 and 4, T7.2). Each is used once, except the shield which works by itself. */
public enum Item {
	/** Kills a living player within 3 blocks, in the same zone. */
	KNIFE,
	/** Ties a player on the spot until another one unties him, or the maximum time. */
	BONDS,
	/** Locks an ordinary door or a cabin from outside. */
	LOCK,
	/** Listens to a cabin during a vote (the sound itself is T5.4). */
	EAR,
	/** Cancels the next -2 its bearer suffers. */
	SHIELD,
	/** Reopens a locked door or cabin. */
	PASS,
	/** An anonymous short message to a player. */
	LETTER;

	public static Item parse(Object value) {
		try {
			return valueOf(String.valueOf(value).trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
