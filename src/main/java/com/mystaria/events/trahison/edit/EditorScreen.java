package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.edit.EditKinds.Kind;
import com.mystaria.events.trahison.edit.LayoutDto.Box;
import com.mystaria.events.trahison.edit.LayoutDto.Cabin;
import com.mystaria.events.trahison.edit.LayoutDto.Door;
import com.mystaria.events.trahison.edit.LayoutDto.Pos;
import com.mystaria.events.trahison.edit.LayoutDto.ResultsBoard;
import com.mystaria.events.trahison.edit.LayoutDto.Slot;
import com.mystaria.events.trahison.edit.LayoutDto.Spot;
import com.mystaria.events.trahison.edit.LayoutDto.Zone;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The plan editor's side panel (Documentation/trahison/charte-ui.md, "window" mode, at the right of the screen so the
 * world stays visible and does not pause): the elements by kind and type, creation, deletion, a form per element, saving
 * as draft or complete, and the backend's validation errors. It shows and edits the plan held by {@link TrahisonEditor};
 * the backend decides what is valid. Escape or the editor key closes it; closing loses nothing.
 */
public final class EditorScreen extends Screen {

	private static final String T = "phantasmon_events.trahison.edit.";
	private static final int PANEL_W = 248;
	private static final int PAD = 8;
	private static final int ROW_H = 18;
	private static final int ROW_PITCH = 20;
	private static final int HEADER_H = 12;
	private static final int LABEL_W = 78;
	private static final int FORM_PITCH = 22;
	private static final int BUTTON_W = 68;

	private enum View { LIST, FORM, ERRORS }

	/** A list line: a type header ({@code id} null) or an element. */
	private record Entry(String header, String id) {
	}

	/** A modal question. */
	private record Confirm(Component title, Component text, Component yes, boolean danger, Runnable action) {
	}

	private static EditorScreen current;
	private static View view = View.LIST;

	private final PanelUi ui = new PanelUi();
	private final PanelUi modalUi = new PanelUi();
	private final PanelUi.Field idField = new PanelUi.Field(40);
	private String formKey;
	private Confirm confirm;
	private int listScroll;
	private int formScroll;
	private int errorScroll;
	private long lastRowClick;
	private String lastRowId;

	// Geometry of this frame.
	private int px;
	private int py;
	private int ph;
	private int cx;
	private int cw;
	private int saveY;
	private int statusY;
	private int actionY;
	private int listY;

	EditorScreen() {
		super(Component.translatable(T + "title"));
		current = this;
	}

	/** A save was refused: when the backend listed problems, show them. */
	static void onSaveFailed(boolean hasIssues) {
		if (current != null && current.minecraft != null && current.minecraft.screen == current && hasIssues) {
			view = View.ERRORS;
			current.errorScroll = 0;
		}
	}

	private static Component t(String key, Object... args) {
		return Component.translatable(T + key, args);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The world stays undimmed behind the panel. */
	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void removed() {
		commitId();
		if (current == this) {
			current = null;
		}
	}

	// ---------------------------------------------------------------- frame

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (!TrahisonEditor.isActive()) {
			onClose();
			return;
		}
		px = width - PANEL_W - TrahisonUi.SAFE_MARGIN;
		py = TrahisonUi.SAFE_MARGIN;
		ph = height - 2 * TrahisonUi.SAFE_MARGIN;
		cx = px + PAD;
		cw = PANEL_W - 2 * PAD;
		saveY = py + ph - PAD - ROW_H;
		statusY = saveY - 4 - 12;
		actionY = statusY - 4 - ROW_H;
		listY = py + 28 + ROW_H + 4;

		boolean modal = confirm != null;
		ui.begin(modal ? -1000 : mouseX, modal ? -1000 : mouseY);
		TrahisonUi.titledPanel(g, px, py, PANEL_W, ph, t("title"), TrahisonPalette.ACCENT);
		titlePills(g);
		switch (view) {
			case LIST -> drawList(g);
			case FORM -> drawForm(g);
			case ERRORS -> drawErrors(g);
		}
		drawFooter(g);
		if (ui.tooltip != null) {
			TrahisonUi.tooltip(g, ui.tooltip, mouseX, mouseY, width, height);
		}
		if (modal) {
			drawConfirm(g, mouseX, mouseY);
		}
	}

