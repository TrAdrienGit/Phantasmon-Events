package com.mystaria.events.trahison.screen;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.room.Inventory;
import com.mystaria.events.trahison.room.RoomView;
import com.mystaria.events.trahison.room.RoomView.ItemView;
import com.mystaria.events.trahison.room.Rooms;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.events.trahison.world.Interactables;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The full inventory (T3.3), opened with the inventory key inside a room. Left: what the player carries (a list with pictures),
 * and what his teammates carry. Right: the selected item (picture, name, description) and what can be done with it: hold it
 * (so that a click on an object of the world uses it), give it to a player within reach, use it on an object near him, combine it
 * with another item he carries. In the free time (D8) a button also replays the room he stands in. Every change is a message to
 * the backend; the screen only reads {@link Inventory} and {@link Rooms}, so it shows what the backend answered. Client thread.
 */
public final class InventoryScreen extends TrahisonScreen {

	private static final String T = "phantasmon_events.trahison.";
	private static final int W = 380;
	private static final int H = 236;
	private static final int ROW = 20;

	private enum Mode { NONE, GIVE, USE, COMBINE }

	private String selected;
	private Mode mode = Mode.NONE;
	private int scroll;

	public InventoryScreen() {
		super(Component.translatable(T + "inventory.title"), TrahisonPalette.ACCENT);
	}

	@Override
	protected void drawStage(GuiGraphics g, int mouseX, int mouseY) {
		RoomView room = Rooms.current();
		if (room == null) {
			onClose();
			return;
		}
		int[] r = TrahisonUi.stage(width, height, W, H);
		int x = r[0];
		int y = r[1];
		int w = r[2];
		int h = r[3];
		TrahisonUi.titledPanel(g, x, y, w, h, Component.translatable(T + "inventory.heading", room.name()), TrahisonPalette.ACCENT);
		List<ItemView> mine = Inventory.mine();
		if (selected != null && !Inventory.carries(selected)) {
			selected = null;
			mode = Mode.NONE;
		}
		int listW = 160;
		int top = y + 28;
		int listH = h - 28 - 8 - TrahisonUi.BUTTON_H - 6;
		int ownH = Math.max(ROW * 3, listH * 6 / 10);
		// ---- what I carry
		MenuUi.text(g, Component.translatable(T + "inventory.mine"), x + 8, top, listW, TrahisonPalette.TEXT_MUTED);
		int ownY = top + 12;
		TrahisonUi.well(g, x + 8, ownY, listW, ownH - 12);
		scroll = clamp(scroll, mine.size() * ROW - (ownH - 12));
		ui.clip(g, x + 8, ownY, x + 8 + listW, ownY + ownH - 12);
		if (mine.isEmpty()) {
			MenuUi.paragraph(g, Component.translatable(T + "inventory.empty"), x + 14, ownY + 6, listW - 12, TrahisonPalette.TEXT_MUTED);
		}
		for (int i = 0; i < mine.size(); i++) {
			int ry = ownY + 1 + i * ROW - scroll;
			ItemView item = mine.get(i);
			boolean hovered = ui.hovered(x + 9, ry, listW - 2, ROW - 2);
			TrahisonUi.row(g, x + 9, ry, listW - 2, ROW - 2, hovered, item.id().equals(selected));
			Inventory.drawIcon(g, item.id(), x + 12, ry + 1, 16);
			TrahisonUi.textEllipsis(g, Inventory.name(item.id()), x + 32, ry + 5, listW - 60, TrahisonPalette.TEXT);
			if (item.id().equals(Inventory.held())) {
				TrahisonUi.rotatedFill(g, x + 8 + listW - 10, ry + 8, 45f, -3, -3, 3, 3, TrahisonPalette.ACCENT);
			}
			String id = item.id();
			ui.zone(x + 9, ry, listW - 2, ROW - 2, b -> {
				selected = id;
				mode = Mode.NONE;
			});
		}
		ui.unclip(g);
		// ---- what my teammates carry
		int othersY = ownY + ownH - 12 + 6;
		MenuUi.text(g, Component.translatable(T + "inventory.others"), x + 8, othersY, listW, TrahisonPalette.TEXT_MUTED);
		TrahisonUi.well(g, x + 8, othersY + 12, listW, top + listH - othersY - 12);
		List<ItemView> others = Inventory.others();
		int oy = othersY + 13;
		for (int i = 0; i < others.size() && oy + ROW - 4 <= top + listH; i++) {
			ItemView item = others.get(i);
			Inventory.drawIcon(g, item.id(), x + 12, oy + 1, 14);
			TrahisonUi.textEllipsis(g, Inventory.name(item.id()), x + 30, oy + 3, listW - 30, TrahisonPalette.TEXT_SECONDARY);
			oy += ROW - 4;
		}
		// ---- the selected item
		int rx = x + 8 + listW + 8;
		int rw = w - (rx - x) - 8;
		TrahisonUi.well(g, rx, top, rw, listH);
		if (selected == null) {
			MenuUi.paragraph(g, Component.translatable(T + "inventory.pick"), rx + 8, top + 8, rw - 16, TrahisonPalette.TEXT_MUTED);
		} else {
			drawDetails(g, room, rx + 8, top + 8, rw - 16, listH - 16);
		}
		// ---- footer
		int by = y + h - 8 - TrahisonUi.BUTTON_H;
		if (GameView.current() != null && GameView.current().phase() == Phase.FREE_TIME) {
			ui.button(g, x + 8, by, 150, TrahisonUi.BUTTON_H, Component.translatable(T + "replay.button"), ButtonKind.DEFAULT,
					Component.translatable(T + "replay.tip"), () -> modal = new Modal(Component.translatable(T + "replay.title"),
							Component.translatable(T + "replay.text", room.name()), Component.translatable(T + "replay.confirm"), false, Messages::replay));
		}
		ui.button(g, x + w - 8 - 84, by, 84, TrahisonUi.BUTTON_H, Component.translatable(T + "close"), ButtonKind.PRIMARY, null, this::onClose);
	}

