package com.mystaria.events.trahison.minigame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.widget.PushButton;
import com.mystaria.events.trahison.minigame.widget.SpeciesCard;
import com.mystaria.events.trahison.minigame.widget.Widget;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * Mechanism {@code cry_match}: a console that plays cries, to pair with the Pokemon shown below (no name is written: the models are
 * the only clue, the screen is not allowed to say who is who). Click a cry button to hear it and select it, then click the Pokemon it
 * belongs to: the Pokemon gets that cry's number. Click a numbered Pokemon with no cry selected to take its number back. When every
 * cry has a Pokemon, Confirm sends the pairing; the answer is how many pairs are right, never which.
 *
 * <p>
 * Gesture {@code {match: [card index per cry]}}. View {@code {cries, cards, wrong, last_correct, last_match, solved}} (species ids; the cries
 * are the sounds {@code cobblemon:pokemon.<id>.cry}). Offline: three Pokemon, a local check.
 */
public final class CryMatchScreen extends MiniGameScreen {

	private static final List<String> OFFLINE_CRIES = List.of("pikachu", "eevee", "snorlax");
	private static final List<String> OFFLINE_CARDS = List.of("snorlax", "pikachu", "eevee");
	private static final int GAP = 6;

	/** The cry button: shows its number, lit while selected, a check mark once it has a Pokemon. */
	private final class CryButton extends Widget {
		private final int index;
		private boolean down;

		CryButton(int index) {
			this.index = index;
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (button != 0 || !hovered(mx, my)) {
				return false;
			}
			down = true;
			return true;
		}

		@Override
		public boolean mouseReleased(double mx, double my, int button) {
			if (!down) {
				return false;
			}
			down = false;
			if (hovered(mx, my)) {
				chooseCry(index);
			}
			return true;
		}

		@Override
		public void render(GuiGraphics g, int mx, int my) {
			boolean hot = hovered(mx, my);
			ButtonKind kind = selectedCry == index ? ButtonKind.PRIMARY : ButtonKind.DEFAULT;
			TrahisonUi.button(g, x, y, w, h, Component.translatable("phantasmon_events.trahison.minigame.cry_match.cry", index + 1), kind,
					TrahisonUi.stateOf(enabled, hot, down && hot));
			if (assigned(index)) {
				TrahisonUi.glyph(g, TrahisonUi.Glyph.CIRCLE, x + w - 11, y + h / 2 - 3, 6, TrahisonPalette.SUCCESS);
			}
		}
	}

	private final List<String> cries = new ArrayList<>();
	private final List<String> cardSpecies = new ArrayList<>();
	private final List<CryButton> cryButtons = new ArrayList<>();
	private final List<SpeciesCard> cards = new ArrayList<>();
	/** For each card, the cry (0-based) placed on it, -1 for none. */
	private int[] cardCry = new int[0];
	private int selectedCry = -1;
	private PushButton confirm;
	private int wrong = -1;
	private int lastCorrect = -1;

	public CryMatchScreen(String step, boolean offline) {
		super("cry_match", step, offline);
		if (offline) {
			cries.addAll(OFFLINE_CRIES);
			cardSpecies.addAll(OFFLINE_CARDS);
			wrong = 0;
			rebuild();
		}
	}

	private void rebuild() {
		clearWidgets();
		cryButtons.clear();
		cards.clear();
		int n = cardSpecies.size();
		cardCry = new int[n];
		Arrays.fill(cardCry, -1);
		selectedCry = -1;
		for (int i = 0; i < n; i++) {
			final int at = i;
			cryButtons.add(add(new CryButton(i)));
			cards.add(add(new SpeciesCard(() -> chooseCard(at)).species(cardSpecies.get(i))));
		}
		confirm = add(new PushButton(() -> Component.translatable("phantasmon_events.trahison.minigame.cry_match.confirm"), ButtonKind.PRIMARY, this::confirmMatch));
		refresh();
		if (contentW > 0) {
			layout();
		}
	}

	private boolean assigned(int cry) {
		for (int c : cardCry) {
			if (c == cry) {
				return true;
			}
		}
		return false;
	}

	private boolean complete() {
		for (int i = 0; i < cries.size(); i++) {
			if (!assigned(i)) {
				return false;
			}
		}
		return !cries.isEmpty();
	}

	private void refresh() {
		for (int i = 0; i < cards.size(); i++) {
			cards.get(i).badge(cardCry[i] >= 0 ? Integer.toString(cardCry[i] + 1) : "");
		}
	}

	private void chooseCry(int cry) {
		selectedCry = cry;
		MiniGameSounds.cry(cries.get(cry));
	}

