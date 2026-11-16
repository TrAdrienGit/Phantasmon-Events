package com.mystaria.events.trahison.ui;

/**
 * The game's colour tokens (ARGB). Mirrors the table of {@code Documentation/trahison/charte-ui.md}: change both together.
 * Unlike Phantasmon's {@code MenuUi.Palette} this look is fixed (it does not follow the player's theme choice), so the
 * game keeps its own mood: ink-blue panels, a spectral teal accent. No game logic here, only colours.
 */
public final class TrahisonPalette {

	private TrahisonPalette() {
	}

	// ---- Backgrounds and panels
	public static final int BG_DEEP = 0xFF0E1018;
	public static final int BG = 0xFF161A26;
	public static final int PANEL = 0xFF1F2433;
	public static final int PANEL_RAISED = 0xFF2A3045;
	public static final int PANEL_SUNKEN = 0xFF121520;
	public static final int DOTS = 0xFF222840;
	/** Dimming behind modal windows and full-screen stages. */
	public static final int VEIL = 0xB0080A10;

	// ---- Borders
	public static final int BORDER = 0xFF0A0C12;
	public static final int EDGE = 0xFF4A5372;
	public static final int EDGE_LIGHT = 0xFF7A85AD;

	// ---- Text
	public static final int TEXT = 0xFFF2F4FA;
	public static final int TEXT_SECONDARY = 0xFFC3C9DE;
	public static final int TEXT_MUTED = 0xFF8089A8;
	public static final int TEXT_DISABLED = 0xFF5A6280;

	// ---- Accent (the hostess's spectral teal)
	public static final int ACCENT = 0xFF5FE0D0;
	public static final int ACCENT_DIM = 0xFF2A8A80;
	public static final int ACCENT_HOT = 0xFFA5F5EA;

	// ---- The three game colours (neutral names; later skinned Fire / Grass / Water)
	public static final int RED = 0xFFE0483E;
	public static final int RED_LIGHT = 0xFFFF8A80;
	public static final int RED_DARK = 0xFF8E2620;
	public static final int GREEN = 0xFF4CBF5A;
	public static final int GREEN_LIGHT = 0xFF8FE89A;
	public static final int GREEN_DARK = 0xFF21702C;
	public static final int BLUE = 0xFF4A8CF0;
	public static final int BLUE_LIGHT = 0xFF8FBBFF;
	public static final int BLUE_DARK = 0xFF204E94;

	// ---- Wire-only colours (the wiring puzzle, never a door or a vote; each has its own symbol)
	public static final int YELLOW = 0xFFF2E24A;
	public static final int YELLOW_LIGHT = 0xFFFFF59A;
	public static final int YELLOW_DARK = 0xFF8C8420;
	public static final int WHITE = 0xFFF2F4FA;
	public static final int WHITE_LIGHT = 0xFFFFFFFF;
	public static final int WHITE_DARK = 0xFF8E94A8;

	// ---- Votes
	public static final int ALLY = 0xFFFFD36B;
	public static final int ALLY_DARK = 0xFF8A6A1E;
	public static final int BETRAY = 0xFFC04BD8;
	public static final int BETRAY_DARK = 0xFF65237A;

	// ---- States
	public static final int DANGER = 0xFFFF5C5C;
	public static final int DANGER_DARK = 0xFF8A2A2A;
	public static final int SUCCESS = 0xFF5FD38D;
	public static final int SUCCESS_DARK = 0xFF24704A;

	// ---- Tints
	/** Ghost players and ghost-only UI; drawn over content at about 50% alpha. */
	public static final int GHOST = 0xFF9FB0E8;
	/** Everything only the game master sees (panels, markers). */
	public static final int GAME_MASTER = 0xFFFFB347;
	public static final int GAME_MASTER_DARK = 0xFF8A5A14;

	/** Relief overlays shared with the rest of the mod's look. */
	public static final int SHINE = 0x30FFFFFF;
	public static final int SHADE = 0x50000000;
}
