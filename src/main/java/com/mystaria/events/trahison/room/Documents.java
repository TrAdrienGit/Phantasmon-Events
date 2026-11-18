package com.mystaria.events.trahison.room;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.room.RoomView.Clue;
import com.mystaria.events.trahison.room.RoomView.SlotView;
import com.mystaria.events.trahison.ui.TrahisonUi;

/**
 * The reading side of T3.3: turns what a slot shows (clues of every readable kind) into pages and opens the reading screen. A
 * {@code document} clue has pages of its own (one string per page; a line {@code @image:<id>} shows a picture, see
 * {@link Pictures}); any other readable clue (text, digits, symbols, colours, image) is one page. Nothing is asked of the
 * backend: what a player may read is already in the room message, and it never holds a solution. Client thread.
 */
public final class Documents {

	/** One page: its {@code kind} ({@code text}, {@code digits}, {@code symbols}, {@code colors}, {@code image}) and its lines. */
	public record Page(String kind, List<String> lines) {
	}

	private Documents() {
	}

	/** Opens the reading screen for everything {@code slot} lets a player read. */
	public static void open(RoomView room, SlotView slot) {
		List<Page> pages = pagesOf(slot.readable());
		if (pages.isEmpty()) {
			return;
		}
		Component title = Component.translatable("phantasmon_events.trahison.slot." + slot.kind());
		Minecraft.getInstance().setScreen(new com.mystaria.events.trahison.screen.DocumentScreen(title, pages));
	}

	/** The pages of a list of clues, in order. */
	public static List<Page> pagesOf(List<Clue> clues) {
		List<Page> pages = new ArrayList<>();
		for (Clue clue : clues) {
			String role = clue.role();
			if ("text".equals(clue.type()) && "wall_text".equals(role)) { // T8: a sentence in Zarbi, drawn with the pack's glyphs
				com.mystaria.events.trahison.minigame.PokemonUi.noteRead(role, clue.text());
				pages.add(new Page("zarbi", List.of(clue.text())));
				continue;
			}
			if ("document".equals(clue.type()) && "alphabet".equals(role)) { // T8: the Zarbi key, glyph above its letter
				pages.add(new Page("zarbi_key", List.of("ABCDEFGHIJKLM", "NOPQRSTUVWXYZ")));
				continue;
			}
			if ("text".equals(clue.type()) && (role.startsWith("portrait_") || role.startsWith("pedestal_"))) { // T8: a species shown by its model
				pages.add(new Page("species", List.of(clue.text())));
				continue;
			}
			if ("text".equals(clue.type()) && role.startsWith("mark_")) { // T8: a type mark
				pages.add(new Page("type", List.of(clue.text())));
				continue;
			}
			switch (clue.type()) {
				case "document" -> {
					for (String page : clue.list()) {
						pages.add(textPage(page));
					}
				}
				case "digits", "symbols", "colors" -> pages.add(new Page(clue.type(), clue.list()));
				case "image" -> pages.add(new Page("image", List.of(clue.text())));
				default -> pages.add(textPage(clue.text()));
			}
		}
		return pages;
	}

	private static final java.util.regex.Pattern COLOR = java.util.regex.Pattern.compile("@color:([a-z_]+)");

	/**
	 * A page of text: its lines, where a line {@code @image:<id>} is a picture and a token {@code @color:<id>} is the name of
	 * that wire colour in the player's language ({@code 1 : @color:red} reads "1 : Red"; D37, the wiring scheme as text).
	 */
	static Page textPage(String text) {
		List<String> lines = new ArrayList<>();
		for (String line : text.split("\n", -1)) {
			String id = fragmentOf(line);
			if (id != null) { // T9.4: a numbered lore fragment, whose text lives in the hostess texts file
				String lang = Minecraft.getInstance().getLanguageManager().getSelected();
				for (String l : com.mystaria.events.trahison.hud.AnnouncerTexts.forLanguage(lang).fragmentOrPlaceholder(id).split("\n", -1)) {
					lines.add(l);
				}
				continue;
			}
			lines.add(colorNames(line));
		}
		return new Page("text", lines);
	}

	/** Replaces every {@code @color:<id>} of a line by the colour's name; an unknown colour id is left as it is. */
	static String colorNames(String line) {
		if (!line.contains("@color:")) {
			return line;
		}
		return COLOR.matcher(line).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(
				TrahisonUi.WireColor.of(m.group(1)) == null ? m.group()
						: Component.translatable("phantasmon_events.trahison.color." + m.group(1)).getString()));
	}

	/** The fragment number of a line {@code @fragment:<n>} (T9.4), or null when the line is anything else. */
	public static String fragmentOf(String line) {
		String t = line.strip();
		if (!t.startsWith("@fragment:")) {
			return null;
		}
		String id = t.substring("@fragment:".length()).strip();
		return id.matches("[0-9]{1,4}") ? id : null;
	}

	/** The picture id of a line, or null when the line is plain text. */
	public static String imageOf(String line) {
		return line.startsWith("@image:") ? line.substring("@image:".length()).trim() : null;
	}
}
