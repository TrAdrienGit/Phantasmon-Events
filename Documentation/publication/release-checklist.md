# Publishing on Modrinth and CurseForge: release checklist

Same process as Cobblemon Battle Showtime and Phantasmon (`Phantasmon-Client/Documentation/publication/`). Page text:
[`description.md`](description.md), in Markdown. Replace the `<!-- GIF / Screenshot -->` comments with the gallery's
images. **Publish Phantasmon first**: this mod requires it, and its page links to Phantasmon's.

## Decisions (settled 2026-11-18)

| Point | Decision |
|---|---|
| League logo, Unown glyphs, type symbols | Checked by Adrien: fine to publish. |
| `ai/smogon_sets.json`, `ai/reborn_moves.json` | Checked by Adrien: fine to publish. |
| Icon | Made by hand by Adrien. |
| The betrayal game | Shipped, presented as "in development" in the description. |
| Version | **0.1.0**, **Beta**, requires `phantasmon >=0.1.0 <0.2.0`. |

## Fields to fill in

| Field | Value |
|---|---|
| Name | Phantasmon Events |
| Slug / URL | `phantasmon-events` |
| Summary (256 characters max) | Community events for Phantasmon: trainer NPCs with AIs and imposed intros, a Pokémon League with gym, Elite Four and Champion intros, Swiss and double-elimination tournaments with a live web bracket. Needs a Phantasmon backend. |
| Project type | Mod |
| Loader | Fabric |
| Minecraft version | 1.21.1 |
| Environment (Modrinth) | Client: required. Server: unsupported (not needed) |
| Categories (Modrinth) | Social, Game Mechanics, Minigame; Cobblemon add-on if offered |
| Categories (CurseForge) | Addons, Multiplayer, Miscellaneous; Fabric |
| License | Apache-2.0 |
| Source | https://github.com/TrAdrienGit/Phantasmon-Events |
| Issues | https://github.com/TrAdrienGit/Phantasmon-Events/issues |
| Icon | `src/assets/Phantasmon_Event_Icon.png` (1024 × 1024) |

## Modrinth rules: what to tick and check

| Rule | What it means for Phantasmon Events |
|---|---|
| 1.11 Remote server disclosure | It talks to the Phantasmon backend only: said in "Network and privacy". Tick the **external system interaction** disclosure. |
| Usefulness | Says at the top who it is for and links the backend module's guide: any Phantasmon community can use it. |
| 1.3 / 4 Trademarks, copyright | Assets checked by Adrien (decisions above). "Gym leader", "Elite Four", "Champion" are used as plain words; no logo, no game asset. |
| 3 Cheats | None. |
| **6.1 AI disclosure** | Tick "Contains AI-generated content"; said in "How it was made". |
| **6.2.1 / 6.2.2** | As for Phantasmon. |

## Dependencies to declare

| Mod | Type |
|---|---|
| Fabric API | Required |
| Cobblemon (1.8.1+) | Required |
| Phantasmon | Required |
| Cobblemon Battle Showtime (1.2.0+) | Required |
| Simple Voice Chat | Optional |

## The file

- Jar: `build/libs/phantasmon-events-<version>.jar` (build with JDK 25, Phantasmon published to Maven local first,
  as the deploy script does).
- Version name: `Phantasmon Events <version>`. Channel: Beta. Game version 1.21.1, Fabric.
- Backend module: `backend/build/libs/phantasmon-events-backend-<version>.jar`, attached to the GitHub release
  `v<version>` (not to Modrinth / CurseForge: it is not a Minecraft mod).

### Changelog

```
First public release.

- Trainer NPCs: skins, teams, AI levels, imposed format, intro and ambiance; grouped in events
  you open and close
- The League's intros: one per gym type, the Elite Four, the Champion
- Tournaments: Swiss or double elimination, Smogon formats, dated steps that run by themselves,
  an in-game page and a live public web page with the bracket
- French and English
```

## Gallery to prepare

1. A gym leader's intro (one type), the Elite Four's, the Champion's.
2. A player challenging an NPC, then the lobby with the imposed format, intro and ambiance.
3. The NPC editor.
4. The in-game Tournaments page (bracket tab).
5. The public web page of a running tournament.

## Before you publish

- [ ] Phantasmon is published (its Modrinth slug is the one in the description's links).
- [ ] Clean instance: Fabric API + Cobblemon + Showtime + Phantasmon + Phantasmon Events, no resource pack. With a
      backend that has the module: NPCs show and can be battled, the Tournaments page opens. With a backend without
      it: the game runs, nothing breaks.
- [ ] GitHub release `v<version>` with the mod jar and the backend module jar.
- [ ] Modrinth, then CurseForge: create the project, upload, tick the disclosures.
