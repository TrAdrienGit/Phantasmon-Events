package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A square board of blocks that only slide along their own axis. Block 0 is the piece that must leave: horizontal, two
 * cells long, on the exit row; it is free once its right end touches the right edge (the opening). A gesture
 * {@code {"block": i, "delta": d}} slides block i by {@code d} cells (signed: right or down is positive) when every cell on
 * the way is free.
 *
 * <h2>Parameters</h2>
 * <ul>
 * <li>{@code size} 5..7 (default 6): the board is size x size;</li>
 * <li>{@code blocks} 3..10 (default 6): blocks other than the piece to free;</li>
 * <li>{@code shuffle} 6..60 (default 24): legal slides played from the solved board to scramble it.</li>
 * </ul>
 *
 * <h2>Generation (solvable by construction)</h2>
 * The piece is put against the opening, the other blocks are laid out without overlap, then random legal slides are
 * played. Every slide has an inverse, so playing the scramble backwards solves the board. It never starts solved: when the
 * scramble leaves the piece against the opening, other attempts are drawn, and in the end a board with the piece alone at
 * the far end is used. Nothing here is secret: the view is the whole board.
 */
@Component
public class SlidingBlocks implements Mechanism {

	public static final String ID = "sliding_blocks";
	public static final String TERMINAL_ROLE = "terminal";

	static final int MIN_SIZE = 5;
	static final int MAX_SIZE = 7;
	static final int DEFAULT_SIZE = 6;
	static final int MIN_BLOCKS = 3;
	static final int MAX_BLOCKS = 10;
	static final int DEFAULT_BLOCKS = 6;
	static final int MIN_SHUFFLE = 6;
	static final int MAX_SHUFFLE = 60;
	static final int DEFAULT_SHUFFLE = 24;

	record Config(int size, int blocks, int shuffle) {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int difficulty(JsonNode params) {
		Config c = config(params);
		int score = (c.size() - MIN_SIZE) + (c.blocks() - MIN_BLOCKS) / 3 + (c.shuffle() >= 40 ? 1 : 0);
		return score <= 1 ? 1 : score <= 3 ? 2 : 3;
	}

	@Override
	public int expectedSeconds(JsonNode params) {
		Config c = config(params);
		return 60 + 12 * c.blocks() + 10 * c.size() + c.shuffle();
	}

	@Override
	public List<SlotRole> slotRoles() {
		return List.of(SlotRole.of(TERMINAL_ROLE, "terminal", "pedestal"));
	}

	@Override
	public List<MechanismParam> params() {
		return List.of(
				new MechanismParam("size", "int", MIN_SIZE, MAX_SIZE, String.valueOf(DEFAULT_SIZE), "Side of the square board"),
				new MechanismParam("blocks", "int", MIN_BLOCKS, MAX_BLOCKS, String.valueOf(DEFAULT_BLOCKS),
						"Blocks other than the piece to free"),
				new MechanismParam("shuffle", "int", MIN_SHUFFLE, MAX_SHUFFLE, String.valueOf(DEFAULT_SHUFFLE),
						"Legal slides played to scramble the board"));
	}

	@Override
	public List<String> validateParams(JsonNode params) {
		List<String> problems = new ArrayList<>();
		check(params, "size", MIN_SIZE, MAX_SIZE, problems);
		check(params, "blocks", MIN_BLOCKS, MAX_BLOCKS, problems);
		check(params, "shuffle", MIN_SHUFFLE, MAX_SHUFFLE, problems);
		return problems;
	}

	@Override
	public List<String> validateStep(com.mystaria.phantasmon_backend.events.trahison.room.Step step) {
		return step.slots().isEmpty() ? List.of("missing=slot:" + TERMINAL_ROLE) : List.of();
	}

	private static void check(JsonNode params, String name, int min, int max, List<String> problems) {
		if (params == null || !params.has(name)) {
			return;
		}
		JsonNode v = params.get(name);
		if (!v.isInt() || v.asInt() < min || v.asInt() > max) {
			problems.add(name + " must be an integer " + min + ".." + max);
		}
	}

