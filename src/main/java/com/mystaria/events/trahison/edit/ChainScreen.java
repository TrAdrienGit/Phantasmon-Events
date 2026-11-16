package com.mystaria.events.trahison.edit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;

import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.edit.RoomDto.GiveDto;
import com.mystaria.events.trahison.edit.RoomDto.SlotDto;
import com.mystaria.events.trahison.edit.RoomDto.StepDto;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

import static com.mystaria.events.trahison.edit.RoomUi.t;

/**
 * The chain screen of the room editor (charte-ui.md, "window" mode, a centred stage): the steps in order on the left, the
 * selected step on the right: its mechanism, its parameters (generic key and value rows, checked as far as the hard-coded
 * {@link MechanismCatalog} knows), which slot each role uses, which steps it needs, which clues it gives and where, and up to
 * three hints. Each edit lands in {@link RoomEditor#room} at once; the backend decides what is valid. Escape goes back to the
 * panel ({@link RoomScreen}); closing loses nothing.
 */
final class ChainScreen extends Screen {

	private static final int W = 480;
	private static final int H = 300;
	private static final int ROW_H = 18;
	private static final int ROW_PITCH = 20;
	private static final int FORM_PITCH = 22;
	private static final int LIST_W = 152;
	private static final int LABEL_W = 70;
	private static final int PAD = 8;
	private static final int MAX_HINT = 160;

	private record Confirm(Component title, Component text, Component yes, Runnable action) {
	}

	/** One parameter row: its key and value fields. */
	private record ParamRow(PanelUi.Field key, PanelUi.Field value) {
	}

	private final PanelUi ui = new PanelUi();
	private final PanelUi modalUi = new PanelUi();
	private final PanelUi.Field idField = new PanelUi.Field(40);
	private final List<ParamRow> params = new ArrayList<>();
	private final PanelUi.Field[] hints = { new PanelUi.Field(MAX_HINT, true), new PanelUi.Field(MAX_HINT, true), new PanelUi.Field(MAX_HINT, true) };
	/** The step and mechanism the parameter and hint fields were loaded from; they are reloaded when either changes. */
	private String loadedStep;
	private String loadedMechanism;
	private Confirm confirm;
	private int listScroll;
	private int formScroll;

	private int sx;
	private int sy;
	private int sw;
	private int sh;
	private int footerY;
	private int statusY;

