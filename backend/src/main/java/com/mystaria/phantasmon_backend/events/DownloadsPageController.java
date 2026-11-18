package com.mystaria.phantasmon_backend.events;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The public site's downloads tab ({@code /d}), next to the tournaments' tab ({@code /t}): the community's mods and
 * their optional dependencies. The newest Modrinth file is looked up by the page itself; our unpublished mods come from
 * the public File Browser share, where {@code /d/latest/<core|events>} redirects to the newest matching jar (or to the
 * share's page if none is found).
 */
@RestController
public class DownloadsPageController {

	/** What the share holds, by key: the file name's start and its extension. */
	private record Kind(String prefix, String extension) {
	}

	private static final Map<String, Kind> KINDS = Map.of(
			"core", new Kind("phantasmon-client-", ".jar"),
			"events", new Kind("phantasmon-events-", ".jar"),
			"showtime", new Kind("cobblemon-battle-showtime-", ".jar"),
			"phantasmon-pack", new Kind("phantasmon-resource-pack-", ".zip"),
			"showtime-pack", new Kind("cobblemon-battle-showtime-resource-pack-", ".zip"));
	private static final Duration KEPT = Duration.ofMinutes(1);

	private final String shareBase;
	private final String shareHash;
	private final JsonMapper json = JsonMapper.builder().build();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private volatile Listing listing;

	/** The newest file of each kind found in the share's root (older ones sit in "Anciennes versions"). */
	public record FileView(String name, String version, String modified) {
	}

	private record Listing(Map<String, FileView> files, Instant until) {
	}

	public DownloadsPageController(
			@Value("${phantasmon.downloads.share-base:https://server-linux.tail192969.ts.net:10000}") String shareBase,
			@Value("${phantasmon.downloads.share-hash:GZ8ZJDgR}") String shareHash) {
		this.shareBase = shareBase;
		this.shareHash = shareHash;
	}

	@GetMapping({ "/d", "/d/" })
	public ResponseEntity<String> page() throws IOException {
		try (InputStream in = new ClassPathResource("public/downloads.html").getInputStream()) {
			return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noCache())
					.body(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	/** The newest file of each kind, with its version and upload date, for the page's cards. */
	@GetMapping("/d/files")
	public ResponseEntity<Map<String, FileView>> files() {
		return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(current());
	}

	@GetMapping("/d/latest/{kind}")
	public ResponseEntity<Void> latest(@PathVariable String kind) {
		if (!KINDS.containsKey(kind)) {
			return ResponseEntity.notFound().build();
		}
		FileView file = current().get(kind);
		String url = file == null ? shareBase + "/share/" + shareHash
				: shareBase + "/api/public/dl/" + shareHash + "/" + URLEncoder.encode(file.name(), StandardCharsets.UTF_8).replace("+", "%20");
		return ResponseEntity.status(302).location(URI.create(url)).cacheControl(CacheControl.noStore()).build();
	}

	private Map<String, FileView> current() {
		Listing now = listing;
		if (now == null || Instant.now().isAfter(now.until())) {
			Map<String, FileView> files = read();
			// On error, the last listing is kept a minute more rather than emptying the page.
			now = new Listing(files != null ? files : now != null ? now.files() : Map.of(), Instant.now().plus(KEPT));
			listing = now;
		}
		return now.files();
	}

	/** The share's root, newest file of each kind; null on error. */
	private Map<String, FileView> read() {
		try {
			HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(shareBase + "/api/public/share/" + shareHash))
					.timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) {
				return null;
			}
			Map<String, FileView> found = new TreeMap<>();
			for (JsonNode item : json.readTree(res.body()).path("items")) {
				String name = item.path("name").asString("");
				String modified = item.path("modified").asString("");
				if (item.path("isDir").asBoolean(false) || name.endsWith("-sources.jar") || name.endsWith("-dev.jar")) {
					continue;
				}
				for (Map.Entry<String, Kind> kind : KINDS.entrySet()) {
					Kind k = kind.getValue();
					String version = name.startsWith(k.prefix()) && name.endsWith(k.extension())
							? name.substring(k.prefix().length(), name.length() - k.extension().length()) : null;
					// "cobblemon-battle-showtime-" also starts its pack's name: a version holds no letters.
					if (version == null || !version.matches("[0-9][0-9.]*")) {
						continue;
					}
					FileView best = found.get(kind.getKey());
					if (best == null || modified.compareTo(best.modified()) > 0) {
						found.put(kind.getKey(), new FileView(name, version, modified));
					}
				}
			}
			return found;
		} catch (IOException | JacksonException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}
}