	private void titlePills(GuiGraphics g) {
		int right = px + PANEL_W - 6;
		if (TrahisonEditor.dirty) {
			Component label = t("pill.unsaved");
			int w = TrahisonUi.width(label) + 10;
			right -= w;
			TrahisonUi.pill(g, right, py + 4, label, TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER);
			right -= 4;
		}
		LayoutDto layout = TrahisonEditor.layout;
		Component version = layout.version == 0 ? t("pill.none") : t(layout.draft ? "pill.version_draft" : "pill.version", layout.version);
		int w = TrahisonUi.width(version) + 10;
		TrahisonUi.pill(g, right - w, py + 4, version, TrahisonPalette.PANEL_SUNKEN,
				layout.draft ? TrahisonPalette.TEXT_SECONDARY : TrahisonPalette.SUCCESS);
	}

	// ---------------------------------------------------------------- list view

	private void drawList(GuiGraphics g) {
		int x = cx;
		int tabW = (cw - 2 * (Kind.values().length - 1)) / Kind.values().length;
		for (Kind kind : Kind.values()) {
			boolean selected = kind == TrahisonEditor.selectedKind;
			boolean hovered = ui.hovered(x, py + 28, tabW, ROW_H);
			TrahisonUi.tab(g, x, py + 28, tabW, ROW_H, t("tab." + kind.key), selected, hovered);
			ui.zone(x, py + 28, tabW, ROW_H, button -> {
				if (kind != TrahisonEditor.selectedKind) {
					TrahisonEditor.select(kind, null);
					listScroll = 0;
				}
			});
			x += tabW + 2;
		}
		int listH = actionY - 4 - listY;
		TrahisonUi.well(g, cx, listY, cw, listH);
		List<Entry> entries = entries(TrahisonEditor.selectedKind);
		int content = 4;
		for (Entry entry : entries) {
			content += entry.id() == null ? HEADER_H : ROW_PITCH;
		}
		listScroll = clamp(listScroll, content - listH);
		ui.clip(g, cx + 1, listY + 1, cx + cw - 1, listY + listH - 1);
		int y = listY + 2 - listScroll;
		for (Entry entry : entries) {
			if (entry.id() == null) {
				MenuUi.text(g, typeName(TrahisonEditor.selectedKind, entry.header()), cx + 6, y + 2, cw - 16, TrahisonPalette.TEXT_MUTED);
				y += HEADER_H;
			} else {
				drawRow(g, entry.id(), cx + 2, y, cw - (content > listH ? 8 : 4));
				y += ROW_PITCH;
			}
		}
		if (entries.isEmpty()) {
			MenuUi.paragraph(g, t("list_empty"), cx + 8, listY + 8, cw - 16, TrahisonPalette.TEXT_MUTED);
		}
		ui.unclip(g);
		scrollbar(g, cx + cw - 5, listY + 2, listH - 4, listScroll, content);

		int bx = cx;
		boolean selected = TrahisonEditor.selectedId != null && TrahisonEditor.exists(TrahisonEditor.selectedKind, TrahisonEditor.selectedId);
		boolean boardTaken = TrahisonEditor.selectedKind == Kind.BOARD && TrahisonEditor.layout.resultsBoard != null;
		ui.button(g, bx, actionY, BUTTON_W, ROW_H, t("new"), ButtonKind.PRIMARY, t(boardTaken ? "new.board_exists" : "new.tip"), boardTaken ? null : () -> {
			TrahisonEditor.create(TrahisonEditor.selectedKind);
			openForm();
		});
		ui.button(g, bx + BUTTON_W + 6, actionY, BUTTON_W, ROW_H, t("edit"), ButtonKind.DEFAULT, null, selected ? this::openForm : null);
		ui.button(g, bx + 2 * (BUTTON_W + 6), actionY, BUTTON_W, ROW_H, t("delete"), ButtonKind.DANGER, null,
				selected ? () -> askDelete(TrahisonEditor.selectedKind, TrahisonEditor.selectedId) : null);
	}

	private List<Entry> entries(Kind kind) {
		List<String> ids = TrahisonEditor.ids(kind);
		boolean typed = kind == Kind.ZONE || kind == Kind.DOOR;
		List<String> sorted = new ArrayList<>(ids);
		if (typed) {
			sorted.sort(Comparator.comparing((String id) -> String.valueOf(TrahisonEditor.typeOf(kind, id))).thenComparing(Comparator.naturalOrder()));
		}
		List<Entry> entries = new ArrayList<>();
		String lastType = null;
		for (String id : sorted) {
			String type = typed ? String.valueOf(TrahisonEditor.typeOf(kind, id)) : null;
			if (typed && !type.equals(lastType)) {
				entries.add(new Entry(type, null));
				lastType = type;
			}
			entries.add(new Entry(null, id));
		}
		return entries;
	}