	static Config config(JsonNode params) {
		return new Config(clamp(intParam(params, "size", DEFAULT_SIZE), MIN_SIZE, MAX_SIZE),
				clamp(intParam(params, "blocks", DEFAULT_BLOCKS), MIN_BLOCKS, MAX_BLOCKS),
				clamp(intParam(params, "shuffle", DEFAULT_SHUFFLE), MIN_SHUFFLE, MAX_SHUFFLE));
	}

	private static int intParam(JsonNode params, String name, int fallback) {
		return params != null && params.path(name).isNumber() ? params.get(name).asInt() : fallback;
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	// ---------------------------------------------------------------- the board

	/** A block: top-left cell, length, direction. */
	private static final class Block {
		int r;
		int c;
		final int len;
		final boolean horizontal;

		Block(int r, int c, int len, boolean horizontal) {
			this.r = r;
			this.c = c;
			this.len = len;
			this.horizontal = horizontal;
		}

		int cellR(int k) {
			return horizontal ? r : r + k;
		}

		int cellC(int k) {
			return horizontal ? c + k : c;
		}
	}

	private static boolean[][] occupancy(int size, List<Block> blocks, Block except) {
		boolean[][] grid = new boolean[size][size];
		for (Block b : blocks) {
			if (b == except) {
				continue;
			}
			for (int k = 0; k < b.len; k++) {
				grid[b.cellR(k)][b.cellC(k)] = true;
			}
		}
		return grid;
	}

	/** How far block {@code b} can slide in direction {@code dir} (+1 or -1): the number of free cells in a row. */
	private static int room(int size, boolean[][] grid, Block b, int dir) {
		int free = 0;
		while (true) {
			int next = dir > 0 ? (b.horizontal ? b.c + b.len : b.r + b.len) + free : (b.horizontal ? b.c : b.r) - 1 - free;
			if (next < 0 || next >= size) {
				return free;
			}
			boolean taken = b.horizontal ? grid[b.r][next] : grid[next][b.c];
			if (taken) {
				return free;
			}
			free++;
		}
	}

	private static void slide(Block b, int delta) {
		if (b.horizontal) {
			b.c += delta;
		} else {
			b.r += delta;
		}
	}

	private static boolean solved(int size, Block piece) {
		return piece.c + piece.len == size;
	}

	// ---------------------------------------------------------------- generation

	@Override
	public MechanismState generate(RandomGenerator rng, JsonNode params) {
		Config c = config(params);
		List<Block> board = null;
		for (int attempt = 0; attempt < 60 && board == null; attempt++) {
			List<Block> candidate = layout(rng, c);
			if (candidate != null && scramble(rng, c, candidate)) {
				board = candidate;
			}
		}
		if (board == null) {
			board = new ArrayList<>();
			board.add(new Block(rng.nextInt(c.size()), 0, 2, true));
		}
		ObjectNode data = Json.object();
		data.put("size", c.size());
		data.put("exitRow", board.get(0).r);
		data.set("blocks", blocksJson(board));
		data.put("moves", 0);
		data.put("solved", false);
		return new MechanismState(data, List.of());
	}

	/** The piece against the opening on a random row, then non-overlapping random blocks; null when they do not fit. */
	private static List<Block> layout(RandomGenerator rng, Config c) {
		List<Block> blocks = new ArrayList<>();
		blocks.add(new Block(rng.nextInt(c.size()), c.size() - 2, 2, true));
		for (int placed = 0, tries = 0; placed < c.blocks() && tries < 400; tries++) {
			boolean horizontal = rng.nextBoolean();
			int len = rng.nextInt(3) == 0 ? 3 : 2;
			Block b = new Block(rng.nextInt(horizontal ? c.size() : c.size() - len + 1),
					rng.nextInt(horizontal ? c.size() - len + 1 : c.size()), len, horizontal);
			boolean[][] grid = occupancy(c.size(), blocks, null);
			boolean free = true;
			for (int k = 0; k < len; k++) {
				free &= !grid[b.cellR(k)][b.cellC(k)];
			}
			if (free) {
				blocks.add(b);
				placed++;
			}
			if (placed == c.blocks()) {
				return blocks;
			}
		}
		return null;
	}

	/** Plays random legal slides; true when the piece ended off the opening. */
	private static boolean scramble(RandomGenerator rng, Config c, List<Block> blocks) {
		int done = 0;
		for (int attempts = 0; done < c.shuffle() && attempts < c.shuffle() * 8; attempts++) {
			Block b = blocks.get(rng.nextInt(blocks.size()));
			int dir = rng.nextBoolean() ? 1 : -1;
			int free = room(c.size(), occupancy(c.size(), blocks, b), b, dir);
			if (free > 0) {
				slide(b, dir * (1 + rng.nextInt(free)));
				done++;
			}
		}
		return !solved(c.size(), blocks.get(0));
	}

	// ---------------------------------------------------------------- gestures

	@Override
	public InputResult onInput(MechanismState state, int player, JsonNode input) {
		if (input == null || !input.path("block").isInt() || !input.path("delta").isInt()) {
			throw new IllegalArgumentException("block and delta are required");
		}
		ObjectNode old = state.data();
		int size = old.get("size").asInt();
		List<Block> blocks = readBlocks(old);
		int index = input.get("block").asInt();
		int delta = input.get("delta").asInt();
		if (index < 0 || index >= blocks.size() || delta == 0 || Math.abs(delta) >= size) {
			throw new IllegalArgumentException("block 0.." + (blocks.size() - 1) + ", delta 1.." + (size - 1) + " either way");
		}
		if (old.path("solved").asBoolean(false)) {
			return InputResult.unchanged(state);
		}
		Block b = blocks.get(index);
		int dir = delta > 0 ? 1 : -1;
		if (room(size, occupancy(size, blocks, b), b, dir) < Math.abs(delta)) {
			throw new IllegalArgumentException("the way is blocked");
		}
		slide(b, delta);
		boolean solved = solved(size, blocks.get(0));
		ObjectNode data = state.copyData();
		data.set("blocks", blocksJson(blocks));
		data.put("moves", old.path("moves").asInt() + 1);
		data.put("solved", solved);
		ObjectNode event = Json.object();
		event.put("type", "slide");
		event.put("block", index);
		event.put("delta", delta);
		return new InputResult(state.withData(data), List.of(event), solved);
	}

	// ---------------------------------------------------------------- view

	/** The whole board: there is nothing to hide, and the same view goes to everybody. */
	@Override
	public JsonNode view(MechanismState state, ViewerKind viewer) {
		ObjectNode d = state.data();
		ObjectNode view = Json.object();
		view.set("size", d.get("size").deepCopy());
		view.set("exitRow", d.get("exitRow").deepCopy());
		view.set("blocks", d.get("blocks").deepCopy());
		view.set("moves", d.get("moves").deepCopy());
		view.put("solved", d.path("solved").asBoolean(false));
		return view;
	}

	private static ArrayNode blocksJson(List<Block> blocks) {
		ArrayNode array = Json.array();
		for (Block b : blocks) {
			ObjectNode o = Json.object();
			o.put("r", b.r);
			o.put("c", b.c);
			o.put("len", b.len);
			o.put("h", b.horizontal);
			array.add(o);
		}
		return array;
	}

	private static List<Block> readBlocks(ObjectNode data) {
		List<Block> blocks = new ArrayList<>();
		for (JsonNode n : data.get("blocks")) {
			blocks.add(new Block(n.get("r").asInt(), n.get("c").asInt(), n.get("len").asInt(), n.get("h").asBoolean()));
		}
		return blocks;
	}
}
