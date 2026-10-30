package com.mystaria.events.tournament;

import java.util.List;
import java.util.UUID;

/** A tournament as the backend shows it ({@code GET /tournaments/{name}}); dates ISO-8601, null if not set. */
public record TournamentDto(String name, String kind, int bestOf, String formatId, String formatName, String status,
		String startsAt, String registrationOpensAt, String registrationClosesAt, boolean registrationOpen,
		Integer plannedRounds, int currentRound, List<Player> players, List<Match> matches, List<Standing> standings) {

	public record Player(UUID uuid, String username, boolean checkedIn, boolean dropped) {
	}

	/**
	 * {@code status}: PENDING, PLAYING or DONE. Swiss: {@code player2} null, a bye. Bracket: a player null, not known
	 * yet, {@code "-"} nobody (bye); {@code wave}: when an admin opens it.
	 */
	public record Match(int round, String bracket, int position, int wave, String player1, String player2, int wins1, int wins2,
			String winner, String status, boolean forfeit) {

		boolean walkover() {
			return "-".equals(player1) || "-".equals(player2);
		}
	}

	public record Standing(int rank, String username, int points, int wins, int losses, int buchholz, double opponentsWinRate,
			boolean dropped) {
	}

	public boolean swiss() {
		return "SWISS".equals(kind);
	}

	/**
	 * The matches being played: Swiss, the current round's; bracket, those of the waves opened (the current one, and
	 * earlier ones not decided yet), walkovers left out.
	 */
	public List<Match> currentMatches() {
		if (matches == null) {
			return List.of();
		}
		if (swiss()) {
			return matchesOf(currentRound);
		}
		return matches.stream().filter(m -> !m.walkover() && m.wave() <= currentRound
				&& (m.wave() == currentRound || !"DONE".equals(m.status()))).toList();
	}

	public List<Match> matchesOf(int round) {
		return matches == null ? List.of() : matches.stream().filter(m -> m.round() == round).toList();
	}
}
