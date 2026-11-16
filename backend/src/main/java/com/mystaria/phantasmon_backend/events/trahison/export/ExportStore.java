package com.mystaria.phantasmon_backend.events.trahison.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mystaria.phantasmon_backend.common.ApiException;

/**
 * The files an admin pastes for a game (T4.2, D80): the game master's client uploads them after writing them, an admin
 * downloads them later from a menu. Kept per game as plain files under {@code <schematics-dir>/trahison_exports/<game>/}; no
 * database. Names are checked so that nothing leaves that folder.
 */
@Service
public class ExportStore {

	public static final String BAD_NAME = "ERROR_TRAHISON_EXPORT_BAD_NAME";
	public static final String NOT_FOUND = "ERROR_TRAHISON_EXPORT_NOT_FOUND";
	public static final String INVALID = "ERROR_TRAHISON_EXPORT_INVALID";
	/** 256 MiB per file: the combined file of the nine places can be big (D82). */
	public static final long MAX_FILE_BYTES = 256L * 1024 * 1024;

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,79}");

	public record Game(String game, List<String> files, Instant updatedAt) {
	}

	private final Path root;

	public ExportStore(@Value("${phantasmon.hub.schematics-dir:hub_schematics}") String schematicsDir) {
		this.root = Path.of(schematicsDir, "trahison_exports");
	}

	/** The games that have files, the latest first, each with its file names. */
	public List<Game> games() {
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		try (Stream<Path> dirs = Files.list(root)) {
			return dirs.filter(Files::isDirectory).map(d -> {
				try (Stream<Path> files = Files.list(d)) {
					List<String> names = files.filter(Files::isRegularFile).map(f -> f.getFileName().toString()).sorted().toList();
					return new Game(d.getFileName().toString(), names, Files.getLastModifiedTime(d).toInstant());
				} catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
			}).filter(g -> !g.files().isEmpty()).sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt())).toList();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Stores (or replaces) one file of a game. */
	public void write(String game, String name, byte[] bytes) {
		Path target = file(game, name);
		if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, INVALID, Map.of("size", bytes == null ? 0 : bytes.length, "max", MAX_FILE_BYTES));
		}
		try {
			Files.createDirectories(target.getParent());
			Path temp = Files.createTempFile(target.getParent(), "up", ".tmp");
			Files.write(temp, bytes);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public byte[] read(String game, String name) {
		try {
			return Files.readAllBytes(file(game, name));
		} catch (NoSuchFileException ex) {
			throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND, Map.of("game", game, "name", name));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Decodes a base64 upload, refusing a text that would decode past the limit before decoding it. */
	public byte[] decode(String base64) {
		if (base64 == null || base64.isEmpty() || base64.length() / 4L * 3 > MAX_FILE_BYTES + 3) {
			throw new ApiException(HttpStatus.BAD_REQUEST, INVALID, Map.of("max", MAX_FILE_BYTES));
		}
		try {
			return Base64.getDecoder().decode(base64);
		} catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, INVALID, Map.of("reason", "not base64"));
		}
	}

	private Path file(String game, String name) {
		if (game == null || name == null || !NAME.matcher(game).matches() || !NAME.matcher(name).matches()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, BAD_NAME, Map.of());
		}
		return root.resolve(game).resolve(name);
	}
}
