#!/usr/bin/env node
// League Reborn engine tests, in Cobblemon's own Showdown engine (the "showdown" folder Cobblemon extracts in an
// instance, or data/cobblemon/showdown.zip from its jar, unzipped).
// Usage: node test.js <showdown folder>
'use strict';
const path = require('path');
const fs = require('fs');
const assert = require('assert');

const SD = process.argv[2];
if (!SD) {
	console.error('usage: node test.js <showdown folder>');
	process.exit(1);
}
const {Dex} = require(path.join(SD, 'sim/dex'));
const {Battle} = require(path.join(SD, 'sim/battle'));
const RES = path.join(__dirname, '../../src/main/resources/assets/phantasmon_events/league_reborn');
const install = eval(fs.readFileSync(path.join(RES, 'engine.js'), 'utf8'));
const {LR} = install(Dex, JSON.parse(fs.readFileSync(path.join(RES, 'fields.json'), 'utf8')));

// --- helpers ---------------------------------------------------------------------------------------------------

function mon(species, moves, extra = {}) {
	return {
		name: species, species, moves, movesInfo: moves.map(() => ({pp: 32, maxPp: 32})),
		ability: extra.ability || '', item: extra.item || '', level: extra.level || 50, nature: 'Hardy',
		gender: '', evs: {hp: 0, atk: 0, def: 0, spa: 0, spd: 0, spe: 0}, ivs: {hp: 31, atk: 31, def: 31, spa: 31, spd: 31, spe: 31},
	};
}
function battle(field, p1, p2) {
	const formatid = field ? `gen9customgame@@@League Reborn = ${field}` : 'gen9customgame';
	const b = new Battle({formatid, seed: [1, 2, 3, 4]});
	b.setPlayer('p1', {team: p1});
	b.setPlayer('p2', {team: p2});
	b.makeChoices('default', 'default');
	return b;
}
function turn(b, c1 = 'move 1', c2 = 'move 1') {
	const from = b.log.length;
	b.makeChoices(c1, c2);
	return b.log.slice(from);
}
const lost = (b, side) => b.sides[side].active[0].maxhp - b.sides[side].active[0].hp;
const lines = (log, re) => log.filter((l) => re.test(l));

const tests = [];
const test = (name, fn) => tests.push([name, fn]);

// --- tests -----------------------------------------------------------------------------------------------------

test('no rule: nothing changes, no field state', () => {
	const b = battle(null, [mon('Pikachu', ['thunderbolt'])], [mon('Snorlax', ['splash'])]);
	assert.strictEqual(LR.field(b), null);
});

test('the rule lays the field down and announces it', () => {
	const b = battle('electerrain', [mon('Pikachu', ['thunderbolt'])], [mon('Snorlax', ['splash'])]);
	assert.strictEqual(LR.field(b), 'electerrain');
	assert.ok(b.log.includes('|-leaguereborn|start|electerrain'));
});

test('Electric Terrain: grounded Electric moves x1.5 on the damage', () => {
	const vanilla = battle(null, [mon('Pikachu', ['thunderbolt'])], [mon('Snorlax', ['splash'])]);
	turn(vanilla);
	const field = battle('electerrain', [mon('Pikachu', ['thunderbolt'])], [mon('Snorlax', ['splash'])]);
	const log = turn(field);
	const ratio = lost(field, 1) / lost(vanilla, 1);
	assert.ok(ratio > 1.4 && ratio < 1.6, `ratio ${ratio}`);
	assert.ok(lines(log, /-leaguereborn\|type\|electerrain\|1/).length === 1);
});

test('Electric Terrain: an airborne attacker gets no type boost', () => {
	const vanilla = battle(null, [mon('Zapdos', ['thunderbolt'])], [mon('Snorlax', ['splash'])]);
	turn(vanilla);
	const field = battle('electerrain', [mon('Zapdos', ['thunderbolt'])], [mon('Snorlax', ['splash'])]);
	turn(field);
	assert.strictEqual(lost(field, 1), lost(vanilla, 1));
});

test('Electric Terrain: Surf gains Electric, x1.5, and cannot touch a Ground type', () => {
	const b = battle('electerrain', [mon('Lapras', ['surf'])], [mon('Quagsire', ['splash'])]);
	const log = turn(b);
	assert.strictEqual(lost(b, 1), 0);
	assert.ok(lines(log, /\|-immune\|/).length > 0);
});