	ChainScreen() {
		super(t("chain.title"));
		if (RoomEditor.selectedStep == null && !RoomEditor.room.steps.isEmpty()) {
			RoomEditor.selectedStep = RoomEditor.room.steps.get(0).id;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		commitId();
	}

	@Override
	public void onClose() {
		commitId();
		minecraft.setScreen(RoomEditor.isActive() ? new RoomScreen() : null);
	}

	// ---------------------------------------------------------------- frame

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (!RoomEditor.isActive()) {
			minecraft.setScreen(null);
			return;
		}
		int[] r = TrahisonUi.stage(width, height, W, H);
		sx = r[0];
		sy = r[1];
		sw = r[2];
		sh = r[3];
		footerY = sy + sh - PAD - ROW_H;
		statusY = footerY - 4 - 12;

		boolean modal = confirm != null;
		TrahisonUi.veil(g, width, height);
		ui.begin(modal ? -1000 : mouseX, modal ? -1000 : mouseY);
		TrahisonUi.titledPanel(g, sx, sy, sw, sh, t("chain.title"), TrahisonPalette.ACCENT);
		titlePills(g);
		StepDto step = RoomEditor.selectedStep == null ? null : RoomEditor.step(RoomEditor.selectedStep);
		if (step == null) {
			RoomEditor.selectedStep = null;
		}
		drawList(g, step);
		if (step != null) {
			drawForm(g, step);
		} else {
			MenuUi.paragraph(g, t("chain.empty"), sx + PAD + LIST_W + 16, sy + 36, sw - LIST_W - 2 * PAD - 16, TrahisonPalette.TEXT_MUTED);
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
		int right = sx + sw - 6;
		if (RoomEditor.dirty) {
			Component label = t("pill.unsaved");
			int w = TrahisonUi.width(label) + 10;
			right -= w;
			TrahisonUi.pill(g, right, sy + 4, label, TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.GAME_MASTER);
			right -= 4;
		}
		int[] totals = RoomChecks.totals(RoomEditor.room);
		Component total = t("chain.totals", totals[0], totals[1] / 60, totals[1] % 60);
		int w = TrahisonUi.width(total) + 10;
		TrahisonUi.pill(g, right - w, sy + 4, total, TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.TEXT_SECONDARY);
	}

	// ---------------------------------------------------------------- step list

	private void drawList(GuiGraphics g, StepDto selected) {
		int x = sx + PAD;
		int y = sy + 28;
		int bw = (LIST_W - 6) / 4;
		ui.button(g, x, y, bw, ROW_H, t("chain.add"), ButtonKind.PRIMARY, t("chain.add.tip"), () -> {
			commitId();
			RoomEditor.addStep();
			formScroll = 0;
		});
		ui.button(g, x + (bw + 2), y, bw, ROW_H, t("chain.remove"), ButtonKind.DANGER, null, selected == null ? null : () -> askRemove(selected.id));
		ui.button(g, x + 2 * (bw + 2), y, bw, ROW_H, t("chain.up"), ButtonKind.DEFAULT, t("chain.up.tip"), selected == null ? null : () -> RoomEditor.moveStep(selected.id, -1));
		ui.button(g, x + 3 * (bw + 2), y, bw, ROW_H, t("chain.down"), ButtonKind.DEFAULT, t("chain.down.tip"), selected == null ? null : () -> RoomEditor.moveStep(selected.id, 1));
		int top = y + ROW_H + 4;
		int listH = statusY - 4 - top;
		TrahisonUi.well(g, x, top, LIST_W, listH);
		List<StepDto> steps = RoomEditor.room.steps;
		int content = 4 + steps.size() * ROW_PITCH;
		listScroll = RoomUi.clamp(listScroll, content - listH);
		ui.clip(g, x + 1, top + 1, x + LIST_W - 1, top + listH - 1);
		int ry = top + 2 - listScroll;
		for (int i = 0; i < steps.size(); i++) {
			StepDto step = steps.get(i);
			int w = LIST_W - (content > listH ? 8 : 4);
			boolean isSelected = step.id.equals(RoomEditor.selectedStep);
			TrahisonUi.row(g, x + 2, ry, w, ROW_H, ui.hovered(x + 2, ry, w, ROW_H), isSelected);
			MenuUi.text(g, Component.literal(Integer.toString(i + 1)), x + 8, ry + 5, 12, TrahisonPalette.TEXT_MUTED);
			TrahisonUi.textEllipsis(g, Component.literal(step.id + "  " + RoomUi.mechanism(step.mechanism == null ? "" : step.mechanism).getString()),
					x + 22, ry + 5, w - 26, TrahisonPalette.TEXT);
			if (RoomEditor.hasIssue(step.id)) {
				g.fill(x + 2 + w - 2, ry, x + 2 + w, ry + ROW_H, TrahisonPalette.DANGER);
			}
			String id = step.id;
			ui.zone(x + 2, ry, w, ROW_H, button -> {
				if (!id.equals(RoomEditor.selectedStep)) {
					commitId();
					RoomEditor.selectedStep = id;
					formScroll = 0;
				}
			});
			ry += ROW_PITCH;
		}
		if (steps.isEmpty()) {
			MenuUi.paragraph(g, t("chain.list_empty"), x + 6, top + 6, LIST_W - 12, TrahisonPalette.TEXT_MUTED);
		}
		ui.unclip(g);
		RoomUi.scrollbar(g, x + LIST_W - 5, top + 2, listH - 4, listScroll, content);
	}

	private void askRemove(String id) {
		confirm = new Confirm(t("chain.confirm_remove.title"), t("chain.confirm_remove.text", id), t("chain.remove"), () -> RoomEditor.removeStep(id));
	}

	// ---------------------------------------------------------------- step form

	/** Applies the id typed in the form: renames the step, or puts the old id back when it is refused. */
	private void commitId() {
		String id = RoomEditor.selectedStep;
		if (id == null || RoomEditor.step(id) == null || idField.value.isEmpty()) {
			return;
		}
		if (!idField.value.equals(id) && !RoomEditor.renameStep(id, idField.value)) {
			RoomEditor.setStatus(t("id_refused", idField.value), true);
			idField.value = id;
		}
		loadedStep = RoomEditor.selectedStep;
	}

	private boolean idInvalid(StepDto step) {
		return !idField.value.equals(step.id) && (!RoomChecks.validId(idField.value) || RoomEditor.step(idField.value) != null);
	}

	/** Reloads the id, parameter and hint fields when another step (or another mechanism) is shown. */
	private void syncFields(StepDto step) {
		String mechanism = step.mechanism == null ? "" : step.mechanism;
		if (step.id.equals(loadedStep) && mechanism.equals(loadedMechanism)) {
			return;
		}
		loadedStep = step.id;
		loadedMechanism = mechanism;
		idField.value = step.id;
		params.clear();
		for (var entry : step.params.entrySet()) {
			if (!entry.getValue().isJsonPrimitive()) {
				// Nested values are not editable here: they are kept as they are (see applyFields).
				continue;
			}
			ParamRow row = new ParamRow(new PanelUi.Field(30), new PanelUi.Field(60, true));
			row.key().value = entry.getKey();
			row.value().value = entry.getValue().getAsString();
			params.add(row);
		}
		for (int i = 0; i < hints.length; i++) {
			hints[i].value = i < step.hints.size() ? step.hints.get(i) : "";
		}
	}

	/** Writes the parameter and hint fields back into the step (rows left blank are not saved). */
	private void applyFields(StepDto step) {
		JsonObject object = new JsonObject();
		step.params.entrySet().stream().filter(e -> !e.getValue().isJsonPrimitive()).forEach(e -> object.add(e.getKey(), e.getValue()));
		for (ParamRow row : params) {
			if (!row.key().value.isBlank() && !row.value().value.isBlank()) {
				RoomEditor.setParam(object, row.key().value.trim(), row.value().value.trim());
			}
		}
		if (!object.equals(step.params)) {
			step.params = object;
			RoomEditor.changed();
		}
		List<String> list = new ArrayList<>();
		for (PanelUi.Field hint : hints) {
			if (!hint.value.isBlank()) {
				list.add(hint.value.trim());
			}
		}
		if (!list.equals(step.hints)) {
			step.hints = list;
			RoomEditor.changed();
		}
	}

	private void drawForm(GuiGraphics g, StepDto step) {
		syncFields(step);
		int x = sx + PAD + LIST_W + 8;
		int w = sx + sw - PAD - x;
		int top = sy + 28;
		int bottom = statusY - 4;
		TrahisonUi.well(g, x, top, w, bottom - top);
		ui.clip(g, x + 1, top + 1, x + w - 1, bottom - 1);
		int content = formRows(g, step, top + 4 - formScroll, x + 4, w - 14) + formScroll - top;
		ui.unclip(g);
		formScroll = RoomUi.clamp(formScroll, content - (bottom - top));
		RoomUi.scrollbar(g, x + w - 6, top + 2, bottom - top - 4, formScroll, content);
		applyFields(step);
	}

	private void label(GuiGraphics g, int x, int y, String key) {
		MenuUi.text(g, t("field." + key), x + 2, y + 5, LABEL_W - 6, TrahisonPalette.TEXT_MUTED);
	}

	private void heading(GuiGraphics g, int x, int y, int w, String key) {
		MenuUi.text(g, t("section." + key), x + 2, y + 2, w - 4, TrahisonPalette.ACCENT);
		g.fill(x + 2, y + 12, x + w - 2, y + 13, TrahisonPalette.EDGE);
	}

	private int cycleRow(GuiGraphics g, int x, int y, int w, String key, Component value, IntConsumer step) {
		label(g, x, y, key);
		ui.cycle(g, x + LABEL_W, y, w - LABEL_W, ROW_H, value, step);
		return y + FORM_PITCH;
	}

	private int formRows(GuiGraphics g, StepDto step, int y, int x, int w) {
		int fieldW = w - LABEL_W;
		// The problems that name this step, first.
		for (RoomChecks.Issue issue : RoomEditor.issues()) {
			if (issue.ids().contains(step.id)) {
				Component text = RoomUi.issueText(issue);
				int h = MenuUi.paragraphHeight(text, w - 12);
				g.fill(x, y, x + 2, y + h, issue.warning() ? TrahisonPalette.GAME_MASTER : TrahisonPalette.DANGER);
				MenuUi.paragraph(g, text, x + 6, y, w - 12, issue.warning() ? TrahisonPalette.TEXT_SECONDARY : TrahisonPalette.DANGER);
				y += h + 4;
			}
		}
		label(g, x, y, "id");
		ui.field(g, idField, x + LABEL_W, y, fieldW, ROW_H, idInvalid(step));
		y += FORM_PITCH;
		List<String> mechanisms = MechanismCatalog.ids();
		List<String> options = new ArrayList<>(mechanisms);
		if (step.mechanism != null && !options.contains(step.mechanism)) {
			options.add(0, step.mechanism);
		}
		y = cycleRow(g, x, y, w, "mechanism", RoomUi.mechanism(step.mechanism == null ? "" : step.mechanism), dir -> RoomEditor.setMechanism(step, RoomUi.step(options, step.mechanism, dir)));
		if (MechanismCatalog.usingFallback()) {
			MenuUi.paragraph(g, t("mechanism.fallback"), x + 2, y - 2, w - 4, TrahisonPalette.GAME_MASTER);
			y += MenuUi.paragraphHeight(t("mechanism.fallback"), w - 4) + 4;
		}
		var info = MechanismCatalog.find(step.mechanism);
		if (info.isEmpty() || !info.get().known()) {
			MenuUi.paragraph(g, t(info.isEmpty() ? "mechanism.unlisted" : "mechanism.planned"), x + 2, y - 2, w - 4, TrahisonPalette.GAME_MASTER);
			y += MenuUi.paragraphHeight(t(info.isEmpty() ? "mechanism.unlisted" : "mechanism.planned"), w - 4) + 4;
		}

		// ---- parameters
		heading(g, x, y, w, "params");
		y += 18;
		Set<String> seen = new HashSet<>();
		for (ParamRow row : params) {
			boolean keyBad = row.key().value.isBlank() || !seen.add(row.key().value);
			boolean valueBad = row.value().value.isBlank() || outOfRange(step.mechanism, row.key().value, row.value().value);
			int keyW = Math.min(90, fieldW / 2);
			ui.field(g, row.key(), x + 2, y, keyW, ROW_H, keyBad);
			ui.field(g, row.value(), x + 2 + keyW + 4, y, w - 4 - keyW - 4 - ROW_H - 4, ROW_H, valueBad);
			ParamRow target = row;
			ui.button(g, x + w - 4 - ROW_H, y, ROW_H, ROW_H, t("param.remove"), ButtonKind.DANGER, null, () -> params.remove(target));
			y += FORM_PITCH;
		}
		ui.button(g, x + 2, y, w - 4, ROW_H, t("param.add"), ButtonKind.DEFAULT, t("param.add.tip"), () -> params.add(new ParamRow(new PanelUi.Field(30), new PanelUi.Field(60, true))));
		y += FORM_PITCH;
		var known = MechanismCatalog.params(step.mechanism);
		if (!known.isEmpty()) {
			StringBuilder text = new StringBuilder();
			for (MechanismCatalog.Param p : known) {
				text.append(text.isEmpty() ? "" : "; ").append(p.name()).append(" (").append(p.type())
						.append("int".equals(p.type()) ? " " + p.min() + ".." + p.max() : "").append(')');
			}
			MenuUi.paragraph(g, t("param.known", text.toString()), x + 2, y - 2, w - 4, TrahisonPalette.TEXT_MUTED);
			y += MenuUi.paragraphHeight(t("param.known", text.toString()), w - 4) + 4;
		}

		// ---- roles
		heading(g, x, y, w, "roles");
		y += 18;
		for (String role : MechanismCatalog.roles(step.mechanism)) {
			boolean exitRole = MechanismCatalog.EXIT_ROLE.equals(role);
			List<String> slotOptions = new ArrayList<>();
			slotOptions.add(null);
			for (SlotDto slot : RoomEditor.room.slots) {
				if (!exitRole || "exit".equals(slot.kind)) {
					slotOptions.add(slot.id);
				}
			}
			String bound = step.slots.get(role);
			MenuUi.text(g, Component.literal(role), x + 2, y + 5, LABEL_W - 6, exitRole ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_SECONDARY);
			ui.cycle(g, x + LABEL_W, y, fieldW, ROW_H, bound == null ? t("none") : Component.literal(bound),
					dir -> RoomEditor.bindRole(step, role, RoomUi.step(slotOptions, bound, dir)));
			y += FORM_PITCH;
		}

		// ---- needs
		heading(g, x, y, w, "needs");
		y += 18;
		int chipX = x + 2;
		int chips = 0;
		for (StepDto other : RoomEditor.room.steps) {
			if (other == step) {
				continue;
			}
			chips++;
			int chipW = Math.max(28, TrahisonUi.width(Component.literal(other.id)) + 14);
			if (chipX + chipW > x + w - 2) {
				chipX = x + 2;
				y += 20;
			}
			boolean needed = step.needs.contains(other.id);
			String otherId = other.id;
			ui.button(g, chipX, y, chipW, 16, Component.literal(other.id), needed ? ButtonKind.PRIMARY : ButtonKind.DEFAULT, t("needs.tip"),
					() -> RoomEditor.toggleNeed(step, otherId));
			chipX += chipW + 4;
		}
		if (chips == 0) {
			MenuUi.text(g, t("needs.none"), x + 2, y + 2, w - 4, TrahisonPalette.TEXT_MUTED);
		}
		y += 24;

		// ---- gives
		heading(g, x, y, w, "gives");
		y += 18;
		List<String> clues = MechanismCatalog.clues(step.mechanism);
		for (GiveDto give : step.gives) {
			List<String> clueOptions = new ArrayList<>(clues);
			if (!clueOptions.contains(give.clue)) {
				clueOptions.add(0, give.clue);
			}
			List<String> slotOptions = new ArrayList<>(RoomEditor.slotIds());
			if (!slotOptions.contains(give.slot)) {
				slotOptions.add(0, give.slot);
			}
			int half = (w - 4 - ROW_H - 8) / 2;
			ui.cycle(g, x + 2, y, half, ROW_H, Component.literal(give.clue == null || give.clue.isEmpty() ? "?" : give.clue), dir -> {
				give.clue = RoomUi.step(clueOptions, give.clue, dir);
				RoomEditor.changed();
			});
			ui.cycle(g, x + 2 + half + 4, y, half, ROW_H, Component.literal(give.slot == null || give.slot.isEmpty() ? "?" : give.slot), dir -> {
				give.slot = RoomUi.step(slotOptions, give.slot, dir);
				RoomEditor.changed();
			});
			GiveDto target = give;
			ui.button(g, x + w - 4 - ROW_H, y, ROW_H, ROW_H, t("param.remove"), ButtonKind.DANGER, null, () -> {
				step.gives.remove(target);
				RoomEditor.changed();
			});
			y += FORM_PITCH - 2;
			ui.cycle(g, x + 2, y, w - 4 - ROW_H - 4, ROW_H, t("when." + give.when), dir -> {
				give.when = "solved".equals(give.when) ? "open" : "solved";
				RoomEditor.changed();
			});
			y += FORM_PITCH;
		}
		boolean canGive = !clues.isEmpty() && !RoomEditor.room.slots.isEmpty();
		ui.button(g, x + 2, y, w - 4, ROW_H, t("give.add"), ButtonKind.DEFAULT, t(clues.isEmpty() ? "give.add.none" : "give.add.tip"),
				canGive ? () -> RoomEditor.addGive(step) : null);
		y += FORM_PITCH;

		// ---- hints
		heading(g, x, y, w, "hints");
		y += 18;
		for (int i = 0; i < hints.length; i++) {
			label(g, x, y, "hint_" + (i + 1));
			ui.field(g, hints[i], x + LABEL_W, y, fieldW, ROW_H, false);
			y += FORM_PITCH;
		}
		MenuUi.paragraph(g, t("hints.tip"), x + 2, y - 2, w - 4, TrahisonPalette.TEXT_MUTED);
		y += MenuUi.paragraphHeight(t("hints.tip"), w - 4) + 8;
		return y;
	}

	/** Whether the catalog knows the parameter and the value is outside its range, or not a whole number. */
	private static boolean outOfRange(String mechanism, String key, String value) {
		for (MechanismCatalog.Param p : MechanismCatalog.params(mechanism)) {
			if (p.name().equals(key) && "int".equals(p.type())) {
				try {
					int n = Integer.parseInt(value.trim());
					return n < p.min() || n > p.max();
				} catch (NumberFormatException ex) {
					return true;
				}
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- footer

	private void drawFooter(GuiGraphics g) {
		List<RoomChecks.Issue> issues = RoomEditor.issues();
		long errors = issues.stream().filter(i -> !i.warning()).count();
		int pillW = 0;
		int fx = sx + PAD;
		int fw = sw - 2 * PAD;
		if (!issues.isEmpty()) {
			Component label = t(errors > 0 ? "errors.button" : "warnings.button", errors > 0 ? errors : issues.size());
			pillW = TrahisonUi.width(label) + 10;
			int x = fx + fw - pillW;
			TrahisonUi.pill(g, x, statusY, label, errors > 0 ? TrahisonPalette.DANGER_DARK : TrahisonPalette.GAME_MASTER_DARK, TrahisonPalette.TEXT);
			ui.zone(x, statusY, pillW, 12, button -> {
				commitId();
				minecraft.setScreen(new RoomScreen());
			});
		}
		Component status = RoomEditor.status;
		if (status != null) {
			TrahisonUi.textEllipsis(g, status, fx + 2, statusY + 2, fw - 4 - pillW - (pillW > 0 ? 4 : 0),
					RoomEditor.statusError ? TrahisonPalette.DANGER : TrahisonPalette.TEXT_SECONDARY);
		} else {
			TrahisonUi.textEllipsis(g, t("chain.hint"), fx + 2, statusY + 2, fw - 4 - pillW, TrahisonPalette.TEXT_MUTED);
		}
		ui.button(g, fx, footerY, 100, ROW_H, t("chain.back"), ButtonKind.DEFAULT, null, this::onClose);
		ui.button(g, fx + fw - 100, footerY, 100, ROW_H, t("save"), ButtonKind.PRIMARY, t("save.tip"), RoomEditor.busy ? null : () -> {
			commitId();
			RoomEditor.save();
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
		if (before == idField && ui.focused() != before) {
			commitId();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int delta = (int) (-scrollY * ROW_PITCH);
		if (x < sx + PAD + LIST_W + 4) {
			listScroll += delta;
		} else {
			formScroll += delta;
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
			PanelUi.Field focused = ui.focused();
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_TAB) {
				ui.unfocus();
				if (focused == idField) {
					commitId();
				}
			} else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				ui.unfocus();
				if (focused == idField && RoomEditor.selectedStep != null) {
					idField.value = RoomEditor.selectedStep;
				}
			} else {
				ui.key(keyCode, (modifiers & GLFW.GLFW_MOD_CONTROL) != 0);
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || TrahisonEditor.OPEN_KEY.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		return ui.charTyped(chr) || super.charTyped(chr, modifiers);
	}
}
