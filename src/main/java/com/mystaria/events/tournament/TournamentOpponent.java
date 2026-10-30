package com.mystaria.events.tournament;

import java.util.Arrays;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * The opponent of this player's current tournament match (Adrien 2026-10-30): the round pairs players, they launch
 * the battle themselves, where and when they want. The opponent glows ({@code EntityGlowOpponentMixin}) — the real
 * player or their hub avatar — and an arrow at the top of the screen points to them, with the distance. Read from the
 * tournaments on every change and every 30 s.
 */
public final class TournamentOpponent {

	private static final int REFRESH_TICKS = 600;
	private static final String[] ARROWS = { "↑", "↗", "→", "↘", "↓", "↙", "←", "↖" };

	private static volatile String opponent;
	private static volatile String tournament;
	private static int ticks;

	private TournamentOpponent() {
	}

	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if ("EventsTournamentChanged".equals(type) || "EventsTournamentAnnounce".equals(type)) {
				refresh();
			}
		});
	}

	public static void tick() {
		if (++ticks % REFRESH_TICKS == 1 && PhantasmonApi.isAuthenticated()) {
			refresh();
		}
	}

	public static void reset() {
		opponent = null;
		tournament = null;
		ticks = 0;
	}

	/** Whether that entity is the opponent (a player, or a hub avatar named after them). */
	public static boolean isOpponent(Entity entity) {
		String name = opponent;
		return name != null && entity instanceof Player player && player != Minecraft.getInstance().player
				&& player.getGameProfile().getName().equalsIgnoreCase(name);
	}

	private static void refresh() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !PhantasmonApi.isAuthenticated()) {
			return;
		}
		String me = mc.player.getGameProfile().getName();
		PhantasmonApi.get("/tournaments", TournamentDto[].class).thenAccept(all -> mc.execute(() -> {
			String found = null;
			String in = null;
			for (TournamentDto t : Arrays.asList(all)) {
				if (!"RUNNING".equals(t.status())) {
					continue;
				}
				for (TournamentDto.Match m : t.currentMatches()) {
					if (m.player2() == null || "DONE".equals(m.status())) {
						continue;
					}
					if (me.equalsIgnoreCase(m.player1())) {
						found = m.player2();
					} else if (me.equalsIgnoreCase(m.player2())) {
						found = m.player1();
					}
					if (found != null) {
						in = t.name();
						break;
					}
				}
				if (found != null) {
					break;
				}
			}
			opponent = found;
			tournament = in;
		}));
	}

	/** HUD: who to battle and where they are. */
	public static void render(GuiGraphics g) {
		String name = opponent;
		Minecraft mc = Minecraft.getInstance();
		if (name == null || mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		Player target = null;
		for (Player p : mc.level.players()) {
			if (isOpponent(p) && (target == null || p.distanceTo(mc.player) < target.distanceTo(mc.player))) {
				target = p;
			}
		}
		Component line;
		if (target == null) {
			line = Component.translatable("phantasmon_events.opponent.far", tournament, name);
		} else {
			double dx = target.getX() - mc.player.getX();
			double dz = target.getZ() - mc.player.getZ();
			float toward = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
			float relative = Mth.wrapDegrees(toward - mc.player.getYRot());
			String arrow = ARROWS[Math.floorMod(Math.round(relative / 45f), 8)];
			line = Component.translatable("phantasmon_events.opponent.near", tournament, name, arrow,
					Math.round(Math.sqrt(dx * dx + dz * dz)));
		}
		int w = mc.font.width(line);
		int x = (g.guiWidth() - w) / 2;
		g.fill(x - 4, 4, x + w + 4, 17, 0x90000000);
		g.drawString(mc.font, line.copy().withStyle(ChatFormatting.LIGHT_PURPLE), x, 7, 0xFFFFFFFF, true);
	}
}
