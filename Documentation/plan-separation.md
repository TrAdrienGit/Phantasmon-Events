# Plan : séparer Phantasmon Events de Phantasmon

> Plan du 2026-11-10, validé par Adrien le même jour (décision D-41). Suivi côté Phantasmon : TODO-35
> (`Phantasmon-*/Documentation/project/known-issues.md`).
>
> **Avancement** : **séparation terminée le 2026-11-10** (E1 à E5, voir §4). Validé en jeu par Adrien : IA expert et
> Reborn-Tweak ; hubs miroirs pas encore retestés en jeu depuis E2. Les décisions ouvertes (§5) suivent les propositions tant
> qu'Adrien ne dit pas autre chose ; la 1 doit être tranchée avant E2.

## 1. Le besoin (Adrien, 2026-11-10)

Events est **facultatif**. L'admin d'un serveur qui ne veut que Phantasmon installe le mod Phantasmon et fait
tourner le backend Phantasmon, sans rien d'Events. Celui qui veut les deux (nous) fait tourner **le même backend**,
avec en plus un **module Events chargé par ce backend**. Pas de second serveur à faire tourner.

Aujourd'hui, du code d'Events vit dans les dépôts Phantasmon : ce n'est pas propre et un admin qui n'installe pas
Events embarque quand même ses tables, ses routes et ses IA.

## 2. Ce qui doit déménager (inventaire du 2026-11-10)

### Backend (`Phantasmon-Backend`)

| Élément | Où aujourd'hui |
|---|---|
| Hubs miroirs | `events/MirrorService`, `MirrorController`, `ServerMirrorController` |
| Tournois | `events/tournament/` (12 fichiers) + `SwissTest` |
| PNJ d'événements | `events/npc/` (6 fichiers) |
| Tables | V18-V19 (`tournaments`, `tournament_players`, `tournament_matches`), V22-V24 (`event_groups`, `event_npcs`) |
| Colonnes sur des tables du cœur | V15-V17 : `hubs.mirror`, `hubs.radius`, `hub_anchors.role`, contrainte de taille des hubs |

Environ 3 200 lignes. Le cœur appelle Events à ces endroits, qu'il faudra remplacer par des points d'extension :

| Fichier du cœur | Lien avec Events |
|---|---|
| `websocket/PhantasmonWebSocketHandler` | `switch` sur les 7 messages `EventsMirror*` → `MirrorService` |
| `battle/LiveBattleService` | `EventNpcService` injecté (combats et lobby contre un PNJ) ; `TournamentHook` (déjà un crochet) |
| `hub/HubCatalogService`, `HubCatalogController` | `MirrorService` injecté, création de hub miroir |
| `hub/HubAnchorService`, `HubAnchorResponse` | règles des Anchors d'un hub miroir (`isMirror()`, `role`) |
| `auth/SecurityConfig` | routes publiques : page publique des tournois, copie du serveur dédié (jeton propre) |

Dans l'autre sens, Events utilise du cœur : `AdminService`, `SessionRegistry`, `WsMessage`, `ApiException`,
`PlayerRepository`, `PresenceService`, `HubRepository` / `HubAnchorRepository`, `PokemonService`, `BattleTeam`,
`CobblemonPartyParser`, `LiveBattleService`, les formats. C'est normal : l'add-on dépend du cœur, jamais l'inverse.

### Client (`Phantasmon-Client`)

| Élément | Où aujourd'hui |
|---|---|
| IA Expert et Reborn (~9 000 lignes) | `client/battle/ExpertBattleAI.java`, `RebornBattleAI.java` |
| Leurs données | `assets/phantasmon/ai/smogon_sets.json`, `reborn_moves.json` |
| Choix du niveau d'IA, `reborn_tweak`, debug | `GhostBattles.ai(...)`, `LiveBattleController` |

Les niveaux propres à Cobblemon (`random`, `easy` … `veryhard`) restent dans Phantasmon : le combat solo admin (D-32)
s'en sert. Le combat contre un PNJ reste un point d'extension générique de Phantasmon (`PhantasmonApi.startNpcBattle`).

## 3. Organisation proposée

**Pas de nouveau dépôt.** Le dépôt `Phantasmon-Events` a déjà un sous-projet à part (`mirror-server/`) ; on y ajoute
`backend/`, un projet Gradle Java / Spring qui produit `phantasmon-events-backend-<version>.jar`. Un seul dépôt
Events = tout ce qui est Events (mod client, module backend, copie du serveur dédié), une seule doc, une seule
version.

```
Phantasmon-Events/
  src/            mod client (existe)
  mirror-server/  copie du serveur dédié (existe)
  backend/        NOUVEAU : module chargé par le backend Phantasmon
  Documentation/
```

