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
 * The player's side of the vote cabins (T2.4): what he can do where he stands during VOTE, from the view's {@code cabins}
 * and the zone {@link Zones} computed. The key ({@link Trahison#CABIN_KEY}) closes his cabin when he is inside it, and opens
 * the voting pupitre once it is closed; the pupitre also opens by itself the moment the backend says he closed it. The
 * HUD shows the matching prompt ({@code CabinPrompt}). The backend decides everything (own cabin, closed by whom); this
 * only chooses what to suggest. Client thread.
 */
public final class CabinFlow {

	/** What to suggest to the player right now. */
	public enum Prompt {
		/** Nothing: not in a vote, not a player, or votes are free. */
		NONE,
		/** In the vote step, his cabin is elsewhere. */
		GO,
		/** Standing in a cabin that is not his. */
		WRONG,
		/** In his open cabin: the key closes it. */
		CLOSE,
		/** In his cabin that he closed: the key opens the pupitre. */
		VOTE,
		/** Same, and the vote is cast. */
		VOTED,
		/** In his cabin, which his partner closed (the partner votes for the pair). */
		TAKEN
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
			if (state == null || !state.closed()) {
				return Prompt.CLOSE;
			}
			if (!state.closedByMe()) {
				return Prompt.TAKEN;
			}
			return v.viewer().ownVote() != null || v.viewer().sideVoted() ? Prompt.VOTED : Prompt.VOTE;
		}
		LayoutDto plan = Plan.get(v.hub(), v.layoutVersion());
		if (plan != null && !zone.isEmpty() && plan.cabins.stream().anyMatch(c -> zone.equals(c.zone))) {
			return Prompt.WRONG;
		}
		return Prompt.GO;
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
			VoteScreen.open(); // he just closed his cabin: the pupitre opens by itself
		}
		wasMine = mine;
		while (Trahison.CABIN_KEY.consumeClick()) {
			if (mc.screen != null || mc.player == null) {
				continue;
			}
			switch (prompt) {
				case CLOSE -> Messages.closeCabin();
				case VOTE, VOTED -> VoteScreen.open();
				default -> { }
			}
		}
	}
}
