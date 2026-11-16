package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mystaria.phantasmon_backend.common.ApiException;

import tools.jackson.databind.ObjectMapper;

/**
 * The fixed plan of the complex, versioned per hub ({@code trahison_layouts}). Every save adds a row with the next
 * version; nothing is overwritten. Reading a hub that has no plan gives version 0 and an empty plan.
 */
@Service
public class LayoutService {

	public static final String INVALID = "ERROR_TRAHISON_LAYOUT_INVALID";
	public static final String CONFLICT = "ERROR_TRAHISON_LAYOUT_CONFLICT";
	public static final String BAD_HUB = "ERROR_TRAHISON_LAYOUT_BAD_HUB";

	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;

	public LayoutService(JdbcTemplate jdbc, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	/** The latest version of the hub's plan, or version 0 (empty) if it never had one. */
	public Layout latest(String hub) {
		checkHub(hub);
		List<Layout> rows = jdbc.query(
				"SELECT version, draft, data::text, updated_at FROM trahison_layouts WHERE hub = ? ORDER BY version DESC LIMIT 1",
				(rs, n) -> Layout.of(hub, rs.getInt(1), rs.getBoolean(2), rs.getTimestamp(4).toInstant(),
						mapper.readValue(rs.getString(3), LayoutContent.class)),
				hub);
		return rows.isEmpty() ? Layout.of(hub, 0, true, Instant.EPOCH, LayoutContent.empty()) : rows.get(0);
	}

	/** The hub's plan at a given version (a running game keeps the one it started with); the latest if that one is gone. */
	public Layout version(String hub, int version) {
		checkHub(hub);
		List<Layout> rows = jdbc.query(
				"SELECT version, draft, data::text, updated_at FROM trahison_layouts WHERE hub = ? AND version = ?",
				(rs, n) -> Layout.of(hub, rs.getInt(1), rs.getBoolean(2), rs.getTimestamp(4).toInstant(),
						mapper.readValue(rs.getString(3), LayoutContent.class)),
				hub, version);
		return rows.isEmpty() ? latest(hub) : rows.get(0);
	}

	/**
	 * Validates and saves a new version. A refused plan raises {@code 400 ERROR_TRAHISON_LAYOUT_INVALID} whose
	 * {@code details.errors} lists every {@link LayoutValidator.Issue}; a plan saved on top of a newer one than the
	 * editor started from raises {@code 409 ERROR_TRAHISON_LAYOUT_CONFLICT}.
	 */
	@Transactional
	public Layout save(UUID admin, String hub, LayoutRequest request) {
		checkHub(hub);
		LayoutContent content = request.content();
		List<LayoutValidator.Issue> issues = LayoutValidator.validate(content, request.draft());
		if (!issues.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, INVALID, Map.of("errors", issues.stream()
					.map(i -> Map.of("code", i.code(), "ids", i.ids(), "info", i.info() == null ? "" : i.info())).toList()));
		}
		int current = latest(hub).version();
		if (request.baseVersion() != null && request.baseVersion() != current) {
			throw new ApiException(HttpStatus.CONFLICT, CONFLICT, Map.of("version", current));
		}
		int next = current + 1;
		try {
			jdbc.update("INSERT INTO trahison_layouts (hub, version, draft, data, updated_by) VALUES (?, ?, ?, ?::jsonb, ?)",
					hub, next, request.draft(), mapper.writeValueAsString(content), admin);
		} catch (DuplicateKeyException ex) {
			// Another admin saved the same version at the same moment.
			throw new ApiException(HttpStatus.CONFLICT, CONFLICT, Map.of("version", next));
		}
		return latest(hub);
	}

	private static void checkHub(String hub) {
		if (hub == null || hub.isBlank() || hub.length() > 64) {
			throw new ApiException(HttpStatus.BAD_REQUEST, BAD_HUB, Map.of());
		}
	}
}
