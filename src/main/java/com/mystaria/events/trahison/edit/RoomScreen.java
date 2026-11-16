package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.edit.RoomDto.SlotDto;
import com.mystaria.events.trahison.edit.RoomDto.StepDto;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

import static com.mystaria.events.trahison.edit.RoomUi.t;

/**
 * The room editor's side panel (charte-ui.md, "window" mode at the right of the screen so the world stays visible and does
 * not pause): the slots (list, form), the room's own fields, the problems, saving, and a button to the chain screen
 * ({@link ChainScreen}). It shows and edits what {@link RoomEditor} holds. Escape or the editor key closes it; closing
 * loses nothing.
 */
public final class RoomScreen extends Screen {

	private static final int PANEL_W = 232;
	private static final int PAD = 8;
	private static final int ROW_H = 18;
	private static final int ROW_PITCH = 20;
	private static final int LABEL_W = 78;
	private static final int FORM_PITCH = 22;
	private static final int BUTTON_W = 68;

	private enum View { SLOTS, FORM, ROOM, ERRORS }

	private record Confirm(Component title, Component text, Component yes, Runnable action) {
	}

	private static RoomScreen current;
	private static View view = View.SLOTS;
	/** The kind the next new slot gets. */
	private static String newKind = RoomChecks.KINDS.get(0);

	private final PanelUi ui = new PanelUi();
	private final PanelUi modalUi = new PanelUi();
	private final PanelUi.Field idField = new PanelUi.Field(40);
	private final PanelUi.Field nameField = new PanelUi.Field(60, true);
	private final PanelUi.Field authorField = new PanelUi.Field(40, true);
	private Confirm confirm;
	private int listScroll;
	private int formScroll;
	private int errorScroll;
	private long lastRowClick;
	private String lastRowId;

	private int px;
	private int py;
	private int ph;
	private int cx;
	private int cw;
	private int saveY;
	private int statusY;
	private int actionY;
	private int listY;

	RoomScreen() {
		super(t("title"));
		current = this;
		nameField.value = RoomEditor.room.name == null ? "" : RoomEditor.room.name;
		authorField.value = RoomEditor.room.author == null ? "" : RoomEditor.room.author;
	}

