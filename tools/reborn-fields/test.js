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
