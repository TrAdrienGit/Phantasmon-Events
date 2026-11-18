#!/usr/bin/env node
// Converts Pokémon Reborn's field data (Scripts/Reborn/fieldtext.rb, a Ruby hash literal) into JSON for League Reborn.
// Usage: node convert.js <path to fieldtext.rb> <output dir>
// Ruby code kept as strings (conditions, effects) is listed in `rubySnippets` of the output: each one has to be
// translated by hand in the Showdown code. Texts are kept only as a reference for writing our own.
'use strict';
const fs = require('fs');

const [, , src, out] = process.argv;
if (!src || !out) {
	console.error('usage: node convert.js <fieldtext.rb> <output dir>');
	process.exit(1);
}
const ruby = fs.readFileSync(src, 'utf8');

// Tokenize Ruby literal: strings, comments, symbols, numbers, PBStats constants, punctuation, nil/true/false.
let i = 0;
const tokens = [];
while (i < ruby.length) {
	const c = ruby[i];
	if (/\s/.test(c)) { i++; continue; }
	if (c === '#') { while (i < ruby.length && ruby[i] !== '\n') i++; continue; }
	if (c === '"' || c === "'") {
		let s = '';
		i++;
		while (ruby[i] !== c) {
			if (ruby[i] === '\\') { s += ruby[i + 1] === 'n' ? '\n' : ruby[i + 1]; i += 2; continue; }
			s += ruby[i++];
		}
		i++;
		tokens.push({t: 'str', v: s});
		continue;
	}
	if (ruby.startsWith('=>', i)) { tokens.push({t: '=>'}); i += 2; continue; }
	if ('{}[],'.includes(c)) { tokens.push({t: c}); i++; continue; }
	let m;
	const rest = ruby.slice(i, i + 200);
	if ((m = /^:([A-Za-z_][A-Za-z0-9_]*[?!]?)/.exec(rest))) { tokens.push({t: 'sym', v: m[1]}); i += m[0].length; continue; }
	if ((m = /^PBStats::([A-Z]+)/.exec(rest))) { tokens.push({t: 'sym', v: m[1]}); i += m[0].length; continue; }
	if ((m = /^-?\d+(\.\d+)?/.exec(rest))) { tokens.push({t: 'num', v: Number(m[0])}); i += m[0].length; continue; }
	if ((m = /^(nil|true|false)\b/.exec(rest))) {
		tokens.push({t: 'lit', v: m[1] === 'nil' ? null : m[1] === 'true'});
		i += m[0].length;
		continue;
	}
	if ((m = /^FIELDEFFECTS\s*=/.exec(rest))) { i += m[0].length; continue; }
	throw new Error(`unexpected input at ${i}: ${JSON.stringify(rest.slice(0, 40))}`);
}

let p = 0;
const expect = (t) => {
	if (tokens[p].t !== t) throw new Error(`expected ${t} at token ${p}, got ${JSON.stringify(tokens[p])}`);
	p++;
};
function value() {
	const tk = tokens[p];
	if (tk.t === '{') return hash();
	if (tk.t === '[') return array();
	p++;
	return tk.v;
}
function key() {
	// Array keys exist: fieldCounterIncreases is { [counter, increase] => [moves] }. Kept as "counter,increase".
	if (tokens[p].t === '[') return array().join(',');
	const tk = tokens[p++];
	return String(tk.v);
}
function hash() {
	expect('{');
	const obj = {};
	while (tokens[p].t !== '}') {
		const k = key();
		expect('=>');
		obj[k] = value();
		if (tokens[p].t === ',') p++;
	}
	expect('}');
	return obj;
}
function array() {
	expect('[');
	const arr = [];
	while (tokens[p].t !== ']') {
		arr.push(value());
		if (tokens[p].t === ',') p++;
	}
	expect(']');
	return arr;
}

const fields = hash();
if (p !== tokens.length) throw new Error(`trailing tokens from ${p}`);

// The Ruby code kept as strings, translated by hand into names the Showdown code knows (engine.js: CONDITIONS,
// EFFECTS). A snippet missing here stops the conversion: translate it first.
const CODE = {
	'!attacker.isAirborne?': 'attackerGrounded',
	'!opponent.isAirborne?': 'targetGrounded',
	'ignitecheck': 'ignite',
	'@battle.field.counter > 1': 'counterAbove1',
	'suncheck': 'never', // PokeBattle_Move#suncheck returns false: Reborn never applies that change
	'self.pbIsSpecial?(type)': 'special',
	'[:WATERSURFACE,:MURKWATERSURFACE].include?(@battle.field.backup)': 'backupIsWater',
	'self.move!=:SCALD && self.move!=:STEAMERUPTION': 'notScaldOrSteamEruption',
	'!self.contactMove?': 'nonContact',
	'@battle.fieldAccuracyDrop': 'accuracyDrop',
	'@battle.iceSpikes': 'iceSpikes',
	'@battle.waterPollution': 'waterPollution',
	'@battle.mistExplosion(basemove, user)': 'mistExplosion',
	'@battle.caveCollapse(basemove, user)': 'caveCollapse',
	'@battle.mirrorShatter(basemove, user)': 'mirrorShatter',
};
const code = (s, where) => {
	if (!(s in CODE)) throw new Error(`untranslated Ruby code in ${where}: ${s}`);
	return CODE[s];
};
const id = (sym) => sym.toLowerCase(); // Reborn :THUNDERBOLT -> Showdown thunderbolt
const typeName = (sym) => sym[0] + sym.slice(1).toLowerCase(); // :ELECTRIC -> Electric
const STATS = {ATTACK: 'atk', DEFENSE: 'def', SPATK: 'spa', SPDEF: 'spd', SPEED: 'spe', ACCURACY: 'accuracy', EVASION: 'evasion'};