	private void drawRow(GuiGraphics g, String id, int x, int y, int w) {
		Kind kind = TrahisonEditor.selectedKind;
		boolean selected = id.equals(TrahisonEditor.selectedId);
		boolean hovered = ui.hovered(x, y, w, ROW_H);
		TrahisonUi.row(g, x, y, w, ROW_H, hovered, selected);
		int chip = switch (kind) {
			case ZONE -> EditKinds.zoneColor(TrahisonEditor.typeOf(kind, id));
			case DOOR -> EditKinds.doorColor(TrahisonEditor.typeOf(kind, id));
			case SLOT -> EditKinds.SLOT_COLOR;
			case CABIN -> TrahisonPalette.BETRAY;
			case SPOT -> EditKinds.SPOT_COLOR;
			case BOARD -> EditKinds.BOARD_COLOR;
		};
		g.fill(x + 7, y + 4, x + 17, y + 14, TrahisonPalette.BORDER);
		g.fill(x + 8, y + 5, x + 16, y + 13, chip);
		Component pillText = switch (kind) {
			case SLOT -> t("round_short", TrahisonEditor.slot(id).round == null ? "?" : TrahisonEditor.slot(id).round);
			case CABIN -> TrahisonEditor.cabin(id).side == null ? null : t("side." + TrahisonEditor.cabin(id).side);
			default -> null;
		};
		int pillW = pillText == null ? 0 : TrahisonUi.width(pillText) + 10;
		TrahisonUi.textEllipsis(g, Component.literal(id), x + 22, y + 5, w - 22 - pillW - 8, TrahisonPalette.TEXT);
		if (pillText != null) {
			TrahisonUi.pill(g, x + w - pillW - 4, y + 3, pillText, TrahisonPalette.PANEL_RAISED, TrahisonPalette.TEXT_SECONDARY);
		}
		if (hasIssue(id)) {
			g.fill(x + w - 2, y, x + w, y + ROW_H, TrahisonPalette.DANGER);
		}
		ui.zone(x, y, w, ROW_H, button -> {
			TrahisonEditor.select(kind, id);
			long now = System.currentTimeMillis();
			if (id.equals(lastRowId) && now - lastRowClick < 400) {
				openForm();
			}
			lastRowId = id;
			lastRowClick = now;
		});
	}

	private boolean hasIssue(String id) {
		return TrahisonEditor.issues.stream().anyMatch(issue -> issue.ids().contains(id));
	}

	private void askDelete(Kind kind, String id) {
		confirm = new Confirm(t("confirm_delete.title"), t("confirm_delete.text", id), t("delete"), true, () -> {
			TrahisonEditor.delete(kind, id);
			if (view == View.FORM) {
				view = View.LIST;
			}
		});
	}

	// ---------------------------------------------------------------- form view

	private void openForm() {
		String id = TrahisonEditor.selectedId;
		if (id == null || !TrahisonEditor.exists(TrahisonEditor.selectedKind, id)) {
			return;
		}
		view = View.FORM;
		formScroll = 0;
		formKey = TrahisonEditor.selectedKind + ":" + id;
		idField.value = id;
	}

	/** Applies the id typed in the form: renames the element, or puts the old id back when it is refused. */
	private void commitId() {
		if (view != View.FORM || TrahisonEditor.selectedId == null) {
			return;
		}
		String id = TrahisonEditor.selectedId;
		if (!idField.value.equals(id) && !TrahisonEditor.rename(TrahisonEditor.selectedKind, id, idField.value)) {
			TrahisonEditor.setStatus(t("id_refused", idField.value), true);
			idField.value = id;
		}
		formKey = TrahisonEditor.selectedKind + ":" + TrahisonEditor.selectedId;
	}

	private boolean idInvalid() {
		String id = TrahisonEditor.selectedId;
		return !idField.value.equals(id) && (!idField.value.matches("[A-Za-z0-9_-]{1,40}")
				|| TrahisonEditor.exists(TrahisonEditor.selectedKind, idField.value));
	}

