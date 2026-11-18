package com.mystaria.events.trahison.screen;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.Player;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.net.Secrets;
import com.mystaria.events.trahison.ui.TrahisonPalette;
import com.mystaria.events.trahison.ui.TrahisonUi;
import com.mystaria.events.trahison.ui.TrahisonUi.ButtonKind;
import com.mystaria.events.trahison.world.HubBlocks;
import com.mystaria.events.trahison.world.Plan;
import com.mystaria.phantasmon.client.api.PhantasmonApi;
import com.mystaria.phantasmon.client.gui.menu.MenuUi;

/**
 * The secret part of the player screen (T7): the player's own role and what it remembers, the items he carries and how to
 * use them, the search of a hiding spot, the ropes, and, once the game is over, every role. It draws what {@link Secrets}
 * holds and sends through {@link Messages}; the backend checks every distance, stage and right. Client thread.
 */
final class SecretsPanel {

	private static final String T = "phantasmon_events.trahison.";
	/** A little more than the backend's 3 blocks, so a click is not wasted by a rounding. */
	private static final double REACH = 3.5;
	private static final long UNTIE_MS = 5200;

	private final ZoneUi ui;
	/** The item waiting for a target (a knife, bonds, a letter), or null. */
	private String picking;
	private int untieTarget = -1;
	private long untieAt;

	SecretsPanel(ZoneUi ui) {
		this.ui = ui;
	}

	/** Draws the panel at {@code (x, y)}, {@code w} wide; returns its height (0 when there is nothing to show). */
	int draw(GuiGraphics g, View v, int x, int y, int w) {
		if (!v.hasGame() || v.phase() == Phase.LOBBY) {
			return 0;
		}
		if (v.phase() == Phase.ENDED) {
			return drawReveal(g, v, x, y, w);
		}
		ViewerKind kind = v.viewer().kind();
		if (kind != ViewerKind.PLAYER && kind != ViewerKind.GHOST) {
			return 0;
		}
		int used = 0;
		String role = Secrets.role();
		if (role != null) {
			used += drawRole(g, v, role, x, y + used, w);
		}
		if (kind != ViewerKind.PLAYER) {
			return used;
		}
		if (Secrets.restrained()) {
			long left = Math.max(0, (Secrets.restrainedUntil() - GameView.serverNow()) / 1000);
			used += MenuUi.paragraph(g, Component.translatable(T + "secret.tied", left), x, y + used, w, TrahisonPalette.DANGER) + 4;
		}
		used += drawItems(g, v, x, y + used, w);
		used += drawWorldActions(g, v, x, y + used, w);
		used += drawRopes(g, v, x, y + used, w);
		return used;
	}

	// ---------------------------------------------------------------- role

	private int drawRole(GuiGraphics g, View v, String role, int x, int y, int w) {
		String key = role.toLowerCase(java.util.Locale.ROOT);
		int used = 0;
		int pw = TrahisonUi.pill(g, x, y, Component.translatable(T + "role." + key), TrahisonPalette.PANEL_SUNKEN, TrahisonPalette.GAME_MASTER);
		TrahisonUi.textEllipsis(g, Component.translatable(T + "secret.role_label"), x + pw + 6, y + 2, w - pw - 6, TrahisonPalette.TEXT_MUTED);
		used += 16;
		used += MenuUi.paragraph(g, Component.translatable(T + "role.desc." + key), x, y + used, w, TrahisonPalette.TEXT_SECONDARY) + 4;
		Secrets.RoleInfo info = Secrets.info();
		switch (role) {
			case "INFILTRATOR" -> {
				if (info.done()) {
					used += MenuUi.paragraph(g, Component.translatable(T + "role.infiltrator.done"), x, y + used, w, TrahisonPalette.SUCCESS) + 4;
				}
			}
			case "SEER" -> used += drawSeer(g, v, info, x, y + used, w);
			case "GAMBLER" -> used += drawGambler(g, v, info, x, y + used, w);
			default -> {
			}
		}
		return used;
	}

