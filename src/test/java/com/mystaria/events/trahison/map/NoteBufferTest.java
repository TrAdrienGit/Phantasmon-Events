package com.mystaria.events.trahison.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mystaria.events.trahison.map.NoteBuffer.Line;

/** Width: 1 pixel per character, so a wrap width of 10 is ten characters. */
class NoteBufferTest {

	private static NoteBuffer buffer(String text) {
		NoteBuffer b = new NoteBuffer(50, String::length);
		b.set(text);
		return b;
	}

	@Test
	void typingBackspaceAndDeleteWorkAtTheCaret() {
		NoteBuffer b = buffer("");
		"abc".chars().forEach(c -> b.insert((char) c));
		b.left();
		b.insert('X');
		assertEquals("abXc", b.text());
		assertTrue(b.backspace());
		assertTrue(b.delete());
		assertEquals("ab", b.text());
		assertFalse(b.delete());
	}

	@Test
	void controlCharactersAreRefusedButNewlinesAreKept() {
		NoteBuffer b = buffer("");
		assertFalse(b.insert((char) 7));
		assertTrue(b.insert('\n'));
		assertTrue(b.paste("a\r\nb\u0001"));
		assertEquals("\na\nb", b.text());
	}

	@Test
	void theTextIsLimited() {
		NoteBuffer b = new NoteBuffer(3, String::length);
		assertTrue(b.paste("abcdef"));
		assertEquals("abc", b.text());
		assertFalse(b.insert('d'));
	}

	@Test
	void linesWrapAtSpacesAndInsideTooLongWords() {
		List<Line> lines = buffer("hello brave new world").lines(10);
		assertEquals(List.of("hello ", "brave new ", "world"), lines.stream().map(l -> "hello brave new world".substring(l.start(), l.end())).toList());
		List<Line> word = buffer("abcdefghijklmnop").lines(10);
		assertEquals(2, word.size());
		assertEquals(10, word.get(0).end());
	}

	@Test
	void hardBreaksStartNewLinesAndAnEmptyTextHasOneLine() {
		assertEquals(1, buffer("").lines(10).size());
		assertEquals(3, buffer("a\n\nb").lines(10).size());
	}

	@Test
	void upAndDownKeepTheColumn() {
		NoteBuffer b = buffer("abcdef\nxy\nghijkl");
		b.click(0, 4, 20);
		assertEquals(4, b.caret());
		b.vertical(1, 20); // the short line: end of it
		assertEquals(9, b.caret());
		b.vertical(1, 20); // back to column 4
		assertEquals(14, b.caret());
		b.vertical(5, 20);
		assertEquals(b.text().length(), b.caret());
	}

	@Test
	void homeAndEndGoToTheEdgesOfTheDisplayedLine() {
		NoteBuffer b = buffer("hello brave new world");
		b.click(1, 3, 10); // "brave new " is the second line
		b.home(10);
		assertEquals(6, b.caret());
		b.end(10);
		assertEquals(16, b.caret());
	}
}
