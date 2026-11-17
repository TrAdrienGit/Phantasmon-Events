package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.List;

/**
 * A floor of the complex, for the map (T11): an {@code id}, a display {@code name} and a slice of heights
 * {@code yMin} to {@code yMax} (both included, world {@code y}). A plan that declares none counts one implicit floor.
 */
public record Floor(String id, String name, Integer yMin, Integer yMax) {

	/** Id of the implicit floor of a plan without any. */
	public static final String IMPLICIT_ID = "main";

	public boolean overlaps(Floor other) {
		return yMin != null && yMax != null && other.yMin != null && other.yMax != null
				&& yMin <= other.yMax && other.yMin <= yMax;
	}

	/** The floors of a plan: the declared ones, or one implicit floor covering every height. */
	public static List<Floor> effective(List<Floor> floors) {
		return floors == null || floors.isEmpty()
				? List.of(new Floor(IMPLICIT_ID, IMPLICIT_ID, Integer.MIN_VALUE, Integer.MAX_VALUE)) : floors;
	}

	/**
	 * The ids of the floors a box of heights {@code minY..maxY} belongs to: every floor whose slice it touches, so a
	 * box that straddles two floors (a staircase) is on both. A box above or below every floor is on none.
	 */
	public static List<String> floorsOf(List<Floor> floors, int minY, int maxY) {
		return effective(floors).stream()
				.filter(f -> f.yMin() != null && f.yMax() != null && minY <= f.yMax() && f.yMin() <= maxY)
				.map(Floor::id).toList();
	}
}
