package com.mystaria.events.trahison.voice;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mystaria.events.trahison.net.VoiceData;
import com.mystaria.events.trahison.net.VoiceData.Voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T5.2 to T5.4: the {@code voice} block of the view is read as the backend writes it. */
class VoiceDataTest {

	@Test
	void readsTheBlockAsTheBackendSendsIt() {
		UUID gm = UUID.randomUUID();
		Voice v = VoiceData.parse(Map.of("zone", "hall", "same", List.of(1, 4), "announcing", List.of(gm.toString()), "ear", List.of(2)));
		assertEquals("hall", v.zone());
		assertEquals(Set.of(1, 4), v.same());
		assertEquals(Set.of(gm), v.announcing());
		assertEquals(Set.of(2), v.ear());
	}

	@Test
	void aNullEarMeansTheEarListensToNothingAndAnEmptyOneToAnEmptyCabin() {
		Map<String, Object> block = new java.util.HashMap<>();
		block.put("zone", null);
		block.put("same", List.of());
		block.put("announcing", List.of());
		block.put("ear", null);
		assertNull(VoiceData.parse(block).ear());
		block.put("ear", List.of());
		assertTrue(VoiceData.parse(block).ear().isEmpty());
	}

	@Test
	void malformedBlocksGiveNothing() {
		assertEquals(Voice.NONE, VoiceData.parse(null));
		assertEquals(Voice.NONE, VoiceData.parse("x"));
		assertTrue(VoiceData.parse(Map.of("announcing", List.of("not-a-uuid"), "same", List.of("a"))).announcing().isEmpty());
	}
}
