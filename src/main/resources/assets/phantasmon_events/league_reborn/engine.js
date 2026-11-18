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
			s.conversion = null;
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
	const HAIL = ['hail', 'snow'];
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

	// --- Third batch: the poison fields (Corrosive, Corrosive Mist, Wasteland) -------------------------------------

	/** Adds a status move's boosts on a list of fields. */
	function boostOn(move, fields, boosts) {
		BOOSTS[move] = BOOSTS[move] || {};
		for (const f of fields) BOOSTS[move][f] = boosts;
	}
	boostOn('smokescreen', ['burning', 'corrosivemist', 'volcanic', 'volcanictop', 'backalley', 'city'], {accuracy: -2});
	boostOn('sandattack', ['desert', 'ashenbeach'], {accuracy: -2});
	boostOn('flash', ['shortcircuit', 'darkcrystalcavern', 'mirror', 'starlight', 'newworld', 'darkness1'], {accuracy: -2});
	/** Who the poison of a corroded field leaves alone. */
	const POISON_PROOF = ['magicguard', 'poisonheal', 'immunity', 'wonderguard', 'toxicboost', 'pastelveil'];
	const poisonProof = (pokemon) => pokemon.hasType(['Poison', 'Steel']) || pokemon.hasAbility(POISON_PROOF);
	const RANDOM_STATUS = ['brn', 'psn', 'par', 'frz'];
	/** Reborn's hazard damage: maxhp * eff / divisor, with 1x = 4 (at least 1). */
	function hazardDamage(battle, pokemon, type, divisor) {
		const e = battle.dex.getEffectiveness(type, pokemon);
		if (!battle.dex.getImmunity(type, pokemon)) return 0;
		return Math.max(1, Math.floor(pokemon.maxhp * 4 * Math.pow(2, e) / divisor));
	}

	shared({
		tryMove(source, target, move) {
			// The corrosive mist would blow up: Damp stops Eruption and Fire Pledge.
			if (LR.field(this) !== 'corrosivemist' || !['eruption', 'firepledge'].includes(move.id)) return;
			const bearer = this.getAllActive().find((p) => p.hasAbility('damp'));
			if (!bearer) return;
			LR.say(this, 'text', 'damp_prevented');
			return false;
		},
		modifyMove(move, source) {
			const f = LR.field(this);
			if (move.id === 'direclaw' && f === 'wasteland' && move.secondaries) move.secondaries = move.secondaries.map((s) => Object.assign({}, s, {chance: 100}));
			if (move.id === 'swallow' && f === 'wasteland') move.lrStockpile = source.volatiles.stockpile ? source.volatiles.stockpile.layers : 0;
		},
		basePower(basePower, source, target, move) {
			if (source.hasAbility('corrosion') && ['corrosive', 'corrosivemist', 'corrupted'].includes(LR.field(this))) return 1.5;
		},
		entry(pokemon) {
			// The corrosion sears what touches the ground.
			if (LR.field(this) !== 'corrosive' || !pokemon.isGrounded() || pokemon.hasItem('heavydutyboots') || poisonProof(pokemon)) return;
			const damage = hazardDamage(this, pokemon, 'Poison', 16);
			if (!damage) return;
			LR.say(this, 'text', 'corrosion_seared', pokemon.name);
			this.directDamage(damage, pokemon);
		},
		afterMove(source, target, move) {
			const f = LR.field(this);
			if (source.moveThisTurnResult !== true) return;
			if (move.id === 'swallow' && move.lrStockpile) {
				// Wasteland: Swallow heals twice as much, and three stockpiles cure the status.
				const extra = [0.25, 0.5, 0][move.lrStockpile - 1];
				if (extra) this.heal(Math.floor(source.maxhp * extra), source, source);
				if (move.lrStockpile === 3 && source.status) source.cureStatus();
			}
			if (move.id === 'floralhealing' && ['corrosive', 'corrosivemist'].includes(f) && target && target.hp) target.trySetStatus('psn', source);
			if (move.id === 'lifedew' && f === 'corrosivemist') {
				for (const ally of source.alliesAndSelf()) if (ally !== source && ally.hp) ally.trySetStatus('psn', source);
			}
			// Corrosion on the Wasteland: 10% chance of a random status on what it hits.
			if (source.hasAbility('corrosion') && f === 'wasteland' && move.category !== 'Status' && target && target !== source &&
				target.hp && this.randomChance(1, 10)) target.trySetStatus(this.sample(RANDOM_STATUS), source);
		},
		residual() {
			const f = LR.field(this);
			if (f === 'wasteland') wastelandHazards(this);
			for (const pokemon of this.getAllActive()) {
				if (!pokemon.hp) continue;
				// Dry Skin drinks the poison: it hurts, unless it is a Poison type.
				if (pokemon.hasAbility('dryskin') && TOXIC_AIR.includes(f) && !pokemon.hasType('Steel')) {
					if (!pokemon.hasType('Poison')) this.damage(pokemon.baseMaxhp / 8, pokemon, pokemon, ability(this, 'dryskin'));
					else this.heal(pokemon.baseMaxhp / 8, pokemon, pokemon, ability(this, 'dryskin'));
				}
			}
		},
	});

	/** Wasteland: the waste swallows each side's hazards and spits them back at its battlers. */
	function wastelandHazards(battle) {
		for (const side of battle.sides) {
			const mons = side.active.filter((p) => p && p.hp);
			const away = (p) => p.volatiles.twoturnmove || p.volatiles.skydrop;
			const sc = side.sideConditions;
			if (sc.stealthrock) {
				side.removeSideCondition('stealthrock');
				LR.say(battle, 'text', 'waste_rocks');
				for (const p of mons) {
					if (away(p) || p.hasAbility('magicguard')) continue;
					const damage = hazardDamage(battle, p, 'Rock', 16);
					if (damage && battle.dex.getEffectiveness('Rock', p) >= 0) battle.directDamage(damage, p);
				}
			}
			if (sc.spikes) {
				const layers = sc.spikes.layers || 1;
				side.removeSideCondition('spikes');
				LR.say(battle, 'text', 'waste_spikes');
				for (const p of mons) {
					if (away(p) || !p.isGrounded() || p.hasAbility('magicguard')) continue;
					battle.directDamage(Math.max(1, Math.floor(layers * p.maxhp / 3)), p);
				}
			}
			if (sc.toxicspikes) {
				const layers = sc.toxicspikes.layers || 1;
				side.removeSideCondition('toxicspikes');
				LR.say(battle, 'text', 'waste_toxic_spikes');
				for (const p of mons) {
					if (away(p) || !p.isGrounded() || p.hasType(['Steel', 'Poison'])) continue;
					if (!p.hasAbility('magicguard')) battle.directDamage(Math.max(1, Math.floor(layers * p.maxhp / 8)), p);
					if (p.hp) p.trySetStatus(layers > 1 ? 'tox' : 'psn');
				}
			}
			if (sc.stickyweb) {
				side.removeSideCondition('stickyweb');
				LR.say(battle, 'text', 'waste_web');
				for (const p of mons) battle.boost({spe: -4}, p, p, null, true);
			}
		}
	}

	Object.assign(HOOKS, {
		/** Corrosive: Grass Pelt corrodes, the sleeping are seared. */
		corrosive: {
			residual() {
				for (const pokemon of this.getAllActive()) {
					if (!pokemon.hp) continue;
					if (pokemon.hasAbility('grasspelt')) {
						LR.say(this, 'text', 'pelt_corroded', pokemon.name);
						this.directDamage(Math.max(1, Math.floor(pokemon.maxhp / 8)), pokemon);
					}
					if (pokemon.hp && (pokemon.status === 'slp' || pokemon.hasAbility('comatose')) && pokemon.isGrounded() && !poisonProof(pokemon)) {
						LR.say(this, 'text', 'corrosion_seared', pokemon.name);
						this.directDamage(Math.max(1, Math.floor(pokemon.maxhp / 16)), pokemon);
					}
				}
			},
		},
		/** Corrosive Mist: the mist poisons everyone at the end of each turn. */
		corrosivemist: {
			residual() {
				if (this.getAllActive().some((p) => p.hasAbility('neutralizinggas'))) return;
				let said = false;
				for (const pokemon of this.getAllActive()) {
					if (!pokemon.hp || pokemon.status || !pokemon.runStatusImmunity('psn')) continue;
					if (!said) LR.say(this, 'text', 'mist_poisoned');
					said = true;
					pokemon.trySetStatus('psn', pokemon);
				}
			},
		},
	});

	// --- Fourth batch: the fire fields (Burning, Superheated, Dragon's Den) and the field defenses -----------------

	/** fieldDefenseBoost: a target's defense multiplier on a field (Misty's is its modifySpD hook). */
	function fieldDefense(f, target, move) {
		const special = move.overrideDefensiveStat ? move.overrideDefensiveStat === 'spd' : move.category === 'Special';
		const hail = target.effectiveWeather() === 'hail'; // Showdown's snow already gives Ice its x1.5
		const prism = target.hasAbility('prismarmor') ? 1.33 : 1;
		switch (f) {
		case 'darkcrystalcavern': return (target.hasType(['Dark', 'Ghost']) ? 1.5 : 1) * prism;
		case 'rainbow': case 'crystalcavern': return prism;
		case 'dragonsden': return target.hasType('Dragon') ? 1.3 : 1;
		case 'newworld': return target.isGrounded() ? 1 : 0.9;
		case 'snowymountain': case 'icy': return !special && target.hasType('Ice') && hail ? 1.5 : 1;
		case 'desert': return special && target.hasType('Ground') ? 1.5 : 1;
		case 'dimensional': return target.hasType('Ghost') ? 1.5 : 1;
		case 'frozendimension': return (target.hasType(['Ghost', 'Ice']) ? 1.2 : 1) * (target.hasType('Fire') ? 0.8 : 1);
		case 'darkness2': return target.hasType(['Dark', 'Ghost']) ? 1.1 : 1;
		case 'darkness3': return target.hasType(['Dark', 'Ghost']) ? 1.2 : 1;
		}
		return 1;
	}
	const FIRE_FIELDS = ['burning', 'volcanic', 'infernal'];
	const MAGMA_FIELDS = ['dragonsden', 'volcanictop', 'infernal'];
	boostOn('dragondance', ['bigtop', 'dragonsden'], {atk: 2, spe: 2});
	boostOn('nobleroar', ['fairytale', 'dragonsden'], {atk: -2, spa: -2});

	shared({
		setStatus(status, target) {
			if (status.id === 'frz' && ['burning', 'volcanic'].includes(LR.field(this))) return false;
		},
		tryHit(target, source, move) {
			// Magma Armor swallows Fire moves on the lava fields.
			if (move.type !== 'Fire' || !target.hasAbility('magmaarmor') || !MAGMA_FIELDS.includes(LR.field(this)) || target === source) return;
			this.add('-immune', target, '[from] ability: Magma Armor');
			return null;
		},
		basePower(basePower, source, target, move) {
			const f = LR.field(this);
			let mult = 1;
			if (FIRE_FIELDS.includes(f) && source.hasAbility('flareboost') && move.category === 'Special' && source.status !== 'brn') mult *= 1.5;
			if (FIRE_FIELDS.includes(f) && source.hasAbility('blaze') && move.type === 'Fire' && source.hp > source.maxhp / 3) mult *= 1.5;
			return mult;
		},
		modifyDamage(damage, source, target, move) {
			if (!target || move.category === 'Status') return;
			const def = fieldDefense(LR.field(this), target, move);
			if (def !== 1) return 1 / def;
		},
		entry(pokemon) {
			if (LR.field(this) === 'dragonsden' && pokemon.hasAbility('magmaarmor')) {
				this.boost({def: 1, spd: 1}, pokemon, pokemon, ability(this, 'magmaarmor'));
			}
		},
		afterMove(source, target, move) {
			const f = LR.field(this);
			// Superheated: Outrage, Thrash and Petal Dance last one turn (then confusion).
			if (['superheated', 'volcanictop'].includes(f) && source.volatiles.lockedmove) {
				source.volatiles.lockedmove.trueDuration = 1;
				source.removeVolatile('lockedmove');
			}
			if (move.id === 'payday' && f === 'dragonsden' && source.moveThisTurnResult === true) LR.say(this, 'text', 'treasure');
		},
		residual() {
			const f = LR.field(this);
			const weather = this.field.effectiveWeather();
			if (f === 'burning' && ['raindance', 'sandstorm'].includes(weather)) {
				LR.say(this, 'text', weather === 'sandstorm' ? 'sand_snuffed' : 'rain_snuffed');
				LR.breakField(this);
				return;
			}
			if (['superheated', 'volcanic', 'volcanictop', 'infernal'].includes(f) && HAIL.includes(weather)) {
				LR.say(this, 'text', 'hail_melted');
				this.field.clearWeather();
			}
			// Burn Up's lost Fire type comes back at the end of the turn.
			if (FIRE_FIELDS.includes(f)) {
				for (const pokemon of this.getAllActive()) {
					if (!pokemon.hp || !pokemon.getTypes().includes('???') || !pokemon.lastMove || pokemon.lastMove.id !== 'burnup') continue;
					pokemon.setType(pokemon.species.types);
					this.add('-start', pokemon, 'typechange', pokemon.getTypes().join('/'), '[silent]');
				}
			}
		},
	});

	/** burningFieldPassiveDamage?: what the flames reach. */
	function burnable(pokemon) {
		if (!pokemon.isGrounded() || pokemon.hasType('Fire') || pokemon.volatiles.aquaring) return false;
		if (pokemon.hasAbility(['flareboost', 'magmaarmor', 'flamebody', 'flashfire', 'waterveil', 'magicguard', 'heatproof', 'waterbubble'])) return false;
		return !pokemon.volatiles.dig && !pokemon.volatiles.dive;
	}

	Object.assign(HOOKS, {
		/** Burning: Flash Fire lights up, the flames burn the grounded (by their Fire weakness). */
		burning: {
			residual() {
				let said = false;
				for (const pokemon of this.getAllActive()) {
					if (!pokemon.hp) continue;
					if (pokemon.hasAbility('flashfire') && !pokemon.volatiles.flashfire) pokemon.addVolatile('flashfire');
					if (!burnable(pokemon)) continue;
					const e = this.dex.getEffectiveness('Fire', pokemon);
					if (e < 0) continue;
					let eff = 4 * Math.pow(2, e);
					if (pokemon.hasAbility(['leafguard', 'icebody', 'fluffy', 'grasspelt'])) eff *= 2;
					if (pokemon.volatiles.tarshot) eff *= 2;
					if (!said) LR.say(this, 'text', 'field_burned');
					said = true;
					this.directDamage(Math.max(1, Math.floor(pokemon.maxhp * eff / 32)), pokemon);
				}
			},
		},
		/** Dragon's Den: a Dragon's weakness does not reach a Multiscale. */
		dragonsden: {
			effectiveness(typeMod, target, type, move) {
				if (type === 'Dragon' && target.hasAbility('multiscale') && typeMod > 0) return 0;
			},
		},
	});

	// --- Fifth batch: ice, mountains, rocks, caves and sand (Icy, Snowy Mountain, Mountain, Rocky, Cave, Desert, Ashen Beach)

	const ICE_FIELDS = ['icy', 'snowymountain', 'frozendimension'];
	const SAND_FIELDS = ['desert', 'ashenbeach'];
	const PEAKS = ['mountain', 'snowymountain'];
	const WIND_MOVES = ['ominouswind', 'icywind', 'silverwind', 'twister', 'razorwind', 'fairywind', 'gust'];
	const BULLET_DODGE = 'bullet';
	/** Weathers that last 8 turns on these fields (as with their rock). */
	const LONG_WEATHER = {
		sunnyday: ['desert', 'mountain', 'snowymountain', 'sky'],
		sandstorm: ['desert', 'ashenbeach', 'sky'],
		hail: ['icy', 'snowymountain', 'frozendimension', 'sky', 'clouds'],
		snow: ['icy', 'snowymountain', 'frozendimension', 'sky', 'clouds'],
	};
	LR.longWeather = (battle) => {
		const w = battle.field.weatherState;
		const list = LONG_WEATHER[battle.field.weather];
		if (w && w.duration === 5 && list && list.includes(LR.field(battle))) w.duration = 8;
	};
	const strongWinds = (battle) => battle.field.weather === 'deltastream';
	boostOn('rockpolish', ['rocky'], {spe: 3});
	const ICY_SLIDES = ['feint', 'rollout', 'defensecurl', 'steamroller', 'lunge', 'iceball'];
	const ASHEN_FOCUS = ['owntempo', 'innerfocus', 'purepower', 'sandveil', 'steadfast'];

	shared({
		tryAddVolatile(status, target) {
			// Ashen Beach: a Fighting type or Inner Focus breaks through confusion.
			if (status.id !== 'confusion' || LR.field(this) !== 'ashenbeach') return;
			if (!target.hasType('Fighting') && !target.hasAbility('innerfocus')) return;
			LR.say(this, 'text', 'confusion_broken', target.name);
			return null;
		},
		tryMove(source, target, move) {
			if (move.id === 'skydrop' && LR.field(this) === 'cave') {
				LR.say(this, 'text', 'low_ceiling');
				return false;
			}
		},
		tryHit(target, source, move) {
			// Rocky: a target behind a substitute or with raised Defense hides from bullets.
			if (LR.field(this) !== 'rocky' || !move.flags[BULLET_DODGE] || target === source) return;
			if (!target.volatiles.substitute && !(target.boosts.def > 0)) return;
			LR.say(this, 'text', 'rock_hide', target.name);
			return null;
		},
		chargeMove(pokemon, target, move) {
			if (!['fly', 'bounce'].includes(move.id) || !['cave', 'clouds', 'sky'].includes(LR.field(this))) return;
			this.attrLastMove('[still]');
			this.addMove('-anim', pokemon, move.name, target);
			return false;
		},
		modifyType(move, pokemon) {
			const f = LR.field(this);
			if (f === 'ashenbeach' && move.id === 'strength') move.type = 'Fighting';
			if (f === 'icy' && move.flags.sound && pokemon.hasAbility('liquidvoice')) move.type = 'Ice';
		},
		modifyMove(move, source) {
			const f = LR.field(this);
			// Cave: Ground moves reach what flies (Levitate included).
			if (f === 'cave' && move.type === 'Ground') move.ignoreImmunity = Object.assign({}, move.ignoreImmunity || {}, {Ground: true});
			if (move.id === 'bittermalice' && ['icy', 'snowymountain'].includes(f)) {
				move.secondaries = (move.secondaries || []).concat([{chance: 10, status: 'frz'}]);
			}
			if (f === 'ashenbeach' && source.hasAbility(ASHEN_FOCUS) && typeof move.accuracy === 'number' &&
				!source.foes().some((p) => p.hasAbility(['unnerve', 'asonespectrier', 'asoneglastrier']))) {
				move.accuracy = true;
			}
			if (source.hasAbility('longreach') && ['rocky', 'forest'].includes(f) && typeof move.accuracy === 'number') move.accuracy *= 0.9;
		},
		basePower(basePower, source, target, move) {
			const f = LR.field(this);
			const changer = move.typeChangerBoosted && move.typeChangerBoosted.id;
			let mult = 1;
			if (changer === 'aerilate' && [...PEAKS, 'sky'].includes(f)) mult *= 1.5 / 1.2;
			if (changer === 'refrigerate' && ICE_FIELDS.includes(f)) mult *= 1.5 / 1.2;
			if (source.hasAbility('punkrock') && move.flags.sound && ['cave', 'bigtop'].includes(f)) mult *= 1.5 / 1.3;
			if (move.flags.sound && f === 'cave') mult *= 1.5;
			if (PEAKS.includes(f) && strongWinds(this) && (WIND_MOVES.includes(move.id) || (move.type === 'Flying' && move.category === 'Special'))) mult *= 1.5;
			if (source.hasAbility('longreach') && [...PEAKS, 'sky'].includes(f)) mult *= 1.5;
			if (source.hasAbility('sandforce') && SAND_FIELDS.includes(f) && ['Rock', 'Ground', 'Steel'].includes(move.type) &&
				this.field.effectiveWeather() !== 'sandstorm') mult *= 1.3;
			return mult;
		},
		modifySpe(spe, pokemon) {
			const f = LR.field(this);
			if (pokemon.hasAbility('sandrush') && SAND_FIELDS.includes(f) && pokemon.effectiveWeather() !== 'sandstorm') return 2;
			if (pokemon.hasAbility('slushrush') && ICE_FIELDS.includes(f) && !HAIL.includes(pokemon.effectiveWeather())) return 2;
		},
		afterMove(source, target, move) {
			const f = LR.field(this);
			if (source.moveThisTurnResult !== true) {
				// Rocky: a physical contact move that misses hits a rock.
				if (f === 'rocky' && move.flags.contact && move.category === 'Physical' && !source.hasAbility('rockhead') &&
					target && target !== source && !target.volatiles.protect && source.hp) {
					LR.say(this, 'text', 'hit_rock', source.name);
					this.directDamage(Math.max(1, Math.floor(source.maxhp / (source.hasAbility('gorillatactics') ? 4 : 8))), source);
				}
				return;
			}
			// Icy: priority contact moves (and a few rolling ones) slide the grounded user faster.
			if (f === 'icy' && source.isGrounded() && ((move.priority >= 1 && move.category !== 'Status' && move.flags.contact &&
				!source.hasAbility('longreach')) || ICY_SLIDES.includes(move.id))) {
				this.boost({spe: 1}, source, source, null, true);
			}
			if (move.id === 'tailwind' && ['mountain', 'snowymountain', 'volcanictop', 'clouds', 'sky'].includes(f)) {
				const tw = source.side.sideConditions.tailwind;
				if (tw) tw.duration = f === 'sky' ? 8 : 6;
				if (f !== 'clouds' && !['primordialsea', 'desolateland'].includes(this.field.weather) && this.field.setWeather('deltastream', source)) {
					this.field.weatherState.duration = 6;
				}
			}
			if (move.id === 'psychup' && f === 'ashenbeach' && source.status) source.cureStatus();
		},
		residual() {
			const f = LR.field(this);
			const weather = this.field.effectiveWeather();
			if (f === 'mountain' && HAIL.includes(weather)) {
				const s = LR.state(this);
				s.counter[0] += 1;
				if (s.counter[0] >= 3) {
					LR.say(this, 'text', 'mountain_snow');
					LR.set(this, 'snowymountain');
					return;
				}
			}
			for (const pokemon of this.getAllActive()) {
				if (!pokemon.hp) continue;
				if (pokemon.hasAbility('icebody') && ICE_FIELDS.includes(f) && !HAIL.includes(pokemon.effectiveWeather())) {
					this.heal(pokemon.baseMaxhp / 16, pokemon, pokemon, ability(this, 'icebody'));
				}
				if (pokemon.hasAbility('dryskin') && f === 'desert') this.damage(pokemon.baseMaxhp / 8, pokemon, pokemon, ability(this, 'dryskin'));
				if (pokemon.hasAbility('wanderingspirit') && ['desert', 'haunted'].includes(f)) this.boost({spe: -1}, pokemon, pokemon, null, true);
				// Ashen Beach: Sand Tomb also blinds.
				const trap = pokemon.volatiles.partiallytrapped;
				if (f === 'ashenbeach' && trap && trap.sourceEffect && trap.sourceEffect.id === 'sandtomb') this.boost({accuracy: -1}, pokemon);
			}
		},
	});

	Object.assign(HOOKS, {
		/** Snowy Mountain: an Ice weakness does not reach Ice Scales. */
		snowymountain: {
			effectiveness(typeMod, target, type) {
				if (type === 'Ice' && target.hasAbility('icescales') && typeMod > 0) return 0;
			},
		},
	});

	// --- Sixth batch: Forest, Factory, Short-Circuit, Inverse, Flower Garden, no field -------------------------------

	const GARDEN = ['flowergarden1', 'flowergarden2', 'flowergarden3', 'flowergarden4', 'flowergarden5'];
	const GARDEN_HIGH = ['flowergarden3', 'flowergarden4', 'flowergarden5'];
	const LEAFGUARD_FIELDS = ['forest', 'flowergarden2', ...GARDEN_HIGH];
	const MACHINES = ['factory', 'shortcircuit'];
	/** Short-Circuit's Electric damage rolls, used in turn (PBStuff::SHORTCIRCUITROLLS). */
	const SC_ROLLS = [0.8, 1.5, 0.5, 1.2, 2.0];
	boostOn('growth', ['forest', 'flowergarden1', 'flowergarden2'], {atk: 2, spa: 2});
	boostOn('growth', GARDEN_HIGH, {atk: 3, spa: 3});
	boostOn('defendorder', ['forest'], {def: 2, spd: 2});
	boostOn('autotomize', ['factory', 'deepearth', 'city'], {spe: 3});
	boostOn('shiftgear', ['factory', 'city'], {atk: 2, spe: 2});
	boostOn('metalsound', MACHINES, {spd: -3});
	boostOn('sweetscent', ['flowergarden3'], {evasion: -1, def: -1, spd: -1});
	boostOn('sweetscent', ['flowergarden4'], {evasion: -2, def: -2, spd: -2});
	boostOn('sweetscent', ['flowergarden5'], {evasion: -3, def: -3, spd: -3});
	/** Swarm and Overgrow in the Flower Garden (by stage), Overgrow and Swarm in the Forest. */
	function gardenPinch(battle, source, move) {
		const f = LR.field(battle);
		const stage = GARDEN.indexOf(f) + 1;
		if (source.hasAbility('swarm') && move.type === 'Bug') {
			if (f === 'forest') return 1.5;
			if (stage) return stage <= 2 ? 1.5 : stage <= 4 ? 1.8 : 2;
		}
		if (source.hasAbility('overgrow') && move.type === 'Grass') {
			if (f === 'forest') return 1.5;
			if (stage === 2) return source.hp <= Math.floor(source.maxhp * 0.67) ? 1.5 : 1;
			if (stage >= 3) return [1.6, 1.8, 2][stage - 3];
		}
		return 1;
	}

	shared({
		setStatus(status, target) {
			if (target.hasAbility('leafguard') && LEAFGUARD_FIELDS.includes(LR.field(this))) return false;
		},
		modifyMove(move) {
			const f = LR.field(this);
			if (move.id === 'healorder' && f === 'forest') move.heal = [2, 3];
		},
		basePower(basePower, source, target, move) {
			const f = LR.field(this);
			let mult = gardenPinch(this, source, move);
			// Showdown already boosts a pinched Swarm or Overgrow by 1.5.
			if (mult !== 1 && source.hp <= source.maxhp / 3) mult /= 1.5;
			if (f === 'shortcircuit' && move.type === 'Electric') {
				const s = LR.state(this);
				const i = (s.roll || 0) % SC_ROLLS.length;
				s.roll = i + 1;
				LR.say(this, 'text', 'bzzt' + i);
				mult *= SC_ROLLS[i];
			}
			if (move.id === 'terrainpulse' && f !== NONE && !this.field.terrain) mult *= 2;
			return mult;
		},
		afterMove(source, target, move) {
			const f = LR.field(this);
			// Topsy-Turvy inverts the field even when no stat changed (Showdown fails it then).
			if (move.id === 'topsyturvy' && target && target !== source) layField(this, source, 'inverse', 3);
			if (source.moveThisTurnResult !== true) return;
			if (move.id === 'magnetrise' && MACHINES.includes(f)) {
				const rise = source.volatiles.magnetrise;
				if (rise && rise.duration === 5) rise.duration = 8;
			}
			if (move.id === 'forestscurse' && ['forest', 'fairytale', 'bewitched'].includes(f) && target && target.hp) target.addVolatile('curse', source);
			if (move.id === 'steelroller' && LR.canChange(this, NONE)) {
				LR.say(this, 'text', 'flattened');
				LR.set(this, NONE, source.hasItem('amplifieldrock') ? 8 : 5);
			}
		},
		residual() {
			const f = LR.field(this);
			for (const pokemon of this.getAllActive()) {
				if (!pokemon.hp || pokemon.hp >= pokemon.maxhp) continue;
				if (f === 'forest' && pokemon.hasAbility('sapsipper') && this.heal(pokemon.baseMaxhp / 16, pokemon, pokemon)) {
					LR.say(this, 'text', 'sap_drunk', pokemon.name);
				}
				if (f === 'shortcircuit' && pokemon.hasAbility('voltabsorb') && this.heal(pokemon.baseMaxhp / 16, pokemon, pokemon)) {
					LR.say(this, 'text', 'stray_electricity', pokemon.name);
				}
			}
		},
	});
	Object.assign(HOOKS, {
		/** Inverse: every matchup is turned around, immunities become weaknesses (Showdown's inversemod). */
		inverse: {
			effectiveness(typeMod, target, type, move) {
				if (move.id === 'freezedry' && type === 'Water') return;
				if (!this.dex.getImmunity(move, type)) return 1;
				return -typeMod;
			},
		},
	});
	// --- Seventh batch: Rainbow, Starlight, Holy, Crystal Cavern, Dark Crystal Cavern, Mirror ----------------------

	const ALL_TYPES = ['Fire', 'Water', 'Grass', 'Electric', 'Ice', 'Fighting', 'Poison', 'Ground', 'Flying', 'Psychic', 'Bug',
		'Rock', 'Ghost', 'Dragon', 'Dark', 'Steel', 'Fairy'];
	/** PBStuff::CCROLLS: the Crystal Cavern's crystals, in turn. */
	const CC_ROLLS = ['Fire', 'Water', 'Grass', 'Psychic'];
	const CC_MOVES = ['judgment', 'rockclimb', 'strength', 'multiattack', 'prismaticlaser'];
	const MIRROR_MOVES = ['chargebeam', 'solarbeam', 'psybeam', 'triattack', 'bubblebeam', 'hyperbeam', 'icebeam', 'originpulse',
		'moongeistbeam', 'fleurcannon'];
	const BLINDING_MOVES = ['aurorabeam', 'signalbeam', 'flashcannon', 'lusterpurge', 'dazzlinggleam', 'technoblast', 'doomdummy',
		'prismaticlaser', 'photongeyser', 'lightthatburnsthesky', 'mirrorshot'];
	const DARK_SKIES = ['starlight', 'newworld', 'darkcrystalcavern', 'darkness2', 'darkness3'];
	boostOn('doubleteam', ['mirror'], {evasion: 2});
	boostOn('rockpolish', ['crystalcavern'], {spe: 2, atk: 1});
	/** Moonlight and its kin on the dark and starry fields (the factor of max HP healed). */
	LR.moonFactor = (battle, id, factor) => {
		const f = LR.field(battle);
		if (id === 'moonlight' && ['darkcrystalcavern', 'starlight', 'newworld', 'bewitched'].includes(f)) return 0.75;
		if (id === 'moonlight' && f === 'darkness1') return 0.4;
		if (id !== 'moonlight' && ['darkcrystalcavern', 'darkness2'].includes(f)) return 0.25;
		return factor;
	};
	/** A weather change: rain and sun meeting make a rainbow; sun lights the dark crystals. */
	LR.weatherSet = (battle, before, now, source) => {
		const f = LR.field(battle);
		const meet = (before === 'raindance' && now === 'sunnyday') || (before === 'sunnyday' && now === 'raindance');
		if (meet && f !== 'rainbow' && LR.canChange(battle, 'rainbow')) {
			const held = source && source.hasItem && source.hasItem(['heatrock', 'damprock']);
			const turns = held || ['desert', 'mountain', 'snowymountain', 'sky'].includes(f) ? 8 : 5;
			LR.say(battle, 'text', 'rainbow_formed');
			LR.set(battle, 'rainbow', turns);
		} else if (now === 'sunnyday' && f === 'darkcrystalcavern' && LR.canChange(battle, 'crystalcavern')) {
			LR.say(battle, 'text', 'crystals_lit');
			LR.set(battle, 'crystalcavern', (battle.field.weatherState.duration || 5) + 1);
		}
	};

	shared({
		tryHit(target, source, move) {
			const f = LR.field(this);
			// Holy: attacks pass through allies (Telepathy for everyone).
			if (f === 'holy' && move.category !== 'Status' && target !== source && target.side === source.side) {
				this.add('-activate', target, 'ability: Telepathy');
				return null;
			}
			// Starlight: Mirror Armor dazzles priority moves like Queenly Majesty.
			if (f === 'starlight' && move.priority > 0 && target.side !== source.side &&
				target.alliesAndSelf().some((p) => p.hasAbility('mirrorarmor'))) {
				this.add('cant', source, 'ability: Mirror Armor', move);
				return null;
			}
		},
		chargeMove(pokemon, target, move) {
			const f = LR.field(this);
			const skip = (['solarbeam', 'solarblade'].includes(move.id) && f === 'rainbow') ||
				(move.id === 'meteorbeam' && ['starlight', 'newworld'].includes(f));
			if (!skip) return;
			this.attrLastMove('[still]');
			this.addMove('-anim', pokemon, move.name, target);
			return false;
		},
		modifyType(move) {
			if (LR.field(this) === 'starlight' && ['solarbeam', 'solarblade'].includes(move.id)) move.type = 'Fairy';
		},
		modifyMove(move, source) {
			const f = LR.field(this);
			// Rainbow: every added effect is twice as likely (Serene Grace does not stack).
			if (f === 'rainbow' && !source.hasAbility('serenegrace') && move.secondaries) {
				move.secondaries = move.secondaries.map((s) => s.chance ? Object.assign({}, s, {chance: Math.min(100, s.chance * 2)}) : s);
			}
			if (f === 'rainbow' && move.id === 'sonicboom') move.damage = 140;
			if (f === 'rainbow' && move.category === 'Special' && move.type === 'Normal') move.lrAdded = this.sample(ALL_TYPES);
			if (f === 'crystalcavern' && (move.type === 'Rock' || CC_MOVES.includes(move.id))) {
				const s = LR.state(this);
				const i = (s.ccRoll || 0) % CC_ROLLS.length;
				s.ccRoll = i + 1;
				move.lrAdded = CC_ROLLS[i];
			}
			if (f === 'mirror' && BLINDING_MOVES.includes(move.id)) move.accuracy = true;
			if (f === 'mirror' && move.id === 'mirrorshot' && move.secondaries) move.secondaries = move.secondaries.map((s) => Object.assign({}, s, {chance: 100}));
			if (move.id === 'meteorassault' && f === 'starlight') delete move.self;
			if (move.id === 'lifedew' && ['rainbow', 'holy'].includes(f)) move.heal = [1, 2];
			if (move.id === 'naturesmadness' && f === 'holy') move.damageCallback = (pokemon, target) => Math.max(1, Math.floor(target.hp * 0.66));
		},
		basePower(basePower, source, target, move) {
			const f = LR.field(this);
			let mult = 1;
			if (move.id === 'weatherball' && f === 'rainbow' && !this.field.weather) mult *= 2;
			if (f === 'mirror' && MIRROR_MOVES.includes(move.id) && target && target.boosts.evasion > 0) mult *= 2;
			if (source.hasAbility('victorystar') && ['starlight', 'newworld'].includes(f)) mult *= 1.5;
			return mult;
		},
		modifyDamage(damage, source, target, move) {
			if (target && target.hasAbility('shadowshield') && DARK_SKIES.includes(LR.field(this)) && target.getMoveHitData(move).typeMod > 0) return 0.75;
		},
		entry(pokemon) {
			const f = LR.field(this);
			if (f === 'starlight' && pokemon.hasAbility('illuminate')) this.boost({spa: 1}, pokemon, pokemon, ability(this, 'illuminate'));
			if (f === 'mirror' && pokemon.hasAbility(['snowcloak', 'sandveil', 'tangledfeet', 'magicbounce', 'colorchange'])) {
				this.boost({evasion: 1}, pokemon, pokemon, pokemon.getAbility());
			}
		},
		afterMove(source, target, move) {
			const f = LR.field(this);
			if (source.moveThisTurnResult !== true) {
				// Mirror: a physical contact move that misses hits a mirror.
				if (f === 'mirror' && move.flags.contact && move.category === 'Physical' && target && target !== source && source.hp) {
					LR.say(this, 'text', 'hit_mirror', source.name);
					this.directDamage(Math.max(1, Math.floor(source.maxhp / 4)), source);
				}
				return;
			}
			if (f === 'mirror' && ['reflect', 'lightscreen', 'auroraveil'].includes(move.id)) {
				const screen = source.side.sideConditions[move.id];
				if (screen && screen.duration === 5) screen.duration = 8;
				this.boost({evasion: 1}, source, source, null, true);
			}
			if (f === 'mirror' && move.id === 'mirrormove') this.boost({atk: 1, spa: 1, accuracy: 1}, source, source, null, true);
		},
		residual() {
			const f = LR.field(this);
			// Dark Crystal Cavern's sunlit crystals fade with the sun.
			if (f === 'crystalcavern' && LR.backup(this) === 'darkcrystalcavern' && LR.state(this).overlay != null &&
				this.field.effectiveWeather() !== 'sunnyday') {
				LR.endTemp(this);
				return;
			}
			for (const pokemon of this.getAllActive()) {
				if (!pokemon.hp) continue;
				if (f === 'rainbow' && (pokemon.status === 'slp' || pokemon.hasAbility('comatose')) && pokemon.hp < pokemon.maxhp &&
					this.heal(pokemon.baseMaxhp / 16, pokemon, pokemon)) LR.say(this, 'text', 'rainbow_dream', pokemon.name);
				if (f === 'holy' && pokemon.volatiles.curse) {
					pokemon.removeVolatile('curse');
					LR.say(this, 'text', 'curse_lifted', pokemon.name);
				}
			}
		},
	});
	const addedOnMove = (move) => move.lrAdded;
	Object.assign(HOOKS, {
		rainbow: {addedType: addedOnMove},
		crystalcavern: {addedType: addedOnMove},
		/** Holy: Normal moves strike Dark and Ghost types hard. */
		holy: {
			effectiveness(typeMod, target, type, move) {
				if (move.type === 'Normal' && ['Dark', 'Ghost'].includes(type)) return 1;
			},
		},
		shortcircuit: {
			addedType(move, source) {
				if (move.type === 'Steel' && source && source.hasAbility('steelworker')) return 'Electric';
			},
		},
	});
	HOOKS.corrosivemist.addedType = (move) => (move.type === 'Flying' && move.category === 'Special' ? 'Poison' : undefined);

	/** Steel Beam and Chloroblast recoil: 1/4 on Factory (Steel Beam) and Forest (Chloroblast), all HP on Short-Circuit. */
	LR.beamRecoil = (battle, pokemon, move) => {
		const f = LR.field(battle);
		if ((f === 'factory' && move.id === 'steelbeam') || (f === 'forest' && move.id === 'chloroblast')) return Math.ceil(pokemon.maxhp / 4);
		if (f === 'shortcircuit' && move.id === 'steelbeam') return pokemon.maxhp;
		return Math.round(pokemon.maxhp / 2);
	};

	// --- Eighth batch: Glitch, New World, Fairy Tale, Chess Board, Big Top Arena -----------------------------------

	/** Glitch: the gen-3 split, a move's category comes from its type (PBTypes.isSpecialType?). */
	const GLITCH_SPECIAL = ['Fire', 'Water', 'Grass', 'Electric', 'Psychic', 'Ice', 'Dragon', 'Dark'];
	const GLITCH_NORMAL = ['Dark', 'Steel', 'Fairy'];
	const CHESS_MOVES = ['strength', 'ancientpower', 'psychic', 'continentalcrush', 'secretpower', 'shatteredpsyche'];
	const STRIKER_MOVES = ['strength', 'woodhammer', 'dualchop', 'heatcrash', 'skydrop', 'bulldoze', 'pound', 'iciclecrash',
		'bodyslam', 'stomp', 'slam', 'gigaimpact', 'smackdown', 'irontail', 'meteormash', 'dragonrush', 'crabhammer', 'bounce',
		'heavyslam', 'magnitude', 'earthquake', 'stompingtantrum', 'brutalswing', 'highhorsepower', 'icehammer',
		'dragonhammer', 'blazekick', 'continentalcrush'];
	/** Big Top's strength meter: [roll at least, multiplier], best first (texts striker_4..striker_0). */
	const STRIKER = [[15, 3], [13, 2], [9, 1.5], [3, 1], [-99, 0.5]];
	const STRIKER_STRONG = ['hugepower', 'guts', 'purepower', 'sheerforce'];
	boostOn('swordsdance', ['bigtop', 'fairytale', 'colosseum', 'dancefloor'], {atk: 3});
	boostOn('quiverdance', ['bigtop', 'dancefloor'], {spa: 2, spd: 2, spe: 2});
	boostOn('victorydance', ['bigtop', 'dancefloor'], {atk: 2, def: 2, spe: 2});
	boostOn('featherdance', ['bigtop'], {atk: -3});
	boostOn('clangoroussoul', ['bigtop'], {atk: 2, def: 2, spa: 2, spd: 2, spe: 2});
	boostOn('noretreat', ['chess'], {atk: 2, spa: 2, spe: 2, def: -1, spd: -1});
	LONG_WEATHER.raindance = ['bigtop', 'clouds', 'sky'];

	/**
	 * Chess Board pieces (Battle#pieceAssignment), once per side: the last Pokémon is the Queen, the lead a Pawn, the
	 * frailest of the others (King's Rock first) the King; the rest by their best stat (Speed: Knight, attacks: Bishop,
	 * defenses: Rook).
	 */
	function piece(pokemon) {
		const side = pokemon.side;
		if (!side.lrPieces) {
			side.lrPieces = true;
			const party = side.pokemon.slice();
			const set = (p, name) => {
				if (p && !p.lrPiece) p.lrPiece = name;
			};
			for (const p of party) p.lrPiece = null;
			set(party[party.length - 1], 'queen');
			set(party.find((p) => p.hp > 0), 'pawn');
			const rest = party.filter((p) => !p.lrPiece);
			rest.sort((a, b) => (a.item === 'kingsrock' ? 0 : 1) - (b.item === 'kingsrock' ? 0 : 1) || a.maxhp - b.maxhp);
			set(rest[0], 'king');
			for (const p of party) {
				if (p.lrPiece) continue;
				const s = p.storedStats;
				const best = Math.max(s.spe, s.atk, s.spa, s.def, s.spd);
				let name = 'knight';
				if (best === Math.max(s.atk, s.spa)) name = 'bishop';
				if (best === Math.max(s.def, s.spd)) name = 'rook';
				p.lrPiece = name;
			}
		}
		return pokemon.lrPiece || null;
	}
	LR.piece = piece;
	const PIECE_BOOSTS = {bishop: {atk: 1, spa: 1}, rook: {def: 1, spd: 1}, queen: {def: 1, spd: 1}};
	/** King's Shield and Obstruct also stop status moves there (Battler.rb pbTryUseMove). */
	LR.shieldsStatus = (battle, id) => {
		const f = LR.field(battle);
		return f === 'chess' || (id === 'kingsshield' && f === 'fairytale');
	};
	/** King's Shield takes Special Attack too there. */
	LR.kingsDrop = (battle) => (['fairytale', 'chess', 'colosseum'].includes(LR.field(battle)) ? {atk: -1, spa: -1} : {atk: -1});
	const moveWorked = (battle, move) => battle.lastSuccessfulMoveThisTurn === move.id;

	shared({
		afterMove(source, target, move) {
			const f = LR.field(this);
			// Conversion then Conversion 2 (or the other way round) corrupt the field into a Glitch Field.
			if ((move.id === 'conversion' || move.id === 'conversion2') && moveWorked(this, move)) {
				const s = LR.state(this);
				const mine = move.id === 'conversion' ? 1 : 2;
				if (s.conversion && s.conversion !== mine && !source.hasItem('everstone') && LR.canChange(this, 'glitch')) {
					LR.set(this, 'glitch', source.hasItem('amplifieldrock') ? 8 : 5);
					LR.say(this, 'text', 'glitch_corrupt');
				} else {
					s.conversion = mine;
				}
			}
			if (!moveWorked(this, move)) return;
			if (f === 'fairytale') {
				// A kiss wakes the sleeping.
				if ((move.id === 'sweetkiss' || move.id === 'drainingkiss') && target && target.hp && target.status === 'slp' &&
					!target.volatiles.substitute) target.cureStatus();
				if (move.id === 'craftyshield' || (move.id === 'flowershield' && !source.hasType('Grass'))) {
					LR.say(this, 'text', 'shield_boost', source.name);
					this.boost({def: 1, spd: 1}, source, source);
				}
			}
			if (f === 'bigtop') {
				if (move.id === 'bellydrum') this.boost({def: 1}, source, source);
				if (move.id === 'spotlight' && target) {
					this.boost({atk: 1, spa: 1}, source, source);
					if (target !== source && target.hp) this.boost({atk: 1, spa: 1}, target, source);
				}
				// Dancer: a dance on the Big Top lifts the dancers.
				if (move.flags.dance) {
					for (const p of this.getAllActive()) {
						if (p !== source && p.hp && p.hasAbility('dancer')) this.boost({spa: 1, spe: 1}, p, p, ability(this, 'dancer'));
					}
				}
			}
		},
	});

	/** The Pawn holds on at 1 HP from full (a Focus Sash of its own), and one-hit KO moves miss it. */
	const pawnHolds = (battle, target) => LR.field(battle) === 'chess' && piece(target) === 'pawn';

	Object.assign(HOOKS, {
		glitch: {
			modifyType(move) {
				if (GLITCH_NORMAL.includes(move.type)) move.type = 'Normal';
				if (move.category !== 'Status') move.category = GLITCH_SPECIAL.includes(move.type) ? 'Special' : 'Physical';
			},
			/** One Special stat: the higher of Sp. Atk and Sp. Def, for the attack and for the defense (getSpecialStat). */
			tryHit(target, source, move) {
				if (move.type === 'Ghost' && target.hasType('Psychic') && move.category !== 'Status') {
					this.add('-immune', target);
					return null;
				}
				if (move.category !== 'Special') return;
				if (!move.overrideOffensiveStat) move.overrideOffensiveStat = source.getStat('spd') > source.getStat('spa') ? 'spd' : 'spa';
				if (!move.overrideDefensiveStat || move.lrGlitchDef) {
					move.lrGlitchDef = true;
					move.overrideDefensiveStat = target.getStat('spa') > target.getStat('spd') ? 'spa' : 'spd';
				}
			},
			/** The old type chart: Dragon hits everything neutrally, Bug and Poison hit each other hard, Ice is fine on Fire. */
			effectiveness(typeMod, target, type, move) {
				if (move.type === 'Dragon') return 0;
				if ((move.type === 'Bug' && type === 'Poison') || (move.type === 'Poison' && type === 'Bug')) return 1;
				if (move.type === 'Ice' && type === 'Fire') return 0;
			},
			/** Explosion and Self-Destruct halve the defense, as in the first games. */
			modifyDamage(damage, source, target, move) {
				return ['explosion', 'selfdestruct'].includes(move.id) ? 2 : 1;
			},
			/** A faster attacker lands more critical hits. */
			critRatio(critRatio, source, target) {
				return target && source.getStat('spe') > target.getStat('spe') ? 1 : 0;
			},
			/** Hyper Beam needs no recharge after a knock-out. */
			afterMove(source, target, move) {
				if (move.flags.recharge && target && target.hp <= 0 && source.volatiles.mustrecharge) source.removeVolatile('mustrecharge');
			},
		},
		newworld: {
			modifyMove(move) {
				if (move.id === 'naturesmadness') {
					move.damageCallback = function (pokemon, target) {
						return this.clampIntRange(Math.floor(target.maxhp * 0.5), 1);
					};
				}
			},
		},
		fairytale: {
			modifyType(move) {
				if (['sacredsword', 'cut', 'slash', 'secretsword'].includes(move.id)) move.type = 'Steel';
			},
			modifyMove(move, source) {
				if (move.id === 'strangesteam') move.secondaries = [{chance: 100, volatileStatus: 'confusion'}];
				if (source && source.hasAbility('fairyaura') && move.accuracy !== true) move.accuracy = true;
				if (move.id === 'floralhealing') {
					move.onHit = function (target, src) {
						const success = !!this.heal(target.baseMaxhp);
						if (success && !target.isAlly(src)) target.staleness = 'external';
						if (!success) {
							this.add('-fail', target, 'heal');
							return this.NOT_FAIL;
						}
						return success;
					};
				}
			},
			/** Steel slays dragons. */
			effectiveness(typeMod, target, type, move) {
				if (move.type === 'Steel' && type === 'Dragon') return 1;
			},
			basePower(basePower, source, target, move) {
				let mult = 1;
				if (source.hasAbility('queenlymajesty')) mult *= 1.5;
				if (move.type === 'Steel' && [source, ...source.alliesAndSelf()].some((p) => p.hasAbility('steelyspirit'))) mult *= 2 / 1.5;
				return mult;
			},
			/** Shining armor and magical power at the switch-in. */
			entry(pokemon) {
				if (pokemon.hasAbility(['battlearmor', 'shellarmor', 'powerofalchemy', 'stancechange'])) {
					LR.say(this, 'text', 'shining_armor', pokemon.name);
					this.boost({def: 1}, pokemon, pokemon);
				}
				if (pokemon.hasAbility(['magicguard', 'magicbounce', 'powerofalchemy', 'mirrorarmor', 'pastelveil'])) {
					LR.say(this, 'text', pokemon.hasAbility('mirrorarmor') ? 'reflective_armor' : 'magic_spdef', pokemon.name);
					this.boost({spd: 1}, pokemon, pokemon);
				}
			},
		},
		chess: {
			entry(pokemon) {
				const p = piece(pokemon);
				if (!p) return;
				LR.say(this, 'text', 'piece_' + p, pokemon.name);
				if (PIECE_BOOSTS[p]) this.boost(PIECE_BOOSTS[p], pokemon, pokemon);
			},
			priority(priority, pokemon) {
				return piece(pokemon) === 'king' ? 1 : 0;
			},
			tryHit(target, source, move) {
				if (move.ohko && piece(target) === 'pawn') {
					this.add('-immune', target);
					return null;
				}
			},
			basePower(basePower, source, target, move) {
				let mult = 1;
				if (CHESS_MOVES.includes(move.id) && target) {
					if (target.hasAbility(['adaptability', 'anticipation', 'synchronize', 'telepathy'])) mult *= 0.5;
					if (target.hasAbility(['oblivious', 'klutz', 'unaware', 'simple']) || target.volatiles.confusion) mult *= 2;
					if (!move.lrChessMsg) LR.say(this, 'text', 'chess_slam');
					move.lrChessMsg = true;
				}
				if (piece(source) === 'queen' || source.hasAbility('queenlymajesty')) {
					mult *= 1.5;
					if (piece(source) === 'queen' && !move.lrQueenMsg) LR.say(this, 'text', 'queen_dominates');
					move.lrQueenMsg = true;
				}
				if (piece(source) === 'knight' && target && piece(target) === 'queen') {
					mult *= 3;
					LR.say(this, 'text', 'knight_queen');
				}
				return mult;
			},
			/** A Knight's spread move forks: x1.25 instead of the spread x0.75. */
			modifyDamage(damage, source, target, move) {
				if (piece(source) !== 'knight' || !move.spreadHit || move.target !== 'allAdjacentFoes') return 1;
				if (!move.lrForkMsg) LR.say(this, 'text', 'knight_fork');
				move.lrForkMsg = true;
				return 1.25 / 0.75;
			},
			damage(damage, target, source, effect) {
				if (!effect || effect.effectType !== 'Move' || piece(target) !== 'pawn') return;
				if (target.hp === target.maxhp && damage >= target.hp) {
					LR.say(this, 'text', 'pawn_hold', target.name);
					return target.hp - 1;
				}
			},
		},
		bigtop: {
			basePower(basePower, source, target, move) {
				let mult = 1;
				// The strength meter: physical Fighting moves and the hammer blows.
				if ((move.type === 'Fighting' && move.category === 'Physical') || STRIKER_MOVES.includes(move.id)) {
					let roll = this.random(1, 15);
					if (source.hasAbility(STRIKER_STRONG)) roll = roll >= 9 ? 15 : 14;
					roll += source.boosts.atk;
					const i = STRIKER.findIndex(([min]) => roll >= min);
					if (!move.lrStrikerMsg) LR.say(this, 'text', 'whammo');
					move.lrStrikerMsg = true;
					LR.say(this, 'text', 'striker_' + (4 - i));
					mult *= STRIKER[i][1];
				}
				if (move.flags.sound) {
					mult *= 1.5;
					if (!move.lrLoudMsg) LR.say(this, 'text', 'loud_clear');
					move.lrLoudMsg = true;
				}
				return mult;
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
		magmastorm: ['dragonsden'], sandtomb: ['desert'], infestation: ['forest', 'flowergarden3'], firespin: ['burning', 'haunted'],
	};
	/** ...and those that squeeze more than one step. */
	const BIND_STEPS = {infestation: {flowergarden4: 2, flowergarden5: 3}};
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
			if (LR.field(this) === 'corrosivemist' && !pokemon.hasType(['Steel', 'Poison'])) {
				LR.say(this, 'text', 'aquaring_poison', pokemon.name);
				this.damage(pokemon.baseMaxhp / 16, pokemon, pokemon);
				return;
			}
			this.heal(pokemon.baseMaxhp / (AQUARING_FIELDS.includes(LR.field(this)) ? 8 : 16));
		}],
		// Roots drink foul nutrients on the Swamp and the corroded fields.
		['conditions', 'ingrain', 'onResidual', (orig) => function (pokemon) {
			const f = LR.field(this);
			if (!['swamp', 'corrosive', 'corrupted'].includes(f) || pokemon.hasType(['Steel', 'Poison'])) {
				// Forest and Flower Garden: the roots drink more.
				const div = ['flowergarden3', 'flowergarden4', 'flowergarden5'].includes(f) ? 4 : ['forest', 'flowergarden2'].includes(f) ? 8 : 0;
				if (div) return void this.heal(pokemon.baseMaxhp / div);
				return orig.call(this, pokemon);
			}
			if (pokemon.hasAbility('magicguard')) return;
			LR.say(this, 'text', 'foul_roots', pokemon.name);
			this.damage(pokemon.baseMaxhp / 16, pokemon, pokemon);
		}],
		['conditions', 'leechseed', 'onResidual', (orig) => rewrite(orig, [/pokemon\.baseMaxhp \/ 8/, '(pokemon.baseMaxhp / (LR.field(this) === "wasteland" ? 4 : 8))'])],
		// Corrosive: Poison types do not absorb Toxic Spikes (a rewrite, so before any wrapper of the same function).
		['conditions', 'toxicspikes', 'onEntryHazard', (orig) => rewrite(orig, [/if \(pokemon\.hasType\("Poison"\)\) \{/, 'if (pokemon.hasType("Poison") && LR.field(this) !== "corrosive") {'])],
		// Rocky and Cave: Stealth Rock cuts twice as deep (a rewrite, before the Wasteland wrapper).
		['conditions', 'stealthrock', 'onEntryHazard', (orig) => rewrite(orig, [/\* Math\.pow\(2, typeMod\) \/ 8/, '* Math.pow(2, typeMod) / (["rocky", "cave"].includes(LR.field(this)) ? 4 : 8)'])],
		// The Wasteland swallows the hazards: they do nothing on entry (it spits them out at the end of the turn).
		...['spikes', 'stealthrock', 'stickyweb', 'toxicspikes'].map((id) => ['conditions', id, 'onEntryHazard', (orig) => function (...args) {
			if (LR.field(this) === 'wasteland') return;
			return orig.apply(this, args);
		}]),
		['abilities', 'aftermath', 'onDamagingHit', (orig) => rewrite(orig, [/source\.baseMaxhp \/ 4/, 'source.baseMaxhp / (LR.field(this) === "corrosivemist" ? 2 : 4)'])],
		['abilities', 'poisonpoint', 'onDamagingHit', (orig) => rewrite(orig, [/this\.randomChance\(3, 10\)/, 'this.randomChance(["wasteland", "corrupted"].includes(LR.field(this)) ? 6 : 3, 10)'])],
		['abilities', 'effectspore', 'onDamagingHit', (orig) => rewrite(orig, [/this\.random\(100\)/, 'this.random(["forest", "wasteland", "bewitched"].includes(LR.field(this)) ? 50 : 100)'])],
		// Rainbow, Starlight, Holy, the crystals.
		...['moonlight', 'morningsun', 'synthesis'].map((id) => ['moves', id, 'onHit', (orig) => rewrite(orig,
			[/this\.modify\(pokemon\.maxhp, factor\)/, `this.modify(pokemon.maxhp, LR.moonFactor(this, "${id}", factor))`])]),
		['abilities', 'wonderskin', 'onModifyAccuracy', (orig) => rewrite(orig, [/return 50;/, 'return LR.field(this) === "rainbow" ? 0 : 50;'])],
		['abilities', 'justified', 'onDamagingHit', (orig) => rewrite(orig, [/\{ atk: 1 \}/, '{ atk: LR.field(this) === "holy" ? 2 : 1 }'])],
		...['perishbody', 'cursedbody'].map((id) => ['abilities', id, 'onDamagingHit', (orig) => function (...args) {
			if (LR.field(this) === 'holy') return;
			return orig.apply(this, args);
		}]),
		['conditions', 'nightmare', 'onResidual', (orig) => function (...args) {
			if (LR.field(this) === 'rainbow') return;
			return orig.apply(this, args);
		}],
		['abilities', 'baddreams', 'onResidual', (orig) => function (...args) {
			if (LR.field(this) === 'rainbow') return;
			return orig.apply(this, args);
		}],
		// Forest, machines, gardens.
		...['steelbeam', 'chloroblast'].map((id) => ['moves', id, 'onAfterMove', (orig) => rewrite(orig, [/Math\.round\(pokemon\.maxhp \/ 2\)/, 'LR.beamRecoil(this, pokemon, move)'])]),
		['abilities', 'technician', 'onBasePower', (orig) => rewrite(orig, [/basePowerAfterMultiplier <= 60/, 'basePowerAfterMultiplier <= (LR.field(this) === "factory" ? 80 : 60)'])],
		['abilities', 'motordrive', 'onTryHit', (orig) => rewrite(orig, [/\{ spe: 1 \}/, '{ spe: LR.field(this) === "factory" ? 2 : 1 }'])],
		...['onModifyAtk', 'onModifySpA'].map((key) => ['abilities', 'steelworker', key, (orig) => function (atk, attacker, defender, move) {
			if (LR.field(this) === 'factory' && move.type === 'Steel') return this.chainModify(2);
			return orig.call(this, atk, attacker, defender, move);
		}]),
		...['plus', 'minus'].map((id) => ['abilities', id, 'onModifySpA', (orig) => function (spa, pokemon, ...rest) {
			if (LR.field(this) === 'shortcircuit') return this.chainModify(1.5);
			return orig.call(this, spa, pokemon, ...rest);
		}]),
		['abilities', 'static', 'onDamagingHit', (orig) => rewrite(orig, [/this\.randomChance\(3, 10\)/, 'this.randomChance(LR.field(this) === "shortcircuit" ? 6 : 3, 10)'])],
		['abilities', 'download', 'onStart', (orig) => rewrite(orig, [/\{ atk: 1 \}|\{ spa: 1 \}/g, (m) => m.replace('1', '(["factory", "city", "backalley"].includes(LR.field(this)) ? 2 : 1)')])],
		['moves', 'steelroller', 'onTry', (orig) => function (...args) {
			if (LR.field(this) !== null) return LR.canChange(this, NONE);
			return orig.apply(this, args);
		}],
		['abilities', 'grasspelt', 'onModifyDef', (orig) => function (def, ...rest) {
			if (LR.field(this) === 'forest') return this.chainModify(1.5);
			return orig.call(this, def, ...rest);
		}],
		// Ice, rocks and sand.
		['moves', 'auroraveil', 'onTry', (orig) => function (...args) {
			if (['darkcrystalcavern', 'rainbow', 'icy', 'crystalcavern', 'snowymountain', 'mirror', 'starlight', 'frozendimension'].includes(LR.field(this))) return;
			return orig.apply(this, args);
		}],
		['abilities', 'sandveil', 'onModifyAccuracy', (orig) => function (accuracy, ...rest) {
			if (typeof accuracy === 'number' && SAND_FIELDS.includes(LR.field(this)) && !this.field.isWeather('sandstorm')) return this.chainModify([3277, 4096]);
			return orig.call(this, accuracy, ...rest);
		}],
		['abilities', 'snowcloak', 'onModifyAccuracy', (orig) => function (accuracy, ...rest) {
			if (typeof accuracy === 'number' && ICE_FIELDS.includes(LR.field(this)) && !this.field.isWeather(HAIL)) return this.chainModify([3277, 4096]);
			return orig.call(this, accuracy, ...rest);
		}],
		['abilities', 'galewings', 'onModifyPriority', (orig) => function (priority, pokemon, target, move) {
			const f = LR.field(this);
			if (move && move.type === 'Flying' && pokemon.hp < pokemon.maxhp &&
				(f === 'sky' || (['mountain', 'snowymountain', 'volcanictop'].includes(f) && strongWinds(this)))) return priority + 1;
			return orig.call(this, priority, pokemon, target, move);
		}],
		['conditions', 'brn', 'onResidual', (orig) => rewrite(orig, [/pokemon\.baseMaxhp \/ 16/, 'pokemon.baseMaxhp / (LR.field(this) === "icy" ? 32 : 16)'])],
		['conditions', 'focusenergy', 'onModifyCritRatio', (orig) => rewrite(orig, [/critRatio \+ 2/, 'critRatio + (LR.field(this) === "ashenbeach" ? 3 : 2)'])],
		['moves', 'shoreup', 'onHit', (orig) => rewrite(rewrite(orig, [/let factor = 0\.5;/, 'let factor = LR.field(this) === "ashenbeach" ? 1 : LR.field(this) === "desert" ? 0.667 : 0.5;']),
			[/this\.field\.isWeather\("sandstorm"\)/, '(this.field.isWeather("sandstorm") && LR.field(this) !== "ashenbeach")'])],
		// Rocky: raised Defense keeps from flinching; otherwise the flinch knocks into a rock.
		['conditions', 'flinch', 'onBeforeMove', (orig) => function (pokemon, ...rest) {
			if (LR.field(this) !== 'rocky') return orig.call(this, pokemon, ...rest);
			if (pokemon.boosts.def >= 1) {
				LR.say(this, 'text', 'flinch_defended', pokemon.name);
				return;
			}
			const r = orig.call(this, pokemon, ...rest);
			if (!pokemon.hasAbility(['steadfast', 'sturdy', 'innerfocus'])) {
				LR.say(this, 'text', 'knocked_rock', pokemon.name);
				this.directDamage(Math.max(1, Math.floor(pokemon.maxhp / 4)), pokemon);
			}
			return r;
		}],
		// Dragon's Den: Shed Skin always works, heals 1/4 and trades defenses for Speed and Sp. Atk.
		['abilities', 'shedskin', 'onResidual', (orig) => function (pokemon) {
			if (LR.field(this) !== 'dragonsden' || !pokemon.hp || !pokemon.status) return orig.call(this, pokemon);
			this.add('-activate', pokemon, 'ability: Shed Skin');
			pokemon.cureStatus();
			LR.say(this, 'text', 'scaled_sheen', pokemon.name);
			this.heal(pokemon.baseMaxhp / 4, pokemon, pokemon);
			this.boost({spe: 1, spa: 1, def: -1, spd: -1}, pokemon, pokemon);
		}],
		// Gooey: -2 on Swamp and Murkwater (a rewrite, before the wrapper below).
		['abilities', 'gooey', 'onDamagingHit', (orig) => rewrite(orig, [/\{ spe: -1 \}/, '{ spe: ["swamp", "murkwatersurface"].includes(LR.field(this)) ? -2 : -1 }'])],
		// Gooey and Tangling Hair also poison on the Wasteland.
		...['gooey', 'tanglinghair'].map((id) => ['abilities', id, 'onDamagingHit', (orig) => function (damage, target, source, move) {
			const r = orig.call(this, damage, target, source, move);
			if (LR.field(this) === 'wasteland' && this.checkMoveMakesContact(move, source, target, true)) source.trySetStatus('psn', target);
			return r;
		}]),
		['conditions', 'partiallytrapped', 'onStart', (orig) => function (...args) {
			const r = orig.apply(this, args);
			const by = this.effectState.sourceEffect && this.effectState.sourceEffect.id;
			const f = LR.field(this);
			const steps = (BIND_STEPS[by] && BIND_STEPS[by][f]) || ((BIND_FIELDS[by] || []).includes(f) ? 1 : 0);
			const i = BINDING.indexOf(this.effectState.boundDivisor);
			if (steps && i >= 0) this.effectState.boundDivisor = BINDING[Math.min(BINDING.length - 1, i + steps)];
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
		// Eighth batch.
		...['kingsshield', 'obstruct'].map((id) => ['conditions', id, 'onTryHit', (orig) => rewrite(rewrite(orig,
			[/move\.category === "Status"/, `(move.category === "Status" && !LR.shieldsStatus(this, "${id}"))`]),
			[/\{ atk: -1 \}/, 'LR.kingsDrop(this)'])]),
		['moves', 'clangoroussoul', 'onTry', (orig) => rewrite(orig, [/33 \/ 100/g, '(LR.field(this) === "bigtop" ? 50 : 33) / 100'])],
		['moves', 'clangoroussoul', 'onHit', (orig) => rewrite(orig, [/33 \/ 100/g, '(LR.field(this) === "bigtop" ? 50 : 33) / 100'])],
		['moves', 'acrobatics', 'basePowerCallback', (orig) => rewrite(orig, [/!pokemon\.item/, '(!pokemon.item || LR.field(this) === "bigtop")'])],
		/** New World: Heart Swap also shares the HP out evenly (and fails through a substitute). */
		['moves', 'heartswap', 'onHit', (orig) => function (target, source, ...rest) {
			if (LR.field(this) !== 'newworld') return orig.call(this, target, source, ...rest);
			if (target.volatiles.substitute) return false;
			const r = orig.call(this, target, source, ...rest);
			const avg = Math.floor((source.hp + target.hp) / 2);
			for (const p of [source, target]) {
				p.sethp(Math.min(avg, p.maxhp));
				this.add('-sethp', p, p.getHealth, '[silent]');
			}
			LR.say(this, 'text', 'shared_pain');
			return r;
		}],
		/** Big Top: Encore lasts 3 more turns. */
		['conditions', 'encore', 'onStart', (orig) => function (target, ...rest) {
			const r = orig.call(this, target, ...rest);
			if (r !== false && LR.field(this) === 'bigtop' && target.volatiles.encore) target.volatiles.encore.duration += 3;
			return r;
		}],
		/** Healing Wish and Lunar Dance give the healed Pokémon boosts on the starry and fairy-tale fields. */
		...[['healingwish', {fairytale: {atk: 1}, starlight: {atk: 1}}],
			['lunardance', {starlight: {atk: 1, spa: 1}, newworld: {atk: 1, def: 1, spa: 1, spd: 1, spe: 1}}]].map(([id, boosts]) =>
			['conditions', id, 'onSwap', (orig) => function (target, ...rest) {
				const slot = () => target.side.slotConditions[target.position] || {};
				const had = !!slot()[id];
				const r = orig.call(this, target, ...rest);
				const b = boosts[LR.field(this)];
				if (had && !slot()[id] && b) this.boost(b, target, target);
				return r;
			}]),
		/** Gravity ending on a New World made Starlight: the world breaks apart again. */
		['conditions', 'gravity', 'onFieldEnd', (orig) => function (...args) {
			const r = orig.apply(this, args);
			const f = LR.field(this);
			if (f && f !== 'newworld' && LR.backup(this) === 'newworld') {
				LR.breakField(this);
				LR.say(this, 'text', 'world_broke');
			}
			return r;
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
				const before = sdField.weather;
				const r = setWeather(...args);
				if (r) {
					LR.longWeather(battle);
					LR.weatherSet(battle, before, sdField.weather, args[1]);
				}
				return r;
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
		/** Inverse: no type immunity (Showdown's inversemod). */
		onNegateImmunity(pokemon, type) {
			const f = LR.field(this);
			if (f === 'inverse' && type !== 'prankster') return false;
			// Holy: Normal moves reach Ghost types.
			if (f === 'holy' && type === 'Normal') return false;
			// Glitch: Dragon moves reach Fairy types.
			if (f === 'glitch' && type === 'Dragon') return false;
		},
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
		/** Chess Board: the King moves first. */
		onModifyPriority(priority, pokemon, target, move) {
			let add = 0;
			for (const h of LR.hooks(this, 'priority')) add += h.call(this, priority, pokemon, target, move) || 0;
			if (add) return priority + add;
		},
		onModifyCritRatio(critRatio, source, target, move) {
			let add = 0;
			for (const h of LR.hooks(this, 'critRatio')) add += h.call(this, critRatio, source, target, move) || 0;
			if (add) return critRatio + add;
		},
		/** Damage a field changes as it is dealt (the Pawn holding on). */
		onDamage(damage, target, source, effect) {
			return LR.veto(this, 'damage', [damage, target, source, effect]);
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
