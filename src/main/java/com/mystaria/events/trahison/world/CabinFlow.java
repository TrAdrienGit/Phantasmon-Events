package com.mystaria.events.trahison.world;

import java.util.Locale;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.mystaria.events.trahison.Trahison;
import com.mystaria.events.trahison.edit.LayoutDto;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Cabins;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.screen.VoteScreen;

/**
 * The player's side of the vote cabins (T2.4, D4, D5): what he can do where he stands during VOTE, from the view's {@code cabins}
 * and the zone {@link Zones} computed. Walking into his own cabin is what holds it: a solo locks it at once, a pair has
 * {@code cabin_partner_seconds} for the partner to join, and the key ({@link Trahison#CABIN_KEY}) locks it right now without
 * waiting. Once it is locked the key opens the voting pupitre (it also opens by itself); once his group has voted, the
 * cabin's door opens for him and he may leave. The HUD shows the matching prompt ({@code CabinPrompt}). The backend decides
 * everything (own cabin, who is let in, when it locks); this only chooses what to suggest. Client thread.
 */
public final class CabinFlow {

	/** What to suggest to the player right now. */
	public enum Prompt {
		/** Nothing: not in a vote, not a player, or votes are free. */
		NONE,
		/** In the vote step, his cabin is elsewhere. */
		GO,
		/** His partner is already in their cabin and waits for him (the countdown runs). */
		JOIN,
		/** Standing in a cabin that is not his. */
		WRONG,
		/** In his cabin, waiting for the partner: the key locks it now. */
		WAIT,
		/** In his locked cabin: the key opens the voting pupitre. */
		VOTE,
		/** Same, and his group voted: he may leave. */
		VOTED,
		/** His cabin was locked without him (his partner votes for the pair). */
		LOCKED_OUT
	}

	private static boolean wasMine;

	private CabinFlow() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CabinFlow::tick);
	}

	/** The plan's cabin the viewer closes and votes in, or null. */
	public static LayoutDto.Cabin myCabin() {
		View v = GameView.current();
		Cabins cabins = GameView.cabins();
		if (!voting(v) || cabins.myCabin() == null) {
			return null;
		}
		LayoutDto plan = Plan.get(v.hub(), v.layoutVersion());
		if (plan == null) {
			return null;
		}
		return plan.cabins.stream().filter(c -> cabins.myCabin().equals(c.id)).findFirst().orElse(null);
	}

	/** A VOTE step in which this viewer is a living player and the votes go through the cabins. */
	private static boolean voting(View v) {
		return v != null && v.hasGame() && v.phase() == Phase.VOTE && v.viewer().kind() == ViewerKind.PLAYER && GameView.cabins().required();
	}

	public static Prompt prompt() {
		View v = GameView.current();
		LayoutDto.Cabin mine = myCabin();
		if (!voting(v) || mine == null) {
			return Prompt.NONE;
		}
		String zone = Zones.current();
		if (mine.zone != null && mine.zone.equals(zone)) {
			GameView.Cabin state = GameView.cabins().mine();
			if (state == null || !state.locked() && !state.waiting()) {
				return Prompt.WAIT; // just walked in: the backend locks or starts the window with the next zone report
			}
			if (state.waiting()) {
				return Prompt.WAIT;
			}
			return v.viewer().ownVote() != null || v.viewer().sideVoted() ? Prompt.VOTED : Prompt.VOTE;
		}
		LayoutDto plan = Plan.get(v.hub(), v.layoutVersion());
		if (plan != null && !zone.isEmpty() && plan.cabins.stream().anyMatch(c -> zone.equals(c.zone))) {
			return Prompt.WRONG;
		}
		GameView.Cabin state = GameView.cabins().mine();
		if (state != null && state.waiting()) {
			return Prompt.JOIN;
		}
		if (state != null && state.locked() && !v.viewer().sideVoted()) {
			return Prompt.LOCKED_OUT;
		}
		return Prompt.GO;
	}

	/** Seconds left for the partner to join (the countdown of a waiting cabin of his), or 0. */
	public static int partnerSeconds() {
		GameView.Cabin state = GameView.cabins().mine();
		if (state == null || state.windowUntil() == null) {
			return 0;
		}
		return (int) Math.max(0, (state.windowUntil() - GameView.serverNow() + 999) / 1000);
	}

	/** He stands inside his own cabin, locked: the pupitre works (and still shows once his group voted). */
	public static boolean atPupitre() {
		Prompt p = prompt();
		return p == Prompt.VOTE || p == Prompt.VOTED;
	}

	/** {@code Red + Green, pair}: a cabin by the colour pair it serves and its side. */
	public static Component cabinName(LayoutDto.Cabin cabin) {
		String serves = cabin.serves == null ? "" : cabin.serves;
		String[] colors = serves.split("_");
		Component pair = colors.length == 2
				? Component.translatable("phantasmon_events.trahison.color." + colors[0]).append(" + ").append(Component.translatable("phantasmon_events.trahison.color." + colors[1]))
				: Component.literal(cabin.id == null ? "?" : cabin.id);
		Component side = Component.translatable("phantasmon_events.trahison.status." + ("solo".equals(cabin.side) ? "solo" : "pair")).copy();
		return Component.empty().append(pair).append(", ").append(Component.literal(side.getString().toLowerCase(Locale.ROOT)));
	}

	private static void tick(Minecraft mc) {
		Prompt prompt = prompt();
		boolean mine = prompt == Prompt.VOTE || prompt == Prompt.VOTED;
		if (mine && !wasMine && mc.screen == null && mc.player != null && prompt == Prompt.VOTE) {
			VoteScreen.open(); // his cabin just locked with him inside: the pupitre opens by itself
		}
		wasMine = mine;
		while (Trahison.CABIN_KEY.consumeClick()) {
			if (mc.screen != null || mc.player == null) {
				continue;
			}
			switch (prompt) {
				case WAIT -> Messages.closeCabin();
				case VOTE, VOTED -> VoteScreen.open();
				default -> { }
			}
		}
	}
}