// Inverts Reborn's { value => [keys] } into { key => value } (PBStuff#pbHashForwardizer).
const forward = (h) => {
	const r = {};
	for (const [v, keys] of Object.entries(h || {})) for (const k of keys) r[k] = v;
	return r;
};

// The same fusion as Reborn's compiler ("DataObjects - Compilers.rb"): per move and per type. Messages become their
// index (1-based) in the field's list, as in Reborn: the texts themselves are ours, in the mod's lang files.
const runtime = {};
const reference = {};
for (const [sym, f] of Object.entries(fields)) {
	const fid = id(sym);
	const msgIndex = (h) => {
		const r = {};
		Object.values(h || {}).forEach((keys, i) => keys.forEach((k) => (r[k] = i + 1)));
		return r;
	};
	const moveMsg = msgIndex(f.moveMessages);
	const typeMsg = msgIndex(f.typeMessages);
	const changeMsg = msgIndex(f.changeMessage);
	const dmg = forward(f.damageMods);
	const typemod = forward(f.typeMods);
	const acc = forward(f.accuracyMods);
	const counters = forward(f.fieldCounterIncreases);
	const effects = forward(f.moveEffects);
	const change = forward(f.fieldChange);
	const changeFx = forward(f.changeEffects);
	const keep = f.dontChangeBackup || [];

	const moves = {};
	const moveKeys = new Set([dmg, typemod, acc, counters, effects, change].flatMap(Object.keys));
	for (const m of moveKeys) {
		const d = {};
		if (dmg[m] !== undefined) d.mult = Number(dmg[m]);
		if (typemod[m]) d.type = typeName(typemod[m]);
		if (acc[m] !== undefined) d.acc = Number(acc[m]);
		if (moveMsg[m]) d.msg = moveMsg[m];
		if (counters[m]) d.counter = counters[m].split(',').map(Number);
		if (effects[m]) d.effect = code(effects[m], `${sym}.moveEffects`);
		if (change[m]) d.change = id(change[m]);
		if (changeMsg[m]) d.changeMsg = changeMsg[m];
		if (changeFx[m]) d.changeEffect = code(changeFx[m], `${sym}.changeEffects`);
		if (keep.includes(m)) d.keepBackup = true;
		moves[id(m)] = d;
	}
	const types = {};
	const boost = forward(f.typeBoosts);
	const addOn = forward(f.typeAddOns);
	const typeKeys = new Set([boost, addOn, f.typeEffects || {}].flatMap(Object.keys));
	for (const t of typeKeys) {
		const d = {};
		if (boost[t] !== undefined) d.mult = Number(boost[t]);
		if (addOn[t]) d.type = typeName(addOn[t]);
		if (f.typeEffects?.[t]) d.effect = code(f.typeEffects[t], `${sym}.typeEffects`);
		if (typeMsg[t]) d.msg = typeMsg[t];
		if (f.typeCondition?.[t]) d.cond = code(f.typeCondition[t], `${sym}.typeCondition`);
		types[typeName(t)] = d;
	}
	const changeCond = {};
	for (const [to, c] of Object.entries(f.changeCondition || {})) changeCond[id(to)] = code(c, `${sym}.changeCondition`);

	const seed = f.seed?.seedtype ? {
		item: id(f.seed.seedtype),
		effect: f.seed.effect || undefined,
		duration: f.seed.duration ?? undefined,
		animation: f.seed.animation ? id(f.seed.animation) : undefined,
		stats: Object.fromEntries(Object.entries(f.seed.stats || {}).map(([s, n]) => [STATS[s], n])),
	} : undefined;

	runtime[fid] = {
		naturePower: f.naturePower ? id(f.naturePower) : 'triattack',
		secretPower: f.secretPower ? id(f.secretPower) : 'triattack',
		mimicry: f.mimicry ? typeName(f.mimicry) : 'Normal',
		statusMods: (f.statusMods || []).map(id),
		moves, types, changeCond, seed,
	};
	// Not shipped: Reborn's own texts, to write ours from.
	reference[fid] = {
		name: f.name,
		intro: f.fieldMessage,
		moveMessages: Object.keys(f.moveMessages || {}),
		typeMessages: Object.keys(f.typeMessages || {}),
		changeMessages: Object.keys(f.changeMessage || {}),
		seedMessage: f.seed?.message,
	};
}

fs.mkdirSync(out, {recursive: true});
const write = (name, obj) => fs.writeFileSync(`${out}/${name}`, JSON.stringify(obj, null, '\t') + '\n');
write('fields.json', runtime);
write('reference-texts.json', reference);
console.log(`${Object.keys(runtime).length} fields -> ${out}/fields.json (+ reference-texts.json, not shipped)`);