	private void drawForm(GuiGraphics g) {
		Kind kind = TrahisonEditor.selectedKind;
		String id = TrahisonEditor.selectedId;
		if (id == null || !TrahisonEditor.exists(kind, id)) {
			view = View.LIST;
			return;
		}
		ui.button(g, cx, py + 28, BUTTON_W, ROW_H, t("back"), () -> {
			commitId();
			view = View.LIST;
		});
		ui.button(g, cx + BUTTON_W + 6, py + 28, BUTTON_W, ROW_H, t("delete"), ButtonKind.DANGER, null, () -> askDelete(kind, id));
		// The selection, pinned under the buttons: what the "from selection" buttons will use.
		Box selection = TrahisonEditor.selectionHub();
		Component selectionText = selection == null ? t("selection.none") : t("selection", selection.minX, selection.minY, selection.minZ,
				selection.maxX, selection.maxY, selection.maxZ);
		TrahisonUi.textEllipsis(g, selectionText, cx + 2, py + 52, cw - 4 - (selection == null ? 0 : 36),
				selection == null ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.ACCENT);
		if (selection != null) {
			ui.button(g, cx + cw - 34, py + 49, 34, 14, t("selection.clear"), ButtonKind.DEFAULT, t("selection.clear.tip"), TrahisonEditor::clearSelection);
		}
		int top = py + 66;
		int bottom = statusY - 4;
		TrahisonUi.well(g, cx, top, cw, bottom - top);
		ui.clip(g, cx + 1, top + 1, cx + cw - 1, bottom - 1);
		int content = formRows(g, kind, id, top + 4 - formScroll, cx + 4, cw - 14) + formScroll - top;
		ui.unclip(g);
		formScroll = clamp(formScroll, content - (bottom - top));
		scrollbar(g, cx + cw - 6, top + 2, bottom - top - 4, formScroll, content);
	}

