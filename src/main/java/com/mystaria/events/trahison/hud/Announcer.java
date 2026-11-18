package com.mystaria.events.trahison.hud;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Shows the hostess's lines ({@code EventsTrahisonSay}, kind {@code announce}) at the top of the screen, typed letter by
 * letter (charte-ui.md sections 7 and 8), and the backend's refusals ({@code error}) as a short red toast above the
 * bracelet widget. Lines queue; each stays 3 s after its last letter, then fades. Nothing here is clickable. All timing
 * comes from the clock, never from a frame counter. Client thread.
 */
public final class Announcer {

	private static final long APPEAR_MS = 160;
	private static final long PUNCTUATION_PAUSE_MS = 300;
	private static final long HOLD_MS = 3000;
	private static final long FADE_MS = 250;
	private static final long TOAST_MS = 4000;
	private static final int MAX_QUEUED = 4;

	/** A line being typed: the host's name (shown at once) and the body (typed). */
	private static final class Line {
		final String host;
		final String body;
		/** Time at which each character of the body is shown, ms from the start. */
		final long[] at;
		final long start = System.currentTimeMillis();
		java.util.List<String> wrapped;
		int wrappedWidth;

		Line(String host, String body) {
			this.host = host;
			this.body = body;
			this.at = new long[body.length()];
			long t = 0;
			long per = Math.round(1000f / TrahisonUi.TYPEWRITER_CPS);
			for (int i = 0; i < body.length(); i++) {
				at[i] = t;
				t += per;
				char c = body.charAt(i);
				boolean end = i + 1 >= body.length() || body.charAt(i + 1) == ' ';
				if (end && (c == '.' || c == '!' || c == '?')) {
					t += PUNCTUATION_PAUSE_MS;
				}
			}
		}

		long typedMs() {
			return at.length == 0 ? 0 : at[at.length - 1] + Math.round(1000f / TrahisonUi.TYPEWRITER_CPS);
		}

		int visible(long elapsed) {
			int n = 0;
			while (n < at.length && at[n] <= elapsed) {
				n++;
			}
			return n;
		}

		long lifetimeMs() {
			return typedMs() + HOLD_MS + FADE_MS;
		}
	}

	private static final ArrayDeque<Line> QUEUE = new ArrayDeque<>();
	private static Line current;
	private static Component toast;
	private static long toastAt;
	private static Field bossEvents;
	private static boolean bossLookupDone;

	private Announcer() {
	}

	// ---------------------------------------------------------------- input

	/** An {@code EventsTrahisonSay} message. */
	public static void onSay(Map<String, Object> data) {
		String kind = String.valueOf(data.get("kind"));
		if ("announce".equals(kind) && data.get("key") != null) {
			say(lineOf(data));
		} else if ("whisper".equals(kind) && data.get("key") != null) {
			// T7: a role at the prologue, an anonymous letter: private, said in the hostess's voice.
			say(lineOf(data));
		} else if ("error".equals(kind) && data.get("code") != null) {
			String code = String.valueOf(data.get("code"));
			error(Component.translatableWithFallback("phantasmon_events.error." + code.toLowerCase(Locale.ROOT), code));
			// A refusal while a puzzle screen is open: the screen shakes and sounds the error (its gesture was refused).
			Minecraft.getInstance().execute(() -> {
				if (Minecraft.getInstance().screen instanceof com.mystaria.events.trahison.minigame.MiniGameScreen puzzle) {
					puzzle.fail();
				}
			});
		}
	}

	/**
	 * The text of a {@code say} message: the variant the backend chose among those of the texts file
	 * ({@link AnnouncerTexts}), or, when the moment is not in the file, the language key.
	 */
	static Component lineOf(Map<String, Object> data) {
		String key = String.valueOf(data.get("key"));
		java.util.List<String> args = data.get("args") instanceof java.util.List<?> list ? list.stream().map(String::valueOf).toList() : java.util.List.of();
		int variant = data.get("variant") instanceof Number n ? n.intValue() : 0;
		return moment(key, variant, args);
	}

	/** A line by language key and variant number; falls back to the language key when the texts file lacks it. */
	public static Component moment(String key, int variant, java.util.List<String> args) {
		String moment = AnnouncerTexts.momentOf(key);
		if (moment != null) {
			String text = AnnouncerTexts.forLanguage(Minecraft.getInstance().getLanguageManager().getSelected()).line(moment, variant, args);
			if (text != null) {
				return Component.literal(text);
			}
		}
		return Component.translatable(key, args.toArray());
	}

	/** Queues a line of the hostess; her name (the text before the first colon) is shown in the accent colour. */
	public static void say(Component text) {
		String raw = text.getString();
		int colon = raw.indexOf(':');
		String host = colon > 0 && colon <= 24 ? raw.substring(0, colon).trim() : "";
		String body = (colon > 0 && colon <= 24 ? raw.substring(colon + 1) : raw).trim();
		while (QUEUE.size() >= MAX_QUEUED) {
			QUEUE.pollFirst();
		}
		QUEUE.addLast(new Line(host, body));
	}

	/** Shows a short refusal, replacing the previous one. */
	public static void error(Component text) {
		toast = text;
		toastAt = System.currentTimeMillis();
	}

	public static void clear() {
		QUEUE.clear();
		current = null;
		toast = null;
	}

	// ---------------------------------------------------------------- drawing

