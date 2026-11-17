package com.mystaria.events.trahison.minigame.widget;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.minigame.MiniGameSounds;
import com.mystaria.events.trahison.minigame.PokemonUi;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * A card of a Pokemon screen: the species' model on top, then up to a few lines of text. It can be selected (one click picks it, the
 * next card clicked acts with it), locked (a darker frame with a lock mark, never clickable), flashed (someone else touched it) and
 * numbered (the order the cries are heard in). Acts on release inside, like {@link PushButton}.
 */
public final class SpeciesCard extends Widget {

	private final Runnable action;
	private String species = "";
	private List<Component> lines = List.of();
	private boolean selected;
	private boolean locked;
	private boolean down;
	private long flashUntil;
	private String badge = "";
	private boolean modelHidden;

	public SpeciesCard(Runnable action) {
		this.action = action;
	}

	public SpeciesCard species(String id) {
		this.species = id == null ? "" : id;
		return this;
	}

	public String species() {
		return species;
	}

	public SpeciesCard lines(List<Component> lines) {
		this.lines = lines;
		return this;
	}

	public SpeciesCard selected(boolean selected) {
		this.selected = selected;
		return this;
	}

	public SpeciesCard locked(boolean locked) {
		this.locked = locked;
		return this;
	}

	/** A small label in the top left corner (a number), empty for none. */
	public SpeciesCard badge(String badge) {
		this.badge = badge;
		return this;
	}

	/** Draws a plain tile with a question mark instead of the model (a card whose species must stay unknown). */
	public SpeciesCard modelHidden(boolean hidden) {
		this.modelHidden = hidden;
		return this;
	}

	public boolean isLocked() {
		return locked;
	}

	public void flash(long ms) {
		flashUntil = System.currentTimeMillis() + ms;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0 || !hovered(mx, my) || locked) {
			return false;
		}
		down = true;
		MiniGameSounds.click();
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (!down) {
			return false;
		}
		down = false;
		if (hovered(mx, my)) {
			action.run();
		}
		return true;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my) {
		boolean hot = hovered(mx, my) && !locked;
		boolean flash = System.currentTimeMillis() < flashUntil;
		int edge = selected ? TrahisonPalette.ACCENT_HOT : flash ? TrahisonPalette.ACCENT : hot ? TrahisonPalette.ACCENT_DIM : locked ? TrahisonPalette.BORDER : TrahisonPalette.EDGE;
		g.fill(x, y, x + w, y + h, edge);
		int inset = selected ? 2 : 1;
		g.fill(x + inset, y + inset, x + w - inset, y + h - inset, locked ? TrahisonPalette.PANEL_SUNKEN : down && hot ? TrahisonPalette.PANEL_SUNKEN : TrahisonPalette.PANEL);
		int textH = lines.size() * 10;
		int modelH = Math.max(16, h - textH - 6);
		if (modelHidden) {
			g.fill(x + w / 2 - modelH / 3, y + 3 + modelH / 2 - modelH / 3, x + w / 2 + modelH / 3, y + 3 + modelH / 2 + modelH / 3, TrahisonPalette.PANEL_SUNKEN);
			MenuUi.centered(g, Component.literal("?"), x + w / 2, y + 3 + modelH / 2 - 4, w, TrahisonPalette.TEXT_MUTED);
		} else if (!species.isEmpty()) {
			PokemonUi.model(g, species, x + w / 2, y + 3 + modelH / 2, Math.min(modelH, w - 8));
		}
		int ty = y + h - 3 - textH;
		for (int i = 0; i < lines.size(); i++) {
			TrahisonUi.textEllipsis(g, lines.get(i), x + 4, ty + i * 10, w - 8, i == 0 ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_SECONDARY);
		}
		if (!badge.isEmpty()) {
			TrahisonUi.pill(g, x + 3, y + 3, Component.literal(badge), TrahisonPalette.BG_DEEP, TrahisonPalette.TEXT);
		}
		if (locked) {
			// a padlock: body and shackle (the symbol, never a colour alone)
			int lx = x + w - 12;
			int ly = y + 4;
			g.fill(lx, ly + 4, lx + 8, ly + 10, TrahisonPalette.TEXT_MUTED);
			g.fill(lx + 1, ly, lx + 2, ly + 4, TrahisonPalette.TEXT_MUTED);
			g.fill(lx + 6, ly, lx + 7, ly + 4, TrahisonPalette.TEXT_MUTED);
			g.fill(lx + 1, ly, lx + 7, ly + 1, TrahisonPalette.TEXT_MUTED);
		}
	}
}