	/** Draws the form's rows from {@code y} and returns the y below the last one. */
	private int formRows(GuiGraphics g, Kind kind, String id, int y, int x, int w) {
		int cw2 = w - LABEL_W;
		int cxl = x + LABEL_W;
		// id (every kind)
		if (kind != Kind.BOARD) { // the board's id is fixed
			label(g, x, y, "id");
			ui.field(g, idField, cxl, y, cw2, ROW_H, idInvalid());
			y += FORM_PITCH;
		}
		switch (kind) {
			case ZONE -> {
				Zone zone = TrahisonEditor.zone(id);
				y = cycleRow(g, x, y, w, "type", typeName(Kind.ZONE, zone.type), dir -> {
					zone.type = step(EditKinds.ZONE_TYPES, zone.type, dir);
					TrahisonEditor.changed();
				});
				label(g, x, y, "boxes");
				MenuUi.text(g, t("boxes_count", zone.boxes.size()), cxl, y + 5, cw2, TrahisonPalette.TEXT_SECONDARY);
				y += FORM_PITCH;
				int half = (w - 4) / 2;
				ui.button(g, x, y, half, ROW_H, t("box.add"), ButtonKind.PRIMARY, t("box.add.tip"), TrahisonEditor.selectionLocal() == null ? null : () -> TrahisonEditor.addBox(zone));
				ui.button(g, x + half + 4, y, half, ROW_H, t("box.replace"), ButtonKind.DEFAULT, t("box.replace.tip"),
						TrahisonEditor.selectionLocal() == null ? null : () -> TrahisonEditor.replaceBoxes(zone));
				y += FORM_PITCH;
				ui.button(g, x, y, half, ROW_H, t("box.remove"), ButtonKind.DEFAULT, null, zone.boxes.isEmpty() ? null : () -> {
					zone.boxes.remove(zone.boxes.size() - 1);
					TrahisonEditor.changed();
				});
				ui.button(g, x + half + 4, y, half, ROW_H, t("box.clear"), ButtonKind.DANGER, null, zone.boxes.isEmpty() ? null : () -> {
					zone.boxes.clear();
					TrahisonEditor.changed();
				});
				y += FORM_PITCH;
				for (Box box : zone.boxes) {
					TrahisonUi.textEllipsis(g, boxText(box), x + 2, y, w - 4, TrahisonPalette.TEXT_MUTED);
					y += TrahisonUi.LINE + 2;
				}
			}
			case DOOR -> {
				Door door = TrahisonEditor.door(id);
				y = cycleRow(g, x, y, w, "type", typeName(Kind.DOOR, door.type), dir -> {
					door.type = step(EditKinds.DOOR_TYPES, door.type, dir);
					if ("color".equals(door.type)) {
						door.round = door.round == null ? Integer.valueOf(1) : door.round;
						door.colors = door.colors.isEmpty() ? new ArrayList<>(EditKinds.colorsOf(EditKinds.PAIRS.get(0))) : door.colors;
					} else {
						door.round = null;
						door.colors = new ArrayList<>();
						if (!"final".equals(door.type)) {
							door.threshold = null; // only colour doors and the final door (its pupitre) have one
						}
					}
					TrahisonEditor.changed();
				});
				y = cycleRow(g, x, y, w, "zone_a", refName(door.zoneA), dir -> {
					door.zoneA = step(withNone(TrahisonEditor.ids(Kind.ZONE)), door.zoneA, dir);
					TrahisonEditor.changed();
				});
				y = cycleRow(g, x, y, w, "zone_b", refName(door.zoneB), dir -> {
					door.zoneB = step(withNone(TrahisonEditor.ids(Kind.ZONE)), door.zoneB, dir);
					TrahisonEditor.changed();
				});
				if ("final".equals(door.type)) {
					// T2.5: the pupitre stands in this zone, in front of the door; without one the door opens from anywhere.
					y = cycleRow(g, x, y, w, "pupitre", refName(door.threshold), dir -> {
						door.threshold = step(withNone(TrahisonEditor.ids(Kind.ZONE)), door.threshold, dir);
						TrahisonEditor.changed();
					});
				}
				if ("color".equals(door.type)) {
					y = cycleRow(g, x, y, w, "threshold", refName(door.threshold), dir -> {
						door.threshold = step(withNone(TrahisonEditor.ids(Kind.ZONE)), door.threshold, dir);
						TrahisonEditor.changed();
					});
					y = cycleRow(g, x, y, w, "round", Component.literal(door.round == null ? "-" : Integer.toString(door.round)), dir -> {
						int next = ((door.round == null ? 1 : door.round) - 1 + dir + 3) % 3 + 1;
						door.round = next;
						TrahisonEditor.changed();
					});
					String pair = EditKinds.pairOf(door.colors);
					y = cycleRow(g, x, y, w, "colors", pair == null ? t("none") : pairName(pair), dir -> {
						door.colors = new ArrayList<>(EditKinds.colorsOf(step(EditKinds.PAIRS, pair, dir)));
						TrahisonEditor.changed();
					});
					if (pair != null) {
						drawPairGlyphs(g, cxl + cw2 - 28, y - FORM_PITCH + 4, pair);
					}
				}
				label(g, x, y, "block");
				TrahisonUi.textEllipsis(g, door.block == null ? t("not_set") : boxText(door.block), cxl, y + 5, cw2,
						door.block == null ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.TEXT_SECONDARY);
				y += FORM_PITCH;
				ui.button(g, x, y, w, ROW_H, t("from_selection"), ButtonKind.PRIMARY, t("from_selection.tip"),
						TrahisonEditor.selectionLocal() == null ? null : () -> TrahisonEditor.setBlock(door));
				y += FORM_PITCH;
			}
			case SLOT -> {
				Slot slot = TrahisonEditor.slot(id);
				y = cycleRow(g, x, y, w, "round", Component.literal(slot.round == null ? "-" : Integer.toString(slot.round)), dir -> {
					slot.round = ((slot.round == null ? 1 : slot.round) - 1 + dir + 3) % 3 + 1;
					TrahisonEditor.changed();
				});
				y = cycleRow(g, x, y, w, "orientation", slot.orientation == null ? t("none") : t("orientation." + slot.orientation), dir -> {
					slot.orientation = step(EditKinds.ORIENTATIONS, slot.orientation, dir);
					TrahisonEditor.changed();
				});
				label(g, x, y, "origin");
				TrahisonUi.textEllipsis(g, slot.origin == null ? t("not_set") : posText(slot.origin), cxl, y + 5, cw2,
						slot.origin == null ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.TEXT_SECONDARY);
				y += FORM_PITCH;
				ui.button(g, x, y, w, ROW_H, t("origin_from_selection"), ButtonKind.PRIMARY, t("origin_from_selection.tip"),
						TrahisonEditor.selectionLocal() == null ? null : () -> TrahisonEditor.setOrigin(slot));
				y += FORM_PITCH;
			}
			case CABIN -> {
				Cabin cabin = TrahisonEditor.cabin(id);
				y = cycleRow(g, x, y, w, "serves", cabin.serves == null ? t("none") : pairName(cabin.serves), dir -> {
					cabin.serves = step(EditKinds.PAIRS, cabin.serves, dir);
					TrahisonEditor.changed();
				});
				y = cycleRow(g, x, y, w, "side", cabin.side == null ? t("none") : t("side." + cabin.side), dir -> {
					cabin.side = step(EditKinds.SIDES, cabin.side, dir);
					TrahisonEditor.changed();
				});
				y = cycleRow(g, x, y, w, "zone", refName(cabin.zone), dir -> {
					cabin.zone = step(withNone(idsOfType(Kind.ZONE, "cabin")), cabin.zone, dir);
					TrahisonEditor.changed();
				});
				y = cycleRow(g, x, y, w, "door", refName(cabin.door), dir -> {
					cabin.door = step(withNone(idsOfType(Kind.DOOR, "cabin")), cabin.door, dir);
					TrahisonEditor.changed();
				});
			}
			case BOARD -> {
				ResultsBoard board = TrahisonEditor.board(id);
				y = cycleRow(g, x, y, w, "facing", board.facing == null ? t("none") : t("orientation." + board.facing), dir -> {
					board.facing = step(EditKinds.ORIENTATIONS, board.facing, dir);
					TrahisonEditor.changed();
				});
				y = cycleRow(g, x, y, w, "width", Component.literal(board.width == null ? "-" : Integer.toString(board.width)), dir -> {
					board.width = sized(board.width, dir);
					TrahisonEditor.changed();
				});
				y = cycleRow(g, x, y, w, "height", Component.literal(board.height == null ? "-" : Integer.toString(board.height)), dir -> {
					board.height = sized(board.height, dir);
					TrahisonEditor.changed();
				});
				label(g, x, y, "position");
				TrahisonUi.textEllipsis(g, board.position == null ? t("not_set") : posText(board.position), cxl, y + 5, cw2,
						board.position == null ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.TEXT_SECONDARY);
				y += FORM_PITCH;
				ui.button(g, x, y, w, ROW_H, t("board.fit"), ButtonKind.PRIMARY, t("board.fit.tip"),
						TrahisonEditor.selectionLocal() == null ? null : () -> TrahisonEditor.fitBoard(board));
				y += FORM_PITCH;
				y += MenuUi.paragraph(g, t("board.help"), x + 2, y, w - 4, TrahisonPalette.TEXT_MUTED) + 4;
			}
			case SPOT -> {
				Spot spot = TrahisonEditor.spot(id);
				label(g, x, y, "position");
				TrahisonUi.textEllipsis(g, spot.position == null ? t("not_set") : posText(spot.position), cxl, y + 5, cw2,
						spot.position == null ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.TEXT_SECONDARY);
				y += FORM_PITCH;
				ui.button(g, x, y, w, ROW_H, t("position_from_selection"), ButtonKind.PRIMARY, t("position_from_selection.tip"),
						TrahisonEditor.selectionLocal() == null ? null : () -> TrahisonEditor.setPosition(spot));
				y += FORM_PITCH;
			}
		}
		return y + 4;
	}

