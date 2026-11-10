# Référence du module backend de Phantasmon Events

> D-41 (2026-11-10) : la partie serveur de Phantasmon Events est un module (`backend/`) que le backend Phantasmon charge
> depuis son dossier `addons/`. Cette page reprend ce qui en était décrit dans la référence du backend Phantasmon
> (`Phantasmon-Backend/Documentation/reference/`), qui n'en garde que des renvois. Les décisions sont dans
> `Phantasmon-Backend/Documentation/architecture/decisions.md` (D-38, D-41) ; le plan de séparation dans
> [`plan-separation.md`](plan-separation.md).

## 1. Le module

- Jar `backend/build/libs/phantasmon-events-backend-<version>.jar` (`./gradlew jar` dans `backend/`, après
  `./gradlew publishToMavenLocal` dans `Phantasmon-Backend`). Déployé par `Phantasmon-Backend/scripts/deploy-ubuntu.sh`
  dans `/opt/phantasmon/addons/` (`--no-events` : sans).
- Même package que le cœur (`com.mystaria.phantasmon_backend.events`) : le scan des composants, entités et dépôts du
  cœur le trouve sans rien enregistrer.
- Branché sur les points d'extension du cœur (package `addon`) : `EventsWsHandler` (`WsMessageHandler`),
  `EventNpcService` (`NpcOpponentProvider`), `TournamentRunService` (`TournamentHook`), `MirrorHubKind` (`HubKind`,
  type `MIRROR`), `EventsPublicRoutes` (`PublicRoutesProvider`), `EventsAddon` (`BackendAddon`, annoncé par
  `GET /version` : `"addons": {"phantasmon_events": "<version>"}`).
- Configuration : `PHANTASMON_MIRROR_SERVER_TOKEN` (ou `phantasmon.mirror.server-token`), jeton du serveur dédié ;
  vide : copie du serveur dédié désactivée.

## 2. Routes REST

Toutes demandent un jeton de joueur, sauf la page publique des tournois et la copie du serveur dédié (son propre jeton).

| Méthode | Route | Accès | Rôle |
|---|---|---|---|
| `GET` | `/events/npcs` | Joueur | Dresseurs PNJ visibles ici (§2.1) |
| `GET` | `/admin/events` | Admin | Événements, ouverts ou non |
| `POST` | `/admin/events/npcs` | Admin | Poser un dresseur PNJ |
| `POST` | `/admin/events/npcs/{uuid}` | Admin | Le modifier (nom, skin, événement, IA, format, place) |
| `POST` | `/admin/events/npcs/{uuid}/team` | Admin | Son équipe : copie de celle de l'admin |
| `DELETE` | `/admin/events/npcs/{uuid}` | Admin | Le supprimer |
| `POST` | `/admin/events/{name}/open` | Admin | Ouvrir ou fermer un événement |
| `GET` | `/tournaments`, `/tournaments/{name}` | Joueur | Tournois et leur état ([`plan-tournois.md`](plan-tournois.md)) |
| `POST` / `DELETE` | `/tournaments/{name}/register` | Joueur | S'inscrire / se désinscrire |
| `POST` | `/tournaments/{name}/check-in` | Joueur | Check-in (équipe Ghost ou party Cobblemon conforme au format) |
| `POST` | `/admin/tournaments` | Admin | Créer un tournoi |
| `POST` | `/admin/tournaments/{name}/schedule`, `/check-in`, `/start`, `/rounds`, `/next-round`, `/finish`, `/cancel`, `/reinvite`, `/result`, `/players` | Admin | Piloter le tournoi |
| `DELETE` | `/admin/tournaments/{name}`, `/admin/tournaments/{name}/players/{username}` | Admin | Effacer un tournoi fini ou annulé ; retirer un joueur |
| `GET` | `/t`, `/t/{name}`, `/public/tournaments`, `/public/tournaments/{name}` | Public | Page web des tournois (sans UUID de joueur) |
| `GET` | `/events/mirror/{hub}`, `/events/mirror/{hub}/tile/{tx}/{tz}`, `/events/mirror/{hub}/ghosts` | Joueur | Terrain d'un hub miroir (D-38) |
| `DELETE` | `/admin/events/mirror/{hub}/ghosts` | Admin | Effacer les blocs fantômes d'un hub miroir |
| `POST` | `/admin/hubs/{name}/resize` `{ "delta" }` | Admin | Agrandir / réduire un hub miroir (`ERROR_HUB_NOT_MIRROR`, `ERROR_HUB_INVALID_RADIUS`) |
| `GET` / `POST` | `/server/mirror/...` | Jeton du serveur | Copie du serveur dédié (§2.2) |