	private void drawDetails(GuiGraphics g, RoomView room, int x, int y, int w, int h) {
		String id = selected;
		Inventory.drawIcon(g, id, x, y, 32);
		MenuUi.text(g, Inventory.name(id).copy().withStyle(net.minecraft.ChatFormatting.BOLD), x + 40, y + 4, w - 40, TrahisonPalette.TEXT);
		boolean held = id.equals(Inventory.held());
		MenuUi.text(g, Component.translatable(T + (held ? "inventory.held" : "inventory.not_held")), x + 40, y + 18, w - 40,
				held ? TrahisonPalette.ACCENT : TrahisonPalette.TEXT_MUTED);
		int ty = y + 40;
		Component desc = Inventory.description(id);
		if (desc != null) {
			ty += MenuUi.paragraph(g, desc, x, ty, w, TrahisonPalette.TEXT_SECONDARY) + 6;
		}
		int bottom = y + h;
		if (mode == Mode.NONE) {
			int bw = (w - 4) / 2;
			int bh = TrahisonUi.BUTTON_H;
			int row2 = bottom - bh;
			int row1 = row2 - bh - 4;
			ui.button(g, x, row1, bw, bh, Component.translatable(T + (held ? "inventory.release" : "inventory.hold")), ButtonKind.DEFAULT,
					Component.translatable(T + "inventory.hold_tip"), () -> Inventory.hold(held ? null : id));
			List<Integer> mates = Inventory.teammates();
			ui.button(g, x + bw + 4, row1, bw, bh, Component.translatable(T + "inventory.give"), ButtonKind.DEFAULT,
					mates.isEmpty() ? Component.translatable(T + "inventory.give_none") : Component.translatable(T + "inventory.give_tip"),
					mates.isEmpty() ? null : () -> mode = Mode.GIVE);
			ui.button(g, x, row2, bw, bh, Component.translatable(T + "inventory.use"), ButtonKind.PRIMARY,
					Component.translatable(T + "inventory.use_tip"), () -> mode = Mode.USE);
			boolean canCombine = Inventory.mine().size() > 1;
			ui.button(g, x + bw + 4, row2, bw, bh, Component.translatable(T + "inventory.combine"), ButtonKind.DEFAULT,
					Component.translatable(T + (canCombine ? "inventory.combine_tip" : "inventory.combine_none")), canCombine ? () -> mode = Mode.COMBINE : null);
			return;
		}
		// a target list takes the place of the buttons
		MenuUi.text(g, Component.translatable(T + "inventory.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)), x, ty, w, TrahisonPalette.TEXT);
		int ly = ty + 14;
		int backY = bottom - TrahisonUi.BUTTON_H;
		ui.button(g, x, backY, 84, TrahisonUi.BUTTON_H, Component.translatable(T + "back"), () -> mode = Mode.NONE);
		int rows = Math.max(1, (backY - 4 - ly) / ROW);
		switch (mode) {
			case GIVE -> {
				List<Integer> mates = Inventory.teammates();
				for (int i = 0; i < mates.size() && i < rows; i++) {
					int who = mates.get(i);
					double d = Inventory.distanceTo(who);
					boolean ok = d < 0 || d <= Inventory.GIVE_REACH;
					int ry = ly + i * ROW;
					boolean hovered = ui.hovered(x, ry, w, ROW - 2);
					TrahisonUi.row(g, x, ry, w, ROW - 2, hovered && ok, false);
					TrahisonUi.textEllipsis(g, Component.literal(Inventory.teammateName(who)), x + 6, ry + 5, w - 70, ok ? TrahisonPalette.TEXT : TrahisonPalette.TEXT_DISABLED);
					MenuUi.text(g, d < 0 ? Component.translatable(T + "inventory.far_unknown") : Component.literal(String.format(java.util.Locale.ROOT, "%.1f m", d)),
							x + w - 60, ry + 5, 56, ok ? TrahisonPalette.TEXT_MUTED : TrahisonPalette.DANGER);
					ui.tip(x, ry, w, ROW - 2, ok ? null : Component.translatable(T + "inventory.too_far"));
					if (ok) {
						ui.zone(x, ry, w, ROW - 2, b -> {
							Inventory.give(id, who);
							mode = Mode.NONE;
						});
					}
				}
			}
			case USE -> {
				List<Interactables.Item> near = Interactables.nearby(room.round(), room.group());
				if (near.isEmpty()) {
					MenuUi.paragraph(g, Component.translatable(T + "inventory.use_none"), x, ly, w, TrahisonPalette.TEXT_MUTED);
				}
				for (int i = 0; i < near.size() && i < rows; i++) {
					Interactables.Item it = near.get(i);
					int ry = ly + i * ROW;
					TrahisonUi.row(g, x, ry, w, ROW - 2, ui.hovered(x, ry, w, ROW - 2), false);
					TrahisonUi.textEllipsis(g, slotName(it.slot().kind(), it.slot().id()), x + 6, ry + 5, w - 12, TrahisonPalette.TEXT);
					String slot = it.slot().id();
					int group = it.group();
					int round = it.round();
					ui.zone(x, ry, w, ROW - 2, b -> {
						Messages.inRoom(round, group, () -> Inventory.useOn(id, slot));
						mode = Mode.NONE;
					});
				}
			}
			case COMBINE -> {
				List<ItemView> mine = Inventory.mine();
				List<ItemView> choices = new ArrayList<>();
				boolean skipped = false;
				for (ItemView item : mine) {
					if (!skipped && item.id().equals(id)) {
						skipped = true; // this very copy; a second copy of the same item is a fair partner
						continue;
					}
					choices.add(item);
				}
				for (int i = 0; i < choices.size() && i < rows; i++) {
					ItemView item = choices.get(i);
					int ry = ly + i * ROW;
					TrahisonUi.row(g, x, ry, w, ROW - 2, ui.hovered(x, ry, w, ROW - 2), false);
					Inventory.drawIcon(g, item.id(), x + 4, ry + 1, 16);
					TrahisonUi.textEllipsis(g, Inventory.name(item.id()), x + 26, ry + 5, w - 32, TrahisonPalette.TEXT);
					String other = item.id();
					ui.zone(x, ry, w, ROW - 2, b -> {
						Inventory.combine(id, other);
						mode = Mode.NONE;
					});
				}
			}
			default -> {
			}
		}
	}

	private static Component slotName(String kind, String id) {
		return Component.translatable(T + "slot." + kind).append(Component.literal("  " + id).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
	}

	@Override
	protected boolean scrolled(double mouseX, double mouseY, double amount) {
		scroll -= (int) Math.signum(amount) * ROW;
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (modal == null && Inventory.KEY.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		if (modal == null && keyCode == GLFW.GLFW_KEY_BACKSPACE && mode != Mode.NONE) {
			mode = Mode.NONE;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