	private void label(GuiGraphics g, int x, int y, String key) {
		MenuUi.text(g, t("field." + key), x + 2, y + 5, LABEL_W - 6, TrahisonPalette.TEXT_MUTED);
	}

	/** A labelled stepping control; returns the next row's y. */
	private int cycleRow(GuiGraphics g, int x, int y, int w, String key, Component value, IntConsumer step) {
		label(g, x, y, key);
		ui.cycle(g, x + LABEL_W, y, w - LABEL_W, ROW_H, value, step);
		return y + FORM_PITCH;
	}

	/** The next (or previous) of {@code options} after {@code current}; the first one when {@code current} is not in it. */
	private static String step(List<String> options, String current, int dir) {
		int at = options.indexOf(current);
		if (at < 0) {
			return options.get(dir >= 0 ? 0 : options.size() - 1);
		}
		return options.get(Math.floorMod(at + dir, options.size()));
	}

	/** A board size stepped by one (four with Shift), kept inside the backend's bounds. */
	private static Integer sized(Integer current, int dir) {
		int step = Screen.hasShiftDown() ? 4 : 1;
		int base = current == null ? EditKinds.BOARD_MIN : current;
		return Math.max(EditKinds.BOARD_MIN, Math.min(EditKinds.BOARD_MAX, base + dir * step));
	}

	/** {@code ids} preceded by null, "no element". */
	private static List<String> withNone(List<String> ids) {
		List<String> all = new ArrayList<>();
		all.add(null);
		all.addAll(ids);
		return all;
	}

	private static List<String> idsOfType(Kind kind, String type) {
		return TrahisonEditor.ids(kind).stream().filter(id -> type.equals(TrahisonEditor.typeOf(kind, id))).toList();
	}

