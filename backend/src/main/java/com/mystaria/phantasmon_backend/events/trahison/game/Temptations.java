package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.List;
import java.util.Random;

/**
 * T7.3: the automatic temptations, as pure functions of the game's seed (the same seed always tempts the same people). The
 * service gathers the candidates and the facts; this class only draws.
 */
public final class Temptations {

	/** The extra point of an accepted cabin offer ("Betray: one more point"). */
	public static final int OFFER_BONUS = 1;

	private Temptations() {
	}

	/** One true thing taken from the game's log: the language key suffix, who it is about and its arguments. */
	public record Fact(String kind, int about, List<String> args) {
	}

	/** What the temptations remember in a game; kept with it so a restart brings it back. */
	public static class State {
		/** The round the current offer belongs to (as {@code RoundState.number}); 0 before any. */
		public int offerRound;
		/** The seat who may receive the offer, or -1. */
		public int offerSeat = -1;
		/** The offer was handed to him (whispered, announced). */
		public boolean offerMade;
		/** The game master made it by hand. */
		public boolean offerForced;
	}

	private static Random random(long seed, int round, int salt) {
		return new Random(seed * 1_000_003L + round * 7919L + salt);
	}

	/** The seat that gets the offer of the round among {@code candidates}; -1 when there is nobody. */
	public static int pickOfferee(long seed, int round, List<Integer> candidates) {
		if (candidates.isEmpty()) {
			return -1;
		}
		return candidates.get(random(seed, round, 1).nextInt(candidates.size()));
	}

	/**
	 * The leak after the results of a round: a receiver among {@code receivers}, and a fact about somebody else drawn among
	 * {@code facts}. Null when no pair works.
	 */
	public static Leak pickLeak(long seed, int round, List<Integer> receivers, List<Fact> facts) {
		if (receivers.isEmpty() || facts.isEmpty()) {
			return null;
		}
		Random rnd = random(seed, round, 2);
		int first = rnd.nextInt(receivers.size());
		for (int k = 0; k < receivers.size(); k++) {
			int receiver = receivers.get((first + k) % receivers.size());
			List<Fact> usable = facts.stream().filter(f -> f.about() != receiver).toList();
			if (!usable.isEmpty()) {
				return new Leak(receiver, usable.get(rnd.nextInt(usable.size())));
			}
		}
		return null;
	}

	public record Leak(int receiver, Fact fact) {
	}
}