### Chargement par le backend

- Le backend Phantasmon reste **un seul processus**. Il lit un dossier `addons/` à côté de son jar
  (`/opt/phantasmon/addons/` sur le serveur Ubuntu). Dossier vide ou absent = Phantasmon seul, comme aujourd'hui.
- Mécanisme standard de Spring Boot : lanceur `PropertiesLauncher` du jar exécutable, avec `loader.path=addons`. Le
  jar du module déclare une auto-configuration (`META-INF/spring/…AutoConfiguration.imports`) qui active ses
  services, contrôleurs, entités et dépôts. Rien à changer dans le code du cœur pour le trouver.
- `GET /version` annonce les modules chargés (`addons: { "events": "0.1.0" }`). Le mod Events le lit au login et
  affiche un message clair si le backend n'a pas le module.
- Le module vérifie au démarrage la version du cœur (plage, comme `phantasmon >=0.1.0 <0.2.0` côté mod).
- Compilation : le cœur publie un jar « API » dans le Maven local (comme Showtime pour le mod), le module compile
  contre lui.

### Points d'extension à ajouter au cœur (backend)

1. **Messages WebSocket** : interface `WsMessageHandler` (types gérés + traitement). Le `switch` du cœur délègue tout
   type inconnu aux handlers enregistrés ; `Events*` disparaît du cœur.
