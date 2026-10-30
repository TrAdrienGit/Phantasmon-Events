package com.mystaria.events.tournament;

import java.util.List;
import java.util.UUID;

/** A tournament as the backend shows it ({@code GET /tournaments/{name}}); dates ISO-8601, null if not set. */
public record TournamentDto(String name, String kind, int bestOf, String formatId, String formatName, String status,
		String startsAt, String registrationOpensAt, String registrationClosesAt, boolean registrationOpen,
		Integer plannedRounds, int currentRound, List<Player> players) {

	public record Player(UUID uuid, String username, boolean checkedIn, boolean dropped) {
	}

	public boolean swiss() {
		return "SWISS".equals(kind);
	}
}
