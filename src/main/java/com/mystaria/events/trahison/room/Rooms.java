package com.mystaria.events.trahison.room;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;

import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.GameView.ViewerKind;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.events.trahison.world.HubBlocks;
import com.mystaria.events.trahison.world.RoomFrame;
import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * The rooms the backend tells this client about ({@code EventsTrahisonRoom}), kept by group: only the latest message of each.
 * A player sees his own group's room as his; a spectator or a ghost receives every room and plays none. Everything the world
 * objects, the item bar, the inventory and the reading screen show comes from here, never from a guess. The rooms are
 * forgotten when a round starts (the backend sends no end message), when the game ends, and when the view is lost.
 * Client thread.
 */
public final class Rooms {

	public static final String ROOM = "EventsTrahisonRoom";

	private static final Map<Integer, RoomView> ROOMS = new TreeMap<>();
	private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
	private static int revision;

	private Rooms() {
	}

	/** Called once by {@code Trahison.register()}. */
	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if (ROOM.equals(type)) {
				Minecraft.getInstance().execute(() -> onRoom(data));
			}
		});
		GameView.addListener(Rooms::onView);
	}

	private static void onRoom(Map<String, Object> data) {
		RoomView room = RoomView.parse(data);
		String hub = Link.hub();
		if (room == null || hub != null && !room.hub().isEmpty() && !room.hub().equals(hub)) {
			return;
		}
		ROOMS.put(room.group(), room);
		changed();
	}

	private static void onView() {
		View v = GameView.current();
		if (v == null) {
			clear();
			return;
		}
		switch (v.phase()) {
			case NONE, LOBBY, PROLOGUE, DOORS, ENDED -> clear();
			default -> {
			}
		}
	}

	/** Forgets every room (new round, end of game, left the hub). */
	public static void clear() {
		if (!ROOMS.isEmpty()) {
			ROOMS.clear();
			changed();
		}
	}

	private static void changed() {
		revision++;
		for (Runnable l : LISTENERS) {
			l.run();
		}
	}

	/** Called when a room message changed what is known (client thread). */
	public static void addListener(Runnable listener) {
		LISTENERS.add(listener);
	}

	/** Counts the changes: a cheap way for a ticking class to know it must look again. */
	public static int revision() {
		return revision;
	}

	/** The index of the local player in the game when he plays (alive, not a spectator), else -1. */
	public static int ownIndex() {
		View v = GameView.current();
		if (v == null || !v.hasGame() || v.viewer().kind() != ViewerKind.PLAYER || v.viewer().index() == null) {
			return -1;
		}
		return v.viewer().index();
	}

	/** The room the local player plays, or null. */
	public static RoomView mine() {
		int own = ownIndex();
		if (own < 0) {
			return null;
		}
		for (RoomView room : ROOMS.values()) {
			if (room.members().contains(own)) {
				return room;
			}
		}
		return null;
	}

	/**
	 * The room the local player is dealing with: his own, except in the free time (D8), when it is the room whose square he
	 * stands in, if any (every room of the round can be visited and replayed). Null when he has none.
	 */
	public static RoomView current() {
		View v = GameView.current();
		if (v != null && v.phase() == Phase.FREE_TIME && ownIndex() >= 0) {
			RoomView at = roomAtPlayer();
			if (at != null) {
				return at;
			}
		}
		return mine();
	}

	/** The known room whose 48 x 48 square holds the local player (hub coordinates), or null. */
	public static RoomView roomAtPlayer() {
		Minecraft mc = Minecraft.getInstance();
		com.mystaria.phantasmon.client.hub.HubAnchorDto anchor = PhantasmonApi.hubs() == null ? null : PhantasmonApi.hubs().joinedAnchor();
		if (mc.player == null || anchor == null || !anchor.hub().equals(Link.hub())) {
			return null;
		}
		HubBlocks blocks = HubBlocks.of(anchor);
		double x = blocks.hubX(mc.player.getX());
		double z = blocks.hubZ(mc.player.getZ());
		for (RoomView room : ROOMS.values()) {
			RoomView.Place p = room.place();
			if (p != null && x >= p.x() && x < p.x() + RoomFrame.SQUARE && z >= p.z() && z < p.z() + RoomFrame.SQUARE
					&& mc.player.getY() >= p.y() - 3 && mc.player.getY() < p.y() + 30) {
				return room;
			}
		}
		return null;
	}

	/** Whether the local player is a living player and the stage lets him act in {@code room}: his own in ROOMS, any in FREE_TIME. */
	public static boolean canAct(RoomView room) {
		View v = GameView.current();
		int own = ownIndex();
		if (room == null || v == null || own < 0) {
			return false;
		}
		return switch (v.phase()) {
			case ROOMS -> room.members().contains(own) && !room.finished();
			case FREE_TIME -> true;
			default -> false;
		};
	}

	/** Whether the local player's room accepts actions now: his room runs, the stage is {@code ROOMS}, it is not finished. */
	public static boolean playing() {
		RoomView room = mine();
		View v = GameView.current();
		return room != null && !room.finished() && v != null && v.phase() == Phase.ROOMS;
	}

	/** Every known room (spectators). */
	public static Map<Integer, RoomView> all() {
		return ROOMS;
	}
}
