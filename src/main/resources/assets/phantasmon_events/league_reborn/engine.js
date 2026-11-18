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
 *   start <field> | field <field> <turns left, 0 = permanent> | change <from> <to> <msg index> |
 *   end <field> (temporary field over) |
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

	/**
	 * Field-specific code, filled per field (see the batches in Documentation/terrains/conception.md), and ALL: code
	 * run on every field (moves that lay a field down anywhere, effects several fields share through a list).
	 * Hooks: setStatus, tryAddVolatile, tryMove, tryHit (null/false stops), modifyMove, basePower, modifyDamage,
	 * modifySpD (multipliers), entry (switch-in), afterMove, residual, onEnter, onLeave, addedType (per field only).
	 */
	const HOOKS = {};
	/** hook name -> functions, added by shared({...}) one group at a time. */
	const ALL = {};
	function shared(hooks) {
		for (const [name, fn] of Object.entries(hooks)) (ALL[name] = ALL[name] || []).push(fn);
	}

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
		/** ALL's hook then the field's, for the hooks every field may add to. */
		hooks(battle, name) {
			const out = [];
			if (ALL[name]) out.push(...ALL[name]);
			const h = LR.hook(battle, name);
			if (h) out.push(h);
			return out;
		},
		/** The first hook answer that is not undefined (a veto). */
		veto(battle, name, args) {
			for (const h of LR.hooks(battle, name)) {
				const r = h.apply(battle, args);
				if (r !== undefined) return r;
			}
			return undefined;
		},
		/** The hooks' multipliers, chained. */
		mult(battle, name, args) {
			let m = 1;
			for (const h of LR.hooks(battle, name)) m *= h.apply(battle, args) || 1;
			return m;
		},
		/** Cramorant's form from Surf or Dive: the field decides before the HP do (Battler.rb, Gulp Missile). */
		gulpForme(battle, pokemon) {
			const f = LR.field(battle);
			if (['electerrain', 'factory', 'shortcircuit'].includes(f)) return 'cramorantgorging';
			if (['swamp', 'watersurface', 'underwater'].includes(f)) return 'cramorantgulping';
			return pokemon.hp <= pokemon.maxhp / 2 ? 'cramorantgorging' : 'cramorantgulping';
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
			const s = LR.state(battle);
			// The turns a temporary field has left (0: permanent), for the clients' field display.
			LR.say(battle, 'field', to, s.overlay != null ? s.turns : 0);
			if (LOCKED.includes(to) && battle.field.weather) battle.field.clearWeather();
			const leave = HOOKS[from] && HOOKS[from].onLeave;
			if (leave) leave.call(battle, to);
			const enter = HOOKS[to] && HOOKS[to].onEnter;
			if (enter) enter.call(battle, from, growth);
			for (const pokemon of battle.getAllActive()) battle.runEvent('LeagueRebornField', pokemon, null, null, to);
			// Showdown's terrain watchers (Quark Drive, Mimicry, the seeds) look again (field.isTerrain is ours).
			battle.eachEvent('TerrainChange');
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
		for (const h of LR.hooks(battle, 'afterMove')) h.call(battle, source, target, move);
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

	// --- Field code (Reborn's hand-written field checks) ----------------------------------------------------------

	/** Not in the air nor out of reach (Reborn: !isAirborne?). */
	function grounded(pokemon) {
		return pokemon.isGrounded() && !pokemon.isSemiInvulnerable();
	}
	function ability(battle, id) {
		return battle.dex.abilities.get(id);
	}
	/** A field's move laid down anywhere for a few turns (Ion Deluge, Plasma Fists, Mist): never past an Everstone. */
	function layField(battle, source, to, turns) {
		if (source.hasItem('everstone') || !LR.canChange(battle, to)) return;
		LR.set(battle, to, source.hasItem('amplifieldrock') ? turns * 2 : turns);
	}

	/** Status moves a field strengthens (Battle_MoveEffects.rb): the boosts they give there instead. */
	const BOOSTS = {
		charge: {electerrain: {spd: 2}},
		eerieimpulse: {electerrain: {spa: -3}},
		coil: {grassy: {atk: 2, def: 2, accuracy: 2}},
		growth: {grassy: {atk: 2, spa: 2}},
		aromaticmist: {misty: {spd: 2}},
		sweetscent: {misty: {evasion: -1, def: -1, spd: -1}},
		cosmicpower: Object.fromEntries(['misty', 'rainbow', 'holy', 'starlight', 'newworld', 'psyterrain'].map((f) => [f, {def: 2, spd: 2}])),
		calmmind: Object.fromEntries(['chess', 'ashenbeach', 'psyterrain'].map((f) => [f, {spa: 2, spd: 2}])),
		nastyplot: Object.fromEntries(['chess', 'psyterrain', 'infernal', 'backalley'].map((f) => [f, {spa: 3}])),
		meditate: {rainbow: {atk: 3}, ashenbeach: {atk: 3}, psyterrain: {atk: 2, spa: 2}},
		kinesis: {ashenbeach: {accuracy: -2}, psyterrain: {accuracy: -2}},
		telekinesis: {psyterrain: {def: -2, spd: -2}},
	};
	/** ...the boosts their user gets there on top. */
	const SELF_BOOSTS = {
		kinesis: {psyterrain: {atk: 2, spa: 2}},
		mindreader: {psyterrain: {spa: 2}},
		miracleeye: {holy: {spa: 2}, fairytale: {spa: 2}, psyterrain: {spa: 2}},
		psychup: {psyterrain: {spa: 2}},
	};
	/** Damaging moves whose own boost grows there. */
	const SECONDARY_BOOSTS = {
		psyshieldbash: {psyterrain: {def: 1, spd: 1}},
		esperwing: {psyterrain: {spe: 2}},
		mysticalpower: {psyterrain: {spa: 2}},
	};
	/** Rooms and Gravity last 8 turns there (5 elsewhere). */
	const LONG_ROOMS = {
		trickroom: ['chess', 'newworld', 'psyterrain'],
		magicroom: ['newworld', 'psyterrain'],
		wonderroom: ['newworld', 'psyterrain'],
		gravity: ['psyterrain'],
	};
	const WISH_FIELDS = ['misty', 'rainbow', 'holy', 'fairytale', 'starlight'];

	shared({
		modifyMove(move, source) {
			const f = LR.field(this);
			const boosts = BOOSTS[move.id] && BOOSTS[move.id][f];
			if (boosts) move.boosts = boosts;
			const self = SELF_BOOSTS[move.id] && SELF_BOOSTS[move.id][f];
			if (self) move.self = {boosts: self};
			const secondary = SECONDARY_BOOSTS[move.id] && SECONDARY_BOOSTS[move.id][f];
			if (secondary) move.secondaries = [{chance: 100, self: {boosts: secondary}}];
		},
		basePower(basePower, source, target, move) {
			const f = LR.field(this);
			const changer = move.typeChangerBoosted && move.typeChangerBoosted.id;
			// Galvanize and Pixilate: x1.5 (x2 on Short-Circuit) instead of Showdown's x1.2.
			if (changer === 'galvanize' && ['electerrain', 'factory'].includes(f)) return 1.5 / 1.2;
			if (changer === 'galvanize' && f === 'shortcircuit') return 2 / 1.2;
			if (changer === 'pixilate' && f === 'misty') return 1.5 / 1.2;
		},
		modifyDamage(damage, source, target, move) {
			const f = LR.field(this);
			if (move.type === 'Poison' && ['misty', 'rainbow'].includes(f) && target &&
				(target.hasAbility('pastelveil') || target.allies().some((a) => a !== target && a.hasAbility('pastelveil')))) return 0.5;
		},
		entry(pokemon) {
			const f = LR.field(this);
			if (['misty', 'corrosivemist'].includes(f) && pokemon.hasAbility('watercompaction')) {
				this.boost({def: 2}, pokemon, pokemon, ability(this, 'watercompaction'));
			}
			if (f === 'psyterrain' && pokemon.hasAbility('anticipation')) {
				this.boost({spa: 2}, pokemon, pokemon, ability(this, 'anticipation'));
			}
		},
		afterMove(source, target, move) {
			const f = LR.field(this);
			if (source.moveThisTurnResult !== true) return;
			if (move.id === 'iondeluge' || move.id === 'plasmafists') layField(this, source, 'electerrain', 3);
			if (move.id === 'mist') layField(this, source, 'misty', 3);
			if (LONG_ROOMS[move.id] && LONG_ROOMS[move.id].includes(f)) {
				const room = this.field.pseudoWeather[move.id];
				if (room && room.duration === 5) room.duration = 8;
			}
			if (move.id === 'wish' && WISH_FIELDS.includes(f)) {
				const wish = source.side.slotConditions[source.position] && source.side.slotConditions[source.position].wish;
				if (wish && wish.duration === 2) wish.hp = Math.floor((source.maxhp + 1) * 0.75);
			}
		},
	});

	Object.assign(HOOKS, {
		/** Electric Terrain: no sleep on the ground, Focus Punch never lands, Motor Drive and Slow Start run. */
		electerrain: {
			setStatus(status, target, source, effect) {
				if (status.id !== 'slp' || !grounded(target)) return;
				if (effect && (effect.effectType === 'Move' || effect.id === 'yawn')) LR.say(this, 'text', 'electric_awake', target.name);
				return false;
			},
			tryAddVolatile(status, target) {
				if (status.id !== 'yawn' || !grounded(target)) return;
				LR.say(this, 'text', 'electric_awake', target.name);
				return null;
			},
			tryMove(source, target, move) {
				if (move.id !== 'focuspunch') return;
				LR.say(this, 'text', 'focus_lost', source.name);
				return null;
			},
			modifyMove(move) {
				if (move.id === 'wildcharge') delete move.recoil;
			},
			basePower(basePower, source, target, move) {
				if (move.type === 'Ground' && target && target.hasAbility('transistor') && !move.ignoreAbility) return 0.5;
			},
			afterMove(source, target, move) {
				const rise = source.volatiles.magnetrise;
				if (move.id === 'magnetrise' && rise && rise.duration === 5) rise.duration = 8;
			},
			residual() {
				for (const pokemon of this.getAllActive()) {
					if (!pokemon.hp) continue;
					const slow = pokemon.volatiles.slowstart;
					if (slow && slow.duration > 1) slow.duration -= 1; // its turns count twice
					if (pokemon.hasAbility('motordrive') && pokemon.activeTurns > 0) {
						this.boost({spe: 1}, pokemon, pokemon, ability(this, 'motordrive'));
					}
				}
			},
		},
		/** Grassy Terrain: grounded Pokémon heal 1/16 at the end of the turn. */
		grassy: {
			modifyMove(move) {
				if (move.id === 'naturesmadness') {
					move.damageCallback = function (pokemon, target) {
						return this.clampIntRange(Math.floor(target.getUndynamaxedHP() * 0.75), 1);
					};
				}
				if (move.id === 'floralhealing') {
					move.onHit = function (target, source) {
						const success = !!this.heal(target.baseMaxhp);
						if (success && !target.isAlly(source)) target.staleness = 'external';
						if (!success) {
							this.add('-fail', target, 'heal');
							return this.NOT_FAIL;
						}
						return success;
					};
				}
			},
			residual() {
				const terrain = this.dex.conditions.get('grassyterrain');
				for (const pokemon of this.getAllActive()) {
					if (pokemon.hp && grounded(pokemon)) this.heal(pokemon.baseMaxhp / 16, pokemon, pokemon, terrain);
				}
			},
		},
		/** Misty Terrain: no status nor confusion on the ground, Fairy types' Sp. Def x1.5, Dry Skin drinks the mist. */
		misty: {
			setStatus(status, target, source, effect) {
				if (!grounded(target)) return;
				if (effect && (effect.effectType === 'Move' || effect.id === 'yawn')) LR.say(this, 'text', 'misty_status', target.name);
				return false;
			},
			tryAddVolatile(status, target) {
				if (!['confusion', 'yawn'].includes(status.id) || !grounded(target)) return;
				LR.say(this, 'text', 'misty_status', target.name);
				return null;
			},
			modifySpD(spd, target) {
				if (target.hasType('Fairy')) return 1.5;
			},
		},
		/** Psychic Terrain: grounded Pokémon cannot be hit by priority moves; Secret Power confuses. */
		psyterrain: {
			tryHit(target, source, move) {
				if (target === source || move.priority <= 0.1 || move.target === 'self') return;
				if (target.isAlly(source) || !grounded(target)) return;
				this.add('-activate', target, 'move: Psychic Terrain');
				return null;
			},
			modifyMove(move) {
				if (move.id === 'confide') LR.say(this, 'text', 'confide');
			},
		},
	});

	// --- Shared field code, second batch: water fields, Secret Power, the pledges --------------------------------

	/** Secret Power's added effect per field (Move 0A4 pbAdditionalEffect), 30%; paralysis elsewhere. */
	const drop = (boosts) => ({boosts});
	const SECRET_POWER = {};
	const secretOn = (fields, effect) => fields.forEach((f) => { SECRET_POWER[f] = effect; });
	secretOn(['electerrain', 'shortcircuit'], {status: 'par'});
	secretOn(['grassy', 'forest', 'fairytale'], {status: 'slp'});
	secretOn(['misty', 'holy'], drop({spa: -1}));
	secretOn(['darkcrystalcavern', 'desert', 'ashenbeach', 'clouds'], drop({accuracy: -1}));
	secretOn(['chess', 'darkness1', 'darkness2', 'darkness3'], drop({def: -1}));
	secretOn(['bigtop', 'starlight'], drop({spd: -1}));
	secretOn(['burning', 'superheated', 'dragonsden', 'volcanic', 'volcanictop', 'infernal', 'dancefloor'], {status: 'brn'});
	secretOn(['swamp', 'watersurface', 'glitch'], drop({spe: -1}));
	secretOn(['corrosive', 'corrosivemist', 'murkwatersurface', 'corrupted', 'backalley', 'city'], {status: 'psn'});
	secretOn(['icy', 'snowymountain', 'frozendimension'], {status: 'frz'});
	secretOn(['rocky', 'cave', 'mountain', 'dimensional', 'deepearth'], {volatileStatus: 'flinch'});
	secretOn(['factory', 'underwater'], drop({atk: -1}));
	secretOn(['mirror'], drop({evasion: -1}));
	secretOn(['flowergarden1', 'flowergarden2'], drop({evasion: -1}));
	secretOn(['flowergarden3', 'flowergarden4'], drop({def: -1, spd: -1, evasion: -1}));
	secretOn(['flowergarden5'], drop({def: -2, spd: -2, evasion: -2}));
	secretOn(['newworld'], drop({atk: -1, def: -1, spe: -1, spa: -1, spd: -1, accuracy: -1}));
	secretOn(['inverse', 'psyterrain', 'sky'], {volatileStatus: 'confusion'});
	secretOn(['haunted'], {volatileStatus: 'curse'});
	secretOn(['colosseum'], {self: {boosts: {atk: 1}}});
	/** ...or one of several, at random. */
	const SECRET_RANDOM = {
		rainbow: ['par', 'psn', 'brn', 'frz', 'slp'],
		wasteland: ['par', 'psn', 'brn', 'frz'],
		crystalcavern: ['brn', 'frz', 'slp', 'confusion'],
		bewitched: ['par', 'psn', 'slp'],
	};
	function secretPower(f) {
		const random = SECRET_RANDOM[f];
		if (!random) return Object.assign({chance: 30}, SECRET_POWER[f] || {status: 'par'});
		return {
			chance: 30,
			onHit(target, source) {
				const pick = this.sample(random);
				if (pick === 'confusion') target.addVolatile('confusion', source);
				else target.trySetStatus(pick, source);
			},
		};
	}

	/** In the water: anywhere Underwater, on the ground on the two water surfaces. */
	const SURFACES = ['watersurface', 'murkwatersurface'];
	function inWater(battle, pokemon) {
		const f = LR.field(battle);
		return f === 'underwater' || (SURFACES.includes(f) && pokemon.isGrounded());
	}
	LR.inWater = inWater;
	/** Fields that count as poisoned ground (Toxic Boost, Merciless, Poison Heal...): everywhere, or on the ground. */
	const TOXIC_AIR = ['corrosivemist', 'corrupted'];
	const TOXIC_GROUND = ['corrosive', 'murkwatersurface', 'wasteland'];
	function toxicGround(battle, pokemon) {
		const f = LR.field(battle);
		return TOXIC_AIR.includes(f) || (TOXIC_GROUND.includes(f) && pokemon.isGrounded());
	}
	/** Fields where Venoshock, Barb Barrage and Venom Drench act as if the target were poisoned. */
	const TOXIC_FIELDS = ['corrosive', 'corrosivemist', 'wasteland', 'murkwatersurface'];
	LR.toxicField = (battle) => TOXIC_FIELDS.includes(LR.field(battle));
	const RAIN = ['raindance', 'primordialsea'];
	const PLEDGES = ['firepledge', 'grasspledge', 'waterpledge'];

	Object.assign(BOOSTS, {
		acidarmor: Object.fromEntries(['corrosive', 'corrosivemist', 'murkwatersurface', 'fairytale'].map((f) => [f, {def: 3}])),
		irondefense: {factory: {def: 3}},
	});

	shared({
		modifyMove(move) {
			const f = LR.field(this);
			if (move.id === 'secretpower') move.secondaries = [secretPower(f)];
			if (move.id === 'wavecrash' && ['watersurface', 'underwater'].includes(f)) move.recoil = [1, 4];
			if (f === 'underwater' && move.type === 'Electric') move.accuracy = true;
		},
		basePower(basePower, source, target, move) {
			const f = LR.field(this);
			let mult = 1;
			// Torrent works at any HP in the water (Showdown already boosts at a third of the HP).
			if (move.type === 'Water' && source.hasAbility('torrent') && source.hp > source.maxhp / 3 &&
				(f === 'underwater' || (f === 'watersurface' && source.isGrounded()))) mult *= 1.5;
			if (source.hasAbility('propellertail') && move.priority >= 1 && ['watersurface', 'underwater'].includes(f)) mult *= 1.5;
			// Underwater, physical moves other than Water ones are slowed by the water.
			if (f === 'underwater' && move.category === 'Physical' && move.type !== 'Water' &&
				!source.hasAbility(['steelworker', 'swiftswim'])) mult *= 0.5;
			if (TOXIC_FIELDS.includes(f) && move.category === 'Physical' && source.hasAbility('toxicboost') &&
				!['psn', 'tox'].includes(source.status)) mult *= 1.5;
			return mult;
		},
		modifySpe(spe, pokemon) {
			const f = LR.field(this);
			const water = inWater(this, pokemon);
			let mult = 1;
			if (pokemon.hasAbility('swiftswim') && water && !RAIN.includes(pokemon.effectiveWeather())) mult *= 2;
			if (pokemon.hasAbility('surgesurfer') && (water || ['shortcircuit', 'underwater'].includes(f))) mult *= 2;
			if (f === 'newworld' && pokemon.isGrounded()) mult *= 0.75;
			if (SURFACES.includes(f) && pokemon.isGrounded() && !pokemon.hasType('Water') &&
				!pokemon.hasAbility(['surgesurfer', 'swiftswim'])) mult *= 0.75;
			if (f === 'underwater' && !pokemon.hasType('Water') && !pokemon.hasAbility(['swiftswim', 'steelworker'])) mult *= 0.5;
			return mult;
		},
		chargeMove(pokemon, target, move) {
			if (move.id !== 'dive' || !['watersurface', 'underwater'].includes(LR.field(this))) return;
			this.attrLastMove('[still]');
			this.addMove('-anim', pokemon, move.name, target);
			return false;
		},
		entry(pokemon) {
			// Spikes and Toxic Spikes sink or blow away.
			if (!['watersurface', 'murkwatersurface', 'sky', 'clouds'].includes(LR.field(this))) return;
			pokemon.side.removeSideCondition('spikes');
			pokemon.side.removeSideCondition('toxicspikes');
		},
		afterMove(source, target, move) {
			const f = LR.field(this);
			const hit = source.moveThisTurnResult === true;
			if (move.id === 'splash' && f === 'watersurface' && !this.field.pseudoWeather.gravity) {
				for (const pokemon of this.getAllActive()) if (pokemon !== source) this.boost({accuracy: -1}, pokemon, source, null, true);
			}
			if (!hit) return;
			if (PLEDGES.includes(move.id)) pledge(this, source, move);
			if (move.id === 'roar' && f === 'swamp') LR.say(this, 'text', 'swamp_roar');
			if (move.id === 'whirlpool' && ['watersurface', 'underwater'].includes(f) && target && target.hp && target !== source) {
				target.addVolatile('confusion', source);
			}
			if (move.id === 'tarshot' && ['murkwatersurface', 'corrupted'].includes(f) && target && target.hp) target.trySetStatus('psn', source);
			if (move.id === 'lifedew') {
				if (f === 'watersurface') source.addVolatile('aquaring');
				if (['corrosivemist', 'murkwatersurface'].includes(f)) source.trySetStatus('psn', source);
			}
			if (move.id === 'shoreup' && SURFACES.includes(f) && source.hasAbility('watercompaction')) {
				this.boost({def: 2}, source, source, ability(this, 'watercompaction'));
			}
		},
		residual() {
			const f = LR.field(this);
			for (const pokemon of this.getAllActive()) {
				if (!pokemon.hp) continue;
				const water = inWater(this, pokemon) || (f === 'swamp' && pokemon.isGrounded());
				if (pokemon.hasAbility('steamengine') && pokemon.activeTurns > 0 &&
					['volcanic', 'volcanictop', 'watersurface', 'underwater', 'infernal'].includes(f)) {
					this.boost({spe: 1}, pokemon, pokemon, ability(this, 'steamengine'));
				}
				if (pokemon.status && ((pokemon.hasAbility('hydration') && (f === 'underwater' || (f === 'watersurface' && pokemon.isGrounded())) &&
					!RAIN.includes(pokemon.effectiveWeather())) || (pokemon.hasAbility('waterveil') && ['watersurface', 'underwater'].includes(f)))) {
					this.add('-activate', pokemon, 'ability: ' + pokemon.getAbility().name);
					pokemon.cureStatus();
				}
				if (pokemon.hasAbility('watercompaction') && water && f !== 'misty') {
					this.boost({def: 2}, pokemon, pokemon, ability(this, 'watercompaction'));
				}
				if (pokemon.hasAbility('poisonheal') && !['psn', 'tox'].includes(pokemon.status) && toxicGround(this, pokemon) &&
					pokemon.hp < pokemon.maxhp) {
					this.heal(pokemon.baseMaxhp / 8, pokemon, pokemon, ability(this, 'poisonheal'));
				}
				// Dry Skin drinks the mist or the murk, out of rain and sun.
				if (pokemon.hasAbility('dryskin') && ['misty', 'swamp'].includes(f) &&
					![...RAIN, 'sunnyday', 'desolateland'].includes(pokemon.effectiveWeather())) {
					this.heal(pokemon.baseMaxhp / 16, pokemon, pokemon, ability(this, 'dryskin'));
				}
			}
		},
	});

	/** setPledge: two different pledges make a field for 4 turns (water + grass: Swamp, fire + water: Rainbow, fire + grass: Burning). */
	function pledge(battle, source, move) {
		const s = LR.state(battle);
		if (!s.pledge) {
			s.pledge = move.id;
			return;
		}
		if (s.pledge === move.id) return;
		const pair = [move.id, s.pledge];
		s.pledge = null;
		const to = !pair.includes('firepledge') ? 'swamp' : !pair.includes('grasspledge') ? 'rainbow' : 'burning';
		const turns = source.hasItem('amplifieldrock') ? 7 : 4;
		if (LR.field(battle) === to) {
			if (s.overlay != null) s.turns = turns; // the pledges reinforce it
			return;
		}
		if (LR.canChange(battle, to)) LR.set(battle, to, turns);
	}

	/** Water that washes Tar Shot off and that Water Absorb and Dry Skin drink. */
	function bathe(battle, pokemon, grounded) {
		if (pokemon.volatiles.tarshot) {
			pokemon.removeVolatile('tarshot');
			LR.say(battle, 'text', 'tar_washed', pokemon.name);
		}
		if (pokemon.hasAbility(['waterabsorb', 'dryskin']) && (!grounded || pokemon.isGrounded()) && pokemon.hp < pokemon.maxhp) {
			if (battle.heal(pokemon.baseMaxhp / 16, pokemon, pokemon, pokemon.getAbility())) LR.say(battle, 'text', 'water_absorbed', pokemon.name);
		}
	}
	/** Damage from a type's effectiveness, Reborn's way: maxhp * eff / divisor, with 1x = 4 (at least 1). */
	function typeDamage(battle, pokemon, type, divisor) {
		let eff = 4 * Math.pow(2, battle.dex.getEffectiveness(type, pokemon));
		if (pokemon.hasAbility(['flamebody', 'magmaarmor'])) eff *= 2;
		return Math.max(1, Math.floor(pokemon.maxhp * eff / divisor));
	}

	Object.assign(HOOKS, {
		/** Water Surface: the water heals water drinkers and washes tar off. */
		watersurface: {
			residual() {
				for (const pokemon of this.getAllActive()) if (pokemon.hp) bathe(this, pokemon, true);
			},
		},
		/** Underwater: Water hits Water normally; Pokémon the water hurts struggle; Dive takes one turn. */
		underwater: {
			effectiveness(typeMod, target, type, move) {
				if (move.type === 'Water' && type === 'Water') return 0;
			},
			residual() {
				for (const pokemon of this.getAllActive()) {
					if (!pokemon.hp) continue;
					bathe(this, pokemon, false);
					if (pokemon.hasType('Water') || pokemon.hasAbility(['swiftswim', 'magicguard'])) continue;
					if (this.dex.getEffectiveness('Water', pokemon) <= 0) continue;
					LR.say(this, 'text', 'water_struggle', pokemon.name);
					this.directDamage(typeDamage(this, pokemon, 'Water', 32), pokemon);
				}
			},
		},
		/** Murkwater Surface: the toxic water hurts (worse under it), heals Poison water drinkers; mud is water. */
		murkwatersurface: {
			modifyType(move) {
				if (['mudslap', 'mudbomb', 'mudbarrage', 'mudshot', 'thousandwaves'].includes(move.id)) move.type = 'Water';
			},
			residual() {
				for (const pokemon of this.getAllActive()) {
					if (!pokemon.hp) continue;
					const immune = pokemon.hasType(['Steel', 'Poison']) ||
						pokemon.hasAbility(['poisonheal', 'magicguard', 'wonderguard', 'toxicboost', 'immunity', 'pastelveil']);
					if (!immune) {
						const drinker = pokemon.hasAbility(['flamebody', 'magmaarmor', 'dryskin', 'waterabsorb']) ? 2 : 1;
						if (pokemon.volatiles.dive) {
							LR.say(this, 'text', 'toxic_water_dive', pokemon.name);
							this.directDamage(Math.max(1, Math.floor(pokemon.maxhp * 4 * Math.pow(2, this.dex.getEffectiveness('Poison', pokemon)) * drinker / 8)), pokemon);
						} else if (pokemon.isGrounded()) {
							LR.say(this, 'text', 'toxic_water', pokemon.name);
							this.directDamage(Math.max(1, Math.floor(pokemon.maxhp * 4 * Math.pow(2, this.dex.getEffectiveness('Poison', pokemon)) * drinker / 32)), pokemon);
						}
					}
					if (pokemon.hp && pokemon.hasType('Poison') && pokemon.hasAbility(['dryskin', 'waterabsorb']) && pokemon.isGrounded() &&
						pokemon.hp < pokemon.maxhp && this.heal(pokemon.baseMaxhp / 8, pokemon, pokemon, pokemon.getAbility())) {
						LR.say(this, 'text', 'poison_water_heal', pokemon.name);
					}
				}
			},
		},
		/** Swamp: the mire slows the grounded down every turn and saps the sleeping. */
		swamp: {
			residual() {
				for (const pokemon of this.getAllActive()) {
					if (!pokemon.hp) continue;
					if (pokemon.isGrounded() && !pokemon.hasItem('heavydutyboots') &&
						!pokemon.hasAbility(['whitesmoke', 'clearbody', 'quickfeet', 'swiftswim', 'propellertail', 'steamengine'])) {
						this.boost({spe: -1}, pokemon, pokemon, null, true);
					}
					if ((pokemon.status === 'slp' || pokemon.hasAbility('comatose')) && !pokemon.hasAbility('magicguard')) {
						LR.say(this, 'text', 'swamp_sapped', pokemon.name);
						this.directDamage(Math.max(1, Math.floor(pokemon.maxhp / 16)), pokemon);
					}
				}
			},
		},
	});

	/**
	 * Showdown's own effects a field changes, patched once per Dex (the battle's: Cobblemon's mod or the base one).
	 * Each patch keeps Showdown's behaviour outside League Reborn (LR.field is null there).
	 */
	const MARVEL_FIELDS = ['misty', 'rainbow', 'fairytale', 'dragonsden', 'starlight'];
	const SOULHEART_FIELDS = ['misty', 'rainbow', 'fairytale'];
	const POWERSPOT_FIELDS = ['haunted', 'bewitched', 'holy', 'psyterrain', 'deepearth'];
	const AQUARING_FIELDS = ['misty', 'swamp', 'watersurface', 'underwater'];
	const BINDING = [8, 6, 4, 3, 2];
	/** Binding moves that squeeze harder on their fields (one step down Reborn's divisors). */
	const BIND_FIELDS = {
		thundercage: ['electerrain'], snaptrap: ['grassy'], whirlpool: ['watersurface', 'underwater'],
		magmastorm: ['dragonsden'], sandtomb: ['desert'], infestation: ['forest'], firespin: ['burning', 'haunted'],
	};
	const OOZE_FIELDS = ['murkwatersurface', 'wasteland', 'corrupted'];
	const STENCH_FIELDS = ['wasteland', 'murkwatersurface', 'backalley', 'city'];
	const POISONED = /target\.status === "psn" \|\| target\.status === "tox"/;
	const POISONED_HERE = '(target.status === "psn" || target.status === "tox" || LR.toxicField(this))';
	/** Rewrites Showdown code: Comatose is no sleep on Electric Terrain (ability == :COMATOSE && FE != :ELECTERRAIN). */
	const AWAKE = [/(\w+)\.hasAbility\("comatose"\)/g, '($1.hasAbility("comatose") && LR.field(this) !== "electerrain")'];
	const GULP = [/(\w+)\.hp <= \1\.maxhp \/ 2 \? "cramorantgorging" : "cramorantgulping"/g, 'LR.gulpForme(this, $1)'];
	function rewrite(fn, [from, to]) {
		const src = fn.toString().replace(from, to);
		const head = /^(async\s+)?function\b/.test(src) ? '' : 'function ';
		return new Function('LR', `return ${head}${src};`)(LR);
	}
	const PATCHES = [
		['abilities', 'comatose', 'onSetStatus', (orig) => function (...args) {
			if (LR.field(this) === 'electerrain') return;
			return orig.apply(this, args);
		}],
		...[['moves', 'dreameater'], ['moves', 'hex'], ['moves', 'infernalparade'], ['moves', 'sleeptalk'], ['moves', 'snore'],
			['moves', 'wakeupslap'], ['moves', 'rest'], ['conditions', 'nightmare'], ['abilities', 'baddreams']]
			.map(([kind, id]) => [kind, id, '*', (orig) => rewrite(orig, AWAKE)]),
		['abilities', 'gulpmissile', 'onSourceTryPrimaryHit', (orig) => rewrite(orig, GULP)],
		['moves', 'dive', 'onTryMove', (orig) => rewrite(orig, GULP)],
		['abilities', 'cottondown', 'onDamagingHit', (orig) => rewrite(orig, [/\{ spe: -1 \}/, '{ spe: LR.field(this) === "grassy" ? -2 : -1 }'])],
		['abilities', 'marvelscale', 'onModifyDef', () => function (def, pokemon) {
			if (pokemon.status || MARVEL_FIELDS.includes(LR.field(this))) return this.chainModify(1.5);
		}],
		['abilities', 'soulheart', 'onAnyFaint', (orig) => function (...args) {
			const r = orig.apply(this, args);
			if (SOULHEART_FIELDS.includes(LR.field(this))) this.boost({spd: 1}, this.effectState.target);
			return r;
		}],
		['abilities', 'purepower', 'onModifyAtk', () => function () {
			if (LR.field(this) !== 'psyterrain') return this.chainModify(2);
		}],
		['abilities', 'purepower', 'onModifySpA', () => function () {
			if (LR.field(this) === 'psyterrain') return this.chainModify(2);
		}],
		['abilities', 'purepower', 'onModifySpAPriority', () => 5],
		['abilities', 'telepathy', 'onModifySpe', () => function () {
			if (LR.field(this) === 'psyterrain') return this.chainModify(2);
		}],
		['abilities', 'powerspot', 'onAllyBasePower', () => function (basePower, attacker) {
			if (attacker === this.effectState.target) return;
			return this.chainModify(POWERSPOT_FIELDS.includes(LR.field(this)) ? 1.5 : [5325, 4096]);
		}],
		['conditions', 'aquaring', 'onResidual', () => function (pokemon) {
			this.heal(pokemon.baseMaxhp / (AQUARING_FIELDS.includes(LR.field(this)) ? 8 : 16));
		}],
		['conditions', 'partiallytrapped', 'onStart', (orig) => function (...args) {
			const r = orig.apply(this, args);
			const by = this.effectState.sourceEffect && this.effectState.sourceEffect.id;
			if ((BIND_FIELDS[by] || []).includes(LR.field(this))) {
				const i = BINDING.indexOf(this.effectState.boundDivisor);
				if (i >= 0 && i < BINDING.length - 1) this.effectState.boundDivisor = BINDING[i + 1];
			}
			return r;
		}],
		// The toxic fields count as a poisoned target.
		['moves', 'venoshock', 'onBasePower', (orig) => rewrite(orig, [POISONED, POISONED_HERE])],
		['moves', 'barbbarrage', 'onBasePower', (orig) => rewrite(orig, [POISONED, POISONED_HERE])],
		['moves', 'venomdrench', 'onHit', (orig) => rewrite(orig, [POISONED, POISONED_HERE])],
		['abilities', 'merciless', 'onModifyCritRatio', (orig) => function (critRatio, source, target) {
			if (target && LR.toxicField(this)) return 5;
			return orig.call(this, critRatio, source, target);
		}],
		['abilities', 'liquidooze', 'onSourceTryHeal', (orig) => rewrite(orig, [/this\.damage\(damage\)/, 'this.damage(OOZE.includes(LR.field(this)) ? damage * 2 : damage)'.replace('OOZE', JSON.stringify(OOZE_FIELDS))])],
		['moves', 'strengthsap', 'onHit', (orig) => rewrite(orig, [/this\.heal\(atk,/, 'this.heal(["swamp", "forest"].includes(LR.field(this)) ? Math.floor(atk * 1.3) : atk,'])],
		['moves', 'takeheart', 'onHit', (orig) => rewrite(orig, [/\{ spa: 1, spd: 1 \}/, '(["watersurface", "underwater"].includes(LR.field(this)) ? { spa: 2, spd: 2 } : { spa: 1, spd: 1 })'])],
		['abilities', 'gooey', 'onDamagingHit', (orig) => rewrite(orig, [/\{ spe: -1 \}/, '{ spe: ["swamp", "murkwatersurface"].includes(LR.field(this)) ? -2 : -1 }'])],
		['abilities', 'stench', 'onModifyMove', (orig) => rewrite(orig, [/chance: 10,/, 'chance: STENCH.includes(LR.field(this)) ? 20 : 10,'.replace('STENCH', JSON.stringify(STENCH_FIELDS))])],
		// Wishiwashi stays schooled in the water, at any HP.
		...['onStart', 'onResidual'].map((key) => ['abilities', 'schooling', key, (orig) => rewrite(orig, [/pokemon\.hp > pokemon\.maxhp \/ 4/, '(pokemon.hp > pokemon.maxhp / 4 || LR.inWater(this, pokemon))'])]),
		// Mimicry takes the field's type (its "mimicry" data), on any field.
		['abilities', 'mimicry', 'onTerrainChange', (orig) => function (pokemon) {
			const f = LR.field(this);
			if (f === null) return orig.call(this, pokemon);
			const type = FIELDS[f] && FIELDS[f].mimicry;
			const types = type ? [type] : pokemon.baseSpecies.types;
			if (pokemon.getTypes().join() === types.join() || !pokemon.setType(types)) return;
			if (type || pokemon.transformed) {
				this.add('-start', pokemon, 'typechange', types.join('/'), '[from] ability: Mimicry');
			} else {
				this.add('-activate', pokemon, 'ability: Mimicry');
				this.add('-end', pokemon, 'typechange', '[silent]');
			}
		}],
	];
	/**
	 * Showdown freezes the entries it hands out (and what they share with the data, like a move's condition), so the
	 * patches go into new copies of the raw data entries, and the cached ones are dropped. A dex mod shares its
	 * parent's untouched entries: those already patched (in PATCHED_ENTRIES) are left alone.
	 */
	const PATCHED_DEX = new WeakSet();
	const PATCHED_ENTRIES = new WeakSet();
	const TABLES = {abilities: 'Abilities', moves: 'Moves', conditions: 'Conditions'};
	function patched(holder, patches) {
		const copy = Object.assign({}, holder);
		for (const [key, make] of patches) {
			try {
				if (key !== '*') {
					copy[key] = make(copy[key]);
					continue;
				}
				// Every function that has the code to rewrite.
				for (const k of Object.keys(copy)) {
					if (typeof copy[k] === 'function' && /comatose/.test(copy[k].toString())) copy[k] = make(copy[k]);
				}
			} catch (e) {
				// Showdown's code is not what the patch expects: its own behaviour stays.
			}
		}
		return copy;
	}
	function patchDex(dex) {
		if (PATCHED_DEX.has(dex)) return;
		PATCHED_DEX.add(dex);
		const byEntry = new Map();
		for (const [kind, id, key, make] of PATCHES) {
			const k = kind + ':' + id;
			if (!byEntry.has(k)) byEntry.set(k, [kind, id, []]);
			byEntry.get(k)[2].push([key, make]);
		}
		for (const [kind, id, patches] of byEntry.values()) {
			// A condition is its own entry, or a move's.
			let table = dex.data[TABLES[kind]];
			let inMove = false;
			if (kind === 'conditions' && !table[id] && dex.data.Moves[id] && dex.data.Moves[id].condition) {
				table = dex.data.Moves;
				inMove = true;
			}
			const entry = table[id];
			if (!entry || PATCHED_ENTRIES.has(entry)) continue;
			let next;
			if (inMove) {
				next = Object.assign({}, entry, {condition: patched(entry.condition, patches)});
			} else {
				next = patched(entry, patches);
				if (patches.some(([key]) => key === '*') && entry.condition) next.condition = patched(entry.condition, patches.filter(([key]) => key === '*'));
			}
			PATCHED_ENTRIES.add(next);
			table[id] = next;
			dex.abilities.abilityCache.delete(id);
			dex.moves.moveCache.delete(id);
			dex.conditions.conditionCache.delete(id);
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
			patchDex(this.dex);
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
			if (LR.veto(this, 'tryMove', [source, target, move]) !== undefined) {
				this.attrLastMove('[still]');
				return null;
			}
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
			for (const h of LR.hooks(this, 'modifyMove')) h.call(this, move, source);
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
			mult *= LR.mult(this, 'basePower', [basePower, source, target, move]);
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
			mult *= LR.mult(this, 'modifyDamage', [damage, source, target, move]);
			if (mult !== 1) return this.chainModify(mult);
		},

		/** fieldTypeChange: the added type counts in the effectiveness, as a second type of the move. */
		onEffectiveness(typeMod, target, type, move) {
			if (move.category === 'Status') return;
			// A field's own type chart change (Underwater: Water is neutral on Water), then the added type.
			const h = LR.hook(this, 'effectiveness');
			const own = h ? h.call(this, typeMod, target, type, move) : undefined;
			let mod = own === undefined ? typeMod : own;
			const added = LR.addedType(this, move, this.activePokemon);
			if (added && added !== move.type) mod += this.dex.getEffectiveness(added, type);
			if (mod !== typeMod) return mod;
		},
		/** Pbtype: a field turning a move into another type outright (Murkwater Surface: mud is water). */
		onModifyTypePriority: -2,
		onModifyType(move, pokemon) {
			for (const h of LR.hooks(this, 'modifyType')) h.call(this, move, pokemon);
		},
		onModifySpe(spe, pokemon) {
			const mult = LR.mult(this, 'modifySpe', [spe, pokemon]);
			if (mult !== 1) return this.chainModify(mult);
		},
		/** A two-turn move done in one turn (false: no charging turn, as Power Herb). */
		onChargeMove(pokemon, target, move) {
			return LR.veto(this, 'chargeMove', [pokemon, target, move]);
		},
		/** ...and so does its immunity (a type modifier of 0 in Reborn). */
		onTryHit(target, source, move) {
			const stop = LR.veto(this, 'tryHit', [target, source, move]);
			if (stop !== undefined) return stop;
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

		onSetStatus(status, target, source, effect) {
			return LR.veto(this, 'setStatus', [status, target, source, effect]);
		},
		onTryAddVolatile(status, target, source, effect) {
			return LR.veto(this, 'tryAddVolatile', [status, target, source, effect]);
		},
		onModifySpD(spd, target, source, move) {
			const mult = LR.mult(this, 'modifySpD', [spd, target, source, move]);
			if (mult !== 1) return this.chainModify(mult);
		},
		onSwitchIn(pokemon) {
			for (const h of LR.hooks(this, 'entry')) h.call(this, pokemon);
		},

		/** A temporary field counts its turns down at the end of the turn (Battle#pbEndOfRoundPhase, "Terrain"). */
		onFieldResidualOrder: 27,
		onFieldResidual() {
			const s = LR.state(this);
			for (const h of LR.hooks(this, 'residual')) h.call(this);
			if (s.turns > 0) {
				s.turns -= 1;
				if (s.turns === 0 && LR.field(this) !== NONE) LR.endTemp(this);
			}
		},
	};

	return {LR, HOOKS, ALL, CONDITIONS, EFFECTS, FIELDS};
});
