package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.List;

/**
 * A zone: where a player is, which sound group applies, which action is valid. {@code type} is one of
 * {@link LayoutValidator#ZONE_TYPES}; the boxes of one zone may touch or overlap each other, but never another zone's.
 */
public record LayoutZone(String id, String type, List<Box> boxes) {

	public LayoutZone {
		boxes = boxes == null ? List.of() : List.copyOf(boxes);
	}
}
