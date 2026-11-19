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
 * Players' faces and skins for the public site. {@code /public/heads/<username>}: a 64 × 64 PNG of the skin's face with
 * its hat layer (tournaments, 2026-11-11). {@code /public/skins/<username>}: the whole skin seen from the front, flat,
 * 128 × 256 with its outer layer (the league's page, 2026-11-19). The backend asks Mojang itself (profile, then skin) and
 * keeps each skin 6 hours: the pages' visitors send nothing to anyone else. A player Phantasmon does not know is looked
 * up by name at Mojang. 404 for an unknown player or a skin Mojang did not give (the page then shows the initial).
 */
@RestController
public class PlayerHeadController {

	private static final Logger log = LoggerFactory.getLogger(PlayerHeadController.class);
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Pattern SKIN_URL = Pattern.compile("\"SKIN\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern SLIM = Pattern.compile("\"model\"\\s*:\\s*\"slim\"");
	private static final Pattern TEXTURES = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
	private static final Duration KEPT = Duration.ofHours(6);
	/** A skin Mojang could not give: asked again after this. */
	private static final Duration RETRY = Duration.ofMinutes(5);
	private static final int SIZE = 64;
	private static final int BODY_SCALE = 8;

	private record Skin(BufferedImage image, boolean slim, byte[] face, byte[] body, Instant until) {
	}

	private final PlayerRepository players;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private final Map<String, Skin> skins = new ConcurrentHashMap<>();

	public PlayerHeadController(PlayerRepository players) {
		this.players = players;
	}

	@GetMapping("/public/heads/{username}")
	public ResponseEntity<byte[]> head(@PathVariable String username) {
		Skin skin = skin(username);
		return png(skin == null ? null : skin.face());
	}

	@GetMapping("/public/skins/{username}")
	public ResponseEntity<byte[]> body(@PathVariable String username) {
		Skin skin = skin(username);
		return png(skin == null ? null : skin.body());
	}

	private static ResponseEntity<byte[]> png(byte[] png) {
		if (png == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.maxAge(Duration.ofHours(1))).body(png);
	}

	private Skin skin(String username) {
		if (username == null || !USERNAME.matcher(username).matches()) {
			return null;
		}
		String key = username.toLowerCase(java.util.Locale.ROOT);
		Skin skin = skins.get(key);
		if (skin == null || Instant.now().isAfter(skin.until())) {
			skin = fetch(username);
			skins.put(key, skin);
		}
		return skin.image() == null ? null : skin;
	}

	/** The player's UUID: Phantasmon's own record, else Mojang's by name; null if neither knows them. */
	private UUID uuid(String username) throws Exception {
		UUID known = players.findFirstByLastUsernameIgnoreCaseOrderByLastSeenAtDesc(username).map(p -> p.getUuid()).orElse(null);
		if (known != null) {
			return known;
		}
		String profile = get("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + username);
		Matcher id = profile == null ? null : ID.matcher(profile);
		if (id == null || !id.find()) {
			return null;
		}
		String h = id.group(1);
		return UUID.fromString(h.substring(0, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16) + "-" + h.substring(16, 20) + "-" + h.substring(20));
	}

	/** Mojang's profile and skin, with the face and the front view drawn; an empty skin if any step fails. */
	private Skin fetch(String username) {
		Skin none = new Skin(null, false, null, null, Instant.now().plus(RETRY));
		try {
			UUID uuid = uuid(username);
			if (uuid == null) {
				return none;
			}
			String profile = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
			Matcher value = profile == null ? null : TEXTURES.matcher(profile);
			if (value == null || !value.find()) {
				return none;
			}
			String textures = new String(Base64.getDecoder().decode(value.group(1)), StandardCharsets.UTF_8);
			Matcher url = SKIN_URL.matcher(textures);
			if (!url.find()) {
				return none;
			}
			HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url.group(1).replace("http://", "https://")))
					.timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
			if (res.statusCode() != 200) {
				return none;
			}
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(res.body()));
			if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
				return none;
			}
			boolean slim = SLIM.matcher(textures).find();
			return new Skin(image, slim, encode(face(image)), encode(front(image, slim)), Instant.now().plus(KEPT));
		} catch (Exception ex) {
			log.info("No skin for player {}: {}", username, ex.toString());
			return none;
		}
	}

	private static BufferedImage face(BufferedImage image) {
		BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		int scale = SIZE / 8;
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				int pixel = blend(image.getRGB(8 + x, 8 + y) | 0xFF000000, image.getRGB(40 + x, 8 + y));
				fill(out, x * scale, y * scale, scale, pixel);
			}
		}
		return out;
	}

	/** The skin from the front, 16 × 32 pixels enlarged: head, body, arms (3 wide on a slim skin) and legs, outer layer on top. */
	private static BufferedImage front(BufferedImage image, boolean slim) {
		boolean legacy = image.getHeight() == 32;
		int arm = slim ? 3 : 4;
		BufferedImage out = new BufferedImage(16 * BODY_SCALE, 32 * BODY_SCALE, BufferedImage.TYPE_INT_ARGB);
		part(out, image, 4, 0, 8, 8, 8, 8, 40, 8, false, true);
		part(out, image, 4, 8, 8, 12, 20, 20, legacy ? -1 : 20, 36, false, true);
		part(out, image, 4 - arm, 8, arm, 12, 44, 20, legacy ? -1 : 44, 36, false, true);
		part(out, image, 4, 20, 4, 12, 4, 20, legacy ? -1 : 4, 36, false, true);
		if (legacy) {
			part(out, image, 12, 8, arm, 12, 44, 20, -1, 0, true, true);
			part(out, image, 8, 20, 4, 12, 4, 20, -1, 0, true, true);
		} else {
			part(out, image, 12, 8, arm, 12, 36, 52, 52, 52, false, true);
			part(out, image, 8, 20, 4, 12, 20, 52, 4, 52, false, true);
		}
		return out;
	}

	/** One part: its base (opaque) at (bx, by) of the skin, then its outer layer at (ox, oy) if any (ox < 0: none). */
	private static void part(BufferedImage out, BufferedImage skin, int dx, int dy, int w, int h, int bx, int by, int ox, int oy,
			boolean mirror, boolean opaque) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int sx = mirror ? w - 1 - x : x;
				int pixel = skin.getRGB(bx + sx, by + y) | (opaque ? 0xFF000000 : 0);
				if (ox >= 0) {
					pixel = blend(pixel, skin.getRGB(ox + sx, oy + y));
				}
				fill(out, (dx + x) * BODY_SCALE, (dy + y) * BODY_SCALE, BODY_SCALE, pixel);
			}
		}
	}

	private static void fill(BufferedImage out, int x0, int y0, int scale, int pixel) {
		for (int dy = 0; dy < scale; dy++) {
			for (int dx = 0; dx < scale; dx++) {
				out.setRGB(x0 + dx, y0 + dy, pixel);
			}
		}
	}

	private static byte[] encode(BufferedImage image) throws java.io.IOException {
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(image, "png", png);
		return png.toByteArray();
	}

	private String get(String url) throws Exception {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		return response.statusCode() == 200 ? response.body() : null;
	}

	/** The outer layer over the base, by its alpha. */
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
