/*
 * League Reborn: Pokémon Reborn's field effects, in Cobblemon's Showdown engine.
 *
 * Evaluated once in the Showdown context (Cobblemon's GraalJS, or Node for the tests) as a function expression and
 * called with Showdown's Dex and the field data (fields.json, made by tools/reborn-fields/convert.js from Reborn's
 * fieldtext.rb). It adds:
 *   - the rule "League Reborn = <field>": only battles with it change; it lays the field down at the start;
 *   - the condition holding the field stack (a field-wide "pseudo-weather" with no duration).
 *
 * Mechanics follow Reborn's scripts (Battle_Field.rb, Battle_Move.rb pbCalcDamage, Battler.rb pbUseMove); the
 * comments name the Reborn method each part reproduces. Field-specific code, written by hand in Reborn's engine, goes
 * in HOOKS (one entry per field), added batch by batch.
 *
 * Messages are protocol lines `|-leaguereborn|<kind>|...` that the mod translates (our own texts, in its lang files):
 *   start <field> | change <from> <to> <msg index> | end <field> (temporary field over) |
 *   move <field> <msg index> | type <field> <msg index> | text <key> [args...]
 */
(function (Dex, FIELDS) {
	'use strict';
	const RULE = 'leaguereborn';
	const STATE = 'leaguerebornfield';
	const NONE = 'indoor';
	const toID = Dex.toID;

	/** Showdown terrain -> the Reborn field a terrain move or Surge ability lays down for a few turns instead. */
	const TERRAINS = {electricterrain: 'electerrain', grassyterrain: 'grassy', mistyterrain: 'misty', psychicterrain: 'psyterrain'};
	const FLOWERGARDEN = ['flowergarden1', 'flowergarden2', 'flowergarden3', 'flowergarden4', 'flowergarden5'];
	/** Fields where no weather can be set (Battle#canSetWeather?) and that no move can change (canChangeFE?). */
	const LOCKED = ['underwater', 'newworld'];

	/** Field-specific code, filled per field (see the batches in Documentation/terrains/conception.md). */
	const HOOKS = {};

	const LR = {
		state(battle) {
			return battle.field.pseudoWeather[STATE] || null;
		},
		/** The current field id, or null when the battle is not a League Reborn battle. */
		field(battle) {
			const s = LR.state(battle);
			return s ? s.layer[s.layer.length - 1] || NONE : null;
		},
		data(battle) {
			const f = LR.field(battle);
			return f ? FIELDS[f] : null;
		},
		hook(battle, name) {
			const h = HOOKS[LR.field(battle)];
			return h && h[name];
		},
		say(battle, ...parts) {
			battle.add('-leaguereborn', ...parts);
		},

		// --- The stack (Battle_Field.rb: setField, breakField, endTempField) -------------------------------------

		reset(s) {
			s.counter = [0, 0, 0, 0];
			s.pledge = null;
		},
		canChange(battle, to) {
			const f = LR.field(battle);
			return !(LOCKED.includes(f) || (to && f === to));
		},
		set(battle, to, temp = 0, {addOn = false, growth = false} = {}) {
			const s = LR.state(battle);
			const from = LR.field(battle);
			if (from === to && to !== 'rainbow') return false;
			if (temp > 0) {
				if (s.overlay == null) s.overlay = s.layer.length;
				s.turns = temp;
			}
			if (temp <= 0 && !addOn) s.layer.pop();
			s.layer.push(to);
			LR.reset(s);
			LR.changed(battle, from, growth);
			return true;
		},
		/** Back to the field below (breakField): a change to "no field". */
		breakField(battle) {
			const s = LR.state(battle);
			const from = s.layer.pop();
			if (s.overlay != null && s.layer.length < s.overlay) {
				s.overlay = null;
				s.turns = 0;
			}
			LR.reset(s);
			LR.changed(battle, from, false);
		},
		/** A temporary field is over: back to what was under it (endTempField). */
		endTemp(battle) {
			const s = LR.state(battle);
			if (s.overlay == null) return;
			const from = LR.field(battle);
			while (s.layer.length > s.overlay) s.layer.pop();
			if (FLOWERGARDEN.includes(s.layer[s.layer.length - 1])) s.layer[s.layer.length - 1] = 'flowergarden1';
			s.overlay = null;
			s.turns = 0;
			LR.reset(s);
			LR.say(battle, 'end', from);
			LR.changed(battle, from, false);
		},
		/** What follows any field change (pbChangeBGSprite, abilities on field, seedCheck, noWeather). */
		changed(battle, from, growth) {
			const to = LR.field(battle);
			LR.say(battle, 'field', to);
			if (LOCKED.includes(to) && battle.field.weather) battle.field.clearWeather();
			const leave = HOOKS[from] && HOOKS[from].onLeave;
			if (leave) leave.call(battle, to);
			const enter = HOOKS[to] && HOOKS[to].onEnter;
			if (enter) enter.call(battle, from, growth);
			for (const pokemon of battle.getAllActive()) battle.runEvent('LeagueRebornField', pokemon, null, null, to);
		},
		backup(battle) {
			const s = LR.state(battle);
			return s.layer[s.layer.length - 2] || NONE;
		},

		// --- Move data (Battle_Move.rb: moveFieldBoost, typeFieldBoost, fieldTypeChange, pbAccuracyCheck) --------

		moveData(battle, move) {
			const d = LR.data(battle);
			return d && d.moves[move.id];
		},
		typeData(battle, move) {
			const d = LR.data(battle);
			return d && d.types[move.type];
		},
		/** Starlight Arena: no field boost under any weather but strong winds. */
		boostsOff(battle) {
			return LR.field(battle) === 'starlight' && battle.field.weather && battle.field.weather !== 'deltastream';
		},
		/** The type a field adds to a move (fieldTypeChange): field code first, then the move's, then its type's. */
		addedType(battle, move, source) {
			const h = LR.hook(battle, 'addedType');
			const special = h && h.call(battle, move, source);
			if (special) return special;
			const md = LR.moveData(battle, move);
			if (md && md.type) return md.type;
			const td = LR.typeData(battle, move);
			return td && td.type;
		},
		/** The type boost (typeFieldBoost), 1 when its condition is not met. */
		typeBoost(battle, move, source, target) {
			const td = LR.typeData(battle, move);
			if (!td || td.mult === undefined || LR.boostsOff(battle)) return 1;
			if (td.cond && source && target && !CONDITIONS[td.cond].call(battle, source, target, move)) return 1;
			return td.mult;
		},
		/** shouldApplyFieldChangeDamageBoost: the move will change the field (counters counted as if increased). */
		willChange(battle, move, source, target) {
			const md = LR.moveData(battle, move);
			if (!md || !md.change) return false;
			const s = LR.state(battle);
			const saved = s.counter.slice();
			if (md.counter) s.counter[md.counter[0] - 1] += md.counter[1];
			const cond = LR.data(battle).changeCond[md.change];
			let ok = !cond || CONDITIONS[cond].call(battle, source, target, move);
			const f = LR.field(battle);
			if (FLOWERGARDEN.includes(f) && FLOWERGARDEN.includes(md.change)) ok = false;
			s.counter = saved;
			return ok;
		},
	};

	/** Conditions of the data (Ruby kept as strings in fieldtext.rb, named by convert.js). */
	const CONDITIONS = {
		attackerGrounded(source) {
			return source.isGrounded();
		},
		targetGrounded(source, target) {
			return !target || target.isGrounded();
		},
		/** ignitecheck: no Water Sport, no rain. */
		ignite() {
			return !this.field.pseudoWeather.watersport && !['raindance', 'primordialsea'].includes(this.field.effectiveWeather());
		},
		counterAbove1() {
			return LR.state(this).counter[0] > 1;
		},
		never() {
			return false;
		},
		special(source, target, move) {
			return move.category === 'Special';
		},
		backupIsWater() {
			return ['watersurface', 'murkwatersurface'].includes(LR.backup(this));
		},
		notScaldOrSteamEruption(source, target, move) {
			return move.id !== 'scald' && move.id !== 'steameruption';
		},
		nonContact(source, target, move) {
			return !move.flags.contact;
		},
	};

	/**
	 * A battler shielded from a field's blast (the shared checks of mistExplosion, caveCollapse, mirrorShatter):
	 * PBStuff::INVULEFFECTS (Protect, Endure, Mat Block: Endure blocks the whole blast, not just the last HP),
	 * a two-turn move under way, Sky Drop, Wide Guard, Mat Block, Quick Guard against a priority move.
	 */
	function shielded(battle, pokemon, source, move) {
		if (pokemon.volatiles.protect || pokemon.volatiles.endure) return true;
		if (pokemon.volatiles.skydrop || pokemon.volatiles.twoturnmove) return true;
		const side = pokemon.side.sideConditions;
		if (side.wideguard || side.matblock) return true;
		if (side.quickguard && move.priority > 0) return true;
		return false;
	}

	/** Effects of the data (Battle_Field.rb), run after a move. */
	const EFFECTS = {
		/** fieldAccuracyDrop: every battler not in the air or underground loses 1 accuracy. */
		accuracyDrop(source, move) {
			const f = LR.field(this);
			LR.say(this, 'text', f === 'ashenbeach' ? 'ash_stirred' : 'steam');
			for (const pokemon of this.getAllActive()) {
				if (pokemon.volatiles.twoturnmove || pokemon.volatiles.skydrop) continue;
				this.boost({accuracy: -1}, pokemon, source, null, true);
			}
		},
		/** iceSpikes: a quake breaks the ice into a layer of Spikes on both sides (not over water). */
		iceSpikes() {
			if (['watersurface', 'murkwatersurface'].includes(LR.backup(this))) return;
			let added = false;
			for (const side of this.sides) {
				const spikes = side.sideConditions.spikes;
				if (!spikes || spikes.layers < 3) {
					side.addSideCondition('spikes', 'debug');
					added = true;
				}
			}
			if (added) LR.say(this, 'text', 'ice_spikes');
		},
		/** waterPollution: everything not Poison or Steel, and not mid-move above water, faints. */
		waterPollution() {
			LR.say(this, 'text', 'water_polluted');
			for (const pokemon of this.getAllActive()) {
				if (pokemon.volatiles.twoturnmove) continue;
				if (pokemon.hasType('Poison') || pokemon.hasType('Steel')) continue;
				this.directDamage(pokemon.maxhp, pokemon);
			}
			LR.state(this).counter[0] = 0;
		},
		/** mistExplosion: the corrosive mist blows up, unless a battler has Damp. */
		mistExplosion(source, move) {
			if (this.getAllActive().some((p) => p.hasAbility('damp'))) {
				LR.say(this, 'text', 'damp_prevented');
				return;
			}
			LR.say(this, 'text', 'mist_combusted');
			for (const pokemon of this.getAllActive()) {
				if (shielded(this, pokemon, source, move) || pokemon.hasAbility('flashfire')) continue;
				let damage = pokemon.maxhp;
				if (pokemon.hasAbility('sturdy')) damage -= 1; // Sturdy keeps 1 HP of the total, whatever the HP
				if (damage > 0) this.directDamage(Math.min(damage, pokemon.hp), pokemon);
			}
		},
		/** caveCollapse: a first quake shakes the ceiling, the second brings it down. */
		caveCollapse(source, move) {
			const s = LR.state(this);
			if (s.counter[0] === 0) {
				LR.say(this, 'text', 'cave_crumbling');
				s.counter[0] += 1;
				return;
			}
			s.counter[0] = 0;
			LR.say(this, 'text', 'cave_collapsed');
			for (const pokemon of this.getAllActive()) {
				if (shielded(this, pokemon, source, move)) continue;
				if (pokemon.hasAbility(['bulletproof', 'rockhead', 'stalwart'])) continue;
				let damage = pokemon.maxhp;
				if (pokemon.hasAbility('sturdy')) damage -= 1;
				if (pokemon.hasAbility(['shellarmor', 'battlearmor'])) damage = Math.floor(damage / 2);
				if (pokemon.hasAbility(['prismarmor', 'solidrock'])) damage = Math.floor(damage / 3);
				if (damage > 0) this.directDamage(Math.min(damage, pokemon.hp), pokemon);
			}
		},
		/** mirrorShatter: the mirrors burst and cut everyone for half their HP. */
		mirrorShatter(source, move) {
			for (const pokemon of this.getAllActive()) {
				if (shielded(this, pokemon, source, move)) continue;
				if (pokemon.hasAbility(['shellarmor', 'battlearmor'])) continue;
				const damage = Math.floor(pokemon.maxhp / 2);
				if (damage > 0) this.directDamage(Math.min(damage, pokemon.hp), pokemon);
			}
		},
	};

	/** fieldEffectAfterMove: type effect, counters, move effect, then the field change. */
	function afterMove(battle, source, target, move) {
		const s = LR.state(battle);
		const md = LR.moveData(battle, move);
		const td = LR.typeData(battle, move);
		if (td && td.effect && (!td.cond || CONDITIONS[td.cond].call(battle, source, target, move))) {
			EFFECTS[td.effect].call(battle, source, move);
		}
		const h = LR.hook(battle, 'afterMove');
		if (h) h.call(battle, source, target, move);
		if (!md) return;
		if (md.counter) s.counter[md.counter[0] - 1] += md.counter[1];
		if (md.effect) EFFECTS[md.effect].call(battle, source, move);
		if (!md.change) return;
		const from = LR.field(battle);
		const cond = LR.data(battle).changeCond[md.change];
		if (cond && !CONDITIONS[cond].call(battle, source, target, move)) return;
		if (md.changeMsg) LR.say(battle, 'change', from, md.change, md.changeMsg);
		if (md.changeEffect) EFFECTS[md.changeEffect].call(battle, source, move);
		let to = md.change;
		// Ripen jumps the Flower Garden two stages at once.
		if (FLOWERGARDEN.includes(from) && FLOWERGARDEN.indexOf(from) < 3 && source.hasAbility('ripen') &&
			FLOWERGARDEN.indexOf(from) < FLOWERGARDEN.indexOf(to)) {
			const next = FIELDS[to].moves[move.id];
			if (next && next.change) to = next.change;
		}
		if (to === NONE) {
			LR.breakField(battle);
		} else {
			LR.set(battle, to, 0, {addOn: !!md.keepBackup, growth: FLOWERGARDEN.includes(from) && FLOWERGARDEN.includes(to)});
		}
	}

	// --- The rule ----------------------------------------------------------------------------------------------

	Dex.data.Rulesets[RULE] = {
		effectType: 'Rule',
		name: 'League Reborn',
		desc: "Pokémon Reborn's field effects; the value is the field the battle starts on.",
		hasValue: true,
		onValidateRule(value) {
			const id = toID(value);
			if (!FIELDS[id] || id === NONE) throw new Error(`League Reborn: unknown field "${value}"`);
			return id;
		},
		onBegin() {
			const field = this.ruleTable.valueRules.get(RULE);
			this.field.addPseudoWeather(STATE);
			const s = LR.state(this);
			s.layer = [field];
			s.overlay = null;
			s.turns = 0;
			LR.reset(s);
			LR.say(this, 'start', field);
			// Showdown's four terrains become Reborn's temporary fields (Electric Terrain the move, Electric Surge...).
			const battle = this;
			const sdField = this.field;
			sdField.setTerrain = function (status, source, sourceEffect) {
				const to = TERRAINS[toID(typeof status === 'string' ? status : status.id)];
				if (!to || !LR.canChange(battle, to)) return false;
				source = source || (battle.event && battle.event.target) || null;
				const item = source && source.getItem ? source.getItem().id : '';
				const turns = item === 'amplifieldrock' || item === 'terrainextender' ? 8 : 5;
				return LR.set(battle, to, turns);
			};
			sdField.clearTerrain = () => false;
			sdField.isTerrain = function (terrain) {
				const f = LR.field(battle);
				const list = Array.isArray(terrain) ? terrain : [terrain];
				return list.some((t) => TERRAINS[toID(t)] === f);
			};
			const setWeather = sdField.setWeather.bind(sdField);
			sdField.setWeather = function (...args) {
				if (LOCKED.includes(LR.field(battle))) return false;
				return setWeather(...args);
			};
			const h = HOOKS[field] && HOOKS[field].onEnter;
			if (h) h.call(this, null, false);
		},
	};

	// --- The field condition: Showdown events -> Reborn's rules ---------------------------------------------------

	Dex.data.Conditions[STATE] = {
		name: STATE, // must differ from the rule's name: conditions and rules share ids
		effectType: 'Condition',

		/** Field moves and types that fail outright (a multiplier of 0). */
		onTryMove(source, target, move) {
			const md = LR.moveData(this, move);
			const failsMove = md && md.mult === 0;
			const failsType = move.category !== 'Status' && LR.typeBoost(this, move, source, target) === 0;
			if (!failsMove && !failsType) return;
			if (md && md.msg) LR.say(this, 'move', LR.field(this), md.msg);
			else {
				const td = LR.typeData(this, move);
				if (td && td.msg) LR.say(this, 'type', LR.field(this), td.msg);
			}
			this.add('-fail', source);
			this.attrLastMove('[still]');
			return null;
		},

		onModifyMove(move, source) {
			const md = LR.moveData(this, move);
			if (md && md.acc !== undefined) move.accuracy = md.acc === 0 ? true : md.acc;
			// Nature Power calls the field's move (PokeBattle_Field#naturePower).
			if (move.id === 'naturepower') {
				const call = LR.data(this).naturePower;
				move.onTryHit = function (target, pokemon) {
					this.actions.useMove(call, pokemon, target); // positional in Cobblemon's Showdown
					return null;
				};
			}
			const h = LR.hook(this, 'modifyMove');
			if (h) h.call(this, move, source);
		},

		/** moveFieldBoost: in the base power (basemult), with its message once per move. */
		onBasePowerPriority: 1,
		onBasePower(basePower, source, target, move) {
			const md = LR.moveData(this, move);
			let mult = 1;
			if (md && md.mult !== undefined && md.mult !== 0 && !LR.boostsOff(this)) {
				mult = md.mult;
				if (md.msg && !move.leagueRebornMoveMsg) {
					move.leagueRebornMoveMsg = true;
					LR.say(this, 'move', LR.field(this), md.msg, source.name, target ? target.name : '');
				}
			}
			const h = LR.hook(this, 'basePower');
			if (h) mult *= h.call(this, basePower, source, target, move) || 1;
			if (mult !== 1) return this.chainModify(mult);
		},

		/** typeFieldBoost and the field-change boost (x1.3): on the damage, as in Reborn. */
		onModifyDamage(damage, source, target, move) {
			let mult = LR.typeBoost(this, move, source, target);
			if (mult !== 1) {
				const td = LR.typeData(this, move);
				if (td.msg && !move.leagueRebornTypeMsg) {
					move.leagueRebornTypeMsg = true;
					LR.say(this, 'type', LR.field(this), td.msg);
				}
			}
			if (LR.willChange(this, move, source, target)) mult *= 1.3;
			const h = LR.hook(this, 'modifyDamage');
			if (h) mult *= h.call(this, damage, source, target, move) || 1;
			if (mult !== 1) return this.chainModify(mult);
		},

		/** fieldTypeChange: the added type counts in the effectiveness, as a second type of the move. */
		onEffectiveness(typeMod, target, type, move) {
			if (move.category === 'Status') return;
			const added = LR.addedType(this, move, this.activePokemon);
			if (!added || added === move.type) return;
			return typeMod + this.dex.getEffectiveness(added, type);
		},
		/** ...and so does its immunity (a type modifier of 0 in Reborn). */
		onTryHit(target, source, move) {
			if (move.category === 'Status' || target === source) return;
			const added = LR.addedType(this, move, source);
			if (!added || added === move.type) return;
			if (!target.runImmunity(added)) {
				this.add('-immune', target);
				return null;
			}
		},

		onAfterMove(source, target, move) {
			afterMove(this, source, target, move);
		},

		/** A temporary field counts its turns down at the end of the turn (Battle#pbEndOfRoundPhase, "Terrain"). */
		onFieldResidualOrder: 27,
		onFieldResidual() {
			const s = LR.state(this);
			const h = LR.hook(this, 'residual');
			if (h) h.call(this);
			if (s.turns > 0) {
				s.turns -= 1;
				if (s.turns === 0 && LR.field(this) !== NONE) LR.endTemp(this);
			}
		},
	};

	return {LR, HOOKS, CONDITIONS, EFFECTS, FIELDS};
});
