package com.mystaria.phantasmon_backend.events.trahison.game;

import com.mystaria.phantasmon_backend.events.trahison.rules.Placement;

/** One of the nine bracelets. Bracelet {@code i} is worn by player {@code i} from the start. */
public class BraceletState {
	public int index;
	public int points;
	/** Colour and status of this round; null before the first round. */
	public Placement placement;
	/** Player index carrying it (the wearer at the start); null when lying on a body (later steps). */
	public Integer holder;
	/** False once its wearer is dead: it then votes ALLY. */
	public boolean ownerAlive = true;

	public BraceletState() {
	}

	public BraceletState(int index, int points) {
		this.index = index;
		this.points = points;
		this.holder = index;
	}
}
