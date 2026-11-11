package com.mystaria.phantasmon_backend.events.tournament;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.mystaria.phantasmon_backend.player.PlayerRepository;

/**
 * Players' faces for the tournaments' public page (2026-11-11): {@code /public/heads/<username>}, a 64 × 64 PNG of the
 * skin's face with its hat layer, sharp. The backend asks Mojang itself (profile, then skin) for players Phantasmon
 * knows, and keeps each face 6 hours: the page's visitors send nothing to anyone else, and no UUID leaves the server.
 * 404 for an unknown player or a skin Mojang did not give (the page then shows the name's initial).
 */
@RestController
public class PlayerHeadController {

	private static final Logger log = LoggerFactory.getLogger(PlayerHeadController.class);
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Pattern SKIN_URL = Pattern.compile("\"SKIN\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern TEXTURES = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
	private static final Duration KEPT = Duration.ofHours(6);
	/** A face Mojang could not give: asked again after this. */
	private static final Duration RETRY = Duration.ofMinutes(5);
	private static final int SIZE = 64;

	private record Face(byte[] png, Instant until) {
	}

	private final PlayerRepository players;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private final Map<UUID, Face> faces = new ConcurrentHashMap<>();

	public PlayerHeadController(PlayerRepository players) {
		this.players = players;
	}

	@GetMapping("/public/heads/{username}")
	public ResponseEntity<byte[]> head(@PathVariable String username) {
		if (username == null || !USERNAME.matcher(username).matches()) {
			return ResponseEntity.notFound().build();
		}
		UUID uuid = players.findFirstByLastUsernameIgnoreCaseOrderByLastSeenAtDesc(username).map(p -> p.getUuid()).orElse(null);
		if (uuid == null) {
			return ResponseEntity.notFound().build();
		}
		Face face = faces.get(uuid);
		if (face == null || Instant.now().isAfter(face.until())) {
			byte[] png = fetch(uuid);
			face = new Face(png, Instant.now().plus(png == null ? RETRY : KEPT));
			faces.put(uuid, face);
		}
		if (face.png() == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
				.body(face.png());
	}

	/** Mojang's profile, its skin, then the face cut out and enlarged; null if any step fails. */
	private byte[] fetch(UUID uuid) {
		try {
			String profile = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
			Matcher value = profile == null ? null : TEXTURES.matcher(profile);
			if (value == null || !value.find()) {
				return null;
			}
			String textures = new String(Base64.getDecoder().decode(value.group(1)), StandardCharsets.UTF_8);
			Matcher url = SKIN_URL.matcher(textures);
			if (!url.find()) {
				return null;
			}
			HttpResponse<byte[]> skin = http.send(HttpRequest.newBuilder(URI.create(url.group(1).replace("http://", "https://")))
					.timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			if (skin.statusCode() != 200) {
				return null;
			}
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(skin.body()));
			if (image == null || image.getWidth() < 64) {
				return null;
			}
			BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
			int scale = SIZE / 8;
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 8; x++) {
					int base = image.getRGB(8 + x, 8 + y) | 0xFF000000;
					int hat = image.getRGB(40 + x, 8 + y);
					int pixel = blend(base, hat);
					for (int dy = 0; dy < scale; dy++) {
						for (int dx = 0; dx < scale; dx++) {
							out.setRGB(x * scale + dx, y * scale + dy, pixel);
						}
					}
				}
			}
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(out, "png", png);
			return png.toByteArray();
		} catch (Exception ex) {
			log.info("No face for player {}: {}", uuid, ex.toString());
			return null;
		}
	}

	private String get(String url) throws Exception {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		return response.statusCode() == 200 ? response.body() : null;
	}

	/** The hat layer over the face, by its alpha. */
	private static int blend(int base, int over) {
		int a = over >>> 24;
		if (a == 0) {
			return base;
		}
		if (a == 255) {
			return over;
		}
		int r = (((over >> 16) & 0xFF) * a + ((base >> 16) & 0xFF) * (255 - a)) / 255;
		int g = (((over >> 8) & 0xFF) * a + ((base >> 8) & 0xFF) * (255 - a)) / 255;
		int b = ((over & 0xFF) * a + (base & 0xFF) * (255 - a)) / 255;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
