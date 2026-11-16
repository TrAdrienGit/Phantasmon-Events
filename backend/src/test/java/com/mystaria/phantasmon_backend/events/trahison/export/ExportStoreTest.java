package com.mystaria.phantasmon_backend.events.trahison.export;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mystaria.phantasmon_backend.common.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportStoreTest {

	@TempDir
	Path dir;

	@Test
	void filesAreKeptPerGameAndReadBack() {
		ExportStore store = new ExportStore(dir.toString());
		assertThat(store.games()).isEmpty();
		store.write("game_1", "place_1a.schem", new byte[] { 1, 2, 3 });
		store.write("game_1", "paste_sheet.txt", "é".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		store.write("game_2", "place_1a.schem", new byte[] { 9 });
		assertThat(store.read("game_1", "place_1a.schem")).containsExactly(1, 2, 3);
		assertThat(store.games()).extracting(ExportStore.Game::game).containsExactlyInAnyOrder("game_1", "game_2");
		assertThat(store.games().stream().filter(g -> g.game().equals("game_1")).findFirst().get().files())
				.containsExactly("paste_sheet.txt", "place_1a.schem");
		store.write("game_1", "place_1a.schem", new byte[] { 7 });
		assertThat(store.read("game_1", "place_1a.schem")).containsExactly(7);
	}

	@Test
	void namesCannotLeaveTheFolder() {
		ExportStore store = new ExportStore(dir.toString());
		for (String bad : new String[] { "..", "../x", "a/b", ".hidden", "", "a\\b" }) {
			assertThatThrownBy(() -> store.write("g", bad, new byte[] { 1 })).isInstanceOf(ApiException.class);
			assertThatThrownBy(() -> store.read(bad, "f")).isInstanceOf(ApiException.class);
		}
	}

	@Test
	void missingFileIsNotFoundAndBadBase64IsRefused() {
		ExportStore store = new ExportStore(dir.toString());
		assertThatThrownBy(() -> store.read("g", "nope.txt")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> store.decode("***")).isInstanceOf(ApiException.class);
		assertThat(store.decode("AQID")).containsExactly(1, 2, 3);
	}
}
