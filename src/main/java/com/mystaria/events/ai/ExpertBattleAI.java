package com.mystaria.events.ai;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.mystaria.phantasmon.client.api.PhantasmonBattleAI;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.InBattleGimmickMove;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Phantasmon's own competitive AI, level {@code expert} (Adrien 2026-11-03), third version. Single battles only (the
 * only kind Phantasmon plays).
 *
 * <p><b>What it knows of the opponent</b>: what a player would — the Pokémon on the field (species, level, HP, stat
 * stages, status), the moves it has used, the Pokémon of its team already seen, how many it has. The rest comes from
 * Smogon's usage stats ({@code assets/phantasmon_events/ai/smogon_sets.json}, {@code tools/smogon_sets.py}): usual moves, item,
 * ability, EV spread and nature. It never reads the opponent's real set (only for a Pokémon Smogon has no data on).
 *
 * <p><b>The team picture</b> (Adrien 2026-11-03: teams are rock-paper-scissors cycles — whoever breaks the cycle is
 * left with a win condition, so Pokémon must be read as checks and preserved): every Pokémon of each side against every
 * one of the other in a one-on-one table (best damage both ways with HP, Speed and priority: who wins). From it:
 * <ul>
 * <li>a Pokémon that is the only answer to an opposing threat is worth more, so it is kept healthy and switched out
 * rather than sacrificed;</li>
 * <li>a Pokémon that beats every opponent left is a win condition: once the opposing checks are gone, it closes the
 * game; the same is read on the opponent's side, whose checks and win condition become targets;</li>
 * <li>the board is scored by HP weighted by that importance, whether each opposing threat is still checked, win
 * conditions, hazards, statuses, boosts and the matchup on the field.</li>
 * </ul>
 *
 * <p><b>How it chooses</b>: two turns ahead, both sides choosing at once. Its options are every usable move (with Mega
 * Evolution, Terastallization or its Z-move when it can) and every switch; the opponent's are its likely moves
 * (weighted by usage) and switches to its seen Pokémon, more or less likely as they are good for it. Each pair is played
 * out — switches first with Stealth Rock damage, then moves by priority and Speed (a KO stops the slower move), boosts,
 * recovery, status, protection, residual damage, the replacement after a KO — and the board scored; the best expected
 * option is played. Tera and Z cost a little (used once), Mega Evolution is always taken. Battle thread only.
 */
public final class ExpertBattleAI implements PhantasmonBattleAI {

	private static final Logger LOG = LoggerFactory.getLogger(ExpertBattleAI.class);
	/** How sharply the opponent is expected to prefer its best options (lower: more predictable). */
	private static final double OPPONENT_TEMPERATURE = 0.12;
	private static final double TERA_RESERVE = 0.2;
	private static final double Z_RESERVE = 0.25;
	/** A later turn counts this much (less certain). */
	private static final double LATER = 0.9;

	private static Map<String, JsonObject> sets;

	private final Set<UUID> seenFoes = new HashSet<>();
	/** Revealed by the battle (Showdown's log): Pokémon uuid → item ("" once lost), ability, Tera type. */
	private static final class Revealed {
		String item;
		String ability;
		String tera;
	}

	private final Map<UUID, Revealed> revealed = new HashMap<>();
	/** The NPC's AI debug (Adrien 2026-11-03): each decision explained in the chat of the player battling it. */
	private final boolean debug;

	public ExpertBattleAI() {
		this(false);
	}

	public ExpertBattleAI(boolean debug) {
		this.debug = debug;
	}
	private int logRead;
	/** Switches in a row: each makes the next one dearer (no panic switching when every option looks bad). */
	private int switchesInARow;
	private final Map<UUID, String> terastallized = new HashMap<>();
	private boolean zUsed;

	// ================================================================ choosing

	@Override
	public ShowdownActionResponse choose(ActiveBattlePokemon active, PokemonBattle battle, BattleSide side,
			ShowdownMoveset moveset, boolean forceSwitch) {
		try {
			return decide(active, side, moveset, forceSwitch);
		} catch (RuntimeException ex) {
			LOG.warn("Expert AI failed, the random AI plays this turn", ex);
			return forceSwitch ? null : new RandomBattleAI().choose(active, battle, side, moveset, forceSwitch);
		}
	}

	/**
	 * Reads what Showdown revealed since last time: an item shown or lost ({@code -item}, {@code -enditem}), an ability
	 * ({@code -ability}), a Tera ({@code -terastallize}). Pokémon are named by their uuid in Cobblemon's Showdown.
	 */
	private void readLog(com.cobblemon.mod.common.api.battles.model.PokemonBattle battle) {
		List<String> log = battle.getShowdownMessages();
		for (int i = Math.min(logRead, log.size()); i < log.size(); i++) {
			for (String line : log.get(i).split("\n")) {
				String[] parts = line.split("\\|");
				if (parts.length < 4) {
					continue;
				}
				String kind = parts[1];
				if (!kind.equals("-item") && !kind.equals("-enditem") && !kind.equals("-ability") && !kind.equals("-terastallize")) {
					continue;
				}
				UUID uuid = uuidOf(parts[2]);
				if (uuid == null) {
					continue;
				}
				Revealed known = revealed.computeIfAbsent(uuid, key -> new Revealed());
				String value = id(parts[3].replaceFirst("^(item|ability): ", ""));
				switch (kind) {
					case "-item" -> known.item = value;
					case "-enditem" -> known.item = "";
					case "-ability" -> known.ability = value;
					default -> known.tera = value;
				}
			}
		}
		logRead = log.size();
	}

