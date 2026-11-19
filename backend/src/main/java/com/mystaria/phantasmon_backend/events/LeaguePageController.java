package com.mystaria.phantasmon_backend.events;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The league's page ({@code /league}) of the public site. Its content (how the league works, gyms, Elite Four,
 * members, notes on the fields) comes from {@code site/ligue.json} in the public cloud share, so it can be edited in
 * Nextcloud without a deploy; the copy in the jar ({@code public/league.json}) is used when the cloud has none or an
 * invalid one. The field wiki ({@code /league/fields}) is built from the mod's data (tools/league_fields_site.py).
 */
@RestController
public class LeaguePageController {

	private static final Duration KEPT = Duration.ofMinutes(1);

	private final String cloudUrl;
	private final JsonMapper json = JsonMapper.builder().build();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private volatile String cached;
	private volatile Instant until = Instant.EPOCH;

	public LeaguePageController(
			@Value("${phantasmon.downloads.share-base:https://server-linux.tail192969.ts.net:10000}") String shareBase,
			@Value("${phantasmon.downloads.share-hash:GZ8ZJDgR}") String shareHash) {
		this.cloudUrl = shareBase + "/api/public/dl/" + shareHash + "/site/ligue.json";
	}

	@GetMapping({ "/league", "/league/" })
	public ResponseEntity<String> page() throws IOException {
		return SitePages.page("league.html");
	}

	@GetMapping("/league/data")
	public ResponseEntity<String> data() throws IOException {
		if (cached == null || Instant.now().isAfter(until)) {
			String fromCloud = fetch();
			cached = fromCloud != null ? fromCloud : read("public/league.json");
			until = Instant.now().plus(KEPT);
		}
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noCache()).body(cached);
	}

	@GetMapping("/league/fields")
	public ResponseEntity<String> fields() throws IOException {
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
				.body(read("public/league-fields.json"));
	}

	/** The cloud's league file if it is there and valid JSON; null otherwise (a typo must not break the page). */
	private String fetch() {
		try {
			HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(cloudUrl)).timeout(Duration.ofSeconds(8)).GET().build(),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (res.statusCode() != 200) {
				return null;
			}
			json.readTree(res.body());
			return res.body();
		} catch (IOException | JacksonException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private static String read(String path) throws IOException {
		try (InputStream in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