2. **Adversaire PNJ** : interface `NpcOpponentProvider` (équipe, niveau d'IA, verrou de combat) utilisée par
   `LiveBattleService` à la place de `EventNpcService`. Absent = pas de combat PNJ.
3. **Tournois** : `TournamentHook` existe déjà ; il devient une interface publique du paquet d'extension.
4. **Hubs** : interface `HubKindExtension` (création d'un type de hub, règles des Anchors, champs en plus dans les
   réponses). Le cœur ne connaît plus `MirrorService`.
5. **Sécurité** : le module déclare ses propres routes publiques (sa propre chaîne de filtres, ordonnée avant celle
   du cœur).

### Base de données

- Le module a **son propre historique Flyway** (table `flyway_schema_history_events`, dossier `db/events`) : il crée
  ses tables seulement s'il est installé.
- Ses migrations reprennent V18-V19 et V22-V24 en `CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` : sur
  notre base de production, où ces tables existent déjà, elles ne changent rien ; sur une base neuve, elles les créent.
- Le cœur retire ces fichiers et ignore leur trace dans son propre historique (`ignore-migration-patterns: *:missing`
  limité à ces versions). Une installation neuve sans Events n'a plus ces tables.
- **Colonnes miroir (V15-V17)** sur `hubs` et `hub_anchors` : elles touchent des tables du cœur. Proposition : le
  cœur garde un champ générique « type de hub » (`kind`, `NORMAL` par défaut) et les rôles d'Anchor ; le rayon et
  ce qui n'a de sens que pour les miroirs passe dans une table du module (`events_mirror_hubs`). **À décider.**
- Avant la bascule : sauvegarde, puis essai complet sur une copie de la base de production (`test-restore`).

### Client

- `PhantasmonApi.registerBattleAi(niveau, fabrique)` : Phantasmon garde les niveaux de Cobblemon, Events enregistre
  `expert`, `reborn`, `reborn_tweak` et leur debug au démarrage.
- `ExpertBattleAI`, `RebornBattleAI` et `assets/phantasmon/ai/` passent dans le mod Events (`assets/phantasmon_events/ai/`).
- Un PNJ dont le niveau n'est pas enregistré (Events absent) retombe sur `random`, avec une ligne de log.

## 4. Étapes

| Étape | Contenu | Vérification |
|---|---|---|
| E0 | Ce plan validé par Adrien ; décisions ouvertes tranchées (§5) ; décision D-41 écrite dans les deux dépôts Phantasmon | — |
| E1 | Backend : points d'extension dans le cœur, Events encore dedans mais branché dessus. Aucun changement de comportement | 228 tests verts |
| E2 | Backend : création de `Phantasmon-Events/backend/`, déplacement du package `events/`, de ses tests et migrations ; dossier `addons/` et lanceur | Tests des deux côtés ; essai sur copie de la base de prod ; backend sans module = Phantasmon seul |
| E3 | Serveur Ubuntu : `deploy-ubuntu.sh` installe aussi le module s'il est construit ; service systemd avec `loader.path` | Déploiement réel, tournois / miroirs / PNJ testés en jeu |
| E4 | Client : registre d'IA dans `PhantasmonApi`, IA et données déplacées dans le mod Events | Combat PNJ expert / reborn en jeu ; combat solo admin intact |
| E5 | Docs : `status`, `known-issues`, schéma de base, référence API (les routes et messages Events passent dans la doc d'Events), guides de déploiement | Relecture |

E1 et E4 peuvent se faire indépendamment ; E2 dépend d'E1, E3 d'E2.

**E1 faite (2026-11-10).** Package `addon` du backend : `WsMessageHandler` (messages `EventsMirror*`, via
`events/EventsWsHandler`), `NpcOpponentProvider` (`EventNpcService` le fournit, `LiveBattleService` ne voit plus
qu'un `NpcOpponent`), `TournamentHook` (sorti de `LiveBattleService`), `HubListener` (`MirrorService` suit le
redimensionnement d'un hub miroir), `PublicRoutesProvider` (`events/EventsPublicRoutes`). Le cœur n'importe plus
rien de `events` ; `CoreIndependenceTest` l'interdit désormais. Pas de changement de comportement. Reste dans le cœur,
en attendant la décision 1 : le drapeau `mirror` et le rayon des hubs, les rôles d'Anchor, `createMirror` / `resize`
dans `HubCatalogService` et les règles des Anchors miroirs dans `HubAnchorService`.

**E4 faite (2026-11-10).** Phantasmon : interface `api/PhantasmonBattleAI` (choix du meneur `orderTeam`, remplaçant après K.O., `onChoiceRefused` pour le debug) et `PhantasmonApi.registerBattleAi(niveau, fabrique)` ; `GhostBattles.ai` ne connaît plus que les niveaux de Cobblemon et les niveaux enregistrés (inconnu : `normal`, avec un avertissement dans le journal). Events : `ai/ExpertBattleAI`, `ai/RebornBattleAI`, `assets/phantasmon_events/ai/` et `tools/smogon_sets.py`, enregistrés au démarrage (`expert`, `reborn`, `reborn_tweak`). Compilé et déployé dans les 4 instances ; combats PNJ à valider en jeu.

**E2 faite (2026-11-10).** Décision 1 tranchée par Adrien : « type de hub » dans le cœur, le reste côté Events.
- Cœur : `hubs.kind` (V25 ; `mirror` et `radius` supprimés, le rayon se déduit de la taille, donc aucune table Events
  n'a été nécessaire), `hub_anchors.role` libre, point d'extension `addon/HubKind` (taille, rôle et placement des
  Anchors, champs ajoutés aux vues), `addon/BackendAddon` (`GET /version` → `addons`). Jar exécutable en
  `PropertiesLauncher` avec `Loader-Path: addons/` ; publié dans le Maven local (`publishToMavenLocal`).
- Module `backend/` de ce dépôt : tout l'ancien package `events/` (même nom de package, pour que le cœur le trouve
  sans rien enregistrer), `MirrorHubKind`, `MirrorHubController` (redimensionnement), `EventsDatabase` (Flyway propre,
  `db/events` V1-V3, historique `flyway_schema_history_events`), la page `public/tournament.html`, ses tests (11).
- Vérifié sur une copie de la base de production : cœur seul (V25, aucune table Events créée), puis avec le module
  (tables reconnues sans perte, `GET /version` annonce `phantasmon_events`), puis redémarrage.
- Déploiement : `scripts/deploy-ubuntu.sh` compile et installe le module dans `addons/` (`--no-events` : sans).

## 5. Décisions ouvertes

1. **Colonnes des hubs miroirs** : champ générique `kind` dans le cœur + table du module (proposé), ou les laisser
   telles quelles dans le cœur.
2. **Nom et version du module** : `phantasmon-events-backend`, même numéro de version que le mod Events (proposé).
3. **`TrainerProfile` (V20)** et **mutes du chat du hub (V21)** : restent dans le cœur (profils d'animation de
   Showtime, modération du hub). À confirmer.

**E3 faite (2026-11-10).** Sauvegarde de la base, puis `deploy-ubuntu.sh` : V25 appliquée, module installé dans
`/opt/phantasmon/addons/`, ses migrations ont reconnu les tables existantes ; hubs, Anchors et PNJ conservés ;
`GET /version` annonce `phantasmon_events`.

**E5 faite (2026-11-10).** La référence du module ([`backend-reference.md`](backend-reference.md) : module, routes,
messages `Events*`, codes d'erreur, tables) quitte `Phantasmon-Backend/Documentation/reference/`, qui n'en garde que
des renvois ; ajouts au cœur : `kind` des hubs, `GET /version` → `addons`, `ERROR_HUB_KIND_UNAVAILABLE`, guide de
déploiement (`addons/`, `--no-events`).