	static UUID uuidOf(String ident) {
		int colon = ident.indexOf(": ");
		String name = colon < 0 ? ident : ident.substring(colon + 2);
		try {
			return UUID.fromString(name.strip());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** The believed set corrected by what the battle revealed. */
	private Mon belief(BattlePokemon battler, boolean opponent) {
		Mon mon = opponent ? belief(battler) : own(battler);
		Revealed known = revealed.get(battler.getUuid());
		if (opponent && known != null) {
			if (known.item != null) {
				mon.item = known.item;
			}
			if (known.ability != null) {
				mon.ability = known.ability;
			}
			if (known.tera != null) {
				mon.teraType = known.tera;
			}
		}
		return mon;
	}

	private ShowdownActionResponse decide(ActiveBattlePokemon active, BattleSide side, ShowdownMoveset moveset, boolean forceSwitch) {
		readLog(active.getBattle());
		ActiveBattlePokemon foeSlot = side.getOppositeSide().getActivePokemon().get(0);
		BattlePokemon foeActive = foeSlot.getBattlePokemon();
		if (foeActive != null) {
			seenFoes.add(foeActive.getUuid());
		}
		Board board = board(active.getActor().getPokemonList(), active.getBattlePokemon(),
				foeActive == null ? List.of() : foeActive.getActor().getPokemonList(), foeActive, moveset);
		if (board.foeActive < 0) {
			BattlePokemon next = forceSwitch ? healthiest(active) : null;
			return forceSwitch ? (next == null ? null : new SwitchActionResponse(next.getUuid())) : null;
		}
		State root = board.start();
		if (forceSwitch || active.getBattlePokemon() == null || active.getBattlePokemon().getHealth() <= 0) {
			int best = -1;
			double bestValue = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < board.mine.size(); i++) {
				if (root.myHp[i] <= 0 || i == board.myActive) {
					continue;
				}
				State in = root.copy();
				board.enter(in, true, i);
				double value = board.value(in, 1);
				if (value > bestValue) {
					bestValue = value;
					best = i;
				}
			}
			if (best < 0) {
				return null;
			}
			LOG.debug("Expert AI sends in {} ({})", board.mine.get(best).name, String.format(Locale.ROOT, "%.2f", bestValue));
			if (debug) {
				say("§6[IA] §fRemplaçant après KO : §a" + board.mine.get(best).name + " §7(" + signed(bestValue) + ")");
			}
			return new SwitchActionResponse(board.mine.get(best).battler.getUuid());
		}
		board.switchesInARow = switchesInARow;
		List<Action> mine = board.rootOptions(moveset, zUsed);
		List<Action> theirs = board.foeOptions(root);
		double[] p = board.foePolicy(root, theirs);
		Action best = null;
		double bestValue = Double.NEGATIVE_INFINITY;
		Map<Action, Double> values = new LinkedHashMap<>();
		for (Action a : mine) {
			double value = -a.cost;
			for (int k = 0; k < theirs.size(); k++) {
				if (p[k] < 0.01) {
					continue;
				}
				State next = board.turn(root, a, theirs.get(k));
				value += p[k] * board.value(next, 1);
			}
			values.put(a, value);
			if (value > bestValue) {
				bestValue = value;
				best = a;
			}
		}
		if (best == null) {
			return null;
		}
		LOG.debug("Expert AI: {} ({})", board.label(best), String.format(Locale.ROOT, "%.2f", bestValue));
		if (debug) {
			report(board, root, theirs, p, values, best, bestValue);
		}
		if (best.switchTo >= 0) {
			switchesInARow++;
			return new SwitchActionResponse(board.mine.get(best.switchTo).battler.getUuid());
		}
		switchesInARow = 0;
		String gimmick = best.gimmick;
		if (gimmick == null && moveset.getCanMegaEvo()) {
			gimmick = ShowdownMoveset.Gimmick.MEGA_EVOLUTION.getId(); // no reason to wait
		}
		if (best.tera) {
			terastallized.put(active.getBattlePokemon().getUuid(), board.teraType);
		}
		if (best.z) {
			zUsed = true;
		}
		// No target in a single battle: a spread move (Earthquake, Surf…) refuses one and the battle would wait forever
		// (Adrien's test 2026-11-03: "You can't choose a target for Earthquake").
		return new MoveActionResponse(best.moveId, null, gimmick);
	}

	/**
	 * The AI debug, in the chat: the field and the board's score, what it believes of the opponent's Pokémon, the
	 * opponent's likely answers, its options with their values, threats and win conditions, its choice.
	 */
	private void report(Board board, State root, List<Action> theirs, double[] p, Map<Action, Double> values, Action best,
			double bestValue) {
		Mon me = board.mine.get(root.myActive);
		Mon foe = board.theirs.get(root.foeActive);
		say("§6[IA] §f" + me.name + " §7" + percent(root.myHp[root.myActive], me.maxHp) + " §fcontre " + foe.name + " §7"
				+ percent(root.foeHp[root.foeActive], foe.maxHp) + " · plateau " + signed(board.evaluate(root)));
		StringBuilder belief = new StringBuilder("§7Croit : ");
		for (int m = 0; m < foe.moveList.size(); m++) {
			belief.append(m > 0 ? ", " : "").append(moveName(foe.moveList.get(m)));
			if (foe.movePrior.get(m) < 1) {
				belief.append(" ").append(Math.round(foe.movePrior.get(m) * 100)).append("%");
			}
		}
		belief.append(" · objet ").append(foe.item.isEmpty() ? "?" : foe.item).append(" · talent ").append(foe.ability.isEmpty() ? "?" : foe.ability);
		if (foe.teraType != null) {
			belief.append(" · Téra ").append(foe.teraType);
		}
		say(belief.toString());
		List<Integer> order = new ArrayList<>();
		for (int k = 0; k < theirs.size(); k++) {
			order.add(k);
		}
		order.sort((x, y) -> Double.compare(p[y], p[x]));
		StringBuilder expects = new StringBuilder("§7Prévoit : ");
		for (int n = 0; n < Math.min(4, order.size()); n++) {
			Action b = theirs.get(order.get(n));
			String label = b.switchTo >= 0 ? "→ " + board.theirs.get(b.switchTo).name : moveName(foe.moveList.get(b.move));
			expects.append(n > 0 ? " · " : "").append(label).append(" ").append(Math.round(p[order.get(n)] * 100)).append("%");
		}
		say(expects.toString());
		List<Map.Entry<Action, Double>> ranked = new ArrayList<>(values.entrySet());
		ranked.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
		StringBuilder options = new StringBuilder("§7Options : ");
		for (int n = 0; n < Math.min(5, ranked.size()); n++) {
			options.append(n > 0 ? " · " : "").append(optionName(board, ranked.get(n).getKey())).append(" ").append(signed(ranked.get(n).getValue()));
		}
		say(options.toString());
		say("§7" + board.describe(root));
		say("§6[IA] §fChoisit : §a" + optionName(board, best) + " §7(" + signed(bestValue) + ")");
	}

	private static String optionName(Board board, Action a) {
		if (a.switchTo >= 0) {
			return "→ " + board.mine.get(a.switchTo).name;
		}
		Mon me = board.mine.get(board.myActive);
		String name = a.move >= 0 && a.move < me.moveList.size() ? moveName(me.moveList.get(a.move)) : a.moveId;
		return name + (a.tera ? " + Téra " + board.teraType : "") + (a.z ? " (Z)" : "");
	}

	static String moveName(MoveTemplate move) {
		return move.getDisplayName().getString();
	}

	static String percent(double hp, double max) {
		return Math.max(0, Math.round(hp * 100 / Math.max(1, max))) + "%";
	}

	static String signed(double value) {
		return String.format(Locale.ROOT, "%+.2f", value);
	}

	/** A line in the chat of this client (the player battling the NPC hosts the battle). */
	static void say(String line) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player != null) {
				mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(line), false);
			}
		});
	}

	private static BattlePokemon healthiest(ActiveBattlePokemon active) {
		BattlePokemon best = null;
		for (BattlePokemon candidate : active.getActor().getPokemonList()) {
			if (canComeIn(candidate, active.getBattlePokemon()) && (best == null || candidate.getHealth() > best.getHealth())) {
				best = candidate;
			}
		}
		return best;
	}

	static boolean canComeIn(BattlePokemon candidate, BattlePokemon current) {
		return candidate != current && candidate.getHealth() > 0 && !candidate.isSentOut() && candidate.canBeSentOut();
	}

	// ================================================================ lead

	/**
	 * Its team with the best lead first, against the opponent's team as team preview shows it (species: their sets are
	 * believed from Smogon): the Pokémon whose one-on-ones are best on average and least bad at worst.
	 */
	@Override
	public List<BattlePokemon> orderTeam(List<BattlePokemon> own, List<BattlePokemon> opponents) {
		return leadFirst(own, opponents);
	}

	@Override
	public void onChoiceRefused(String message) {
		say(message);
	}

	public List<BattlePokemon> leadFirst(List<BattlePokemon> team, List<BattlePokemon> opponents) {
		List<Mon> mine = new ArrayList<>();
		team.forEach(pokemon -> mine.add(own(pokemon)));
		List<Mon> theirs = new ArrayList<>();
		opponents.forEach(pokemon -> theirs.add(belief(pokemon)));
		Board board = new Board(mine, theirs, 0, 0, 0);
		int best = 0;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < mine.size(); i++) {
			double sum = 0;
			double worst = Double.POSITIVE_INFINITY;
			for (int j = 0; j < theirs.size(); j++) {
				double duel = board.duel(i, j, mine.get(i).hp, theirs.get(j).hp);
				sum += duel;
				worst = Math.min(worst, duel);
			}
			// A win condition is better kept in the back: leads that check much but matter less are preferred.
			double score = 0.6 * sum / Math.max(1, theirs.size()) + 0.4 * worst;
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		List<BattlePokemon> ordered = new ArrayList<>(team);
		BattlePokemon lead = team.get(best);
		ordered.remove(lead);
		ordered.add(0, lead);
		LOG.info("Expert AI leads with {}", lead.getEffectedPokemon().getSpecies().getName());
		return ordered;
	}

	// ================================================================ the board

	private Board board(List<BattlePokemon> myTeam, BattlePokemon myActive, List<BattlePokemon> foeTeam, BattlePokemon foeActive,
			ShowdownMoveset moveset) {
		List<Mon> mine = new ArrayList<>();
		int myIndex = -1;
		for (BattlePokemon pokemon : myTeam) {
			if (pokemon == myActive) {
				myIndex = mine.size();
			}
			Mon mon = own(pokemon);
			if (pokemon != myActive) {
				mon.stages = Map.of(); // stages go when it leaves the field
			}
			mine.add(mon);
		}
		List<Mon> theirs = new ArrayList<>();
		int foeIndex = -1;
		int unknown = 0;
		for (BattlePokemon pokemon : foeTeam) {
			if (pokemon == foeActive) {
				foeIndex = theirs.size();
			}
			if (pokemon == foeActive || seenFoes.contains(pokemon.getUuid())) {
				Mon mon = belief(pokemon, true);
				if (pokemon != foeActive) {
					mon.stages = Map.of();
				}
				theirs.add(mon);
			} else if (pokemon.getHealth() > 0) {
				unknown++;
			}
		}
		Board board = new Board(mine, theirs, Math.max(0, myIndex), foeIndex, unknown);
		if (moveset != null && moveset.getCanTerastallize() != null && myIndex >= 0) {
			String type = moveset.getCanTerastallize().toLowerCase(Locale.ROOT);
			if (!type.equals("stellar")) {
				board.teraType = type;
			}
		}
		for (int i = 0; i < mine.size(); i++) {
			String tera = terastallized.get(mine.get(i).battler.getUuid());
			if (tera != null) {
				mine.get(i).teraType = tera;
			}
		}
		board.prepare();
		return board;
	}

	/** One option: a move of the active Pokémon (maybe with a gimmick), or a switch. */
	private static final class Action {
		int move = -1;
		String moveId;
		int switchTo = -1;
		String gimmick;
		boolean tera;
		boolean z;
		double cost;
	}

	/** The battle as the search plays it: HP, who is out, field boosts, statuses, hazards. */
	private static final class State {
		double[] myHp;
		double[] foeHp;
		String[] myStatus;
		String[] foeStatus;
		int myActive;
		int foeActive;
		double myAttack = 1;
		double mySpeed = 1;
		double myDefence = 1;
		double foeAttack = 1;
		double foeSpeed = 1;
		double foeDefence = 1;
		boolean myTera;
		boolean rocksOnMe;
		boolean rocksOnFoe;
		int spikesOnMe;
		int spikesOnFoe;

		State copy() {
			State s = new State();
			s.myHp = myHp.clone();
			s.foeHp = foeHp.clone();
			s.myStatus = myStatus.clone();
			s.foeStatus = foeStatus.clone();
			s.myActive = myActive;
			s.foeActive = foeActive;
			s.myAttack = myAttack;
			s.mySpeed = mySpeed;
			s.myDefence = myDefence;
			s.foeAttack = foeAttack;
			s.foeSpeed = foeSpeed;
			s.foeDefence = foeDefence;
			s.myTera = myTera;
			s.rocksOnMe = rocksOnMe;
			s.rocksOnFoe = rocksOnFoe;
			s.spikesOnMe = spikesOnMe;
			s.spikesOnFoe = spikesOnFoe;
			return s;
		}
	}

	/** Both teams and their damage tables (computed once per decision), and the search over them. */
	private static final class Board {
		final List<Mon> mine;
		final List<Mon> theirs;
		final int myActive;
		final int foeActive;
		final int unknownFoes;
		String teraType;
		int switchesInARow;
		/** [my i][move m][foe j]: expected damage; the same with Tera (my active only) and as a Z-move. */
		double[][][] myDamage;
		double[][] myTeraDamage;
		double[][] myZDamage;
		/** [foe j][move m][my i]: expected damage; [foe j][move m]: on my active once it terastallized. */
		double[][][] foeDamage;
		double[][] foeOnTera;
		double[] myBest;

		Board(List<Mon> mine, List<Mon> theirs, int myActive, int foeActive, int unknownFoes) {
			this.mine = mine;
			this.theirs = theirs;
			this.myActive = myActive;
			this.foeActive = foeActive;
			this.unknownFoes = unknownFoes;
			prepare();
		}

		void prepare() {
			myDamage = new double[mine.size()][][];
			for (int i = 0; i < mine.size(); i++) {
				Mon a = mine.get(i);
				myDamage[i] = new double[a.moveList.size()][theirs.size()];
				for (int m = 0; m < a.moveList.size(); m++) {
					for (int j = 0; j < theirs.size(); j++) {
						myDamage[i][m][j] = damage(a, theirs.get(j), a.moveList.get(m), a.teraType, 0, null);
					}
				}
			}
			foeDamage = new double[theirs.size()][][];
			for (int j = 0; j < theirs.size(); j++) {
				Mon a = theirs.get(j);
				foeDamage[j] = new double[a.moveList.size()][mine.size()];
				for (int m = 0; m < a.moveList.size(); m++) {
					for (int i = 0; i < mine.size(); i++) {
						foeDamage[j][m][i] = damage(a, mine.get(i), a.moveList.get(m), null, 0, mine.get(i).teraType);
					}
				}
			}
			if (mine.isEmpty() || theirs.isEmpty() || foeActive < 0) {
				return;
			}
			Mon me = mine.get(myActive);
			myTeraDamage = new double[me.moveList.size()][theirs.size()];
			myZDamage = new double[me.moveList.size()][theirs.size()];
			for (int m = 0; m < me.moveList.size(); m++) {
				MoveTemplate move = me.moveList.get(m);
				for (int j = 0; j < theirs.size(); j++) {
					myTeraDamage[m][j] = teraType == null ? myDamage[myActive][m][j] : damage(me, theirs.get(j), move, teraType, 0, null);
					myZDamage[m][j] = isStatus(move) ? 0 : damage(me, theirs.get(j), move, me.teraType, zPower(move.getPower()), null);
				}
			}
			foeOnTera = new double[theirs.size()][];
			for (int j = 0; j < theirs.size(); j++) {
				Mon a = theirs.get(j);
				foeOnTera[j] = new double[a.moveList.size()];
				for (int m = 0; m < a.moveList.size(); m++) {
					foeOnTera[j][m] = teraType == null ? foeDamage[j][m][myActive] : damage(a, me, a.moveList.get(m), null, 0, teraType);
				}
			}
		}

		State start() {
			State s = new State();
			s.myHp = new double[mine.size()];
			s.myStatus = new String[mine.size()];
			for (int i = 0; i < mine.size(); i++) {
				s.myHp[i] = mine.get(i).hp;
				s.myStatus[i] = mine.get(i).status;
			}
			s.foeHp = new double[theirs.size()];
			s.foeStatus = new String[theirs.size()];
			for (int j = 0; j < theirs.size(); j++) {
				s.foeHp[j] = theirs.get(j).hp;
				s.foeStatus[j] = theirs.get(j).status;
			}
			s.myActive = myActive;
			s.foeActive = foeActive;
			Mon me = mine.get(myActive);
			s.myAttack = boost(me, me.stat(Stats.ATTACK) >= me.stat(Stats.SPECIAL_ATTACK) ? Stats.ATTACK : Stats.SPECIAL_ATTACK);
			s.mySpeed = boost(me, Stats.SPEED);
			s.myTera = me.teraType != null;
			Mon foe = theirs.get(foeActive);
			s.foeAttack = boost(foe, foe.stat(Stats.ATTACK) >= foe.stat(Stats.SPECIAL_ATTACK) ? Stats.ATTACK : Stats.SPECIAL_ATTACK);
			s.foeSpeed = boost(foe, Stats.SPEED);
			return s;
		}

		/** The table damage is computed without the active's stages: they are carried by the state's multipliers. */
		private static double boost(Mon mon, Stat stat) {
			int stage = mon.stages.getOrDefault(stat, 0);
			return stage >= 0 ? (2.0 + stage) / 2 : 2.0 / (2 - stage);
		}

		// ------------------------------------------------ options

		List<Action> rootOptions(ShowdownMoveset moveset, boolean zUsed) {
			List<Action> options = new ArrayList<>();
			Mon me = mine.get(myActive);
			List<InBattleGimmickMove> zMoves = moveset.getCanZMove();
			for (int index = 0; index < moveset.getMoves().size(); index++) {
				InBattleMove slot = moveset.getMoves().get(index);
				if (!slot.canBeUsed()) {
					continue;
				}
				int m = me.moveIndex(slot.getId());
				if (m < 0) {
					continue;
				}
				options.add(moveAction(m, slot.getId(), null));
				if (teraType != null && me.teraType == null) {
					Action tera = moveAction(m, slot.getId(), ShowdownMoveset.Gimmick.TERASTALLIZATION.getId());
					tera.tera = true;
					tera.cost = TERA_RESERVE;
					options.add(tera);
				}
				if (!zUsed && zMoves != null && index < zMoves.size() && zMoves.get(index) != null && !isStatus(me.moveList.get(m))) {
					Action z = moveAction(m, slot.getId(), ShowdownMoveset.Gimmick.Z_POWER.getId());
					z.z = true;
					z.cost = Z_RESERVE;
					options.add(z);
				}
			}
			if (!moveset.getTrapped()) {
				for (int i = 0; i < mine.size(); i++) {
					if (i != myActive && mine.get(i).hp > 0 && canComeIn(mine.get(i).battler, mine.get(myActive).battler)) {
						Action change = new Action();
						change.switchTo = i;
						change.cost = 0.08 + 0.35 * switchesInARow;
						options.add(change);
					}
				}
			}
			return options;
		}

		private static Action moveAction(int m, String id, String gimmick) {
			Action action = new Action();
			action.move = m;
			action.moveId = id;
			action.gimmick = gimmick;
			return action;
		}

		/** Our options in a later turn: the active's moves and switches (no gimmick). */
		List<Action> laterOptions(State s) {
			List<Action> options = new ArrayList<>();
			Mon me = mine.get(s.myActive);
			for (int m = 0; m < me.moveList.size(); m++) {
				options.add(moveAction(m, me.moveList.get(m).getName(), null));
			}
			for (int i = 0; i < mine.size(); i++) {
				if (i != s.myActive && s.myHp[i] > 0) {
					Action change = new Action();
					change.switchTo = i;
					options.add(change);
				}
			}
			return options;
		}

		List<Action> foeOptions(State s) {
			List<Action> options = new ArrayList<>();
			Mon foe = theirs.get(s.foeActive);
			for (int m = 0; m < foe.moveList.size(); m++) {
				Action move = moveAction(m, foe.moveList.get(m).getName(), null);
				move.cost = foe.movePrior.get(m); // the prior, for the policy
				options.add(move);
			}
			for (int j = 0; j < theirs.size(); j++) {
				if (j != s.foeActive && s.foeHp[j] > 0) {
					Action change = new Action();
					change.switchTo = j;
					change.cost = 0.35;
					options.add(change);
				}
			}
			return options;
		}

		/**
		 * How likely each opponent option is: its prior (usage), sharpened by how good it is for the opponent against
		 * our most obvious answer (our hardest-hitting move).
		 */
		double[] foePolicy(State s, List<Action> theirs) {
			Action obvious = obvious(s);
			double[] weights = new double[theirs.size()];
			double total = 0;
			double[] goods = new double[theirs.size()];
			double top = Double.NEGATIVE_INFINITY;
			for (int k = 0; k < theirs.size(); k++) {
				goods[k] = -evaluate(turn(s, obvious, theirs.get(k)));
				top = Math.max(top, goods[k]);
			}
			for (int k = 0; k < theirs.size(); k++) {
				weights[k] = Math.max(0.02, theirs.get(k).cost) * Math.exp((goods[k] - top) / OPPONENT_TEMPERATURE);
				total += weights[k];
			}
			for (int k = 0; k < weights.length; k++) {
				weights[k] /= total;
			}
			return weights;
		}

		private Action obvious(State s) {
			Mon me = mine.get(s.myActive);
			int best = -1;
			double bestDamage = -1;
			for (int m = 0; m < me.moveList.size(); m++) {
				double damage = myDamage[s.myActive][m][s.foeActive];
				if (damage > bestDamage) {
					bestDamage = damage;
					best = m;
				}
			}
			return best < 0 ? new Action() : moveAction(best, me.moveList.get(best).getName(), null);
		}

		/** The value of a state, {@code depth} more turns searched (both choosing at once, the opponent as predicted). */
		double value(State s, int depth) {
			if (depth <= 0 || over(s)) {
				return evaluate(s);
			}
			List<Action> theirs = foeOptions(s);
			double[] p = foePolicy(s, theirs);
			double best = Double.NEGATIVE_INFINITY;
			for (Action a : laterOptions(s)) {
				double value = 0;
				for (int k = 0; k < theirs.size(); k++) {
					if (p[k] >= 0.02) {
						value += p[k] * evaluate(turn(s, a, theirs.get(k)));
					}
				}
				best = Math.max(best, value);
			}
			return LATER * best + (1 - LATER) * evaluate(s);
		}

		private boolean over(State s) {
			boolean meAlive = false;
			for (double hp : s.myHp) {
				meAlive |= hp > 0;
			}
			boolean foeAlive = unknownFoes > 0;
			for (double hp : s.foeHp) {
				foeAlive |= hp > 0;
			}
			return !meAlive || !foeAlive;
		}

		// ------------------------------------------------ one turn

		/** One turn: switches first, then the moves by priority and Speed, residual damage, replacements after KOs. */
		State turn(State from, Action a, Action b) {
			State s = from.copy();
			if (a.switchTo >= 0) {
				enter(s, true, a.switchTo);
			}
			if (b.switchTo >= 0) {
				enter(s, false, b.switchTo);
			}
			if (a.tera) {
				s.myTera = true;
			}
			boolean myMove = a.move >= 0 && s.myHp[s.myActive] > 0 && !failsAgainst(true, s, a, b);
			boolean foeMove = b.move >= 0 && s.foeHp[s.foeActive] > 0 && !failsAgainst(false, s, b, a);
			if (myMove && foeMove) {
				MoveTemplate mine = this.mine.get(s.myActive).moveList.get(a.move);
				MoveTemplate theirs = this.theirs.get(s.foeActive).moveList.get(b.move);
				boolean first = mine.getPriority() != theirs.getPriority() ? mine.getPriority() > theirs.getPriority()
						: speed(s, true) >= speed(s, false);
				boolean myProtect = AIUtility.INSTANCE.getProtectMoves().contains(mine.getName());
				boolean foeProtect = AIUtility.INSTANCE.getProtectMoves().contains(theirs.getName());
				if (first) {
					use(s, true, a, foeProtect);
					if (s.foeHp[s.foeActive] > 0) {
						use(s, false, b, myProtect);
					}
				} else {
					use(s, false, b, myProtect);
					if (s.myHp[s.myActive] > 0) {
						use(s, true, a, foeProtect);
					}
				}
			} else if (myMove) {
				use(s, true, a, false);
			} else if (foeMove) {
				use(s, false, b, false);
			}
			residual(s, true);
			residual(s, false);
			if (s.myHp[s.myActive] <= 0) {
				replace(s, true);
			}
			if (s.foeHp[s.foeActive] <= 0) {
				replace(s, false);
			}
			return s;
		}

		/** Sucker Punch and Thunderclap fail when the target switches or uses a status move (Adrien 2026-11-03). */
		private boolean failsAgainst(boolean ours, State s, Action action, Action other) {
			Mon user = ours ? mine.get(s.myActive) : theirs.get(s.foeActive);
			String name = user.moveList.get(action.move).getName();
			if (!name.equals("suckerpunch") && !name.equals("thunderclap")) {
				return false;
			}
			if (other.switchTo >= 0 || other.move < 0) {
				return true;
			}
			Mon target = ours ? theirs.get(s.foeActive) : mine.get(s.myActive);
			return isStatus(target.moveList.get(other.move));
		}

		/** A Pokémon comes in: the field boosts go, hazards hit it, Intimidate lowers the other side's Attack. */
		void enter(State s, boolean ours, int index) {
			Mon mon = ours ? mine.get(index) : theirs.get(index);
			if (ours) {
				s.myActive = index;
				s.myAttack = 1;
				s.mySpeed = 1;
				s.myDefence = 1;
				s.myTera = mon.teraType != null;
			} else {
				s.foeActive = index;
				s.foeAttack = 1;
				s.foeSpeed = 1;
				s.foeDefence = 1;
			}
			if (mon.ability.equals("intimidate")) {
				Mon other = ours ? theirs.get(s.foeActive) : mine.get(s.myActive);
				if (other.stat(Stats.ATTACK) >= other.stat(Stats.SPECIAL_ATTACK)) {
					if (ours) {
						s.foeAttack *= 2.0 / 3;
					} else {
						s.myAttack *= 2.0 / 3;
					}
				}
			}
			double[] hp = ours ? s.myHp : s.foeHp;
			boolean rocks = ours ? s.rocksOnMe : s.rocksOnFoe;
			int spikes = ours ? s.spikesOnMe : s.spikesOnFoe;
			if (mon.item.equals("heavydutyboots")) {
				return;
			}
			if (rocks) {
				hp[index] -= mon.maxHp * 0.125 * effectiveness(ElementalTypes.get("rock"), mon, null);
			}
			if (spikes > 0 && !mon.types.contains("flying") && !mon.ability.equals("levitate") && !mon.item.equals("airballoon")) {
				hp[index] -= mon.maxHp * (spikes == 1 ? 0.125 : spikes == 2 ? 1.0 / 6 : 0.25);
			}
		}

		/** After a KO, the side brings in what scores best for it (greedy). */
		private void replace(State s, boolean ours) {
			double[] hp = ours ? s.myHp : s.foeHp;
			int best = -1;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < hp.length; i++) {
				if (hp[i] <= 0) {
					continue;
				}
				double score = ours ? duel(i, s.foeActive, hp[i], s.foeHp[s.foeActive]) : -duel(s.myActive, i, s.myHp[s.myActive], hp[i]);
				if (score > bestScore) {
					bestScore = score;
					best = i;
				}
			}
			if (best >= 0) {
				enter(s, ours, best);
			}
		}

		private double speed(State s, boolean ours) {
			Mon mon = ours ? mine.get(s.myActive) : theirs.get(s.foeActive);
			String status = ours ? s.myStatus[s.myActive] : s.foeStatus[s.foeActive];
			double speed = mon.stat(Stats.SPEED) * (ours ? s.mySpeed : s.foeSpeed);
			if ("par".equals(status)) {
				speed *= 0.5;
			}
			if (mon.item.equals("choicescarf")) {
				speed *= 1.5;
			}
			return speed;
		}

		/** One side's move, its effects on the state. */
		private void use(State s, boolean ours, Action action, boolean targetProtects) {
			Mon user = ours ? mine.get(s.myActive) : theirs.get(s.foeActive);
			Mon target = ours ? theirs.get(s.foeActive) : mine.get(s.myActive);
			MoveTemplate move = user.moveList.get(action.move);
			String name = move.getName();
			String status = ours ? s.myStatus[s.myActive] : s.foeStatus[s.foeActive];
			double acts = "slp".equals(status) ? 0.33 : "par".equals(status) ? 0.75 : 1;
			AIUtility ai = AIUtility.INSTANCE;
			if (!isStatus(move)) {
				if (targetProtects) {
					return;
				}
				double damage;
				if (ours) {
					damage = action.z ? myZDamage[action.move][s.foeActive]
							: action.tera || s.myTera && s.myActive == myActive && teraType != null && user.teraType == null
							? myTeraDamage[action.move][s.foeActive] : myDamage[s.myActive][action.move][s.foeActive];
					damage *= s.myAttack / s.foeDefence;
				} else {
					damage = s.myTera && s.myActive == myActive && foeOnTera != null && mine.get(myActive).teraType == null
							? foeOnTera[s.foeActive][action.move] : foeDamage[s.foeActive][action.move][s.myActive];
					damage *= s.foeAttack / s.myDefence;
				}
				if ("brn".equals(status) && "physical".equals(move.getDamageCategory().getName()) && !user.ability.equals("guts")) {
					damage *= 0.5;
				}
				damage *= acts;
				double[] hp = ours ? s.foeHp : s.myHp;
				int index = ours ? s.foeActive : s.myActive;
				hp[index] -= damage;
				if (user.item.equals("lifeorb")) {
					(ours ? s.myHp : s.foeHp)[ours ? s.myActive : s.foeActive] -= user.maxHp * 0.1 * acts;
				}
				if (contact(move) && !user.ability.equals("longreach") && !user.item.equals("protectivepads")) {
					// Adrien 2026-11-03: Garchomp's Rocky Helmet + Rough Skin punish every contact hit.
					double punished = (target.item.equals("rockyhelmet") ? 1.0 / 6 : 0)
							+ (target.ability.equals("roughskin") || target.ability.equals("ironbarbs") ? 1.0 / 8 : 0);
					(ours ? s.myHp : s.foeHp)[ours ? s.myActive : s.foeActive] -= user.maxHp * punished * acts;
				}
				if (ai.getSelfRecoveryMoves().contains(name)) {
					(ours ? s.myHp : s.foeHp)[ours ? s.myActive : s.foeActive] += damage * 0.5; // a draining hit
				}
				return;
			}
			Map<Stat, Integer> boosts = ai.getBoostFromMoves().get(name);
			if (boosts != null) {
				boolean physical = user.stat(Stats.ATTACK) >= user.stat(Stats.SPECIAL_ATTACK);
				for (Map.Entry<Stat, Integer> boost : boosts.entrySet()) {
					double factor = 1 + 0.5 * boost.getValue() * acts;
					Stat stat = boost.getKey();
					if (stat == (physical ? Stats.ATTACK : Stats.SPECIAL_ATTACK)) {
						if (ours) {
							s.myAttack = Math.min(4, s.myAttack * factor);
						} else {
							s.foeAttack = Math.min(4, s.foeAttack * factor);
						}
					} else if (stat == Stats.SPEED) {
						if (ours) {
							s.mySpeed = Math.min(4, s.mySpeed * factor);
						} else {
							s.foeSpeed = Math.min(4, s.foeSpeed * factor);
						}
					} else if (stat == Stats.DEFENCE || stat == Stats.SPECIAL_DEFENCE) {
						if (ours) {
							s.myDefence = Math.min(4, s.myDefence * (1 + 0.25 * boost.getValue() * acts));
						} else {
							s.foeDefence = Math.min(4, s.foeDefence * (1 + 0.25 * boost.getValue() * acts));
						}
					}
				}
				return;
			}
			if (ai.getSelfRecoveryMoves().contains(name)) {
				double[] hp = ours ? s.myHp : s.foeHp;
				int index = ours ? s.myActive : s.foeActive;
				hp[index] = Math.min(user.maxHp, hp[index] + user.maxHp * 0.5 * acts);
				return;
			}
			if (ai.getEntryHazards().contains(name)) {
				if (name.equals("stealthrock")) {
					if (ours) {
						s.rocksOnFoe = true;
					} else {
						s.rocksOnMe = true;
					}
				} else if (name.equals("spikes")) {
					if (ours) {
						s.spikesOnFoe = Math.min(3, s.spikesOnFoe + 1);
					} else {
						s.spikesOnMe = Math.min(3, s.spikesOnMe + 1);
					}
				}
				return;
			}
			if (ai.getAntiBoostMoves().contains(name) && !targetProtects) {
				// Haze, Clear Smog, Roar, Whirlwind, Dragon Tail…: the boosts go.
				s.foeAttack = Math.min(1, s.foeAttack);
				s.foeSpeed = Math.min(1, s.foeSpeed);
				s.foeDefence = Math.min(1, s.foeDefence);
				if (!ours) {
					s.myAttack = Math.min(1, s.myAttack);
					s.mySpeed = Math.min(1, s.mySpeed);
					s.myDefence = Math.min(1, s.myDefence);
				}
			}
			if (ai.getAntiHazardsMoves().contains(name)) {
				if (ours) {
					s.rocksOnMe = false;
					s.spikesOnMe = 0;
				} else {
					s.rocksOnFoe = false;
					s.spikesOnFoe = 0;
				}
				return;
			}
			String inflicted = ai.getStatusMoves().get(name);
			if (inflicted != null && !targetProtects) {
				String[] statuses = ours ? s.foeStatus : s.myStatus;
				int index = ours ? s.foeActive : s.myActive;
				if (statuses[index].isEmpty() && target.battler != null
						&& ai.canAffectWithStatus(inflicted, target.battler.getEffectedPokemon().getTypes(), target.battler.getEffectedPokemon().getAbility())
						&& accuracy(move) * acts >= 0.5) {
					statuses[index] = inflicted;
				}
			}
		}

		/** End of turn: poison, burn, Leftovers. */
		private void residual(State s, boolean ours) {
			double[] hp = ours ? s.myHp : s.foeHp;
			String[] statuses = ours ? s.myStatus : s.foeStatus;
			int index = ours ? s.myActive : s.foeActive;
			Mon mon = ours ? mine.get(index) : theirs.get(index);
			if (hp[index] <= 0) {
				return;
			}
			switch (statuses[index]) {
				case "tox", "psn" -> hp[index] -= mon.maxHp * (mon.ability.equals("poisonheal") ? -0.125 : 0.125);
				case "brn" -> hp[index] -= mon.maxHp / 16;
				default -> {
				}
			}
			if (mon.item.equals("leftovers") || mon.item.equals("blacksludge") && mon.types.contains("poison")) {
				hp[index] = Math.min(mon.maxHp, hp[index] + mon.maxHp / 16);
			}
		}

		// ------------------------------------------------ scoring the board

		/**
		 * One on one, from my {@code i}'s side with these HP: which of the two knocks the other out first (best damage,
		 * priority, Speed), and how hard each hits. In [-1, 1].
		 */
		double duel(int i, int j, double myHp, double foeHp) {
			return duel(i, j, myHp, foeHp, 1, 1, 1, 1, 1, 1);
		}

		/**
		 * The same with the field multipliers of each side (boosts of the Pokémon facing each other): a boosted sweeper
		 * beats what it beat not, and must be answered. A priority move that finishes the other wins whatever the
		 * Speed (Adrien 2026-11-03: winning the race with priority).
		 */
		double duel(int i, int j, double myHp, double foeHp, double myAttack, double mySpeed, double myDefence, double foeAttack,
				double foeSpeed, double foeDefence) {
			if (myHp <= 0) {
				return -1;
			}
			if (foeHp <= 0) {
				return 1;
			}
			Mon a = mine.get(i);
			Mon b = theirs.get(j);
			double dealt = 0;
			double myPriorityHit = 0;
			for (int m = 0; m < a.moveList.size(); m++) {
				double damage = myDamage[i][m][j] * myAttack / foeDefence;
				dealt = Math.max(dealt, damage);
				if (a.moveList.get(m).getPriority() > 0) {
					myPriorityHit = Math.max(myPriorityHit, damage);
				}
			}
			double taken = 0;
			double foePriorityHit = 0;
			for (int m = 0; m < b.moveList.size(); m++) {
				double damage = foeDamage[j][m][i] * foeAttack / myDefence;
				taken = Math.max(taken, damage);
				if (b.moveList.get(m).getPriority() > 0) {
					foePriorityHit = Math.max(foePriorityHit, damage);
				}
			}
			double wins;
			if (myPriorityHit >= foeHp && foePriorityHit < myHp) {
				wins = 0.6;
			} else if (foePriorityHit >= myHp && myPriorityHit < foeHp) {
				wins = -0.6;
			} else {
				boolean first = ExpertBattleAI.speed(a) * mySpeed >= ExpertBattleAI.speed(b) * foeSpeed;
				double toKo = dealt <= 0 ? 99 : Math.ceil(foeHp / dealt);
				double toDie = taken <= 0 ? 99 : Math.ceil(myHp / taken);
				wins = toKo < toDie || toKo == toDie && first ? 0.6 : -0.6;
			}
			return wins + 0.2 * Math.min(1, dealt / b.maxHp) - 0.2 * Math.min(1, taken / a.maxHp);
		}

		/**
		 * The board for us: HP weighted by each Pokémon's importance (the only check to a threat, a win condition), every
		 * opposing threat still checked, win conditions, the matchup on the field, boosts, hazards and statuses.
		 */
		double evaluate(State s) {
			int n = mine.size();
			int f = theirs.size();
			double[][] table = new double[n][f];
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < f; j++) {
					if (s.myHp[i] <= 0 || s.foeHp[j] <= 0) {
						continue;
					}
					// Each side's boosts count where its boosted Pokémon is: on the field.
					boolean mineOut = i == s.myActive;
					boolean foeOut = j == s.foeActive;
					table[i][j] = duel(i, j, s.myHp[i], s.foeHp[j],
							mineOut ? s.myAttack : 1, mineOut ? s.mySpeed : 1, mineOut ? s.myDefence : 1,
							foeOut ? s.foeAttack : 1, foeOut ? s.foeSpeed : 1, foeOut ? s.foeDefence : 1);
				}
			}
			int[] checksOfFoe = new int[f];
			int[] checksOfMine = new int[n];
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < f; j++) {
					if (s.myHp[i] > 0 && s.foeHp[j] > 0) {
						if (table[i][j] > 0) {
							checksOfFoe[j]++;
						} else {
							checksOfMine[i]++;
						}
					}
				}
			}
			double score = 0;
			int foeAlive = unknownFoes;
			for (int j = 0; j < f; j++) {
				foeAlive += s.foeHp[j] > 0 ? 1 : 0;
			}
			boolean myWinCondition = false;
			for (int i = 0; i < n; i++) {
				if (s.myHp[i] <= 0) {
					score -= 1.0;
					continue;
				}
				double importance = 1;
				int beaten = 0;
				for (int j = 0; j < f; j++) {
					if (s.foeHp[j] > 0 && table[i][j] > 0) {
						beaten++;
						if (checksOfFoe[j] == 1) {
							importance += 0.5; // the only answer to that threat
						}
					}
				}
				if (beaten > 0 && beaten == foeAlive) {
					myWinCondition = true;
					importance += 0.6;
				}
				score += importance * s.myHp[i] / mine.get(i).maxHp;
			}
			boolean foeWinCondition = false;
			for (int j = 0; j < f; j++) {
				if (s.foeHp[j] <= 0) {
					score += 1.0;
					continue;
				}
				double importance = 1;
				int beaten = 0;
				int myAlive = 0;
				for (int i = 0; i < n; i++) {
					if (s.myHp[i] > 0) {
						myAlive++;
						if (table[i][j] <= 0) {
							beaten++;
							if (checksOfMine[i] == 1) {
								importance += 0.5;
							}
						}
					}
				}
				if (beaten > 0 && beaten == myAlive) {
					foeWinCondition = true;
					importance += 0.6;
				}
				score -= importance * s.foeHp[j] / theirs.get(j).maxHp;
				// A threat with no answer left on our side.
				if (checksOfFoe[j] == 0) {
					score -= 0.35;
				}
			}
			score -= unknownFoes;
			score += myWinCondition ? 0.5 : 0;
			score -= foeWinCondition ? 0.5 : 0;
			if (s.myHp[s.myActive] > 0 && s.foeHp[s.foeActive] > 0) {
				score += 0.3 * table[s.myActive][s.foeActive];
			}
			score += 0.25 * (Math.min(s.myAttack, 3) - 1) + 0.1 * (Math.min(s.mySpeed, 2) - 1) + 0.1 * (s.myDefence - 1);
			score -= 0.4 * (Math.min(s.foeAttack, 4) - 1) + 0.2 * (Math.min(s.foeSpeed, 3) - 1) + 0.15 * (s.foeDefence - 1);
			score += (s.rocksOnFoe ? 0.05 : 0) * (foeAlive - 1) + 0.03 * s.spikesOnFoe * (foeAlive - 1);
			score -= (s.rocksOnMe ? 0.05 : 0) * (n - 1) + 0.03 * s.spikesOnMe * (n - 1);
			for (int j = 0; j < f; j++) {
				if (s.foeHp[j] > 0 && !s.foeStatus[j].isEmpty() && !s.foeStatus[j].equals(theirs.get(j).status)) {
					score += s.foeStatus[j].equals("brn") && theirs.get(j).stat(Stats.ATTACK) > theirs.get(j).stat(Stats.SPECIAL_ATTACK) ? 0.3
							: s.foeStatus[j].equals("slp") ? 0.35 : 0.15;
				}
			}
			for (int i = 0; i < n; i++) {
				if (s.myHp[i] > 0 && !s.myStatus[i].isEmpty() && !s.myStatus[i].equals(mine.get(i).status)) {
					score -= 0.2;
				}
			}
			return score;
		}

		/** For the AI debug: opposing threats nothing answers, and the win conditions of both sides. */
		String describe(State s) {
			int n = mine.size();
			int f = theirs.size();
			List<String> unanswered = new ArrayList<>();
			List<String> foeWin = new ArrayList<>();
			List<String> ourWin = new ArrayList<>();
			int myAlive = 0;
			for (int i = 0; i < n; i++) {
				myAlive += s.myHp[i] > 0 ? 1 : 0;
			}
			int foeAlive = unknownFoes;
			for (int j = 0; j < f; j++) {
				foeAlive += s.foeHp[j] > 0 ? 1 : 0;
			}
			for (int j = 0; j < f; j++) {
				if (s.foeHp[j] <= 0) {
					continue;
				}
				int checks = 0;
				int beats = 0;
				for (int i = 0; i < n; i++) {
					if (s.myHp[i] <= 0) {
						continue;
					}
					boolean mineOut = i == s.myActive;
					boolean foeOut = j == s.foeActive;
					double d = duel(i, j, s.myHp[i], s.foeHp[j], mineOut ? s.myAttack : 1, mineOut ? s.mySpeed : 1, mineOut ? s.myDefence : 1,
							foeOut ? s.foeAttack : 1, foeOut ? s.foeSpeed : 1, foeOut ? s.foeDefence : 1);
					if (d > 0) {
						checks++;
					} else {
						beats++;
					}
				}
				if (checks == 0) {
					unanswered.add(theirs.get(j).name);
				}
				if (beats > 0 && beats == myAlive) {
					foeWin.add(theirs.get(j).name);
				}
			}
			for (int i = 0; i < n; i++) {
				if (s.myHp[i] <= 0) {
					continue;
				}
				int beaten = 0;
				for (int j = 0; j < f; j++) {
					if (s.foeHp[j] > 0 && duel(i, j, s.myHp[i], s.foeHp[j]) > 0) {
						beaten++;
					}
				}
				if (beaten > 0 && beaten == foeAlive) {
					ourWin.add(mine.get(i).name);
				}
			}
			return "Sans réponse chez nous : " + (unanswered.isEmpty() ? "aucun" : String.join(", ", unanswered))
					+ " · condition de victoire adverse : " + (foeWin.isEmpty() ? "aucune" : String.join(", ", foeWin))
					+ " · la nôtre : " + (ourWin.isEmpty() ? "aucune" : String.join(", ", ourWin))
					+ (unknownFoes > 0 ? " · " + unknownFoes + " adverse(s) pas encore vu(s)" : "");
		}

		String label(Action a) {
			if (a.switchTo >= 0) {
				return "switch to " + mine.get(a.switchTo).name;
			}
			return a.moveId + (a.tera ? " + tera " + teraType : "") + (a.z ? " (Z)" : "");
		}
	}

	// ================================================================ what is known of a Pokémon

	/** A Pokémon as the AI sees it: exact for its own, believed (revealed + Smogon) for the opponent's. */
	static final class Mon {
		BattlePokemon battler;
		String name = "?";
		int level;
		double hp;
		double maxHp;
		final Map<Stat, Double> stats = new HashMap<>();
		Map<Stat, Integer> stages = Map.of();
		final List<String> types = new ArrayList<>();
		String ability = "";
		String item = "";
		String status = "";
		/** Terastallized into this type, or null. */
		String teraType;
		final List<MoveTemplate> moveList = new ArrayList<>();
		/** How likely each move is (1 for a known one). */
		final List<Double> movePrior = new ArrayList<>();

		/** The stat without its stage (the search carries the stages). */
		double stat(Stat stat) {
			return stats.getOrDefault(stat, 100.0);
		}

		int moveIndex(String id) {
			for (int m = 0; m < moveList.size(); m++) {
				if (moveList.get(m).getName().equals(id)) {
					return m;
				}
			}
			return -1;
		}

		void addMove(MoveTemplate move, double prior) {
			if (move != null && moveIndex(move.getName()) < 0) {
				moveList.add(move);
				movePrior.add(prior);
			}
		}
	}

	static Mon common(BattlePokemon battler) {
		Pokemon pokemon = battler.getEffectedPokemon();
		Mon mon = new Mon();
		mon.battler = battler;
		mon.name = pokemon.getSpecies().getName();
		mon.level = pokemon.getLevel();
		mon.hp = battler.getHealth();
		mon.maxHp = Math.max(1, battler.getMaxHealth());
		mon.stages = battler.getStatChanges();
		for (ElementalType type : pokemon.getTypes()) {
			mon.types.add(type.getName().toLowerCase(Locale.ROOT));
		}
		var status = pokemon.getStatus();
		mon.status = status == null ? "" : status.getStatus().getShowdownName();
		return mon;
	}

	/** One of ours: everything known. */
	static Mon own(BattlePokemon battler) {
		Mon mon = common(battler);
		Pokemon pokemon = battler.getEffectedPokemon();
		for (Stat stat : List.of(Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)) {
			mon.stats.put(stat, (double) pokemon.getStat(stat));
		}
		mon.ability = id(pokemon.getAbility() == null ? "" : pokemon.getAbility().getName());
		var stack = pokemon.heldItem();
		mon.item = stack == null || stack.isEmpty() ? "" : id(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
		for (Move move : battler.getMoveSet().getMoves()) {
			if (move.getCurrentPp() > 0) {
				mon.addMove(move.getTemplate(), 1.0);
			}
		}
		return mon;
	}

	/**
	 * One of the opponent's: what is visible (species, level, HP, stages, status, types) and the moves it has used;
	 * the rest from Smogon's usage (moves until four, item, ability, spread). A Pokémon Smogon has no data on is read
	 * as it is.
	 */
	static Mon belief(BattlePokemon battler) {
		Pokemon pokemon = battler.getEffectedPokemon();
		JsonObject set = sets().get(id(pokemon.getForm().showdownId()));
		if (set == null) {
			set = sets().get(id(pokemon.getSpecies().getName()));
		}
		if (set == null) {
			return own(battler);
		}
		Mon mon = common(battler);
		Map<Stat, Integer> base = pokemon.getForm().getBaseStats();
		Nature nature = set.has("nature") ? Natures.getNature(set.get("nature").getAsString()) : null;
		JsonArray evs = set.has("evs") ? set.getAsJsonArray("evs") : null;
		List<Stat> order = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);
		for (int i = 1; i < order.size(); i++) {
			Stat stat = order.get(i);
			int ev = evs == null ? 84 : evs.get(i).getAsInt();
			double value = Math.floor((2.0 * base.getOrDefault(stat, 80) + 31 + Math.floor(ev / 4.0)) * mon.level / 100) + 5;
			if (nature != null && stat == nature.getIncreasedStat() && stat != nature.getDecreasedStat()) {
				value = Math.floor(value * 1.1);
			} else if (nature != null && stat == nature.getDecreasedStat() && stat != nature.getIncreasedStat()) {
				value = Math.floor(value * 0.9);
			}
			mon.stats.put(stat, value);
		}
		mon.ability = set.has("ability") ? id(set.get("ability").getAsString()) : "";
		mon.item = set.has("item") ? id(set.get("item").getAsString()) : "";
		for (Move move : battler.getMoveSet().getMoves()) {
			if (move.getCurrentPp() < move.getMaxPp()) {
				mon.addMove(move.getTemplate(), 1.0);
			}
		}
		for (JsonElement element : set.getAsJsonArray("moves")) {
			if (mon.moveList.size() >= 4) {
				break;
			}
			mon.addMove(Moves.getByName(element.getAsJsonArray().get(0).getAsString()), element.getAsJsonArray().get(1).getAsDouble());
		}
		return mon;
	}

	static synchronized Map<String, JsonObject> sets() {
		if (sets == null) {
			sets = new HashMap<>();
			try (InputStream in = ExpertBattleAI.class.getResourceAsStream("/assets/phantasmon_events/ai/smogon_sets.json")) {
				if (in != null) {
					JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					all.entrySet().forEach(entry -> sets.put(entry.getKey(), entry.getValue().getAsJsonObject()));
				}
			} catch (Exception ex) {
				LOG.warn("Smogon sets unreadable: the Expert AI reads the opponents as they are", ex);
			}
		}
		return sets;
	}

	static String id(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	// ================================================================ damage

	private static final Set<String> FIXED_DAMAGE = Set.of("seismictoss", "nightshade");

	static boolean isStatus(MoveTemplate move) {
		return "status".equals(move.getDamageCategory().getName()) || move.getPower() <= 0 && !FIXED_DAMAGE.contains(move.getName());
	}

	/**
	 * Expected damage in HP points without stat stages (the search carries them), accuracy included; 0 for a status
	 * move or an immune target. {@code attackerTera}: its Tera type (STAB), {@code defenderTera}: the defender's (its
	 * only type), {@code zPower}: a Z-move's power.
	 */
	/** Common physical moves that make no contact; every other physical move does (special ones never). */
	private static final Set<String> NO_CONTACT = Set.of("earthquake", "stoneedge", "rockslide", "bulldoze", "earthpower",
			"iciclecrash", "iciclespear", "rockblast", "bulletseed", "scaleshot", "pinmissile", "gunkshot",
			"bonemerang", "precipiceblades", "thousandarrows", "diamondstorm", "seedbomb", "magnetbomb", "beatup", "fling",
			"pyroball", "bonerush", "glaciallance", "rockwrecker", "explosion", "selfdestruct",
			"saltcure", "spiritshackle", "skyattack", "drumbeating", "terablast", "smackdown",
			"thousandwaves", "shadowbone", "mountaingale", "stoneaxe", "ceaselessedge", "tachyoncutter",
			"barrage", "eggbomb", "rockthrow", "rocktomb", "iceshard", "aquacutter",
			"psychocut", "razorleaf", "sacredfire");

	static boolean contact(MoveTemplate move) {
		return "physical".equals(move.getDamageCategory().getName()) && !NO_CONTACT.contains(move.getName());
	}

	private static double damage(Mon attacker, Mon defender, MoveTemplate move, String attackerTera, double zPower, String defenderTera) {
		if (effectiveness(move.getElementalType(), defender, defenderTera) == 0) {
			return 0;
		}
		if (FIXED_DAMAGE.contains(move.getName())) {
			return attacker.level * accuracy(move);
		}
		if (isStatus(move)) {
			return 0;
		}
		return 0.925 * rawDamage(attacker, defender, move.getName(), move.getElementalType(),
				"physical".equals(move.getDamageCategory().getName()), move.getPower(), attackerTera, zPower, defenderTera)
				* (zPower > 0 ? 1 : accuracy(move));
	}

	/**
	 * A damaging move's damage at the top roll, multi-hit counted, without accuracy nor stat stages (shared with the
	 * Reborn AI, which rolls and stages it itself). {@code physical}: the category.
	 */
	static double rawDamage(Mon attacker, Mon defender, String name, ElementalType type, boolean physical, double basePower,
			String attackerTera, double zPower, String defenderTera) {
		String moveType = type.getName().toLowerCase(Locale.ROOT);
		double effectiveness = effectiveness(type, defender, defenderTera);
		if (effectiveness == 0) {
			return 0;
		}
		if (FIXED_DAMAGE.contains(name)) {
			return attacker.level;
		}
		double attack = name.equals("foulplay") ? defender.stat(Stats.ATTACK)
				: name.equals("bodypress") ? attacker.stat(Stats.DEFENCE)
				: attacker.stat(physical ? Stats.ATTACK : Stats.SPECIAL_ATTACK);
		boolean targetsDefence = physical || name.equals("psyshock") || name.equals("psystrike") || name.equals("secretsword");
		double defence = defender.stat(targetsDefence ? Stats.DEFENCE : Stats.SPECIAL_DEFENCE);
		if (physical && (attacker.ability.equals("hugepower") || attacker.ability.equals("purepower"))) {
			attack *= 2;
		}
		if (physical && attacker.item.equals("choiceband") || !physical && attacker.item.equals("choicespecs")) {
			attack *= 1.5;
		}
		if (!targetsDefence && defender.item.equals("assaultvest")) {
			defence *= 1.5;
		}
		if (defender.item.equals("eviolite")) {
			defence *= 1.5;
		}
		double power = zPower > 0 ? zPower : basePower;
		double base = Math.floor(Math.floor(Math.floor(2.0 * attacker.level / 5 + 2) * power * attack / Math.max(1, defence)) / 50) + 2;
		double stab = 1;
		boolean original = attacker.types.contains(moveType);
		if (original) {
			stab = attacker.ability.equals("adaptability") ? 2 : 1.5;
		}
		if (attackerTera != null && attackerTera.equals(moveType)) {
			stab = original ? 2 : 1.5;
		}
		double modifier = stab * effectiveness;
		if (physical && attacker.ability.equals("guts") && !attacker.status.isEmpty()) {
			modifier *= 1.5;
		}
		if (attacker.item.equals("lifeorb")) {
			modifier *= 1.3;
		}
		if (attacker.item.equals("expertbelt") && effectiveness > 1) {
			modifier *= 1.2;
		}
		var hits = zPower > 0 ? null : AIUtility.INSTANCE.getMultiHitMoves().get(name);
		double count = hits == null ? 1
				: attacker.ability.equals("skilllink") ? hits.getSecond() : (hits.getFirst() + hits.getSecond()) / 2.0 + (hits.getSecond() == 5 ? 0.1 : 0);
		return base * modifier * count;
	}

	/** Type effectiveness on a Pokémon (its Tera type alone if terastallized), immunity abilities and Air Balloon included. */
	static double effectiveness(ElementalType moveType, Mon defender, String defenderTera) {
		if (moveType == null) {
			return 1;
		}
		ElementalType immune = AIUtility.INSTANCE.getTypeImmuneAbilities().get(defender.ability);
		if (immune != null && immune.getName().equalsIgnoreCase(moveType.getName())) {
			return 0;
		}
		if (defender.item.equals("airballoon") && moveType.getName().equalsIgnoreCase("ground")) {
			return 0;
		}
		String tera = defenderTera != null ? defenderTera : defender.teraType;
		List<String> types = tera != null && !tera.equals("stellar") ? List.of(tera) : defender.types;
		double multiplier = 1;
		for (String type : types) {
			ElementalType defending = ElementalTypes.get(type);
			if (defending != null) {
				multiplier *= AIUtility.INSTANCE.getDamageMultiplier(moveType, defending);
			}
		}
		return multiplier;
	}

	/** A Z-move's power from its base move's (the games' table). */
	static double zPower(double power) {
		if (power <= 55) {
			return 100;
		} else if (power <= 65) {
			return 120;
		} else if (power <= 75) {
			return 140;
		} else if (power <= 85) {
			return 160;
		} else if (power <= 95) {
			return 175;
		} else if (power <= 100) {
			return 180;
		} else if (power <= 110) {
			return 185;
		} else if (power <= 125) {
			return 190;
		} else if (power <= 130) {
			return 195;
		}
		return 200;
	}

	static double accuracy(MoveTemplate move) {
		double accuracy = move.getAccuracy();
		return accuracy <= 0 || accuracy > 100 ? 1 : accuracy / 100;
	}

	/** Speed with its stage, paralysis and Choice Scarf (for the one-on-one table). */
	static double speed(Mon mon) {
		int stage = mon.stages.getOrDefault(Stats.SPEED, 0);
		double speed = mon.stat(Stats.SPEED) * (stage >= 0 ? (2.0 + stage) / 2 : 2.0 / (2 - stage));
		if (mon.status.equals("par")) {
			speed *= 0.5;
		}
		if (mon.item.equals("choicescarf")) {
			speed *= 1.5;
		}
		return speed;
	}
}