	/** Draws the current line and the error toast. {@code hudBottom}: the top of the bracelet widget, so the toast sits above it. */
	public static void render(GuiGraphics g, int screenW, int screenH, int hudBottom) {
		long now = System.currentTimeMillis();
		if (current != null && now - current.start >= current.lifetimeMs()) {
			current = null;
		}
		if (current == null && !QUEUE.isEmpty()) {
			current = QUEUE.pollFirst();
		}
		if (current != null) {
			drawLine(g, current, now, screenW);
		}
		if (toast != null) {
			if (now - toastAt >= TOAST_MS + FADE_MS) {
				toast = null;
			} else {
				drawToast(g, now, screenW, hudBottom);
			}
		}
	}

	private static void drawLine(GuiGraphics g, Line line, long now, int screenW) {
		long elapsed = now - line.start;
		float appear = TrahisonUi.easeOut(elapsed / (float) APPEAR_MS);
		long fadeStart = line.typedMs() + HOLD_MS;
		float fade = elapsed > fadeStart ? 1f - Math.min(1f, (elapsed - fadeStart) / (float) FADE_MS) : 1f;
		float alpha = appear * fade;
		if (alpha <= 0.01f) {
			return;
		}
		int a = Math.round(alpha * 255);
		int boxW = Math.max(120, Math.min(screenW - 2 * TrahisonUi.SAFE_MARGIN, Math.round(screenW * 0.6f)));
		int textW = boxW - 16;
		String prefix = line.host.isEmpty() ? "" : line.host + ": ";
		// The whole line is wrapped first (and once per width), so that words do not jump while it is typed.
		if (line.wrapped == null || line.wrappedWidth != textW) {
			line.wrapped = wrap(prefix + line.body, textW);
			line.wrappedWidth = textW;
		}
		int boxH = line.wrapped.size() * TrahisonUi.LINE + 8;
		int x = (screenW - boxW) / 2;
		int y = 24 + 12 * bossBars() + Math.round((1f - appear) * 6f);
		g.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, TrahisonUi.withAlpha(TrahisonPalette.BORDER, Math.round(a * 0.9f)));
		g.fill(x, y, x + boxW, y + boxH, TrahisonUi.withAlpha(TrahisonPalette.BG_DEEP, Math.round(a * 0.7f)));
		g.fill(x, y, x + 2, y + boxH, TrahisonUi.withAlpha(TrahisonPalette.ACCENT, a));
		// The host's name is there at once; the body is typed.
		int budget = prefix.length() + line.visible(elapsed);
		int accentLeft = prefix.length();
		for (int i = 0; i < line.wrapped.size() && budget > 0; i++) {
			String text = line.wrapped.get(i);
			String shown = text.substring(0, Math.min(text.length(), budget));
			budget -= text.length();
			net.minecraft.network.chat.MutableComponent part = Component.empty();
			int accent = Math.min(accentLeft, shown.length());
			accentLeft -= accent;
			if (accent > 0) {
				part.append(Component.literal(shown.substring(0, accent)).withColor(TrahisonPalette.ACCENT & 0xFFFFFF));
			}
			if (accent < shown.length()) {
				part.append(Component.literal(shown.substring(accent)).withColor(TrahisonPalette.TEXT & 0xFFFFFF));
			}
			MenuUi.paragraph(g, part, x + 8, y + 4 + i * TrahisonUi.LINE, textW + 8, TrahisonUi.withAlpha(TrahisonPalette.TEXT, a));
		}
	}

	/** Word-wraps {@code text} to {@code width} in the menu's font. */
	private static java.util.List<String> wrap(String text, int width) {
		net.minecraft.network.chat.Style uniform = net.minecraft.network.chat.Style.EMPTY.withFont(net.minecraft.resources.ResourceLocation.parse("uniform"));
		java.util.List<String> lines = new java.util.ArrayList<>();
		Minecraft.getInstance().font.getSplitter().splitLines(text, width, uniform).forEach(f -> lines.add(f.getString()));
		return lines.isEmpty() ? java.util.List.of(text) : lines;
	}

	private static void drawToast(GuiGraphics g, long now, int screenW, int hudBottom) {
		long elapsed = now - toastAt;
		float fade = elapsed > TOAST_MS ? 1f - (elapsed - TOAST_MS) / (float) FADE_MS : 1f;
		float appear = TrahisonUi.easeOut(elapsed / (float) APPEAR_MS);
		int a = Math.round(Math.max(0f, Math.min(1f, fade * appear)) * 255);
		int maxW = Math.min(screenW - 2 * TrahisonUi.SAFE_MARGIN, 260);
		int w = Math.min(maxW, MenuUi.width(toast) + 16);
		int h = MenuUi.paragraphHeight(toast, w - 16) + 8;
		int x = (screenW - w) / 2;
		int y = hudBottom - 4 - h + Math.round((1f - appear) * 6f);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TrahisonUi.withAlpha(TrahisonPalette.DANGER, a));
		g.fill(x, y, x + w, y + h, TrahisonUi.withAlpha(TrahisonPalette.DANGER_DARK, Math.round(a * 0.9f)));
		MenuUi.paragraph(g, toast, x + 8, y + 4, w - 16, TrahisonUi.withAlpha(TrahisonPalette.TEXT, a));
	}

	/** How many boss bars are showing (the line sits below them). Looked up by field type, which survives renaming. */
	private static int bossBars() {
		try {
			if (!bossLookupDone) {
				bossLookupDone = true;
				for (Field f : net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredFields()) {
					if (Map.class.isAssignableFrom(f.getType())) {
						f.setAccessible(true);
						bossEvents = f;
						break;
					}
				}
			}
			if (bossEvents != null) {
				return ((Map<?, ?>) bossEvents.get(Minecraft.getInstance().gui.getBossOverlay())).size();
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			bossEvents = null;
		}
		return 0;
	}
}
