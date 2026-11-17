package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.PushButton;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code type_chain}: levers marked with a type, to pull in the order "who beats whom" (each type must be strong against
 * the next). Like {@code three_levers} it is played <b>in the world</b> (a click on a lever slot sends {@code use_slot}), so no screen
 * opens on its own: this screen is the offline preview ({@code /phantasmon event trahison minigame type_chain}), and it also
 * follows a real view if one is routed to it. The marks and the sheet of who beats whom are documents of the room.
 *
 * <p>
 * View {@code {types, progress, wrong, solved}}; events {@code pull}, {@code wrong}, {@code unlock}. Offline: three levers, the
 * order is worked out locally from a fixed three type chain.
 */
public final class TypeChainScreen extends MiniGameScreen {

	/** Offline: lever marks, and the order of the chain (indices of levers): water beats fire beats grass. */
	private static final List<String> OFFLINE_TYPES = List.of("fire", "grass", "water");
	private static final int[] OFFLINE_ORDER = { 2, 0, 1 };

	private final List<String> types = new ArrayList<>();
	private final List<PushButton> levers = new ArrayList<>();
	private int progress;
	private int wrong = -1;

	public TypeChainScreen(String step, boolean offline) {
		super("type_chain", step, offline);
		if (offline) {
			types.addAll(OFFLINE_TYPES);
			wrong = 0;
			rebuild();
		}
	}

	private void rebuild() {
		clearWidgets();
		levers.clear();
		for (int i = 0; i < types.size(); i++) {
			final int at = i;
			levers.add(add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.type_chain.pull"), ButtonKind.PRIMARY, () -> pull(at))));
		}
		if (contentW > 0) {
			layout();
		}
	}

	private void pull(int lever) {
		if (!offline()) {
			return; // in the world the click is use_slot; there is nothing to send from here
		}
		if (OFFLINE_ORDER[progress] == lever) {
			progress++;
			if (progress == OFFLINE_ORDER.length) {
				succeed();
			}
		} else {
			progress = 0;
			wrong++;
			fail();
		}
	}

	@Override
	protected void layout() {
		int n = levers.size();
		if (n == 0) {
			return;
		}
		int gap = 6;
		int cw = Math.min(72, (contentW - 16 - (n - 1) * gap) / n);
		int left = contentX + (contentW - (n * cw + (n - 1) * gap)) / 2;
		for (int i = 0; i < n; i++) {
			levers.get(i).at(left + i * (cw + gap), contentY + contentH - 44, cw, 20);
		}
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		List<String> next = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("types"))) {
			next.add(String.valueOf(o));
		}
		if (!next.equals(types)) {
			types.clear();
			types.addAll(next);
			rebuild();
		}
		progress = PuzzleScreens.asInt(view.get("progress"), 0);
		int w = PuzzleScreens.asInt(view.get("wrong"), 0);
		if (wrong >= 0 && w > wrong) {
			fail();
		}
		wrong = w;
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		int n = types.size();
		if (n == 0) {
			return;
		}
		int gap = 6;
		int cw = Math.min(72, (contentW - 16 - (n - 1) * gap) / n);
		int left = contentX + (contentW - (n * cw + (n - 1) * gap)) / 2;
		int top = contentY + 8;
		int ph = Math.max(30, contentH - 44 - 8 - 12);
		for (int i = 0; i < n; i++) {
			int x = left + i * (cw + gap);
			g.fill(x, top, x + cw, top + ph, TrahisonPalette.EDGE);
			g.fill(x + 1, top + 1, x + cw - 1, top + ph - 1, TrahisonPalette.PANEL);
			Glyphs.draw(g, PokemonUi.typeGlyph(types.get(i)), x + cw / 2f, top + ph / 2f - 6, Math.min(24, ph - 20), 0, TrahisonPalette.ACCENT_HOT);
			MenuUi.centered(g, PokemonUi.typeName(types.get(i)), x + cw / 2, top + ph - 12, cw - 4, TrahisonPalette.TEXT);
		}
		int dotsW = n * 10;
		int dx = contentX + (contentW - dotsW) / 2;
		for (int i = 0; i < n; i++) {
			TrahisonUi.glyph(g, TrahisonUi.Glyph.CIRCLE, dx + i * 10, contentY + contentH - 16, 6, i < progress ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_DISABLED);
		}
		if (wrong > 0) {
			MenuUi.text(g, Component.translatable("phantasmon_events.trahison.minigame.type_chain.wrong", wrong), contentX + 8, contentY + contentH - 14, 110, TrahisonPalette.TEXT_MUTED);
		}
	}
}
