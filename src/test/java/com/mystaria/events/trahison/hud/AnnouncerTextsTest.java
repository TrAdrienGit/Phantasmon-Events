package com.mystaria.events.trahison.hud;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T9.4: the hostess's texts file has every moment in both languages, and picks a variant as the backend says. */
class AnnouncerTextsTest {

	private static final List<String> REQUIRED = List.of("phase.lobby", "phase.prologue", "phase.doors", "phase.rooms", "phase.free_time", "phase.vote",
			"phase.results", "phase.final", "phase.ended", "doors_dead", "death", "betrayal", "alliance", "final_door_open", "final_countdown",
			"game_master_killed", "ending.solo", "ending.some", "ending.all", "ending.nobody", "role.infiltrator", "role.game_master", "letter");

	private static List<String> sayKeysOf(String lang) {
		JsonObject lines = new JsonObject();
		try (var in = AnnouncerTextsTest.class.getResourceAsStream("/assets/phantasmon_events/lang/" + lang + ".json")) {
			lines = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
		List<String> out = new ArrayList<>();
		for (String key : lines.keySet()) {
			if (key.startsWith(AnnouncerTexts.KEY_PREFIX)) {
				out.add(AnnouncerTexts.momentOf(key));
			}
		}
		return out;
	}

	@Test
	void everyMomentHasAVariantInBothLanguages() {
		for (String lang : List.of("fr_fr", "en_us")) {
			AnnouncerTexts texts = AnnouncerTexts.forLanguage(lang);
			for (String moment : REQUIRED) {
				assertTrue(texts.variants(moment) >= 1, lang + " lacks " + moment);
			}
			// every hostess key of the language file is in the texts file too (the file is the one place to edit)
			for (String moment : sayKeysOf(lang)) {
				assertTrue(texts.variants(moment) >= 1, lang + " texts file lacks the language key say." + moment);
			}
		}
	}

	@Test
	void bothLanguagesHaveTheSameMomentsAndFragments() {
		AnnouncerTexts fr = AnnouncerTexts.forLanguage("fr_fr");
		AnnouncerTexts en = AnnouncerTexts.forLanguage("en_us");
		assertEquals(fr.moments(), en.moments());
		assertNotNull(fr.fragment("1"));
		assertNotNull(en.fragment("1"));
	}

	@Test
	void theVariantIsTakenModuloAndTheArgumentsFilledIn() {
		AnnouncerTexts t = AnnouncerTexts.parse(new StringReader("{\"lines\":{\"m\":[\"one %1$s\",\"two %1$s\",\"three\"]}}"));
		assertEquals("one x", t.line("m", 0, List.of("x")));
		assertEquals("two x", t.line("m", 1, List.of("x")));
		assertEquals("one x", t.line("m", 3, List.of("x")));
		assertEquals("two x", t.line("m", 999_999 * 3 + 1, List.of("x")));
		assertEquals("one y", t.line("m", -5, List.of("y")));
		assertNull(t.line("absent", 0, List.of()));
	}

	@Test
	void aMissingFragmentReadsAsToBeWritten() {
		AnnouncerTexts t = AnnouncerTexts.forLanguage("en_us");
		assertEquals("Fragment 999 — to be written.", t.fragmentOrPlaceholder("999"));
		assertEquals("Fragment 999 — à écrire.", AnnouncerTexts.forLanguage("fr_fr").fragmentOrPlaceholder("999"));
	}

	@Test
	void aBrokenPlaceholderDoesNotHideTheLine() {
		AnnouncerTexts t = AnnouncerTexts.parse(new StringReader("{\"lines\":{\"m\":[\"100% sure %z\"]}}"));
		assertEquals("100% sure %z", t.line("m", 0, List.of()));
	}
}
