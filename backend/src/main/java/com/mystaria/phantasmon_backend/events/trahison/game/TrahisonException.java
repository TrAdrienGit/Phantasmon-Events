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
