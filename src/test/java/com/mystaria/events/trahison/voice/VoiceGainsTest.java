package com.mystaria.events.trahison.voice;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mystaria.events.trahison.voice.VoiceGains.Input;
import com.mystaria.events.trahison.voice.VoiceGains.Speaker;
import com.mystaria.events.trahison.voice.VoiceGains.Table;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/** T5.2 to T5.4: the gain rules, without the game. */
class VoiceGainsTest {

	private static final UUID A = new UUID(0, 1);
	private static final UUID B = new UUID(0, 2);
	private static final UUID AVATAR_A = new UUID(5, 1);
	private static final double EPS = 1e-9;

	private static Speaker speaker(UUID id, boolean dead, boolean same, boolean ear, boolean announcing, double distance) {
		return new Speaker(id, null, dead, same, ear, announcing, distance);
	}

	private static Table living(Speaker... speakers) {
		return VoiceGains.compute(new Input(true, true, List.of(speakers)));
	}

	@Test
	void outsideAGameNothingIsFiltered() {
		Table t = VoiceGains.compute(new Input(false, true, List.of(speaker(A, false, false, false, false, 1))));
		assertEquals(1, t.gain(A, false), EPS);
		assertEquals(1, t.gain(UUID.randomUUID(), false), EPS);
	}

	@Test
	void onlyTheSameSoundZoneIsHeardAndNobodyElse() {
		Table t = living(speaker(A, false, true, false, false, 1), speaker(B, false, false, false, false, 1));
		assertEquals(1, t.gain(A, false), EPS);
		assertEquals(0, t.gain(B, false), EPS);
	}

	@Test
	void inTheZoneTheVoiceIsFullToFourBlocksThenFallsToZeroAtEight() {
		assertEquals(1, living(speaker(A, false, true, false, false, 0)).gain(A, false), EPS);
		assertEquals(1, living(speaker(A, false, true, false, false, 4)).gain(A, false), EPS);
		assertEquals(0.5, living(speaker(A, false, true, false, false, 6)).gain(A, false), EPS);
		assertEquals(0, living(speaker(A, false, true, false, false, 8)).gain(A, false), EPS);
		assertEquals(0, living(speaker(A, false, true, false, false, 30)).gain(A, false), EPS);
	}

	@Test
	void aWhisperIsHeardToThreeBlocks() {
		Table t = living(speaker(A, false, true, false, false, 2.25));
		assertEquals(0.5, t.gain(A, true), EPS);
		assertEquals(1, t.gain(A, false), EPS); // the same distance is nothing for a normal voice
		assertEquals(0, living(speaker(A, false, true, false, false, 3)).gain(A, true), EPS);
		assertEquals(1, living(speaker(A, false, true, false, false, 1.5)).gain(A, true), EPS);
	}

	@Test
	void aSpeakerOutOfTheWorldInTheSameZoneIsFull() {
		assertEquals(1, living(speaker(A, false, true, false, false, Double.NaN)).gain(A, false), EPS);
	}

	@Test
	void aGhostIsNotHeardByTheLivingEvenInTheirZone() {
		Table t = living(speaker(A, true, true, false, false, 1));
		assertEquals(0, t.gain(A, false), EPS);
	}

	@Test
	void aVoiceTheGameDoesNotListIsMutedForTheLivingButNotForTheOthers() {
		UUID spectator = UUID.randomUUID();
		assertEquals(0, living(speaker(A, false, true, false, false, 1)).gain(spectator, false), EPS);
		Table ghost = VoiceGains.compute(new Input(true, false, List.of(speaker(A, false, false, false, false, 30))));
		assertEquals(1, ghost.gain(spectator, false), EPS);
	}

	@Test
	void aGhostOrASpectatorHearsEverybodyAtFullGain() {
		Table t = VoiceGains.compute(new Input(true, false, List.of(speaker(A, false, false, false, false, 50), speaker(B, true, false, false, false, 2))));
		assertEquals(1, t.gain(A, false), EPS);
		assertEquals(1, t.gain(B, true), EPS);
	}

	@Test
	void theAnnouncingGameMasterIsHeardByEveryoneAtFullGain() {
		Table living = living(speaker(A, false, false, false, true, 50));
		assertEquals(1, living.gain(A, false), EPS);
		assertEquals(1, living.gain(A, true), EPS);
		// even if the game master is also "dead" in a corner of the view
		assertEquals(1, living(speaker(A, true, false, false, true, 50)).gain(A, false), EPS);
	}

	@Test
	void theEarHearsTheListenedCabinWhateverTheZone() {
		Table t = living(speaker(A, false, false, true, false, 40), speaker(B, false, false, false, false, 1));
		assertEquals(1, t.gain(A, false), EPS);
		assertEquals(0, t.gain(B, false), EPS);
	}

	@Test
	void theEarDoesNotUnmuteAGhost() {
		assertEquals(0, living(speaker(A, true, false, true, false, 1)).gain(A, false), EPS);
	}

	@Test
	void theHubCopyVoiceIsFilteredUnderTheAvatarId() {
		Speaker muted = new Speaker(A, AVATAR_A, false, false, false, false, 3);
		Speaker heard = new Speaker(B, null, false, true, false, false, 1);
		Table t = VoiceGains.compute(new Input(true, true, List.of(muted, heard)));
		assertEquals(0, t.gain(A, false), EPS);
		assertEquals(0, t.gain(AVATAR_A, false), EPS);
		// a ghost watching from a copy hears the avatar of a living player in another zone
		Table watcher = VoiceGains.compute(new Input(true, false, List.of(muted)));
		assertEquals(1, watcher.gain(AVATAR_A, false), EPS);
	}

	@Test
	void forcedGainsOverrideTheRulesForTheGivenKeys() {
		Table t = living(speaker(A, false, true, false, false, 1)).withForced(Map.of(A, 0.0, AVATAR_A, 0.5));
		assertEquals(0, t.gain(A, false), EPS);
		assertEquals(0.5, t.gain(AVATAR_A, false), EPS);
		assertEquals(1, Table.OPEN.withForced(Map.of(B, 7.0)).gain(B, false), EPS); // clamped
		assertSame(Table.OPEN, Table.OPEN.withForced(Map.of()));
	}

	@Test
	void applyingZeroGivesSilenceOfTheSameLength() {
		short[] frame = { 100, -200, 300, Short.MAX_VALUE };
		short[] out = VoiceGains.apply(frame, 0);
		assertArrayEquals(new short[4], out);
		assertNotSame(frame, out);
		assertArrayEquals(new short[] { 100, -200, 300, Short.MAX_VALUE }, frame); // the input is not touched
	}

	@Test
	void applyingAGainScalesEverySampleAndClamps() {
		assertArrayEquals(new short[] { 50, -100, 150 }, VoiceGains.apply(new short[] { 100, -200, 300 }, 0.5));
		assertArrayEquals(new short[] { Short.MIN_VALUE, 0 }, VoiceGains.apply(new short[] { Short.MIN_VALUE, 0 }, 1.0));
	}

	@Test
	void anEmptyFrameAndAFullGainAreReturnedAsTheyAre() {
		short[] empty = new short[0];
		assertSame(empty, VoiceGains.apply(empty, 0)); // the end of speech
		short[] frame = { 1, 2 };
		assertSame(frame, VoiceGains.apply(frame, 1));
		assertNull(VoiceGains.apply(null, 0));
	}
}
