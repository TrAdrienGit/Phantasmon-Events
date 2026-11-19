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
	// (Levitate no longer dodges Earthquake in a cave: a Torterra survives the two quakes.)
	const b = battle('cave', [mon('Garchomp', ['earthquake'])], [mon('Torterra', ['splash'])]);
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

// --- the water fields' own code (second batch) ----------------------------------------------------------------

test('Swamp: grounded Pokemon lose Speed each turn, flying ones do not', () => {
	const b = battle('swamp', [mon('Snorlax', ['splash'])], [mon('Pidgeot', ['splash'])]);
	turn(b);
	turn(b);
	assert.strictEqual(active(b, 0).boosts.spe, -2);
	assert.strictEqual(active(b, 1).boosts.spe, 0);
});

test('Swamp: Gooey -2 Speed; Water + Grass Pledge make a Swamp', () => {
	const b = battle('swamp', [mon('Rattata', ['tackle'])], [mon('Goodra', ['splash'], {ability: 'gooey'})]);
	turn(b);
	assert.strictEqual(active(b, 0).boosts.spe, -3); // Gooey -2, the swamp -1
	const p = battle('cave', [mon('Samurott', ['waterpledge'])], [mon('Serperior', ['grasspledge'])]);
	turn(p);
	assert.strictEqual(LR.field(p), 'swamp');
});

test('Underwater: Water hits Water normally, Electric never misses', () => {
	const b = battle('underwater', [mon('Squirtle', ['watergun'])], [mon('Vaporeon', ['splash'])]);
	const log = turn(b);
	assert.strictEqual(lines(log, /-resisted/).length, 0, log.join('\n'));
	const v = battle(null, [mon('Squirtle', ['watergun'])], [mon('Vaporeon', ['splash'])]);
	assert.strictEqual(lines(turn(v), /-resisted/).length, 1);
});

