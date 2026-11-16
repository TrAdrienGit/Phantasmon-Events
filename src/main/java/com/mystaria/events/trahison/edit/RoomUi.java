package com.mystaria.events.trahison.edit;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.ui.TrahisonPalette;

/** Small helpers shared by the room editor's two screens ({@link RoomScreen}, {@link ChainScreen}). */
final class RoomUi {

	static final String T = "phantasmon_events.trahison.room_edit.";

	private RoomUi() {
	}

	static Component t(String key, Object... args) {
		return Component.translatable(T + key, args);
	}

	static int clamp(int scroll, int overflow) {
		return Math.max(0, Math.min(scroll, overflow));
	}

	/** A thin scroll thumb at the right of a clipped area, drawn only when the content overflows. */
	static void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int content) {
		if (content <= h) {
			return;
		}
		int thumb = Math.max(12, h * h / content);
		int top = y + (h - thumb) * scroll / Math.max(1, content - h);
		g.fill(x, top, x + 2, top + thumb, TrahisonPalette.EDGE_LIGHT);
	}

	/** The next (or previous) of {@code options} after {@code current}; the first one when {@code current} is not in it. */
	static String step(List<String> options, String current, int dir) {
		if (options.isEmpty()) {
			return current;
		}
		int at = options.indexOf(current);
		if (at < 0) {
			return options.get(dir >= 0 ? 0 : options.size() - 1);
		}
		return options.get(Math.floorMod(at + dir, options.size()));
	}

	/** A problem as one sentence (the backend's codes and the editor's {@code LOCAL_*} ones share the lang keys). */
	static Component issueText(RoomChecks.Issue issue) {
		return Component.translatableWithFallback(T + "error." + issue.code().toLowerCase(Locale.ROOT), issue.code(),
				String.join(", ", issue.ids()), issue.info());
	}

	static Component pos(RoomDto.P p) {
		return t("pos", p.x, p.y, p.z);
	}

	static Component box(RoomDto.SlotDto slot) {
		return t("box", slot.position.x, slot.position.y, slot.position.z, slot.size.x, slot.size.y, slot.size.z);
	}

	static Component orientation(String orientation) {
		return t("orientation." + orientation);
	}

	static Component kind(String kind) {
		return t("kind." + kind);
	}

	static Component mechanism(String id) {
		return Component.translatableWithFallback(T + "mechanism." + id, id);
	}
}