	private static Component refName(String id) {
		return id == null ? t("none") : Component.literal(id);
	}

	private static Component typeName(Kind kind, String type) {
		return type == null ? t("none") : t((kind == Kind.DOOR ? "door_type." : "zone_type.") + type);
	}

	private static Component colorName(String color) {
		return t("color." + color);
	}

	private static Component pairName(String pair) {
		List<String> colors = EditKinds.colorsOf(pair);
		return Component.literal(colorName(colors.get(0)).getString() + " + " + colorName(colors.get(1)).getString());
	}

	/** The two colours' symbols beside a colour pair (never colour alone, charte section 9). */
	private static void drawPairGlyphs(GuiGraphics g, int x, int y, String pair) {
		List<String> colors = EditKinds.colorsOf(pair);
		for (int i = 0; i < 2; i++) {
			TrahisonUi.colorGlyph(g, TrahisonUi.GameColor.valueOf(colors.get(i).toUpperCase(Locale.ROOT)), x + i * 14, y, 10);
		}
	}

	private static Component boxText(Box box) {
		return t("box", box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
	}

	private static Component posText(Pos pos) {
		return t("pos", pos.x, pos.y, pos.z);
	}

	// ---------------------------------------------------------------- errors view

	private void drawErrors(GuiGraphics g) {
		List<TrahisonEditor.Issue> issues = TrahisonEditor.issues;
		MenuUi.text(g, t("errors.title", issues.size()), cx + 2, py + 33, cw - 4, TrahisonPalette.DANGER);
		int top = py + 28 + ROW_H + 4;
		int bottom = actionY - 4;
		TrahisonUi.well(g, cx, top, cw, bottom - top);
		List<Component> texts = new ArrayList<>();
		int content = 4;
		for (TrahisonEditor.Issue issue : issues) {
			Component text = issueText(issue);
			texts.add(text);
			content += MenuUi.paragraphHeight(text, cw - 36) + 8;
		}
		errorScroll = clamp(errorScroll, content - (bottom - top));
		ui.clip(g, cx + 1, top + 1, cx + cw - 1, bottom - 1);
		int y = top + 2 - errorScroll;
		for (int i = 0; i < issues.size(); i++) {
			TrahisonEditor.Issue issue = issues.get(i);
			int h = Math.max(ROW_H, MenuUi.paragraphHeight(texts.get(i), cw - 36) + 8);
			boolean hovered = ui.hovered(cx + 2, y, cw - 8, h);
			TrahisonUi.row(g, cx + 2, y, cw - 8, h, hovered, false);
			g.fill(cx + 2, y, cx + 5, y + h, TrahisonPalette.DANGER);
			MenuUi.paragraph(g, texts.get(i), cx + 12, y + 4, cw - 36, TrahisonPalette.TEXT);
			ui.zone(cx + 2, y, cw - 8, h, button -> jumpTo(issue));
			y += h + 2;
		}
		ui.unclip(g);
		scrollbar(g, cx + cw - 5, top + 2, bottom - top - 4, errorScroll, content);
		ui.button(g, cx, actionY, cw, ROW_H, t("errors.back"), ButtonKind.PRIMARY, null, () -> view = View.LIST);
	}

	private static Component issueText(TrahisonEditor.Issue issue) {
		return Component.translatableWithFallback(T + "error." + issue.code().toLowerCase(Locale.ROOT), issue.code(),
				String.join(", ", issue.ids()), issue.info());
	}

	/** A click on a problem selects the first element it names, when it still exists. */
	private void jumpTo(TrahisonEditor.Issue issue) {
		for (String id : issue.ids()) {
			for (Kind kind : List.of(Kind.DOOR, Kind.ZONE, Kind.SLOT, Kind.CABIN, Kind.SPOT, Kind.BOARD)) {
				if (TrahisonEditor.exists(kind, id)) {
					TrahisonEditor.select(kind, id);
					view = View.LIST;
					listScroll = 0;
					return;
				}
			}
		}
	}

	// ---------------------------------------------------------------- footer

	private void drawFooter(GuiGraphics g) {
		boolean idle = !TrahisonEditor.busy;
		int errorsW = 0;
		if (!TrahisonEditor.issues.isEmpty() && view != View.ERRORS) {
			Component label = t("errors.button", TrahisonEditor.issues.size());
			errorsW = TrahisonUi.width(label) + 10;
			int x = cx + cw - errorsW;
			TrahisonUi.pill(g, x, statusY, label, TrahisonPalette.DANGER_DARK, TrahisonPalette.TEXT);
			ui.zone(x, statusY, errorsW, 12, button -> {
				view = View.ERRORS;
				errorScroll = 0;
			});
		}
		Component status = TrahisonEditor.status;
		if (status != null) {
			TrahisonUi.textEllipsis(g, status, cx + 2, statusY + 2, cw - 4 - errorsW - (errorsW > 0 ? 4 : 0),
					TrahisonEditor.statusError ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_SECONDARY);
		} else {
			TrahisonUi.textEllipsis(g, t("hint"), cx + 2, statusY + 2, cw - 4 - errorsW, TrahisonPalette.TEXT_MUTED);
		}
		ui.button(g, cx, saveY, BUTTON_W, ROW_H, t("save_draft"), ButtonKind.DEFAULT, t("save_draft.tip"), idle ? () -> save(true) : null);
		ui.button(g, cx + BUTTON_W + 6, saveY, BUTTON_W, ROW_H, t("save"), ButtonKind.PRIMARY, t("save.tip"), idle ? () -> save(false) : null);
		ui.button(g, cx + 2 * (BUTTON_W + 6), saveY, BUTTON_W, ROW_H, t("reload"), ButtonKind.DEFAULT, t("reload.tip"), idle ? this::askReload : null);
	}

	private void save(boolean draft) {
		commitId();
		TrahisonEditor.save(draft);
	}

	private void askReload() {
		if (!TrahisonEditor.dirty) {
			TrahisonEditor.reload();
			return;
		}
		confirm = new Confirm(t("confirm_reload.title"), t("confirm_reload.text"), t("reload"), true, TrahisonEditor::reload);
	}

	// ---------------------------------------------------------------- modal

	private void drawConfirm(GuiGraphics g, int mouseX, int mouseY) {
		modalUi.begin(mouseX, mouseY);
		int[] r = TrahisonUi.modal(g, width, height, 220, 88, confirm.title(), confirm.danger() ? TrahisonPalette.DANGER : TrahisonPalette.ACCENT);
		MenuUi.paragraph(g, confirm.text(), r[0], r[1] + 2, r[2], TrahisonPalette.TEXT_SECONDARY);
		int by = r[1] + r[3] - ROW_H;
		Confirm question = confirm;
		modalUi.button(g, r[0], by, 80, ROW_H, t("cancel"), () -> confirm = null);
		modalUi.button(g, r[0] + r[2] - 80, by, 80, ROW_H, question.yes(), question.danger() ? ButtonKind.DANGER : ButtonKind.PRIMARY, null, () -> {
			confirm = null;
			question.action().run();
		});
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (confirm != null) {
			modalUi.click(x, y, button);
			return true;
		}
		PanelUi.Field before = ui.focused();
		boolean handled = ui.click(x, y, button);
		if (before != null && ui.focused() != before) {
			commitId();
		}
		if (handled) {
			return true;
		}
		// A click on the panel but on nothing: keep it from reaching anything below. Elsewhere: nothing happens either (no world clicks in a screen).
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int delta = (int) (-scrollY * ROW_PITCH);
		switch (view) {
			case LIST -> listScroll += delta;
			case FORM -> formScroll += delta;
			case ERRORS -> errorScroll += delta;
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (confirm != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				confirm = null;
			}
			return true;
		}
		if (ui.focused() != null) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_TAB) {
				ui.unfocus();
				commitId();
			} else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				ui.unfocus();
				idField.value = TrahisonEditor.selectedId == null ? "" : TrahisonEditor.selectedId;
			} else {
				ui.key(keyCode, (modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || TrahisonEditor.OPEN_KEY.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && view == View.LIST && TrahisonEditor.selectedId != null) {
			openForm();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		return ui.charTyped(chr) || super.charTyped(chr, modifiers);
	}

	// ---------------------------------------------------------------- helpers

	private static int clamp(int scroll, int overflow) {
		return Math.max(0, Math.min(scroll, overflow));
	}

	/** A thin scroll thumb at the right of a clipped area, drawn only when the content overflows. */
	private static void scrollbar(GuiGraphics g, int x, int y, int h, int scroll, int content) {
		if (content <= h) {
			return;
		}
		int thumb = Math.max(12, h * h / content);
		int top = y + (h - thumb) * scroll / Math.max(1, content - h);
		g.fill(x, top, x + 2, top + thumb, TrahisonPalette.EDGE_LIGHT);
	}
}