Un hub miroir se crée par la route du cœur : `POST /admin/hubs` `{ "name", "mirror": true, "radius" }` ; ses Anchors
par `POST /hub/anchors` avec `"role": "SOURCE"` (une par hub, `ERROR_HUB_SOURCE_TAKEN`) ou `"COPY"` (une par admin,
`ERROR_HUB_ANCHOR_QUOTA`), admins seulement (`ERROR_HUB_MIRROR_ADMIN_ONLY`). Les vues du hub et de ses Anchors portent
`"kind": "MIRROR", "mirror": true, "radius"`.

### 2.1 Dresseurs PNJ des événements

Phantasmon Events (Adrien 2026-11-02). Un dresseur PNJ appartient à un événement ; les joueurs ne le voient et ne le
défient que si l'événement est ouvert, les admins toujours (pour le régler). Chaque changement envoie
`EventsNpcsChanged` à tous les clients connectés, qui relisent la liste.

- `GET /events/npcs?server=<empreinte>&dimension=<id>` → `[{ "uuid", "event", "open", "name", "skin", "hub", "x", "y",
  "z", "yaw", "ai", "format_id", "team_size", "team_species" }]` (`team_species` : admins seulement) : ceux posés ici
  hors hub, et ceux de chaque hub qui a une ancre ici (`hub` non null : `x`, `y`, `z`, `yaw` en coordonnées du hub, le
  client les place dans chaque zone de ce hub).
- `GET /admin/events` → `[{ "name", "open", "npcs" }]`.
- `POST /admin/events/npcs` `{ "event", "name", "server_fingerprint", "dimension", "x", "y", "z", "yaw" }` → le PNJ ;
  l'événement est créé (fermé) s'il n'existe pas. `400 ERROR_EVENT_NAME` (minuscules, chiffres, `_`, 32 au plus),
  `ERROR_NPC_NAME`.
