package com.mystaria.phantasmon_backend.events.trahison.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.mystaria.phantasmon_backend.TestcontainersConfiguration;
import com.mystaria.phantasmon_backend.events.trahison.game.TrahisonException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T11.4: a person's strokes and notes are validated, limited, kept per game and account, and shown to all at the end. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
class MapNotesServiceTest {

	@Autowired
	private MapNotesService service;

	private static Map<String, Object> strokes(int floors, int pointsPerFloor) {
		Map<String, Object> out = new java.util.LinkedHashMap<>();
		for (int f = 0; f < floors; f++) {
			List<Object> flat = new ArrayList<>();
			for (int i = 0; i < pointsPerFloor; i++) {
				flat.add(1.04 * i);
				flat.add(-2.0);
			}
			out.put("floor_" + f, List.of(Map.of("c", 1, "p", flat)));
		}
		return out;
	}

	@Test
	void whatIsSavedComesBackWhole() {
		UUID game = UUID.randomUUID();
		UUID me = UUID.randomUUID();
		assertThat(service.get(game, me).isEmpty()).isTrue();
		service.save(game, me, strokes(2, 3), "my notes\nsecond line");
		MapNotesService.Notes read = service.get(game, me);
		assertThat(read.notes()).isEqualTo("my notes\nsecond line");
		assertThat(read.strokes()).containsOnlyKeys("floor_0", "floor_1");
		assertThat(read.strokes().toString()).contains("c=1").contains("1.0");
	}

	@Test
	void everyoneKeepsHisOwnAndTheOthersAreNeverMixedIn() {
		UUID game = UUID.randomUUID();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		service.save(game, a, strokes(1, 2), "a's secret");
		assertThat(service.get(game, b).isEmpty()).isTrue();
		assertThat(service.get(UUID.randomUUID(), a).isEmpty()).isTrue(); // another game
		service.save(game, b, null, "b's note");
		assertThat(service.all(game)).extracting(MapNotesService.Row::account).containsExactlyInAnyOrder(a, b);
		assertThat(service.all(UUID.randomUUID())).isEmpty();
	}

	@Test
	void aLaterWriteReplacesTheEarlierOneAndAnEmptyWriteIsNotListedAtTheEnd() throws Exception {
		UUID game = UUID.randomUUID();
		UUID me = UUID.randomUUID();
		service.save(game, me, strokes(1, 2), "one");
		Thread.sleep(MapNotesService.MIN_WRITE_GAP_MS + 100);
		service.save(game, me, null, "");
		assertThat(service.get(game, me).isEmpty()).isTrue();
		assertThat(service.all(game)).isEmpty();
	}

	@Test
	void writesCloserThanTwoSecondsAreRefused() {
		UUID game = UUID.randomUUID();
		UUID me = UUID.randomUUID();
		service.save(game, me, null, "first");
		assertThatThrownBy(() -> service.save(game, me, null, "second")).isInstanceOf(TrahisonException.class)
				.satisfies(e -> assertThat(((TrahisonException) e).code()).isEqualTo(MapNotesService.TOO_FAST));
		assertThat(service.get(game, me).notes()).isEqualTo("first");
	}

	@Test
	void sizesAreLimited() {
		String tooBig = MapNotesService.TOO_BIG;
		assertThat(MapNotesService.validate(strokes(1, MapNotesService.MAX_POINTS_PER_FLOOR), "x".repeat(MapNotesService.MAX_NOTES)).notes())
				.hasSize(MapNotesService.MAX_NOTES);
		assertThatThrownBy(() -> MapNotesService.validate(strokes(1, MapNotesService.MAX_POINTS_PER_FLOOR + 1), ""))
				.satisfies(e -> assertThat(((TrahisonException) e).code()).isEqualTo(tooBig));
		assertThatThrownBy(() -> MapNotesService.validate(null, "x".repeat(MapNotesService.MAX_NOTES + 1)))
				.satisfies(e -> assertThat(((TrahisonException) e).code()).isEqualTo(tooBig));
		assertThatThrownBy(() -> MapNotesService.validate(strokes(MapNotesService.MAX_FLOORS + 1, 1), ""))
				.satisfies(e -> assertThat(((TrahisonException) e).code()).isEqualTo(tooBig));
	}

	@Test
	void malformedStrokesAreRefused() {
		String bad = TrahisonException.BAD_ARGUMENT;
		for (Object wrong : List.of("text", Map.of("bad floor!", List.of()), Map.of("f", "x"), Map.of("f", List.of(Map.of("c", 9, "p", List.of(1, 2)))),
				Map.of("f", List.of(Map.of("c", 1, "p", List.of(1)))), Map.of("f", List.of(Map.of("c", 1, "p", List.of(1, "a")))))) {
			assertThatThrownBy(() -> MapNotesService.validate(wrong, "")).satisfies(e -> assertThat(((TrahisonException) e).code()).isEqualTo(bad));
		}
		assertThatThrownBy(() -> MapNotesService.validate(null, 42)).isInstanceOf(TrahisonException.class);
	}
}
