package com.mystaria.phantasmon_backend.events.trahison.pokemon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The draw of the partner Pokemon of a room's group (T8.3). Every member gets one partner holding one power. The powers the room
 * asks for (its {@code power_spot} steps) go to <em>different</em> members, as long as the group has enough members; the other
 * members get the powers not used yet, then any. Deterministic for a given seed and member list.
 */
public final class Partners {

	/** Species that stand for each power (Cobblemon ids, gen 1 to 3): a flashing fish, a strong fighter, a bladed bug, a water rider. */
	static final Map<Power, List<String>> SPECIES = Map.of(
			Power.FLASH, List.of("chinchou", "lanturn", "volbeat"),
			Power.STRENGTH, List.of("machop", "geodude", "makuhita"),
			Power.CUT, List.of("scyther", "sneasel", "farfetchd"),
			Power.SURF, List.of("lapras", "wailmer", "azumarill"));

	/** The partner every ghost gets, whatever the room: a Ghost type with no power. */
	public static final String GHOST_SPECIES = "gastly";

	private Partners() {
	}

	/**
	 * @param seed     the room's seed
	 * @param members  player indices of the group
	 * @param required the powers the room asks for, in the order its steps list them (duplicates allowed)
	 */
	public static List<Partner> draw(long seed, List<Integer> members, List<Power> required) {
		SplittableRandom rng = new SplittableRandom(seed * 0x9E3779B97F4A7C15L + 0x7A52D1L);
		List<Integer> order = new ArrayList<>(members);
		shuffle(order, rng);
		Set<Power> distinct = new LinkedHashSet<>(required);
		List<Power> pool = new ArrayList<>(distinct);
		List<Power> rest = new ArrayList<>(Power.ALL);
		rest.removeAll(distinct);
		shuffle(rest, rng);
		pool.addAll(rest);
		List<Partner> out = new ArrayList<>();
		for (int i = 0; i < order.size(); i++) {
			Power power = pool.get(i % pool.size());
			List<String> species = SPECIES.get(power);
			out.add(new Partner(order.get(i), species.get(rng.nextInt(species.size())), power));
		}
		out.sort(java.util.Comparator.comparingInt(Partner::player));
		return List.copyOf(out);
	}

	private static <T> void shuffle(List<T> list, SplittableRandom rng) {
		for (int i = list.size() - 1; i > 0; i--) {
			Collections.swap(list, i, rng.nextInt(i + 1));
		}
	}
}
