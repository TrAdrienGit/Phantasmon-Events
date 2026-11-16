package com.mystaria.phantasmon_backend.events.trahison.room;

import com.mystaria.phantasmon_backend.events.trahison.game.TrahisonException;

/** A refused room operation. It is a {@link TrahisonException}: whatever answers those with an error code answers these. */
public class RoomException extends TrahisonException {

	public static final String NO_ROOM = "ERROR_TRAHISON_NO_ROOM";
	public static final String NOT_IN_ROOM = "ERROR_TRAHISON_NOT_IN_ROOM";
	public static final String BAD_ACTION = "ERROR_TRAHISON_BAD_ACTION";
	public static final String BAD_INPUT = "ERROR_TRAHISON_BAD_INPUT";
	public static final String SLOT_INACTIVE = "ERROR_TRAHISON_SLOT_INACTIVE";
	public static final String STEP_NOT_OPEN = "ERROR_TRAHISON_STEP_NOT_OPEN";
	public static final String STEP_SOLVED = "ERROR_TRAHISON_STEP_SOLVED";
	public static final String NOTHING_TO_PICK = "ERROR_TRAHISON_NOTHING_TO_PICK";
	public static final String ROOM_FINISHED = "ERROR_TRAHISON_ROOM_FINISHED";

	public RoomException(String code, String message) {
		super(code, message);
	}
}
