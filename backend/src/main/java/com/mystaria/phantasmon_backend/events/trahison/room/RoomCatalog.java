package com.mystaria.phantasmon_backend.events.trahison.room;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mystaria.phantasmon_backend.common.ApiException;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;

import tools.jackson.databind.ObjectMapper;

/**
 * The room bank: definitions in {@code trahison_rooms} (JSON), blocks in a file next to the hub schematics
 * ({@code <schematics-dir>/trahison_rooms/<id>/room.bin}, solution S13). Saving validates the definition, numbers the
 * version and computes difficulty and expected time from the steps.
 */
@Service
public class RoomCatalog {

	public static final String INVALID = "ERROR_TRAHISON_ROOM_INVALID";
	public static final String NOT_FOUND = "ERROR_TRAHISON_ROOM_NOT_FOUND";
	public static final String BAD_ID = "ERROR_TRAHISON_ROOM_BAD_ID";
	public static final String SCHEMATIC_MISSING = "ERROR_TRAHISON_ROOM_SCHEMATIC_MISSING";
	public static final String SCHEMATIC_INVALID = "ERROR_TRAHISON_ROOM_SCHEMATIC_INVALID";
	/** 16 MiB: a 48 x 48 x 24 room is far below. */
	public static final int MAX_SCHEMATIC_BYTES = 16 * 1024 * 1024;

	private static final String FILE = "room.bin";

	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;
	private final MechanismRegistry mechanisms;
	private final Path root;

	public RoomCatalog(JdbcTemplate jdbc, ObjectMapper mapper, MechanismRegistry mechanisms,
			@Value("${phantasmon.hub.schematics-dir:hub_schematics}") String schematicsDir) {
		this.jdbc = jdbc;
		this.mapper = mapper;
		this.mechanisms = mechanisms;
		this.root = Path.of(schematicsDir, "trahison_rooms");
	}

	public List<RoomSummary> list() {
		return jdbc.query("SELECT data::text, updated_at FROM trahison_rooms ORDER BY id", (rs, n) -> {
			RoomDefinition d = mapper.readValue(rs.getString(1), RoomDefinition.class);
			return new RoomSummary(d.id(), d.name(), d.author(), d.version(), d.difficulty(), d.expectedSeconds(),
					d.steps().size(), d.slots().size(), Files.exists(file(d.id())), rs.getTimestamp(2).toInstant());
		});
	}

	public Optional<RoomDefinition> find(String id) {
		checkId(id);
		List<RoomDefinition> rows = jdbc.query("SELECT data::text FROM trahison_rooms WHERE id = ?",
				(rs, n) -> mapper.readValue(rs.getString(1), RoomDefinition.class), id);
		return rows.stream().findFirst();
	}

	public RoomDefinition get(String id) {
		return find(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, Map.of("id", id)));
	}

	/** Validates and stores the room under {@code id} (create or replace); the version goes up by one. */
	@Transactional
	public RoomDefinition save(UUID admin, String id, RoomDefinition submitted) {
		checkId(id);
		RoomDefinition room = submitted.withId(id);
		List<RoomValidator.Issue> issues = RoomValidator.validate(room, mechanisms::find);
		if (!issues.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, INVALID, Map.of("errors", issues.stream()
					.map(i -> Map.of("code", i.code(), "ids", i.ids(), "info", i.info() == null ? "" : i.info())).toList()));
		}
		int[] totals = RoomValidator.totals(room, mechanisms::find);
		int version = jdbc.query("SELECT version FROM trahison_rooms WHERE id = ? FOR UPDATE", (rs, n) -> rs.getInt(1), id)
				.stream().findFirst().orElse(0) + 1;
		RoomDefinition stored = room.withComputed(version, totals[0], totals[1]);
		jdbc.update("""
				INSERT INTO trahison_rooms (id, version, data, updated_by) VALUES (?, ?, ?::jsonb, ?)
				ON CONFLICT (id) DO UPDATE SET version = EXCLUDED.version, data = EXCLUDED.data,
					updated_by = EXCLUDED.updated_by, updated_at = now()
				""", id, version, mapper.writeValueAsString(stored), admin);
		return stored;
	}

	/** Removes the room and its blocks. */
	@Transactional
	public void delete(String id) {
		get(id);
		jdbc.update("DELETE FROM trahison_rooms WHERE id = ?", id);
		try {
			Files.deleteIfExists(file(id));
			Files.deleteIfExists(file(id).getParent());
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	// ---------------------------------------------------------------- blocks

	/** The room's blocks (an opaque file: the client's format). */
	public byte[] readSchematic(String id) {
		get(id);
		try {
			return Files.readAllBytes(file(id));
		} catch (java.nio.file.NoSuchFileException ex) {
			throw new ApiException(HttpStatus.NOT_FOUND, SCHEMATIC_MISSING, Map.of("id", id));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Decodes the base64 of a JSON upload; the decoded size is checked early so that a huge text is not decoded at all. */
	public byte[] decodeSchematic(String base64) {
		if (base64 == null || base64.isEmpty() || base64.length() / 4L * 3 > MAX_SCHEMATIC_BYTES + 3) {
			throw new ApiException(HttpStatus.BAD_REQUEST, SCHEMATIC_INVALID,
					Map.of("size", base64 == null ? 0 : base64.length() / 4L * 3, "max", MAX_SCHEMATIC_BYTES));
		}
		try {
			return java.util.Base64.getDecoder().decode(base64);
		} catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, SCHEMATIC_INVALID, Map.of("reason", "not base64"));
		}
	}

	/** Replaces the room's blocks (written to a temporary file, then moved). The room's definition must exist. */
	public void writeSchematic(String id, byte[] bytes) {
		get(id);
		if (bytes == null || bytes.length == 0 || bytes.length > MAX_SCHEMATIC_BYTES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, SCHEMATIC_INVALID,
					Map.of("size", bytes == null ? 0 : bytes.length, "max", MAX_SCHEMATIC_BYTES));
		}
		Path target = file(id);
		try {
			Files.createDirectories(target.getParent());
			Path temp = Files.createTempFile(target.getParent(), "room", ".tmp");
			Files.write(temp, bytes);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private Path file(String id) {
		return root.resolve(id).resolve(FILE);
	}

	private static void checkId(String id) {
		if (!RoomValidator.validId(id)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, BAD_ID, Map.of());
		}
	}
}
