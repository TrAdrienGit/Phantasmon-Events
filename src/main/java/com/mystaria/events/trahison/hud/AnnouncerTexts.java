package com.mystaria.events.trahison.hud;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * T9.4: the hostess's lines in their own editable file, {@code assets/phantasmon_events/trahison/animateur/<lang>.json}
 * (Documentation/trahison/textes-animateur.md). Each moment ({@code phase.vote}, {@code ending.solo}...) has one or more
 * variants; the backend sends a stable {@code variant} number with every line and the client takes it modulo the number of
 * variants, so everybody hears the same one. The same file holds the numbered lore fragments ({@code @fragment:<n>} in a
 * document). Pure Java (no Minecraft class), so it is unit tested. A moment or fragment missing from the file is simply not
 * resolved here: the caller falls back to the language key.
 */
public final class AnnouncerTexts {

	/** The prefix the backend puts before a moment in the language keys. */
	public static final String KEY_PREFIX = "phantasmon_events.trahison.say.";

	private static final String RESOURCE = "/assets/phantasmon_events/trahison/animateur/%s.json";
	private static final Map<String, AnnouncerTexts> CACHE = new HashMap<>();

	private final Map<String, List<String>> lines;
	private final Map<String, String> fragments;
	private final String placeholder;

	AnnouncerTexts(Map<String, List<String>> lines, Map<String, String> fragments) {
		this(lines, fragments, "Fragment %s");
	}

	AnnouncerTexts(Map<String, List<String>> lines, Map<String, String> fragments, String placeholder) {
		this.lines = lines;
		this.fragments = fragments;
		this.placeholder = placeholder;
	}

	/** The texts of a language code ({@code fr_fr}, {@code en_us}); anything not French is English. Cached. */
	public static synchronized AnnouncerTexts forLanguage(String code) {
		String lang = code != null && code.toLowerCase(java.util.Locale.ROOT).startsWith("fr") ? "fr_fr" : "en_us";
		return CACHE.computeIfAbsent(lang, AnnouncerTexts::load);
	}

	/** Forgets the loaded files (the next call reads them again). */
	public static synchronized void reload() {
		CACHE.clear();
	}

	private static AnnouncerTexts load(String lang) {
		try (InputStream in = AnnouncerTexts.class.getResourceAsStream(RESOURCE.formatted(lang))) {
			String placeholder = "fr_fr".equals(lang) ? "Fragment %s — à écrire." : "Fragment %s — to be written.";
			if (in == null) {
				return new AnnouncerTexts(Map.of(), Map.of(), placeholder);
			}
			AnnouncerTexts read = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
			return new AnnouncerTexts(read.lines, read.fragments, placeholder);
		} catch (Exception e) {
			return new AnnouncerTexts(Map.of(), Map.of());
		}
	}

	/** Reads a texts file: {@code {"lines": {moment: [variants]}, "fragments": {id: text}}}. A bad entry is skipped. */
	static AnnouncerTexts parse(java.io.Reader reader) {
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		Map<String, List<String>> lines = new HashMap<>();
		if (root.get("lines") instanceof JsonObject o) {
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				List<String> variants = new ArrayList<>();
				if (e.getValue() instanceof JsonArray a) {
					a.forEach(x -> {
						if (x.isJsonPrimitive() && !x.getAsString().isBlank()) {
							variants.add(x.getAsString());
						}
					});
				} else if (e.getValue().isJsonPrimitive() && !e.getValue().getAsString().isBlank()) {
					variants.add(e.getValue().getAsString());
				}
				if (!variants.isEmpty()) {
					lines.put(e.getKey(), List.copyOf(variants));
				}
			}
		}
		Map<String, String> fragments = new HashMap<>();
		if (root.get("fragments") instanceof JsonObject o) {
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					fragments.put(e.getKey(), e.getValue().getAsString());
				}
			}
		}
		return new AnnouncerTexts(Map.copyOf(lines), Map.copyOf(fragments));
	}

	/** The moment of a language key ({@code ...say.phase.vote} gives {@code phase.vote}), or null for any other key. */
	public static String momentOf(String key) {
		return key != null && key.startsWith(KEY_PREFIX) ? key.substring(KEY_PREFIX.length()) : null;
	}

	public boolean has(String moment) {
		return lines.containsKey(moment);
	}

	public int variants(String moment) {
		List<String> v = lines.get(moment);
		return v == null ? 0 : v.size();
	}

	/** The moments this file knows. */
	public java.util.Set<String> moments() {
		return lines.keySet();
	}

	/**
	 * The text of a moment for a variant number (taken modulo the number of variants; a negative number counts as 0), with the
	 * {@code %1$s}-style arguments filled in; null when the moment is not in the file.
	 */
	public String line(String moment, int variant, List<String> args) {
		List<String> v = lines.get(moment);
		if (v == null) {
			return null;
		}
		String text = v.get(Math.floorMod(Math.max(variant, 0), v.size()));
		try {
			return String.format(text, (Object[]) (args == null ? new String[0] : args.toArray()));
		} catch (java.util.IllegalFormatException e) {
			return text; // a malformed placeholder in a hand-edited file must not hide the line
		}
	}

	/** The text of lore fragment {@code id}, or null when the file has none. */
	public String fragment(String id) {
		return fragments.get(id);
	}

	/** The text of lore fragment {@code id}; a fragment the file does not have yet reads "Fragment n — to be written". */
	public String fragmentOrPlaceholder(String id) {
		String text = fragments.get(id);
		return text == null || text.isBlank() ? placeholder.formatted(id) : text;
	}
}