	private void chooseCard(int card) {
		if (selectedCry < 0) {
			cardCry[card] = -1;
		} else {
			for (int i = 0; i < cardCry.length; i++) {
				if (cardCry[i] == selectedCry) {
					cardCry[i] = -1;
				}
			}
			cardCry[card] = selectedCry;
			int next = -1;
			for (int k = 1; k <= cries.size() && next < 0; k++) {
				int cand = (selectedCry + k) % cries.size();
				if (!assigned(cand)) {
					next = cand;
				}
			}
			selectedCry = next;
		}
		refresh();
	}

	private void confirmMatch() {
		if (!complete()) {
			fail();
			return;
		}
		int[] match = new int[cries.size()];
		for (int card = 0; card < cardCry.length; card++) {
			match[cardCry[card]] = card;
		}
		List<Integer> list = new ArrayList<>();
		for (int m : match) {
			list.add(m);
		}
		sendGesture(Map.of("match", list));
		if (offline()) {
			int correct = 0;
			for (int cry = 0; cry < match.length; cry++) {
				correct += cardSpecies.get(match[cry]).equals(cries.get(cry)) ? 1 : 0;
			}
			lastCorrect = correct;
			if (correct == match.length) {
				succeed();
			} else {
				wrong++;
				fail();
			}
		}
	}

	@Override
	protected void layout() {
		int n = cards.size();
		if (n == 0) {
			return;
		}
		int cw = Math.min(88, (contentW - 16 - (n - 1) * GAP) / n);
		int left = contentX + (contentW - (n * cw + (n - 1) * GAP)) / 2;
		int ch = Math.min(96, contentH - 4 - 20 - 6 - 6 - 18 - 4);
		for (int i = 0; i < n; i++) {
			cryButtons.get(i).at(left + i * (cw + GAP), contentY + 4, cw, 20);
			cards.get(i).at(left + i * (cw + GAP), contentY + 4 + 20 + 6, cw, ch);
		}
		confirm.at(contentX + contentW - 8 - 96, contentY + contentH - 22, 96, 18);
	}

	@Override
	protected void applyView(Map<String, Object> view) {
		List<String> newCries = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("cries"))) {
			newCries.add(String.valueOf(o));
		}
		List<String> newCards = new ArrayList<>();
		for (Object o : PuzzleScreens.asList(view.get("cards"))) {
			newCards.add(String.valueOf(o));
		}
		if (!newCries.equals(cries) || !newCards.equals(cardSpecies)) {
			cries.clear();
			cries.addAll(newCries);
			cardSpecies.clear();
			cardSpecies.addAll(newCards);
			rebuild();
		}
		int w = PuzzleScreens.asInt(view.get("wrong"), 0);
		if (wrong >= 0 && w > wrong) {
			fail();
		}
		wrong = w;
		lastCorrect = PuzzleScreens.asInt(view.get("last_correct"), -1);
	}

	@Override
	protected void onRemote(int player, List<Map<String, Object>> events) {
		if (confirm != null) {
			confirm.flash(300);
			remoteTouch(player, confirm.centerX() + 14, confirm.centerY() - 8);
		}
	}

	@Override
	protected void simulateRemote(int player) {
		if (!cryButtons.isEmpty()) {
			Widget b = cryButtons.get((int) (System.currentTimeMillis() / 3200 % cryButtons.size()));
			remoteTouch(player, b.centerX() + 14, b.centerY() - 8);
		}
	}

	@Override
	protected void drawUnder(GuiGraphics g, int mx, int my) {
		int y = contentY + contentH - 14;
		Component line;
		int color = TrahisonPalette.TEXT_MUTED;
		if (lastCorrect >= 0 && !isSolved()) {
			line = Component.translatable("phantasmon_events.trahison.minigame.cry_match.result", lastCorrect, Math.max(cries.size(), 1));
			color = TrahisonPalette.TEXT_SECONDARY;
		} else if (selectedCry >= 0) {
			line = Component.translatable("phantasmon_events.trahison.minigame.cry_match.pick_card", selectedCry + 1);
			color = TrahisonPalette.ACCENT_HOT;
		} else {
			line = Component.translatable("phantasmon_events.trahison.minigame.cry_match.pick_cry");
		}
		MenuUi.text(g, line, contentX + 8, y, contentW - 120, color);
	}

	@Override
	protected boolean key(int key) {
		if (key >= 49 && key < 49 + cryButtons.size()) { // 1..n
			chooseCry(key - 49);
			return true;
		}
		if (key == 257 || key == 335) {
			confirm.activate();
			return true;
		}
		return false;
	}
}
