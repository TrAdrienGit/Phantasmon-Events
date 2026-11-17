package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.List;

/**
 * The secret roles (plan.md section 4, T7.1). The six first are drawn at the start of a game; {@link #GAME_MASTER} (D12) is
 * never drawn: it is the role of the game master who also plays, derived from {@code Game.gameMasters}, shown in the same
 * places as any other role (his own view, the game masters' block, the reveal at the end).
 */
public enum Role {
	/** Must betray in round 1; +1 point when he does. */
	INFILTRATOR,
	/** Allied with the ghosts: wins when at least {@link Roles#FERRYMAN_GHOSTS} players are ghosts at the end. */
	FERRYMAN,
	/** Can never betray; wins when at least {@link Roles#GUARDIAN_ESCAPED} players walk through the final door. */
	GUARDIAN,
	/** Once in the game, sees the vote of one side of a group before the results. */
	SEER,
	/** Before a vote, bets on the opponent's vote; the first right bet gives +1 point. */
	GAMBLER,
	/** Wins only when he is the only player through the final door. */
	LONER,
	/** D12: the game master who plays. */
	GAME_MASTER;

	/** The roles the draw hands out. */
	public static List<Role> drawable() {
		return List.of(INFILTRATOR, FERRYMAN, GUARDIAN, SEER, GAMBLER, LONER);
	}

	public static Role parse(Object value) {
		try {
			return valueOf(String.valueOf(value).trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
