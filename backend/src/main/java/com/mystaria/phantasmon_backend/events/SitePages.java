package com.mystaria.phantasmon_backend.events;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public site "Village 14": {@code /home}, {@code /tournament} and {@code /download}, under one shared header
 * ({@code public/site-header.html}). A page marks where it goes with
 * {@code <!--SITE_HEADER page="<name>"-->extra<!--/SITE_HEADER-->}: the header replaces it, its tab lit, the extra
 * (a breadcrumb, a live dot) at its end. The old addresses ({@code /t}, {@code /d}) lead to the new ones.
 */
@RestController
public class SitePages {

	private static final Pattern MARK = Pattern.compile("<!--SITE_HEADER page=\"([a-z]+)\"-->(.*?)<!--/SITE_HEADER-->", Pattern.DOTALL);

	/** The page {@code public/<file>} with the shared header in place. */
	public static ResponseEntity<String> page(String file) throws IOException {
		String html = read("public/" + file);
		Matcher m = MARK.matcher(html);
		if (m.find()) {
			String header = read("public/site-header.html")
					.replace("data-page=\"" + m.group(1) + "\"", "class=\"on\" data-page=\"" + m.group(1) + "\"")
					.replace("{{EXTRA}}", m.group(2));
			html = html.substring(0, m.start()) + header + html.substring(m.end());
		}
		return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noCache()).body(html);
	}

	private static String read(String path) throws IOException {
		try (InputStream in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@GetMapping({ "/", "/home", "/home/" })
	public ResponseEntity<String> home() throws IOException {
		return page("home.html");
	}

	/** The site's images: its logo (Phantasmon's icon, also the favicon) and Cobblemon's type icons. */
	@GetMapping("/site/{name}.png")
	public ResponseEntity<byte[]> image(@PathVariable String name) throws IOException {
		if (!name.equals("logo") && !name.equals("types")) {
			return ResponseEntity.notFound().build();
		}
		try (InputStream in = new ClassPathResource("public/" + name + ".png").getInputStream()) {
			return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(1)))
					.body(in.readAllBytes());
		}
	}

	// The old addresses, still shared on Discord.
	@GetMapping({ "/t", "/t/" })
	public ResponseEntity<Void> oldTournaments() {
		return moved("/tournament");
	}

	@GetMapping("/t/{name}")
	public ResponseEntity<Void> oldTournament(@PathVariable String name) {
		return moved("/tournament/" + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
	}

	@GetMapping({ "/d", "/d/" })
	public ResponseEntity<Void> oldDownloads() {
		return moved("/download");
	}

	@GetMapping("/d/latest/{kind}")
	public ResponseEntity<Void> oldLatest(@PathVariable String kind) {
		return moved("/download/latest/" + kind);
	}

	private static ResponseEntity<Void> moved(String to) {
		return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).location(URI.create(to)).build();
	}
}
