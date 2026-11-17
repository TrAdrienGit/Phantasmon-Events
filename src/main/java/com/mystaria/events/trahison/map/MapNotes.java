package com.mystaria.events.trahison.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import com.mystaria.events.trahison.map.MapModel.Stroke;
import com.mystaria.events.trahison.net.GameView;
import com.mystaria.events.trahison.net.GameView.Phase;
import com.mystaria.events.trahison.net.GameView.View;
import com.mystaria.events.trahison.net.Link;
import com.mystaria.phantasmon.client.api.PhantasmonApi;

/**
 * What this player drew and wrote on the map (T11.4): strokes per floor, in hub blocks, and a free text. The backend keeps them
 * (one record per game and account, never sent to anyone else while the game runs) so they come back after a disconnection or
 * on another computer: {@code get} when the game becomes known, {@code set} for every change but at most once per
 * {@value #SEND_GAP_MS} ms (the backend refuses faster writes). Once the game has ended, {@code all} brings everybody's (D15).
 * Limits mirror the backend's {@code MapNotesService}; the backend decides. Client thread.
 */
public final class MapNotes {

	public static final String MAP = "EventsTrahisonMap";

	/** Same limits as the backend. */
	public static final int MAX_POINTS_PER_FLOOR = 2000;
	public static final int MAX_NOTES = 8000;

	private static final long SEND_GAP_MS = 2200;
	private static final long RETRY_MS = 4000;

	/** One person's annotations as shown at the end of the game. */
	public record Person(int index, String name, Map<String, List<Stroke>> strokes, String notes) {
	}

	private static String gameId = "";
	private static boolean loaded;
	private static boolean dirty;
	private static long lastSent;
	private static long lastAsked;
	private static boolean askedAll;
	private static Map<String, List<Stroke>> strokes = new LinkedHashMap<>();
	private static String notes = "";
	private static List<Person> everyone;

	private MapNotes() {
	}

	public static void register() {
		PhantasmonApi.onEventsMessage((type, data) -> {
			if (MAP.equals(type)) {
				Minecraft.getInstance().execute(() -> receive(data));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		GameView.addListener(MapNotes::onView);
	}

	// ---------------------------------------------------------------- state

	/** Whether our own annotations came from the backend (before that the screen shows no pen). */
	public static boolean loaded() {
		return loaded;
	}

	/** The strokes of a floor, ours, to read or change (call {@link #changed()} after changing). */
	public static List<Stroke> strokes(String floorId) {
		return strokes.computeIfAbsent(floorId, k -> new ArrayList<>());
	}

	public static String notes() {
		return notes;
	}

	public static void setNotes(String text) {
		String next = text.length() > MAX_NOTES ? text.substring(0, MAX_NOTES) : text;
		if (!next.equals(notes)) {
			notes = next;
			dirty = true;
		}
	}

	/** The strokes were changed: they go to the backend within a couple of seconds. */
	public static void changed() {
		dirty = true;
	}

	/** Everybody's annotations once the game ended and they came; null before. */
	public static List<Person> everyone() {
		return everyone;
	}

	/** Whether the next stroke point would pass the backend's limit on this floor. */
	public static boolean floorFull(String floorId) {
		return MapModel.pointCount(strokes.getOrDefault(floorId, List.of())) >= MAX_POINTS_PER_FLOOR;
	}

	// ---------------------------------------------------------------- wire

	private static void onView() {
		View v = GameView.current();
		String id = v == null || !v.hasGame() ? "" : v.gameId();
		if (!id.equals(gameId)) {
			gameId = id;
			loaded = false;
			dirty = false;
			strokes = new LinkedHashMap<>();
			notes = "";
			everyone = null;
			askedAll = false;
			lastAsked = 0;
		}
	}

	private static void tick() {
		View v = GameView.current();
		if (v == null || !v.hasGame() || Link.hub() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!loaded && now - lastAsked >= RETRY_MS) {
			lastAsked = now;
			send("get", null);
		}
		if (loaded && dirty && v.phase() != Phase.ENDED && now - lastSent >= SEND_GAP_MS) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("strokes", encode(strokes));
			body.put("notes", notes);
			if (send("set", body)) {
				lastSent = now;
				dirty = false;
			}
		}
		if (v.phase() == Phase.ENDED && loaded && !askedAll) {
			askedAll = true;
			send("all", null);
		}
	}

	private static boolean send(String op, Map<String, Object> extra) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("op", op);
		data.put("hub", Link.hub());
		if (extra != null) {
			data.putAll(extra);
		}
		return PhantasmonApi.send(MAP, data);
	}

	private static Map<String, Object> encode(Map<String, List<Stroke>> all) {
		Map<String, Object> out = new LinkedHashMap<>();
		all.forEach((floor, list) -> {
			List<Object> encoded = new ArrayList<>();
			for (Stroke s : list) {
				if (s.pointCount() > 0) {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("c", s.color);
					List<Double> p = new ArrayList<>(s.points.size());
					s.points.forEach(n -> p.add(Math.round(n * 10.0) / 10.0));
					m.put("p", p);
					encoded.add(m);
				}
			}
			if (!encoded.isEmpty()) {
				out.put(floor, encoded);
			}
		});
		return out;
	}

	private static void receive(Map<String, Object> data) {
		String op = String.valueOf(data.get("op"));
		switch (op) {
			case "state" -> {
				if (!loaded) { // the backend's copy is only taken when we have not started to edit
					strokes = decode(data.get("strokes"));
					notes = data.get("notes") instanceof String s ? s : "";
					loaded = true;
				}
			}
			case "all" -> {
				List<Person> people = new ArrayList<>();
				if (data.get("players") instanceof List<?> list) {
					for (Object o : list) {
						if (o instanceof Map<?, ?> m) {
							people.add(new Person(m.get("index") instanceof Number i ? i.intValue() : -1,
									m.get("name") == null ? "?" : String.valueOf(m.get("name")), decode(m.get("strokes")),
									m.get("notes") instanceof String s ? s : ""));
						}
					}
				}
				everyone = List.copyOf(people);
			}
			default -> {
			}
		}
	}

	private static Map<String, List<Stroke>> decode(Object raw) {
		Map<String, List<Stroke>> out = new LinkedHashMap<>();
		if (raw instanceof Map<?, ?> floors) {
			floors.forEach((floor, list) -> {
				List<Stroke> strokesOfFloor = new ArrayList<>();
				if (list instanceof List<?> items) {
					for (Object item : items) {
						if (item instanceof Map<?, ?> m && m.get("c") instanceof Number c && m.get("p") instanceof List<?> p) {
							Stroke stroke = new Stroke(c.intValue());
							for (int i = 0; i + 1 < p.size(); i += 2) {
								if (p.get(i) instanceof Number x && p.get(i + 1) instanceof Number z) {
									stroke.add(x.doubleValue(), z.doubleValue());
								}
							}
							strokesOfFloor.add(stroke);
						}
					}
				}
				out.put(String.valueOf(floor), strokesOfFloor);
			});
		}
		return out;
	}
}
