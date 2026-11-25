# Phantasmon Events

**Run community events on any Cobblemon server: trainer NPCs, a Pokémon League with Pokémon Reborn's field effects,
tournaments with a live bracket, mirror hubs and community games.**

Phantasmon Events is an add-on for [Phantasmon](https://modrinth.com/mod/phantasmon). It gives a community's
organisers the tools to stage events (gym leaders to beat, a League to climb, Smogon-rules tournaments) on a Minecraft
server where no mod or plugin can be installed. Everything is run from the game by the organisers, and stored on the
community's Phantasmon backend.

**Client-side, like Phantasmon.** Nothing to install on the Minecraft server. Every player taking part in the events
installs it: it is what shows the event NPCs, the tournaments and the League's intros. It needs a Phantasmon backend
with the Phantasmon Events module (see [Who is it for](#who-is-it-for)).

<!-- GIF: a gym leader's intro -->

## Who is it for

It is made for communities that use Phantasmon and run their own backend:

- **You play in such a community:** install it with Phantasmon, and take part in its events.
- **You organise events for your community:** add the Phantasmon Events module to your backend (one file, guide
  below), and you are an organiser as soon as you are a Phantasmon admin.

On a backend without the module, or without a backend, it does nothing.

## Trainer NPCs

Place a trainer anywhere, with any player's skin, and give it a team copied from yours. Players challenge it through
Cobblemon's interaction wheel, with their Ghost team or their Cobblemon party, in a full Phantasmon battle lobby.

- **AI levels**, from random to an expert AI that reads the battle (it knows the usual competitive sets from
  Smogon's usage statistics), and an AI adapted from [Pokémon Reborn](https://www.rebornevo.com)'s, with its
  settings.
- **What its battles impose:** the format (free or Smogon rules), the intro and the ambiance from Cobblemon Battle
  Showtime, the NPC starring in the intro. The player cannot change them in the lobby.
- **Events:** NPCs are grouped by event. Prepare an event closed (only organisers see its NPCs and can test them),
  then open it: all its NPCs appear for the players at once.
- One editor for everything: name, skin, event, AI, format, intro, ambiance, team, place.

<!-- Screenshot: the NPC editor -->

## A Pokémon League

Battle intros made for a League, in Cobblemon Battle Showtime:

- **Gym leaders:** one intro per type, in the type's colours, with its symbol.
- **Elite Four:** a grander entrance.
- **Champion:** the grandest of all.

Give them to an NPC, or as a reward to a player (a player who became a gym leader gets their gym's intro).

<!-- GIF: the Champion's intro -->

## League Reborn: field effects

The field effects of the fan game [Pokémon Reborn](https://www.rebornevo.com), adapted to Cobblemon's battle engine as
a battle rule:

- every field of the game, each with its own effects on moves, abilities and items, the seeds and the Amplifield Rock;
- a Cobblemon Battle Showtime ambiance and stadium for each field, and the transitions when a move changes the field;
- players around a battle see its field from afar;
- an organiser imposes a field on an NPC's battles, or gives a field as a reward: its owner can turn it on in the
  battle lobby.

The field data, the move table and the AI are adapted from Pokémon Reborn, with credit to its team (see
[Credits](#credits)). The game's texts and graphics are not included.

<!-- GIF: a field and its transition -->

## Mirror hubs

An organiser can copy a zone of a multiplayer server into their own single-player world, as a Phantasmon hub: players
from other servers then meet in that copy, see it change live, talk, trade and battle there.

**How the copy is made, and who sends it:** when a player who has Phantasmon Events walks near the zone an organiser
chose as a mirror hub's source, their game reads the blocks of that zone as the server sends them and uploads them to
the backend (the whole zone the first time, then each block that changes, including what blocks hold, such as the text
of signs). Nothing is sent to the Minecraft server. **Only set up a mirror hub on a server whose owners agree**: many
servers forbid copying their builds, and the builds belong to their authors.

Sending is on by default. Any player can turn it off: Phantasmon menu (**H**), Settings, "Hub terrain sync"; their game then reads and sends nothing.

<!-- Screenshot: a mirror hub -->

## Tournaments

- **Swiss** or **double elimination**, best of 1, 3 or 5, in a Smogon format.
- **Planned steps:** registration, check-in, start; set the dates and the tournament runs itself: the check-in opens,
  the tournament starts with those present, the rounds follow.
- **In game**, a Tournaments page in the Phantasmon menu: list, players, matches, standings, the bracket, and the
  organisers' tools.
- Each match opens the usual Phantasmon battle lobby for both players, in the tournament's format.
- **A public web page** for each tournament, served by your backend, like a tournament site: bracket, standings,
  matches, players' heads, updated live.

<!-- Screenshot: the web page with a bracket -->

## Community games (in development)

A betrayal game for nine players, played in rooms built in a hub: escape-game rooms, secret votes, roles, ghosts,
and a game master. With Simple Voice Chat, the game lowers or mutes what each player hears of the others (on their
own game only). It is not finished: it will come in a later version, and its placeholder art will be replaced.

## Public web pages

The backend module also serves public web pages for your community: each tournament's bracket, standings and matches,
and a wiki with the League Reborn fields. They show players' names and heads (from their Minecraft skins) and match
results.

## How to use

- **Players:** the Tournaments page is in the Phantasmon menu (or bind the "Tournaments" key in Options → Controls →
  Phantasmon Events); challenge an event NPC with **R** (Cobblemon's wheel).
- **Organisers (Phantasmon admins):**
  - `/phantasmon event npc create <event> <name>` places a trainer where you stand; `npc edit` opens the nearest
    one's editor; `npc move` brings it to you.
  - `/phantasmon event open <event>` / `close <event>` shows or hides an event's NPCs; `list` lists the events.
  - Tournaments are created and run from their page in the Phantasmon menu.
  - Mirror hubs: created in the Phantasmon menu's hub manager ("Event hubs" tab); `/phantasmon event hub anchor
    <hub>` places the source zone on the server, or the copy in your single-player world.
- French and English.

## Network and privacy

Phantasmon Events talks only to your community's Phantasmon backend (the address set in Phantasmon), as Phantasmon
does, and adds no other server. What it sends there:

- event NPCs, tournaments, registrations and results;
- **mirror hubs:** when you walk near the source zone of a mirror hub on a server, the blocks of that zone, as
  described above, so the organiser's copy stays up to date. This happens only where an organiser of your backend has
  set up a mirror hub, and you can turn it off in Settings ("Hub terrain sync", on by default).

Tournament results and players' names are shown on the backend's public web pages. Player skins come from Mojang as in
vanilla. See Phantasmon's page for what the backend receives.

## Dependencies

**Required mods**

| Mod | Version |
|---|---|
| [Fabric Loader](https://fabricmc.net/use/) | 0.18.1 or later, Minecraft 1.21.1, Java 21 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | any for 1.21.1 |
| [Cobblemon](https://modrinth.com/mod/cobblemon) | 1.8.1 or later |
| [Phantasmon](https://modrinth.com/mod/phantasmon) | 0.1.66 or later (0.1.x) |
| [Cobblemon Battle Showtime](https://modrinth.com/mod/cobblemon-battle-showtime) | 1.7.30 or later (1.x) |

**Optional:** [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), for the voice in the mirror hubs
and the community games.

**Also needed:** a Phantasmon backend with the Phantasmon Events module.

## Add the module to your backend

The backend module is in the same repository, free and open source. Build it and drop it in your backend's `addons/`
folder, then restart: the steps are in the Phantasmon backend's hosting guide, section "Add-ons":
**https://github.com/TrAdrienGit/Phantasmon-Backend/blob/main/Documentation/guides/self-hosting.md#7-add-ons-phantasmon-events**

## How it was made

Phantasmon Events was developed with the help of generative AI coding tools, under its author's direction, design and
testing in game with the community. The Reborn item icons and the betrayal game's placeholder art (Unown glyphs, map,
documents) are drawn by scripts that were also written with AI; the mod's icon is hand-made. It contains no game
assets and no music.

## Credits

- **[Pokémon Reborn](https://www.rebornevo.com)** by Amethyst and the Pokémon Reborn team: the League Reborn field
  data and effects, the move table and the Reborn AI are adapted from the game's scripts, as its FAQ allows with
  credit. Phantasmon Events is not affiliated with or endorsed by the Pokémon Reborn team.
- **[Smogon](https://www.smogon.com/stats/)**: the expert AI's competitive sets come from Smogon's usage statistics.

## Links

- Source (mod and backend module) and issues: https://github.com/TrAdrienGit/Phantasmon-Events
- License: Apache 2.0
- Author: Mystaria. Contact: the issue tracker, https://github.com/TrAdrienGit/Phantasmon-Events/issues

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

*A fan project, not affiliated with Nintendo, The Pokémon Company, Game Freak, the Cobblemon team or the Pokémon Reborn
team.*