test('Electric Terrain: Mud Sport shorts the field out', () => {
	const b = battle('electerrain', [mon('Quagsire', ['mudsport'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.strictEqual(LR.field(b), 'indoor');
	assert.ok(lines(log, /-leaguereborn\|change\|electerrain\|indoor\|1/).length === 1);
});

test('Nature Power calls the field move', () => {
	const b = battle('electerrain', [mon('Smeargle', ['naturepower'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.ok(lines(log, /\|move\|p1a: .*\|Thunderbolt\|/).length === 1, log.join('\n'));
});

test('Electric Terrain the move: a temporary field for 5 turns, then back', () => {
	const b = battle('burning', [mon('Pikachu', ['electricterrain', 'splash'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(LR.field(b), 'electerrain');
	for (let i = 0; i < 4; i++) turn(b, 'move 2');
	assert.strictEqual(LR.field(b), 'burning');
});

test('Electric Surge: the ability sets the temporary field', () => {
	const b = battle('grassy', [mon('Tapu Koko', ['splash'], {ability: 'electricsurge'})], [mon('Snorlax', ['splash'])]);
	assert.strictEqual(LR.field(b), 'electerrain');
});

test('Burning: a Water move puts the fire out, with the x1.3 change boost', () => {
	const vanilla = battle(null, [mon('Lapras', ['surf'])], [mon('Snorlax', ['splash'])]);
	turn(vanilla);
	const b = battle('burning', [mon('Lapras', ['surf'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(LR.field(b), 'indoor');
	const ratio = lost(b, 1) / lost(vanilla, 1);
	// Burning: Water... see fields.json burning.types; the change boost alone is x1.3
	console.log(`    surf on burning: x${ratio.toFixed(3)} (Water on Burning ${JSON.stringify(LR.data(battle('burning', [mon('Lapras', ['surf'])], [mon('Snorlax', ['splash'])])).types.Water)})`);
});

test('Cave: the second quake brings the ceiling down', () => {
	const b = battle('cave', [mon('Garchomp', ['earthquake'])], [mon('Bronzong', ['splash'], {ability: 'levitate'})]);
	turn(b);
	assert.ok(b.log.some((l) => l.includes('|-leaguereborn|text|cave_crumbling')));
	turn(b);
	assert.ok(b.log.some((l) => l.includes('|-leaguereborn|text|cave_collapsed')));
});

// --- the four terrain fields' own code (HOOKS) -----------------------------------------------------------------

const active = (b, side) => b.sides[side].active[0];

test('Electric Terrain: a grounded Pokémon cannot be put to sleep, a flying one can', () => {
	const b = battle('electerrain', [mon('Breloom', ['spore'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.strictEqual(active(b, 1).status, '');
	assert.ok(lines(log, /-leaguereborn\|text\|electric_awake/).length === 1, log.join('\n'));
	const air = battle('electerrain', [mon('Breloom', ['spore'])], [mon('Pidgeot', ['splash'])]);
	turn(air);
	assert.strictEqual(active(air, 1).status, 'slp');
});

test('Electric Terrain: Focus Punch never lands', () => {
	const b = battle('electerrain', [mon('Machamp', ['focuspunch'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.strictEqual(lost(b, 1), 0);
	assert.ok(lines(log, /-leaguereborn\|text\|focus_lost/).length === 1);
});

test('Electric Terrain: Comatose is awake (Sleep Talk fails), not elsewhere', () => {
	const b = battle('electerrain', [mon('Komala', ['sleeptalk', 'tackle'], {ability: 'comatose'})], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(lost(b, 1), 0);
	const v = battle('grassy', [mon('Komala', ['sleeptalk', 'tackle'], {ability: 'comatose'})], [mon('Snorlax', ['splash'])]);
	turn(v);
	assert.ok(lost(v, 1) > 0);
});

test('Electric Terrain: Motor Drive speeds up each turn, Eerie Impulse -3, Charge +2', () => {
	const b = battle('electerrain', [mon('Electivire', ['charge'], {ability: 'motordrive'})], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(active(b, 0).boosts.spe, 1);
	assert.strictEqual(active(b, 0).boosts.spd, 2);
	const e = battle('electerrain', [mon('Pikachu', ['eerieimpulse'])], [mon('Snorlax', ['splash'])]);
	turn(e);
	assert.strictEqual(active(e, 1).boosts.spa, -3);
});

test('Electric Terrain: Wild Charge has no recoil', () => {
	const b = battle('electerrain', [mon('Raichu', ['wildcharge'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(lost(b, 0), 0);
});

test('Ion Deluge lays Electric Terrain for 3 turns', () => {
	const b = battle('burning', [mon('Heliolisk', ['iondeluge', 'splash'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(LR.field(b), 'electerrain');
	turn(b, 'move 2');
	turn(b, 'move 2');
	assert.strictEqual(LR.field(b), 'burning');
});

test('Electric Terrain: Thunder Cage binds harder (1/6)', () => {
	const b = battle('electerrain', [mon('Regieleki', ['thundercage'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(active(b, 1).volatiles.partiallytrapped.boundDivisor, 6);
});

test('Grassy Terrain: grounded Pokémon heal 1/16 at the end of the turn', () => {
	const b = battle('grassy', [mon('Rattata', ['tackle'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.ok(lines(log, /\|-heal\|p2a: .*Grassy Terrain/).length > 0, log.join('\n'));
	assert.ok(lines(log, /\|-heal\|p1a: /).length === 0); // full HP: nothing to heal
});

test('Grassy Terrain: Coil +2 everywhere, Cotton Down -2 Speed', () => {
	const b = battle('grassy', [mon('Arbok', ['coil', 'tackle'])], [mon('Eldegoss', ['splash'], {ability: 'cottondown'})]);
	turn(b);
	assert.deepStrictEqual([active(b, 0).boosts.atk, active(b, 0).boosts.def, active(b, 0).boosts.accuracy], [2, 2, 2]);
	turn(b, 'move 2');
	assert.strictEqual(active(b, 0).boosts.spe, -2);
});

test('Mimicry takes the field type', () => {
	const b = battle('grassy', [mon('Stunfisk-Galar', ['splash'], {ability: 'mimicry'})], [mon('Snorlax', ['splash'])]);
	assert.deepStrictEqual(active(b, 0).getTypes(), ['Grass']);
});

test('Misty Terrain: no status nor confusion on the ground', () => {
	const b = battle('misty', [mon('Jolteon', ['thunderwave', 'confuseray'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.strictEqual(active(b, 1).status, '');
	turn(b, 'move 2');
	assert.ok(!active(b, 1).volatiles.confusion);
	assert.ok(lines(log, /-leaguereborn\|text\|misty_status/).length === 1);
});

test('Misty Terrain: Wish heals 75%', () => {
	const b = battle('misty', [mon('Togekiss', ['wish'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	const wish = b.sides[0].slotConditions[0].wish;
	assert.strictEqual(wish.hp, Math.floor((active(b, 0).maxhp + 1) * 0.75));
});

test('Psychic Terrain: priority cannot touch a grounded target', () => {
	const b = battle('psyterrain', [mon('Rattata', ['quickattack'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(lost(b, 1), 0);
	const air = battle('psyterrain', [mon('Rattata', ['quickattack'])], [mon('Pidgeot', ['splash'])]);
	turn(air);
	assert.ok(lost(air, 1) > 0);
});

test('Psychic Terrain: Nasty Plot +3, Trick Room 8 turns', () => {
	const b = battle('psyterrain', [mon('Alakazam', ['nastyplot'])], [mon('Slowbro', ['trickroom'])]);
	turn(b);
	assert.strictEqual(active(b, 0).boosts.spa, 3);
	assert.strictEqual(b.field.pseudoWeather.trickroom.duration, 7); // 8, one turn already gone
});

test('Psychic Terrain: Pure Power doubles the special side instead', () => {
	const b = battle('psyterrain', [mon('Medicham', ['splash'], {ability: 'purepower'})], [mon('Snorlax', ['splash'])]);
	const m = active(b, 0);
	assert.strictEqual(b.runEvent('ModifySpA', m, null, null, m.storedStats.spa), m.storedStats.spa * 2);
	assert.strictEqual(b.runEvent('ModifyAtk', m, null, null, m.storedStats.atk), m.storedStats.atk);
});

test('without the rule, the patched Showdown code is Showdown\'s', () => {
	const komala = battle(null, [mon('Komala', ['sleeptalk', 'tackle'], {ability: 'comatose'})], [mon('Snorlax', ['splash'])]);
	turn(komala);
	assert.ok(lost(komala, 1) > 0);
	const b = battle(null, [mon('Medicham', ['splash'], {ability: 'purepower'})], [mon('Snorlax', ['splash'])]);
	const m = active(b, 0);
	assert.strictEqual(b.runEvent('ModifyAtk', m, null, null, m.storedStats.atk), m.storedStats.atk * 2);
	const fish = battle(null, [mon('Stunfisk-Galar', ['splash'], {ability: 'mimicry'})], [mon('Snorlax', ['splash'])]);
	assert.deepStrictEqual(active(fish, 0).getTypes(), ['Ground', 'Steel']);
});

// --- run -------------------------------------------------------------------------------------------------------

let failed = 0;
for (const [name, fn] of tests) {
	try {
		fn();
		console.log(`ok   ${name}`);
	} catch (e) {
		failed++;
		console.log(`FAIL ${name}\n     ${(e.stack || e).toString().split('\n').slice(0, 4).join('\n     ')}`);
	}
}
console.log(failed ? `${failed} failed` : 'all passed');
process.exit(failed ? 1 : 0);
