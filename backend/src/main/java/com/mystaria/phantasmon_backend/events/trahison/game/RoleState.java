package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the secret roles remember during a game (T7.1). Kept with the game, so a restart brings it back. Everything in it is
 * secret: only {@code Views} decides who sees what (the owner of a role, the game masters, everybody at the end).
 */
public class RoleState {
	/** The seer used his only {@code foresee}. */
	public boolean seerUsed;
	/** What he saw: the group (0..2), the side ({@code PAIR} or {@code SOLO}) and the vote; null before. */
	public Integer seerGroup;
	public String seerSide;
	public String seerVote;
	/** The gambler's bets: number of the round (as {@code RoundState.number}) to the vote he bet the opponent casts. */
	public Map<Integer, String> bets = new LinkedHashMap<>();
	/** The gambler already got his point for a right bet. */
	public boolean gamblerPaid;
	/** Seats of the infiltrators who betrayed in round 1 and got the point. */
	public List<Integer> infiltratorBetrayed = new ArrayList<>();
}
