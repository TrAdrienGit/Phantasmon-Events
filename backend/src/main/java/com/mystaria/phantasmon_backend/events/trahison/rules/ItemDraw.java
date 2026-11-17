package com.mystaria.phantasmon_backend.events.trahison.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Where the hidden items go at the start of a game (T7.2): a pure draw from the seed. */
public final class ItemDraw {

	private ItemDraw() {
	}

	public static final int MAX_PER_ITEM = 2;

	/** {@code placed}: hiding spot to item (one item per spot); {@code unplaced}: the items that found no spot. */
	public record Result(Map<String, Item> placed, List<Item> unplaced) {
	}

	/** {@code counts}: how many of each item (clamped to 0..{@link #MAX_PER_ITEM}); {@code spots}: the ids of the hiding spots. */
	public static Result draw(long seed, Map<Item, Integer> counts, Collection<String> spots) {
		List<Item> items = new ArrayList<>();
		for (Item item : Item.values()) {
			int n = Math.max(0, Math.min(MAX_PER_ITEM, counts.getOrDefault(item, 0)));
			for (int i = 0; i < n; i++) {
				items.add(item);
			}
		}
		List<String> free = new ArrayList<>(new java.util.TreeSet<>(spots));
		Random random = new Random(seed ^ 0x17E45CAFEL);
		Collections.shuffle(items, random);
		Collections.shuffle(free, random);
		Map<String, Item> placed = new LinkedHashMap<>();
		List<Item> unplaced = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			if (i < free.size()) {
				placed.put(free.get(i), items.get(i));
			} else {
				unplaced.add(items.get(i));
			}
		}
		return new Result(placed, unplaced);
	}
}
