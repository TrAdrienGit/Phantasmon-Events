package com.mystaria.events.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.mystaria.phantasmon.client.api.PhantasmonBattleAI;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
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
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;

import com.mystaria.events.ai.ExpertBattleAI.Mon;

/**
 * The AI of Pokémon Reborn, level {@code reborn} (Adrien 2026-11-03: "un graal en termes de difficulté"), translated
 * from Reborn's {@code Battle_AI.rb} at its highest skill ({@code BESTSKILL}), single battles only. Reborn's field
 * effects, double battles, boss Pokémon and item use (NPCs have no bag) are left out.
 *
 * <p><b>How it chooses</b> — no search, a hand-tuned score per option:
 * <ul>
 * <li>each usable move starts from the % of the target's HP it takes (capped at 110; a status move from a fixed
 * table), then is multiplied by general rules (immunities, priority that finishes or saves, contact punished, a killing
 * move preferred over setup, accuracy…) and the move's own rules (its "miniscore");</li>
 * <li>a switch score weighs reasons to leave (status, dropped stats, nothing useful to do, a bad matchup) against
 * reasons to stay (boosts, hazards on its side, a fresh Pokémon, a KO it can land first); the Pokémon coming in is the
 * one whose own score (takes the hits, hits back, its role, ability, item) is best, and never one that would just
 * die;</li>
 * <li>it switches when the switch score beats every move's; otherwise weak moves are cut and it picks at random among
 * the moves within 95% of the best (the best counted twice).</li>
 * </ul>
 *
 * <p><b>What it knows of the opponent</b>, as Reborn: the moves it has seen each Pokémon use (its "memory"), with an
 * 80-power STAB move standing for each type not yet shown while it has seen fewer than four. Stats, item and ability
 * come from Smogon's usual set (Reborn reads the real ones), corrected by what the battle revealed. Battle thread.
 */
public final class RebornBattleAI implements PhantasmonBattleAI {

	private static final Logger LOG = LoggerFactory.getLogger(RebornBattleAI.class);

	/** Percent values of non-damaging moves (Reborn's {@code PBStuff::STATUSDAMAGE}). */
	private static final Map<String, Integer> STATUS_DAMAGE = new HashMap<>();

	static {
		statusDamage(0, "afteryou bestow craftyshield luckychant memento quash safeguard spite splash sweetscent telekinesis teleport");
		statusDamage(5, "allyswitch aromaticmist camouflage conversion endure entrainment flowershield foresight forestscurse gravity "
				+ "defog guardswap healblock imprison instruct fairylock laserfocus helpinghand magicroom magnetrise soak lockon "
				+ "mindreader miracleeye mudsport nightmare odorsleuth powersplit powerswap grudge guardsplit powertrick quickguard "
				+ "recycle reflecttype rototiller skillswap snatch magiccoat speedswap spotlight swallow teeterdance watersport "
				+ "wideguard wonderroom magicpowder teatime powershift");
		statusDamage(10, "acidarmor acupressure agility amnesia autotomize babydolleyes barrier bellydrum bulkup calmmind captivate "
				+ "charge charm coil confide cosmicpower cottonguard cottonspore curse defendorder defensecurl dragondance doubleteam "
				+ "eerieimpulse embargo faketears featherdance flash focusenergy geomancy growl growth gearup harden haze honeclaws "
				+ "howl irondefense kinesis leer magneticflux meditate metalsound minimize nastyplot nobleroar playnice powder psychup "
				+ "protect quiverdance rockpolish sandattack scaryface screech sharpen shellsmash shiftgear smokescreen stockpile "
				+ "stringshot supersonic swordsdance tailglow tailwhip tearfullook tickle torment venomdrench wish withdraw workup "
				+ "tarshot clangoroussoul decorate coaching extremeevoboost victorydance aquabatics takeheart shelter");
		statusDamage(15, "assist batonpass darkvoid floralhealing grasswhistle healpulse healingwish hypnosis ingrain lunardance "
				+ "mefirst mimic partingshot poisonpowder refresh roleplay sing sketch trickortreat toxicthread sandstorm hail snowscape "
				+ "sunnyday raindance lifedew");
		statusDamage(20, "aquaring block conversion2 detect electrify flatter gastroacid healorder heartswap iondeluge meanlook "
				+ "lovelykiss milkdrink metronome moonlight morningsun copycat mirrormove mist perishsong recover rest roar roost "
				+ "simplebeam shoreup spiderweb sleeppowder slackoff softboiled strengthsap swagger sweetkiss synthesis poisongas "
				+ "transform whirlwind worryseed yawn junglehealing lunarblessing");
		statusDamage(25, "attract confuseray destinybond disable followme leechseed painsplit psychoshift ragepowder stunspore "
				+ "substitute switcheroo taunt topsyturvy toxic trick willowisp corrosivegas octolock");
		statusDamage(30, "electricterrain encore glare grassyterrain mistyterrain naturepower psychicterrain purify sleeptalk spikes "
				+ "stealthrock spikyshield thunderwave toxicspikes trickroom");
		statusDamage(35, "aromatherapy banefulbunker healbell kingsshield lightscreen matblock reflect tailwind obstruct stuffcheeks "
				+ "noretreat courtchange silktrap burningbulwark");
		statusDamage(60, "auroraveil stickyweb spore");
	}

	private static void statusDamage(int value, String moves) {
		for (String move : moves.split(" ")) {
			STATUS_DAMAGE.put(move, value);
		}
	}

	private static final Set<String> PROTECT = Set.of("protect", "detect", "kingsshield", "spikyshield", "banefulbunker", "obstruct",
			"silktrap", "burningbulwark");
	private static final Set<String> PHASE = Set.of("roar", "whirlwind", "circlethrow", "dragontail", "yawn", "perishsong");
	private static final Set<String> PIVOT = Set.of("uturn", "voltswitch", "partingshot", "flipturn");
	private static final Set<String> SCREEN = Set.of("lightscreen", "reflect", "auroraveil");
	private static final Set<String> DANCE = Set.of("quiverdance", "dragondance", "fierydance", "featherdance", "petaldance",
			"swordsdance", "teeterdance", "lunardance", "revelationdance", "victorydance", "aquastep");
	private static final Set<String> BULLET = Set.of("acidspray", "aurasphere", "barrage", "bulletseed", "eggbomb", "electroball",
			"energyball", "focusblast", "gyroball", "iceball", "magnetbomb", "mistball", "mudbomb", "octazooka", "pollenpuff",
			"rockwrecker", "searingshot", "seedbomb", "shadowball", "sludgebomb", "weatherball", "zapcannon", "beakblast", "pyroball");
	private static final Set<String> SOUND = Set.of("boomburst", "bugbuzz", "chatter", "clangingscales", "clangoroussoul",
			"confide", "disarmingvoice", "echoedvoice", "grasswhistle", "growl", "healbell", "hypervoice", "metalsound", "nobleroar",
			"overdrive", "partingshot", "perishsong", "relicsong", "roar", "round", "screech", "sing", "snarl", "snore", "sparklingaria",
			"supersonic", "uproar", "torchsong", "alluringvoice", "psychicnoise");
	private static final Set<String> BURN_MOVES = Set.of("willowisp", "sacredfire", "inferno", "burningjealousy");
	private static final Set<String> PARA_MOVES = Set.of("thunderwave", "stunspore", "glare", "nuzzle", "zapcannon");
	private static final Set<String> SLEEP_MOVES = Set.of("spore", "sleeppowder", "hypnosis", "darkvoid", "grasswhistle",
			"lovelykiss", "sing", "yawn");
	private static final Set<String> POISON_MOVES = Set.of("toxic", "poisonpowder", "poisongas", "toxicthread");
	private static final Set<String> CONFUSE_MOVES = Set.of("confuseray", "supersonic", "flatter", "swagger", "sweetkiss",
			"teeterdance", "chatter", "dynamicpunch");
	private static final Set<String> POWDER = Set.of("cottonspore", "sleeppowder", "stunspore", "spore", "ragepowder",
			"poisonpowder", "powder", "magicpowder");
	private static final Set<String> UNFREEZE = Set.of("flamewheel", "sacredfire", "flareblitz", "fusionflare", "scald",
			"steameruption", "burnup", "pyroball", "scorchingsands", "matchagotcha");
	private static final Set<String> TWO_TURN = Set.of("bounce", "dig", "dive", "fly", "phantomforce", "shadowforce", "skydrop");
	private static final Set<String> HEALING = Set.of("recover", "roost", "softboiled", "slackoff", "milkdrink", "moonlight",
			"morningsun", "synthesis", "shoreup", "wish", "rest", "strengthsap", "healorder", "junglehealing", "lunarblessing",
			"lifedew", "swallow");
	/** Recoil moves and their share of the damage dealt. */
	private static final Map<String, Double> RECOIL = Map.ofEntries(Map.entry("bravebird", 0.33), Map.entry("doubleedge", 0.33),
			Map.entry("flareblitz", 0.33), Map.entry("woodhammer", 0.33), Map.entry("headsmash", 0.5), Map.entry("volttackle", 0.33),
			Map.entry("wildcharge", 0.25), Map.entry("takedown", 0.25), Map.entry("headcharge", 0.25), Map.entry("submission", 0.25),
			Map.entry("wavecrash", 0.33), Map.entry("lightofruin", 0.5));
	private static final Set<String> TYPE_ABSORBS_FIRE = Set.of("flashfire", "wellbakedbody");
	private static final Set<String> TYPE_ABSORBS_WATER = Set.of("waterabsorb", "stormdrain", "dryskin");
	private static final Set<String> TYPE_ABSORBS_GRASS = Set.of("sapsipper");
	private static final Set<String> TYPE_ABSORBS_ELECTRIC = Set.of("voltabsorb", "lightningrod", "motordrive");
	private static final Set<String> MOLD_BREAKERS = Set.of("moldbreaker", "teravolt", "turboblaze");

	private final boolean debug;
	private final Random random = new Random();

	// ---------------------------------------------------------------- what the battle showed (Showdown's log)

	private int logRead;
	private int turn;
	private boolean upkeep;
	/** Moves each opposing Pokémon was seen using (Reborn's AI memory). */
	private final Map<UUID, Set<String>> memory = new HashMap<>();
	/** Item ("" once lost) and ability each Pokémon revealed. */
	private final Map<UUID, String> revealedItem = new HashMap<>();
	private final Map<UUID, String> revealedAbility = new HashMap<>();
	/** Volatile effects on the field (leechseed, substitute, confusion, encore, taunt, torment, yawn, curse, perish…). */
	private final Map<UUID, Set<String>> volatiles = new HashMap<>();
	private final Map<UUID, Integer> entered = new HashMap<>();
	private final Map<UUID, Integer> toxicSince = new HashMap<>();
	private final Map<UUID, String> lastMove = new HashMap<>();
	/** A two-turn move being charged (Fly, Dig…). */
	private final Map<UUID, String> charging = new HashMap<>();
	/** Showdown's side ids ("p1", "p2") → its conditions (stealthrock, spikes×n, toxicspikes×n, stickyweb, reflect…). */
	private final Map<String, Map<String, Integer>> sideConditions = new HashMap<>();
	private String weather = "";
	private String terrain = "";
	private boolean trickRoom;
	private String mySide;
	private boolean zUsed;

	/** Reborn-Tweak: picks its lead against the opponent's team (the expert AI's choice) instead of its first Pokémon. */
	private boolean picksLead;

	public RebornBattleAI(boolean debug, boolean picksLead) {
		this(debug);
		this.picksLead = picksLead;
	}

	public boolean picksLead() {
		return picksLead;
	}

	/**
	 * Reborn-Tweak (Adrien 2026-11-07) leads with the expert's choice; plain Reborn, as in the game, with the first of
	 * its team.
	 */
	@Override
	public List<BattlePokemon> orderTeam(List<BattlePokemon> own, List<BattlePokemon> opponents) {
		return picksLead ? new ExpertBattleAI(false).leadFirst(own, opponents) : own;
	}

	@Override
	public void onChoiceRefused(String message) {
		ExpertBattleAI.say(message);
	}

	public RebornBattleAI(boolean debug) {
		this.debug = debug;
	}

	@Override
	public ShowdownActionResponse choose(ActiveBattlePokemon active, PokemonBattle battle, BattleSide side, ShowdownMoveset moveset,
			boolean forceSwitch) {
		try {
			return decide(active, side, moveset, forceSwitch);
		} catch (RuntimeException ex) {
			LOG.warn("Reborn AI failed, the random AI plays this turn", ex);
			return forceSwitch ? null : new RandomBattleAI().choose(active, battle, side, moveset, forceSwitch);
		}
	}

	private void readLog(PokemonBattle battle, Set<UUID> ours) {
		List<String> log = battle.getShowdownMessages();
		for (int i = Math.min(logRead, log.size()); i < log.size(); i++) {
			for (String line : log.get(i).split("\n")) {
				String[] parts = line.split("\\|");
				if (parts.length < 2) {
					continue;
				}
				read(parts, ours);
			}
		}
		logRead = log.size();
	}

	private void read(String[] parts, Set<UUID> ours) {
		String kind = parts[1];
		UUID uuid = parts.length > 2 ? ExpertBattleAI.uuidOf(parts[2]) : null;
		String value = parts.length > 3 ? ExpertBattleAI.id(parts[3].replaceFirst("^(move|item|ability): ", "")) : "";
		switch (kind) {
			case "turn" -> {
				turn = parts.length > 2 ? Integer.parseInt(parts[2].strip()) : turn + 1;
				upkeep = false;
			}
			case "upkeep" -> upkeep = true;
			case "switch", "drag", "replace" -> {
				if (uuid == null) {
					return;
				}
				if (mySide == null && ours.contains(uuid)) {
					mySide = parts[2].substring(0, 2);
				}
				// Reborn's turncount: 0 on the first turn out (a lead, or a Pokémon sent after the turn ended).
				entered.put(uuid, turn == 0 || upkeep ? turn + 1 : turn);
				volatiles.remove(uuid);
				charging.remove(uuid);
				toxicSince.remove(uuid);
			}
			case "move" -> {
				if (uuid != null) {
					memory.computeIfAbsent(uuid, key -> new HashSet<>()).add(value);
					lastMove.put(uuid, value);
					charging.remove(uuid);
					// Destiny Bond lasts until its user moves again; a recharge is spent.
					if (volatiles.containsKey(uuid)) {
						volatiles.get(uuid).remove("destinybond");
						volatiles.get(uuid).remove("mustrecharge");
					}
				}
			}
			case "-mustrecharge" -> {
				if (uuid != null) {
					volatiles.computeIfAbsent(uuid, key -> new HashSet<>()).add("mustrecharge");
				}
			}
			case "-activate" -> {
				if (uuid != null && value.equals("trapped")) {
					volatiles.computeIfAbsent(uuid, key -> new HashSet<>()).add("trapped");
				}
			}
			case "cant" -> {
				if (uuid != null && volatiles.containsKey(uuid)) {
					volatiles.get(uuid).remove("mustrecharge");
				}
			}
			case "-prepare" -> {
				if (uuid != null) {
					charging.put(uuid, value);
				}
			}
			case "-item" -> revealedItem.put(uuid, value);
			case "-enditem" -> revealedItem.put(uuid, "");
			case "-ability" -> revealedAbility.put(uuid, value);
			case "-status" -> {
				if (uuid != null && value.equals("tox")) {
					toxicSince.put(uuid, turn);
				}
			}
			case "-start", "-singlemove", "-singleturn" -> {
				if (uuid != null) {
					Set<String> effects = volatiles.computeIfAbsent(uuid, key -> new HashSet<>());
					if (value.startsWith("perish")) {
						effects.removeIf(effect -> effect.startsWith("perish"));
					}
					effects.add(value);
				}
			}
			case "-end" -> {
				if (uuid != null && volatiles.containsKey(uuid)) {
					volatiles.get(uuid).remove(value);
				}
			}
			case "faint" -> {
				if (uuid != null) {
					volatiles.remove(uuid);
				}
			}
			case "-sidestart" -> side(parts).merge(value, 1, Integer::sum);
			case "-sideend" -> side(parts).remove(value);
			case "-weather" -> weather = parts.length < 3 || parts[2].equals("none") ? "" : ExpertBattleAI.id(parts[2]);
			case "-fieldstart" -> {
				String field = parts.length > 2 ? ExpertBattleAI.id(parts[2].replaceFirst("^move: ", "")) : "";
				if (field.equals("trickroom")) {
					trickRoom = true;
				} else if (field.endsWith("terrain")) {
					terrain = field;
				}
			}
			case "-fieldend" -> {
				String field = parts.length > 2 ? ExpertBattleAI.id(parts[2].replaceFirst("^move: ", "")) : "";
				if (field.equals("trickroom")) {
					trickRoom = false;
				} else if (field.endsWith("terrain")) {
					terrain = "";
				}
			}
			default -> {
			}
		}
	}

	private Map<String, Integer> side(String[] parts) {
		String id = parts.length > 2 && parts[2].length() >= 2 ? parts[2].substring(0, 2) : "?";
		return sideConditions.computeIfAbsent(id, key -> new HashMap<>());
	}

	private Map<String, Integer> conditions(boolean ours) {
		if (mySide == null) {
			return Map.of();
		}
		String id = ours ? mySide : mySide.equals("p1") ? "p2" : "p1";
		return sideConditions.getOrDefault(id, Map.of());
	}

	// ---------------------------------------------------------------- the battlers

	/** A move as Reborn's AI handles it: a real one, or the fake STAB move standing for an unseen one. */
	private static final class Mv {
		MoveTemplate template;
		String id;
		ElementalType type;
		boolean physical;
		boolean status;
		double power;
		int priority;
		/** 0: never misses. */
		double accuracy;
		/** The slot in the moveset (ours), or -1. */
		int slot = -1;
		boolean disabled;
		int pp = 99;

		static Mv of(MoveTemplate template) {
			Mv mv = new Mv();
			mv.template = template;
			mv.id = template.getName();
			mv.type = template.getElementalType();
			mv.physical = "physical".equals(template.getDamageCategory().getName());
			mv.status = ExpertBattleAI.isStatus(template);
			mv.power = template.getPower();
			mv.priority = template.getPriority();
			mv.accuracy = template.getAccuracy() > 100 ? 0 : Math.max(0, template.getAccuracy());
			return mv;
		}

		/** Reborn's {@code PokeBattle_Move_FFF}: 80 power (less at a low level), the user's better attacking side. */
		static Mv fake(B user, ElementalType type) {
			Mv mv = new Mv();
			mv.id = "fakemove";
			mv.type = type;
			mv.physical = user.mon.stat(Stats.ATTACK) > user.mon.stat(Stats.SPECIAL_ATTACK);
			mv.power = user.mon.level >= 40 ? 80 : Math.max(2 * user.mon.level, 40);
			mv.accuracy = 100;
			return mv;
		}

		boolean contact() {
			return template != null && ExpertBattleAI.contact(template);
		}

		boolean sound() {
			return SOUND.contains(id);
		}

		boolean multiHit() {
			return com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility.INSTANCE.getMultiHitMoves().containsKey(id);
		}

		boolean highCrit() {
			return template != null && template.getCritRatio() > 1;
		}

		String name() {
			return template == null ? "Attaque " + type.getDisplayName().getString() : template.getDisplayName().getString();
		}
	}

	/** A Pokémon on the field or in a party, as Reborn's battler clone. */
	private final class B {
		Mon mon;
		BattlePokemon battler;
		boolean ours;
		double hp;
		double maxHp;
		/** Stat stages: attack, defence, sp. attack, sp. defence, speed, accuracy, evasion. */
		final int[] stages = new int[7];
		final List<Mv> moves = new ArrayList<>();
		Set<String> roles = Set.of();
		int partyIndex;

		boolean has(String effect) {
			Set<String> set = volatiles.get(battler.getUuid());
			return set != null && set.contains(effect);
		}

		int turncount() {
			Integer since = entered.get(battler.getUuid());
			return since == null ? 0 : Math.max(0, turn - since);
		}

		String ability() {
			return mon.ability;
		}

		String item() {
			return mon.item;
		}

		String status() {
			return mon.status;
		}

		boolean hasType(String type) {
			return mon.types.contains(type);
		}

		boolean hasMove(String id) {
			for (Mv move : moves) {
				if (move.id.equals(id)) {
					return true;
				}
			}
			return false;
		}

		double attack() {
			return mon.stat(Stats.ATTACK);
		}

		double spatk() {
			return mon.stat(Stats.SPECIAL_ATTACK);
		}

		double speed() {
			double speed = mon.stat(Stats.SPEED) * stageMul(stages[4]);
			if (status().equals("par") && !ability().equals("quickfeet")) {
				speed *= 0.5;
			}
			if (item().equals("choicescarf")) {
				speed *= 1.5;
			}
			if (conditions(ours).containsKey("tailwind")) {
				speed *= 2;
			}
			if (ability().equals("swiftswim") && weather.equals("raindance") || ability().equals("chlorophyll") && weather.equals("sunnyday")
					|| ability().equals("sandrush") && weather.equals("sandstorm")
					|| ability().equals("slushrush") && (weather.equals("hail") || weather.equals("snow"))) {
				speed *= 2;
			}
			return speed;
		}

		boolean grounded() {
			return !hasType("flying") && !ability().equals("levitate") && !item().equals("airballoon") && !has("magnetrise");
		}
	}

	private static double stageMul(int stage) {
		return stage >= 0 ? (2.0 + stage) / 2 : 2.0 / (2 - stage);
	}

	private B battler(BattlePokemon pokemon, boolean ours, int partyIndex, boolean onField) {
		B b = new B();
		b.battler = pokemon;
		b.ours = ours;
		b.partyIndex = partyIndex;
		Mon mon = ours ? ExpertBattleAI.own(pokemon) : ExpertBattleAI.belief(pokemon);
		UUID uuid = pokemon.getUuid();
		if (!ours) {
			if (revealedItem.containsKey(uuid)) {
				mon.item = revealedItem.get(uuid);
			}
			if (revealedAbility.containsKey(uuid)) {
				mon.ability = revealedAbility.get(uuid);
			}
		}
		b.mon = mon;
		b.hp = pokemon.getHealth();
		b.maxHp = Math.max(1, pokemon.getMaxHealth());
		if (onField) {
			List<Stat> order = List.of(Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED,
					Stats.ACCURACY, Stats.EVASION);
			for (int s = 0; s < order.size(); s++) {
				b.stages[s] = pokemon.getStatChanges().getOrDefault(order.get(s), 0);
			}
		} else {
			mon.stages = Map.of();
		}
		if (ours) {
			for (Move move : pokemon.getMoveSet().getMoves()) {
				Mv mv = Mv.of(move.getTemplate());
				mv.pp = move.getCurrentPp();
				b.moves.add(mv);
			}
			b.roles = roles(pokemon);
		} else {
			// Its real moves, for the type of the fake STAB moves only: the AI scores against its memory.
			for (Move move : pokemon.getMoveSet().getMoves()) {
				b.moves.add(Mv.of(move.getTemplate()));
			}
			b.roles = roles(pokemon);
		}
		return b;
	}

	/** Reborn's {@code getAIMemory}: the moves seen (its own moves for one of ours). */
	private List<Mv> memoryOf(B b) {
		if (b.ours) {
			return b.moves;
		}
		List<Mv> seen = new ArrayList<>();
		Set<String> ids = memory.getOrDefault(b.battler.getUuid(), Set.of());
		for (Move move : b.battler.getMoveSet().getMoves()) {
			if (ids.contains(move.getTemplate().getName()) || move.getCurrentPp() < move.getMaxPp()) {
				seen.add(Mv.of(move.getTemplate()));
			}
		}
		return seen;
	}

	// ---------------------------------------------------------------- roles

