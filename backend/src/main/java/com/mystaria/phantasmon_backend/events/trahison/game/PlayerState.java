package com.mystaria.phantasmon_backend.events.trahison.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One of the nine players. Player {@code i} wears bracelet {@code i}. */
public class PlayerState {
	public UUID uuid;
	public String name;
	public boolean connected = true;
	public boolean alive = true;
	/** A fake player added by the game master (never receives messages, acts through {@code Bots}). */
	public boolean bot;

	// Fields for later steps (T6, T7), unused in T1.
	public String zone;
	/** Epoch millis until which the player is restrained; null when free. */
	public Long restrainedUntil;
	public String role;
	public List<String> items = new ArrayList<>();
	/** Indices of bracelets picked up from bodies. */
	public List<Integer> carried = new ArrayList<>();

	public PlayerState() {
	}

	public PlayerState(UUID uuid, String name) {
		this.uuid = uuid;
		this.name = name;
	}
}