- `POST /admin/events/npcs/{uuid}` : chaque champ facultatif — `name`, `skin` (pseudo Minecraft, vide : aucun,
  `ERROR_NPC_SKIN`), `event`, `ai` (`random`, `easy`, `normal`, `hard`, `veryhard`, `expert`, `reborn`, `reborn_tweak`, `ERROR_NPC_AI`), `ai_debug` (bool : l'IA explique chaque décision dans le chat du joueur), `format_id`, et
  la place (`server_fingerprint`, `dimension`, `x`, `y`, `z`, `yaw` ensemble, et `hub` : le nom du hub si la place est
  donnée dans ses coordonnées, absent ou null sinon). `404 ERROR_NPC_NOT_FOUND`, `ERROR_HUB_NOT_FOUND`. `POST
  /admin/events/npcs` accepte aussi `hub`.
- `POST /admin/events/npcs/{uuid}/team` `{ "team": "GHOST" }` ou `{ "team": "COBBLEMON", "party": [...] }` (comme une
  invitation) : l'équipe du PNJ devient une copie de celle de l'admin (6 au plus). `400 ERROR_BATTLE_EMPTY_TEAM`,
  `ERROR_BATTLE_INVALID_PARTY`.
- `DELETE /admin/events/npcs/{uuid}` → `{ "deleted": true }`.
- `POST /admin/events/{name}/open` `{ "open": bool }` → `{ "name", "open" }` ; `404 ERROR_EVENT_NOT_FOUND`.

Le combat contre un PNJ passe par le WebSocket (`BattleNpcStart`).

### 2.2 Copie miroir du serveur dédié

Notre propre serveur Minecraft (son mod `phantasmon-mirror-server`) construit les copies posées sur lui. Pas un joueur :
en-tête `X-Phantasmon-Server-Token` = `phantasmon.mirror.server-token` (`PHANTASMON_MIRROR_SERVER_TOKEN`) ; sans jeton
configuré, `404` ; jeton faux, `403 ERROR_SERVER_TOKEN`.

- `GET /server/mirror/copies?fingerprint=<empreinte>` → `[{ "hub", "dimension", "min_x", "min_z", "side" }]` : les
  ancres COPY posées sur ce serveur.
- `GET /server/mirror/{hub}`, `/server/mirror/{hub}/tile/{tx}/{tz}`, `/server/mirror/{hub}/ghosts` : comme
  `/events/mirror/...`.
- `GET /server/mirror/{hub}/feed?since=<n>` → `{ "reset", "next", "messages": [{ "type", "data" }] }` : les messages
  envoyés aux copies depuis le numéro `n` (`EventsMirrorBlocks`, `EventsMirrorTileChanged`, `EventsMirrorGhosts`,
  `EventsMirrorReset`), les 2 000 derniers par hub ; `reset: true` s'il en manque (la copie est refaite) ; `since=0` :
  seulement le numéro actuel.
- `POST /server/mirror/{hub}/ghosts` `{ "changes": [[x, y, z, state], ...] }` → `{ "changed" }` : blocs posés
  (`state`) ou cassés (`""`) à la main par un opérateur dans la copie de ce serveur, traités comme les blocs fantômes
  d'une copie solo (montrés dans la zone source et les autres copies).

## 3. Messages WebSocket

Reçus par le cœur et confiés au module (`EventsWsHandler`), ou envoyés par lui.

| Message | Sens | Rôle |
|---|---|---|
| `EventsMirrorTile`, `EventsMirrorBlocks` | C2S | Terrain lu sur le serveur source (tuile entière ; blocs changés) |
| `EventsMirrorSubscribe`, `EventsMirrorUnsubscribe` | C2S | Une copie suit / ne suit plus son hub |
| `EventsMirrorWatch`, `EventsMirrorUnwatch` | C2S | Un joueur de la zone source voit / ne voit plus les blocs fantômes |
| `EventsMirrorGhosts` | C2S, S2C | Blocs fantômes posés ou retirés dans une copie |
| `EventsMirrorTileChanged`, `EventsMirrorReset` | S2C | Une tuile a changé ; la copie est à refaire (redimensionnement) |
| `EventsTournamentChanged`, `EventsTournamentAnnounce` | S2C | Un tournoi a changé ; annonce dans le chat |
| `EventsNpcsChanged` | S2C | **Tous les clients connectés** : un dresseur PNJ ou un événement a changé ; le client relit `GET /events/npcs` |

`BattleNpcStart` (défier un PNJ) est un message du cœur : il demande le PNJ au module (`NpcOpponentProvider`).

## 4. Codes d'erreur propres au module

| Code | HTTP | Canal | Cas | Clé i18n |
|---|---|---|---|---|
| `ERROR_NPC_NAME`, `ERROR_NPC_SKIN`, `ERROR_NPC_AI` | 400 | REST | Nom (32 au plus), skin (pseudo Minecraft) ou IA invalides | `phantasmon_events.error.*` (Events) |
| `ERROR_EVENT_NAME` | 400 | REST | Nom d'événement invalide (minuscules, chiffres, `_`) | `phantasmon_events.error.error_event_name` |
| `ERROR_EVENT_NOT_FOUND` | 404 | REST | Événement inconnu | `phantasmon_events.error.error_event_not_found` |

Les tournois ont leurs codes `ERROR_TOURNAMENT_*` (nom, format, étape, inscription, check-in…), décrits par leur message
dans le client Events. `ERROR_NPC_NOT_FOUND`, `ERROR_NPC_EMPTY_TEAM` et `ERROR_NPC_BUSY` restent des codes du cœur.

## 5. Base de données

### 5.1 Migrations du module

Le module backend de Phantasmon Events (`Phantasmon-Events/backend`, chargé depuis le dossier `addons/`) a ses
propres migrations : `db/events/`, historique dans `flyway_schema_history_events`, jouées juste après celles du cœur
et avant JPA. Sans le module, une base n'a aucune de ces tables. Sur une base qui les avait déjà (créées par le cœur
en V18-V19 et V22-V24), la ligne de base `0` puis les `IF NOT EXISTS` ne changent rien.

| Version | Fichier | Contenu |
|---|---|---|
| 1 | `V1__tournaments.sql` | Ancien V18 + V19 : `tournaments`, `tournament_players`, `tournament_matches` (§5.2) |
| 2 | `V2__event_npcs.sql` | Ancien V22 à V24 : `event_groups`, `event_npcs` (§5.3) |
| 3 | `V3__mirror_anchor_roles.sql` | Règles des Anchors miroirs : `uq_events_mirror_source` (une `SOURCE` par hub), `uq_events_mirror_copy` (une `COPY` par admin et par hub) |

### 5.2 Tournois (ancien V18-V19 du cœur, V1 du module)

