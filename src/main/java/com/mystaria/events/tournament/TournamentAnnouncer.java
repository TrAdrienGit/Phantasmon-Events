package com.mystaria.events.tournament;

import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * The tournament's announcements to this player ({@code EventsTournamentAnnounce}, plan-tournois.md): in the chat,
 * and for what concerns them most (their round and opponent, their result, the end) as a title in the middle of the
 * screen, like Minecraft's {@code /title}.
 */
public final class TournamentAnnouncer {

	private TournamentAnnouncer() {
	}

	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if ("EventsTournamentAnnounce".equals(type)) {
				announce(data);
			}
		});
	}

	private static void announce(Map<String, Object> data) {
		String t = String.valueOf(data.get("tournament"));
		String kind = String.valueOf(data.get("kind"));
		switch (kind) {
			case "ROUND" -> {
				boolean bye = Boolean.TRUE.equals(data.get("bye"));
				Object round = number(data.get("round"));
				Object of = number(data.get("of"));
				title(Component.translatable("phantasmon_events.announce.round.title", round),
						bye ? Component.translatable("phantasmon_events.announce.round.bye")
								: Component.translatable("phantasmon_events.announce.round.versus", data.get("opponent")));
				chat(bye ? Component.translatable("phantasmon_events.announce.round.bye_chat", t, round, of)
						: Component.translatable("phantasmon_events.announce.round.chat", t, round, of, data.get("opponent")));
			}
			case "GAME" -> {
				Object own = number(data.get("own"));
				Object their = number(data.get("their"));
				title(Component.translatable("phantasmon_events.announce.game.title", own, their),
						Component.translatable("phantasmon_events.announce.game.subtitle", data.get("opponent")));
				chat(Component.translatable("phantasmon_events.announce.game.chat", t, own, their, number(data.get("next"))));
			}
			case "RESULT" -> {
				boolean won = Boolean.TRUE.equals(data.get("won"));
				boolean forfeit = Boolean.TRUE.equals(data.get("forfeit"));
				Component line = Component.translatable(forfeit ? "phantasmon_events.announce.result.forfeit"
						: "phantasmon_events.announce.result.line", data.get("winner"), data.get("loser"), data.get("score"));
				title(Component.translatable(won ? "phantasmon_events.announce.result.won" : "phantasmon_events.announce.result.lost")
						.withStyle(won ? ChatFormatting.GOLD : ChatFormatting.GRAY), line);
				chat(Component.literal("[" + t + "] ").append(line));
			}
			case "MATCH_WAITING" -> chat(Component.translatable("phantasmon_events.announce.waiting", t, data.get("opponent"))
					.withStyle(ChatFormatting.YELLOW));
			case "ROUND_DONE" -> chat(Component.translatable(Boolean.TRUE.equals(data.get("last"))
					? "phantasmon_events.announce.round_done.last" : "phantasmon_events.announce.round_done", t, number(data.get("round"))));
			case "FINISHED" -> {
				Object rank = number(data.get("rank"));
				title(Component.translatable("phantasmon_events.announce.finished.title").withStyle(ChatFormatting.GOLD),
						Component.translatable("phantasmon_events.announce.finished.subtitle", data.get("winner"), rank));
				chat(Component.translatable("phantasmon_events.announce.finished.chat", t, data.get("winner"), rank));
			}
			default -> {
			}
		}
	}

	/** Gson reads numbers as doubles: shown without ".0". */
	private static Object number(Object value) {
		return value instanceof Number number ? number.longValue() : value;
	}

	private static void title(Component title, Component subtitle) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setTimes(10, 70, 20);
		mc.gui.setSubtitle(subtitle);
		mc.gui.setTitle(title);
	}

	private static void chat(Component message) {
		if (Minecraft.getInstance().player != null) {
			Minecraft.getInstance().player.displayClientMessage(message.copy().withStyle(s -> s.getColor() == null
					? s.withColor(ChatFormatting.LIGHT_PURPLE) : s), false);
		}
	}
}
