package com.mystaria.events.trahison.voice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import com.mystaria.phantasmon.client.hub.HubAvatarEntity;
import com.mystaria.phantasmon.client.hub.HubAvatars;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.Messages;
import com.mystaria.events.trahison.net.VoiceData;

/**
 * The game-thread half of the betrayal game's voice (T5): each tick it turns the view into a {@link VoiceGains.Table} and
 * publishes it for {@link TrahisonVoicePlugin}, which reads it on the audio thread. This class loads no Simple Voice Chat
 * class (the mod stays optional, D-36); the plugin is the only one that does. It also holds the game master's announce key
 * (T5.3) and the admin test gains (T5.1). Client thread, except {@link #table()}.
 */
public final class TrahisonVoice {

	/** T5.3: held by a game master, everybody hears him. Unbound by default (D201). */
	public static final KeyMapping ANNOUNCE_KEY = new KeyMapping("key.phantasmon_events.trahison_announce",
			InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.phantasmon_events");

	private static volatile VoiceGains.Table table = VoiceGains.Table.OPEN;
	/** The gains forced by the admin test command (account to gain), applied to the account's own voice and to his avatar's. */
	private static volatile Map<UUID, Double> forced = Map.of();
	private static boolean announcing;

	private TrahisonVoice() {
	}

	/** The latest table; safe from any thread (an immutable object behind a volatile). */
	public static VoiceGains.Table table() {
		return table;
	}

	/** Called once by {@code Trahison.register()}. */
	public static void register() {
		KeyBindingHelper.registerKeyBinding(ANNOUNCE_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(TrahisonVoice::tick);
	}

	private static void tick(Minecraft mc) {
		announceKey();
		table = build(mc).withForced(forcedKeys());
	}

	// ---------------------------------------------------------------- announce key (T5.3)

	private static void announceKey() {
		GameView.View view = GameView.current();
		boolean allowed = view != null && view.hasGame() && (view.viewer().kind() == ViewerKind.GM || view.viewer().isGameMaster());
		boolean down = allowed && ANNOUNCE_KEY.isDown();
		if (down != announcing) {
			announcing = down;
			Messages.announce(down);
		}
	}

	// ---------------------------------------------------------------- the table

	/** Unfiltered in the lobby and after the game: the voice is the community's again. */
	static boolean filtering(GameView.View view) {
		return view != null && view.hasGame() && view.phase() != Phase.LOBBY && view.phase() != Phase.ENDED;
	}

	private static VoiceGains.Table build(Minecraft mc) {
		GameView.View view = GameView.current();
		if (!filtering(view) || mc.player == null || mc.level == null) {
			return VoiceGains.Table.OPEN;
		}
		VoiceData.Voice voice = VoiceData.current();
		UUID me = mc.player.getUUID();
		List<VoiceGains.Speaker> speakers = new ArrayList<>();
		Set<UUID> listed = new java.util.HashSet<>();
		for (GameView.Player p : view.players()) {
			if (p.uuid() == null || p.uuid().equals(me)) {
				continue;
			}
			listed.add(p.uuid());
			speakers.add(new VoiceGains.Speaker(p.uuid(), avatarOf(p.uuid()), voice.deadFor(p.index(), p.alive()), voice.same().contains(p.index()),
				voice.ear() != null && voice.ear().contains(p.index()), voice.announcing().contains(p.uuid()), distanceTo(mc, p.uuid())));
		}
		for (UUID gm : voice.announcing()) { // a game master who does not play is no player of the view
			if (!listed.contains(gm) && !gm.equals(me)) {
				speakers.add(new VoiceGains.Speaker(gm, avatarOf(gm), false, false, false, true, Double.NaN));
			}
		}
		return VoiceGains.compute(new VoiceGains.Input(true, view.viewer().kind() == ViewerKind.PLAYER, speakers));
	}

	/** The entity id the hub-copy avatar's voice arrives under (S5), or null when no avatar is shown. */
	private static UUID avatarOf(UUID account) {
		HubAvatarEntity avatar = HubAvatars.shownAvatar(account);
		return avatar == null ? null : avatar.getUUID();
	}

	private static double distanceTo(Minecraft mc, UUID account) {
		Entity e = mc.level.getPlayerByUUID(account);
		if (e == null) {
			e = HubAvatars.shownAvatar(account);
		}
		return e == null ? Double.NaN : mc.player.distanceTo(e);
	}

	// ---------------------------------------------------------------- admin test command (T5.1)

	private static Map<UUID, Double> forcedKeys() {
		Map<UUID, Double> now = forced;
		if (now.isEmpty()) {
			return now;
		}
		Map<UUID, Double> all = new HashMap<>(now);
		now.forEach((account, gain) -> {
			UUID avatar = avatarOf(account);
			if (avatar != null) {
				all.put(avatar, gain);
			}
		});
		return all;
	}

	/**
	 * {@code /phantasmon event trahison voice <player> <gain>}: forces what this client hears of a player (0 mute, 0.5, 1),
	 * game or no game. {@code player}: a name (of the game or of the server's player list) or an account id.
	 */
	public static void force(String player, double gain) {
		UUID account = resolve(player);
		if (account == null) {
			chat(Component.translatable("phantasmon_events.trahison.voice.unknown", player));
			return;
		}
		Map<UUID, Double> next = new HashMap<>(forced);
		next.put(account, Math.max(0, Math.min(1, gain)));
		forced = Map.copyOf(next);
		chat(Component.translatable("phantasmon_events.trahison.voice.forced", player, String.format(Locale.ROOT, "%.2f", Math.max(0, Math.min(1, gain)))));
	}

	/** {@code /phantasmon event trahison voice reset}: back to the game's own rules. */
	public static void resetForced() {
		forced = Map.of();
		chat(Component.translatable("phantasmon_events.trahison.voice.reset"));
	}

	private static UUID resolve(String player) {
		try {
			return UUID.fromString(player);
		} catch (IllegalArgumentException ignored) {
			// a name
		}
		GameView.View view = GameView.current();
		if (view != null) {
			for (GameView.Player p : view.players()) {
				if (p.uuid() != null && p.name().equalsIgnoreCase(player)) {
					return p.uuid();
				}
			}
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null) {
			for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
				if (info.getProfile().getName().equalsIgnoreCase(player)) {
					return info.getProfile().getId();
				}
			}
		}
		return null;
	}

	private static void chat(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(message, false);
		}
	}
}
