package com.mystaria.phantasmon_backend.events.trahison.game;

/** A refused operation; {@link #code()} is an {@code ERROR_TRAHISON_*} code the client translates. */
public class TrahisonException extends RuntimeException {

	public static final String NO_GAME = "ERROR_TRAHISON_NO_GAME";
	public static final String GAME_EXISTS = "ERROR_TRAHISON_GAME_EXISTS";
	public static final String BAD_ARGUMENT = "ERROR_TRAHISON_BAD_ARGUMENT";
	public static final String NOT_IN_LOBBY = "ERROR_TRAHISON_NOT_IN_LOBBY";
	public static final String GAME_FULL = "ERROR_TRAHISON_GAME_FULL";
	public static final String ALREADY_JOINED = "ERROR_TRAHISON_ALREADY_JOINED";
	public static final String NOT_A_PLAYER = "ERROR_TRAHISON_NOT_A_PLAYER";
	public static final String NOT_ENOUGH_PLAYERS = "ERROR_TRAHISON_NOT_ENOUGH_PLAYERS";
	public static final String WRONG_PHASE = "ERROR_TRAHISON_WRONG_PHASE";
	public static final String PAUSED = "ERROR_TRAHISON_PAUSED";
	public static final String NOT_PAUSED = "ERROR_TRAHISON_NOT_PAUSED";
	public static final String DEAD = "ERROR_TRAHISON_DEAD";
	public static final String WRONG_DOOR = "ERROR_TRAHISON_WRONG_DOOR";
	public static final String DOOR_INCOMPATIBLE = "ERROR_TRAHISON_DOOR_INCOMPATIBLE";
	public static final String ALREADY_PASSED = "ERROR_TRAHISON_ALREADY_PASSED";
	public static final String ALREADY_VOTED = "ERROR_TRAHISON_ALREADY_VOTED";
	public static final String CANNOT_OPEN_FINAL = "ERROR_TRAHISON_CANNOT_OPEN_FINAL";
	public static final String NOT_AT_FINAL_DOOR = "ERROR_TRAHISON_NOT_AT_FINAL_DOOR";
	public static final String GAME_ENDED = "ERROR_TRAHISON_GAME_ENDED";
	public static final String ADMIN_REQUIRED = "ERROR_TRAHISON_ADMIN_REQUIRED";
	public static final String NO_HUB = "ERROR_TRAHISON_NO_HUB";
	public static final String NOT_IN_HUB = "ERROR_TRAHISON_NOT_IN_HUB";
	public static final String NOT_IN_CABIN = "ERROR_TRAHISON_NOT_IN_CABIN";
	public static final String WRONG_CABIN = "ERROR_TRAHISON_WRONG_CABIN";
	public static final String CABIN_NOT_CLOSED = "ERROR_TRAHISON_CABIN_NOT_CLOSED";
	public static final String NOT_GAME_MASTER = "ERROR_TRAHISON_NOT_GAME_MASTER";
	public static final String NO_GAME_MASTER = "ERROR_TRAHISON_NO_GAME_MASTER";
	public static final String GAME_MASTER_PLAYS = "ERROR_TRAHISON_GAME_MASTER_PLAYS";
	public static final String NO_DRAW = "ERROR_TRAHISON_NO_DRAW";
	public static final String ONE_PLAYING_GAME_MASTER = "ERROR_TRAHISON_ONE_PLAYING_GAME_MASTER";
	public static final String BANK_TOO_SMALL = "ERROR_TRAHISON_BANK_TOO_SMALL";
	public static final String NO_BODY = "ERROR_TRAHISON_NO_BODY";
	public static final String BODY_TOO_FAR = "ERROR_TRAHISON_BODY_TOO_FAR";
	/** T7.1: the guardian's betray vote is refused (answered to him alone, nobody else can ever trigger it). */
	public static final String CANNOT_BETRAY = "ERROR_TRAHISON_CANNOT_BETRAY";
	public static final String NOT_YOUR_ROLE = "ERROR_TRAHISON_NOT_YOUR_ROLE";
	public static final String POWER_USED = "ERROR_TRAHISON_POWER_USED";
	public static final String ALREADY_BET = "ERROR_TRAHISON_ALREADY_BET";
	public static final String NOT_VOTED_YET = "ERROR_TRAHISON_NOT_VOTED_YET";
	/** T7.2: items. */
	public static final String NO_SUCH_SECRET_ITEM = "ERROR_TRAHISON_NO_SUCH_SECRET_ITEM";
	public static final String RESTRAINED = "ERROR_TRAHISON_RESTRAINED";
	public static final String ALREADY_RESTRAINED = "ERROR_TRAHISON_ALREADY_RESTRAINED";
	public static final String NOT_RESTRAINED = "ERROR_TRAHISON_NOT_RESTRAINED";
	public static final String TARGET_TOO_FAR = "ERROR_TRAHISON_TARGET_TOO_FAR";
	public static final String UNTYING = "ERROR_TRAHISON_UNTYING";
	public static final String NOT_LOCKABLE = "ERROR_TRAHISON_NOT_LOCKABLE";
	public static final String NOT_LOCKED = "ERROR_TRAHISON_NOT_LOCKED";
	public static final String ALREADY_LOCKED = "ERROR_TRAHISON_ALREADY_LOCKED";
	public static final String DOOR_LOCKED = "ERROR_TRAHISON_DOOR_LOCKED";
	public static final String NO_SPOT = "ERROR_TRAHISON_NO_SPOT";
	public static final String SPOT_EMPTY = "ERROR_TRAHISON_SPOT_EMPTY";
	public static final String SPOT_TOO_FAR = "ERROR_TRAHISON_SPOT_TOO_FAR";
	public static final String CANNOT_SEARCH_NOW = "ERROR_TRAHISON_CANNOT_SEARCH_NOW";
	public static final String UNKNOWN_ACTION = "ERROR_TRAHISON_UNKNOWN_ACTION";

	private final String code;

	public TrahisonException(String code, String message) {
		super(code + ": " + message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