	/** Reborn's {@code pbGetMonRoles}, from the set: its moves, EVs, nature, item, ability and party place. */
	private Set<String> roles(BattlePokemon battler) {
		Pokemon mon = battler.getEffectedPokemon();
		List<BattlePokemon> party = battler.getActor().getPokemonList();
		int index = party.indexOf(battler);
		Set<String> roles = new HashSet<>();
		if (index == 0) {
			roles.add("lead");
		}
		if (index == party.size() - 1) {
			roles.add("ace");
		}
		boolean secondHighest = true;
		if (party.size() > 2) {
			for (int i = 0; i <= party.size() - 2; i++) {
				if (mon.getLevel() < party.get(i).getEffectedPokemon().getLevel()) {
					secondHighest = false;
				}
			}
		}
		Set<String> moves = new HashSet<>();
		for (Move move : battler.getMoveSet().getMoves()) {
			moves.add(move.getTemplate().getName());
		}
		boolean healing = moves.stream().anyMatch(HEALING::contains);
		String nature = mon.getNature().getName().getPath();
		String item = ExpertBattleAI.id(mon.heldItem().isEmpty() ? ""
				: net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mon.heldItem().getItem()).getPath());
		String ability = ExpertBattleAI.id(mon.getAbility() == null ? "" : mon.getAbility().getName());
		int hpEv = ev(mon, Stats.HP);
		if (ev(mon, Stats.SPEED) > 251 && (Set.of("modest", "jolly", "timid", "adamant").contains(nature)
				|| Set.of("choiceband", "choicespecs", "choicescarf").contains(item) || ability.equals("gorillatactics"))) {
			roles.add("sweeper");
		}
		if (healing && ev(mon, Stats.DEFENCE) > 251 && Set.of("bold", "relaxed", "impish", "lax").contains(nature)) {
			roles.add("physicalwall");
		}
		if (healing && ev(mon, Stats.SPECIAL_DEFENCE) > 251 && Set.of("calm", "gentle", "sassy", "careful").contains(nature)) {
			roles.add("specialwall");
		}
		if (moves.contains("healbell") || moves.contains("aromatherapy") || moves.contains("wish") && hpEv > 251) {
			roles.add("cleric");
		}
		if (moves.stream().anyMatch(PHASE::contains)) {
			roles.add("phazer");
		}
		if (moves.stream().anyMatch(SCREEN::contains) && item.equals("lightclay")) {
			roles.add("screener");
		}
		if (moves.stream().anyMatch(PIVOT::contains) && healing || ability.equals("regenerator")) {
			roles.add("pivot");
		}
		if (moves.contains("rapidspin")) {
			roles.add("spinner");
		}
		if (hpEv > 251 && !healing || item.equals("assaultvest")) {
			roles.add("tank");
		}
		if (moves.contains("batonpass")) {
			roles.add("batonpasser");
		}
		if (moves.contains("taunt") || Set.of("choiceband", "choicespecs").contains(item) || ability.equals("gorillatactics")) {
			roles.add("stallbreaker");
		}
		if (moves.contains("rest") || Set.of("toxicorb", "flameorb").contains(item) || Set.of("comatose", "guts", "quickfeet",
				"flareboost", "toxicboost", "naturalcure", "magicguard", "magicbounce").contains(ability)) {
			roles.add("statusabsorber");
		}
		if (Set.of("arenatrap", "magnetpull", "shadowtag").contains(ability)) {
			roles.add("trapper");
		}
		if (moves.stream().anyMatch(Set.of("sunnyday", "raindance", "hail", "sandstorm", "snowscape")::contains)
				|| Set.of("drought", "sandspit", "sandstream", "drizzle", "snowwarning", "primordialsea", "desolateland", "deltastream").contains(ability)) {
			roles.add("weathersetter");
		}
		if (moves.stream().anyMatch(Set.of("grassyterrain", "electricterrain", "mistyterrain", "psychicterrain")::contains)
				|| Set.of("grassysurge", "electricsurge", "mistysurge", "psychicsurge").contains(ability)) {
			roles.add("fieldsetter");
		}
		if (secondHighest) {
			roles.add("second");
		}
		return roles;
	}

	private static int ev(Pokemon pokemon, Stat stat) {
		Integer value = pokemon.getEvs().get(stat);
		return value == null ? 0 : value;
	}

	// ---------------------------------------------------------------- damage, speed, accuracy

	private boolean moldBreaker(B b) {
		return MOLD_BREAKERS.contains(b.ability());
	}

	/** Reborn's {@code pbTypeModNoMessages}, as a multiplier (0 immune). */
	private double typeMod(Mv move, B attacker, B defender) {
		Mon target = defender.mon;
		if (moldBreaker(attacker)) {
			String ability = target.ability;
			target.ability = "";
			double value = ExpertBattleAI.effectiveness(move.type, target, null);
			target.ability = ability;
			return value;
		}
		if (move.type.getName().equalsIgnoreCase("ground") && defender.has("magnetrise")) {
			return 0;
		}
		return ExpertBattleAI.effectiveness(move.type, target, null);
	}

	/**
	 * Reborn's {@code pbRoughDamage} at its best skill: stat stages, burn, screens, weather and the roll — the minimum
	 * for the AI's own attacks, the maximum for the opponent's.
	 */
	private double roughDamage(Mv move, B attacker, B defender) {
		return roughDamage(move, attacker, defender, 0);
	}

	private double roughDamage(Mv move, B attacker, B defender, double zPower) {
		if (move.status || attacker.hp <= 0 || defender.hp <= 0) {
			return 0;
		}
		if (move.pp == 0 && zPower == 0) {
			return 0;
		}
		if (typeMod(move, attacker, defender) == 0) {
			return 0;
		}
		double damage = ExpertBattleAI.rawDamage(attacker.mon, defender.mon, move.id, move.type, move.physical, move.power,
				attacker.mon.teraType, zPower, null);
		boolean targetsDefence = move.physical || move.id.equals("psyshock") || move.id.equals("psystrike") || move.id.equals("secretsword");
		int attackStage = move.id.equals("foulplay") ? defender.stages[0] : move.id.equals("bodypress") ? attacker.stages[1]
				: attacker.stages[move.physical ? 0 : 2];
		int defenceStage = defender.stages[targetsDefence ? 1 : 3];
		if (defender.ability().equals("unaware") && !moldBreaker(attacker)) {
			attackStage = 0;
		}
		if (attacker.ability().equals("unaware")) {
			defenceStage = 0;
		}
		damage *= stageMul(attackStage) / stageMul(defenceStage);
		if (move.physical && attacker.status().equals("brn") && !attacker.ability().equals("guts") && !move.id.equals("facade")) {
			damage *= 0.5;
		}
		Map<String, Integer> screens = conditions(defender.ours);
		if (!move.id.equals("brickbreak") && !move.id.equals("psychicfangs") && !attacker.ability().equals("infiltrator")
				&& (screens.containsKey("auroraveil") || move.physical && screens.containsKey("reflect")
						|| !move.physical && screens.containsKey("lightscreen"))) {
			damage *= 0.5;
		}
		String type = move.type.getName().toLowerCase(Locale.ROOT);
		if (weather.equals("raindance") || weather.equals("primordialsea")) {
			damage *= type.equals("water") ? 1.5 : type.equals("fire") ? 0.5 : 1;
		} else if (weather.equals("sunnyday") || weather.equals("desolateland")) {
			damage *= type.equals("fire") ? 1.5 : type.equals("water") ? 0.5 : 1;
		}
		if ((defender.ability().equals("multiscale") || defender.ability().equals("shadowshield")) && defender.hp >= defender.maxHp
				&& !moldBreaker(attacker)) {
			damage *= 0.5;
		}
		damage *= attacker.ours ? 0.85 : 1.0;
		if (defender.has("substitute") && !move.sound() && !attacker.ability().equals("infiltrator")) {
			damage = Math.min(damage, defender.maxHp / 4);
		}
		return Math.floor(damage);
	}

	/** Reborn's {@code pbRoughAccuracy} (no field). */
	private double roughAccuracy(Mv move, B attacker, B defender) {
		if (attacker.ability().equals("noguard") || defender.ability().equals("noguard")) {
			return 100;
		}
		if (move.accuracy == 0) {
			return 100;
		}
		if (move.id.equals("blizzard") && (weather.equals("hail") || weather.equals("snow"))
				|| (move.id.equals("thunder") || move.id.equals("hurricane")) && weather.equals("raindance")
				|| move.id.equals("toxic") && attacker.hasType("poison")) {
			return 100;
		}
		double base = move.accuracy;
		if ((move.id.equals("thunder") || move.id.equals("hurricane")) && weather.equals("sunnyday")) {
			base = 50;
		}
		if (Set.of("fissure", "sheercold", "guillotine", "horndrill").contains(move.id)) {
			if (defender.ability().equals("sturdy") || defender.mon.level > attacker.mon.level) {
				return 0;
			}
			return base + attacker.mon.level - defender.mon.level;
		}
		int accStage = defender.ability().equals("unaware") && !moldBreaker(attacker) ? 0 : attacker.stages[5];
		int evaStage = attacker.ability().equals("unaware") || attacker.ability().equals("keeneye") ? 0 : defender.stages[6];
		int stage = Math.max(-6, Math.min(6, accStage - evaStage));
		double accuracy = stage >= 0 ? (stage + 3) * 100.0 / 3 : 300.0 / (3 - stage);
		if (attacker.ability().equals("compoundeyes")) {
			accuracy *= 1.3;
		}
		if (attacker.ability().equals("victorystar")) {
			accuracy *= 1.1;
		}
		if (attacker.ability().equals("hustle") && move.physical && !move.status) {
			accuracy *= 0.8;
		}
		if (defender.ability().equals("tangledfeet") && defender.has("confusion")) {
			accuracy *= 0.5;
		}
		if (defender.ability().equals("sandveil") && weather.equals("sandstorm")
				|| defender.ability().equals("snowcloak") && (weather.equals("hail") || weather.equals("snow"))) {
			accuracy *= 0.8;
		}
		if (attacker.item().equals("widelens")) {
			accuracy *= 1.1;
		}
		if (attacker.item().equals("zoomlens") && attacker.speed() < defender.speed()) {
			accuracy *= 1.2;
		}
		if (defender.item().equals("brightpowder") || defender.item().equals("laxincense")) {
			accuracy *= 0.9;
		}
		accuracy *= base / 100;
		return Math.min(100, accuracy);
	}

	private int priority(Mv move, B user) {
		int priority = move.priority;
		if (user.ability().equals("prankster") && move.status) {
			priority++;
		}
		if (user.ability().equals("galewings") && move.type.getName().equalsIgnoreCase("flying") && user.hp >= user.maxHp) {
			priority++;
		}
		if (user.ability().equals("triage") && HEALING.contains(move.id)) {
			priority += 3;
		}
		if (move.id.equals("grassyglide") && terrain.equals("grassyterrain")) {
			priority++;
		}
		return priority;
	}

	private boolean isPriority(Mv move, B user) {
		return priority(move, user) > 0;
	}

	/** Reborn's {@code pbAIfaster?}: priority, then Speed (Trick Room reverses it); a tie counts as slower. */
	private boolean faster(Mv attackerMove, Mv opponentMove, B attacker, B opponent) {
		if (opponent == null || opponent.hp <= 0) {
			return true;
		}
		if (attacker == null || attacker.hp <= 0) {
			return false;
		}
		int[] priority = {attackerMove == null ? 0 : priority(attackerMove, attacker), opponentMove == null ? 0 : priority(opponentMove, opponent)};
		int[] bracket = {bracket(attacker), bracket(opponent)};
		double[] speed = {trickRoom ? -attacker.speed() : attacker.speed(), trickRoom ? -opponent.speed() : opponent.speed()};
		if (priority[0] != priority[1]) {
			return priority[0] > priority[1];
		}
		if (bracket[0] != bracket[1]) {
			return bracket[0] > bracket[1];
		}
		return speed[0] > speed[1];
	}

	private static int bracket(B battler) {
		if (battler.ability().equals("stall") || battler.item().equals("laggingtail") || battler.item().equals("fullincense")) {
			return battler.ability().equals("stall") ? -1 : -2;
		}
		if (battler.item().equals("custapberry") && battler.hp <= battler.maxHp / 4) {
			return 1;
		}
		return 0;
	}

	/** Reborn's {@code checkAIMovePlusDamage}: the opponent's moves on the target, with the fake STAB moves. */
	private List<Object[]> movesAndDamage(B user, B target) {
		List<Object[]> out = new ArrayList<>();
		if (user == null || user.hp <= 0) {
			return out;
		}
		List<Mv> seen = memoryOf(user);
		if (!user.ours && seen.size() < Math.max(1, user.moves.size())) {
			for (String type : user.mon.types) {
				boolean shown = false;
				for (Mv move : seen) {
					shown |= !move.status && move.type.getName().equalsIgnoreCase(type);
				}
				ElementalType elemental = ElementalTypes.get(type);
				if (!shown && elemental != null) {
					Mv fake = Mv.fake(user, elemental);
					out.add(new Object[] {fake, roughDamage(fake, user, target)});
				}
			}
		}
		for (Mv move : seen) {
			out.add(new Object[] {move, roughDamage(move, user, target)});
		}
		return out;
	}

	private Object[] bestMove(B user, B target) {
		Object[] best = null;
		for (Object[] entry : movesAndDamage(user, target)) {
			if (best == null || (double) entry[1] > (double) best[1]) {
				best = entry;
			}
		}
		return best;
	}

	/** Reborn's {@code checkAIdamage}: the most the attacker expects to take from the opponent. */
	private double expectedDamage(B attacker, B opponent) {
		Object[] best = bestMove(opponent, attacker);
		return best == null ? 0 : (double) best[1];
	}

	private Mv expectedMove(B opponent, B attacker) {
		Object[] best = bestMove(opponent, attacker);
		return best == null ? null : (Mv) best[0];
	}

	private boolean memoryHas(B battler, Set<String> ids) {
		for (Mv move : memoryOf(battler)) {
			if (ids.contains(move.id)) {
				return true;
			}
		}
		return false;
	}

	private boolean memoryHealing(B battler) {
		return memoryHas(battler, HEALING);
	}

	private boolean memoryPriority(B battler) {
		for (Mv move : memoryOf(battler)) {
			if (isPriority(move, battler)) {
				return true;
			}
		}
		return false;
	}

	/** Reborn's {@code notOHKO?}: full HP with a Focus Sash or Sturdy. */
	private boolean notOHKO(B attacker, B opponent) {
		if (attacker.hp < attacker.maxHp) {
			return false;
		}
		if (attacker.ability().equals("skilllink") || attacker.ability().equals("parentalbond")) {
			return false;
		}
		Mv best = expectedMove(opponent, attacker);
		if (best != null && best.multiHit() && expectedDamage(attacker, opponent) >= attacker.hp) {
			return false;
		}
		return attacker.item().equals("focussash") || attacker.ability().equals("sturdy") && !moldBreaker(opponent);
	}

	/** Reborn's {@code canKillBeforeOpponentKills?}. */
	private boolean canKillFirst(B attacker, B opponent) {
		List<Mv> attackerKills = new ArrayList<>();
		for (Object[] entry : movesAndDamage(attacker, opponent)) {
			double damage = (double) entry[1];
			if (damage > 0 && notOHKO(opponent, attacker)) {
				damage--;
			}
			if (damage >= opponent.hp) {
				attackerKills.add((Mv) entry[0]);
			}
		}
		List<Mv> opponentKills = new ArrayList<>();
		for (Object[] entry : movesAndDamage(opponent, attacker)) {
			double damage = (double) entry[1];
			if (damage > 0 && notOHKO(attacker, opponent)) {
				damage--;
			}
			if (damage >= attacker.hp) {
				opponentKills.add((Mv) entry[0]);
			}
		}
		if (opponentKills.isEmpty()) {
			return true;
		}
		if (attackerKills.isEmpty()) {
			return false;
		}
		for (Mv mine : attackerKills) {
			boolean first = true;
			for (Mv theirs : opponentKills) {
				first &= faster(mine, theirs, attacker, opponent);
			}
			if (first) {
				return true;
			}
		}
		return false;
	}

	/** Reborn's {@code hpGainPerTurn}: passive healing and damage per turn, 1 = none. */
	private double hpGain(B b) {
		double healing = 1;
		if (!b.ability().equals("magicguard")) {
			if (weather.equals("sunnyday") && (b.ability().equals("solarpower") || b.ability().equals("dryskin"))) {
				healing -= 0.125;
			}
			if (weather.equals("sandstorm") && !(b.hasType("ground") || b.hasType("rock") || b.hasType("steel")
					|| Set.of("sandforce", "sandrush", "sandveil", "overcoat").contains(b.ability()))) {
				healing -= 0.0625;
			}
			if (weather.equals("hail") && !(b.hasType("ice") || Set.of("icebody", "snowcloak", "slushrush", "overcoat").contains(b.ability()))) {
				healing -= 0.0625;
			}
			if (b.status().equals("psn") && !b.ability().equals("poisonheal")) {
				healing -= 0.125;
			}
			if (b.status().equals("tox") && !b.ability().equals("poisonheal")) {
				healing -= Math.min(15, toxicTurns(b)) / 16.0;
			}
			if (b.status().equals("brn")) {
				healing -= 0.0625;
			}
			if (b.has("leechseed") && !b.ability().equals("liquidooze")) {
				healing -= 0.125;
			}
			if (b.has("curse")) {
				healing -= 0.3;
			}
			if (b.item().equals("stickybarb")) {
				healing -= 0.125;
			}
			if (b.item().equals("blacksludge") && !b.hasType("poison")) {
				healing -= 0.125;
			}
		}
		healing = Math.max(0, healing);
		if (b.has("healblock")) {
			return healing;
		}
		if (b.has("aquaring")) {
			healing += b.item().equals("bigroot") ? 0.0813 : 0.0625;
		}
		if (b.has("ingrain")) {
			healing += b.item().equals("bigroot") ? 0.0813 : 0.0625;
		}
		if (b.ability().equals("dryskin") && weather.equals("raindance")) {
			healing += 0.125;
		}
		if (b.item().equals("leftovers") || b.item().equals("blacksludge") && b.hasType("poison")) {
			healing += 0.0625;
		}
		if (b.ability().equals("raindish") && weather.equals("raindance")) {
			healing += 0.0625;
		}
		if (b.ability().equals("icebody") && (weather.equals("hail") || weather.equals("snow"))) {
			healing += 0.0625;
		}
		if (b.ability().equals("poisonheal") && (b.status().equals("psn") || b.status().equals("tox"))) {
			healing += 0.125;
		}
		if (terrain.equals("grassyterrain") && b.grounded()) {
			healing += 0.0625;
		}
		return healing;
	}

	private int toxicTurns(B b) {
		Integer since = toxicSince.get(b.battler.getUuid());
		return since == null ? 1 : Math.max(1, turn - since);
	}

	/** Reborn's {@code totalHazardDamage}, in % of max HP, for a Pokémon coming in on its side. */
	private double hazardDamage(B b) {
		Map<String, Integer> side = conditions(b.ours);
		if (b.ability().equals("magicguard") || b.item().equals("heavydutyboots")) {
			return 0;
		}
		double percent = 0;
		int spikes = Math.min(3, side.getOrDefault("spikes", 0));
		if (spikes > 0 && b.grounded()) {
			percent += Math.floor(100.0 / new int[] {8, 8, 6, 4}[spikes]);
		}
		if (side.containsKey("stealthrock")) {
			percent += 12.5 * ExpertBattleAI.effectiveness(ElementalTypes.get("rock"), b.mon, null);
		}
		return percent;
	}

	// ---------------------------------------------------------------- choosing

	private B current;

	private ShowdownActionResponse decide(ActiveBattlePokemon active, BattleSide side, ShowdownMoveset moveset, boolean forceSwitch) {
		List<BattlePokemon> party = active.getActor().getPokemonList();
		Set<UUID> ours = new HashSet<>();
		party.forEach(pokemon -> ours.add(pokemon.getUuid()));
		readLog(active.getBattle(), ours);
		BattlePokemon foePokemon = side.getOppositeSide().getActivePokemon().get(0).getBattlePokemon();
		BattlePokemon mine = active.getBattlePokemon();
		if (foePokemon == null) {
			return null;
		}
		B opponent = battler(foePokemon, false, -1, true);
		if (forceSwitch || mine == null || mine.getHealth() <= 0) {
			current = mine == null ? null : battler(mine, true, party.indexOf(mine), true);
			int[] scores = switchInScores(party, current, opponent, false);
			int best = bestIndex(scores);
			if (best < 0) {
				return null;
			}
			if (debug) {
				say("§6[Reborn] §fRemplaçant après KO : §a" + name(party.get(best)) + " §7(" + scores[best] + ") · " + switchInLine(party, scores));
			}
			return new SwitchActionResponse(party.get(best).getUuid());
		}
		B attacker = battler(mine, true, party.indexOf(mine), true);
		current = attacker;
		// The usable moves, in moveset order (Cobblemon's slots match the Pokémon's moves).
		List<Mv> usable = new ArrayList<>();
		for (int index = 0; index < moveset.getMoves().size(); index++) {
			InBattleMove slot = moveset.getMoves().get(index);
			MoveTemplate template = com.cobblemon.mod.common.api.moves.Moves.getByName(slot.getId());
			if (template == null) {
				continue;
			}
			Mv mv = Mv.of(template);
			mv.slot = index;
			mv.pp = slot.getPp();
			mv.disabled = !slot.canBeUsed();
			usable.add(mv);
		}
		// Percent array (Reborn's roughdamagearray), then the scores.
		double[] percent = new double[usable.size() + 1];
		for (int m = 0; m < usable.size(); m++) {
			Mv move = usable.get(m);
			percent[m] = move.status ? STATUS_DAMAGE.getOrDefault(move.id, 0)
					: Math.min(110, roughDamage(move, attacker, opponent) * 100 / Math.max(1, opponent.hp));
		}
		int zSlot = -1;
		double zPower = 0;
		List<InBattleGimmickMove> zMoves = moveset.getCanZMove();
		if (!zUsed && zMoves != null) {
			double bestZ = 0;
			for (int m = 0; m < usable.size(); m++) {
				Mv move = usable.get(m);
				if (move.slot < zMoves.size() && zMoves.get(move.slot) != null && !move.status && !move.disabled) {
					double power = ExpertBattleAI.zPower(move.power);
					double damage = roughDamage(move, attacker, opponent, power);
					if (damage > bestZ) {
						bestZ = damage;
						zSlot = m;
						zPower = power;
					}
				}
			}
			if (zSlot >= 0) {
				percent[usable.size()] = Math.min(110, bestZ * 100 / Math.max(1, opponent.hp));
			}
		}
		double[] scores = new double[usable.size() + 1];
		for (int m = 0; m < usable.size(); m++) {
			scores[m] = usable.get(m).disabled ? -1 : moveScore(usable.get(m), m, percent, attacker, opponent, usable, 0);
		}
		scores[usable.size()] = -1;
		if (zSlot >= 0) {
			scores[usable.size()] = moveScore(usable.get(zSlot), usable.size(), percent, attacker, opponent, usable, zPower);
		}
		// Switching.
		double shouldSwitch = shouldSwitch(attacker, opponent, party, usable, scores, moveset.getTrapped());
		int[] switchScores = null;
		int switchTo = -1;
		double maxMove = 0;
		for (int m = 0; m < usable.size(); m++) {
			maxMove = Math.max(maxMove, scores[m]);
		}
		boolean zAllowed = zSlot >= 0;
		for (int m = 0; m < usable.size(); m++) {
			zAllowed &= scores[m] < 100;
		}
		if (zAllowed) {
			maxMove = Math.max(maxMove, scores[usable.size()]);
		}
		if (shouldSwitch > 0) {
			switchScores = switchInScores(party, attacker, opponent, true);
			int best = bestIndex(switchScores);
			if (shouldSwitch > maxMove && best >= 0 && switchScores[best] > 100 && shouldHardSwitch(attacker, opponent, usable, party.get(best))) {
				switchTo = best;
			}
		}
		if (debug) {
			report(attacker, opponent, usable, percent, scores, zAllowed ? zSlot : -1, shouldSwitch, maxMove, switchScores, party);
		}
		if (switchTo >= 0) {
			if (debug) {
				say("§6[Reborn] §fChoisit : §a→ " + name(party.get(switchTo)));
			}
			return new SwitchActionResponse(party.get(switchTo).getUuid());
		}
		// Minmax (best skill: anything under 1/1.5 of the best drops to 5), then random among the moves within 95%.
		List<Integer> choosable = new ArrayList<>();
		for (int m = 0; m < usable.size(); m++) {
			if (scores[m] >= 0 && !usable.get(m).disabled) {
				choosable.add(m);
			}
		}
		if (zAllowed && scores[usable.size()] >= 0) {
			choosable.add(usable.size());
		}
		if (choosable.isEmpty()) {
			return new RandomBattleAI().choose(active, active.getBattle(), side, moveset, false);
		}
		double max = 0;
		for (int m : choosable) {
			max = Math.max(max, scores[m]);
		}
		double[] finals = scores.clone();
		for (int m : choosable) {
			finals[m] = finals[m] > 5 && finals[m] * 1.5 < max ? 5 : finals[m];
		}
		List<Integer> preferred = new ArrayList<>();
		for (int m : choosable) {
			if (finals[m] >= max * 0.95) {
				preferred.add(m);
				if (finals[m] == max) {
					preferred.add(m);
				}
			}
		}
		int chosen = preferred.get(random.nextInt(preferred.size()));
		boolean z = chosen == usable.size();
		Mv move = z ? usable.get(zSlot) : usable.get(chosen);
		String gimmick = z ? ShowdownMoveset.Gimmick.Z_POWER.getId()
				: moveset.getCanMegaEvo() ? ShowdownMoveset.Gimmick.MEGA_EVOLUTION.getId() : null; // Reborn always Mega Evolves
		if (z) {
			zUsed = true;
		}
		if (debug) {
			say("§6[Reborn] §fChoisit : §a" + move.name() + (z ? " (Z)" : "") + " §7(" + (int) scores[chosen] + ")");
		}
		return new MoveActionResponse(moveset.getMoves().get(move.slot).getId(), null, gimmick);
	}

	private static int bestIndex(int[] scores) {
		int best = -1;
		for (int i = 0; i < scores.length; i++) {
			if (scores[i] > -1000000 && (best < 0 || scores[i] > scores[best])) {
				best = i;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- move score

	private static boolean hasGreatMoves(double[] initial, int index) {
		for (int i = 0; i < initial.length; i++) {
			if (i != index && initial[i] >= 100) {
				return true;
			}
		}
		return false;
	}

	/** Reborn's {@code getMoveScore}: the general rules, then the move's own (its miniscore). */
	private double moveScore(Mv move, int index, double[] initial, B attacker, B opponent, List<Mv> usable, double zPower) {
		double score = initial[index];
		boolean damaging = !move.status;
		String type = move.type.getName().toLowerCase(Locale.ROOT);
		// Immunities.
		if (damaging) {
			double mod = typeMod(move, attacker, opponent);
			if (mod <= 0) {
				return 0;
			}
		}
		if (!moldBreaker(attacker) && move.status && targetsOpponent(move)) {
			String ability = opponent.ability();
			if (type.equals("fire") && TYPE_ABSORBS_FIRE.contains(ability) || type.equals("grass") && TYPE_ABSORBS_GRASS.contains(ability)
					|| type.equals("water") && TYPE_ABSORBS_WATER.contains(ability)
					|| type.equals("electric") && TYPE_ABSORBS_ELECTRIC.contains(ability)) {
				return -1;
			}
			if (ability.equals("magicbounce") || opponent.has("magiccoat")) {
				return -1;
			}
			if (ability.equals("wonderguard")) {
				// Reborn's "wondercheck" only exists for damaging moves; a status move on Wonder Guard goes on.
			}
		}
		if (damaging && opponent.ability().equals("wonderguard") && typeMod(move, attacker, opponent) <= 1 && !moldBreaker(attacker)) {
			return 0;
		}
		// Priority.
		boolean aiFaster = faster(null, null, attacker, opponent);
		if (isPriority(move, attacker)) {
			if (!opponent.status().equals("slp") && !opponent.status().equals("frz") && !opponent.has("mustrecharge")) {
				if (score > 100) {
					score *= aiFaster ? 1.3 : 2;
				} else if (attacker.ability().equals("stancechange") && !aiFaster && attacker.mon.name.toLowerCase(Locale.ROOT).contains("aegislash")) {
					score *= 0.7;
				}
				double moveDamage = -1;
				boolean opponentPriority = false;
				double priorityDamage = -1;
				if (!aiFaster || memoryPriority(opponent)) {
					for (Object[] entry : movesAndDamage(opponent, attacker)) {
						Mv theirs = (Mv) entry[0];
						double damage = (double) entry[1];
						moveDamage = Math.max(moveDamage, damage);
						if (!theirs.status && isPriority(theirs, opponent)) {
							opponentPriority = true;
							priorityDamage = Math.max(priorityDamage, damage);
						}
					}
				}
				if (notOHKO(attacker, opponent)) {
					moveDamage = attacker.hp - 1;
				}
				double boost = PROTECT.contains(move.id) ? 30 : 150;
				if (!aiFaster && moveDamage > attacker.hp && score > 1) {
					score += boost;
				}
				if (opponentPriority) {
					score *= 1.1;
					if (priorityDamage > attacker.hp) {
						score *= aiFaster ? 3 : 0.5;
					}
				}
			}
			if (!aiFaster && charging.containsKey(opponent.battler.getUuid())) {
				score = 0;
			}
			if (terrain.equals("psychicterrain") && opponent.grounded() && targetsOpponent(move)) {
				score = 0;
			}
			if (Set.of("dazzling", "queenlymajesty", "armortail").contains(opponent.ability())) {
				score = 0;
			}
			if (memoryHas(opponent, Set.of("quickguard"))) {
				score *= 0.2;
			}
		} else if (move.priority < 0 && aiFaster) {
			score *= 0.9;
			int killers = 0;
			for (double value : initial) {
				killers += value >= 100 ? 1 : 0;
			}
			if (initial[index] >= 100 && killers > 1) {
				score *= 0.6;
			}
			if (damaging && charging.containsKey(opponent.battler.getUuid())) {
				score *= 2;
			}
		}
		// Sound, Dancer.
		if (move.sound()) {
			if (opponent.ability().equals("soundproof") && !moldBreaker(attacker) || attacker.has("throatchop")) {
				return 0;
			}
			if (memoryHas(opponent, Set.of("throatchop"))) {
				score *= 0.6;
			}
		}
		if (opponent.ability().equals("dancer") && DANCE.contains(move.id)) {
			score *= 0.5;
		}
		// Destiny Bond, Powder.
		if (damaging) {
			if (opponent.has("destinybond")) {
				score *= 0.2;
			} else if (!faster(move, null, attacker, opponent) && memoryHas(opponent, Set.of("destinybond"))) {
				score *= 0.7;
			}
		}
		if (memoryHas(opponent, Set.of("powder")) && type.equals("fire")) {
			score *= 0.2;
		}
		// If the opponent would heal before being hit, check the damage against its healed HP.
		if (memoryHealing(opponent) && !faster(move, null, attacker, opponent) && damaging) {
			double healed = Math.min((opponent.maxHp + 1) / 2 + opponent.hp, opponent.maxHp);
			score *= Math.min(roughDamage(move, attacker, opponent, zPower) / healed, 1.1);
		}
		// High critical-hit moves.
		if (zPower == 0 && move.highCrit() && !Set.of("shellarmor", "battlearmor").contains(opponent.ability()) && !attacker.has("laserfocus")) {
			int boosters = 0;
			if (move.physical) {
				boosters += Math.max(0, opponent.stages[1]) - Math.min(0, attacker.stages[0]);
			} else {
				boosters += Math.max(0, opponent.stages[3]) - Math.min(0, attacker.stages[2]);
			}
			if (hasGreatMoves(initial, index)) {
				score *= Math.pow(1.05, boosters);
			}
		}
		if (hasGreatMoves(initial, index) && attacker.item().equals("powerherb")
				&& Set.of("solarbeam", "solarblade", "meteorbeam", "skyattack", "geomancy", "skullbash", "freezeshock", "iceburn",
						"razorwind", "electroshot").contains(move.id)) {
			score *= 0.85;
		}
		// Contact.
		if (zPower == 0 && move.contact() && !attacker.item().equals("protectivepads") && !attacker.ability().equals("longreach")) {
			double contact = 1;
			boolean shield = memoryHas(opponent, Set.of("spikyshield", "kingsshield", "banefulbunker", "obstruct", "silktrap"));
			if (opponent.item().equals("rockyhelmet") || shield) {
				contact *= attacker.hp < 0.2 * attacker.maxHp ? 0.5 : 0.85;
			}
			switch (opponent.ability()) {
				case "effectspore", "perishbody" -> contact *= 0.75;
				case "flamebody" -> contact *= attacker.status().isEmpty() && !attacker.hasType("fire") ? 0.75 : 1;
				case "static" -> contact *= attacker.status().isEmpty() && !attacker.hasType("electric") ? 0.75 : 1;
				case "poisonpoint" -> contact *= attacker.status().isEmpty() && !attacker.hasType("poison") && !attacker.hasType("steel") ? 0.75 : 1;
				case "cutecharm" -> contact *= initial[index] < 110 ? 0.8 : 1;
				case "roughskin", "ironbarbs" -> contact *= attacker.hp < 0.2 * attacker.maxHp ? 0.5 : 0.85;
				case "gooey", "tanglinghair", "cottondown" -> {
					if (attacker.stages[4] > -6) {
						contact *= 0.9;
						if (aiFaster) {
							contact *= 0.8;
						}
					}
				}
				case "mummy", "wanderingspirit", "lingeringaroma" -> {
					double disrupt = abilityDisrupt(opponent, attacker);
					contact *= disrupt < 2 ? 2 - disrupt : 0;
				}
				default -> {
				}
			}
			if (opponent.mon.name.toLowerCase(Locale.ROOT).contains("aegislash") && !memoryHas(opponent, Set.of("kingsshield")) && move.physical) {
				contact *= 0.8;
			}
			String opponentLast = lastMove.getOrDefault(opponent.battler.getUuid(), "");
			boolean protectedLast = PROTECT.contains(opponentLast) || Set.of("quickguard", "wideguard", "endure").contains(opponentLast);
			if (memoryHas(opponent, Set.of("kingsshield")) && !protectedLast && move.physical) {
				contact *= 0.5;
			}
			if (memoryHas(opponent, Set.of("banefulbunker")) && !protectedLast && attacker.status().isEmpty()) {
				contact *= 0.7;
			}
			if (memoryHas(opponent, Set.of("spikyshield")) && !protectedLast && attacker.hp < 0.3 * attacker.maxHp) {
				contact *= 0.6;
			}
			if (attacker.ability().equals("poisontouch") && opponent.status().isEmpty()) {
				contact *= 1.1;
			}
			if (attacker.ability().equals("pickpocket") && !opponent.item().isEmpty() && attacker.item().isEmpty()) {
				contact *= 1.1;
			}
			if (move.multiHit()) {
				contact *= contact;
			}
			score *= contact;
		}
		// A move that kills is better than a status move.
		if (move.status && hasGreatMoves(initial, index)) {
			double expected = expectedDamage(attacker, opponent);
			score *= expected > 0 && expected < attacker.hp * 0.3 ? 0.6 : 0.2;
		}
		if (POWDER.contains(move.id) && (opponent.hasType("grass") || opponent.ability().equals("overcoat") || opponent.item().equals("safetygoggles"))) {
			return 0;
		}
		// Its last Pokémon: damage over status (the opponent's last too, at Reborn's high skill).
		if (reserves(attacker) == 0 && reserves(opponent) == 0) {
			if (move.status) {
				score *= 0.9;
			} else if (opponent.hp <= opponent.maxHp / 2) {
				score *= 1.1;
			}
		}
		// An opponent in the semi-invulnerable turn of Fly, Dig…
		String charge = charging.get(opponent.battler.getUuid());
		if (charge != null && TWO_TURN.contains(charge) && (move.accuracy > 0 || move.id.equals("whirlwind"))
				&& faster(move, null, attacker, opponent) && !attacker.ability().equals("noguard") && !opponent.ability().equals("noguard")) {
			boolean hits = switch (charge) {
				case "fly", "bounce", "skydrop" -> Set.of("thunder", "hurricane", "skyuppercut", "smackdown", "thousandarrows", "twister",
						"gust", "whirlwind").contains(move.id);
				case "dig" -> Set.of("earthquake", "magnitude", "fissure").contains(move.id);
				case "dive" -> Set.of("surf", "whirlpool").contains(move.id);
				default -> false;
			};
			if (!hits) {
				return 0;
			}
		}
		// A seen opposing Pokémon immune to it.
		boolean knownImmune = false;
		boolean knownWall = false;
		for (BattlePokemon pokemon : opponent.battler.getActor().getPokemonList()) {
			if (pokemon == opponent.battler || pokemon.getHealth() <= 0 || !memory.containsKey(pokemon.getUuid()) && !entered.containsKey(pokemon.getUuid())) {
				continue;
			}
			double effect = ExpertBattleAI.effectiveness(move.type, ExpertBattleAI.belief(pokemon), null);
			knownWall |= effect <= 0.25;
			knownImmune |= effect == 0;
		}
		if (damaging && knownWall) {
			score *= 0.9;
		}
		// Choice items.
		if (Set.of("choiceband", "choicespecs", "choicescarf").contains(attacker.item()) || attacker.ability().equals("gorillatactics")) {
			if (move.status && !Set.of("trick", "switcheroo", "partingshot", "sleeptalk").contains(move.id)) {
				score *= 0.1;
			} else if (knownImmune) {
				score *= 0.8;
			}
			if (move.accuracy > 0) {
				score *= move.accuracy / 100;
			}
			if (move.pp <= 5) {
				score *= 0.9;
			}
			if ((move.id.equals("fakeout") || move.id.equals("firstimpression")) && (reserves(opponent) > 0 || initial[index] < 100)) {
				score *= 0.1;
			}
		}
		// Frozen.
		if (attacker.status().equals("frz")) {
			if (UNFREEZE.contains(move.id)) {
				score += 30;
			} else {
				for (Mv other : usable) {
					if (UNFREEZE.contains(other.id)) {
						return 0;
					}
				}
			}
		}
		if (opponent.status().equals("frz") && type.equals("fire")) {
			score *= 0.1;
		}
		if (attacker.ability().equals("prankster") && opponent.hasType("dark") && move.status && move.priority > -1 && targetsOpponent(move)) {
			return 0;
		}
		// Accuracy.
		double accuracy = roughAccuracy(move, attacker, opponent);
		score *= (accuracy + 100) / 200;
		// The move's own rules (Reborn's function codes; a Z-move keeps the general rules only).
		double miniscore = 1;
		if (zPower == 0) {
			a = attacker;
			o = opponent;
			m = move;
			this.initial = initial;
			this.index = index;
			double[] own = ownRules(score);
			miniscore = own[0];
			score = own[1];
		}
		if (RECOIL.containsKey(move.id) && zPower == 0) {
			miniscore *= recoil(move, index, initial, attacker, opponent);
		}
		score *= miniscore;
		return Math.max(0, Math.floor(score));
	}

	private double recoil(Mv move, int index, double[] initial, B attacker, B opponent) {
		if (attacker.ability().equals("rockhead") || attacker.ability().equals("magicguard")) {
			return 1;
		}
		double miniscore = 0.9;
		if (notOHKO(attacker, opponent)) {
			miniscore *= 0.7;
		}
		if (attacker.hp > 0.1 * attacker.maxHp && attacker.hp < 0.4 * attacker.maxHp) {
			miniscore *= 0.8;
		}
		if (initial[index] * RECOIL.get(move.id) > attacker.hp && (opponent.status().equals("slp") || opponent.status().equals("frz"))) {
			miniscore *= 0.4;
		}
		return miniscore;
	}

	private static boolean targetsOpponent(Mv move) {
		if (move.template == null) {
			return true;
		}
		// Reborn's SingleNonUser, RandomOpposing, AllOpposing, OpposingSide, SingleOpposing.
		return Set.of("normal", "any", "randomNormal", "allAdjacentFoes", "adjacentFoe", "foeSide", "allAdjacent")
				.contains(move.template.getTarget().name());
	}

	private static int reserves(B b) {
		int count = 0;
		for (BattlePokemon pokemon : b.battler.getActor().getPokemonList()) {
			if (pokemon != b.battler && pokemon.getHealth() > 0) {
				count++;
			}
		}
		return count;
	}

	/** Reborn's {@code getAbilityDisruptScore} (the parts that matter outside its fields), ×0.01 at the end. */
	private double abilityDisrupt(B attacker, B opponent) {
		double score = 100;
		String ability = opponent.ability();
		List<Mv> moves = attacker.ours ? attacker.moves : memoryOf(attacker);
		java.util.function.Predicate<String> only = type -> !moves.isEmpty()
				&& moves.stream().filter(m -> !m.status).allMatch(m -> m.type.getName().equalsIgnoreCase(type));
		java.util.function.Predicate<String> weak = type -> ExpertBattleAI.effectiveness(ElementalTypes.get(type), opponent.mon, null) > 1;
		switch (ability) {
			case "battlebond", "comatose", "disguise", "multitype", "powerconstruct", "rkssystem", "schooling", "shieldsdown",
					"stancechange", "zenmode" -> score = 0;
			case "speedboost" -> score *= opponent.stages[4] < 2 ? 1.1 * 1.3 : 1.1;
			case "lightningrod", "motordrive", "voltabsorb" -> score *= (only.test("electric") ? 3 : 1) * (weak.test("electric") ? 2 : 1);
			case "dryskin", "waterabsorb", "stormdrain" -> score *= (only.test("water") ? 3 : 1) * (weak.test("water") ? 2 : 1);
			case "sapsipper" -> score *= (only.test("grass") ? 3 : 1) * (weak.test("grass") ? 2 : 1);
			case "flashfire" -> score *= (only.test("fire") ? 3 : 1) * (weak.test("fire") ? 2 : 1);
			case "levitate" -> score *= (only.test("ground") ? 3 : 1) * (weak.test("ground") ? 2 : 1);
			case "shadowtag" -> score *= attacker.hasType("ghost") ? 1 : 1.5;
			case "arenatrap" -> score *= attacker.grounded() ? 1 : 1.5;
			case "serenegrace", "technician", "adaptability", "hydration" -> score *= 1.3;
			case "purepower", "hugepower" -> score *= 2;
			case "truant" -> score *= 0.1;
			case "guts", "quickfeet", "marvelscale" -> score *= opponent.status().isEmpty() ? 1 : 1.5;
			case "skilllink" -> score *= 1.5;
			case "magicguard" -> score *= 1.4;
			case "unaware" -> score *= 1.7;
			case "slowstart" -> score *= 0.3;
			case "sheerforce", "toughclaws" -> score *= 1.2;
			case "contrary" -> score *= 1.4;
			case "defeatist", "stall" -> score *= 0.5;
			case "multiscale" -> score *= opponent.hp >= opponent.maxHp ? 1.5 : 1;
			case "moody" -> score *= 1.8;
			case "prankster" -> score *= faster(null, null, attacker, opponent) ? 1.5 : 1;
			case "furcoat" -> score *= attacker.attack() > attacker.spatk() ? 1.5 : 1;
			case "parentalbond", "protean", "libero" -> score *= 3;
			case "fluffy" -> score *= 1.5;
			case "regenerator", "intimidate" -> score *= 1.2;
			default -> {
			}
		}
		return score * 0.01;
	}

	// ---------------------------------------------------------------- the move's own rules (Reborn's function codes)

	/** Reborn's move table: function code, effect chance and flags per move ({@code tools}: movetext.rb). */
	private static Map<String, com.google.gson.JsonObject> rebornMoves;

	private static synchronized Map<String, com.google.gson.JsonObject> rebornMoves() {
		if (rebornMoves == null) {
			rebornMoves = new HashMap<>();
			try (java.io.InputStream in = RebornBattleAI.class.getResourceAsStream("/assets/phantasmon_events/ai/reborn_moves.json")) {
				if (in != null) {
					com.google.gson.JsonObject all = com.google.gson.JsonParser.parseReader(
							new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
					all.entrySet().forEach(entry -> rebornMoves.put(entry.getKey(), entry.getValue().getAsJsonObject()));
				}
			} catch (Exception ex) {
				LOG.warn("Reborn move table unreadable: the Reborn AI scores moves without their own rules", ex);
			}
		}
		return rebornMoves;
	}

	private static int function(String id) {
		com.google.gson.JsonObject entry = rebornMoves().get(id);
		return entry == null || !entry.has("f") ? -1 : entry.get("f").getAsInt();
	}

	/** The secondary effect's chance (0 when the move has none). */
	private static int effectChance(String id) {
		com.google.gson.JsonObject entry = rebornMoves().get(id);
		return entry == null || !entry.has("e") ? 0 : entry.get("e").getAsInt();
	}

	private static final Set<String> SETUP = Set.of("swordsdance", "dragondance", "calmmind", "workup", "nastyplot", "victorydance",
			"amnesia", "irondefense", "cottonguard", "tailglow", "bellydrum", "bulkup", "coil", "curse", "growth", "honeclaws",
			"quiverdance", "shellsmash", "shiftgear", "noretreat", "stuffcheeks", "takeheart", "acidarmor", "geomancy", "gearup");
	private static final Set<String> CONTRARY_BAIT = Set.of("superpower", "overheat", "dracometeor", "leafstorm", "fleurcannon", "psychoboost");
	private static final Set<String> PROTECT_IGNORING = Set.of("feint", "hyperspacehole", "hyperspacefury", "shadowforce", "phantomforce");
	private static final Set<String> SCREEN_BREAKERS = Set.of("defog", "brickbreak", "psychicfangs");

	/** The move being scored, as Reborn's {@code @move}, {@code @initial_scores}, {@code @score_index}. */
	private Mv m;
	private B a;
	private B o;
	private double[] initial;
	private int index;

	private boolean damaging() {
		return !m.status;
	}

	/** A damaging move's neutral answer when its added effect cannot happen, 0 for a status move. */
	private double none() {
		return damaging() ? 1 : 0;
	}

	private boolean greatMoves() {
		return hasGreatMoves(initial, index);
	}

	private boolean badMoves(double threshold) {
		for (int i = 0; i < initial.length; i++) {
			if (i != index && initial[i] > threshold) {
				return false;
			}
		}
		return true;
	}

	private double serene(double miniscore) {
		if (!damaging()) {
			return miniscore;
		}
		int effect = effectChance(m.id);
		if (effect != 100) {
			double chance = Math.min(100, effect * (a.ability().equals("serenegrace") ? 2 : 1));
			miniscore = (miniscore - 1) * chance / 100 + 1;
		}
		return miniscore;
	}

	private double reduceWhenKills(double miniscore) {
		return initial[index] < 100 ? miniscore : Math.sqrt(miniscore);
	}

	private boolean secondaryNegated() {
		return damaging() && (o.ability().equals("shielddust") || a.ability().equals("sheerforce"));
	}

	private boolean hydration(B b) {
		return b.ability().equals("hydration") && weather.equals("raindance");
	}

	private int stageSum(B b, int limiter) {
		int count = 0;
		for (int s = 0; s < 7; s++) {
			int stage = b.stages[s];
			count += limiter == 0 || limiter > 0 && stage > 0 || limiter < 0 && stage < 0 ? stage : 0;
		}
		return count;
	}

	private boolean memory(Set<String> ids) {
		return memoryHas(o, ids);
	}

	private boolean memory(String id) {
		return memoryHas(o, Set.of(id));
	}

	private double rough(B b, Stat stat) {
		int stage = stat == Stats.ATTACK ? b.stages[0] : stat == Stats.DEFENCE ? b.stages[1] : stat == Stats.SPECIAL_ATTACK ? b.stages[2]
				: stat == Stats.SPECIAL_DEFENCE ? b.stages[3] : b.stages[4];
		return stat == Stats.SPEED ? b.speed() : b.mon.stat(stat) * stageMul(stage);
	}

	private boolean mistyGrounded(B b) {
		return terrain.equals("mistyterrain") && b.grounded();
	}

	/** Reborn's pbCanSleep?/pbCanPoison?… for the target of the move being scored. */
	private boolean canStatus(B target, String status) {
		if (!target.status().isEmpty() || target.ability().equals("comatose") || target.ability().equals("purifyingsalt")) {
			return false;
		}
		boolean breaker = moldBreaker(a) && target != a;
		String ability = breaker ? "" : target.ability();
		if (conditions(target.ours).containsKey("safeguard") && target != a || mistyGrounded(target)) {
			return false;
		}
		if (m != null && m.status && target.has("substitute") && target != a) {
			return false;
		}
		if (ability.equals("leafguard") && weather.equals("sunnyday")) {
			return false;
		}
		return switch (status) {
			case "slp" -> !Set.of("insomnia", "vitalspirit", "sweetveil").contains(ability) && !(terrain.equals("electricterrain") && target.grounded());
			case "psn" -> !Set.of("immunity", "pastelveil").contains(ability)
					&& (!target.hasType("poison") && !target.hasType("steel") || a.ability().equals("corrosion"));
			case "brn" -> !target.hasType("fire") && !Set.of("waterveil", "waterbubble", "thermalexchange").contains(ability);
			case "par" -> !target.hasType("electric") && !ability.equals("limber");
			case "frz" -> !target.hasType("ice") && !ability.equals("magmaarmor") && !weather.equals("sunnyday");
			default -> true;
		};
	}

	private boolean canConfuse(B target) {
		return !target.has("confusion") && !(target.ability().equals("owntempo") && !moldBreaker(a)) && !mistyGrounded(target)
				&& !(m.status && target.has("substitute"));
	}

	private boolean anySetup(B b) {
		for (Mv move : b.moves) {
			if (SETUP.contains(move.id)) {
				return true;
			}
		}
		return false;
	}

	private boolean partyRole(String role) {
		for (BattlePokemon pokemon : a.battler.getActor().getPokemonList()) {
			if (roles(pokemon).contains(role)) {
				return true;
			}
		}
		return false;
	}

	private boolean partyHasType(String type) {
		for (BattlePokemon pokemon : a.battler.getActor().getPokemonList()) {
			if (pokemon.getHealth() > 0) {
				for (ElementalType elemental : pokemon.getEffectedPokemon().getTypes()) {
					if (elemental.getName().equalsIgnoreCase(type)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static int alive(B b) {
		return reserves(b) + (b.hp > 0 ? 1 : 0);
	}

	private boolean hasProtect(B b) {
		for (Mv move : b.moves) {
			if (PROTECT.contains(move.id)) {
				return true;
			}
		}
		return false;
	}

	private boolean faster() {
		return faster(null, null, a, o);
	}

	private boolean fasterWith() {
		return faster(m, null, a, o);
	}

	/** Reborn's {@code case @move.function}: the move's miniscore; may also change the score itself (returned in [1]). */
	private double[] ownRules(double score) {
		double miniscore = 1;
		int f = function(m.id);
		switch (f) {
			case 0x02 -> miniscore *= 0.2;
			case 0x03 -> {
				miniscore = sleep();
				if (fasterWith()) {
					miniscore *= 1.3;
				}
			}
			case 0x04 -> miniscore = sleep();
			case 0x05 -> miniscore = poison();
			case 0x06 -> {
				miniscore = poison();
				if (m.id.equals("toxic") && a.hasType("poison")) {
					miniscore *= 1.1;
				}
			}
			case 0x07 -> miniscore = paralysis();
			case 0x08 -> {
				miniscore = paralysis() * thunderBoost();
				if (weather.equals("raindance")) {
					miniscore *= neverMiss(initial[index]);
				}
			}
			case 0x09 -> miniscore = paralysis() * flinch();
			case 0x0A -> miniscore = burn();
			case 0x0B -> miniscore = burn() * flinch();
			case 0x0C -> miniscore = freeze();
			case 0x0D -> {
				miniscore = freeze();
				if (weather.equals("hail") || weather.equals("snow")) {
					miniscore *= neverMiss(initial[index]);
				}
			}
			case 0x0E -> miniscore = freeze() * flinch();
			case 0x0F, 0x10 -> miniscore = flinch();
			case 0x11 -> {
				if (a.status().equals("slp")) {
					miniscore = flinch();
				} else {
					score = 0;
				}
			}
			case 0x12 -> {
				if (a.turncount() == 0 && !(o.has("substitute") || o.ability().equals("innerfocus") || secondaryNegated())) {
					score *= flinch();
					if (score > 1) {
						score += 115;
					}
					if (!conditions(true).containsKey("tailwind")) {
						score *= 1.5;
					}
					if (a.ability().equals("unburden")) {
						score *= 1.5;
					}
					if (memory("encore")) {
						score *= 0.3;
					}
				} else if (a.turncount() != 0) {
					score = 0;
				}
			}
			case 0x13 -> miniscore = confuse();
			case 0x15 -> {
				miniscore = confuse() * thunderBoost();
				if (weather.equals("raindance")) {
					miniscore *= neverMiss(initial[index]);
				}
			}
			case 0x16 -> miniscore = attract();
			case 0x17 -> miniscore = (burn() + paralysis() + freeze()) / 3;
			case 0x18 -> miniscore = refresh();
			case 0x19 -> miniscore = partyRefresh();
			case 0x1b -> miniscore = psychoShift();
			case 0x1c -> miniscore = selfBoost(1, 0, 0, 0, 0, 0, 0);
			case 0x1d -> miniscore = selfBoost(0, 1, 0, 0, 0, 0, 0);
			case 0x1e -> {
				miniscore = selfBoost(0, 1, 0, 0, 0, 0, 0);
				if (a.hasMove("rollout") || a.hasMove("iceball")) {
					score *= 1.3;
				}
			}
			case 0x1f -> miniscore = selfBoost(0, 0, 0, 0, 1, 0, 0);
			case 0x20 -> miniscore = selfBoost(0, 0, 1, 0, 0, 0, 0);
			case 0x21 -> {
				miniscore = selfBoost(0, 0, 0, 1, 0, 0, 0);
				if (a.moves.stream().anyMatch(x -> x.type.getName().equalsIgnoreCase("electric")) && !a.has("charge")) {
					miniscore *= 1.5;
				}
			}
			case 0x22 -> miniscore = selfBoost(0, 0, 0, 0, 0, 0, 1);
			case 0x23 -> miniscore = focusEnergy();
			case 0x24 -> miniscore = selfBoost(1, 1, 0, 0, 0, 0, 0);
			case 0x25 -> miniscore = selfBoost(1, 1, 0, 0, 0, 1, 0);
			case 0x26 -> miniscore = selfBoost(1, 0, 0, 0, 1, 0, 0);
			case 0x27, 0x28 -> miniscore = selfBoost(1, 0, 1, 0, 0, 0, 0);
			case 0x29 -> miniscore = selfBoost(1, 0, 0, 0, 0, 1, 0);
			case 0x2a -> miniscore = selfBoost(0, 1, 0, 1, 0, 0, 0);
			case 0x2b -> miniscore = selfBoost(0, 0, 1, 1, 1, 0, 0);
			case 0x2c -> miniscore = selfBoost(0, 0, 1, 1, 0, 0, 0);
			case 0x2d -> miniscore = selfBoost(1, 1, 1, 1, 1, 0, 0);
			case 0x2e -> miniscore = selfBoost(2, 0, 0, 0, 0, 0, 0);
			case 0x2f -> miniscore = selfBoost(0, 2, 0, 0, 0, 0, 0);
			case 0x30 -> miniscore = selfBoost(0, 0, 0, 0, 2, 0, 0);
			case 0x31 -> {
				miniscore = selfBoost(0, 0, 0, 0, 2, 0, 0);
				if (memory(Set.of("lowkick", "grassknot"))) {
					miniscore *= 1.5;
				}
				if (memory(Set.of("heatcrash", "heavyslam"))) {
					miniscore *= 0.5;
				}
				if (a.hasMove("heatcrash") || a.hasMove("heavyslam")) {
					miniscore *= 0.7;
				}
			}
			case 0x32 -> miniscore = selfBoost(0, 0, 2, 0, 0, 0, 0);
			case 0x33 -> miniscore = selfBoost(0, 0, 0, 2, 0, 0, 0);
			case 0x34 -> miniscore = selfBoost(0, 0, 0, 0, 0, 0, 2);
			case 0x35 -> {
				miniscore = selfBoost(2, 0, 2, 0, 2, 0, 0);
				if (a.item().equals("whiteherb")) {
					miniscore *= 1.3;
				} else {
					miniscore *= selfDrop(score, 0, 1, 0, 1, 0) * 0.8;
				}
			}
			case 0x36 -> miniscore = selfBoost(1, 0, 0, 0, 2, 0, 0);
			case 0x37 -> miniscore = (selfBoost(2, 0, 0, 0, 0, 0, 0) + selfBoost(0, 2, 0, 0, 0, 0, 0) + selfBoost(0, 0, 2, 0, 0, 0, 0)
					+ selfBoost(0, 0, 0, 2, 0, 0, 0) + selfBoost(0, 0, 0, 0, 2, 0, 0) + selfBoost(0, 0, 0, 0, 0, 2, 0)
					+ selfBoost(0, 0, 0, 0, 0, 0, 2)) / 7;
			case 0x38 -> miniscore = selfBoost(0, 3, 0, 0, 0, 0, 0);
			case 0x39 -> miniscore = selfBoost(0, 0, 3, 0, 0, 0, 0);
			case 0x3a -> {
				miniscore = Math.pow(selfBoost(6, 0, 0, 0, 0, 0, 0), 1.4);
				boolean priorityHit = a.moves.stream().anyMatch(x -> !x.status && isPriority(x, a));
				if (!priorityHit && !faster()) {
					miniscore *= 0.3;
				}
				if (a.turncount() < 1) {
					miniscore *= 1.2;
				}
				if (a.hp / a.maxHp <= 0.5) {
					miniscore = 1;
				}
			}
			case 0x3b -> {
				if (a.ability().equals("contrary")) {
					miniscore = selfBoost(1, 1, 0, 0, 0, 0, 0);
				} else {
					miniscore = selfDrop(score, 1, 1, 0, 0, 0);
					if (Set.of("moxie", "chillingneigh", "asonechilling").contains(a.ability())) {
						miniscore *= 1.5;
					}
				}
			}
			case 0x3c -> miniscore = a.ability().equals("contrary") ? selfBoost(0, 1, 0, 1, 0, 0, 0) : selfDrop(score, 0, 1, 0, 1, 0);
			case 0x3d -> miniscore = a.ability().equals("contrary") ? selfBoost(0, 1, 0, 1, 1, 0, 0) : selfDrop(score, 0, 1, 0, 1, 1);
			case 0x3e -> miniscore = a.ability().equals("contrary") || trickRoom ? selfBoost(0, 0, 0, 0, 1, 0, 0) : selfDrop(score, 0, 0, 0, 0, 1);
			case 0x3f -> {
				if (a.ability().equals("contrary")) {
					miniscore = selfBoost(0, 0, 2, 0, 0, 0, 0);
				} else {
					miniscore = selfDrop(score, 0, 0, 2, 0, 0);
					if (a.ability().equals("soulheart")) {
						miniscore *= 1.3;
					}
				}
			}
			case 0x42 -> miniscore = oppDrop(1, 0, 0, 0, 0, 0, 0);
			case 0x43 -> miniscore = oppDrop(0, 1, 0, 0, 0, 0, 0);
			case 0x44 -> miniscore = oppDrop(0, 0, 0, 0, 1, 0, 0);
			case 0x45 -> miniscore = oppDrop(0, 0, 1, 0, 0, 0, 0);
			case 0x46 -> miniscore = oppDrop(0, 0, 0, 1, 0, 0, 0);
			case 0x47 -> miniscore = oppDrop(0, 0, 0, 0, 0, 1, 0);
			case 0x48 -> miniscore *= oppDrop(0, 0, 0, 0, 0, 0, 1);
			case 0x49 -> miniscore = defog() * hazardRemoval();
			case 0x4a -> miniscore = oppDrop(1, 1, 0, 0, 0, 0, 0);
			case 0x4b -> miniscore = oppDrop(2, 0, 0, 0, 0, 0, 0);
			case 0x4c -> miniscore = oppDrop(0, 2, 0, 0, 0, 0, 0);
			case 0x4d -> miniscore = oppDrop(0, 0, 0, 0, 2, 0, 0);
			case 0x4f -> miniscore = oppDrop(0, 0, 0, 2, 0, 0, 0);
			case 0x50 -> miniscore = (o.has("substitute") ? 1 : 1 + 0.05 * stageSum(o, 0) * (o.ability().equals("speedboost") ? 1.1 : 1))
					* neverMiss(initial[index]);
			case 0x51 -> {
				miniscore = 1.1 * stageSum(o, 0) - 1.1 * stageSum(a, 0);
				if (o.ability().equals("speedboost") || memory(SETUP)) {
					miniscore *= 0.8;
				}
			}
			case 0x5a -> miniscore = painSplit();
			case 0x5b -> miniscore = tailwind();
			case 0xa2, 0xa3, 0x15b -> miniscore = screen(f);
			case 0xaa -> miniscore = protect();
			case 0x133, 0x140 -> miniscore = protectEffect();
			case 0x15c -> {
				miniscore = protectEffect();
				if (!o.status().isEmpty()) {
					miniscore *= 0.8;
				} else if (canStatus(o, "psn")) {
					miniscore *= 1.3;
					if (a.ability().equals("merciless")) {
						miniscore *= 1.3;
					}
					if (o.ability().equals("poisonheal")) {
						miniscore *= 0.3;
					}
					if (o.ability().equals("toxicboost")) {
						miniscore *= 0.7;
					}
				}
			}
			case 0xb7 -> miniscore = torment();
			case 0xb9 -> miniscore = disable();
			case 0xba -> miniscore = taunt();
			case 0xbc -> miniscore = encore();
			case 0xd2 -> miniscore *= outrage(score);
			case 0xd5 -> miniscore = recover(a.maxHp / 2);
			case 0xd6 -> {
				miniscore = recover(a.maxHp / 2);
				Mv best = expectedMove(o, a);
				if (fasterWith() && a.hasType("flying") && best != null) {
					String type = best.type.getName().toLowerCase(Locale.ROOT);
					if (Set.of("rock", "ice", "electric").contains(type)) {
						score *= 1.5;
					} else if (Set.of("grass", "bug", "fighting", "ground").contains(type)) {
						score *= 0.5;
					}
				}
			}
			case 0xd7 -> miniscore = wish();
			case 0xd8 -> {
				double amount = Math.floor(a.maxHp / 2);
				if (weather.equals("sunnyday")) {
					amount = Math.floor(a.maxHp * 0.66);
				} else if (!weather.isEmpty()) {
					amount = Math.floor(a.maxHp * 0.25);
				}
				miniscore = recover(amount);
			}
			case 0x16c -> miniscore = recover(weather.equals("sandstorm") ? a.maxHp * 2 / 3 : a.maxHp / 2);
			case 0xd9 -> miniscore = rest();
			case 0xda, 0xdb -> miniscore = aquaRing(f);
			case 0xdc -> miniscore = leechSeed();
			case 0xdd, 0x139 -> miniscore = absorb(initial[index], f);
			case 0xe0 -> miniscore = death();
			case 0xe5 -> miniscore = perish();
			case 0xe7 -> miniscore = destiny();
			case 0xeb -> miniscore = phase();
			case 0xec -> miniscore = phase();
			case 0xed, 0xee -> miniscore = pivot();
			case 0x13d -> miniscore = pivot() * oppDrop(1, 0, 1, 0, 0, 0, 0);
			case 0xef -> miniscore = meanLook();
			case 0xf0 -> miniscore = reduceWhenKills(knockOff());
			case 0xff -> miniscore = weatherMove() * sun();
			case 0x100 -> miniscore = weatherMove() * rain();
			case 0x101 -> miniscore = weatherMove() * sand();
			case 0x102 -> miniscore = weatherMove() * hail();
			case 0x103 -> {
				int spikes = conditions(false).getOrDefault("spikes", 0);
				miniscore = spikes < 3 ? hazard() * (spikes > 0 ? 0.9 : 1) : 0;
			}
			case 0x104 -> {
				int toxic = conditions(false).getOrDefault("toxicspikes", 0);
				miniscore = toxic < 2 ? hazard() * (toxic > 0 ? 0.9 : 1) : 0;
			}
			case 0x105 -> {
				if (!conditions(false).containsKey("stealthrock")) {
					miniscore = hazard();
					if (a.hasMove("spikes") || a.hasMove("toxicspikes")) {
						miniscore *= 1.05;
					}
				} else {
					miniscore = 0;
				}
			}
			case 0x141 -> miniscore = conditions(false).containsKey("stickyweb") ? 0 : hazard();
			case 0x10b -> miniscore = jump(score);
			case 0x10c -> miniscore = substitute();
			case 0x110 -> {
				if (a.has("leechseed")) {
					score += 20;
				}
				if (reserves(a) > 0) {
					Map<String, Integer> mine = conditions(true);
					score += (mine.containsKey("stealthrock") ? 25 : 0) + (mine.containsKey("stickyweb") ? 25 : 0)
							+ 10 * mine.getOrDefault("spikes", 0) + 15 * mine.getOrDefault("toxicspikes", 0);
					miniscore = hazardRemoval();
				}
			}
			case 0x116 -> miniscore = suckerPunch();
			case 0x11f -> miniscore = trickRoomMove();
			case 0x134 -> miniscore = electricTerrain();
			case 0x135 -> miniscore = grassyTerrain();
			case 0x136 -> miniscore = mistyTerrain();
			case 0x168 -> miniscore = psychicTerrain();
			case 0x161 -> {
				if (a.turncount() != 0) {
					score = 0;
				}
				miniscore = score >= 110 ? 1.1 : 1.0;
			}
			case 0x172 -> miniscore = recover(rough(o, Stats.ATTACK)) * oppDrop(1, 0, 0, 0, 0, 0, 0);
			case 0x40 -> miniscore = o.ability().equals("contrary") ? oppDrop(0, 0, 1, 0, 0, 0, 0) : oppBoost(true, 0, 0, 1, 0, 0);
			case 0x41 -> miniscore = o.ability().equals("contrary") ? oppDrop(2, 0, 0, 0, 0, 0, 0) : oppBoost(true, 2, 0, 0, 0, 0);
			case 0x4e -> miniscore = attractable() ? oppDrop(0, 0, 2, 0, 0, 0, 0) : 0;
			case 0x52 -> miniscore = statSwap(0, 2);
			case 0x53 -> miniscore = statSwap(1, 3);
			case 0x54 -> {
				int[][] arrays = psychUp();
				double buff = selfBoost(arrays[0]) - selfDrop(score, java.util.Arrays.copyOf(arrays[1], 5));
				double drop = oppDrop(arrays[0]) - selfBoost(arrays[1]);
				miniscore = buff + drop;
			}
			case 0x55 -> {
				int[][] arrays = psychUp();
				miniscore = selfBoost(arrays[0]) - selfDrop(score, java.util.Arrays.copyOf(arrays[1], 5));
				int counter = 0;
				for (int s = 0; s < 7; s++) {
					counter += arrays[0][s] - arrays[1][s];
				}
				if (counter >= 3) {
					miniscore *= 1.3;
				}
			}
			case 0x56 -> {
				miniscore = conditions(true).containsKey("mist") ? 1 : memoryOf(o).stream().anyMatch(x -> {
					int code = function(x.id);
					return code >= 0x42 && code <= 0x4F || code == 0xE2 || code == 0x138 || code == 0x13B || code == 0x13F;
				}) ? 1.1 * 1.3 : 1.1;
				if (miniscore <= 1) {
					score = 0;
				}
			}
			case 0x57 -> miniscore = powerTrick();
			case 0x58 -> miniscore = split(true);
			case 0x59 -> miniscore = split(false);
			case 0x5c, 0x5d -> miniscore = mimic(f == 0x5d);
			case 0x5e -> miniscore = typeChange(a.moves.isEmpty() ? null : a.moves.get(0).type.getName().toLowerCase(Locale.ROOT));
			case 0x5f -> {
				Mv last = opponentLast();
				miniscore = 0;
				if (last != null) {
					List<String> resisted = new ArrayList<>();
					for (ElementalType type : ElementalTypes.all()) {
						if (com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility.INSTANCE.getDamageMultiplier(last.type, type) < 1) {
							resisted.add(type.getName().toLowerCase(Locale.ROOT));
						}
					}
					for (String type : resisted) {
						miniscore += typeChange(type) / resisted.size();
					}
				}
			}
			case 0x60 -> miniscore = typeChange("normal");
			case 0x61 -> miniscore = oppTypeChange("water");
			case 0x62 -> {
				double best = 0;
				for (String type : o.mon.types) {
					best = Math.max(best, typeChange(type));
				}
				miniscore = best;
			}
			case 0x63 -> miniscore = abilityChange("simple");
			case 0x64 -> miniscore = abilityChange("insomnia");
			case 0x65 -> miniscore = FIXED_ABILITIES.contains(a.ability()) || o.ability().isEmpty() || a.ability().equals(o.ability()) ? 0
					: 1 + (abilityDisrupt(a, o) - abilityDisrupt(o, a));
			case 0x66 -> {
				if (FIXED_ABILITIES.contains(o.ability()) || o.ability().equals("truant") || o.ability().isEmpty() || a.ability().equals(o.ability())) {
					score = 0;
				} else {
					score *= 1 + (abilityDisrupt(a, o) - abilityDisrupt(o, a));
					if (a.ability().equals("truant")) {
						score *= 3;
					} else if (a.ability().equals("wonderguard")) {
						score = 0;
					}
				}
			}
			case 0x67 -> {
				if (FIXED_ABILITIES.contains(a.ability()) && !a.ability().equals("zenmode") || FIXED_ABILITIES.contains(o.ability()) && !o.ability().equals("zenmode")
						|| a.ability().equals("illusion") || o.ability().equals("illusion") || o.ability().isEmpty() || a.ability().equals(o.ability())) {
					miniscore = 0;
				} else {
					double theirs = abilityDisrupt(o, a);
					double mine = abilityDisrupt(a, o);
					miniscore = theirs * (1 + (mine - theirs) * 2);
					if (a.ability().equals("truant")) {
						miniscore *= 2;
					}
				}
			}
			case 0x68 -> miniscore = o.has("gastroacid") || o.has("substitute") || FIXED_ABILITIES.contains(o.ability()) ? 0 : abilityDisrupt(a, o);
			case 0x69 -> miniscore = o.has("transform") || o.has("substitute") || a.has("transform") ? 0
					: (1 + (o.mon.level - a.mon.level) / 20) * 1.1 * (stageSum5(o) - stageSum5(a));
			case 0x6e -> {
				if (a.hp > o.hp) {
					miniscore = 0;
				} else {
					if (a.moves.stream().anyMatch(x -> isPriority(x, a))) {
						miniscore *= 1.5;
					}
					if (notOHKO(a, o)) {
						miniscore *= 1.5;
					}
					if (o.mon.level - a.mon.level > 9) {
						miniscore *= 2;
					}
				}
			}
			case 0x70 -> miniscore = o.mon.level > a.mon.level || notOHKO(o, a) ? 0 : 0.7;
			case 0x71, 0x72, 0x73 -> miniscore = counterAttack(f);
			case 0x74 -> {
			}
			case 0x78 -> miniscore = flinch();
			case 0x7c -> {
				if (o.status().equals("par") && !o.has("substitute")) {
					score *= 0.8;
					if (o.speed() > a.speed() && o.speed() / 2 < a.speed()) {
						score *= 0.5;
					}
				}
			}
			case 0x7d -> {
				if (o.status().equals("slp") && !o.has("substitute")) {
					score *= 0.8;
					if (a.ability().equals("baddreams") || a.hasMove("dreameater") || a.hasMove("nightmare")) {
						score *= 0.3;
					}
					if (memory(Set.of("sleeptalk", "snore"))) {
						score *= 1.3;
					}
				}
			}
			case 0x81 -> {
				miniscore = faster() ? 0.5 : 1.5;
				if (a.hp >= a.maxHp) {
					miniscore *= 1.2;
					if (notOHKO(a, o)) {
						miniscore *= 1.1;
					}
				} else if (expectedDamage(a, o) > a.hp) {
					miniscore *= 0.3;
				}
				if (memory(SETUP)) {
					miniscore *= 0.8;
				}
			}
			case 0x82 -> {
				if (!fasterWith()) {
					score *= 1.5;
				}
			}
			case 0x84 -> {
				if (!fasterWith()) {
					score *= 2;
				}
			}
			case 0x88 -> {
				miniscore = 1 - 0.1 * stageSum(o, -1);
				if (o.has("confusion")) {
					miniscore *= 1.2;
				}
				if (o.has("leechseed")) {
					miniscore *= 1.5;
				}
				if (o.has("attract")) {
					miniscore *= 1.3;
				}
				if (o.has("substitute")) {
					miniscore *= 0.7;
				}
				if (o.has("yawn")) {
					miniscore *= 1.5;
				}
				if (typeMod(m, a, o) > 1) {
					miniscore *= 1.5;
				}
				if (a.stages[4] != 6 && score >= 100) {
					miniscore *= 1.5;
					if (fasterWith()) {
						miniscore *= 2;
					}
				}
			}
			case 0x8B -> {
				if (!fasterWith()) {
					double original = Math.max(150 * a.hp / a.maxHp, 1);
					double actual = Math.max(150 * (a.hp - expectedDamage(a, o)) / a.maxHp, 1);
					score *= actual / original;
				}
			}
			case 0x91 -> {
				miniscore = echo() * (1 + 0.15 * a.stages[5]) * (1 - 0.08 * o.stages[6]);
				if (memory(PROTECT)) {
					miniscore *= 0.8;
				}
			}
			case 0x92 -> miniscore = echo();
			case 0x93 -> {
				if (a.attack() > a.spatk()) {
					score *= 1.2;
				}
				if (a.hp >= a.maxHp) {
					score *= 1.3;
				}
				if (expectedDamage(a, o) < a.hp / 4) {
					score *= 1.3;
				}
			}
			case 0x94 -> {
				if (o.hp >= o.maxHp) {
					score *= 1.2;
				}
			}
			case 0x96 -> {
				if (!a.item().endsWith("berry") || a.ability().equals("klutz")) {
					score = 0;
				}
			}
			case 0x97 -> {
				if (a.hp >= a.maxHp) {
					score *= 1.2;
				}
				if (expectedDamage(a, o) < a.hp / 3) {
					score *= 1.3;
				}
			}
			case 0x98 -> {
				if (!fasterWith()) {
					score *= 1.1;
					if (a.hp < a.maxHp) {
						score *= 1.3;
					}
				}
			}
			case 0x9c, 0x117, 0x11d, 0x120, 0x13a, 0x162, 0x164, 0x167, 0x169, 0x170, 0x317 -> {
				// Moves for a partner (double battles): nothing to do in a single battle.
				if (Set.of(0x9c, 0x117, 0x120, 0x13a, 0x162, 0x169, 0x170, 0x317).contains(f)) {
					miniscore = 0;
				}
			}
			case 0xa0 -> miniscore = permaCrit(initial[index]);
			case 0x165 -> miniscore = a.has("laserfocus") ? 0 : permaCrit(initial[index]);
			case 0x319 -> miniscore = permaCrit(initial[index]) * multiHit();
			case 0xa5 -> miniscore = neverMiss(initial[index]);
			case 0xa6 -> {
				if (a.hasMove("inferno") || a.hasMove("zapcannon") || a.hasMove("dynamicpunch")) {
					miniscore *= 3;
				}
				if (a.moves.stream().anyMatch(x -> Set.of("guillotine", "sheercold", "fissure", "horndrill").contains(x.id))) {
					miniscore *= 10;
				}
				miniscore *= (1 + 0.1 * Math.min(0, a.stages[5])) * (1 + 0.1 * o.stages[6]);
			}
			case 0xa7 -> {
				if (o.has("foresight")) {
					miniscore = 0;
				} else {
					miniscore = 1 + 0.1 * Math.max(0, o.stages[6]);
					if (o.hasType("ghost")) {
						miniscore *= 1.5;
						if (!a.ability().equals("scrappy") && a.moves.stream().noneMatch(x -> !x.status
								&& !x.type.getName().equalsIgnoreCase("normal") && !x.type.getName().equalsIgnoreCase("fighting"))) {
							miniscore *= 5;
						}
					}
				}
			}
			case 0xa8 -> {
				if (o.has("miracleeye")) {
					miniscore = 0;
				} else {
					miniscore = 1 + 0.1 * Math.max(0, o.stages[6]);
					if (o.hasType("dark")) {
						miniscore *= 1.1;
						if (a.moves.stream().noneMatch(x -> !x.status && !x.type.getName().equalsIgnoreCase("psychic"))) {
							miniscore *= 2;
						}
					}
				}
			}
			case 0xa9 -> miniscore = 1 + 0.05 * (Math.max(0, o.stages[6]) + Math.max(0, o.stages[1]) + Math.max(0, o.stages[3]));
			case 0xab -> miniscore = o.ability().equals("galewings") && o.hp >= o.maxHp || o.ability().equals("prankster") || memoryPriority(o)
					? specialProtect() : 0;
			case 0xac -> miniscore = 0;
			case 0xad -> miniscore = feint();
			case 0x157 -> miniscore = neverMiss(initial[index]) * feint();
			case 0x159 -> {
				if (a.mon.name.toLowerCase(Locale.ROOT).contains("hoopa")) {
					miniscore = neverMiss(initial[index]) * feint()
							* (a.ability().equals("contrary") ? selfBoost(0, 1, 0, 0, 0, 0, 0) : selfDrop(score, 0, 1, 0, 0, 0));
				} else {
					score = 0;
				}
			}
			case 0xae, 0xaf -> {
				Mv last = opponentLast();
				if (last == null || f == 0xaf && o.has("substitute")) {
					score = 0;
				} else {
					score = Math.min(roughDamage(last, a, o) / Math.max(1, o.hp), 100);
					if (!faster() && !a.ability().equals("prankster")) {
						score *= 0.5;
					}
				}
			}
			case 0xb0 -> {
				if (!fasterWith()) {
					miniscore = 0;
				} else {
					miniscore *= memory(SETUP) ? 0.8 : 1.5;
					miniscore *= memoryPriority(o) ? 0.6 : 1.5;
					Mv best = expectedMove(o, a);
					double max = 0;
					for (double value : initial) {
						max = Math.max(max, value);
					}
					if (best != null && o.hp > 0) {
						miniscore *= 100 * roughDamage(best, a, o) / o.hp > max ? 2.7 : 0.5;
					}
				}
			}
			case 0xb1 -> {
				if ("magiccoat".equals(lastMove.get(a.battler.getUuid()))) {
					miniscore *= 0.5;
				} else {
					if (a.hp >= a.maxHp) {
						miniscore *= 1.5;
					}
					if (o.moves.stream().noneMatch(x -> !x.status)) {
						miniscore *= 3;
					}
				}
			}
			case 0xb2 -> {
				if ("snatch".equals(lastMove.get(a.battler.getUuid()))) {
					miniscore *= 0.5;
				} else {
					if (o.hp >= o.maxHp) {
						miniscore *= 1.5;
					}
					if (memory(SETUP)) {
						miniscore *= 2;
					}
					if (o.attack() > o.spatk()) {
						miniscore *= a.attack() > a.spatk() ? 1.5 : 0.7;
					} else {
						miniscore *= a.spatk() > a.attack() ? 1.5 : 0.7;
					}
				}
			}
			case 0xb4 -> miniscore = sleepTalk();
			case 0xb5, 0xb6 -> {
				boolean high = false;
				for (double value : initial) {
					high |= value > (f == 0xb5 ? 25 : 20);
				}
				miniscore = reserves(a) > 0 ? 0 : high ? 0.5 : 1.5;
			}
			case 0xb8 -> {
				if (o.has("imprison")) {
					miniscore = 0;
				} else {
					Set<String> ours = new HashSet<>();
					a.moves.forEach(x -> ours.add(x.id));
					int sub = 1;
					if (ours.contains(lastMove.get(o.battler.getUuid()))) {
						miniscore *= 1.3;
					}
					for (Mv move : memoryOf(o)) {
						if (ours.contains(move.id)) {
							sub++;
							if (HEALING.contains(move.id)) {
								miniscore *= 1.5;
							}
						} else {
							miniscore *= 0.7;
						}
					}
					miniscore *= sub;
				}
			}
			case 0xbb -> {
				if (o.has("healblock")) {
					miniscore = 0;
				} else {
					if (fasterWith()) {
						miniscore *= 1.5;
					}
					if (memoryHealing(o)) {
						miniscore *= 2.5;
					}
					miniscore *= hpGain(o) * 4;
				}
			}
			case 0xbd, 0xbf, 0xc0, 0x17E -> miniscore = multiHit();
			case 0xbe -> miniscore = Math.pow(poison(), 1.2) * multiHit();
			case 0xc1 -> miniscore = alive(a) > 0 ? multiHit() : 1;
			case 0xc2 -> miniscore = hyperBeam();
			case 0xc4 -> {
				if (!weather.equals("sunnyday") && a.hasMove("sunnyday") && !weatherSuppressed() && !a.item().equals("powerherb")) {
					miniscore = 0.3;
				} else if (!weather.equals("sunnyday")) {
					miniscore = chargeTurn();
				}
			}
			case 0xc5 -> miniscore = paralysis() * chargeTurn();
			case 0xc6 -> miniscore = burn() * chargeTurn();
			case 0xc7 -> miniscore = flinch() * chargeTurn();
			case 0xc8 -> miniscore = selfBoost(0, 1, 0, 0, 0, 0, 0) * chargeTurn();
			case 0x308 -> miniscore = selfBoost(0, 0, 1, 0, 0, 0, 0) * chargeTurn();
			case 0x13e -> miniscore = chargeTurn() * selfBoost(0, 0, 2, 2, 2, 0, 0);
			case 0xc9 -> {
				if (a.ability().equals("noguard") || o.ability().equals("noguard")) {
					if (memory(Set.of("thunder", "hurricane"))) {
						miniscore *= 0.3;
					}
				}
			}
			case 0xca -> {
				if (a.ability().equals("noguard") || o.ability().equals("noguard")) {
					miniscore = twoTurn();
					if (memory("earthquake")) {
						miniscore *= 0.3;
					}
				}
			}
			case 0xcb -> {
				if (a.ability().equals("noguard") || o.ability().equals("noguard")) {
					miniscore = chargeTurn();
				} else {
					miniscore = twoTurn();
					if (memory("surf")) {
						miniscore *= 0.3;
					}
				}
			}
			case 0xcc -> {
				if (a.ability().equals("noguard") || o.ability().equals("noguard")) {
					miniscore = twoTurn();
					if (memory(Set.of("thunder", "hurricane"))) {
						miniscore *= 0.3;
					}
				}
				miniscore *= paralysis();
			}
			case 0xcd -> {
				if (memory(PROTECT)) {
					miniscore *= 1.1;
				}
			}
			case 0xce -> miniscore = a.ability().equals("noguard") || o.ability().equals("noguard") ? chargeTurn() : twoTurn();
			case 0xcf, 0xd0 -> {
				miniscore = fireSpin();
				if (f == 0xd0 && "dive".equals(charging.get(o.battler.getUuid()))) {
					miniscore *= 1.3;
				}
			}
			case 0x180 -> miniscore = fireSpin() * oppDrop(0, 1, 0, 1, 0, 0, 0);
			case 0xd1 -> {
				if (o.status().equals("slp")) {
					miniscore *= 0.7;
				}
				if (memory("rest")) {
					miniscore *= 1.8;
				}
				if (reserves(o) == 0 || Set.of("arenatrap", "shadowtag").contains(a.ability())) {
					miniscore *= 1.1;
				}
				if (typeMod(m, a, o) < 1) {
					miniscore *= 0.7;
				}
				if (a.hp / a.maxHp < 0.75) {
					miniscore *= 0.75;
				}
				if (a.stages[2] < 0) {
					miniscore *= 1 + 0.05 * a.stages[2];
				}
			}
			case 0xd3 -> miniscore = rollout();
			case 0xd4 -> miniscore = bide();
			case 0xde -> miniscore = o.status().equals("slp") ? absorb(initial[index], f) : 0;
			case 0xdf -> miniscore = 0;
			case 0xe1 -> {
				miniscore = 0.7 * (faster() ? 1.1 : 0.5) * (a.hp > o.hp ? 1.1 : 0.5);
				if (notOHKO(o, a)) {
					miniscore *= 0.2;
				}
			}
			case 0xe2 -> {
				if (badMoves(10)) {
					score = 15;
				}
				double memento = a.hp >= a.maxHp ? 0.2 : (1 - a.hp / a.maxHp) * (a.hp * 4 < a.maxHp ? 1.3 : 1);
				score = memento * oppDrop(2, 0, 2, 0, 0, 0, 0) * score;
			}
			case 0xe3, 0xe4 -> miniscore = healingWish();
			case 0xe6 -> miniscore = death() * grudge();
			case 0xe8 -> miniscore *= endure();
			case 0xe9 -> {
				if (score >= 100) {
					miniscore = 0.1;
				}
			}
			case 0xea -> score = 0;
			case 0xf1 -> miniscore = covet();
			case 0xf2 -> miniscore = covet() * bestow();
			case 0xf3 -> miniscore = bestow();
			case 0xf4 -> miniscore = nom();
			case 0xf5 -> miniscore = roast();
			case 0xf6 -> miniscore = 0;
			case 0xf7 -> miniscore = fling();
			case 0xf8 -> miniscore = embargo(o);
			case 0xf9 -> miniscore = embargo(o) / Math.max(embargo(a), 1);
			case 0x10a -> miniscore = brickBreak();
			case 0x10d -> {
				if (a.hasType("ghost")) {
					miniscore = spooky();
				} else {
					miniscore = selfBoost(1, 1, 0, 0, 0, 0, 0) * selfDrop(score, 0, 0, 0, 0, 1);
				}
			}
			case 0x10e, 0x320 -> score = spite(score);
			case 0x10f -> {
				miniscore *= o.ability().equals("comatose") ? 6 : 1;
				if (badMoves(25)) {
					miniscore *= 6;
				}
				if (o.ability().equals("shedskin") || o.ability().equals("earlybird")) {
					miniscore *= 0.5;
				}
				miniscore *= Set.of("arenatrap", "shadowtag").contains(a.ability()) || o.has("trapped") ? 1.3 : 0.8;
			}
			case 0x111 -> {
				if (o.has("futuresight")) {
					miniscore = 0;
				} else {
					miniscore = 0.6;
					if (reserves(a) == 0) {
						miniscore *= 0.7;
					}
					if (a.has("substitute")) {
						miniscore *= 1.2;
					}
					if (a.moves.stream().anyMatch(x -> Set.of("protect", "detect", "banefulbunker", "spikyshield").contains(x.id))) {
						miniscore *= 1.2;
					}
					if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
						miniscore *= 1.1;
					}
					if (a.ability().equals("moody") || a.hasMove("quiverdance") || a.hasMove("nastyplot") || a.hasMove("tailglow")) {
						miniscore *= 1.2;
					}
				}
			}
			case 0x112 -> miniscore = stockpile() < 3 ? selfBoost(1, 1, 0, 0, 0, 0, 0) : 0;
			case 0x113 -> {
				int stock = stockpile();
				if (stock == 0) {
					miniscore = 0;
				} else {
					miniscore = antiStat(score, 0, stock, 0, stock, 0);
					if (a.hasMove("swallow") && a.hp / a.maxHp < 0.66) {
						miniscore *= 0.8;
						if (a.hp < 0.4 * a.maxHp) {
							miniscore *= 0.5;
						}
					}
				}
			}
			case 0x114 -> {
				int stock = stockpile();
				miniscore = stock == 0 ? 0 : recover(a.maxHp / 2) * selfDrop(score, 0, stock, 0, stock, 0);
			}
			case 0x115 -> miniscore = focusPunch();
			case 0x118 -> miniscore = gravity();
			case 0x119 -> {
				if (a.has("magnetrise") || a.has("ingrain") || a.has("smackdown")) {
					miniscore = 0;
				} else {
					Mv best = expectedMove(o, a);
					if (best != null && best.type.getName().equalsIgnoreCase("ground")) {
						miniscore *= 3;
					}
					if (o.hasType("ground")) {
						miniscore *= 3;
					}
				}
			}
			case 0x11a -> score = 0;
			case 0x11c -> miniscore = smackDown();
			case 0x123 -> {
				boolean shares = false;
				for (String type : a.mon.types) {
					shares |= o.hasType(type);
				}
				if (!shares) {
					score = 0;
				}
			}
			case 0x124 -> miniscore = wonderRoom();
			case 0x125 -> {
				boolean allUsed = true;
				Set<String> used = memory.getOrDefault(a.battler.getUuid(), Set.of());
				for (Mv move : a.moves) {
					allUsed &= function(move.id) == 0x125 || used.contains(move.id);
				}
				miniscore = allUsed ? 1 : 0;
			}
			case 0x137 -> {
			}
			case 0x138 -> miniscore = oppDrop(1, 0, 1, 0, 0, 0, 0);
			case 0x13b -> miniscore = oppDrop(0, 0, 2, 0, 0, 0, 0);
			case 0x13f -> miniscore = o.status().equals("psn") || o.status().equals("tox") ? oppDrop(1, 0, 1, 0, 1, 0, 0) : 0;
			case 0x142 -> miniscore = Math.max(1 + 0.1 * stageSum(o, 0), 0);
			case 0x143 -> miniscore = oppTypeChange("grass");
			case 0x144 -> miniscore = oppTypeChange("ghost");
			case 0x145 -> {
				if (a.has("perish1") || a.has("perish2")) {
					miniscore = 0;
				} else {
					if (o.has("perish2")) {
						miniscore *= 10;
					}
					if (o.has("perish1")) {
						miniscore *= 20;
					}
					miniscore *= (a.has("leechseed") ? 0.8 : 1) * (o.has("leechseed") ? 1.2 : 1) * (o.has("curse") ? 1.3 : 1)
							* (a.has("curse") ? 0.7 : 1) * (o.has("confusion") ? 1.1 : 1) * (a.has("confusion") ? 1.1 : 1);
				}
			}
			case 0x146, 0x163 -> miniscore = a.ability().equals("plus") || a.ability().equals("minus")
					? f == 0x146 ? selfBoost(0, 1, 0, 1, 0, 0, 0) : selfBoost(1, 0, 1, 0, 0, 0, 0) : 0;
			case 0x147 -> {
				if (a.stages[0] != 6 && score >= 100) {
					miniscore = fasterWith() ? 4 : 2;
				}
			}
			case 0x148, 0x153 -> miniscore = 0;
			case 0x149, 0x150, 0x151 -> score = 0;
			case 0x152 -> miniscore = powder();
			case 0x154 -> miniscore = a.turncount() == 0 && faster() ? protect() : 0;
			case 0x155, 0x189 -> miniscore = meanLook();
			case 0x15d -> {
				miniscore = burn();
				if (faster()) {
					miniscore *= 0.7;
				}
				if (memoryOf(o).stream().anyMatch(Mv::contact)) {
					miniscore *= 1.5;
				} else {
					miniscore *= o.attack() > o.spatk() ? 1.3 : 0.3;
				}
			}
			case 0x15e -> miniscore = burnUp();
			case 0x15f -> miniscore = a.ability().equals("contrary") ? selfBoost(0, 1, 0, 0, 0, 0, 0) : antiStat(initial[index], 0, 1, 0, 0, 0);
			case 0x160 -> {
				if (!FIXED_ABILITIES.contains(o.ability()) && !o.has("gastroacid") && !o.has("substitute") && (!fasterWith() || memoryPriority(o))) {
					miniscore = reduceWhenKills(abilityDisrupt(a, o));
				}
			}
			case 0x166, 0x176 -> miniscore = moldBreakerMove();
			case 0x16b -> miniscore = shellTrap();
			case 0x16d -> miniscore = o.status().equals("brn") ? 0.6 : 1.0;
			case 0x16e -> {
				miniscore = 0.1 * stageSum(o, 0);
				if (a.ability().equals("contrary")) {
					miniscore *= -1;
				}
				if (a.ability().equals("simple")) {
					miniscore *= 2;
				}
				miniscore += 1;
				if (o.has("substitute")) {
					miniscore *= 1.2;
				}
			}
			case 0x16f -> miniscore = faster() || o.stages[4] == 0 && a.stages[4] == 0 ? 0 : 1 + 0.1 * o.stages[4] - 0.1 * a.stages[4];
			case 0x173 -> {
				if (!secondaryNegated()) {
					Mv best = expectedMove(o, a);
					if (best != null && best.sound()) {
						miniscore *= 1.5;
					} else if (memoryOf(o).stream().anyMatch(Mv::sound)) {
						miniscore *= 1.3;
					}
				}
			}
			case 0x174 -> miniscore = poison() * oppDrop(0, 0, 0, 0, 1, 0, 0);
			case 0x175 -> miniscore = mindBlown(initial[index]);
			case 0x177 -> miniscore = electricTerrain();
			case 0x17A -> {
				if (a.item().endsWith("berry")) {
					miniscore = selfBoost(0, 2, 0, 0, 0, 0, 0) * berryBonus(a, a);
				} else {
					score = 0;
				}
			}
			case 0x17B -> {
				if (!a.has("noretreat")) {
					miniscore = selfBoost(1, 1, 1, 1, 1, 0, 0);
				} else {
					score = 0;
				}
			}
			case 0x17C -> {
				miniscore = oppDrop(0, 0, 0, 0, 1, 0, 0);
				if (!o.has("tarshot") && ExpertBattleAI.effectiveness(ElementalTypes.get("fire"), o.mon, null) != 0
						&& !o.ability().equals("flashfire") && partyHasType("fire")) {
					miniscore *= 1.2;
				}
			}
			case 0x17D -> miniscore = oppTypeChange("psychic");
			case 0x17F -> miniscore = teaTime();
			case 0x182 -> miniscore = defog() * hazardRemoval();
			case 0x183 -> {
				miniscore = Math.pow(selfBoost(1, 1, 1, 1, 1, 0, 0), 1.2);
				if (a.moves.stream().noneMatch(x -> !x.status && isPriority(x, a)) && !faster()) {
					miniscore *= 0.3;
				}
				if (a.turncount() < 1) {
					miniscore *= 1.2;
				}
				if (a.hp / a.maxHp <= 0.333) {
					miniscore = 1;
				}
			}
			case 0x185 -> miniscore = o.ability().equals("contrary") ? oppDrop(2, 0, 2, 0, 0, 0, 0) : oppBoost(false, 2, 0, 2, 0, 0);
			case 0x186 -> miniscore = selfBoost(0, 0, 0, 0, 1, 0, 0);
			case 0x187, 0x318 -> miniscore = recover(a.maxHp / 4);
			case 0x188 -> miniscore = protectEffect();
			case 0x307 -> miniscore = multiHit() * selfBoost(0, 0, 0, 0, 1, 0, 0) * selfDrop(score, 0, 1, 0, 0, 0);
			case 0x309, 0x502 -> miniscore = poison();
			case 0x313 -> {
				if (memory(SETUP) && !faster()) {
					miniscore = burn();
				}
			}
			case 0x314 -> {
			}
			case 0x315 -> {
				if (o.item().isEmpty()) {
					miniscore = 0;
				}
			}
			case 0x316 -> miniscore = knockOff();
			case 0x500 -> miniscore = (sleep() + poison() + paralysis()) / 3;
			case 0x501 -> miniscore = selfBoost(1, 1, 0, 0, 1, 0, 0);
			case 0x503 -> miniscore = oppDrop(0, 1, 0, 0, 0, 0, 0) * flinch();
			case 0x504 -> miniscore = burn();
			case 0x505 -> miniscore = selfBoost(0, 0, 1, 1, 0, 0, 0) * Math.max(refresh(), 1);
			case 0x506 -> miniscore = jump(score) * confuse();
			case 0x207 -> miniscore = selfBoost(0, 0, 1, 0, 1, 0, 0);
			case 0x208 -> miniscore = absorb(initial[index], f) * poison();
			case 0x20A -> miniscore = oppDrop(0, 0, 0, 0, 1, 0, 0);
			case 0x20B -> miniscore = (selfBoost(0, 2, 0, 0, 0, 0, 0) + selfBoost(0, 0, 0, 2, 0, 0, 0)) / 2;
			case 0x20D -> miniscore = m.physical ? oppDrop(0, 1, 0, 0, 0, 0, 0) : oppDrop(0, 0, 0, 1, 0, 0, 0);
			default -> {
			}
		}
		return new double[] {miniscore, score};
	}

	// ------------------------------------------------ status

	private double sleep() {
		if (!canStatus(o, "slp") || o.has("yawn") || secondaryNegated() || hydration(o)) {
			return none();
		}
		if (m.id.equals("darkvoid") && !a.mon.name.toLowerCase(Locale.ROOT).contains("darkrai")) {
			return none();
		}
		double miniscore = 1.2;
		if (a.hasMove("dreameater") || a.hasMove("nightmare") || a.ability().equals("baddreams")) {
			miniscore *= 1.5;
		}
		miniscore *= 1.2 * hpGain(a);
		if (anySetup(a)) {
			miniscore *= 1.3;
		}
		if (a.hasMove("leechseed")) {
			miniscore *= 1.3;
		}
		if (a.hasMove("substitute")) {
			miniscore *= 1.3;
		}
		if (o.hp >= o.maxHp) {
			miniscore *= 1.2;
		}
		if (memory(Set.of("sleeptalk", "snore"))) {
			miniscore *= 0.1;
		}
		if (o.ability().equals("naturalcure")) {
			miniscore *= 0.1;
		}
		if (o.ability().equals("marvelscale")) {
			miniscore *= 0.8;
		}
		if (o.has("confusion")) {
			miniscore *= 0.4;
		}
		if (o.has("attract")) {
			miniscore *= 0.5;
		}
		int stages = stageSum(o, 0);
		if (stages > 0) {
			miniscore *= 1 + 0.1 * stages;
		}
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall") || a.roles.contains("cleric") || a.roles.contains("pivot")) {
			miniscore *= 1.2;
		}
		if (badMoves(40)) {
			miniscore *= 1.3;
		}
		if (badMoves(20)) {
			miniscore *= 1.5;
		}
		return reduceWhenKills(serene(miniscore));
	}

	private double poison() {
		if (!canStatus(o, "psn") || hydration(o) || secondaryNegated()) {
			return none();
		}
		double miniscore = 1.2;
		int stages = o.stages[1] + o.stages[3] + o.stages[6];
		if (stages > 0) {
			miniscore *= 1 + 0.05 * stages;
		}
		if (function(m.id) == 0x06 && memoryHealing(o)) {
			miniscore *= 2;
		}
		String ability = o.ability();
		miniscore *= ability.equals("naturalcure") ? 0.3 : ability.equals("marvelscale") ? 0.7
				: Set.of("toxicboost", "guts", "quickfeet").contains(ability) ? 0.2 : Set.of("poisonheal", "magicguard").contains(ability) ? 0.1
				: ability.equals("shedskin") ? 0.7 : 1;
		if (ability.equals("sturdy") && damaging()) {
			miniscore *= 1.1;
		}
		if (ability.equals("synchronize") && a.status().isEmpty() && !a.hasType("poison") && !a.hasType("steel")) {
			miniscore *= 0.5;
		}
		if (memory("facade")) {
			miniscore *= 0.2;
		}
		if (memory("rest")) {
			miniscore *= 0.1;
		}
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
			miniscore *= 1.5;
		}
		if (badMoves(30)) {
			miniscore *= 1.2;
		}
		if (a.hasMove("venoshock") || a.hasMove("venomdrench") || a.ability().equals("merciless")) {
			miniscore *= 1.6;
		}
		if (o.has("yawn")) {
			miniscore *= 0.4;
		}
		return reduceWhenKills(serene(miniscore));
	}

	private double paralysis() {
		if (!canStatus(o, "par") || hydration(o) || secondaryNegated()) {
			return none();
		}
		if (m.id.equals("thunderwave") && typeMod(m, a, o) == 0) {
			return none();
		}
		double miniscore = 1.0;
		if (anySetup(a)) {
			miniscore *= 1.1;
		}
		if (o.hp >= o.maxHp) {
			miniscore *= 1.2;
		}
		int stages = o.stages[0] + o.stages[2] + o.stages[4];
		if (stages > 0) {
			miniscore *= 1 + 0.05 * stages;
		}
		String ability = o.ability();
		miniscore *= ability.equals("naturalcure") ? 0.3 : ability.equals("marvelscale") ? 0.5
				: ability.equals("guts") || ability.equals("quickfeet") ? 0.2 : ability.equals("shedskin") ? 0.7 : 1;
		if (ability.equals("synchronize") && a.status().isEmpty() && !a.hasType("electric")) {
			miniscore *= 0.5;
		}
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall") || a.roles.contains("pivot")) {
			miniscore *= 1.2;
		}
		if (a.roles.contains("tank")) {
			miniscore *= 1.3;
		}
		boolean overtakes = rough(o, Stats.SPEED) / 2 < a.speed();
		if (!faster() && overtakes && !trickRoom) {
			miniscore *= 1.2;
		} else if (faster() && overtakes && trickRoom) {
			miniscore *= 0.7;
		}
		if (rough(o, Stats.SPECIAL_ATTACK) > rough(o, Stats.ATTACK)) {
			miniscore *= 1.1;
		}
		if (partyRole("sweeper")) {
			miniscore *= 1.1;
		}
		if (o.has("confusion")) {
			miniscore *= 1.1;
		}
		if (o.has("attract")) {
			miniscore *= 1.1;
		}
		if (o.has("yawn")) {
			miniscore *= 0.4;
		}
		miniscore = reduceWhenKills(serene(miniscore));
		if (!faster() && overtakes && !trickRoom && badMoves(40) && effectChance(m.id) == 100) {
			miniscore += 25;
		}
		return miniscore;
	}

	private double burn() {
		if (!canStatus(o, "brn") || hydration(o) || secondaryNegated()) {
			return none();
		}
		double miniscore = 1.2;
		int stages = o.stages[0] + o.stages[2] + o.stages[4];
		if (stages > 0) {
			miniscore *= 1 + 0.05 * stages;
		}
		String ability = o.ability();
		miniscore *= ability.equals("naturalcure") ? 0.3 : ability.equals("marvelscale") ? 0.7
				: ability.equals("guts") || ability.equals("flareboost") ? 0.1 : ability.equals("shedskin") ? 0.7
				: ability.equals("magicguard") ? 0.5 : ability.equals("quickfeet") ? 0.3 : 1;
		if (ability.equals("synchronize") && a.status().isEmpty() && !a.hasType("fire")) {
			miniscore *= 0.5;
		}
		if (ability.equals("sturdy") && damaging()) {
			miniscore *= 1.1;
		}
		if (memory("rest")) {
			miniscore *= 0.1;
		}
		if (memory("facade")) {
			miniscore *= 0.3;
		}
		if (rough(o, Stats.ATTACK) > rough(o, Stats.SPECIAL_ATTACK)) {
			miniscore *= 1.4;
		}
		if (o.has("yawn")) {
			miniscore *= 0.4;
		}
		return reduceWhenKills(serene(miniscore));
	}

	private double freeze() {
		if (!canStatus(o, "frz") || hydration(o) || secondaryNegated()) {
			return none();
		}
		double miniscore = 1.2;
		if (memory(UNFREEZE)) {
			miniscore = 0;
		}
		if (anySetup(a)) {
			miniscore *= 1.2;
		}
		if (memoryHealing(o)) {
			miniscore *= 1.2;
		}
		int stages = stageSum(o, 0);
		if (stages > 0) {
			miniscore *= 1 + 0.05 * stages;
		}
		if (o.ability().equals("naturalcure")) {
			miniscore *= 0.3;
		}
		if (o.ability().equals("marvelscale")) {
			miniscore *= 0.8;
		}
		return reduceWhenKills(serene(miniscore));
	}

	private double flinch() {
		if (o.has("substitute") || o.ability().equals("innerfocus") || secondaryNegated() || !fasterWith()) {
			return none();
		}
		double miniscore = 1.0;
		if (!greatMoves()) {
			miniscore *= 1.3;
		}
		if (trickRoom && a.speed() > rough(o, Stats.SPEED)) {
			miniscore *= 1.3;
		}
		Map<String, Integer> theirs = conditions(false);
		if (theirs.containsKey("reflect") || theirs.containsKey("lightscreen") || theirs.containsKey("auroraveil")) {
			miniscore *= 1.3;
		}
		if (theirs.containsKey("tailwind")) {
			miniscore *= 1.2;
		}
		boolean chipped = o.status().equals("psn") || o.status().equals("tox") || o.status().equals("brn")
				|| weather.equals("hail") && !o.hasType("ice")
				|| weather.equals("sandstorm") && !o.hasType("rock") && !o.hasType("ground") && !o.hasType("steel")
				|| o.has("leechseed") || o.has("curse");
		if (chipped) {
			miniscore *= 1.1;
			if (o.status().equals("tox")) {
				miniscore *= 1.2;
			}
		}
		if (o.ability().equals("steadfast")) {
			miniscore *= 0.3;
		}
		return reduceWhenKills(serene(miniscore));
	}

	private double thunderBoost() {
		double miniscore = 1.0;
		String charge = charging.get(o.battler.getUuid());
		if (charge != null && Set.of("fly", "bounce", "skydrop").contains(charge) && faster()) {
			miniscore *= 2;
		}
		if (!faster() && memory(Set.of("bounce", "fly", "skydrop"))) {
			miniscore *= 1.2;
		}
		return miniscore;
	}

	private double confuse() {
		if (!canConfuse(o) || secondaryNegated()) {
			return none();
		}
		double miniscore = 1.0;
		if (!greatMoves()) {
			miniscore *= 1.2;
		}
		if (o.stages[0] > 0) {
			miniscore *= 1 + 0.1 * o.stages[0];
		}
		if (rough(o, Stats.ATTACK) > rough(o, Stats.SPECIAL_ATTACK)) {
			miniscore *= 1.2;
		}
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
			miniscore *= 1.3;
		}
		if (o.has("attract")) {
			miniscore *= 1.1;
		}
		if (o.status().equals("par")) {
			miniscore *= 1.1;
		}
		if (o.ability().equals("tangledfeet")) {
			miniscore *= 0.7;
		}
		if (a.hasMove("substitute")) {
			miniscore *= 1.2;
			if (a.has("substitute")) {
				miniscore *= 1.3;
			}
		}
		if (badMoves(40)) {
			miniscore *= 1.4;
		}
		return reduceWhenKills(serene(miniscore));
	}

	private double attract() {
		var mine = a.battler.getEffectedPokemon().getGender();
		var theirs = o.battler.getEffectedPokemon().getGender();
		if (mine == theirs || mine.name().equals("GENDERLESS") || theirs.name().equals("GENDERLESS") || o.has("attract")
				|| (Set.of("oblivious", "aromaveil").contains(o.ability()) && !moldBreaker(a))) {
			return 0;
		}
		double miniscore = 1.2;
		if (a.ability().equals("cutecharm")) {
			miniscore *= 0.7;
		}
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
			miniscore *= 1.3;
		}
		if (o.has("confusion")) {
			miniscore *= 1.1;
		}
		if (o.status().equals("par")) {
			miniscore *= 1.1;
		}
		if (o.has("yawn") || o.status().equals("slp")) {
			miniscore *= 0.5;
		}
		if (o.item().equals("destinyknot")) {
			miniscore *= 0.1;
		}
		if (a.hasMove("substitute")) {
			miniscore *= 1.2;
			if (a.has("substitute")) {
				miniscore *= 1.3;
			}
		}
		return miniscore;
	}

	private double refresh() {
		if (!Set.of("brn", "psn", "tox", "par").contains(a.status())) {
			return 0;
		}
		double miniscore = 3;
		miniscore *= a.hp / a.maxHp > 0.5 ? 1.5 : 0.3;
		if (o.has("yawn")) {
			miniscore *= 0.1;
		}
		if (expectedDamage(a, o) > a.hp) {
			miniscore *= 0.1;
		}
		if (memory("hex")) {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	private double partyRefresh() {
		boolean any = false;
		double miniscore = 1.2;
		for (BattlePokemon pokemon : a.battler.getActor().getPokemonList()) {
			var status = pokemon.getEffectedPokemon().getStatus();
			if (pokemon.getHealth() <= 0 || status == null) {
				continue;
			}
			any = true;
			String name = status.getStatus().getShowdownName();
			String ability = ExpertBattleAI.id(pokemon.getEffectedPokemon().getAbility() == null ? "" : pokemon.getEffectedPokemon().getAbility().getName());
			Set<String> roles = roles(pokemon);
			if ((name.equals("psn") || name.equals("tox")) && ability.equals("poisonheal")) {
				miniscore *= 0.5;
			}
			if (ability.equals("guts") || ability.equals("quickfeet")) {
				miniscore *= 0.8;
			}
			if (name.equals("slp") || name.equals("frz")) {
				miniscore *= 1.1;
			}
			if ((roles.contains("physicalwall") || roles.contains("specialwall")) && (name.equals("psn") || name.equals("tox"))) {
				miniscore *= 1.2;
			}
			if (roles.contains("sweeper") && name.equals("par")) {
				miniscore *= 1.2;
			}
			if (name.equals("brn") && pokemon.getEffectedPokemon().getAttack() > pokemon.getEffectedPokemon().getSpecialAttack()) {
				miniscore *= 1.2;
			}
		}
		if (!any) {
			return 0;
		}
		if (!a.status().isEmpty()) {
			miniscore *= 1.3;
		}
		if (memoryHealing(o)) {
			miniscore *= 1.1;
		}
		return miniscore;
	}

	private double psychoShift() {
		String status = a.status();
		if (status.isEmpty() || !o.status().isEmpty() || o.has("substitute") || o.has("yawn")) {
			return 0;
		}
		String as = status.equals("tox") ? "psn" : status;
		if (Set.of("brn", "par", "psn").contains(as) && !canStatus(o, as)) {
			return 0;
		}
		double miniscore = 1.69;
		if (as.equals("brn")) {
			if (rough(o, Stats.ATTACK) > rough(o, Stats.SPECIAL_ATTACK)) {
				miniscore *= 1.2;
			}
			if (o.ability().equals("flareboost")) {
				miniscore *= 0.7;
			}
		}
		if (as.equals("par")) {
			if (rough(o, Stats.ATTACK) < rough(o, Stats.SPECIAL_ATTACK)) {
				miniscore *= 1.1;
			}
			if (fasterWith()) {
				miniscore *= 1.2;
			}
		}
		if (as.equals("psn")) {
			if (memoryHealing(o)) {
				miniscore *= 1.1;
			}
			if (status.equals("tox")) {
				miniscore *= 1.4;
			}
			if (o.ability().equals("poisonheal")) {
				miniscore *= 0.3;
			}
			if (o.ability().equals("toxicboost")) {
				miniscore *= 0.7;
			}
		}
		if (Set.of("shedskin", "naturalcure", "guts", "quickfeet", "marvelscale").contains(o.ability())) {
			miniscore *= 0.7;
		}
		if (memory("facade")) {
			miniscore *= 0.7;
		}
		if (memory("hex")) {
			miniscore *= 1.3;
		}
		if (a.hasMove("hex")) {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	// ------------------------------------------------ stats

	/** Reborn's {@code selfstatboost}: stats in Reborn's order (attack, defence, sp. attack, sp. defence, speed, accuracy, evasion). */
	private double selfBoost(int... boosts) {
		int[] stats = boosts.clone();
		boolean physical = a.moves.stream().anyMatch(x -> !x.status && x.physical);
		boolean special = a.moves.stream().anyMatch(x -> !x.status && !x.physical);
		for (int i = 0; i < 7; i++) {
			if (stats[i] == 0) {
				continue;
			}
			if (a.ability().equals("simple")) {
				stats[i] *= 2;
			}
			stats[i] = Math.min(6 - a.stages[i], stats[i]);
		}
		if (stats[0] != 0 || stats[2] != 0) {
			if (!physical) {
				stats[0] = 0;
			}
			if (!special) {
				stats[2] = 0;
			}
		}
		boolean allZero = true;
		for (int value : stats) {
			allZero &= value == 0;
		}
		if (allZero && function(m.id) != 0x37) {
			return none();
		}
		double miniscore = 1.0;
		int boosted = 0;
		if (!damaging()) {
			for (int value : stats) {
				boosted += value;
			}
			miniscore = boosted;
			if ((stats[0] > 0 || stats[2] > 0) && stats[4] > 0) {
				miniscore *= 1.8;
			} else if (stats[0] > 1 || stats[2] > 1) {
				miniscore *= 1.5;
			} else if ((stats[0] > 0 || stats[2] > 0) && (stats[1] > 0 || stats[3] > 0)) {
				miniscore *= 1.5;
			} else if (stats[1] > 0 && stats[3] > 0) {
				miniscore *= 1.5;
			}
		}
		boolean sweep = false;
		boolean tank = false;
		for (int i = 0; i < 7; i++) {
			if (stats[i] == 0) {
				continue;
			}
			switch (i) {
				case 0 -> {
					if (!physical) {
						continue;
					}
					sweep = true;
					if (memoryHealing(o)) {
						miniscore *= 1.3;
					}
					if (faster() || a.moves.stream().anyMatch(x -> x.priority > 0 && x.physical && !x.status)) {
						miniscore *= 1.5;
					}
					if (a.status().equals("brn") && !a.ability().equals("guts")) {
						miniscore *= 0.5;
					}
					if (memory("foulplay")) {
						miniscore *= 0.3;
					}
					if (notOHKO(a, o)) {
						miniscore *= 1.4;
					}
					if (memoryPriority(o)) {
						miniscore *= 0.6;
					}
					if (o.ability().equals("speedboost")) {
						miniscore *= 0.6;
					}
				}
				case 1, 3 -> {
					tank = true;
					if (i == 3 && o.status().equals("brn")) {
						miniscore *= 1.1;
					}
					boolean matches = i == 1 ? rough(o, Stats.SPECIAL_ATTACK) < rough(o, Stats.ATTACK)
							: rough(o, Stats.SPECIAL_ATTACK) > rough(o, Stats.ATTACK);
					if (matches) {
						if (!(a.roles.contains("physicalwall") || a.roles.contains("specialwall"))) {
							if (fasterWith() && a.hp / a.maxHp > 0.75) {
								miniscore *= 1.3;
							} else if (!fasterWith()) {
								miniscore *= 0.7;
							}
						}
						miniscore *= 1.3;
					}
				}
				case 2 -> {
					sweep = true;
					if (memoryHealing(o)) {
						miniscore *= 1.3;
					}
					if (faster() || a.moves.stream().anyMatch(x -> x.priority > 0 && !x.physical && !x.status)) {
						miniscore *= 1.5;
					}
					if (a.status().equals("par")) {
						miniscore *= 0.5;
					}
					if (notOHKO(a, o)) {
						miniscore *= 1.4;
					}
					if (memoryPriority(o)) {
						miniscore *= 0.6;
					}
				}
				case 4 -> {
					sweep = true;
					if (faster()) {
						miniscore *= 0.8;
						if (boosted == stats[4]) {
							miniscore *= 0.2;
						}
					}
					if (trickRoom || memory("trickroom")) {
						miniscore *= 0.2;
					}
					boolean slowHits = false;
					for (Mv move : a.moves) {
						if (!move.status && move.priority < 1 && roughDamage(move, a, o) * 100 / Math.max(1, o.hp) > 10) {
							slowHits = true;
						}
					}
					if (slowHits) {
						if (physical && Set.of("moxie", "chillingneigh", "asonechilling").contains(a.ability())
								|| special && Set.of("soulheart", "grimneigh", "asonegrim").contains(a.ability())) {
							miniscore *= 1.5;
						}
						if (a.stages[2] < 0) {
							miniscore *= 1 + 0.05 * a.stages[2];
						}
						int defences = o.stages[1] + o.stages[3];
						if (defences > 0) {
							miniscore *= 1 - 0.05 * defences;
						}
					}
				}
				case 5 -> {
					for (Mv move : a.moves) {
						if (move.power < 95) {
							miniscore *= 1.1;
						}
					}
					if (o.stages[6] > 0) {
						miniscore *= 1 + 0.05 * o.stages[6];
					}
					if (o.item().equals("brightpowder") || o.item().equals("laxincense")) {
						miniscore *= 1.1;
					}
				}
				case 6 -> {
					tank = true;
					if (o.ability().equals("noguard") || a.ability().equals("noguard") || memoryOf(o).stream().anyMatch(x -> x.accuracy == 0)) {
						miniscore *= 0.2;
					}
					if (a.item().equals("brightpowder") || a.item().equals("laxincense")) {
						miniscore *= 1.3;
					}
				}
				default -> {
				}
			}
		}
		if (a.has("substitute") || a.ability().equals("disguise")) {
			miniscore *= 1.3;
		}
		if (o.has("substitute") || o.ability().equals("disguise") && !moldBreaker(a)) {
			miniscore *= 0.5;
		}
		if (o.status().equals("slp") || o.status().equals("frz")) {
			miniscore *= 1.3;
		}
		if (badMoves(20)) {
			miniscore *= 1.3;
		}
		double hpShare = a.hp / a.maxHp;
		if (hpShare > 0.5 && hpShare < 0.75 && (a.ability().equals("emergencyexit") || a.ability().equals("wimpout") || a.item().equals("ejectbutton"))) {
			miniscore *= 0.3;
		} else if (hpShare < 0.33 && !damaging()) {
			miniscore *= 0.3;
		}
		Object[] best = bestMove(o, a);
		double maxDamage = best == null ? 0 : (double) best[1];
		Mv bestMove = best == null ? null : (Mv) best[0];
		if (maxDamage < a.hp / 4 && sweep) {
			miniscore *= 1.2;
		} else if (maxDamage < a.hp / 3 && tank) {
			miniscore *= 1.1;
		} else if (maxDamage < a.hp / 4 && stats[1] != 0 && stats[3] != 0) {
			miniscore *= 1.5;
		} else if (maxDamage < a.hp / 2) {
			miniscore *= 1.1;
		} else if (!damaging()) {
			miniscore *= 0.8;
			boolean outspeeds = false;
			for (Mv move : a.moves) {
				outspeeds |= !move.status && faster(move, bestMove, a, o);
			}
			if (!outspeeds) {
				miniscore *= 0.3;
			}
			if (maxDamage > a.hp && !a.has("substitute") && !a.ability().equals("disguise")) {
				miniscore *= 0.1;
			}
		}
		if (bestMove != null && maxDamage * 2 > a.hp + (hpGain(a) - 1) * a.maxHp && (stats[0] == 1 || stats[2] == 1)
				&& (bestMove.physical && stats[1] == 0 || !bestMove.physical && stats[3] == 0) && !damaging()) {
			miniscore *= 0.4;
		}
		if (o.mon.level - 10 > a.mon.level) {
			miniscore *= 0.6;
			if (o.mon.level - 15 > a.mon.level) {
				miniscore *= 0.2;
			}
		}
		if (memory("snatch")) {
			miniscore *= 0.3;
		}
		if (sweep) {
			miniscore *= 1 + 0.05 * (o.stages[0] + o.stages[2] + o.stages[4]);
			if (a.stages[4] < 0 && stats[4] == 0) {
				miniscore *= 1 + 0.05 * a.stages[4];
			}
			if (hpShare > 0.75) {
				miniscore *= 1.2;
			}
			if (a.turncount() < 2) {
				miniscore *= 1.2;
			}
			if (!o.status().isEmpty()) {
				miniscore *= 1.2;
			}
			if (o.has("encore") && encoredStatus()) {
				miniscore *= 1.5;
			}
			if (a.has("leechseed") || a.has("attract")) {
				miniscore *= 0.6;
			}
			if (memory(PHASE)) {
				miniscore *= 0.5;
			}
			if (a.roles.contains("sweeper")) {
				miniscore *= 1.3;
			}
			if (a.status().equals("par")) {
				miniscore *= 0.5;
				if (stats[4] != 0) {
					miniscore *= 0.5;
				}
			}
			if (a.status().isEmpty() && !a.hasType("electric") && memory(PARA_MOVES) && !damaging()) {
				miniscore *= 0.5;
			}
			if (a.status().equals("psn") && !a.ability().equals("poisonheal")) {
				miniscore *= 0.7;
			}
			if (a.status().equals("tox") && !a.ability().equals("poisonheal")) {
				miniscore *= 0.6;
			}
			if (memoryPriority(o)) {
				miniscore *= 0.6;
			}
			if (o.ability().equals("speedboost")) {
				miniscore *= 0.6;
			}
			if (notOHKO(a, o)) {
				miniscore *= 1.4;
			}
		} else {
			if (hpShare > 0.75) {
				miniscore *= 1.1;
			}
			if (a.turncount() < 2) {
				miniscore *= 1.1;
			}
			if (!o.status().isEmpty()) {
				miniscore *= 1.1;
			}
			if (o.has("encore") && encoredStatus()) {
				miniscore *= 1.3;
				if (stats[1] != 0 && stats[3] != 0) {
					miniscore *= 1.2;
				}
			}
			if (memory(PHASE)) {
				miniscore *= 0.2;
			}
			if (a.status().equals("psn") && !a.ability().equals("poisonheal")) {
				miniscore *= 0.7;
			}
			if (a.status().equals("tox") && !a.ability().equals("poisonheal")) {
				miniscore *= 0.2;
			}
			if (a.has("leechseed") || a.has("attract")) {
				miniscore *= 0.3;
			}
		}
		if (tank) {
			if (a.stages[3] > 0 || a.stages[1] > 0) {
				int stages = (stats[3] != 0 ? a.stages[3] : 0) + (stats[1] != 0 ? a.stages[1] : 0);
				miniscore *= 1 - 0.05 * stages;
			}
			if (a.hasMove("leechseed")) {
				miniscore *= 1.3;
			}
			if (a.hasMove("painsplit")) {
				miniscore *= 1.2;
			}
			if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
				miniscore *= 1.2;
			}
			miniscore *= 1.2 * hpGain(a);
			if (expectedDamage(a, o) < 0.12 * a.hp && !memoryOf(o).isEmpty()) {
				miniscore *= 0.3;
			}
		}
		if (a.has("confusion")) {
			if (stats[0] != 0) {
				miniscore *= 0.2;
				if (stats[0] > 1) {
					miniscore *= 0.5;
				}
				if (stats[1] != 0) {
					miniscore *= 1.5;
				}
			} else {
				miniscore *= 0.5;
			}
		}
		if (memory(Set.of("clearsmog", "haze", "topsyturvy"))) {
			miniscore *= 0.2;
		}
		if (o.has("mustrecharge")) {
			miniscore *= 1.3;
		}
		if (o.has("yawn")) {
			miniscore *= 1.7;
		}
		if (a.hasMove("storedpower")) {
			miniscore *= 2;
		}
		if (a.hasMove("batonpass")) {
			miniscore *= 1.2;
		}
		if (damaging()) {
			if (initial[index] >= 100) {
				miniscore *= 1.2;
			} else if (greatMoves()) {
				miniscore *= 0.5;
			}
			if (o.ability().equals("unaware") || miniscore < 1) {
				miniscore = 1;
			}
			miniscore = serene(miniscore);
		} else {
			if (a.ability().equals("contrary")) {
				miniscore = 0;
			}
			if (o.ability().equals("unaware")) {
				miniscore *= 0.01;
			}
		}
		return miniscore;
	}

	/** The opponent is locked by Encore into a status move (its last move). */
	private boolean encoredStatus() {
		String last = lastMove.get(o.battler.getUuid());
		MoveTemplate template = last == null ? null : com.cobblemon.mod.common.api.moves.Moves.getByName(last);
		return template != null && ExpertBattleAI.isStatus(template);
	}

	/** Reborn's {@code selfstatdrop} (attack, defence, sp. attack, sp. defence, speed). */
	private double selfDrop(double score, int... drops) {
		int[] stats = drops.clone();
		boolean any = false;
		for (int i = 0; i < stats.length; i++) {
			if (a.stages[i] <= -6) {
				stats[i] = 0;
			}
			any |= stats[i] != 0;
		}
		if (!any) {
			return 1.0;
		}
		double miniscore = 1.0;
		if (stats[0] != 0) {
			if (score < 100) {
				miniscore *= 0.9;
				if (!faster()) {
					miniscore *= 1.1;
				} else if (memoryHealing(o)) {
					miniscore *= 0.5;
				}
			}
		} else if (stats[1] != 0 || stats[3] != 0) {
			if (score < 100) {
				miniscore *= 0.9;
				if (stats[4] != 0) {
					miniscore *= 0.9;
				}
				if (!faster()) {
					miniscore *= 1.1;
				} else if (memoryHealing(o)) {
					miniscore *= 0.6;
				}
				if (memoryPriority(o)) {
					miniscore *= 0.7;
				}
			}
		} else if (stats[4] != 0) {
			if (score < 100) {
				miniscore *= 0.9;
			}
			if (a.roles.contains("tank")) {
				miniscore *= 1.1;
			}
			if (faster()) {
				miniscore *= 0.8;
				if (alive(o) > 1 && alive(a) == 1) {
					miniscore *= 0.8;
				}
			}
		} else if (stats[2] != 0) {
			if (score < 100) {
				miniscore *= 0.9;
				if (memoryHealing(o)) {
					miniscore *= 0.5;
				}
			}
		}
		if (greatMoves()) {
			miniscore *= 0.6;
		}
		int liveCount = alive(o);
		if (liveCount > 1) {
			miniscore *= 1 - 0.05 * (liveCount - 3);
		}
		if (partyRole("pivot")) {
			miniscore *= 1.2;
		}
		if (liveCount > 1 && alive(a) == 1) {
			miniscore *= 0.8;
		}
		if (miniscore < 1 && liveCount == 1 && score >= 100) {
			miniscore = 1;
		}
		return miniscore;
	}

	/** Reborn's {@code oppstatdrop}. */
	private double oppDrop(int... drops) {
		if (damaging() && initial[index] >= 100) {
			return 1;
		}
		if (o.ability().equals("clearbody") || o.ability().equals("whitesmoke") || o.ability().equals("fullmetalbody")) {
			return none();
		}
		int[] stats = drops.clone();
		for (int i = 0; i < 7; i++) {
			if (stats[i] == 0) {
				continue;
			}
			if (o.stages[i] <= -6 || o.has("mist")) {
				stats[i] = 0;
			}
			if (!damaging() && o.stages[i] > stats[i]) {
				stats[i] = 0;
			}
		}
		if (stats[1] > 0 || stats[3] > 0) {
			boolean physical = a.moves.stream().anyMatch(x -> !x.status && x.physical);
			boolean special = a.moves.stream().anyMatch(x -> !x.status && !x.physical);
			if (!physical) {
				stats[1] = 0;
			}
			if (!special) {
				stats[3] = 0;
			}
		}
		if (stats[0] > 0 || stats[2] > 0) {
			Mv best = expectedMove(o, a);
			if (best != null) {
				if (rough(o, Stats.ATTACK) * 0.9 > rough(o, Stats.SPECIAL_ATTACK) && best.physical) {
					stats[2] = 0;
				}
				if (rough(o, Stats.SPECIAL_ATTACK) * 0.9 > rough(o, Stats.ATTACK) && !best.physical) {
					stats[0] = 0;
				}
			}
		}
		if (stats[4] > 0 && (faster() && !trickRoom || trickRoom)) {
			stats[4] = 0;
		}
		boolean any = false;
		int dropped = 0;
		for (int value : stats) {
			any |= value != 0;
			dropped += value;
		}
		if (!any) {
			return none();
		}
		double miniscore = damaging() ? 1.0 : dropped;
		if (stats[1] > 0 || stats[3] > 0) {
			miniscore *= 1.1;
			if (expectedDamage(a, o) < a.hp) {
				miniscore *= 1.2;
			}
			if (function(m.id) == 0x4C) {
				miniscore *= 1.5;
			}
		} else if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
			if (stats[0] > 0 || stats[2] > 0 || stats[5] > 0) {
				miniscore *= 1.3;
			}
			if (stats[4] > 0) {
				miniscore *= 1.1;
			}
		}
		boolean contrarian = Set.of("competitive", "defiant", "contrary").contains(o.ability());
		if (stats[4] > 0) {
			if (rough(o, Stats.SPEED) * 0.66 > a.speed()) {
				miniscore *= greatMoves() ? 1 : 1.5;
			}
			if (o.stages[4] < 0 && !secondaryNegated()) {
				miniscore *= 1 + 0.05 * o.stages[4];
			}
			if (o.item().equals("laggingtail") || o.item().equals("ironball")) {
				miniscore *= 0.1;
			}
			if (contrarian) {
				miniscore *= 0.2;
			}
		} else {
			if (contrarian) {
				miniscore *= 0.1;
			}
			if (partyRole("sweeper")) {
				miniscore *= 1.1;
			}
		}
		if ((stats[1] > 0 || stats[2] > 0 || stats[3] > 0) && (Set.of("psn", "tox", "brn").contains(o.status()))) {
			miniscore *= 1.2;
		}
		if (stats[0] > 0) {
			if (o.status().equals("psn") || o.status().equals("tox")) {
				miniscore *= 1.2;
			}
			if (o.status().equals("brn")) {
				miniscore *= 0.5;
			}
			if (a.hasMove("foulplay")) {
				miniscore *= 0.5;
			}
		}
		if (stats[4] > 0) {
			if (a.hasMove("gyroball")) {
				miniscore *= 0.5;
			}
			if (a.hasMove("electroball")) {
				miniscore *= 1.5;
			}
			if (memory("electroball")) {
				miniscore *= 1.3;
			}
			if (memory("gyroball")) {
				miniscore *= 0.5;
			}
			if (trickRoom || memory("trickroom")) {
				miniscore *= 0.1;
			}
		}
		if (alive(o) == 1 || Set.of("arenatrap", "shadowtag").contains(a.ability()) || a.ability().equals("magnetpull") && o.hasType("steel")
				|| o.has("trapped")) {
			miniscore *= 1.2;
		}
		if (a.roles.contains("phazer")) {
			miniscore *= 0.5;
		}
		if (greatMoves()) {
			miniscore *= 0.8;
		}
		if (damaging()) {
			miniscore = serene(miniscore);
		} else {
			if (alive(a) == 1) {
				miniscore *= 0.5;
			}
			if (!a.status().isEmpty()) {
				miniscore *= 0.7;
			}
		}
		return miniscore;
	}

	private double focusEnergy() {
		if (a.has("focusenergy")) {
			return 0;
		}
		double miniscore = 1.0;
		double hpShare = a.hp / a.maxHp;
		if (hpShare > 0.75) {
			miniscore *= 1.2;
		} else if (hpShare > 0.5 && hpShare < 0.75 && (a.ability().equals("emergencyexit") || a.ability().equals("wimpout") || a.item().equals("ejectbutton"))) {
			miniscore *= 0.3;
		} else if (hpShare < 0.33) {
			miniscore *= 0.3;
		}
		if (o.has("mustrecharge")) {
			miniscore *= 1.3;
		}
		if (o.has("yawn")) {
			miniscore *= 1.7;
		}
		if (a.has("leechseed") || a.has("attract")) {
			miniscore *= 0.6;
		}
		if (memory(PHASE)) {
			miniscore *= 0.5;
		}
		if (a.has("confusion")) {
			miniscore *= 0.2;
		}
		if (a.hp / 4 > expectedDamage(a, o)) {
			miniscore *= 1.2;
		}
		if (a.turncount() < 2) {
			miniscore *= 1.2;
		}
		if (!o.status().isEmpty()) {
			miniscore *= 1.2;
		}
		if (o.status().equals("slp") || o.status().equals("frz")) {
			miniscore *= 1.3;
		}
		if (a.ability().equals("superluck") || a.ability().equals("sniper")) {
			miniscore *= 2;
		}
		if (a.item().equals("scopelens") || a.item().equals("razorclaw")) {
			miniscore *= 1.2;
		}
		if (Set.of("angerpoint", "shellarmor", "battlearmor").contains(o.ability())) {
			miniscore *= 0.2;
		}
		if (a.hasMove("laserfocus") || a.hasMove("frostbreath") || a.hasMove("stormthrow")) {
			miniscore *= 0.5;
		}
		return miniscore * Math.pow(2, a.moves.stream().filter(Mv::highCrit).count());
	}

	// ------------------------------------------------ hazards, field

	private double defog() {
		double miniscore = 1.0;
		int mine = alive(a);
		int theirs = alive(o);
		Map<String, Integer> own = conditions(true);
		Map<String, Integer> foe = conditions(false);
		if (mine > 1) {
			miniscore *= (own.containsKey("stealthrock") ? 2 : 1) * (own.containsKey("stickyweb") ? 3 : 1)
					* Math.pow(1.5, own.getOrDefault("spikes", 0)) * Math.pow(1.7, own.getOrDefault("toxicspikes", 0));
		}
		miniscore -= 1;
		if (mine > 1) {
			miniscore *= mine - 1;
		}
		double mini = 1.0;
		if (theirs > 1) {
			mini *= (foe.containsKey("stealthrock") ? 0.5 : 1) * (foe.containsKey("stickyweb") ? 0.3 : 1)
					* Math.pow(0.7, foe.getOrDefault("spikes", 0)) * Math.pow(0.6, foe.getOrDefault("toxicspikes", 0));
		}
		mini -= 1;
		if (theirs > 1) {
			mini *= theirs - 1;
		}
		miniscore = Math.max(0, miniscore + mini + 1);
		miniscore *= (foe.containsKey("reflect") ? 2 : 1) * (foe.containsKey("lightscreen") ? 2 : 1)
				* (foe.containsKey("safeguard") ? 1.3 : 1) * (foe.containsKey("auroraveil") ? 3 : 1) * (foe.containsKey("mist") ? 1.3 : 1);
		return miniscore;
	}

	private double hazardRemoval() {
		if (reserves(a) == 0) {
			return 0;
		}
		Map<String, Integer> own = conditions(true);
		int spikes = own.getOrDefault("spikes", 0);
		int toxic = own.getOrDefault("toxicspikes", 0);
		double miniscore = 0;
		for (BattlePokemon pokemon : a.battler.getActor().getPokemonList()) {
			if (pokemon.getHealth() <= 0) {
				continue;
			}
			B mon = battler(pokemon, true, 0, false);
			if (mon.item().equals("heavydutyboots")) {
				continue;
			}
			if (own.containsKey("stickyweb") && mon.grounded() && !mon.ability().equals("contrary")) {
				miniscore += 1;
			}
			if (mon.ability().equals("magicguard")) {
				continue;
			}
			double rock = ExpertBattleAI.effectiveness(ElementalTypes.get("rock"), mon.mon, null);
			boolean hpMove = mon.hasMove("eruption") || mon.hasMove("waterspout") || mon.hasMove("dragonenergy") || mon.hasMove("finalgambit");
			boolean fullHpMatters = Set.of("multiscale", "sturdy", "galewings", "shadowshield", "terashell").contains(mon.ability())
					|| mon.item().equals("focussash");
			if (mon.hp >= mon.maxHp && fullHpMatters) {
				if (own.containsKey("stealthrock")) {
					miniscore += 2.5;
				}
				if (spikes > 0 && mon.grounded()) {
					miniscore += Math.pow(1.5, spikes);
				}
			}
			if (own.containsKey("stealthrock")) {
				miniscore += rock >= 4 ? 1.5 : rock >= 2 ? 1 : 0;
			}
			if (hpMove) {
				if (own.containsKey("stealthrock")) {
					miniscore += 1.3;
				}
				if (spikes > 0) {
					miniscore += Math.pow(1.3, spikes);
				}
			}
			if (mon.grounded() && mon.status().isEmpty() && toxic > 0 && !mon.hasType("poison") && !mon.hasType("steel")
					&& !Set.of("immunity", "poisonheal", "toxicboost", "comatose", "purifyingsalt", "guts").contains(mon.ability())) {
				miniscore += mon.moves.stream().anyMatch(x -> HEALING.contains(x.id)) ? Math.pow(1.8, toxic) : Math.pow(1.5, toxic);
			}
		}
		return miniscore;
	}

	private double hazard() {
		double miniscore = 1.0;
		if (a.roles.contains("lead")) {
			miniscore *= 1.1;
		}
		if (notOHKO(a, o)) {
			miniscore *= 1.3;
		}
		if (a.turncount() < 2) {
			miniscore *= 1.2;
		}
		if (a.stages[0] > 0 || a.stages[2] > 0 && !damaging()) {
			miniscore *= 0.9;
		}
		if (!damaging()) {
			int reserve = reserves(o);
			miniscore *= reserve > 2 ? 0.2 * reserve : 0.2;
		}
		boolean removal = false;
		for (Set<String> seen : memory.values()) {
			removal |= seen.contains("defog") || seen.contains("rapidspin") || seen.contains("courtchange") || seen.contains("tidyup")
					|| seen.contains("mortalspin");
		}
		if (removal && !damaging()) {
			miniscore *= 0.3;
		}
		return miniscore;
	}

	private boolean weatherSuppressed() {
		return Set.of("airlock", "cloudnine").contains(a.ability()) || Set.of("airlock", "cloudnine").contains(o.ability())
				|| Set.of("deltastream", "desolateland", "primordialsea").contains(weather);
	}

	private double weatherMove() {
		if (weatherSuppressed()) {
			return none();
		}
		double miniscore = 1.0;
		if (notOHKO(a, o)) {
			miniscore *= 1.3;
		}
		if (a.roles.contains("lead")) {
			miniscore *= 1.2;
		}
		if (a.hasMove("weatherball")) {
			miniscore *= 1.4;
		}
		if (a.ability().equals("forecast")) {
			miniscore *= 1.5;
		}
		return miniscore;
	}

	private boolean sunMoves(B b) {
		return b.hasMove("moonlight") || b.hasMove("synthesis") || b.hasMove("morningsun") || b.hasMove("growth") || b.hasMove("solarbeam")
				|| b.hasMove("solarblade");
	}

	private double sun() {
		if (weather.equals("sunnyday")) {
			return none();
		}
		double miniscore = 1.0;
		if (a.item().equals("heatrock")) {
			miniscore *= 1.3;
		}
		if (!weather.isEmpty()) {
			miniscore *= 1.5;
		}
		if (sunMoves(a)) {
			miniscore *= 1.5;
		}
		if (memory(Set.of("synthesis", "moonlight", "morningsun"))) {
			miniscore *= 0.7;
		}
		if (a.hasType("fire")) {
			miniscore *= 1.5;
		}
		if (a.ability().equals("chlorophyll") || a.ability().equals("flowergift")) {
			miniscore *= 2;
			if (notOHKO(a, o)) {
				miniscore *= 2;
			}
		}
		if (Set.of("solarpower", "leafguard").contains(a.ability())) {
			miniscore *= 1.3;
		}
		if (partyHasType("water")) {
			miniscore *= 0.5;
		}
		if (a.hasMove("thunder") || a.hasMove("hurricane")) {
			miniscore *= 0.7;
		}
		if (a.ability().equals("dryskin")) {
			miniscore *= 0.5;
		}
		if (a.ability().equals("harvest")) {
			miniscore *= 1.5;
		}
		return miniscore;
	}

	private double rain() {
		if (weather.equals("raindance")) {
			return 0;
		}
		double miniscore = 1.0;
		if (a.item().equals("damprock")) {
			miniscore *= 1.3;
		}
		if (!weather.isEmpty()) {
			miniscore *= 1.3;
		}
		if (a.hasMove("thunder") || a.hasMove("hurricane")) {
			miniscore *= 1.5;
		}
		if (a.hasType("water")) {
			miniscore *= 1.5;
		}
		if (a.ability().equals("swiftswim")) {
			miniscore *= 2;
			if (notOHKO(a, o)) {
				miniscore *= 2;
			}
		}
		if (a.ability().equals("dryskin")) {
			miniscore *= 1.5;
		}
		if (partyHasType("fire")) {
			miniscore *= 0.5;
		}
		if (sunMoves(a)) {
			miniscore *= 0.5;
		}
		if (a.ability().equals("hydration")) {
			miniscore *= 1.5;
		}
		return miniscore;
	}

	private double sand() {
		if (weather.equals("sandstorm")) {
			return 0;
		}
		double miniscore = 1.0;
		if (a.item().equals("smoothrock")) {
			miniscore *= 1.3;
		}
		if (!weather.isEmpty()) {
			miniscore *= 2;
		}
		miniscore *= a.hasType("rock") || a.hasType("ground") || a.hasType("steel") ? 1.3 : 0.7;
		if (a.hasType("rock")) {
			miniscore *= 1.5;
		}
		if (a.ability().equals("sandrush")) {
			miniscore *= 2;
			if (notOHKO(a, o)) {
				miniscore *= 2;
			}
		}
		if (a.ability().equals("sandveil")) {
			miniscore *= 1.3;
		}
		if (sunMoves(a)) {
			miniscore *= 0.5;
		}
		if (a.hasMove("shoreup")) {
			miniscore *= 1.5;
		}
		if (a.ability().equals("sandforce")) {
			miniscore *= 1.5;
		}
		return miniscore;
	}

	private double hail() {
		if (weather.equals("hail") || weather.equals("snow")) {
			return 0;
		}
		double miniscore = 1.0;
		if (a.item().equals("icyrock")) {
			miniscore *= 1.3;
		}
		if (!weather.isEmpty()) {
			miniscore *= 1.3;
		}
		miniscore *= a.hasType("ice") ? 5 : 0.7;
		if (a.ability().equals("slushrush")) {
			miniscore *= 2;
			if (notOHKO(a, o)) {
				miniscore *= 2;
			}
		}
		if (Set.of("snowcloak", "icebody").contains(a.ability())) {
			miniscore *= 1.3;
		}
		if (sunMoves(a)) {
			miniscore *= 0.5;
		}
		if (a.hasMove("auroraveil")) {
			miniscore *= 2;
		}
		if (a.hasMove("blizzard")) {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	private double electricTerrain() {
		if (terrain.equals("electricterrain")) {
			return 0;
		}
		double miniscore = 1;
		if (a.ability().equals("surgesurfer")) {
			miniscore *= 1.5;
		}
		if (a.hasType("electric")) {
			miniscore *= 1.3;
		}
		if (partyHasType("electric")) {
			miniscore *= 1.5;
		}
		if (o.hasType("electric")) {
			miniscore *= 0.5;
		}
		if (a.moves.stream().anyMatch(x -> function(x.id) == 0x03)) {
			miniscore *= 0.5;
		}
		if (memory(SLEEP_MOVES)) {
			miniscore *= 1.6;
		}
		return miniscore;
	}

	private double grassyTerrain() {
		if (terrain.equals("grassyterrain")) {
			return 0;
		}
		double miniscore = 1;
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
			miniscore *= 1.5;
		}
		if (a.hasType("fire")) {
			miniscore *= 1.5;
		}
		if (partyHasType("fire")) {
			miniscore *= 1.5;
		}
		if (o.hasType("fire")) {
			miniscore *= 0.5;
			if (!weather.equals("raindance")) {
				miniscore *= 0.5;
			}
			if (a.hasType("grass")) {
				miniscore *= 0.5;
			}
		} else if (a.hasType("grass")) {
			miniscore *= 1.5;
		}
		if (partyHasType("grass")) {
			miniscore *= 2;
		}
		if (memoryHealing(o)) {
			miniscore *= 0.5;
		}
		if (memory("sludgewave")) {
			miniscore *= 0.5;
		}
		if (a.ability().equals("grasspelt")) {
			miniscore *= 1.5;
		}
		return miniscore;
	}

	private double mistyTerrain() {
		if (terrain.equals("mistyterrain")) {
			return 0;
		}
		double miniscore = 1;
		if (partyHasType("fairy")) {
			miniscore *= 2;
		}
		if (!a.hasType("fairy") && o.hasType("dragon")) {
			miniscore *= 2;
		}
		if (a.hasType("dragon")) {
			miniscore *= 0.5;
		}
		if (o.hasType("fairy")) {
			miniscore *= 0.5;
		}
		if (a.hasType("fairy") && o.spatk() > o.attack()) {
			miniscore *= 2;
		}
		return miniscore;
	}

	private double psychicTerrain() {
		if (terrain.equals("psychicterrain")) {
			return 0;
		}
		double miniscore = 1;
		if (a.ability().equals("telepathy")) {
			miniscore *= 1.5;
		}
		if (a.hasType("psychic")) {
			miniscore *= 1.5;
		}
		if (partyHasType("psychic")) {
			miniscore *= 2;
		}
		if (o.hasType("psychic")) {
			miniscore *= 0.5;
		}
		if (a.ability().equals("anticipation")) {
			miniscore *= 1.5;
		}
		if (a.moves.stream().anyMatch(x -> isPriority(x, a)) && !a.grounded()) {
			miniscore *= 0.7;
		}
		if (memoryPriority(o) && o.grounded()) {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	private double tailwind() {
		if (conditions(true).containsKey("tailwind")) {
			return 0;
		}
		double miniscore = 1.5;
		if (faster() && !a.roles.contains("lead")) {
			miniscore *= 0.9;
			if (reserves(a) == 0) {
				miniscore *= 0.4;
			}
		}
		if (o.ability().equals("speedboost")) {
			miniscore *= 0.5;
		}
		if (trickRoom || memory("trickroom")) {
			miniscore *= 0.1;
		}
		if (a.roles.contains("lead")) {
			miniscore *= 1.4;
		}
		if (memory("snatch")) {
			miniscore *= 0.3;
		}
		return miniscore;
	}

	private double screen(int f) {
		Map<String, Integer> own = conditions(true);
		if (f == 0xA2 && own.containsKey("reflect") || f == 0xA3 && own.containsKey("lightscreen") || f == 0x15b && own.containsKey("auroraveil")) {
			return 0;
		}
		if (f == 0x15b && !(weather.equals("hail") || weather.equals("snow"))) {
			return 0;
		}
		double miniscore = 1.2;
		if (own.containsKey("auroraveil")) {
			miniscore *= 0.2;
		}
		double attack = rough(o, Stats.ATTACK);
		double special = rough(o, Stats.SPECIAL_ATTACK);
		if (f == 0xA2) {
			miniscore *= attack > special * 1.1 ? 1.3 : attack < special * 0.6 ? 0.5 : 0.9;
		} else if (f == 0xA3) {
			miniscore *= attack * 1.1 < special ? 1.3 : attack * 0.6 > special ? 0.5 : 0.9;
		}
		if (a.item().equals("lightclay")) {
			miniscore *= 1.1;
		}
		if (fasterWith()) {
			miniscore *= 1.1;
			double maxDamage = 0;
			for (Mv move : memoryOf(o)) {
				if (f == 0xA2 && !move.physical || f == 0xA3 && move.physical) {
					continue;
				}
				maxDamage = Math.max(maxDamage, roughDamage(move, o, a));
			}
			if (maxDamage > a.hp && maxDamage / 2 < a.hp) {
				miniscore *= 2;
			}
		}
		int liveCount = alive(o);
		if (liveCount <= 2) {
			miniscore *= 0.7;
			if (liveCount == 1) {
				miniscore *= 0.5;
			}
		} else if (a.item().equals("lightclay")) {
			miniscore *= 1.4;
		}
		if (notOHKO(a, o)) {
			miniscore *= 1.3;
		}
		if (memory(SCREEN_BREAKERS)) {
			miniscore *= 0.1;
		}
		if (memory("snatch")) {
			miniscore *= 0.3;
		}
		return miniscore;
	}

	// ------------------------------------------------ protection, disruption

	private int protectRate() {
		String last = lastMove.get(a.battler.getUuid());
		return last != null && (PROTECT.contains(last) || last.equals("endure")) ? 1 : 0;
	}

	private double protect() {
		if (o.ability().equals("unseenfist")) {
			return 0;
		}
		double miniscore = 0.6;
		if (trickRoom && !faster()) {
			miniscore *= 1.3;
		}
		Map<String, Integer> foe = conditions(false);
		if (foe.containsKey("reflect") || foe.containsKey("lightscreen") || foe.containsKey("auroraveil")) {
			miniscore *= 1.3;
		}
		if (foe.containsKey("tailwind")) {
			miniscore *= 1.2;
		}
		if (memory(SETUP)) {
			miniscore *= 0.3;
		}
		if (a.ability().equals("speedboost") && !faster() && !trickRoom) {
			miniscore *= 8;
			if (greatMoves()) {
				miniscore *= 6;
			}
		}
		double gain = hpGain(a);
		if (gain > 1) {
			miniscore *= 1.2 * gain;
		}
		if ((gain - 1) * a.maxHp - a.hp < 0 && (hpGain(o) - 1) * o.maxHp - o.hp > 0) {
			miniscore *= 0.1;
		}
		if (Set.of("psn", "tox", "brn").contains(o.status())) {
			miniscore *= 1.2;
			if (o.status().equals("tox")) {
				miniscore *= 1.3;
			}
		}
		if (Set.of("psn", "tox", "brn").contains(a.status())) {
			miniscore *= 0.7;
			if (a.status().equals("tox") && toxicTurns(a) > 1) {
				miniscore *= 0.3;
			}
		}
		if (o.has("leechseed")) {
			miniscore *= 1.3;
		}
		boolean perish = o.has("perish1") || o.has("perish2") || o.has("perish3");
		if (perish) {
			miniscore *= 4;
		}
		if (o.status().equals("slp") || o.status().equals("frz")) {
			miniscore *= 0.3;
		}
		String charge = charging.get(o.battler.getUuid());
		if (charge != null && TWO_TURN.contains(charge)) {
			miniscore *= 12;
			if (!faster()) {
				miniscore *= 1.5;
			}
		}
		if (memory(PROTECT_IGNORING)) {
			miniscore *= 0.2;
		}
		if (protectRate() > 0) {
			miniscore /= protectRate() * 2.0;
		}
		if (function(m.id) == 0x133 && memory(Set.of("willowisp", "thunderwave", "toxic"))) {
			miniscore *= 0.1;
		}
		return miniscore;
	}

	private double protectEffect() {
		double miniscore = protect();
		if (o.turncount() == 0) {
			miniscore *= 1.5;
		}
		if (memoryOf(o).stream().anyMatch(Mv::contact)) {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	/** The opponent's last move, as a move (Reborn's {@code lastMoveUsed}), or null. */
	private Mv opponentLast() {
		String last = lastMove.get(o.battler.getUuid());
		MoveTemplate template = last == null ? null : com.cobblemon.mod.common.api.moves.Moves.getByName(last);
		return template == null ? null : Mv.of(template);
	}

	private double torment() {
		if (o.has("torment") || o.ability().equals("aromaveil") && !moldBreaker(a)) {
			return 0;
		}
		Mv old = opponentLast();
		double miniscore = fasterWith() ? 1.2 : 0.7;
		if (old != null && !old.status) {
			miniscore *= 1.5;
			Object[] best = bestMove(o, a);
			if (best != null && ((Mv) best[0]).id.equals(old.id)) {
				miniscore *= 1.3;
				if ((double) best[1] * 3 < a.maxHp) {
					miniscore *= 1.5;
				}
			}
			if (hasProtect(a) && !o.ability().equals("unseenfist")) {
				miniscore *= 1.5;
			}
			if (hpGain(a) > 1) {
				miniscore *= 1.3;
			}
		} else {
			miniscore *= 0.5;
		}
		return miniscore;
	}

	private double disable() {
		if (o.has("disable") || o.ability().equals("aromaveil") && !moldBreaker(a)) {
			return 0;
		}
		Mv old = opponentLast();
		if (old == null && fasterWith()) {
			return 0;
		}
		double miniscore = old != null && faster(m, old, a, o) ? 1.2 : 0.7;
		if (old != null && (!old.status || HEALING.contains(old.id))) {
			miniscore *= 1.5;
			Object[] best = bestMove(o, a);
			if (best != null && ((Mv) best[0]).id.equals(old.id)) {
				miniscore *= 1.3;
				if ((double) best[1] * 3 < a.maxHp) {
					miniscore *= 1.5;
				}
			}
		} else {
			miniscore *= 0.5;
		}
		return miniscore;
	}

	private double taunt() {
		if (o.has("taunt") || (o.ability().equals("oblivious") || o.ability().equals("aromaveil")) && !moldBreaker(a)) {
			return none();
		}
		Mv old = opponentLast();
		double miniscore = 0.8;
		miniscore *= old != null && faster(m, old, a, o) ? 1.5 : 0.7;
		miniscore *= roles(o.battler).contains("lead") ? 1.2 : 0.8;
		miniscore *= o.turncount() <= 1 ? 1.1 : 0.9;
		if (old != null && HEALING.contains(old.id)) {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	private double encore() {
		if (o.has("encore") || o.ability().equals("aromaveil") && !moldBreaker(a)) {
			return 0;
		}
		Mv old = opponentLast();
		if (old == null) {
			return 0.2;
		}
		double miniscore = faster(m, old, a, o) || old.status ? 2.0 : 0.2;
		double damage = roughDamage(old, o, a);
		if (damage > a.hp) {
			miniscore *= 0.3;
		}
		if (damage * 4 > a.hp) {
			miniscore *= 0.3;
		} else if (o.stages[4] > 0) {
			miniscore *= o.hasType("dark") || !a.ability().equals("prankster") || o.ability().equals("speedboost") ? 0.5 : 2;
		} else {
			miniscore *= 2;
		}
		return miniscore;
	}

	private double outrage(double score) {
		if (a.ability().equals("owntempo")) {
			return 1.3;
		}
		double miniscore = 1.0;
		if (score < 100) {
			miniscore *= 0.85;
		}
		if (a.item().equals("lumberry") || a.item().equals("persimberry")) {
			miniscore *= 1.3;
		}
		if (a.stages[0] > 0) {
			miniscore *= 1 - 0.05 * a.stages[0];
		}
		int count = alive(a);
		if (count > 2) {
			miniscore *= 1 - 0.025 * count;
		}
		if (memory(PROTECT)) {
			miniscore *= 0.7;
		}
		if (memoryHealing(o)) {
			miniscore *= 0.7;
		}
		return miniscore;
	}

	// ------------------------------------------------ healing

	private double recover(double amount) {
		if (a.has("healblock")) {
			return 0;
		}
		double miniscore = 1.0;
		double recovered = Math.min(a.hp + amount, a.maxHp);
		Object[] best = bestMove(o, a);
		double maxDamage = best == null ? 0 : (double) best[1];
		if (maxDamage > amount) {
			miniscore *= 0.2;
		}
		if (best != null && maxDamage > 1.4 * amount && Set.of(0x1C, 0x20).contains(function(((Mv) best[0]).id))) {
			miniscore *= 0.6;
		}
		if (maxDamage > a.hp) {
			if (maxDamage > recovered) {
				return 0;
			}
			miniscore *= 5;
			if (amount > maxDamage && greatMoves()) {
				miniscore *= 6;
			}
		} else {
			if (maxDamage * 1.5 > a.hp) {
				miniscore *= 2;
			}
			if (!fasterWith() && maxDamage * 2 > a.hp) {
				miniscore *= 5;
				if (amount > maxDamage && greatMoves()) {
					miniscore *= 6;
				}
			}
		}
		if (alive(a) == 1) {
			miniscore *= 1.1;
		}
		if (alive(o) == 1 && greatMoves()) {
			miniscore *= 0.3;
		}
		if (memory(SETUP)) {
			miniscore *= 0.7;
		}
		double share = a.hp / a.maxHp;
		if (share < 0.5) {
			miniscore *= 1.5;
			if (a.has("curse")) {
				miniscore *= 2;
			}
			if (a.hp * 4 < a.maxHp) {
				if (a.status().equals("psn")) {
					miniscore *= 1.5;
				}
				if (a.has("leechseed")) {
					miniscore *= 2;
				}
				if (a.hp < a.maxHp * 0.13) {
					if (a.status().equals("brn")) {
						miniscore *= 2;
					}
					if (weather.equals("hail") && !a.hasType("ice")
							|| weather.equals("sandstorm") && !a.hasType("rock") && !a.hasType("ground") && !a.hasType("steel")) {
						miniscore *= 2;
					}
				}
			}
		} else {
			miniscore *= 0.9;
		}
		if (a.status().equals("tox")) {
			miniscore *= 0.5;
			if (toxicTurns(a) > 3) {
				miniscore *= 0.5;
			}
		}
		if (a.status().equals("par") || a.has("attract") || a.has("confusion")) {
			miniscore *= 1.1;
		}
		if (Set.of("psn", "tox", "brn").contains(o.status()) || o.has("leechseed") || o.has("curse")) {
			miniscore *= 1.3;
			if (o.status().equals("tox")) {
				miniscore *= 1.3;
			}
		}
		if (memory(CONTRARY_BAIT)) {
			miniscore *= 1.3;
		}
		if (charging.containsKey(o.battler.getUuid()) || o.has("mustrecharge")) {
			miniscore *= 1.2;
		}
		if (function(m.id) == 0xD7) {
			return miniscore;
		}
		if (share > 0.8) {
			miniscore = 0;
		} else if (share > 0.6) {
			miniscore *= 0.6;
		} else if (share < 0.25) {
			miniscore *= 2;
		}
		return miniscore;
	}

	private double wish() {
		double miniscore = recover(a.maxHp / 2);
		double maxDamage = expectedDamage(a, o);
		double recovered = Math.min(a.hp + a.maxHp / 2, a.maxHp);
		if (hasProtect(a) && !o.ability().equals("unseenfist")) {
			miniscore *= maxDamage > a.hp && maxDamage < recovered && !greatMoves() ? 4 : 0.6;
		} else if (maxDamage * 2 > a.hp && maxDamage < a.hp && maxDamage * 2 < recovered) {
			miniscore *= 2;
		}
		return miniscore;
	}

	private double rest() {
		if (!a.status().isEmpty() && a.status().equals("slp") || a.hp / a.maxHp >= 0.8
				|| Set.of("insomnia", "vitalspirit", "comatose").contains(a.ability()) || mistyGrounded(a)) {
			return 0;
		}
		double miniscore = 1.0;
		double maxDamage = expectedDamage(a, o);
		double gain = hpGain(a);
		if (maxDamage > a.hp && maxDamage * 2 < a.maxHp * gain) {
			miniscore *= 3;
		} else if (maxDamage * 2 < a.maxHp * gain) {
			if (maxDamage * 1.5 > a.hp) {
				miniscore *= 1.5;
			}
			if (maxDamage * 2 > a.hp && !faster()) {
				miniscore *= 2;
			}
		}
		miniscore *= a.hp < 0.5 * a.maxHp ? 1.5 : 0.5;
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
			miniscore *= 1.2;
		}
		if (Set.of("psn", "tox", "brn").contains(o.status()) || o.has("leechseed") || o.has("curse")) {
			miniscore *= 1.3;
			if (o.status().equals("tox")) {
				miniscore *= 1.3;
			}
		}
		if (a.status().equals("psn") || a.status().equals("tox")) {
			miniscore *= 1.3;
		}
		if (a.status().equals("brn")) {
			miniscore *= 1.3;
			if (a.spatk() < a.attack()) {
				miniscore *= 1.5;
			}
		}
		if (a.status().equals("par")) {
			miniscore *= 1.3;
		}
		if (memory(CONTRARY_BAIT)) {
			miniscore *= 1.3;
		}
		if (!(a.item().equals("lumberry") || a.item().equals("chestoberry") || hydration(a))) {
			miniscore *= 0.8;
			if (maxDamage * 2 > a.maxHp) {
				miniscore *= 0.4;
			} else if (maxDamage * 3 < a.maxHp) {
				miniscore *= 1.3;
				if (greatMoves()) {
					miniscore *= 6;
				}
			}
			if (memory(Set.of("wakeupslap", "nightmare", "dreameater")) || o.ability().equals("baddreams")) {
				miniscore *= 0.7;
			}
			if (a.hasMove("sleeptalk")) {
				miniscore *= 1.3;
			}
			if (a.hasMove("snore")) {
				miniscore *= 1.2;
			}
			if (a.ability().equals("shedskin") || a.ability().equals("earlybird")) {
				miniscore *= 1.1;
			}
		} else {
			miniscore *= a.ability().equals("harvest") ? 1.2 : 0.8;
		}
		if (!a.status().isEmpty()) {
			miniscore *= 1.4;
			if (a.status().equals("tox")) {
				miniscore *= 1.2;
			}
		}
		return miniscore;
	}

	private double aquaRing(int f) {
		if (f == 0xda && a.has("aquaring") || f == 0xdb && a.has("ingrain")) {
			return 0;
		}
		double miniscore = 1.0;
		double share = a.hp / a.maxHp;
		if (share > 0.75) {
			miniscore *= 1.2;
		}
		if (share < 0.5) {
			miniscore *= 0.7;
			if (share < 0.33) {
				miniscore *= 0.5;
			}
		}
		if (memoryHealing(o)) {
			miniscore *= 1.2;
		}
		if (hasProtect(a) && !o.ability().equals("unseenfist")) {
			miniscore *= 1.2;
		}
		if (a.moves.stream().anyMatch(x -> PIVOT.contains(x.id))) {
			miniscore *= 0.8;
		}
		double expected = expectedDamage(a, o);
		if (expected * 5 < a.maxHp && !memoryOf(o).isEmpty()) {
			miniscore *= 1.2;
		} else if (expected > a.maxHp * 0.4) {
			miniscore *= 0.3;
		}
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall") || a.roles.contains("tank")) {
			miniscore *= 1.2;
		}
		if (memory(PHASE)) {
			miniscore *= 0.3;
		}
		return miniscore;
	}

	private double leechSeed() {
		if (o.hasType("grass") || o.has("leechseed") || o.has("substitute")) {
			return 0;
		}
		double miniscore = 1.0;
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall") || a.roles.contains("tank")) {
			miniscore *= 1.2;
		}
		if (a.has("substitute")) {
			miniscore *= 1.3;
		}
		if (o.status().equals("par") || o.status().equals("slp")) {
			miniscore *= 1.2;
		}
		if (o.has("confusion")) {
			miniscore *= 1.2;
		}
		if (o.has("attract")) {
			miniscore *= 1.2;
		}
		if (Set.of("psn", "tox", "brn").contains(o.status())) {
			miniscore *= 1.1;
		}
		if (memory(union(Set.of("rapidspin"), PIVOT))) {
			miniscore *= 0.2;
		}
		miniscore *= o.hp >= o.maxHp ? 1.1 : o.hp / o.maxHp;
		if (o.hp * 2 < o.maxHp) {
			miniscore *= 0.8;
		}
		if (o.hp * 4 < o.maxHp) {
			miniscore *= 0.2;
		}
		if (hasProtect(a) && !o.ability().equals("unseenfist")) {
			miniscore *= 1.2;
		}
		return miniscore * (1 + 0.05 * stageSum(o, 1));
	}

	private double absorb(double score, int f) {
		if (a.has("healblock")) {
			return 0;
		}
		if (a.hp >= a.maxHp && fasterWith() || o.has("substitute")) {
			return none();
		}
		double drained = Math.min(score, 100) * o.hp * 0.01 / 2;
		if (f == 0x139) {
			drained *= 1.5;
		}
		if (a.item().equals("bigroot")) {
			drained *= 1.3;
		}
		if (fasterWith()) {
			drained = Math.min(drained, a.maxHp - a.hp);
		} else {
			drained = Math.min(drained, a.maxHp - (a.hp - expectedDamage(a, o)));
		}
		double miniscore = drained / o.maxHp;
		if (o.ability().equals("liquidooze")) {
			return 1 - miniscore;
		}
		return miniscore * 0.5 + 1;
	}

	// ------------------------------------------------ sacrifice, switching moves

	private double death() {
		double miniscore = 0.7;
		if (o.ability().equals("disguise") && !moldBreaker(a) || o.has("substitute")) {
			miniscore *= 0.3;
		}
		if (memory(PROTECT)) {
			miniscore *= 0.3;
		}
		if (a.hp >= a.maxHp) {
			miniscore *= 0.2;
		} else {
			miniscore *= 1 - a.hp / a.maxHp;
			if (a.hp * 4 < a.maxHp) {
				miniscore *= 1.3;
				if (a.item().equals("custapberry")) {
					miniscore *= 1.4;
				}
			}
		}
		if (a.roles.contains("lead")) {
			miniscore *= 1.2;
		}
		return miniscore;
	}

	private double destiny() {
		if ("destinybond".equals(lastMove.get(a.battler.getUuid()))) {
			return 0;
		}
		double miniscore = 1.0;
		List<Mv> seen = memoryOf(o);
		if (seen.size() >= 4 && seen.stream().allMatch(x -> !x.status)) {
			miniscore *= 3;
		}
		if (greatMoves()) {
			miniscore *= 0.1;
		}
		miniscore *= fasterWith() ? 1.5 : 0.5;
		if (a.hp >= a.maxHp) {
			miniscore *= 0.2;
		} else {
			miniscore *= 1 - a.hp / a.maxHp;
			if (a.hp * 4 < a.maxHp) {
				miniscore *= 1.3;
				if (a.item().equals("custapberry")) {
					miniscore *= 1.5;
				}
			}
		}
		return miniscore;
	}

	private double perish() {
		if (o.has("perish3") || o.has("perish2") || o.has("perish1")) {
			return 0;
		}
		if (reserves(o) == 0) {
			return 4;
		}
		if (reserves(a) == 0) {
			return 0;
		}
		double miniscore = 1.0;
		if (a.moves.stream().anyMatch(x -> PIVOT.contains(x.id))) {
			miniscore *= 1.5;
		}
		if (Set.of("arenatrap", "shadowtag").contains(a.ability()) || a.ability().equals("magnetpull") && o.hasType("steel") || o.has("trapped")) {
			miniscore *= 3;
		}
		if (partyRole("sweeper")) {
			miniscore *= 1.2;
		}
		if (a.moves.stream().anyMatch(x -> HEALING.contains(x.id))) {
			miniscore *= 1.2;
		}
		if (hasProtect(a) && !o.ability().equals("unseenfist")) {
			miniscore *= 1.2;
		}
		miniscore *= 1 - 0.05 * stageSum(a, 0);
		miniscore *= 1 + 0.05 * stageSum(o, 0);
		if (memory(PIVOT)) {
			miniscore *= 0.5;
		}
		if (Set.of("arenatrap", "shadowtag").contains(o.ability())) {
			miniscore *= 0.1;
		}
		if (partyRole("pivot")) {
			miniscore *= 1.5;
		}
		return miniscore;
	}

	private double phase() {
		if (o.has("ingrain") || o.ability().equals("suctioncups") || reserves(o) == 0 || o.has("perish3") || o.has("perish2")
				|| o.has("perish1") || o.has("yawn")) {
			return none();
		}
		double miniscore = 1.0;
		if (faster()) {
			miniscore *= 0.8;
		}
		miniscore *= 1 + 0.1 * stageSum(o, 0);
		if (o.status().equals("slp")) {
			miniscore *= 1.3;
		}
		if (o.ability().equals("slowstart")) {
			miniscore *= 1.3;
		}
		if (o.ability().equals("intimidate")) {
			miniscore *= 0.7;
		}
		if (o.ability().equals("regenerator") || o.ability().equals("naturalcure")) {
			miniscore *= 0.5;
		}
		Map<String, Integer> foe = conditions(false);
		if (foe.getOrDefault("toxicspikes", 0) > 0) {
			miniscore *= 1.1;
		}
		if (o.has("substitute")) {
			miniscore *= 1.4;
		}
		miniscore *= foe.containsKey("stealthrock") ? 1.3 : 0.8;
		int spikes = foe.getOrDefault("spikes", 0);
		miniscore *= spikes > 0 ? Math.pow(1.2, spikes) : 0.8;
		return miniscore;
	}

	private double pivot() {
		if (reserves(a) == 0) {
			return none();
		}
		double miniscore = 1.0;
		Map<String, Integer> own = conditions(true);
		if (own.containsKey("stealthrock")) {
			miniscore *= 0.7;
		}
		if (own.containsKey("stickyweb")) {
			miniscore *= 0.6;
		}
		miniscore *= Math.pow(0.9, own.getOrDefault("spikes", 0)) * Math.pow(0.9, own.getOrDefault("toxicspikes", 0));
		if (o.ability().equals("intimidate")) {
			miniscore *= 1.1;
		}
		if (a.ability().equals("regenerator") && a.hp / a.maxHp < 0.75) {
			miniscore *= 1.2;
			if (a.hp / a.maxHp < 0.5) {
				miniscore *= 1.2;
			}
		}
		if (partyRole("sweeper") && m.id.equals("partingshot")) {
			miniscore *= 1.5;
		}
		if (partyRole("sweeper") && Set.of("uturn", "voltswitch", "flipturn").contains(m.id) && !faster()) {
			miniscore *= 1.2;
		}
		List<BattlePokemon> party = a.battler.getActor().getPokemonList();
		int[] scores = switchInScores(party, a, o, fasterWith());
		int bestIn = bestIndex(scores);
		if (bestIn < 0 || scores[bestIn] < 50) {
			miniscore *= 0.2;
		}
		if (m.id.equals("batonpass")) {
			miniscore *= 1 + 0.3 * stageSum(a, 0);
			if (a.has("perish3") || a.has("perish2") || a.has("perish1")) {
				miniscore = 0;
			}
			if (a.has("substitute")) {
				miniscore *= 1.4;
			}
			if (a.has("confusion")) {
				miniscore *= 0.5;
			}
			if (a.has("leechseed")) {
				miniscore *= 0.5;
			}
			if (a.has("curse")) {
				miniscore *= 0.5;
			}
			if (a.has("yawn")) {
				miniscore *= 0.5;
			}
			if (a.turncount() < 1) {
				miniscore *= 0.5;
			}
			if (a.moves.stream().noneMatch(x -> !x.status)) {
				miniscore *= 1.3;
			}
			if (a.has("ingrain") || a.has("aquaring")) {
				miniscore *= 1.2;
			}
			double expected = expectedDamage(a, o);
			if (fasterWith()) {
				if (expected > a.hp && !memoryOf(o).isEmpty()) {
					miniscore *= 1.8;
				}
			} else if (expected * 2 > a.hp && !memoryOf(o).isEmpty()) {
				miniscore *= 2;
			}
		} else {
			miniscore *= 1 - 0.15 * stageSum(a, -1);
			miniscore *= 1 - 0.25 * stageSum(a, 1);
			if (a.roles.contains("lead")) {
				miniscore *= 1.1;
			}
			if (a.roles.contains("pivot")) {
				miniscore *= 1.1;
			}
			if (fasterWith()) {
				miniscore *= 1.2;
			}
			if (a.status().equals("tox") || a.has("attract") || a.has("confusion") || a.has("yawn")) {
				miniscore *= 1.3;
			}
			if (a.has("leechseed")) {
				miniscore *= 1.5;
			}
			if (a.has("substitute")) {
				miniscore *= 0.5;
			}
			if (a.has("perish3") || a.has("perish2") || a.has("perish1") || a.has("curse")) {
				miniscore *= 1.5;
			}
			if (fasterWith()) {
				double saved = o.hp;
				o.hp -= roughDamage(m, a, o);
				boolean canHardSwitch = false;
				for (BattlePokemon pokemon : party) {
					if (pokemon != a.battler && pokemon.getHealth() > 0 && shouldHardSwitch(a, o, List.of(), pokemon)) {
						canHardSwitch = true;
					}
				}
				if (!canHardSwitch && o.hp > 0) {
					miniscore *= 0.2;
				}
				o.hp = saved;
			}
		}
		if (greatMoves()) {
			miniscore *= 0.5;
		}
		if (badMoves(25)) {
			miniscore *= 2;
		} else if (badMoves(40)) {
			miniscore *= 1.2;
		}
		return miniscore;
	}

	private double meanLook() {
		if (o.has("trapped") || o.has("ingrain") || o.hasType("ghost") || secondaryNegated() || o.has("substitute") || alive(o) == 1) {
			return damaging() ? 1 : 0;
		}
		double miniscore = 1.0;
		if (memory(PIVOT)) {
			miniscore *= 0.1;
		}
		if (o.ability().equals("runaway")) {
			miniscore *= 0.1;
		}
		if (a.hasMove("perishsong")) {
			miniscore *= 1.5;
		}
		if (o.has("perish3") || o.has("perish2") || o.has("perish1")) {
			miniscore *= 4;
		}
		if (Set.of("arenatrap", "shadowtag").contains(a.ability()) || a.ability().equals("magnetpull") && o.hasType("steel")) {
			miniscore = 0;
		}
		if (o.has("attract")) {
			miniscore *= 1.3;
		}
		if (o.has("leechseed")) {
			miniscore *= 1.3;
		}
		if (o.has("curse")) {
			miniscore *= 1.5;
		}
		if (o.has("confusion")) {
			miniscore *= 1.1;
		}
		if (a.moves.stream().anyMatch(x -> PHASE.contains(x.id))) {
			miniscore *= 0.7;
		}
		miniscore *= 1 - 0.05 * stageSum(o, 0);
		if (miniscore < 1 && damaging()) {
			miniscore = 1;
		}
		return miniscore;
	}

	private double knockOff() {
		if (o.has("substitute") || o.item().isEmpty() || o.ability().equals("stickyhold") && !moldBreaker(a)) {
			return none();
		}
		String item = o.item();
		if (item.equals("leftovers") || item.equals("blacksludge") && o.hasType("poison")) {
			return 1.3;
		}
		if (Set.of("lifeorb", "choicescarf", "choiceband", "choicespecs", "assaultvest").contains(item)) {
			return 1.2;
		}
		return 1;
	}

	private double substitute() {
		if (a.hp * 4 <= a.maxHp || a.has("substitute") && fasterWith()) {
			return 0;
		}
		double miniscore = a.hp >= a.maxHp ? 1.1 : a.hp / a.maxHp;
		if (o.has("leechseed")) {
			miniscore *= 1.2;
		}
		if (hpGain(a) > 1) {
			miniscore *= 1.2;
		}
		if (a.moves.stream().anyMatch(x -> HEALING.contains(x.id))) {
			miniscore *= 1.2;
		}
		if (memory(Set.of("spore", "sleeppowder"))) {
			miniscore *= 1.2;
		}
		if (a.hasMove("focuspunch")) {
			miniscore *= 1.5;
		}
		if (o.status().equals("slp")) {
			miniscore *= 1.5;
		}
		if (o.ability().equals("infiltrator")) {
			miniscore *= 0.3;
		}
		if (memory(Set.of("uproar", "hypervoice", "echoedvoice", "snarl", "bugbuzz", "boomburst", "sparklingaria"))) {
			miniscore *= 0.3;
		}
		if (expectedDamage(a, o) * 4 < a.maxHp && !memoryOf(o).isEmpty()) {
			miniscore *= 2;
		}
		if (o.has("confusion")) {
			miniscore *= 1.3;
		}
		if (o.status().equals("par")) {
			miniscore *= 1.3;
		}
		if (o.has("attract")) {
			miniscore *= 1.3;
		}
		if (a.hasMove("batonpass")) {
			miniscore *= 1.2;
		}
		if (a.ability().equals("speedboost")) {
			miniscore *= 1.1;
		}
		return miniscore;
	}

	private double suckerPunch() {
		if (o.has("mustrecharge")) {
			return 0;
		}
		double miniscore = 1.0;
		List<Mv> seen = memoryOf(o);
		if (seen.size() >= 4 && seen.stream().allMatch(x -> !x.status)) {
			return 1.3;
		}
		if (memoryHealing(o)) {
			miniscore *= 0.6;
		}
		if (memory(SETUP)) {
			miniscore *= 0.8;
		}
		if ("suckerpunch".equals(lastMove.get(a.battler.getUuid()))) {
			if (random.nextInt(3) != 1) {
				miniscore *= 0.3;
			}
			if (memory(SETUP)) {
				miniscore *= 0.5;
			}
		}
		if (faster()) {
			miniscore *= 0.8;
			double max = 0;
			for (double value : initial) {
				max = Math.max(max, value);
			}
			if (max != initial[index]) {
				miniscore *= 0.6;
			}
		} else {
			miniscore *= memoryPriority(o) ? 0.5 : 1.3;
		}
		return miniscore;
	}

	private double trickRoomMove() {
		if (faster() && !a.item().equals("ironball")) {
			return 0;
		}
		double miniscore = 1.0;
		if (partyRole("sweeper")) {
			miniscore *= 1.3;
		}
		if (a.roles.contains("tank") || a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
			miniscore *= 1.3;
		}
		if (a.roles.contains("lead")) {
			miniscore *= 1.5;
		}
		if (notOHKO(a, o)) {
			miniscore *= 1.5;
		}
		if (!trickRoom) {
			miniscore *= 2;
			if (greatMoves()) {
				miniscore *= 6;
			}
		} else {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	private double jump(double score) {
		double miniscore = 1.0;
		if (score < 100) {
			miniscore *= 0.8;
		}
		if (memory(PROTECT)) {
			miniscore *= 0.5;
		}
		miniscore *= 1 - 0.1 * o.stages[6];
		miniscore *= 1 + 0.1 * a.stages[5];
		if (o.item().equals("laxincense") || o.item().equals("brightpowder")) {
			miniscore *= 0.7;
		}
		return miniscore;
	}

	private double painSplit() {
		if (o.has("substitute")) {
			return 0;
		}
		double after = Math.min((o.hp + a.hp) / 2, a.maxHp);
		if (after < a.hp && fasterWith()) {
			return 0;
		}
		double maxDamage = expectedDamage(a, o);
		if (o.hp <= a.hp - maxDamage || maxDamage > after) {
			return 0;
		}
		if (maxDamage > a.hp) {
			return fasterWith() ? 2 : 0;
		}
		double miniscore = memory(SETUP) ? 0.3 : 1;
		return miniscore * o.hp / a.hp;
	}

	private double neverMiss(double score) {
		double miniscore = score >= 110 ? 1.05 : 1.0;
		if (a.ability().equals("noguard") || o.ability().equals("noguard")) {
			return miniscore;
		}
		if (a.stages[5] < 0) {
			miniscore *= 1 - 0.05 * a.stages[5];
		}
		if (o.stages[6] > 0) {
			miniscore *= 1 + 0.05 * o.stages[6];
		}
		if (o.item().equals("laxincense") || o.item().equals("brightpowder")) {
			miniscore *= 1.2;
		}
		return miniscore;
	}

	// ------------------------------------------------ the rarer moves (step 3)

	private static final Set<String> FIXED_ABILITIES = Set.of("multitype", "zenmode", "stancechange", "schooling", "battlebond",
			"comatose", "shieldsdown", "disguise", "rkssystem", "powerconstruct", "iceface", "gulpmissile", "asonegrim", "asonechilling");

	private boolean attractable() {
		var mine = a.battler.getEffectedPokemon().getGender();
		var theirs = o.battler.getEffectedPokemon().getGender();
		return mine != theirs && !mine.name().equals("GENDERLESS") && !theirs.name().equals("GENDERLESS") && !o.has("attract")
				&& !(Set.of("oblivious", "aromaveil").contains(o.ability()) && !moldBreaker(a));
	}

	/** Reborn's {@code oppstatboost} (Swagger, Flatter, Decorate on an opponent): only worth it with the confusion. */
	private double oppBoost(boolean confusion, int... boosts) {
		if (o.has("substitute")) {
			return 0;
		}
		if (!(canConfuse(o) && confusion)) {
			return 0;
		}
		double miniscore = 1.0;
		if (boosts[2] != 0) {
			if (o.stages[0] > 0) {
				miniscore *= 1 + 0.1 * o.stages[0];
			}
			miniscore *= o.attack() > o.spatk() ? 1.5 : 0.3;
		} else if (boosts[0] != 0) {
			miniscore *= o.attack() < o.spatk() ? 1.5 : 0.7;
		}
		return miniscore * confuse();
	}

	/** Reborn's {@code statswapcode} (its slip kept: the opponent's part counts the attacker's stages). */
	private double statSwap(int first, int second) {
		int mine = a.stages[first] + a.stages[second];
		double miniscore = -1.1 * mine;
		Stat[] stats = {Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE};
		if (rough(a, stats[first]) > rough(a, stats[second])) {
			if (a.stages[first] < 0) {
				miniscore *= 2;
			}
		} else if (a.stages[second] < 0) {
			miniscore *= 2;
		}
		double mini = -1.1 * mine;
		if (rough(o, stats[first]) > rough(o, stats[second])) {
			if (o.stages[first] > 0) {
				mini *= 2;
			}
		} else if (o.stages[second] > 0) {
			mini *= 2;
		}
		return miniscore + mini;
	}

	/** The stages to gain and to lose by copying the opponent's (Psych Up, Heart Swap). */
	private int[][] psychUp() {
		int[] boost = new int[7];
		int[] drop = new int[7];
		for (int s = 0; s < 7; s++) {
			int diff = o.stages[s] - a.stages[s];
			boost[s] = Math.max(0, diff);
			drop[s] = Math.max(0, -diff);
		}
		return new int[][] {boost, drop};
	}

	private double powerTrick() {
		double miniscore = 1.0;
		double attack = a.mon.stat(Stats.ATTACK);
		double defence = a.mon.stat(Stats.DEFENCE);
		if (attack - defence >= 100) {
			if (fasterWith()) {
				miniscore *= 1.5;
			}
			if (rough(o, Stats.ATTACK) > rough(o, Stats.SPECIAL_ATTACK)) {
				miniscore *= 2;
			}
			if (a.moves.stream().anyMatch(x -> HEALING.contains(x.id))) {
				miniscore *= 2;
			}
		} else if (defence - attack >= 100) {
			if (fasterWith()) {
				miniscore *= 1.5;
				if (notOHKO(a, o)) {
					miniscore *= 2;
				}
			} else {
				miniscore = 0;
			}
		} else {
			miniscore *= 0.1;
		}
		if (a.has("powertrick")) {
			miniscore *= 0.1;
		}
		return miniscore;
	}

	/** Reborn's {@code splitcode} for Power Split ({@code attack}) and Guard Split. */
	private double split(boolean attack) {
		if (o.has("substitute")) {
			return 0;
		}
		double miniscore;
		boolean physicalFoe = o.attack() > o.spatk();
		Stat stat = attack ? (physicalFoe ? Stats.ATTACK : Stats.SPECIAL_ATTACK) : (physicalFoe ? Stats.DEFENCE : Stats.SPECIAL_DEFENCE);
		if (a.mon.stat(stat) > o.mon.stat(stat)) {
			return 0;
		}
		miniscore = o.mon.stat(stat) - a.mon.stat(stat);
		boolean physicalMe = a.attack() > a.spatk();
		miniscore *= physicalFoe == physicalMe ? 2 : 0.5;
		return 1 + miniscore / 100;
	}

	private double mimic(boolean sketch) {
		Mv last = opponentLast();
		if (last == null || o.has("substitute")) {
			return 0;
		}
		if (fasterWith() && Set.of(0x02, 0x14, 0x5C, 0x5D, 0xB6).contains(function(last.id))) {
			return 0;
		}
		double miniscore = 1 + (last.power > 0 ? last.power : 40) / 100;
		if (miniscore <= 1.5) {
			miniscore *= 0.5;
		}
		if (!fasterWith()) {
			miniscore *= 0.5;
		}
		return miniscore;
	}

	private double typeEffect(String attacking, B defender) {
		ElementalType type = attacking == null ? null : ElementalTypes.get(attacking);
		return type == null ? 0 : ExpertBattleAI.effectiveness(type, defender.mon, null);
	}

	/** Reborn's {@code typechangecode}: becoming {@code type} (Conversion, Reflect Type…). */
	private double typeChange(String type) {
		if (type == null || a.mon.types.size() == 1 && a.mon.types.get(0).equals(type)) {
			return 0;
		}
		double now = 0;
		for (String foeType : o.mon.types) {
			now = Math.max(now, typeEffect(foeType, a));
		}
		double after = 0;
		ElementalType target = ElementalTypes.get(type);
		for (String foeType : o.mon.types) {
			ElementalType attacking = ElementalTypes.get(foeType);
			if (attacking != null && target != null) {
				after = Math.max(after, com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility.INSTANCE.getDamageMultiplier(attacking, target));
			}
		}
		if (after > now) {
			return 0;
		}
		double miniscore = now * 4 * 2;
		miniscore *= fasterWith() ? 1.2 : 0.7;
		boolean stab = a.moves.stream().anyMatch(x -> !x.status && a.mon.types.contains(x.type.getName().toLowerCase(Locale.ROOT)));
		boolean newStab = a.moves.stream().anyMatch(x -> !x.status && x.type.getName().equalsIgnoreCase(type));
		miniscore *= stab && !newStab ? 1.2 : 0.6;
		return miniscore;
	}

	/** Reborn's {@code opptypechangecode}: making the opponent {@code type} (Soak, Forest's Curse…). */
	private double oppTypeChange(String type) {
		if (o.mon.types.size() == 1 && o.mon.types.get(0).equals(type)) {
			return 0;
		}
		double now = 0;
		for (String mine : a.mon.types) {
			now = Math.max(now, typeEffect(mine, o));
		}
		double after = 0;
		ElementalType target = ElementalTypes.get(type);
		for (String mine : a.mon.types) {
			ElementalType attacking = ElementalTypes.get(mine);
			if (attacking != null && target != null) {
				after = Math.max(after, com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility.INSTANCE.getDamageMultiplier(attacking, target));
			}
		}
		if (after < now) {
			return 0;
		}
		double mini = after * 2;
		if (memoryOf(o).stream().anyMatch(x -> x.type.getName().equalsIgnoreCase(type))) {
			mini *= 0.5;
		}
		if (target != null && a.moves.stream().anyMatch(x -> com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility.INSTANCE
				.getDamageMultiplier(x.type, target) > 1)) {
			mini *= 1.5;
		}
		return mini;
	}

	private double abilityChange(String ability) {
		if (o.ability().equals(ability) || FIXED_ABILITIES.contains(o.ability())) {
			return 0;
		}
		double miniscore = abilityDisrupt(a, o);
		if (ability.equals("simple") && memory(SETUP)) {
			miniscore *= 0.5;
		} else if (ability.equals("insomnia")) {
			if (memory(Set.of("snore", "sleeptalk"))) {
				miniscore *= 1.3;
			}
			if (memory("rest")) {
				miniscore *= 2;
			}
			if (a.moves.stream().anyMatch(x -> SLEEP_MOVES.contains(x.id))) {
				miniscore *= 0.3;
			}
		}
		return miniscore;
	}

	private static int stageSum5(B b) {
		return b.stages[0] + b.stages[1] + b.stages[2] + b.stages[3] + b.stages[4];
	}

	private double counterAttack(int f) {
		double miniscore = 1.0;
		double maxDamage = expectedDamage(a, o);
		if (faster()) {
			miniscore *= 0.5;
		}
		if (notOHKO(a, o)) {
			miniscore *= 1.2;
		} else {
			miniscore *= 0.8;
			if (maxDamage > a.hp) {
				miniscore *= 0.8;
			}
		}
		String last = lastMove.get(a.battler.getUuid());
		if (last != null && function(last) == f) {
			miniscore *= 0.7;
		}
		if (memory(SETUP)) {
			miniscore *= 0.6;
		}
		miniscore *= a.hp / a.maxHp;
		Mv best = expectedMove(o, a);
		double attack = rough(o, Stats.ATTACK);
		double special = rough(o, Stats.SPECIAL_ATTACK);
		if (f == 0x71) {
			miniscore *= attack > special * 1.1 ? 1.1 : attack < special * 0.6 ? 0.3 : 0.6;
			if (best != null && !best.physical) {
				miniscore *= 0.05;
			}
		} else if (f == 0x72) {
			miniscore *= attack * 1.1 < special ? 1.1 : attack * 0.6 > special ? 0.3 : 0.6;
			if (o.spatk() < o.attack()) {
				miniscore *= 0.3;
			}
			if (best != null && best.physical) {
				miniscore *= 0.05;
			}
		}
		return miniscore;
	}

	private double echo() {
		double miniscore = 1.0;
		if (a.status().equals("par")) {
			miniscore *= 0.7;
		}
		if (a.has("confusion")) {
			miniscore *= 0.7;
		}
		if (a.has("attract")) {
			miniscore *= 0.7;
		}
		if (a.hp >= a.maxHp) {
			miniscore *= 1.3;
		}
		if (expectedDamage(a, o) < a.hp / 3) {
			miniscore *= 1.5;
		}
		return miniscore;
	}

	private double permaCrit(double initialScore) {
		if (o.ability().equals("battlearmor") || o.ability().equals("shellarmor")) {
			return 0.7;
		}
		double miniscore = 1.0 + 0.1 * Math.max(0, o.stages[1]) + 0.1 * Math.max(0, o.stages[3])
				+ 0.1 * Math.min(0, a.stages[0]) + 0.1 * Math.min(0, a.stages[2]);
		if (a.has("focusenergy")) {
			miniscore -= 0.2;
		}
		if (!(o.ability().equals("angerpoint") && o.stages[0] != 6)) {
			return miniscore;
		}
		if (initialScore < 100) {
			miniscore *= 0.7;
			if (o.attack() > o.spatk()) {
				miniscore *= 0.2;
			}
		}
		return miniscore;
	}

	private double specialProtect() {
		if (o.ability().equals("unseenfist")) {
			return 0;
		}
		double miniscore = 1.0;
		if (protectRate() > 0) {
			miniscore /= protectRate() * 2.0;
		}
		if (expectedDamage(a, o) > 0 || memory(SETUP)) {
			miniscore *= 0.3;
		}
		if (memory(PROTECT_IGNORING)) {
			miniscore *= 0.1;
		}
		return miniscore;
	}

	private double feint() {
		if (!memory(PROTECT)) {
			return 1;
		}
		String last = lastMove.getOrDefault(o.battler.getUuid(), "");
		return 1.1 * (PROTECT.contains(last) || Set.of("quickguard", "wideguard", "endure").contains(last) ? 1 : 1.2);
	}

	private double sleepTalk() {
		if (!a.ability().equals("comatose") && !a.status().equals("slp")) {
			return 0;
		}
		if (!a.hasMove("snore")) {
			return 5;
		}
		double others = 0;
		double snore = 0;
		for (int i = 0; i < a.moves.size() && i < initial.length; i++) {
			String id = a.moves.get(i).id;
			if (id.equals("sleeptalk")) {
				continue;
			}
			if (id.equals("snore")) {
				snore = initial[i];
			} else {
				others += initial[i];
			}
		}
		return others * 0.5 < snore ? 0.1 : 5;
	}

	private double multiHit() {
		double miniscore = 1.0;
		if (m.contact() && (o.item().equals("rockyhelmet") || o.ability().equals("ironbarbs") || o.ability().equals("roughskin"))) {
			miniscore *= 0.7;
		}
		if (notOHKO(o, a)) {
			miniscore *= 1.3;
		}
		if (o.has("substitute")) {
			miniscore *= 1.3;
		}
		if (a.item().equals("razorfang") || a.item().equals("kingsrock")) {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	private double hyperBeam() {
		double miniscore = 1.0;
		if (initial[index] < 100) {
			miniscore *= 0.5;
			if (memoryHealing(o)) {
				miniscore *= 0.5;
			}
		}
		if (greatMoves()) {
			miniscore *= 0.3;
		}
		int theirs = alive(o);
		miniscore *= theirs > 1 ? (10 - theirs) * 0.1 : 1.1;
		if (theirs > 1 && alive(a) == 1) {
			miniscore *= 0.7;
		}
		return miniscore;
	}

	/** Reborn's {@code weaselslashcode}: a move charged for a turn (Solar Beam, Meteor Beam…). */
	private double chargeTurn() {
		if (a.item().equals("powerherb")) {
			double miniscore = !greatMoves() && !m.id.equals("geomancy") ? 1.2 : 1.0;
			return a.ability().equals("unburden") ? 1.8 : miniscore;
		}
		double miniscore = 1.0;
		double expected = expectedDamage(a, o);
		if (expected > a.hp) {
			miniscore *= 0.1;
		} else if (expected * 2 > a.hp) {
			miniscore *= fasterWith() ? 0.7 : 0.1;
		}
		if (a.hp / a.maxHp < 0.5) {
			miniscore *= 0.6;
		}
		if (charging.containsKey(o.battler.getUuid())) {
			miniscore *= fasterWith() ? 2 : 0.5;
		}
		for (double value : initial) {
			if (value > 100) {
				miniscore *= 0.1;
				break;
			}
		}
		if (damaging()) {
			if (memory(PROTECT)) {
				miniscore *= 0.1;
			}
			if (initial[index] < 100) {
				miniscore *= 0.7;
			}
		} else {
			miniscore *= 0.4;
		}
		return miniscore;
	}

	private double twoTurn() {
		if (a.item().equals("powerherb")) {
			return a.ability().equals("unburden") ? 1.8 : 1.2;
		}
		double miniscore = 1.0;
		if (Set.of("psn", "tox", "brn").contains(o.status()) || o.has("leechseed") || o.has("curse")) {
			miniscore *= 1.2;
		} else if (alive(o) > 1) {
			miniscore *= 0.8;
		}
		if (!a.status().isEmpty() || a.has("curse") || a.has("attract") || a.has("confusion")) {
			miniscore *= 0.5;
		}
		miniscore *= hpGain(a);
		Map<String, Integer> own = conditions(true);
		if (own.containsKey("reflect") || own.containsKey("lightscreen") || own.containsKey("auroraveil") || own.containsKey("tailwind")) {
			miniscore *= 0.7;
		}
		if ((o.has("perish3") || o.has("perish2") || o.has("perish1")) && !a.has("perish3") && !a.has("perish2") && !a.has("perish1")) {
			miniscore *= 1.3;
		}
		if (faster()) {
			if (charging.containsKey(o.battler.getUuid())) {
				miniscore *= 3;
			}
			miniscore *= 1.1;
		} else {
			miniscore *= 0.8;
			if (memoryHealing(o)) {
				miniscore *= 0.5;
			}
			if (memoryOf(o).stream().anyMatch(x -> x.accuracy == 0)) {
				miniscore *= 0.7;
			}
		}
		return miniscore;
	}

	private double fireSpin() {
		if (initial[index] >= 110 || o.has("partiallytrapped") || o.has("substitute")) {
			return none();
		}
		double miniscore = 1.2;
		if (badMoves(30)) {
			miniscore *= 1.2;
		}
		if (o.hp >= o.maxHp) {
			miniscore *= 1.2;
		} else if (o.hp * 2 < o.maxHp) {
			miniscore *= 0.8;
		}
		miniscore *= 1 - 0.05 * stageSum(o, 1);
		if (expectedDamage(a, o) > a.hp || a.hp * 3 < a.maxHp) {
			miniscore *= 0.7;
		}
		if (o.has("leechseed")) {
			miniscore *= 1.5;
		}
		if (o.has("attract")) {
			miniscore *= 1.3;
		}
		if (o.has("confusion")) {
			miniscore *= 1.3;
		}
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
			miniscore *= 1.2;
		}
		if (hasProtect(a) && !o.ability().equals("unseenfist")) {
			miniscore *= 1.1;
		}
		if (a.item().equals("bindingband")) {
			miniscore *= 1.3;
		}
		if (a.item().equals("gripclaw")) {
			miniscore *= 1.1;
		}
		return miniscore;
	}

	private double rollout() {
		double miniscore = 1.0;
		if (reserves(o) == 0 || Set.of("arenatrap", "shadowtag").contains(a.ability())) {
			miniscore *= 1.1;
		}
		if (a.hp / a.maxHp < 0.75) {
			miniscore *= 0.75;
		}
		miniscore *= (1 + 0.05 * Math.min(0, a.stages[5])) * (1 + 0.05 * Math.min(0, a.stages[0])) * (1 - 0.05 * Math.max(0, o.stages[6]));
		if (o.item().equals("laxincense") || o.item().equals("brightpowder")) {
			miniscore *= 0.8;
		}
		if (a.status().equals("par")) {
			miniscore *= 0.5;
		}
		if (a.has("confusion")) {
			miniscore *= 0.5;
		}
		if (a.has("attract")) {
			miniscore *= 0.5;
		}
		if (reserves(o) > 1) {
			miniscore *= 1 - reserves(o) * 0.05;
		}
		if (expectedDamage(a, o) * 3 < a.hp && !memoryOf(o).isEmpty()) {
			miniscore *= 1.5;
		}
		if (badMoves(15)) {
			miniscore += 4;
		}
		if (memory(PROTECT)) {
			miniscore *= 0.8;
		}
		return miniscore;
	}

	private double bide() {
		double miniscore = a.hp / a.maxHp;
		if (greatMoves()) {
			miniscore *= 0.5;
		}
		if (notOHKO(a, o)) {
			miniscore *= 1.2;
		}
		if (expectedDamage(a, o) * 2 > a.hp) {
			miniscore *= 0.2;
		}
		if (a.hp * 3 < a.maxHp) {
			miniscore *= 0.7;
		}
		if (a.item().equals("leftovers") || a.item().equals("blacksludge") && a.hasType("poison")) {
			miniscore *= 1.1;
		}
		if (a.roles.contains("physicalwall") || a.roles.contains("specialwall")) {
			miniscore *= 1.3;
		}
		if (!faster()) {
			miniscore *= 1.3;
		}
		if (memory(SETUP)) {
			miniscore *= 0.5;
		}
		List<Mv> seen = memoryOf(o);
		if (seen.stream().anyMatch(x -> x.status)) {
			miniscore *= 0.8;
		} else if (seen.size() == 4) {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	/** Reborn's {@code healwishcode} (it reads the opponent's party there: our own is meant). */
	private double healingWish() {
		int hurt = 0;
		double best = 0;
		for (BattlePokemon pokemon : a.battler.getActor().getPokemonList()) {
			if (pokemon == a.battler || pokemon.getHealth() <= 0 || pokemon.getHealth() >= pokemon.getMaxHealth()) {
				continue;
			}
			hurt++;
			double value = 1 - pokemon.getHealth() / (double) pokemon.getMaxHealth();
			if (pokemon.getEffectedPokemon().getStatus() != null) {
				value *= 2;
			}
			best = Math.max(best, value);
		}
		if (hurt == 0) {
			return 0;
		}
		double miniscore = best;
		if (a.hp >= a.maxHp) {
			miniscore *= 0.2;
		} else {
			miniscore *= 1 - a.hp / a.maxHp;
			if (a.hp * 4 < a.maxHp) {
				miniscore *= 1.3;
			}
		}
		return miniscore * (fasterWith() ? 1.1 : 0.5);
	}

	private double grudge() {
		double miniscore = 1.0;
		List<Mv> seen = memoryOf(o);
		if (seen.size() >= 4 && seen.stream().filter(x -> !x.status).count() == 1) {
			miniscore *= 3;
		}
		if (a.hp >= a.maxHp) {
			miniscore *= 0.2;
		} else {
			miniscore *= 1 - a.hp / a.maxHp;
			if (a.hp * 4 < a.maxHp) {
				miniscore *= 1.3;
			}
		}
		return miniscore * (fasterWith() ? 1.3 : 0.5);
	}

	private double endure() {
		if (a.hp <= 1 || notOHKO(a, o) || hpGain(a) < 1 || expectedDamage(a, o) < a.hp) {
			return 0;
		}
		double miniscore = faster() ? 1.3 : 0.5;
		if (faster()) {
			if (a.hasMove("painsplit") || a.hasMove("flail") || a.hasMove("reversal")) {
				miniscore *= 3;
			}
			if (a.hasMove("endeavor")) {
				miniscore *= 5;
			}
			if (charging.containsKey(o.battler.getUuid())) {
				miniscore *= 5;
			}
		}
		if (Set.of("psn", "tox", "brn").contains(o.status()) || o.has("leechseed") || o.has("curse")) {
			miniscore *= 1.5;
		}
		if (protectRate() > 0) {
			miniscore /= protectRate() * 2.0;
		}
		return miniscore;
	}

	private double covet() {
		if (o.ability().equals("stickyhold") && !moldBreaker(a) || o.item().isEmpty() || o.has("substitute")
				|| !a.item().isEmpty() && !Set.of("trick", "switcheroo").contains(m.id)) {
			return none();
		}
		double miniscore = 1.2;
		switch (o.item()) {
			case "leftovers", "lifeorb", "lumberry", "sitrusberry" -> miniscore *= 1.5;
			case "assaultvest", "rockyhelmet" -> miniscore *= 1.3;
			case "focussash", "muscleband", "wiseglasses", "expertbelt", "widelens" -> miniscore *= 1.2;
			case "choicescarf" -> miniscore *= faster() ? 1 : 1.1;
			case "choiceband" -> miniscore *= a.attack() > a.spatk() ? 1.1 : 1;
			case "choicespecs" -> miniscore *= a.spatk() > a.attack() ? 1.1 : 1;
			case "blacksludge" -> miniscore *= a.hasType("poison") ? 1.5 : 0.5;
			case "toxicorb", "flameorb", "laggingtail", "ironball", "stickybarb" -> miniscore *= 0.5;
			default -> {
			}
		}
		return miniscore;
	}

	private double bestow() {
		if (o.ability().equals("stickyhold") && !moldBreaker(a) || a.item().isEmpty() || o.has("substitute")
				|| !o.item().isEmpty() && !Set.of("trick", "switcheroo").contains(m.id)) {
			return 0;
		}
		double miniscore = 1.0;
		switch (a.item()) {
			case "leftovers", "lifeorb", "lumberry", "sitrusberry" -> miniscore *= 0.5;
			case "focussash", "muscleband", "wiseglasses", "expertbelt", "widelens" -> miniscore *= 0.8;
			case "assaultvest", "rockyhelmet" -> miniscore *= 0.7;
			case "choicespecs" -> miniscore *= (o.attack() > o.spatk() ? 1.7 : 1) * (a.attack() < a.spatk() ? 0.8 : 1);
			case "choicescarf" -> miniscore *= faster() ? 0.9 : 1.5;
			case "choiceband" -> miniscore *= (o.attack() < o.spatk() ? 1.7 : 1) * (a.attack() > a.spatk() ? 0.8 : 1);
			case "blacksludge" -> miniscore *= (a.hasType("poison") ? 0.5 : 1.5) * (!o.hasType("poison") ? 1.3 : 1);
			case "toxicorb", "flameorb", "laggingtail", "ironball", "stickybarb" -> miniscore *= 1.5;
			default -> {
			}
		}
		if (Set.of("choicescarf", "choiceband", "choicespecs").contains(a.item())) {
			Mv last = opponentLast();
			if (last != null && fasterWith() && last.status) {
				miniscore *= 3;
			}
			if (badMoves(40)) {
				miniscore *= 1.5;
			}
			if (turn <= 1) {
				miniscore *= 1.5;
			}
			double expected = expectedDamage(a, o);
			if (expected > 0.5 * a.hp) {
				miniscore *= 0.3;
			}
			if (expected < 0.33 * a.hp) {
				miniscore *= 1.3;
			}
		}
		return miniscore;
	}

	/** What a berry would do for {@code user} (Bug Bite, Stuff Cheeks, Teatime). */
	private double berryBonus(B holder, B user) {
		return switch (holder.item()) {
			case "lumberry" -> user.status().isEmpty() ? 1 : 2;
			case "cheriberry" -> user.status().equals("par") ? 2 : 1;
			case "rawstberry" -> user.status().equals("brn") ? 2 : 1;
			case "pechaberry" -> user.status().equals("psn") || user.status().equals("tox") ? 2 : 1;
			case "sitrusberry", "figyberry", "wikiberry", "magoberry", "aguavberry", "iapapaberry" -> user.hp / user.maxHp < 0.66 ? 1.6 : 1;
			case "liechiberry" -> user.attack() > user.spatk() ? 1.5 : 1;
			case "petayaberry" -> user.spatk() > user.attack() ? 1.5 : 1;
			case "apicotberry", "ganlonberry", "starfberry" -> 1.5;
			case "custapberry", "salacberry" -> faster() ? 1.1 : 1.5;
			default -> 1.1;
		};
	}

	private double nom() {
		if (o.has("substitute") || !o.item().isEmpty() && !o.item().endsWith("berry")) {
			return 1;
		}
		return o.item().isEmpty() ? 1.1 : berryBonus(o, a);
	}

	private double roast() {
		if (!o.item().isEmpty() && !o.item().endsWith("berry") && !o.item().endsWith("gem") || o.ability().equals("stickyhold") || o.has("substitute")) {
			return 1;
		}
		double miniscore = 1.0;
		if (o.item().endsWith("berry") && !o.item().equals("occaberry")) {
			miniscore *= 1.2;
		}
		if (Set.of("lumberry", "sitrusberry", "petayaberry", "liechiberry", "salacberry", "custapberry").contains(o.item())) {
			miniscore *= 1.3;
		}
		if (o.item().endsWith("gem")) {
			miniscore *= 1.4;
		}
		return miniscore;
	}

	private double fling() {
		String item = a.item();
		if (item.isEmpty() || a.ability().equals("klutz") || item.endsWith("berry") && Set.of("unnerve", "asonechilling", "asonegrim").contains(o.ability())) {
			return 0;
		}
		double miniscore = 1.0;
		switch (item) {
			case "toxicorb" -> {
				if (canStatus(o, "psn") && !Set.of("guts", "poisonheal", "toxicboost", "quickfeet", "magicguard").contains(o.ability())) {
					miniscore *= 1.2;
				}
			}
			case "flameorb" -> {
				if (canStatus(o, "brn") && !Set.of("guts", "flareboost").contains(o.ability())) {
					miniscore *= Set.of("quickfeet", "marvelscale", "magicguard").contains(o.ability()) ? 1.1 : 1.3;
				}
			}
			case "lightball" -> miniscore *= canStatus(o, "par") && !o.ability().equals("quickfeet") ? 1.3 : 1;
			case "kingsrock", "razorclaw" -> miniscore *= !o.ability().equals("innerfocus") && fasterWith() ? 1.3 : 1;
			case "laxincense", "choicescarf", "choiceband", "choicespecs", "expertbelt", "focussash", "leftovers", "muscleband", "rockyhelmet",
					"bigroot", "shellbell", "widelens", "zoomlens", "wiseglasses", "assaultvest", "eviolite", "lifeorb", "heavydutyboots" -> miniscore = 0;
			case "stickybarb" -> miniscore *= 1.2;
			case "laggingtail" -> miniscore *= 3;
			case "ironball" -> miniscore *= 1.5;
			default -> {
			}
		}
		if (item.endsWith("berry") && !Set.of("figyberry", "wikiberry", "magoberry", "aguavberry", "iapapaberry").contains(item)) {
			miniscore = 0;
		}
		return miniscore;
	}

	private double embargo(B target) {
		if (target.item().isEmpty() || target.has("embargo") && target.has("substitute")) {
			return 0;
		}
		double miniscore = 1.1;
		if (target.item().endsWith("berry")) {
			miniscore *= 1.1;
		}
		if (Set.of("laxincense", "expertbelt", "muscleband", "wiseglasses", "lifeorb", "eviolite", "assaultvest").contains(target.item())) {
			miniscore *= 1.2;
		}
		if (target.item().equals("leftovers") || target.item().equals("blacksludge")) {
			miniscore *= 1.3;
		}
		if (target.hp * 2 < target.maxHp) {
			miniscore *= 1.4;
		}
		return miniscore;
	}

	private double brickBreak() {
		Map<String, Integer> foe = conditions(false);
		return (foe.containsKey("reflect") ? 1.8 : 1) * (foe.containsKey("lightscreen") ? 1.3 : 1) * (foe.containsKey("auroraveil") ? 2 : 1);
	}

	private double spooky() {
		double miniscore = 0.7;
		if (!fasterWith()) {
			miniscore *= 0.5;
		}
		if (!memoryOf(o).isEmpty() && expectedDamage(a, o) * 5 < a.hp) {
			miniscore *= 1.3;
		}
		if (a.moves.stream().anyMatch(x -> HEALING.contains(x.id))) {
			miniscore *= 1.2;
		}
		miniscore *= 1 + 0.05 * stageSum(o, 0);
		miniscore *= Set.of("arenatrap", "shadowtag").contains(a.ability()) || o.has("trapped") ? 1.3 : 0.8;
		if (badMoves(25)) {
			miniscore *= 1.3;
		}
		return miniscore;
	}

	private double spite(double score) {
		Mv last = opponentLast();
		if (!fasterWith()) {
			score *= 0.5;
		}
		if (last != null && last.template != null) {
			int maxPp = last.template.getPp();
			score *= maxPp == 5 ? 1.5 : maxPp == 10 ? 1.2 : 0.7;
		} else {
			score *= 0.7;
		}
		return score;
	}

	private int stockpile() {
		for (int n = 3; n >= 1; n--) {
			if (a.has("stockpile" + n)) {
				return n;
			}
		}
		return 0;
	}

	/** Reborn's {@code antistatcode} (attack, defence, sp. attack, sp. defence, speed). */
	private double antiStat(double begin, int... stats) {
		double miniscore = 1.0;
		int theirReserves = reserves(o);
		if (theirReserves > 0) {
			miniscore *= 1 - 0.05 * (theirReserves - 2);
		}
		if (partyRole("pivot")) {
			miniscore *= 1.2;
		}
		if (greatMoves()) {
			miniscore *= 0.7;
		}
		if (begin < 100) {
			miniscore *= 0.9;
		}
		for (int i = 0; i < stats.length; i++) {
			if (stats[i] == 0) {
				continue;
			}
			switch (i) {
				case 0, 2 -> {
					if (begin < 100 && memoryHealing(o)) {
						miniscore *= 0.5;
					}
					if (theirReserves > 0 && reserves(a) == 0) {
						miniscore *= 0.8;
					}
				}
				case 1 -> {
					if (begin < 100 && !faster() || memoryPriority(o)) {
						miniscore /= 0.9;
					}
					if (begin < 100 && o.attack() < o.spatk()) {
						miniscore /= 0.9;
					}
					if (a.roles.contains("physicalwall")) {
						miniscore *= 0.8;
					}
				}
				case 3 -> {
					if (!faster() || memoryPriority(o)) {
						miniscore /= 0.9;
					}
					if (begin < 100 && o.attack() > o.spatk()) {
						miniscore /= 0.9;
					}
					if (a.roles.contains("specialwall")) {
						miniscore *= 0.9;
					}
				}
				case 4 -> {
					if (a.roles.contains("tank")) {
						miniscore *= 1.1;
					}
					if (a.speed() > rough(o, Stats.SPEED)) {
						miniscore *= 0.8;
					}
				}
				default -> {
				}
			}
		}
		return miniscore;
	}

	private double focusPunch() {
		double miniscore = 1.0;
		List<Mv> seen = memoryOf(o);
		boolean breaks = seen.stream().anyMatch(x -> x.sound() && !x.status || x.multiHit());
		if (a.has("substitute")) {
			miniscore *= breaks || o.ability().equals("infiltrator") ? 0.9 : 1.3;
		} else {
			miniscore *= 0.8;
		}
		if (o.status().equals("slp") && !o.ability().equals("earlybird") && !o.ability().equals("shedskin")) {
			miniscore *= 1.2;
		}
		if (o.has("mustrecharge")) {
			miniscore *= 1.5;
		}
		if (miniscore <= 1.0) {
			miniscore *= 0.3;
		}
		return miniscore;
	}

	private double gravity() {
		if (a.moves.stream().anyMatch(x -> Set.of("skydrop", "bounce", "fly", "jumpkick", "flyingpress", "highjumpkick").contains(x.id))) {
			return 0;
		}
		double miniscore = 1.0;
		if (a.moves.stream().anyMatch(x -> x.accuracy > 0 && x.accuracy <= 70)) {
			miniscore *= 2;
		}
		if (a.hasMove("zapcannon") || a.hasMove("inferno")) {
			miniscore *= 3;
		}
		Mv best = expectedMove(o, a);
		if (best != null && Set.of("skydrop", "bounce", "fly", "jumpkick", "flyingpress", "highjumpkick").contains(best.id)) {
			miniscore *= 2;
		}
		if (a.hasType("ground") && !o.grounded()) {
			miniscore *= 2;
		}
		return miniscore;
	}

	private double smackDown() {
		if (o.has("ingrain") || o.has("smackdown") || o.item().equals("ironball")) {
			return 1;
		}
		double miniscore = 1.0;
		if (!faster()) {
			if (memory(Set.of("bounce", "fly", "skydrop"))) {
				miniscore *= 1.3;
			} else if (charging.containsKey(o.battler.getUuid())) {
				miniscore *= 2;
			}
		}
		if (o.hasType("flying") || o.ability().equals("levitate")) {
			miniscore *= a.moves.stream().anyMatch(x -> !x.status && x.type.getName().equalsIgnoreCase("ground")) ? 2 : 1.2;
		}
		return miniscore;
	}

	private double wonderRoom() {
		double miniscore = 1.0;
		boolean myDefence = a.mon.stat(Stats.DEFENCE) > a.mon.stat(Stats.SPECIAL_DEFENCE);
		if (rough(o, Stats.ATTACK) > rough(o, Stats.SPECIAL_ATTACK)) {
			miniscore *= myDefence ? 0.5 : 2;
		} else {
			miniscore *= myDefence ? 2 : 0.5;
		}
		boolean theirDefence = rough(o, Stats.DEFENCE) > rough(o, Stats.SPECIAL_DEFENCE);
		if (a.attack() > a.spatk()) {
			miniscore *= theirDefence ? 2 : 0.5;
		} else {
			miniscore *= theirDefence ? 0.5 : 2;
		}
		return miniscore;
	}

	private double powder() {
		if (o.hasType("grass") || o.ability().equals("overcoat") || o.item().equals("safetygoggles")) {
			return 0;
		}
		List<Mv> seen = memoryOf(o);
		if (seen.size() >= 4 && seen.stream().noneMatch(x -> x.type.getName().equalsIgnoreCase("fire"))) {
			return 0;
		}
		double miniscore = faster() ? 1.0 : 1.2;
		Mv best = expectedMove(o, a);
		if (best != null && best.type.getName().equalsIgnoreCase("fire")) {
			miniscore *= 3;
		} else {
			miniscore *= o.hasType("fire") ? 2 : 0.2;
		}
		double effect = typeEffect("fire", a);
		if (effect > 1) {
			miniscore *= 2;
		}
		if (effect > 2) {
			miniscore *= 2;
		}
		if ("powder".equals(lastMove.get(a.battler.getUuid()))) {
			miniscore *= 0.6;
		}
		return miniscore;
	}

	private double burnUp() {
		if (!a.hasType("fire")) {
			return 0;
		}
		double miniscore = 1 - reserves(o) * 0.05;
		if (initial[index] < 100) {
			miniscore *= 0.9;
			if (memoryHealing(o)) {
				miniscore *= 0.5;
			}
		}
		if (greatMoves()) {
			miniscore *= 0.5;
		}
		if (reserves(a) == 0 && reserves(o) != 0) {
			miniscore *= 0.7;
		}
		for (String type : o.mon.types) {
			ElementalType defending = ElementalTypes.get(type);
			if (defending != null) {
				double effect = com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility.INSTANCE.getDamageMultiplier(ElementalTypes.get("fire"), defending);
				miniscore *= effect > 1 ? 1.5 : effect < 1 ? 0.5 : 1;
			}
		}
		Mv best = expectedMove(o, a);
		if (best != null) {
			double effect = com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility.INSTANCE.getDamageMultiplier(best.type, ElementalTypes.get("fire"));
			miniscore *= effect > 1 ? 1.5 : effect < 1 ? 0.5 : 1;
		}
		return miniscore;
	}

	/** Reborn's {@code moldbreakeronalaser}: ignoring the opponent's ability (Sunsteel Strike, Photon Geyser). */
	private double moldBreakerMove() {
		if (moldBreaker(a)) {
			return 1;
		}
		long damaging = a.moves.stream().filter(x -> !x.status).count();
		String type = m.type.getName().toLowerCase(Locale.ROOT);
		double miniscore = 1.0;
		java.util.function.BiFunction<String, Set<String>, Double> absorbs = (absorbed, abilities) -> type.equals(absorbed)
				? (damaging == 1 ? 3.0 : 1.0) * (typeEffect(absorbed, o) > 1 ? 2 : 1) : 1.0;
		switch (o.ability()) {
			case "sandveil" -> miniscore *= weather.equals("sandstorm") ? 1 : 1.1;
			case "voltabsorb", "lightningrod" -> miniscore *= absorbs.apply("electric", Set.of());
			case "waterabsorb", "stormdrain", "dryskin" -> miniscore *= absorbs.apply("water", Set.of());
			case "flashfire" -> miniscore *= absorbs.apply("fire", Set.of());
			case "levitate" -> miniscore *= absorbs.apply("ground", Set.of());
			case "sapsipper" -> miniscore *= absorbs.apply("grass", Set.of());
			case "wonderguard" -> miniscore *= 5;
			case "soundproof" -> miniscore *= m.sound() ? 3 : 1;
			case "thickfat" -> miniscore *= type.equals("fire") || type.equals("ice") ? 1.5 : 1;
			case "moldbreaker", "turboblaze", "teravolt" -> miniscore *= 1.1;
			case "unaware" -> miniscore *= 1.7;
			case "multiscale" -> miniscore *= a.hp >= a.maxHp ? 1.5 : 1;
			case "snowcloak" -> miniscore *= weather.equals("hail") ? 1 : 1.1;
			case "furcoat" -> miniscore *= a.attack() > a.spatk() ? 1.5 : 1;
			case "fluffy" -> miniscore *= type.equals("fire") ? 0.75 : 1.5;
			case "waterbubble" -> miniscore *= type.equals("fire") ? 1.95 : 1.5;
			case "icescales" -> miniscore *= a.spatk() > a.attack() ? 1.5 : 1;
			default -> {
			}
		}
		return miniscore;
	}

	private double shellTrap() {
		double miniscore = faster() ? 0.5 : 1.0;
		Object[] best = bestMove(o, a);
		if (notOHKO(a, o)) {
			miniscore *= 1.2;
		} else {
			miniscore *= 0.8;
			if (best != null && (double) best[1] > a.hp) {
				miniscore *= 0.8;
			}
		}
		if ("shelltrap".equals(lastMove.get(a.battler.getUuid()))) {
			miniscore *= 0.7;
		}
		if (memory(SETUP)) {
			miniscore *= 0.6;
		}
		miniscore *= a.hp / a.maxHp;
		if (o.spatk() > o.attack()) {
			miniscore *= 0.3;
		}
		if (best != null && !((Mv) best[0]).physical) {
			miniscore *= 0.05;
		}
		return miniscore;
	}

	/** Reborn's {@code pussydeathcode} (Mind Blown, Steel Beam: half its HP). */
	private double mindBlown(double initialScore) {
		if (m.id.equals("mindblown") && (a.ability().equals("damp") || o.ability().equals("damp"))) {
			return 0;
		}
		double miniscore = 0.7;
		if (initialScore < 100) {
			miniscore *= 0.7;
		}
		if (!faster()) {
			miniscore *= 0.5;
		}
		if (expectedDamage(a, o) < a.maxHp * 0.2) {
			miniscore *= 1.3;
		}
		if (a.moves.stream().anyMatch(x -> HEALING.contains(x.id))) {
			miniscore *= 1.2;
		}
		if (badMoves(25)) {
			miniscore *= 1.3;
		}
		if (memory(PROTECT)) {
			miniscore *= 0.5;
		}
		miniscore *= (1 - 0.1 * o.stages[6]) * (1 + 0.1 * a.stages[5]);
		if (o.item().equals("laxincense") || o.item().equals("brightpowder")) {
			miniscore *= 0.7;
		}
		return miniscore;
	}

	private double teaTime() {
		double miniscore = a.item().endsWith("berry") ? berryBonus(a, a) : 1.0;
		if (!o.item().endsWith("berry")) {
			return miniscore;
		}
		return miniscore * switch (o.item()) {
			case "lumberry" -> o.status().isEmpty() ? 1 : 0.5;
			case "sitrusberry", "figyberry", "wikiberry", "magoberry", "aguavberry", "iapapaberry" -> o.hp / o.maxHp < 0.66 ? 0.65 : 1;
			case "liechiberry" -> o.attack() > o.spatk() ? 0.7 : 1;
			case "petayaberry" -> o.spatk() > o.attack() ? 0.7 : 1;
			case "custapberry", "salacberry" -> faster() ? 0.5 : 0.9;
			default -> 0.9;
		};
	}

	// ---------------------------------------------------------------- switching

	/** Reborn's {@code shouldSwitch?}: reasons to leave minus reasons to stay. */
	private double shouldSwitch(B attacker, B opponent, List<BattlePokemon> party, List<Mv> usable, double[] scores, boolean trapped) {
		if (trapped) {
			return -1000;
		}
		int count = 0;
		for (BattlePokemon pokemon : party) {
			if (pokemon != attacker.battler && pokemon.getHealth() > 0) {
				count++;
			}
		}
		if (count == 0) {
			return -1000;
		}
		double status = 0;
		if (attacker.has("curse")) {
			status += 80;
		}
		if (attacker.has("leechseed")) {
			status += 60;
		}
		if (attacker.has("attract")) {
			status += 60;
		}
		if (attacker.has("confusion")) {
			status += 80;
		}
		if (attacker.has("perish2")) {
			status += 40;
		} else if (attacker.has("perish1")) {
			status += 200;
		}
		if (attacker.status().equals("tox")) {
			status += 15 * toxicTurns(attacker);
		}
		if (attacker.ability().equals("naturalcure") && !attacker.status().isEmpty()) {
			status += 50;
		}
		boolean cleric = false;
		for (BattlePokemon pokemon : party) {
			cleric |= roles(pokemon).contains("cleric");
		}
		if (cleric && !attacker.status().isEmpty()) {
			status += 60;
		}
		if (attacker.status().equals("slp") && memoryHas(opponent, Set.of("dreameater", "nightmare"))) {
			status += 170;
		}
		if (attacker.has("yawn") && !attacker.status().equals("slp")) {
			status += 95;
		}
		// Stat drops.
		boolean special = attacker.moves.stream().anyMatch(m -> !m.status && !m.physical);
		boolean physical = attacker.moves.stream().anyMatch(m -> !m.status && m.physical);
		double stat = 0;
		int weight = attacker.roles.contains("sweeper") ? -30 : -15;
		if (attacker.stages[0] < 0 && physical) {
			stat += weight * attacker.stages[0];
		}
		if (attacker.stages[2] < 0 && special) {
			stat += weight * attacker.stages[2];
		}
		if (attacker.stages[4] < 0) {
			stat += weight * attacker.stages[4];
		}
		if (attacker.stages[5] < 0) {
			stat += weight * attacker.stages[5];
		}
		if (attacker.stages[1] < 0) {
			stat += (attacker.roles.contains("physicalwall") ? -30 : -15) * attacker.stages[1];
		}
		if (attacker.stages[3] < 0) {
			stat += (attacker.roles.contains("specialwall") ? -30 : -15) * attacker.stages[3];
		}
		// Healing (Reborn compares to 2/3 in integers, which never fires: the intended 2/3 here).
		double heal = attacker.hp / attacker.maxHp < 2.0 / 3 && attacker.ability().equals("regenerator") ? 30 : 0;
		// Forced out.
		double forced = 0;
		boolean bothImmune = true;
		boolean trickOrTreat = false;
		boolean forestsCurse = false;
		boolean notNormal = false;
		for (Mv move : attacker.moves) {
			trickOrTreat |= move.id.equals("trickortreat");
			forestsCurse |= move.id.equals("forestscurse");
			notNormal |= !move.type.getName().equalsIgnoreCase("normal");
			if (move.id.equals("destinybond")) {
				bothImmune = false;
			}
			if (POISON_MOVES.contains(move.id) && (opponent.status().isEmpty() && !opponent.hasType("poison") && !opponent.hasType("steel")
					|| opponent.status().equals("psn") || opponent.status().equals("tox"))) {
				bothImmune = false;
			}
			if (move.id.equals("perishsong") && !(opponent.ability().equals("soundproof") && !moldBreaker(attacker))) {
				bothImmune = false;
			}
			if (move.id.equals("leechseed") && (!opponent.hasType("grass") || opponent.has("leechseed"))) {
				bothImmune = false;
			}
			if (!move.status) {
				double mod = typeMod(move, attacker, opponent);
				if (opponent.ability().equals("wonderguard") && mod <= 1) {
					mod = 0;
				}
				if (mod != 0) {
					bothImmune = false;
				}
			}
		}
		if (bothImmune && !(trickOrTreat && notNormal || forestsCurse)) {
			forced += 140;
		}
		boolean hasPp = false;
		for (Mv move : usable) {
			hasPp |= !move.disabled && move.pp != 0;
		}
		if (!hasPp) {
			forced += 200;
		}
		if (attacker.has("torment")) {
			forced += 30;
		}
		List<Integer> enabled = new ArrayList<>();
		for (int m = 0; m < usable.size(); m++) {
			if (!usable.get(m).disabled) {
				enabled.add(m);
			}
		}
		if (attacker.has("encore") && enabled.size() == 1) {
			if (scores[enabled.get(0)] <= 30) {
				forced += 200;
			}
			if (attacker.has("torment")) {
				forced += 110;
			}
		}
		if ((Set.of("choiceband", "choicespecs", "choicescarf").contains(attacker.item()) || attacker.ability().equals("gorillatactics"))
				&& enabled.size() == 1 && usable.size() > 1) {
			double choice = scores[enabled.get(0)];
			forced += (choice <= 50 ? 50 : 0) + (choice <= 30 ? 130 : 0) + (choice <= 10 ? 150 : 0);
		}
		// Typing: the opponent's types on ours (a single type counts twice, as Reborn's type2 = type1).
		double typing = 0;
		List<String> foeTypes = new ArrayList<>(opponent.mon.types);
		if (foeTypes.size() == 1) {
			foeTypes.add(foeTypes.get(0));
		}
		for (String foeType : foeTypes) {
			ElementalType elemental = ElementalTypes.get(foeType);
			if (elemental == null) {
				continue;
			}
			double effect = ExpertBattleAI.effectiveness(elemental, attacker.mon, null);
			typing += effect > 1 ? 20 : effect < 1 ? -20 : 0;
		}
		// Special cases.
		double specialScore = 0;
		if (opponent.turncount() == 0 && expectedDamage(attacker, opponent) > attacker.hp && attacker.hp > 0.6 * attacker.maxHp
				&& !notOHKO(attacker, opponent)) {
			specialScore += 100;
		}
		String charge = charging.get(opponent.battler.getUuid());
		if (charge != null) {
			MoveTemplate template = com.cobblemon.mod.common.api.moves.Moves.getByName(charge);
			if (template != null) {
				for (BattlePokemon pokemon : party) {
					if (pokemon != attacker.battler && pokemon.getHealth() > 0
							&& ExpertBattleAI.effectiveness(template.getElementalType(), ExpertBattleAI.own(pokemon), null) < 1) {
						specialScore += 80;
						if (!faster(null, null, attacker, opponent)) {
							specialScore += 160;
						}
						break;
					}
				}
			}
		}
		if (opponent.ability().equals("skilllink") && memoryOf(opponent).stream().anyMatch(m -> m.multiHit() && m.contact())) {
			for (BattlePokemon pokemon : party) {
				Mon mon = ExpertBattleAI.own(pokemon);
				if (pokemon != attacker.battler && pokemon.getHealth() > 0
						&& (mon.item.equals("rockyhelmet") || mon.ability.equals("roughskin") || mon.ability.equals("ironbarbs"))) {
					specialScore += 70;
					break;
				}
			}
		}
		Mv expected = expectedMove(opponent, attacker);
		if (expected != null && expected.type.getName().equalsIgnoreCase("dark") && !attacker.ability().equals("justified")) {
			for (BattlePokemon pokemon : party) {
				if (pokemon != attacker.battler && pokemon.getHealth() > 0 && ExpertBattleAI.own(pokemon).ability.equals("justified")) {
					specialScore += 70;
					break;
				}
			}
		}
		double leave = status + stat + heal + forced + typing + specialScore;
		// Reasons to stay.
		double statAnti = 0;
		int boostWeight = attacker.roles.contains("sweeper") ? 30 : 15;
		if (attacker.stages[0] > 0 && physical) {
			statAnti += boostWeight * attacker.stages[0];
		}
		if (attacker.stages[2] > 0 && special) {
			statAnti += boostWeight * attacker.stages[2];
		}
		if (attacker.stages[4] > 0 && !(attacker.roles.contains("physicalwall") || attacker.roles.contains("specialwall") || attacker.roles.contains("tank"))) {
			statAnti += boostWeight * attacker.stages[4];
		}
		if (attacker.has("focusenergy")) {
			statAnti += 30 * 2;
		}
		if (attacker.stages[1] > 0) {
			statAnti += (attacker.roles.contains("physicalwall") ? 30 : 15) * attacker.stages[1];
		}
		if (attacker.stages[3] > 0) {
			statAnti += (attacker.roles.contains("specialwall") ? 30 : 15) * attacker.stages[3];
		}
		if (attacker.stages[6] > 0 && memoryOf(opponent).stream().noneMatch(m -> m.accuracy == 0)) {
			statAnti += 20 * attacker.stages[6];
		}
		if (attacker.has("substitute")) {
			statAnti += 100;
		}
		Map<String, Integer> mySideConditions = conditions(true);
		double hazardAnti = 15 * mySideConditions.getOrDefault("spikes", 0) + 15 * mySideConditions.getOrDefault("toxicspikes", 0)
				+ (mySideConditions.containsKey("stealthrock") ? 15 : 0) + (mySideConditions.containsKey("stickyweb") ? 15 : 0)
				+ (mySideConditions.containsKey("stickyweb") && attacker.roles.contains("sweeper") ? 15 : 0);
		if (attacker.hp / attacker.maxHp * 100 < hazardDamage(attacker)) {
			hazardAnti += 100;
		}
		boolean partyDies = true;
		for (int i = 0; i < party.size(); i++) {
			BattlePokemon pokemon = party.get(i);
			if (pokemon == attacker.battler || pokemon.getHealth() <= 0) {
				continue;
			}
			B mon = battler(pokemon, true, i, false);
			if (mon.roles.contains("ace") && hazardAnti > 0) {
				continue;
			}
			if (mon.hp / mon.maxHp * 100 > hazardDamage(mon)) {
				partyDies = false;
			}
		}
		if (partyDies) {
			hazardAnti += 200;
		}
		double better = 0;
		if (faster(null, null, attacker, opponent) && (attacker.hasMove("voltswitch") || attacker.hasMove("uturn") || attacker.hasMove("flipturn"))) {
			better += 90;
		}
		if (attacker.turncount() == 0) {
			better += 100;
		}
		if (attacker.hasMove("batonpass") && !attacker.has("perish1") && !attacker.has("perish2") && !attacker.has("perish3")) {
			better += 90;
		}
		if (attacker.ability().equals("wimpout") || attacker.ability().equals("emergencyexit")) {
			better += 60;
		}
		double secondWind = 0;
		if (!memoryPriority(opponent)) {
			if (faster(null, null, attacker, opponent)) {
				for (Mv move : attacker.moves) {
					if (!move.status && roughDamage(move, attacker, opponent) * 100 / Math.max(1, opponent.hp) > 100) {
						secondWind += 130;
						break;
					}
				}
			}
		} else {
			for (Mv move : attacker.moves) {
				if (isPriority(move, attacker) && roughDamage(move, attacker, opponent) * 100 / Math.max(1, opponent.hp) > 100
						&& faster(null, null, attacker, opponent)) {
					secondWind += 130;
				}
			}
		}
		double monTurn = 50 - attacker.turncount() * 25;
		if (attacker.roles.contains("lead")) {
			monTurn /= 1.5;
		}
		if (monTurn > 0) {
			secondWind += monTurn;
		}
		double stay = statAnti + hazardAnti + better + secondWind;
		lastSwitchParts = String.format(Locale.ROOT, "partir %d (statut %d, stats %d, forcé %d, types %d, spécial %d) − rester %d "
				+ "(boosts %d, pièges %d, options %d, second souffle %d)", (int) leave, (int) status, (int) stat, (int) forced,
				(int) typing, (int) specialScore, (int) stay, (int) statAnti, (int) hazardAnti, (int) better, (int) secondWind);
		return leave - stay;
	}

	private String lastSwitchParts = "";

	/** Reborn's {@code shouldHardSwitch?}: never into a Pokémon that dies to the expected hit or loses the race. */
	private boolean shouldHardSwitch(B attacker, B opponent, List<Mv> usable, BattlePokemon target) {
		for (Mv move : usable) {
			if (move.disabled) {
				return true;
			}
		}
		if (attacker.has("perish1") || attacker.has("perish2") || attacker.has("perish3")) {
			return true;
		}
		String last = lastMove.get(opponent.battler.getUuid());
		MoveTemplate lastTemplate = last == null ? null : com.cobblemon.mod.common.api.moves.Moves.getByName(last);
		if (lastTemplate != null && ExpertBattleAI.isStatus(lastTemplate)) {
			return true;
		}
		B in = battler(target, true, attacker.battler.getActor().getPokemonList().indexOf(target), false);
		double assumed = hazardDamage(in) * in.maxHp / 100;
		Mv expected = expectedMove(opponent, attacker);
		if (expected != null) {
			assumed += roughDamage(expected, opponent, in);
		}
		if (assumed > in.hp) {
			return false;
		}
		in.hp -= assumed;
		return canKillFirst(in, opponent);
	}

	/** Reborn's {@code getSwitchInScoresParty}: how good each Pokémon of the party would be coming in. */
	private int[] switchInScores(List<BattlePokemon> party, B attacker, B opponent, boolean hard) {
		int[] scores = new int[party.size()];
		boolean[] survivors = new boolean[party.size()];
		List<B> candidates = new ArrayList<>();
		int alive = 0;
		for (BattlePokemon pokemon : party) {
			alive += pokemon.getHealth() > 0 ? 1 : 0;
		}
		for (int index = 0; index < party.size(); index++) {
			BattlePokemon pokemon = party.get(index);
			if (attacker != null && pokemon == attacker.battler || pokemon.getHealth() <= 0 || pokemon.isSentOut() || !pokemon.canBeSentOut()) {
				scores[index] = -10000000;
				continue;
			}
			B i = battler(pokemon, true, index, false);
			candidates.add(i);
			Set<String> roles = i.roles;
			double monScore = 0;
			// Intimidate on the way in.
			int savedAttack = opponent.stages[0];
			if (i.ability().equals("intimidate") && !Set.of("clearbody", "whitesmoke", "hypercutter", "innerfocus", "owntempo",
					"oblivious", "scrappy", "fullmetalbody").contains(opponent.ability())) {
				opponent.stages[0] = opponent.ability().equals("contrary") || opponent.ability().equals("defiant") ? opponent.stages[0] + 1
						: Math.max(-6, opponent.stages[0] - 1);
			}
			double[] rough = new double[i.moves.size()];
			for (int m = 0; m < i.moves.size(); m++) {
				rough[m] = Math.min(100, roughDamage(i.moves.get(m), i, opponent) * 100 / Math.max(1, opponent.hp));
			}
			double best = 0;
			int bestMove = -1;
			for (int m = 0; m < rough.length; m++) {
				if (rough[m] > best || bestMove < 0) {
					best = rough[m];
					bestMove = m;
				}
			}
			// Defensive.
			double def = 0;
			double incoming = expectedDamage(i, opponent);
			double incomingPercent = incoming / Math.max(1, i.hp);
			if (incomingPercent > 1.0 && !canKillFirst(i, opponent)) {
				def -= 150;
			} else {
				survivors[index] = true;
			}
			def += incomingPercent < 0.5 ? 25 : 0;
			def += incomingPercent < 0.45 ? 10 : 0;
			def += incomingPercent < 0.4 ? 10 : 0;
			def += incomingPercent < 0.35 ? 10 : 0;
			def += incomingPercent < 0.3 ? 20 : 0;
			def += 2 * incomingPercent + hpGain(i) - 1 < 0.5 ? 50 : 0;
			def += incomingPercent < 0.2 ? 20 : 0;
			def += incomingPercent < 0.1 ? 30 : 0;
			def += 3 * incomingPercent + 2 * (hpGain(i) - 1) < 0.3 ? 50 : 0;
			if (hard && attacker != null && (incomingPercent < 1.0 || faster(null, null, opponent, i))) {
				// The hit aimed at the Pokémon leaving (Reborn compares its raw damage to fractions: the share is meant).
				Mv assumed = expectedMove(opponent, attacker);
				double share = assumed == null ? 0 : roughDamage(assumed, opponent, i) / Math.max(1, i.hp);
				def += share < 0.5 ? 30 : 0;
				def += share < 0.3 ? 50 : 0;
				def += share < 0.1 ? 90 : 0;
			}
			if (opponent.has("substitute")) {
				def *= 2;
			}
			monScore += def;
			// Offensive.
			double off = 0;
			off += best >= 100 ? 50 : 0;
			off += best > 90 ? 10 : 0;
			off += best > 80 ? 10 : 0;
			off += best > 70 ? 10 : 0;
			off += best > 60 ? 10 : 0;
			off += best >= 50 ? 50 : 0;
			Mv expected = expectedMove(opponent, i);
			off *= bestMove >= 0 && faster(i.moves.get(bestMove), expected, i, opponent) ? 1.25 : 0.75;
			monScore += off;
			// Roles.
			double role = 0;
			int reserves = alive - (attacker != null && attacker.hp > 0 ? 1 : 0);
			if (roles.contains("sweeper")) {
				role += reserves < 2 ? 60 : -50;
				role += i.attack() >= i.spatk() && opponent.mon.stat(Stats.DEFENCE) < opponent.mon.stat(Stats.SPECIAL_DEFENCE) ? 30 : 0;
				role += i.spatk() >= i.attack() && opponent.mon.stat(Stats.SPECIAL_DEFENCE) < opponent.mon.stat(Stats.DEFENCE) ? 30 : 0;
				for (int s = 0; s < 7; s++) {
					role += opponent.stages[s] < 0 ? -10 * opponent.stages[s] : 0;
				}
				boolean first = faster(null, null, i, opponent);
				role += first && role > 0 ? 10 : 0;
				role *= first && role > 0 ? 1.5 : 0.5;
				role += opponent.status().equals("slp") || opponent.status().equals("frz") ? 50 : 0;
			}
			if (roles.contains("physicalwall") || roles.contains("specialwall")) {
				role += roles.contains("physicalwall") && opponent.spatk() > opponent.attack() ? 30 : 0;
				role += roles.contains("specialwall") && opponent.spatk() < opponent.attack() ? 30 : 0;
				role += opponent.status().equals("brn") || opponent.status().equals("psn") || opponent.status().equals("tox")
						|| opponent.has("leechseed") ? 30 : 0;
			}
			if (roles.contains("tank")) {
				role += opponent.status().equals("par") || opponent.has("leechseed") ? 40 : 0;
				role += conditions(true).containsKey("tailwind") ? 30 : 0;
			}
			if (roles.contains("lead")) {
				role += 10;
				role += party.size() - reserves <= Math.ceil(party.size() / 2.0) ? 20 : 0;
			}
			if (roles.contains("cleric")) {
				boolean midHp = false;
				for (BattlePokemon other : party) {
					if (other == pokemon || other.getHealth() <= 0) {
						continue;
					}
					role += other.getEffectedPokemon().getStatus() != null ? 50 : 0;
					double share = other.getHealth() / (double) Math.max(1, other.getMaxHealth());
					midHp |= share > 0.3 && share < 0.6;
				}
				role += midHp ? 50 : 0;
			}
			if (roles.contains("phazer")) {
				role += 10 * Math.min(0, opponent.stages[0]) + 20 * Math.min(0, opponent.stages[1]) + 10 * Math.min(0, opponent.stages[2])
						+ 20 * Math.min(0, opponent.stages[3]) + 10 * Math.min(0, opponent.stages[4])
						+ (opponent.stages[5] < 0 ? 20 * opponent.stages[6] : 0);
			}
			role += roles.contains("screener") ? 60 : 0;
			// The revenge killer: a move landing first that kills.
			for (int m = 0; m < i.moves.size(); m++) {
				if (faster(i.moves.get(m), null, i, opponent) && rough[m] >= 100) {
					role += 110;
					break;
				}
			}
			if (roles.contains("spinner") && !opponent.hasType("ghost")) {
				Map<String, Integer> mine = conditions(true);
				role += 20 * mine.getOrDefault("spikes", 0) + 20 * mine.getOrDefault("toxicspikes", 0)
						+ (mine.containsKey("stickyweb") ? 30 : 0) + (mine.containsKey("stealthrock") ? 30 : 0);
			}
			role += roles.contains("pivot") ? 40 : 0;
			role += roles.contains("batonpasser") ? 50 : 0;
			role += roles.contains("stallbreaker") && memoryHealing(opponent) ? 80 : 0;
			if (roles.contains("statusabsorber") && memoryHas(opponent, union(BURN_MOVES, PARA_MOVES, SLEEP_MOVES, SCREEN))) {
				role += 70;
			}
			if (roles.contains("trapper") && faster(null, null, i, opponent) && opponent.hp / opponent.maxHp < 0.6) {
				role += 30;
			}
			if (roles.contains("weathersetter")) {
				role += 30;
				String ability = i.ability();
				if (ability.equals("drought") || i.hasMove("sunnyday")) {
					role += weather.equals("sunnyday") ? 0 : 60;
				} else if (ability.equals("drizzle") || i.hasMove("raindance")) {
					role += weather.equals("raindance") ? 0 : 60;
				} else if (ability.equals("sandstream") || ability.equals("sandspit") || i.hasMove("sandstorm")) {
					role += weather.equals("sandstorm") ? 0 : 60;
				} else if (ability.equals("snowwarning") || i.hasMove("hail") || i.hasMove("snowscape")) {
					role += weather.equals("hail") || weather.equals("snow") ? 0 : 60;
				} else if (Set.of("primordialsea", "desolateland", "deltastream").contains(ability)) {
					role += 60;
				}
			}
			if (roles.contains("fieldsetter")) {
				role += 30;
				String ability = i.ability();
				String sets = ability.equals("electricsurge") || i.hasMove("electricterrain") ? "electricterrain"
						: ability.equals("grassysurge") || i.hasMove("grassyterrain") ? "grassyterrain"
						: ability.equals("mistysurge") || i.hasMove("mistyterrain") ? "mistyterrain"
						: ability.equals("psychicsurge") || i.hasMove("psychicterrain") ? "psychicterrain" : "";
				role += !sets.isEmpty() && !terrain.equals(sets) ? 60 : 0;
			}
			monScore += role;
			// Weather.
			double weatherScore = 0;
			String ability = i.ability();
			switch (weather) {
				case "hail", "snow" -> {
					weatherScore += ability.equals("magicguard") || ability.equals("overcoat") || i.hasType("ice") ? 25 : 0;
					weatherScore += ability.equals("snowcloak") || ability.equals("icebody") ? 50 : 0;
					weatherScore += ability.equals("slushrush") ? 80 : 0;
				}
				case "raindance", "primordialsea" -> {
					weatherScore += Set.of("dryskin", "hydration", "raindish").contains(ability) ? 50 : 0;
					weatherScore += ability.equals("swiftswim") ? 80 : 0;
				}
				case "sunnyday", "desolateland" -> {
					weatherScore -= ability.equals("dryskin") ? 40 : 0;
					weatherScore += ability.equals("solarpower") ? 50 : 0;
					weatherScore += ability.equals("chlorophyll") ? 80 : 0;
				}
				case "sandstorm" -> {
					weatherScore += ability.equals("magicguard") || ability.equals("overcoat") || i.hasType("rock") || i.hasType("ground")
							|| i.hasType("steel") ? 25 : 0;
					weatherScore += ability.equals("sandveil") || ability.equals("sandforce") ? 50 : 0;
					weatherScore += ability.equals("sandrush") ? 80 : 0;
				}
				default -> {
				}
			}
			if (trickRoom) {
				weatherScore += i.speed() < opponent.speed() ? 50 : -50;
			}
			monScore += weatherScore;
			// Moves.
			double moves = 0;
			Map<String, Integer> mine = conditions(true);
			if (mine.getOrDefault("toxicspikes", 0) > 0) {
				moves += i.hasType("poison") && i.grounded() ? 80 : 0;
				moves += !i.grounded() || i.hasType("steel") ? 30 : 0;
			}
			if (i.hasMove("clearsmog") || i.hasMove("haze")) {
				for (int s = 0; s < 7; s++) {
					moves += opponent.stages[s] > 0 ? 10 * opponent.stages[s] : 0;
				}
			}
			moves += i.hasMove("fakeout") || i.hasMove("firstimpression") ? 25 : 0;
			if (i.hasMove("fellstinger") && faster(null, null, i, opponent) && opponent.hp / opponent.maxHp < 0.2) {
				moves += 50;
			}
			if (i.hasMove("tailwind")) {
				moves += mine.containsKey("tailwind") ? -60 : 30;
			}
			if (opponent.ability().equals("wonderguard") && (i.hasMove("pursuit") || i.hasMove("sandstorm") || i.hasMove("hail")
					|| i.hasMove("toxic") || i.hasMove("leechseed") || i.hasMove("curse") && i.hasType("ghost"))) {
				moves += 150;
			}
			monScore += moves;
			// Abilities.
			double abilityScore = 0;
			Mv expectedOnIn = expectedMove(opponent, i);
			boolean contactExpected = expectedOnIn != null && expectedOnIn.contact();
			String expectedType = expectedOnIn == null ? "" : expectedOnIn.type.getName().toLowerCase(Locale.ROOT);
			int opponentBoosts = 0;
			for (int s = 0; s < 7; s++) {
				opponentBoosts += Math.max(0, opponent.stages[s]);
			}
			switch (ability) {
				case "unaware" -> abilityScore += 10 * opponentBoosts;
				case "disguise", "iceface" -> abilityScore += 10 * opponentBoosts + (best >= 100 ? 50 : 0);
				case "drought", "desolateland" -> abilityScore += (opponent.hasType("water") ? 40 : 0)
						+ (memoryOf(opponent).stream().anyMatch(m -> m.type.getName().equalsIgnoreCase("water")) ? 15 : 0);
				case "drizzle", "primordialsea" -> abilityScore += (opponent.hasType("fire") ? 40 : 0)
						+ (memoryOf(opponent).stream().anyMatch(m -> m.type.getName().equalsIgnoreCase("fire")) ? 15 : 0);
				case "limber" -> abilityScore += memoryHas(opponent, PARA_MOVES) ? 15 : 0;
				case "oblivious" -> abilityScore += (opponent.ability().equals("cutecharm") ? 20 : 0) + (memoryHas(opponent, Set.of("attract")) ? 20 : 0);
				case "compoundeyes" -> abilityScore += opponent.item().equals("laxincense") || opponent.item().equals("brightpowder")
						|| opponent.stages[6] > 0 ? 25 : 0;
				case "comatose" -> abilityScore += (memoryHas(opponent, BURN_MOVES) ? 20 : 0) + (memoryHas(opponent, PARA_MOVES) ? 20 : 0)
						+ (memoryHas(opponent, SLEEP_MOVES) ? 20 : 0) + (memoryHas(opponent, POISON_MOVES) ? 20 : 0);
				case "insomnia", "vitalspirit" -> abilityScore += memoryHas(opponent, SLEEP_MOVES) ? 20 : 0;
				case "poisonheal", "toxicboost", "immunity" -> abilityScore += memoryHas(opponent, POISON_MOVES) ? 20 : 0;
				case "magicguard" -> abilityScore += (memoryHas(opponent, Set.of("leechseed")) ? 20 : 0)
						+ (memoryHas(opponent, Set.of("willowisp")) ? 20 : 0) + (memoryHas(opponent, POISON_MOVES) ? 20 : 0);
				case "waterveil", "flareboost" -> abilityScore += memoryHas(opponent, BURN_MOVES) ? (ability.equals("flareboost") ? 20 : 10) : 0;
				case "waterbubble" -> abilityScore += (memoryHas(opponent, BURN_MOVES) ? 10 : 0) + (expectedType.equals("fire") ? 30 : 0);
				case "owntempo" -> abilityScore += memoryHas(opponent, CONFUSE_MOVES) ? 20 : 0;
				case "intimidate", "furcoat", "stamina" -> abilityScore += opponent.attack() > opponent.spatk() ? 40 : 0;
				case "wonderguard" -> {
					boolean dies = memoryOf(opponent).stream().anyMatch(m -> Set.of("fire", "ghost", "dark", "rock", "flying")
							.contains(m.type.getName().toLowerCase(Locale.ROOT)));
					boolean instant = weather.equals("hail") || weather.equals("sandstorm") || i.status().equals("brn") || i.status().equals("psn")
							|| i.status().equals("tox") || mine.containsKey("stealthrock") || mine.containsKey("spikes") || mine.containsKey("toxicspikes");
					dies |= instant || moldBreaker(opponent);
					abilityScore += (!dies ? 90 : 0) - (instant ? 90 : 0);
				}
				case "effectspore", "static", "poisonpoint", "flamebody", "cutecharm", "mummy", "aftermath", "gooey", "fluffy", "perishbody",
						"wanderingspirit" -> abilityScore += contactExpected && !(ability.equals("fluffy") && opponent.hasType("fire")) ? 30 : 0;
				case "ironbarbs", "roughskin" -> abilityScore += (contactExpected ? 30 : 0) + (opponent.ability().equals("skilllink") ? 30 : 0);
				case "cottondown" -> abilityScore += incomingPercent < 0.5 ? (best >= 60 ? 50 : 30) : 0;
				case "trace" -> abilityScore += Set.of("waterabsorb", "voltabsorb", "stormdrain", "motordrive", "flashfire", "levitate",
						"lightningrod", "sapsipper", "dryskin", "slushrush", "sandrush", "swiftswim", "chlorophyll", "speedboost", "wonderguard",
						"prankster").contains(opponent.ability())
						|| faster(null, null, i, opponent) && Set.of("adaptability", "download", "protean", "libero").contains(opponent.ability())
						|| opponent.attack() > opponent.spatk() && opponent.ability().equals("intimidate") || opponent.ability().equals("unaware")
						|| i.hp >= i.maxHp && (opponent.ability().equals("multiscale") || opponent.ability().equals("shadowshield")) ? 60 : 0;
				case "magmaarmor" -> abilityScore += memoryOf(opponent).stream().anyMatch(m -> m.type.getName().equalsIgnoreCase("ice")) ? 20 : 0;
				case "soundproof" -> abilityScore += expectedOnIn != null && expectedOnIn.sound() ? 60 : 0;
				case "thickfat" -> abilityScore += expectedType.equals("ice") || expectedType.equals("fire") ? 30 : 0;
				case "liquidooze" -> abilityScore += memoryHas(opponent, Set.of("leechseed", "gigadrain", "drainpunch", "hornleech",
						"leechlife", "drainingkiss", "paraboliccharge", "strengthsap")) ? 40 : 0;
				case "scrappy" -> abilityScore += opponent.hasType("ghost") ? 30 : 0;
				case "lightmetal" -> abilityScore += memoryHas(opponent, Set.of("grassknot", "lowkick")) ? 10 : 0;
				case "analytic" -> abilityScore += !faster(null, null, i, opponent) ? 30 : 0;
				case "illusion" -> abilityScore += 40;
				case "moxie", "beastboost", "soulheart", "grimneigh", "chillingneigh", "asonechilling", "asonegrim" -> abilityScore +=
						faster(null, null, i, opponent) && opponent.hp / opponent.maxHp < 0.5 ? 40 : 0;
				case "speedboost" -> abilityScore += faster(null, null, i, opponent) && opponent.hp / opponent.maxHp < 0.3 ? 25 : 0;
				case "justified" -> abilityScore += expectedType.equals("dark") ? 30 : 0;
				case "rattled" -> abilityScore += Set.of("dark", "ghost", "bug").contains(expectedType) ? 15 : 0;
				case "prankster" -> abilityScore += !faster(null, null, i, opponent) && !opponent.hasType("dark") ? 50 : 0;
				case "galewings" -> abilityScore += !faster(null, null, i, opponent) && i.hp >= i.maxHp && !mine.containsKey("stealthrock") ? 50 : 0;
				case "bulletproof" -> abilityScore += expectedOnIn != null && BULLET.contains(expectedOnIn.id) ? 60 : 0;
				case "protean", "libero" -> abilityScore += faster(null, null, i, opponent) ? 40 : 0;
				case "dancer" -> abilityScore += memoryHas(opponent, DANCE) ? 30 : 0;
				case "merciless" -> abilityScore += opponent.status().equals("psn") || opponent.status().equals("tox") ? 50 : 0;
				case "dazzling", "queenlymajesty", "armortail" -> abilityScore += memoryPriority(opponent) ? 20 : 0;
				case "sandstream", "snowwarning", "sandspit" -> abilityScore += opponent.ability().equals("wonderguard") ? 70 : 0;
				case "defeatist" -> abilityScore -= hard ? 80 : 0;
				case "sturdy" -> abilityScore -= hard && i.hp >= i.maxHp ? 80 : 0;
				case "icescales" -> abilityScore += opponent.spatk() > opponent.attack() ? 40 : 0;
				case "unseenfist" -> abilityScore += memoryHas(opponent, PROTECT) ? 30 : 0;
				default -> {
				}
			}
			monScore += abilityScore;
			// Items.
			double itemScore = 0;
			String item = i.item();
			if (item.equals("rockyhelmet")) {
				itemScore += (opponent.ability().equals("skilllink") ? 30 : 0) + (contactExpected ? 30 : 0);
			}
			if (item.equals("airballoon")) {
				List<Mv> seen = memoryOf(opponent);
				boolean allGround = seen.stream().allMatch(m -> m.type.getName().equalsIgnoreCase("ground"));
				itemScore += (expectedType.equals("ground") ? 60 : 0) + (allGround ? 100 : 0);
			}
			if (item.equals("floatstone") && memoryHas(opponent, Set.of("lowkick", "grassknot"))) {
				itemScore += 10;
			}
			if (item.equals("destinyknot")) {
				itemScore += (opponent.ability().equals("cutecharm") ? 20 : 0) + (memoryHas(opponent, Set.of("attract")) ? 20 : 0);
			}
			if (item.equals("absorbbulb") && expectedType.equals("water") || item.equals("cellbattery") && expectedType.equals("electric")
					|| item.equals("snowball") && expectedType.equals("ice")) {
				itemScore += 25;
			}
			if ((item.equals("focussash") || ability.equals("sturdy")) && i.hp >= i.maxHp) {
				boolean chip = weather.equals("sandstorm") && !(i.hasType("rock") || i.hasType("ground") || i.hasType("steel"))
						|| weather.equals("hail") && !i.hasType("ice") || mine.containsKey("stealthrock") || mine.containsKey("spikes")
						|| mine.containsKey("toxicspikes");
				if (chip && !ability.equals("magicguard") && !ability.equals("overcoat")) {
					itemScore -= 40;
				}
				if (hard) {
					itemScore -= 80;
				}
				itemScore += 30 * (opponent.stages[0] + opponent.stages[2] + opponent.stages[4]);
			}
			if (item.equals("protectivepads") && (Set.of("effectspore", "static", "poisonpoint", "roughskin", "ironbarbs", "flamebody",
					"cutecharm", "mummy", "aftermath", "gooey", "perishbody", "wanderingspirit").contains(opponent.ability())
					|| opponent.item().equals("rockyhelmet"))) {
				itemScore += 25;
			}
			monScore += itemScore;
			// Other.
			double other = 0;
			if (hard && attacker != null && attacker.mon.name.equals(i.mon.name)) {
				other -= 70;
			}
			if (opponent.ability().equals("wonderguard") && best == 0) {
				other -= 100;
			}
			monScore += other;
			opponent.stages[0] = savedAttack;
			if (mine.containsKey("stealthrock") || mine.containsKey("spikes")) {
				monScore = Math.floor(monScore * i.hp / i.maxHp);
			}
			if (hazardDamage(i) > i.hp / i.maxHp * 100) {
				monScore = 1;
			}
			if (roles.contains("ace")) {
				monScore *= 0.9 - 0.1 * alive;
			}
			scores[index] = (int) Math.floor(monScore);
		}
		// Nothing survives: what can still be done.
		boolean anySurvivor = false;
		for (int index = 0; index < party.size(); index++) {
			anySurvivor |= survivors[index];
		}
		if (!anySurvivor && !candidates.isEmpty()) {
			boolean contactProof = opponent.ability().equals("magicguard") || opponent.item().equals("protectivepads")
					|| opponent.ability().equals("longreach");
			double opponentShare = opponent.hp / opponent.maxHp * 100;
			for (B mon : candidates) {
				Mv expected = expectedMove(opponent, mon);
				boolean contact = expected != null && expected.contact();
				if (mon.ability().equals("innardsout") && mon.hp >= opponent.hp) {
					scores[mon.partyIndex] += 500;
				}
				if (!contactProof && contact) {
					if (mon.ability().equals("aftermath") && 25 >= opponentShare
							|| mon.item().equals("rockyhelmet") && 100.0 / 6 >= opponentShare
							|| (mon.ability().equals("ironbarbs") || mon.ability().equals("roughskin")) && 12.5 >= opponentShare) {
						scores[mon.partyIndex] += 200;
					}
				}
				if (mon.ability().equals("intimidate") && opponent.attack() > opponent.spatk() && !Set.of("defiant", "contrary", "clearbody",
						"whitesmoke", "hypercutter", "scrappy", "oblivious", "owntempo", "innerfocus").contains(opponent.ability())) {
					int saved = opponent.stages[0];
					opponent.stages[0] = Math.max(-6, saved - 1);
					for (B other : candidates) {
						if (other != mon && expectedDamage(other, opponent) <= other.hp) {
							scores[mon.partyIndex] += 200;
						}
					}
					opponent.stages[0] = saved;
				}
				if (mon.ability().equals("prankster") && !opponent.hasType("dark")) {
					boolean useful = mon.moves.stream().anyMatch(m -> m.status && (SLEEP_MOVES.contains(m.id) && opponent.status().isEmpty()
							|| PARA_MOVES.contains(m.id) && opponent.status().isEmpty() && !trickRoom && !opponent.ability().equals("quickfeet")
							|| BURN_MOVES.contains(m.id) && opponent.status().isEmpty() && opponent.attack() > opponent.spatk()
									&& !opponent.ability().equals("guts")));
					if (useful) {
						scores[mon.partyIndex] += 300;
					}
				}
			}
			double prioritySum = 0;
			List<B> priorityUsers = new ArrayList<>();
			for (B mon : candidates) {
				double best = 0;
				Mv theirs = expectedMove(opponent, mon);
				for (Mv move : mon.moves) {
					if (!isPriority(move, mon) || move.status) {
						continue;
					}
					int theirPriority = theirs == null ? 0 : priority(theirs, opponent);
					if (priority(move, mon) < theirPriority || priority(move, mon) == theirPriority && !faster(null, null, mon, opponent)) {
						continue;
					}
					best = Math.max(best, roughDamage(move, mon, opponent));
				}
				if (best > 0) {
					prioritySum += best;
					priorityUsers.add(mon);
				}
			}
			if (prioritySum > opponent.hp) {
				for (B mon : priorityUsers) {
					scores[mon.partyIndex] += 200;
				}
			}
		}
		return scores;
	}

	@SafeVarargs
	private static Set<String> union(Set<String>... sets) {
		Set<String> all = new HashSet<>();
		for (Set<String> set : sets) {
			all.addAll(set);
		}
		return all;
	}

	// ---------------------------------------------------------------- debug

	private static String name(BattlePokemon pokemon) {
		return pokemon.getEffectedPokemon().getSpecies().getTranslatedName().getString();
	}

	private String switchInLine(List<BattlePokemon> party, int[] scores) {
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < party.size(); i++) {
			if (scores[i] > -1000000) {
				line.append(line.length() > 0 ? ", " : "").append(name(party.get(i))).append(" ").append(scores[i]);
			}
		}
		return line.toString();
	}

	private void report(B attacker, B opponent, List<Mv> usable, double[] percent, double[] scores, int zSlot, double shouldSwitch,
			double maxMove, int[] switchScores, List<BattlePokemon> party) {
		say("§6[Reborn] §f" + name(attacker.battler) + " §7" + ExpertBattleAI.percent(attacker.hp, attacker.maxHp) + " §fcontre "
				+ name(opponent.battler) + " §7" + ExpertBattleAI.percent(opponent.hp, opponent.maxHp)
				+ (faster(null, null, attacker, opponent) ? " · plus rapide" : " · plus lent"));
		StringBuilder seen = new StringBuilder("§7Mémoire : ");
		List<Mv> known = memoryOf(opponent);
		if (known.isEmpty()) {
			seen.append("rien vu");
		}
		for (int m = 0; m < known.size(); m++) {
			seen.append(m > 0 ? ", " : "").append(known.get(m).name());
		}
		Mv expected = expectedMove(opponent, attacker);
		if (expected != null) {
			seen.append(" · attendu : ").append(expected.name()).append(" ")
					.append(ExpertBattleAI.percent(expectedDamage(attacker, opponent), attacker.maxHp));
		}
		say(seen.toString());
		StringBuilder moves = new StringBuilder("§7Coups : ");
		for (int m = 0; m < usable.size(); m++) {
			moves.append(m > 0 ? " · " : "").append(usable.get(m).name()).append(" ").append((int) scores[m])
					.append(" §8(").append((int) percent[m]).append(")§7");
		}
		if (zSlot >= 0) {
			moves.append(" · Z ").append(usable.get(zSlot).name()).append(" ").append((int) scores[usable.size()]);
		}
		say(moves.toString());
		String switching = "§7Switch : " + (int) shouldSwitch + " contre meilleur coup " + (int) maxMove + " · " + lastSwitchParts;
		if (switchScores != null) {
			switching += " · entrants : " + switchInLine(party, switchScores);
		}
		say(switching);
	}

	private static void say(String line) {
		ExpertBattleAI.say(line);
	}
}
