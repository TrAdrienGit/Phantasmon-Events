package com.mystaria.events.trahison.room;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.room.RoomView.Clue;
import com.mystaria.events.trahison.room.RoomView.SlotView;

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

	/** A page of text: its lines, where a line {@code @image:<id>} is a picture. */
	static Page textPage(String text) {
		List<String> lines = new ArrayList<>();
		for (String line : text.split("\n", -1)) {
			lines.add(line);
		}
		return new Page("text", lines);
	}

	/** The picture id of a line, or null when the line is plain text. */
	public static String imageOf(String line) {
		return line.startsWith("@image:") ? line.substring("@image:".length()).trim() : null;
	}
}
