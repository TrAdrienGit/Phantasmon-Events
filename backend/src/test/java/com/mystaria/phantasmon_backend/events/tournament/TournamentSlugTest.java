package com.mystaria.phantasmon_backend.events.tournament;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A tournament's id from its readable name (2026-11-12). */
class TournamentSlugTest {

	@Test
	void aReadableNameBecomesAnId() {
		assertEquals("weekly_ou_1", TournamentService.slug("Weekly OU #1"));
		assertEquals("coupe_d_hiver_2026", TournamentService.slug("  Coupe d'Hiver — 2026 !"));
		assertEquals("ligue_eleve", TournamentService.slug("Ligue Élève"));
		assertEquals("tournoi", TournamentService.slug("★★"));
		String longName = TournamentService.slug("Le grand tournoi annuel des champions de toutes les régions");
		assertEquals(32, longName.length(), longName);
	}
}