	private int drawSeer(GuiGraphics g, View v, Secrets.RoleInfo info, int x, int y, int w) {
		int used = 0;
		if (info.used() && info.vote() != null) {
			Component vote = info.vote().equals("BETRAY") ? Component.translatable(T + "vote.betray") : Component.translatable(T + "vote.ally");
			Component side = Shown.side("PAIR".equals(info.side()));
			used += MenuUi.paragraph(g, Component.translatable(T + "role.seer.saw", info.group() == null ? "?" : info.group() + 1, side, vote), x, y, w,
					TrahisonPalette.TEXT) + 4;
		} else if (!info.used() && v.phase() == Phase.VOTE && !v.paused()) {
			used += MenuUi.paragraph(g, Component.translatable(T + "role.seer.use"), x, y, w, TrahisonPalette.TEXT_MUTED) + 2;
			GameView.Cabins cabins = GameView.cabins();
			int half = (w - 4) / 2;
			int n = 0;
			for (int i = 0; i < cabins.cabins().size(); i++) {
				GameView.Cabin cabin = cabins.cabins().get(i);
				boolean closed = "LOCKED".equals(cabin.state()) || "RELEASED".equals(cabin.state());
				if (!closed || cabin.id().equals(cabins.myCabin())) {
					continue;
				}
				final String id = cabin.id();
				ui.button(g, x + (n % 2) * (half + 4), y + used, half, TrahisonUi.BUTTON_H, Component.translatable(T + "role.seer.cabin", i + 1),
						ButtonKind.DEFAULT, null, () -> Messages.foresee(id));
				if (n++ % 2 == 1) {
					used += TrahisonUi.BUTTON_H + 2;
				}
			}
			if (n % 2 == 1) {
				used += TrahisonUi.BUTTON_H + 2;
			}
			used += 2;
		}
		return used;
	}

	private int drawGambler(GuiGraphics g, View v, Secrets.RoleInfo info, int x, int y, int w) {
		int used = 0;
		if (info.paid()) {
			used += MenuUi.paragraph(g, Component.translatable(T + "role.gambler.paid"), x, y, w, TrahisonPalette.SUCCESS) + 4;
		}
		if (v.phase() == Phase.VOTE && !v.paused()) {
			if (info.bet() != null) {
				Component vote = Component.translatable(T + (info.bet().equals("BETRAY") ? "vote.betray" : "vote.ally"));
				used += MenuUi.paragraph(g, Component.translatable(T + "role.gambler.bet", vote), x, y, w, TrahisonPalette.TEXT) + 4;
			} else {
				used += MenuUi.paragraph(g, Component.translatable(T + "role.gambler.use"), x, y, w, TrahisonPalette.TEXT_MUTED) + 2;
				int half = (w - 4) / 2;
				ui.button(g, x, y + used, half, TrahisonUi.BUTTON_H, Component.translatable(T + "vote.ally"), ButtonKind.DEFAULT, null, () -> Messages.bet(true));
				ui.button(g, x + half + 4, y + used, half, TrahisonUi.BUTTON_H, Component.translatable(T + "vote.betray"), ButtonKind.DEFAULT, null, () -> Messages.bet(false));
				used += TrahisonUi.BUTTON_H + 6;
			}
		}
		return used;
	}

	// ---------------------------------------------------------------- items

	private int drawItems(GuiGraphics g, View v, int x, int y, int w) {
		List<String> items = Secrets.items();
		if (items.isEmpty()) {
			return 0;
		}
		int used = MenuUi.paragraph(g, Component.translatable(T + "secret.items"), x, y, w, TrahisonPalette.TEXT_MUTED) + 2;
		boolean can = !v.paused() && !Secrets.restrained();
		for (String item : items) {
			String key = item.toLowerCase(java.util.Locale.ROOT);
			TrahisonUi.row(g, x, y + used, w, 20, false, false);
			TrahisonUi.textEllipsis(g, Component.translatable(T + "item." + key), x + 6, y + used + 6, w - 70, TrahisonPalette.TEXT);
			ui.tip(x, y + used, w - 66, 20, Component.translatable(T + "item.desc." + key));
			if (item.equals("SHIELD")) {
				TrahisonUi.textEllipsis(g, Component.translatable(T + "item.auto"), x + w - 62, y + used + 6, 58, TrahisonPalette.TEXT_MUTED);
			} else {
				ui.button(g, x + w - 62, y + used + 1, 60, 18, Component.translatable(T + "item.use"), ButtonKind.DEFAULT, null,
						can ? () -> useItem(v, item) : null);
			}
			used += 22;
		}
		if (picking != null) {
			used += drawPicker(g, v, x, y + used, w);
		}
		return used + 4;
	}

