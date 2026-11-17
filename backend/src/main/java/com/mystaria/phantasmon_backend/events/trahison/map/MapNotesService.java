package com.mystaria.phantasmon_backend.events.trahison.map;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.mystaria.phantasmon_backend.events.trahison.game.TrahisonException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a person draws and writes on the map (T11.4): strokes per floor, in hub coordinates, and a free text. Kept per game
 * and account ({@code trahison_map_notes}). Only the owner reads and writes his own while the game runs; once it has ended,
 * everybody's can be read ({@link #all}, D15). Writes are limited in size and in rhythm, so the client batches them.
 */
@Service
public class MapNotesService {

	public static final String TOO_BIG = "ERROR_TRAHISON_MAP_TOO_BIG";
	public static final String TOO_FAST = "ERROR_TRAHISON_MAP_TOO_FAST";

	/** Points of stroke per floor, and characters of notes. */
	public static final int MAX_POINTS_PER_FLOOR = 2000;
	public static final int MAX_NOTES = 8000;
	public static final int MAX_FLOORS = 16;
	public static final int MAX_COLORS = 8;
	/** At most one write per this many milliseconds and account (the client groups its edits). */
	public static final long MIN_WRITE_GAP_MS = 2000;

	private static final Pattern FLOOR_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	/** One person's annotations: {@code strokes} is {@code floor id -> [{c: colour index, p: [x0, z0, x1, z1, ...]}]}. */
	public record Notes(Map<String, Object> strokes, String notes) {
		public boolean isEmpty() {
			return strokes.isEmpty() && notes.isEmpty();
		}
	}

	public record Row(UUID account, Notes notes) {
	}

	private final JdbcTemplate jdbc;
	private final Clock clock;
	private final Map<String, Long> lastWrite = new ConcurrentHashMap<>();

	@org.springframework.beans.factory.annotation.Autowired
	public MapNotesService(JdbcTemplate jdbc) {
		this(jdbc, Clock.systemUTC());
	}

	MapNotesService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** This account's annotations in this game; empty ones when there are none. */
	public Notes get(UUID game, UUID account) {
		List<Notes> rows = jdbc.query("SELECT strokes::text, notes FROM trahison_map_notes WHERE game_id = ? AND account = ?",
				(rs, i) -> new Notes(readStrokes(rs.getString(1)), rs.getString(2)), game, account);
		return rows.isEmpty() ? new Notes(new LinkedHashMap<>(), "") : rows.get(0);
	}

	/** Replaces this account's annotations. Refused when too big, or when the previous write was less than 2 seconds ago. */
	public void save(UUID game, UUID account, Object strokes, Object notes) {
		Notes checked = validate(strokes, notes);
		long now = clock.millis();
		Long last = lastWrite.put(game + "/" + account, now);
		if (last != null && now - last < MIN_WRITE_GAP_MS) {
			lastWrite.put(game + "/" + account, last); // the refused write does not push the next one back
			throw new TrahisonException(TOO_FAST, "one write every " + MIN_WRITE_GAP_MS + " ms");
		}
		jdbc.update("""
				INSERT INTO trahison_map_notes (game_id, account, strokes, notes, updated_at)
				VALUES (?, ?, ?::jsonb, ?, now())
				ON CONFLICT (game_id, account) DO UPDATE SET strokes = EXCLUDED.strokes, notes = EXCLUDED.notes, updated_at = now()
				""", game, account, MAPPER.writeValueAsString(checked.strokes()), checked.notes());
	}

	/** Everybody's non-empty annotations of a game, for its results (D15). */
	public List<Row> all(UUID game) {
		return jdbc.query("SELECT account, strokes::text, notes FROM trahison_map_notes WHERE game_id = ? ORDER BY updated_at",
				(rs, i) -> new Row(rs.getObject(1, UUID.class), new Notes(readStrokes(rs.getString(2)), rs.getString(3))), game)
				.stream().filter(r -> !r.notes().isEmpty()).toList();
	}

	/** Forgets the rhythm counters of a game that is gone. */
	public void forget(UUID game) {
		lastWrite.keySet().removeIf(k -> k.startsWith(game + "/"));
	}

	private static Map<String, Object> readStrokes(String json) {
		return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
		});
	}

	/**
	 * Checks what a client sent and rebuilds it clean (only the known fields, numbers rounded to a tenth of a block): at most
	 * {@link #MAX_FLOORS} floors with ids of letters, digits, {@code _} and {@code -}; per floor at most
	 * {@link #MAX_POINTS_PER_FLOOR} points in strokes of a colour {@code 0..7} and a flat list of coordinates; at most
	 * {@link #MAX_NOTES} characters of notes.
	 */
	static Notes validate(Object strokes, Object notes) {
		String text = notes == null ? "" : notes instanceof String s ? s : null;
		if (text == null) {
			throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "notes must be text");
		}
		if (text.length() > MAX_NOTES) {
			throw new TrahisonException(TOO_BIG, "notes: at most " + MAX_NOTES + " characters");
		}
		Map<String, Object> clean = new LinkedHashMap<>();
		if (strokes != null) {
			if (!(strokes instanceof Map<?, ?> floors)) {
				throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "strokes must be an object");
			}
			if (floors.size() > MAX_FLOORS) {
				throw new TrahisonException(TOO_BIG, "strokes: at most " + MAX_FLOORS + " floors");
			}
			for (Map.Entry<?, ?> floor : floors.entrySet()) {
				if (!(floor.getKey() instanceof String id) || !FLOOR_ID.matcher(id).matches()
						|| !(floor.getValue() instanceof List<?> list)) {
					throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "strokes: bad floor");
				}
				List<Object> out = new ArrayList<>();
				int points = 0;
				for (Object item : list) {
					if (!(item instanceof Map<?, ?> stroke) || !(stroke.get("c") instanceof Number c)
							|| !(stroke.get("p") instanceof List<?> flat) || flat.size() % 2 != 0
							|| c.intValue() < 0 || c.intValue() >= MAX_COLORS) {
						throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "strokes: bad stroke");
					}
					points += flat.size() / 2;
					if (points > MAX_POINTS_PER_FLOOR) {
						throw new TrahisonException(TOO_BIG, "strokes: at most " + MAX_POINTS_PER_FLOOR + " points per floor");
					}
					List<Object> p = new ArrayList<>();
					for (Object n : flat) {
						if (!(n instanceof Number num) || !Double.isFinite(num.doubleValue())) {
							throw new TrahisonException(TrahisonException.BAD_ARGUMENT, "strokes: bad coordinate");
						}
						p.add(Math.round(num.doubleValue() * 10.0) / 10.0);
					}
					Map<String, Object> clean1 = new LinkedHashMap<>();
					clean1.put("c", c.intValue());
					clean1.put("p", p);
					out.add(clean1);
				}
				if (!out.isEmpty()) {
					clean.put(id, out);
				}
			}
		}
		return new Notes(clean, text);
	}
}