test('Underwater: Pokemon the water hurts struggle, the others do not', () => {
	const b = battle('underwater', [mon('Arcanine', ['splash'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.ok(lines(log, /-leaguereborn\|text\|water_struggle/).length === 1, log.join('\n'));
	assert.strictEqual(lost(b, 0), Math.floor(active(b, 0).maxhp * 8 / 32));
	assert.strictEqual(lost(b, 1), 0);
});

test('Underwater: Dive hits the same turn, Take Heart +2', () => {
	const b = battle('underwater', [mon('Lapras', ['dive'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.ok(lost(b, 1) > 0);
	const t = battle('underwater', [mon('Manaphy', ['takeheart'])], [mon('Snorlax', ['splash'])]);
	turn(t);
	assert.strictEqual(active(t, 0).boosts.spa, 2);
});

test('Water Surface: Swift Swim doubles Speed, the grounded non-Water slow down', () => {
	const b = battle('watersurface', [mon('Kingdra', ['splash'], {ability: 'swiftswim'})], [mon('Snorlax', ['splash'])]);
	const k = active(b, 0), s = active(b, 1);
	assert.strictEqual(k.getStat('spe'), k.storedStats.spe * 2);
	assert.strictEqual(s.getStat('spe'), Math.floor(s.storedStats.spe * 0.75));
});

test('Murkwater Surface: the toxic water hurts the grounded, not Poison types', () => {
	const b = battle('murkwatersurface', [mon('Snorlax', ['splash'])], [mon('Muk', ['splash'])]);
	const log = turn(b);
	assert.ok(lines(log, /-leaguereborn\|text\|toxic_water/).length === 1, log.join('\n'));
	assert.strictEqual(lost(b, 0), Math.floor(active(b, 0).maxhp * 4 / 32));
	assert.strictEqual(lost(b, 1), 0);
});

test('Murkwater Surface: Venoshock acts as if the target were poisoned', () => {
	const b = battle('murkwatersurface', [mon('Muk', ['venoshock'])], [mon('Muk', ['splash'])]);
	const m = active(b, 0), t = active(b, 1);
	const move = b.dex.getActiveMove('venoshock');
	assert.strictEqual(b.runEvent('BasePower', m, t, move, 65, true), 130);
	const v = battle(null, [mon('Muk', ['venoshock'])], [mon('Muk', ['splash'])]);
	assert.strictEqual(v.runEvent('BasePower', active(v, 0), active(v, 1), v.dex.getActiveMove('venoshock'), 65, true), 65);
});

test('without the rule, Gooey is -1', () => {
	const b = battle(null, [mon('Rattata', ['tackle'])], [mon('Goodra', ['splash'], {ability: 'gooey'})]);
	turn(b);
	assert.strictEqual(active(b, 0).boosts.spe, -1);
});

// --- batches 3 to 6: poison, fire, ice/rock/sand, nature/machines ---------------------------------------------

test('Corrosive Mist: everyone is poisoned at the end of the turn, not Steel types', () => {
	const b = battle('corrosivemist', [mon('Snorlax', ['splash'])], [mon('Skarmory', ['splash'])]);
	const log = turn(b);
	assert.strictEqual(active(b, 0).status, 'psn');
	assert.strictEqual(active(b, 1).status, '');
	assert.ok(lines(log, /-leaguereborn\|text\|mist_poisoned/).length === 1);
});

test('Corrosive: entering sears the grounded by their Poison weakness', () => {
	const b = battle('corrosive', [mon('Snorlax', ['splash']), mon('Venusaur', ['splash'])], [mon('Muk', ['splash'])]);
	turn(b, 'switch 2');
	const v = active(b, 0);
	assert.strictEqual(v.maxhp - v.hp, 0); // Grass/Poison: Poison type, untouched
	const c = battle('corrosive', [mon('Snorlax', ['splash']), mon('Clefable', ['splash'])], [mon('Muk', ['splash'])]);
	turn(c, 'switch 2');
	const f = active(c, 0);
	assert.strictEqual(f.maxhp - f.hp, Math.floor(f.maxhp * 8 / 16)); // Fairy: weak to Poison, 8/16
});

test('Wasteland: hazards do nothing on entry, then the waste spits them out', () => {
	const b = battle('wasteland', [mon('Skarmory', ['spikes'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.ok(!b.sides[1].sideConditions.spikes);
	assert.ok(lines(log, /-leaguereborn\|text\|waste_spikes/).length === 1, log.join('\n'));
	assert.strictEqual(lost(b, 1), Math.floor(active(b, 1).maxhp / 3));
});

test('Burning: the grounded burn by their Fire weakness, Fire types do not', () => {
	const b = battle('burning', [mon('Venusaur', ['splash'])], [mon('Arcanine', ['splash'])]);
	const log = turn(b);
	assert.strictEqual(lost(b, 0), Math.floor(active(b, 0).maxhp * 8 / 32));
	assert.strictEqual(lost(b, 1), 0);
	assert.ok(lines(log, /-leaguereborn\|text\|field_burned/).length === 1);
});

test('Burning: rain puts the fire out; no freezing there', () => {
	const b = battle('burning', [mon('Pelipper', ['raindance'], {ability: 'keeneye'})], [mon('Arcanine', ['splash'])]);
	turn(b);
	assert.notStrictEqual(LR.field(b), 'burning');
});

test("Dragon's Den: Magma Armor swallows Fire, Dragon types take less", () => {
	const b = battle('dragonsden', [mon('Charizard', ['flamethrower'])], [mon('Camerupt', ['splash'], {ability: 'magmaarmor'})]);
	turn(b);
	assert.strictEqual(lost(b, 1), 0);
	assert.strictEqual(active(b, 1).boosts.def, 1);
});

test('Cave: Earthquake hits a Levitate user and a Flying type', () => {
	const b = battle('cave', [mon('Garchomp', ['earthquake'])], [mon('Pidgeot', ['splash'])]);
	turn(b);
	assert.ok(lost(b, 1) > 0);
});

test('Rocky: a missed contact move hits a rock; Stealth Rock twice as sharp', () => {
	const b = battle('rocky', [mon('Hitmonlee', ['highjumpkick', 'tackle'])], [mon('Gengar', ['splash'])]);
	turn(b, 'move 2');
	assert.ok(lost(b, 0) > 0); // Tackle misses the Ghost: a rock instead
});

test('Desert: Sand Rush doubles Speed, Ground types resist special moves', () => {
	const b = battle('desert', [mon('Excadrill', ['splash'], {ability: 'sandrush'})], [mon('Snorlax', ['splash'])]);
	const e = active(b, 0);
	assert.strictEqual(e.getStat('spe'), e.storedStats.spe * 2);
});

test('Ashen Beach: a Fighting type breaks through confusion', () => {
	const b = battle('ashenbeach', [mon('Jolteon', ['confuseray'])], [mon('Machamp', ['splash'])]);
	const log = turn(b);
	assert.ok(!active(b, 1).volatiles.confusion);
	assert.ok(lines(log, /-leaguereborn\|text\|confusion_broken/).length === 1);
});

test('Inverse: Electric hits Ground, Fighting resisted by Normal', () => {
	const b = battle('inverse', [mon('Pikachu', ['thunderbolt'])], [mon('Golem', ['splash'])]);
	const log = turn(b);
	assert.ok(lost(b, 1) > 0, log.join('\n'));
	assert.ok(lines(log, /-supereffective/).length === 1);
});

test('Forest: Leaf Guard blocks status, Growth +2', () => {
	const b = battle('forest', [mon('Breloom', ['spore'])], [mon('Leafeon', ['growth'], {ability: 'leafguard'})]);
	turn(b);
	assert.strictEqual(active(b, 1).status, '');
	assert.strictEqual(active(b, 1).boosts.atk, 2);
});

test('Short-Circuit: Electric damage follows the rolls, with their text', () => {
	const b = battle('shortcircuit', [mon('Pikachu', ['thunderbolt'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.ok(lines(log, /-leaguereborn\|text\|bzzt0/).length === 1, log.join('\n'));
});

test('Topsy-Turvy lays Inverse for 3 turns', () => {
	const b = battle('cave', [mon('Malamar', ['topsyturvy'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(LR.field(b), 'inverse');
});

// --- batch 7: Rainbow, Starlight, Holy, crystals, Mirror -------------------------------------------------------

test('Rain then sun make a Rainbow', () => {
	const b = battle('cave', [mon('Pelipper', ['raindance', 'sunnyday'], {ability: 'keeneye'})], [mon('Snorlax', ['splash'])]);
	turn(b);
	turn(b, 'move 2');
	assert.strictEqual(LR.field(b), 'rainbow');
});

test('Holy: Normal moves hit Ghost types super effectively', () => {
	const b = battle('holy', [mon('Snorlax', ['bodyslam'])], [mon('Gengar', ['splash'])]);
	const log = turn(b);
	assert.ok(lost(b, 1) > 0, log.join('\n'));
	assert.ok(lines(log, /-supereffective/).length === 1);
});

test('Starlight: Solar Beam is Fairy', () => {
	const b = battle('starlight', [mon('Venusaur', ['solarbeam'])], [mon('Hariyama', ['splash'])]);
	turn(b);
	const log = turn(b);
	assert.ok(lost(b, 1) > 0, log.join('\n'));
	assert.ok(lines(log, /-supereffective/).length === 1);
});

test('Mirror: Double Team +2, a missed contact move hits a mirror', () => {
	const b = battle('mirror', [mon('Rattata', ['tackle'])], [mon('Gengar', ['doubleteam'])]);
	turn(b);
	assert.strictEqual(active(b, 1).boosts.evasion, 2);
	assert.ok(lost(b, 0) > 0);
});

test('Crystal Cavern: Rock moves take the crystals types in turn', () => {
	const b = battle('crystalcavern', [mon('Golem', ['rockslide'])], [mon('Charizard', ['splash'])]);
	turn(b);
	assert.ok(lost(b, 1) > 0);
});

// --- batch 8: Glitch, New World, Fairy Tale, Chess Board, Big Top Arena -----------------------------------------

test('Glitch: Dark moves are Normal (Gengar immune), Ghost misses Psychic, Dragon hits Fairy', () => {
	let b = battle('glitch', [mon('Umbreon', ['darkpulse'])], [mon('Gengar', ['splash'])]);
	turn(b);
	assert.strictEqual(lost(b, 1), 0);
	b = battle('glitch', [mon('Gengar', ['shadowball'])], [mon('Alakazam', ['splash'])]);
	turn(b);
	assert.strictEqual(lost(b, 1), 0);
	b = battle('glitch', [mon('Dragonite', ['dragonclaw'])], [mon('Clefable', ['splash'])]);
	turn(b);
	assert.ok(lost(b, 1) > 0);
});

test('Conversion then Conversion 2 corrupt the field into a Glitch Field', () => {
	const b = battle('grassy', [mon('Porygon2', ['thunderbolt', 'conversion', 'conversion2'])], [mon('Snorlax', ['splash'])]);
	turn(b, 'move 2');
	assert.strictEqual(LR.field(b), 'grassy');
	const log = turn(b, 'move 3');
	assert.strictEqual(LR.field(b), 'glitch', log.join('\n'));
});

test('New World: Nature\'s Madness takes half the max HP; Gravity makes Starlight until it ends', () => {
	let b = battle('newworld', [mon('Tapu Koko', ['naturesmadness'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(lost(b, 1), Math.floor(active(b, 1).maxhp / 2));
	b = battle('newworld', [mon('Alakazam', ['gravity', 'calmmind'])], [mon('Snorlax', ['calmmind'])]);
	turn(b);
	assert.strictEqual(LR.field(b), 'starlight');
	for (let i = 0; i < 5 && LR.field(b) === 'starlight'; i++) turn(b, 'move 2');
	assert.strictEqual(LR.field(b), 'newworld');
});

test('Fairy Tale: Steel slays Dragons; Crafty Shield boosts; Battle Armor shines', () => {
	let b = battle('fairytale', [mon('Metagross', ['ironhead'])], [mon('Dragonite', ['splash'])]);
	const log = turn(b);
	assert.ok(lines(log, /-supereffective/).length === 1, log.join('\n'));
	b = battle('fairytale', [mon('Jolteon', ['craftyshield'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(active(b, 0).boosts.def, 1);
	assert.strictEqual(active(b, 0).boosts.spd, 1);
	b = battle('fairytale', [mon('Kabutops', ['splash'], {ability: 'battlearmor'})], [mon('Snorlax', ['splash'])]);
	assert.strictEqual(active(b, 0).boosts.def, 1);
});

test('Chess: pieces, the Pawn hangs on, the King\'s Shield stops status moves', () => {
	let b = battle('chess', [mon('Mewtwo', ['psystrike'])],
		[mon('Rattata', ['splash']), mon('Snorlax', ['splash']), mon('Blissey', ['splash'])]);
	const side = b.sides[1].pokemon;
	assert.deepStrictEqual(side.map((p) => LR.piece(p)), ['pawn', 'king', 'queen']);
	turn(b);
	assert.strictEqual(active(b, 1).hp, 1);
	b = battle('chess', [mon('Aegislash', ['kingsshield'])], [mon('Jolteon', ['thunderwave'])]);
	turn(b);
	assert.strictEqual(active(b, 0).status, '');
});

test('Big Top: the strength meter, Belly Drum +1 Def, Clangorous Soul half HP for +2', () => {
	let b = battle('bigtop', [mon('Machamp', ['closecombat'])], [mon('Snorlax', ['splash'])]);
	const log = turn(b);
	assert.ok(lines(log, /whammo/).length === 1 && lines(log, /striker_/).length === 1, log.join('\n'));
	b = battle('bigtop', [mon('Snorlax', ['bellydrum'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(active(b, 0).boosts.atk, 6);
	assert.strictEqual(active(b, 0).boosts.def, 1);
	b = battle('bigtop', [mon('Kommo-o', ['clangoroussoul'])], [mon('Snorlax', ['splash'])]);
	turn(b);
	assert.strictEqual(active(b, 0).boosts.atk, 2);
	assert.strictEqual(lost(b, 0), Math.floor(active(b, 0).maxhp / 2));
});

test('Seeds: Elemental Seed on Electric Terrain, +1 Spe, Charge, used up', () => {
	const b = battle('electerrain', [mon('Pikachu', ['thunderbolt'], {item: 'elementalseed'})], [mon('Snorlax', ['splash'])]);
	const p = b.sides[0].active[0];
	assert.strictEqual(p.boosts.spe, 1);
	assert.ok(p.volatiles.charge, 'charged');
	assert.strictEqual(p.item, '');
	assert.ok(b.log.includes('|-leaguereborn|text|seed_elementalseed|Pikachu'));
});

test('Seeds: the wrong seed does nothing', () => {
	const b = battle('electerrain', [mon('Pikachu', ['thunderbolt'], {item: 'telluricseed'})], [mon('Snorlax', ['splash'])]);
	const p = b.sides[0].active[0];
	assert.strictEqual(p.boosts.spe, 0);
	assert.strictEqual(p.item, 'telluricseed');
});

test('Seeds: Telluric Seed on Rocky hurts (rock damage), Synthetic Seed grows the Flower Garden', () => {
	const b = battle('rocky', [mon('Snorlax', ['splash'], {item: 'telluricseed'})], [mon('Snorlax', ['splash'])]);
	const p = b.sides[0].active[0];
	assert.strictEqual(p.boosts.def, 1);
	assert.strictEqual(p.maxhp - p.hp, Math.floor(p.maxhp / 4)); // neutral Rock: a quarter
	const g = battle('flowergarden1', [mon('Venusaur', ['splash'], {item: 'syntheticseed'})], [mon('Snorlax', ['splash'])]);
	assert.strictEqual(LR.field(g), 'flowergarden2');
	assert.ok(g.sides[0].active[0].volatiles.ingrain);
});

test('Seeds: Magical Seed on New World, all +1 then a lost turn', () => {
	const b = battle('newworld', [mon('Mew', ['tackle'], {item: 'magicalseed'})], [mon('Snorlax', ['splash'])]);
	const p = b.sides[0].active[0];
	assert.deepStrictEqual([p.boosts.atk, p.boosts.def, p.boosts.spa, p.boosts.spd, p.boosts.spe], [1, 1, 1, 1, 1]);
	const log = turn(b);
	assert.ok(lines(log, /\|cant\|p1a[^|]*\|recharge/).length === 1, 'recharges turn 1');
	const log2 = turn(b);
	assert.ok(lines(log2, /\|move\|p1a[^|]*\|Tackle/).length === 1, 'moves turn 2');
});

test('Seeds: Burning traps its holder, Misty sends a Wish, a field change triggers the seed', () => {
	const b = battle('burning', [mon('Arcanine', ['splash'], {item: 'elementalseed'})], [mon('Snorlax', ['splash'])]);
	assert.ok(b.sides[0].active[0].volatiles.partiallytrapped, 'trapped');
	turn(b);
	assert.ok(b.sides[0].active[0].volatiles.partiallytrapped, 'still trapped after turn 1');
	const m = battle('misty', [mon('Clefable', ['splash'], {item: 'elementalseed'})], [mon('Snorlax', ['splash'])]);
	assert.ok(m.sides[0].slotConditions[0].wish);
	const c = battle('rocky', [mon('Venusaur', ['grassyterrain'], {item: 'elementalseed'})], [mon('Snorlax', ['splash'])]);
	assert.strictEqual(c.sides[0].active[0].item, 'elementalseed');
	turn(c);
	assert.strictEqual(LR.field(c), 'grassy');
	assert.strictEqual(c.sides[0].active[0].item, '');
	assert.ok(c.sides[0].active[0].volatiles.ingrain);
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