	/** A save was refused: when problems were listed, show them. */
	static void onSaveFailed(boolean hasIssues) {
		if (current != null && current.minecraft != null && current.minecraft.screen == current && hasIssues) {
			view = View.ERRORS;
			current.errorScroll = 0;
		}
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
		if (!RoomEditor.isActive()) {
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
			case SLOTS -> {
				drawTabs(g);
				drawList(g);
			}
			case FORM -> drawForm(g);
			case ROOM -> {
				drawTabs(g);
				drawRoom(g);
			}
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
		if (RoomEditor.dirty) {
			Component label = t("pill.unsaved");
			int w = TrahisonUi.width(label) + 10;
			right -= w;
			TrahisonUi.pill(g, right, py + 4, label, TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER);
			right -= 4;
		}
		RoomDto room = RoomEditor.room;
		Component version = room.version == 0 ? t("pill.none") : t("pill.version", room.version);
		int w = TrahisonUi.width(version) + 10;
		TrahisonUi.pill(g, right - w, py + 4, version, TrahisonPalette.PANEL_SUNKEN, room.version == 0 ? TrahisonPalette.TEXT_SECONDARY : TrahisonPalette.SUCCESS);
	}

	private void drawTabs(GuiGraphics g) {
		int x = cx;
		for (View tab : new View[] { View.SLOTS, View.ROOM }) {
			boolean selected = tab == view;
			boolean hovered = ui.hovered(x, py + 28, 56, ROW_H);
			TrahisonUi.tab(g, x, py + 28, 56, ROW_H, t("tab." + tab.name().toLowerCase()), selected, hovered);
			ui.zone(x, py + 28, 56, ROW_H, button -> {
				commitId();
				view = tab;
				if (tab == View.ROOM) {
					nameField.value = RoomEditor.room.name == null ? "" : RoomEditor.room.name;
					authorField.value = RoomEditor.room.author == null ? "" : RoomEditor.room.author;
				}
			});
			x += 60;
		}
	}

	// ---------------------------------------------------------------- slot list

	private void drawList(GuiGraphics g) {
		int kindY = actionY - 4 - ROW_H;
		int listH = kindY - 4 - listY;
		TrahisonUi.well(g, cx, listY, cw, listH);
		List<SlotDto> slots = RoomEditor.room.slots;
		int content = 4 + slots.size() * ROW_PITCH;
		listScroll = RoomUi.clamp(listScroll, content - listH);
		ui.clip(g, cx + 1, listY + 1, cx + cw - 1, listY + listH - 1);
		int y = listY + 2 - listScroll;
		for (SlotDto slot : slots) {
			drawRow(g, slot, cx + 2, y, cw - (content > listH ? 8 : 4));
			y += ROW_PITCH;
		}
		if (slots.isEmpty()) {
			MenuUi.paragraph(g, t("list_empty"), cx + 8, listY + 8, cw - 16, TrahisonPalette.TEXT_MUTED);
		}
		ui.unclip(g);
		RoomUi.scrollbar(g, cx + cw - 5, listY + 2, listH - 4, listScroll, content);

		MenuUi.text(g, t("field.kind"), cx + 2, kindY + 5, LABEL_W - 6, TrahisonPalette.TEXT_MUTED);
		ui.cycle(g, cx + LABEL_W, kindY, cw - LABEL_W, ROW_H, RoomUi.kind(newKind), dir -> newKind = RoomUi.step(RoomChecks.KINDS, newKind, dir));
		boolean selected = RoomEditor.selectedSlot != null && RoomEditor.slot(RoomEditor.selectedSlot) != null;
		ui.button(g, cx, actionY, BUTTON_W, ROW_H, t("new"), ButtonKind.PRIMARY, t("new.tip"), () -> {
			RoomEditor.createSlot(newKind);
			openForm();
		});
		ui.button(g, cx + BUTTON_W + 6, actionY, BUTTON_W, ROW_H, t("edit"), ButtonKind.DEFAULT, null, selected ? this::openForm : null);
		ui.button(g, cx + 2 * (BUTTON_W + 6), actionY, BUTTON_W, ROW_H, t("delete"), ButtonKind.DANGER, null,
				selected ? () -> askDelete(RoomEditor.selectedSlot) : null);
	}

	private void drawRow(GuiGraphics g, SlotDto slot, int x, int y, int w) {
		boolean selected = slot.id.equals(RoomEditor.selectedSlot);
		boolean hovered = ui.hovered(x, y, w, ROW_H);
		TrahisonUi.row(g, x, y, w, ROW_H, hovered, selected);
		g.fill(x + 7, y + 4, x + 17, y + 14, TrahisonPalette.BORDER);
		g.fill(x + 8, y + 5, x + 16, y + 13, RoomWorld.kindColor(slot.kind));
		Component pillText = RoomUi.kind(slot.kind);
		int pillW = TrahisonUi.width(pillText) + 10;
		TrahisonUi.textEllipsis(g, Component.literal(slot.id), x + 22, y + 5, w - 22 - pillW - 8, TrahisonPalette.TEXT);
		TrahisonUi.pill(g, x + w - pillW - 4, y + 3, pillText, TrahisonPalette.PANEL_RAISED, TrahisonPalette.TEXT_SECONDARY);
		if (RoomEditor.hasIssue(slot.id)) {
			g.fill(x + w - 2, y, x + w, y + ROW_H, TrahisonPalette.DANGER);
		}
		String id = slot.id;
		ui.zone(x, y, w, ROW_H, button -> {
			RoomEditor.selectedSlot = id;
			long now = System.currentTimeMillis();
			if (id.equals(lastRowId) && now - lastRowClick < 400) {
				openForm();
			}
			lastRowId = id;
			lastRowClick = now;
		});
	}

	private void askDelete(String id) {
		confirm = new Confirm(t("confirm_delete.title"), t("confirm_delete.text", id), t("delete"), () -> {
			RoomEditor.deleteSlot(id);
			if (view == View.FORM) {
				view = View.SLOTS;
			}
		});
	}

	// ---------------------------------------------------------------- slot form

	private void openForm() {
		String id = RoomEditor.selectedSlot;
		if (id == null || RoomEditor.slot(id) == null) {
			return;
		}
		view = View.FORM;
		formScroll = 0;
		idField.value = id;
	}

	/** Applies the id typed in the form: renames the slot, or puts the old id back when it is refused. */
	private void commitId() {
		if (view != View.FORM || RoomEditor.selectedSlot == null) {
			return;
		}
		String id = RoomEditor.selectedSlot;
		if (!idField.value.equals(id) && !RoomEditor.renameSlot(id, idField.value)) {
			RoomEditor.setStatus(t("id_refused", idField.value), true);
			idField.value = id;
		}
	}

	private boolean idInvalid() {
		String id = RoomEditor.selectedSlot;
		return !idField.value.equals(id) && (!RoomChecks.validId(idField.value) || RoomEditor.slot(idField.value) != null);
	}

	private void drawForm(GuiGraphics g) {
		String id = RoomEditor.selectedSlot;
		SlotDto slot = id == null ? null : RoomEditor.slot(id);
		if (slot == null) {
			view = View.SLOTS;
			return;
		}
		ui.button(g, cx, py + 28, BUTTON_W, ROW_H, t("back"), () -> {
			commitId();
			view = View.SLOTS;
		});
		ui.button(g, cx + BUTTON_W + 6, py + 28, BUTTON_W, ROW_H, t("delete"), ButtonKind.DANGER, null, () -> askDelete(id));
		Box selection = selectionText(g);
		int top = py + 66;
		int bottom = statusY - 4;
		TrahisonUi.well(g, cx, top, cw, bottom - top);
		ui.clip(g, cx + 1, top + 1, cx + cw - 1, bottom - 1);
		int content = formRows(g, slot, selection, top + 4 - formScroll, cx + 4, cw - 14) + formScroll - top;
		ui.unclip(g);
		formScroll = RoomUi.clamp(formScroll, content - (bottom - top));
		RoomUi.scrollbar(g, cx + cw - 6, top + 2, bottom - top - 4, formScroll, content);
	}

	private record Box(RoomDto.P min, RoomDto.P size) {
	}

	/** The selection pinned under the buttons, in the template's own coordinates; returns it. */
	private Box selectionText(GuiGraphics g) {
		LayoutDto.Box box = RoomEditor.selectionCanonical();
		Component text = box == null ? t("selection.none") : t("selection", box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
		TrahisonUi.textEllipsis(g, text, cx + 2, py + 52, cw - 4 - (box == null ? 0 : 36), box == null ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.ACCENT);
		if (box != null) {
			ui.button(g, cx + cw - 34, py + 49, 34, 14, t("selection.clear"), ButtonKind.DEFAULT, t("selection.clear.tip"), RoomEditor.selection::clear);
		}
		return box == null ? null : new Box(new RoomDto.P(box.minX, box.minY, box.minZ), new RoomDto.P(box.maxX - box.minX + 1, box.maxY - box.minY + 1, box.maxZ - box.minZ + 1));
	}

	private int formRows(GuiGraphics g, SlotDto slot, Box selection, int y, int x, int w) {
		int cw2 = w - LABEL_W;
		int cxl = x + LABEL_W;
		label(g, x, y, "id");
		ui.field(g, idField, cxl, y, cw2, ROW_H, idInvalid());
		y += FORM_PITCH;
		y = cycleRow(g, x, y, w, "kind", RoomUi.kind(slot.kind), dir -> {
			slot.kind = RoomUi.step(RoomChecks.KINDS, slot.kind, dir);
			RoomEditor.changed();
		});
		y = cycleRow(g, x, y, w, "orientation", RoomUi.orientation(slot.orientation), dir -> {
			slot.orientation = RoomUi.step(RoomTemplate.ORIENTATIONS, slot.orientation, dir);
			RoomEditor.changed();
		});
		ui.button(g, x, y, w, ROW_H, t("face_me"), ButtonKind.DEFAULT, t("face_me.tip"), () -> RoomEditor.faceMe(slot));
		y += FORM_PITCH;
		label(g, x, y, "position");
		TrahisonUi.textEllipsis(g, RoomUi.pos(slot.position), cxl, y + 5, cw2, TrahisonPalette.TEXT_SECONDARY);
		y += FORM_PITCH;
		label(g, x, y, "size");
		TrahisonUi.textEllipsis(g, RoomUi.pos(slot.size), cxl, y + 5, cw2, TrahisonPalette.TEXT_SECONDARY);
		y += FORM_PITCH;
		ui.button(g, x, y, w, ROW_H, t("place_on_selection"), ButtonKind.PRIMARY, t("place_on_selection.tip"),
				selection == null ? null : () -> RoomEditor.setSlotBox(slot));
		y += FORM_PITCH;
		boolean secret = slot.id.equals(RoomEditor.room.secret);
		ui.button(g, x, y, w, ROW_H, t(secret ? "secret.unset" : "secret.set"), ButtonKind.DEFAULT, t("secret.tip"), () -> {
			RoomEditor.room.secret = secret ? null : slot.id;
			RoomEditor.changed();
		});
		y += FORM_PITCH;
		// Which steps use it, so that deleting or moving it is not blind.
		List<String> users = new ArrayList<>();
		for (StepDto step : RoomEditor.room.steps) {
			if (step.slots.containsValue(slot.id) || step.gives.stream().anyMatch(give -> slot.id.equals(give.slot))) {
				users.add(step.id);
			}
		}
		label(g, x, y, "used_by");
		TrahisonUi.textEllipsis(g, users.isEmpty() ? t("none") : Component.literal(String.join(", ", users)), cxl, y + 5, cw2,
				users.isEmpty() ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.TEXT_SECONDARY);
		y += FORM_PITCH;
		return y + 4;
	}

	private void label(GuiGraphics g, int x, int y, String key) {
		MenuUi.text(g, t("field." + key), x + 2, y + 5, LABEL_W - 6, TrahisonPalette.TEXT_MUTED);
	}

	private int cycleRow(GuiGraphics g, int x, int y, int w, String key, Component value, IntConsumer step) {
		label(g, x, y, key);
		ui.cycle(g, x + LABEL_W, y, w - LABEL_W, ROW_H, value, step);
		return y + FORM_PITCH;
	}

	// ---------------------------------------------------------------- room view

	private void drawRoom(GuiGraphics g) {
		RoomDto room = RoomEditor.room;
		int x = cx + 4;
		int w = cw - 8;
		int y = listY + 2;
		TrahisonUi.well(g, cx, listY, cw, actionY + ROW_H - listY);
		y += 4;
		label(g, x, y, "room_id");
		TrahisonUi.textEllipsis(g, Component.literal(String.valueOf(room.id)), x + LABEL_W, y + 5, w - LABEL_W, TrahisonPalette.TEXT_SECONDARY);
		y += FORM_PITCH;
		label(g, x, y, "name");
		ui.field(g, nameField, x + LABEL_W, y, w - LABEL_W, ROW_H, false);
		y += FORM_PITCH;
		label(g, x, y, "author");
		ui.field(g, authorField, x + LABEL_W, y, w - LABEL_W, ROW_H, false);
		y += FORM_PITCH;
		// The two free fields write straight into the room (there is nothing to rename).
		if (!nameField.value.equals(room.name == null ? "" : room.name)) {
			room.name = nameField.value;
			RoomEditor.changed();
		}
		if (!authorField.value.equals(room.author == null ? "" : room.author)) {
			room.author = authorField.value;
			RoomEditor.changed();
		}
		int[] totals = RoomChecks.totals(room);
		label(g, x, y, "difficulty");
		MenuUi.text(g, t("difficulty_value", totals[0]), x + LABEL_W, y + 5, w - LABEL_W, TrahisonPalette.TEXT_SECONDARY);
		y += FORM_PITCH - 6;
		label(g, x, y, "duration");
		MenuUi.text(g, t("duration_value", totals[1] / 60, totals[1] % 60), x + LABEL_W, y + 5, w - LABEL_W, TrahisonPalette.TEXT_SECONDARY);
		y += FORM_PITCH - 6;
		label(g, x, y, "counts");
		MenuUi.text(g, t("counts_value", room.steps.size(), room.slots.size()), x + LABEL_W, y + 5, w - LABEL_W, TrahisonPalette.TEXT_SECONDARY);
		y += FORM_PITCH;
		MenuUi.paragraph(g, t("room_hint"), x + 2, y, w - 4, TrahisonPalette.TEXT_MUTED);
	}

	// ---------------------------------------------------------------- errors view

	private void drawErrors(GuiGraphics g) {
		List<RoomChecks.Issue> issues = RoomEditor.issues();
		MenuUi.text(g, t("errors.title", issues.size()), cx + 2, py + 33, cw - 4, TrahisonPalette.DANGER);
		int top = py + 28 + ROW_H + 4;
		int bottom = actionY - 4;
		TrahisonUi.well(g, cx, top, cw, bottom - top);
		List<Component> texts = new ArrayList<>();
		int content = 4;
		for (RoomChecks.Issue issue : issues) {
			Component text = RoomUi.issueText(issue);
			texts.add(text);
			content += MenuUi.paragraphHeight(text, cw - 36) + 10;
		}
		errorScroll = RoomUi.clamp(errorScroll, content - (bottom - top));
		ui.clip(g, cx + 1, top + 1, cx + cw - 1, bottom - 1);
		int y = top + 2 - errorScroll;
		for (int i = 0; i < issues.size(); i++) {
			RoomChecks.Issue issue = issues.get(i);
			int h = Math.max(ROW_H, MenuUi.paragraphHeight(texts.get(i), cw - 36) + 8);
			boolean hovered = ui.hovered(cx + 2, y, cw - 8, h);
			TrahisonUi.row(g, cx + 2, y, cw - 8, h, hovered, false);
			g.fill(cx + 2, y, cx + 5, y + h, issue.warning() ? TrahisonPalette.GAME_MASTER : TrahisonPalette.DANGER);
			MenuUi.paragraph(g, texts.get(i), cx + 12, y + 4, cw - 36, TrahisonPalette.TEXT);
			ui.zone(cx + 2, y, cw - 8, h, button -> jumpTo(issue));
			y += h + 2;
		}
		ui.unclip(g);
		RoomUi.scrollbar(g, cx + cw - 5, top + 2, bottom - top - 4, errorScroll, content);
		ui.button(g, cx, actionY, cw, ROW_H, t("errors.back"), ButtonKind.PRIMARY, null, () -> view = View.SLOTS);
	}

	/** A click on a problem selects the first slot it names, or opens the chain when it names a step. */
	private void jumpTo(RoomChecks.Issue issue) {
		for (String id : issue.ids()) {
			if (RoomEditor.slot(id) != null) {
				RoomEditor.selectedSlot = id;
				view = View.SLOTS;
				listScroll = 0;
				return;
			}
			if (RoomEditor.step(id) != null) {
				RoomEditor.selectedStep = id;
				minecraft.setScreen(new ChainScreen());
				return;
			}
		}
	}

	// ---------------------------------------------------------------- footer

	private void drawFooter(GuiGraphics g) {
		boolean idle = !RoomEditor.busy;
		List<RoomChecks.Issue> issues = RoomEditor.issues();
		long errors = issues.stream().filter(i -> !i.warning()).count();
		int errorsW = 0;
		if (!issues.isEmpty() && view != View.ERRORS) {
			Component label = t(errors > 0 ? "errors.button" : "warnings.button", errors > 0 ? errors : issues.size());
			errorsW = TrahisonUi.width(label) + 10;
			int x = cx + cw - errorsW;
			TrahisonUi.pill(g, x, statusY, label, errors > 0 ? TrahisonPalette.DANGER_DARK : TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.TEXT);
			ui.zone(x, statusY, errorsW, 12, button -> {
				view = View.ERRORS;
				errorScroll = 0;
			});
		}
		Component status = RoomEditor.status;
		if (status != null) {
			TrahisonUi.textEllipsis(g, status, cx + 2, statusY + 2, cw - 4 - errorsW - (errorsW > 0 ? 4 : 0),
					RoomEditor.statusError ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_SECONDARY);
		} else {
			TrahisonUi.textEllipsis(g, t("hint"), cx + 2, statusY + 2, cw - 4 - errorsW, TrahisonPalette.TEXT_MUTED);
		}
		int half = (cw - 6) / 2;
		ui.button(g, cx, saveY, half, ROW_H, t("save"), ButtonKind.PRIMARY, t("save.tip"), idle ? () -> {
			commitId();
			RoomEditor.save();
		} : null);
		ui.button(g, cx + half + 6, saveY, half, ROW_H, t("chain"), ButtonKind.DEFAULT, t("chain.tip"), () -> {
			commitId();
			minecraft.setScreen(new ChainScreen());
		});
	}

	// ---------------------------------------------------------------- modal

	private void drawConfirm(GuiGraphics g, int mouseX, int mouseY) {
		modalUi.begin(mouseX, mouseY);
		int[] r = TrahisonUi.modal(g, width, height, 220, 88, confirm.title(), TrahisonPalette.DANGER);
		MenuUi.paragraph(g, confirm.text(), r[0], r[1] + 2, r[2], TrahisonPalette.TEXT_SECONDARY);
		int by = r[1] + r[3] - ROW_H;
		Confirm question = confirm;
		modalUi.button(g, r[0], by, 80, ROW_H, t("cancel"), () -> confirm = null);
		modalUi.button(g, r[0] + r[2] - 80, by, 80, ROW_H, question.yes(), ButtonKind.DANGER, null, () -> {
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
		ui.click(x, y, button);
		if (before != null && ui.focused() != before) {
			commitId();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int delta = (int) (-scrollY * ROW_PITCH);
		switch (view) {
			case SLOTS -> listScroll += delta;
			case FORM -> formScroll += delta;
			case ERRORS -> errorScroll += delta;
			default -> {
			}
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
				idField.value = RoomEditor.selectedSlot == null ? "" : RoomEditor.selectedSlot;
			} else {
				ui.key(keyCode, (modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || TrahisonEditor.OPEN_KEY.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && view == View.SLOTS && RoomEditor.selectedSlot != null) {
			openForm();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		return ui.charTyped(chr) || super.charTyped(chr, modifiers);
	}
}