- `tournaments` : `uuid` PK, `name` (unique casse ignorée parmi les tournois ni terminés ni annulés,
  `uq_tournament_open_name`), `kind` (`SWISS` | `BRACKET`), `best_of` (1, 3 ou 5), `format_id`, `status`
  (`REGISTRATION`, `CHECK_IN`, `RUNNING`, `FINISHED`, `CANCELLED`), `starts_at`, `registration_opens_at`,
  `registration_closes_at`, `planned_rounds`, `current_round`, `created_by` (FK `players` `ON DELETE SET NULL`),
  `created_at`.
- `tournament_players` : PK (`tournament_uuid`, `player_uuid`), FK `ON DELETE CASCADE` des deux côtés, `username`,
  `checked_in`, `dropped`, `registered_at`.
- `tournament_matches` : `uuid` PK, `tournament_uuid` (FK `ON DELETE CASCADE`), `round`, `bracket` (`SWISS`,
  `WINNERS`, `LOSERS`, `FINAL`, `RESET`), `position`, `player1_uuid`, `player2_uuid` (null : exempt), `wins1`,
  `wins2`, `winner_uuid`, `status` (`PENDING`, `PLAYING`, `DONE`), `forfeit`, `wave` (V19 : vague où un admin ouvre le match d'un arbre, 0 en ronde
  suisse). Effacer un tournoi annulé ou terminé efface ses joueurs et ses matchs.

### 5.3 `event_groups` et `event_npcs` (ancien V22-V24 du cœur, V2 du module)

`event_groups` : un événement qu'un admin ouvre et ferme.

| Colonne | Type | Contraintes | Notes |
|---|---|---|---|
| `name` | VARCHAR(32) | PK | Minuscules, chiffres, `_` |
| `open` | BOOLEAN | NOT NULL, défaut `false` | Ses PNJ ne sont vus et défiés par les joueurs que s'il est ouvert (les admins les voient toujours) |
| `created_at` | TIMESTAMPTZ | NOT NULL, défaut `now()` | |

`event_npcs` : un dresseur PNJ d'un événement.

| Colonne | Type | Contraintes | Notes |
|---|---|---|---|
| `uuid` | UUID | PK | Aussi l'UUID de son entité côté client (intro, caméra) |
| `event_name` | VARCHAR(32) | NOT NULL, FK `event_groups` `ON DELETE CASCADE` | |
| `name` | VARCHAR(32) | NOT NULL | |
| `skin` | VARCHAR(16) | | Pseudo Minecraft dont il porte le skin ; null : son nom |
| `hub_uuid` | UUID | FK `hubs` `ON DELETE CASCADE`, index `event_npcs_hub` (V23) | Le hub auquel il appartient : `x`, `y`, `z`, `yaw` sont alors ses coordonnées dans le hub, et chaque client le place dans chaque zone de ce hub ; null : une place dans le monde |
| `server_fingerprint`, `dimension` | VARCHAR(128) | NOT NULL | Où il se tient (index `event_npcs_place`) |
| `x`, `y`, `z`, `yaw` | DOUBLE, REAL | NOT NULL | |
| `team` | JSONB | NOT NULL, défaut `[]` | Copie de Pokémon (même forme que les équipes d'un combat), 6 au plus |
| `ai` | VARCHAR(16) | NOT NULL, défaut `normal` | `random`, `easy`, `normal`, `hard`, `veryhard`, `expert`, `reborn`, `reborn_tweak` (contrôle applicatif) |
| `ai_debug` | BOOLEAN | NOT NULL, défaut `false` (V24) | Son IA explique chaque décision dans le chat du joueur qui l'affronte |
| `format_id` | VARCHAR(64) | NOT NULL, défaut `free` | Format de combat (inconnu : Libre) |
| `created_by` | UUID | NOT NULL | L'admin (pas de FK) |
| `updated_at` | TIMESTAMPTZ | NOT NULL | |

### 5.4 Hubs miroirs

Pas de table propre : un hub miroir est une ligne de `hubs` avec `kind = 'MIRROR'` (V25 du cœur), un carré de côté
`size_x = 2 * rayon + 1` sur toute la hauteur (`size_y = 384`) ; ses Anchors ont le rôle `SOURCE` ou `COPY`
(`uq_events_mirror_source`, `uq_events_mirror_copy`, V3 du module). Le terrain est hors base
(`hub_schematics/hub_<nom>/mirror.bin`), les blocs fantômes en mémoire.
