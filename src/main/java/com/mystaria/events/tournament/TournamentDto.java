package com.mystaria.events.tournament;

import java.util.List;
import java.util.UUID;

/** A tournament as the backend shows it ({@code GET /tournaments/{name}}); dates ISO-8601, null if not set. */
public record TournamentDto(String name, String kind, int bestOf, String formatId, String formatName, String status,
		String startsAt, String registrationOpensAt, String registrationClosesAt, boolean registrationOpen,
		Integer plannedRounds, int currentRound, List<Player> players, List<Match> matches, List<Standing> standings) {

	public record Player(UUID uuid, String username, boolean checkedIn, boolean dropped) {
	}

	/** {@code player2} null: a bye. {@code status}: PENDING, PLAYING or DONE. */
	public record Match(int round, String bracket, int position, String player1, String player2, int wins1, int wins2,
			String winner, String status, boolean forfeit) {
	}

	public record Standing(int rank, String username, int points, int wins, int losses, int buchholz, double opponentsWinRate,
			boolean dropped) {
	}

	public boolean swiss() {
		return "SWISS".equals(kind);
	}

	public List<Match> matchesOf(int round) {
		return matches == null ? List.of() : matches.stream().filter(m -> m.round() == round).toList();
	}
}