	private void useItem(View v, String item) {
		switch (item) {
			case "KNIFE", "BONDS", "LETTER" -> picking = item;
			case "LOCK", "PASS" -> {
				String door = nearestDoor(v, false);
				if (door != null) {
					Messages.useObject(item, null, door, null);
				} else {
					com.mystaria.events.trahison.hud.Announcer.error(Component.translatable(T + "secret.no_door"));
				}
			}
			case "EAR" -> {
				String cabin = nearestCabin(v);
				if (cabin != null) {
					Messages.useObject(item, null, cabin, null);
				} else {
					com.mystaria.events.trahison.hud.Announcer.error(Component.translatable(T + "secret.no_door"));
				}
			}
			default -> {
			}
		}
	}

	/** The other players as buttons: a knife or bonds goes to the one picked (the backend checks the 3 blocks). */
	private int drawPicker(GuiGraphics g, View v, int x, int y, int w) {
		int used = MenuUi.paragraph(g, Component.translatable(T + ("LETTER".equals(picking) ? "secret.pick_letter" : "secret.pick")), x, y, w, TrahisonPalette.TEXT_MUTED) + 2;
		Integer own = v.viewer().index();
		for (Player p : v.players()) {
			if (own != null && p.index() == own || !p.alive() && !"LETTER".equals(picking)) {
				continue;
			}
			final String item = picking;
			final int target = p.index();
			Runnable action = "LETTER".equals(picking) ? () -> {
				// A letter needs words: they are typed with /phantasmon event trahison letter <seat> <text>
				com.mystaria.events.trahison.hud.Announcer.say(Component.translatable(T + "secret.letter_command", target));
				picking = null;
			} : () -> {
				Messages.useObject(item, target, null, null);
				picking = null;
			};
			ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, Component.literal(p.name()), ButtonKind.DEFAULT, null, action);
			used += TrahisonUi.BUTTON_H + 2;
		}
		ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, Component.translatable(T + "secret.cancel"), ButtonKind.DEFAULT, null, () -> picking = null);
		return used + TrahisonUi.BUTTON_H + 4;
	}

	// ---------------------------------------------------------------- search, unlock, untie

	private int drawWorldActions(GuiGraphics g, View v, int x, int y, int w) {
		int used = 0;
		boolean roam = v.phase() == Phase.FREE_TIME || v.viewer().canRoam();
		String spot = roam ? nearestSpot(v) : null;
		if (roam) {
			boolean can = spot != null && !v.paused() && !Secrets.restrained();
			ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, Component.translatable(T + "secret.search"), ButtonKind.DEFAULT,
					can ? null : Component.translatable(T + "secret.search.tip"), can ? () -> Messages.search(spot) : null);
			used += TrahisonUi.BUTTON_H + 4;
		}
		String locked = nearestDoor(v, true);
		if (locked != null && !v.paused() && !Secrets.restrained()) {
			ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, Component.translatable(T + "secret.unlock"), ButtonKind.DEFAULT, null, () -> Messages.unlock(locked));
			used += TrahisonUi.BUTTON_H + 4;
		}
		return used;
	}

	private int drawRopes(GuiGraphics g, View v, int x, int y, int w) {
		int used = 0;
		Integer own = v.viewer().index();
		for (Secrets.Rope rope : Secrets.ropes()) {
			if (own != null && rope.player() == own) {
				continue;
			}
			final int target = rope.player();
			boolean waiting = untieTarget == target;
			Component label = waiting ? Component.translatable(T + "secret.untying", v.nameOf(target)) : Component.translatable(T + "secret.untie", v.nameOf(target));
			ui.button(g, x, y + used, w, TrahisonUi.BUTTON_H, label, ButtonKind.DEFAULT, null, waiting || Secrets.restrained() ? null : () -> {
				untieTarget = target;
				untieAt = System.currentTimeMillis() + UNTIE_MS;
				Messages.untie(target);
			});
			used += TrahisonUi.BUTTON_H + 2;
		}
		if (untieTarget >= 0 && System.currentTimeMillis() >= untieAt) {
			Messages.untie(untieTarget); // the second call, after the waiting time, takes the rope off
			untieTarget = -1;
		}
		return used > 0 ? used + 2 : 0;
	}

	// ---------------------------------------------------------------- reveal

	private int drawReveal(GuiGraphics g, View v, int x, int y, int w) {
		List<Secrets.Reveal> reveal = Secrets.reveal();
		if (reveal == null || reveal.isEmpty()) {
			return 0;
		}
		int used = MenuUi.paragraph(g, Component.translatable(T + "ended.roles"), x, y, w, TrahisonPalette.GAME_MASTER) + 4;
		for (Secrets.Reveal r : reveal) {
			Player p = v.player(r.index());
			TrahisonUi.row(g, x, y + used, w, 18, false, false);
			if (p != null && !p.bot() && p.uuid() != null) {
				PhantasmonApi.drawPlayerHead(g, p.uuid(), x + 6, y + used + 3, 12);
			}
			TrahisonUi.textEllipsis(g, Component.literal(v.nameOf(r.index())), x + 24, y + used + 5, w / 2 - 24, TrahisonPalette.TEXT);
			Component role = Component.translatable(T + "role." + r.role().toLowerCase(java.util.Locale.ROOT));
			TrahisonUi.textEllipsis(g, r.objectiveMet() ? role.copy().append(" ").append(Component.translatable(T + "ended.objective")) : role,
					x + w / 2, y + used + 5, w / 2 - 6, r.objectiveMet() ? TrahisonPalette.SUCCESS : TrahisonPalette.TEXT_SECONDARY);
			used += 20;
		}
		return used + 4;
	}

	// ---------------------------------------------------------------- where the player stands

	/** The player's hub coordinates, or null. */
	private static double[] here() {
		Minecraft mc = Minecraft.getInstance();
		var anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		if (mc.player == null || anchor == null) {
			return null;
		}
		HubBlocks blocks = HubBlocks.of(anchor);
		return new double[] { blocks.hubX(mc.player.getX()), mc.player.getY(), blocks.hubZ(mc.player.getZ()) };
	}

	private static String nearestSpot(View v) {
		LayoutDto plan = Plan.get(v.hub(), v.layoutVersion());
		double[] at = here();
		if (plan == null || at == null) {
			return null;
		}
		String best = null;
		double bestD = REACH;
		for (LayoutDto.Spot s : plan.hidingSpots) {
			double d = Math.sqrt(Math.pow(at[0] - (s.position.x + 0.5), 2) + Math.pow(at[1] - s.position.y, 2) + Math.pow(at[2] - (s.position.z + 0.5), 2));
			if (d <= bestD) {
				bestD = d;
				best = s.id;
			}
		}
		return best;
	}

	private static double distanceToBox(double[] at, LayoutDto.Box b) {
		double dx = Math.max(Math.max(b.minX - at[0], 0), at[0] - (b.maxX + 1));
		double dy = Math.max(Math.max(b.minY - at[1], 0), at[1] - (b.maxY + 1));
		double dz = Math.max(Math.max(b.minZ - at[2], 0), at[2] - (b.maxZ + 1));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** The id of the nearest ordinary door or cabin door within reach; with {@code lockedOnly}, only one that is shut now. */
	private static String nearestDoor(View v, boolean lockedOnly) {
		LayoutDto plan = Plan.get(v.hub(), v.layoutVersion());
		double[] at = here();
		if (plan == null || at == null) {
			return null;
		}
		String best = null;
		double bestD = REACH + 1;
		for (LayoutDto.Door d : plan.doors) {
			if (d.block == null || !"plain".equals(d.type) && !"cabin".equals(d.type)) {
				continue;
			}
			boolean closed = Boolean.FALSE.equals(v.doorsOpen().get(d.id));
			if (lockedOnly && !closed) {
				continue;
			}
			double dist = distanceToBox(at, d.block);
			if (dist <= bestD) {
				bestD = dist;
				best = d.id;
			}
		}
		return best;
	}

	private static String nearestCabin(View v) {
		String door = nearestDoor(v, false);
		LayoutDto plan = Plan.get(v.hub(), v.layoutVersion());
		if (door == null || plan == null) {
			return null;
		}
		return plan.cabins.stream().filter(c -> door.equals(c.door)).map(c -> c.id).findFirst().orElse(null);
	}
}
