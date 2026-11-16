# Protocole du jeu de trahison

> Référence unique de tous les messages et actions du jeu. Chaque étape y **ajoute** ce qu'elle crée (une section par
> message, une section par action) ; elle ne la contourne jamais. Vue d'ensemble : [`architecture.md`](architecture.md)
> section 5. État : étape T0 (Ping et Pong), T1.3 (View, Say, Action, Gm : moteur de partie), T2.1 (routes du plan). Patch, Room, Puzzle et Zone restent à venir.

## 1. Conventions

- Canal : le WebSocket de Phantasmon. Enveloppe `{ "type": "...", "data": { ... } }`.
- Tous les types commencent par `EventsTrahison` (le client de Phantasmon n'accepte que le préfixe `Events`).
- Sens : **C2S** client vers backend, **S2C** backend vers client.
- Le backend décide ; un message C2S est une demande, jamais un fait. Un secret n'est jamais envoyé à qui n'a pas le
  droit de le voir (une vue par destinataire).
- Une demande refusée est répondue par un `EventsTrahisonSay` privé portant un code `ERROR_TRAHISON_…`, traduit dans
  `phantasmon_events.error.*` (`en_us.json` et `fr_fr.json`). Une demande d'un message inconnu du module est ignorée.
- Les noms de champs sont en `snake_case`. Les énumérations (étape, couleur, vote, mode…) sont écrites en MAJUSCULES
  dans les messages du serveur ; les valeurs de texte envoyées par le client (`action`, `vote`, `policy`, `mode`) sont
  acceptées sans tenir compte de la casse.
- **Le hub.** Une partie vit dans un hub miroir, une seule par hub. Le client indique le hub dans le champ `hub`
  (nom du hub) de ses messages `EventsTrahisonAction` et `EventsTrahisonGm`. Il est facultatif dès que l'expéditeur
  joue déjà dans une partie (le backend prend alors celle-là), et pour un maître du jeu qui dirige déjà une partie.
  Un nom qui n'est pas celui d'un hub miroir est refusé (`ERROR_TRAHISON_NO_HUB`).
- **Connexion.** Un module n'est pas prévenu qu'un joueur se connecte : à la connexion, et à chaque reconnexion, le
  client envoie l'action `view` (avec `hub`). Tout message d'un joueur d'une partie le remet à `connected = true`.
  Quand le WebSocket se ferme, le backend le passe à `connected = false` ; la partie continue (voir « Déconnexion »).
- **Valeurs 64 bits.** `seed` est un entier signé de 64 bits (type `long`) ; un client JavaScript le perdrait.
- Les routes HTTP (`/events/trahison/…`) servent aux gros contenus ; elles ont leur section 4.

## 2. Messages

Modèle de section à recopier pour chaque nouveau message :

> ### `EventsTrahisonXxx`
> - Sens : C2S | S2C
> - Qui l'envoie / qui le reçoit : …
> - Quand : …
> - Champs : tableau (nom, type, sens)
> - Exemple JSON
> - Erreurs : codes possibles

### `EventsTrahisonPing`

- Sens : C2S.
- Qui l'envoie : tout joueur connecté.
- Quand : commande `/phantasmon event trahison` (étape T0) ; sert à vérifier la liaison avec le module backend.
- Champs :

| Champ | Type | Notes |
|---|---|---|
| `nonce` | texte | Facultatif. Renvoyé tel quel dans le Pong |

```json
{ "type": "EventsTrahisonPing", "data": { "nonce": "123456789" } }
```

- Erreurs : aucune.

### `EventsTrahisonPong`

- Sens : S2C.
- Qui le reçoit : le joueur qui a envoyé le Ping, et lui seul.
- Quand : en réponse immédiate à un Ping.
- Champs :

| Champ | Type | Notes |
|---|---|---|
| `nonce` | texte | Celui du Ping ; absent s'il n'y en avait pas |

```json
{ "type": "EventsTrahisonPong", "data": { "nonce": "123456789" } }
```

- Erreurs : aucune.

### `EventsTrahisonView`

- Sens : S2C.
- Qui le reçoit : tout destinataire de la partie, une vue **différente pour chacun** (voir « Qui voit quoi » dans
  `architecture.md` section 4). Quatre sortes de destinataires, champ `viewer.kind` :
  - `PLAYER` : un joueur vivant de la partie ;
  - `GHOST` : un joueur mort de la partie ;
  - `SPECTATOR` : tout autre membre du hub, ou quiconque a envoyé `view` ;
  - `GM` : un admin qui dirige la partie et qui ne la joue pas (un maître du jeu qui joue reçoit la vue d'un joueur).
- Quand : à chaque changement de la partie (inscription, étape, porte passée, vote, salle, points, connexion, pause,
  chrono, réglage), et en réponse à l'action `view`. **La vue est toujours complète** : il n'y a pas de différentielle
  (`EventsTrahisonPatch` est réservé, voir plus bas). Le client remplace son état par chaque vue reçue.
- Hub sans partie : réponse à `view` seulement, `{ "hub": "...", "phase": "NONE" }` et rien d'autre.
- Les secrets : aucun vote de la manche en cours (avant `RESULTS`) n'est dans la vue d'un `PLAYER`, `GHOST` ou
  `SPECTATOR`, à part le vote de l'expéditeur lui-même (`viewer.own_vote`). Le journal de la partie n'est dans aucune vue.

Champs (la colonne « Null » dit quand la valeur est `null`) :

| Champ | Type | Null / notes |
|---|---|---|
| `hub` | texte | Nom du hub |
| `game_id` | texte (UUID) | |
| `phase` | texte | `LOBBY`, `PROLOGUE`, `DOORS`, `ROOMS`, `FREE_TIME`, `VOTE`, `RESULTS`, `FINAL`, `ENDED` |
| `round` | entier | 0 avant la première manche (`LOBBY`, `PROLOGUE`), puis 1 à 3 ; reste 3 pendant la porte finale |
| `final_loop` | booléen | Vrai si la manche en cours est un vote de la boucle de la porte finale |
| `final_loops` | entier | Nombre de boucles de vote de la porte finale commencées |
| `mode` | texte | `AUTO` ou `GAME_MASTER` |
| `deadline` | entier (ms epoch) | Fin de l'étape ; `null` en `LOBBY`, `ENDED` et en pause |
| `paused` | booléen | |
| `auto_paused` | booléen | Vrai si la pause est venue de la déconnexion d'un joueur vivant (mode `AUTO`) |
| `remaining_ms` | entier | Temps restant de l'étape ; `null` en `LOBBY` et `ENDED` |
| `server_time` | entier (ms epoch) | Heure du serveur à l'envoi, pour corriger l'écart d'horloge |
| `players` | liste | Les inscrits, dans l'ordre d'inscription (voir ci-dessous) ; vide avant la première inscription |
| `bracelets` | liste | Vide jusqu'au démarrage, puis 9 éléments (voir ci-dessous) |
| `doors` | liste de 3 | Les trois portes : `{ "index": 0..2, "colors": ["RED","GREEN"] }`. Constantes : 0 = `RED`-`GREEN`, 1 = `RED`-`BLUE`, 2 = `GREEN`-`BLUE` |
| `groups` | liste de 3 ou `null` | `null` tant que la répartition de la manche n'est pas connue (jusqu'à la fermeture des portes) ; sinon un élément par porte (voir ci-dessous) |
| `doors_passed` | liste de 9 entiers ou `null` | `null` avant la première manche. Pour chaque bracelet, l'index de la porte (0 à 2) qu'il a passée dans la manche en cours, ou -1 |
| `viewer` | objet | Qui reçoit la vue (voir ci-dessous) |
| `results` | objet ou `null` | Les résultats de la dernière manche **dont les votes sont comptés** (voir ci-dessous) ; `null` avant les premiers résultats |
| `winners` | liste d'entiers ou `null` | `null` tant que la partie n'est pas finie ; en `ENDED`, les index des joueurs qui ont passé la porte finale (liste vide si le maître du jeu a terminé la partie) |
| `gm` | objet ou `null` | Présent seulement pour `viewer.kind == "GM"` |

`players[]` : un élément par inscrit.

| Champ | Type | Notes |
|---|---|---|
| `index` | entier | Rang d'inscription, 0 à 8 ; sert partout comme identifiant du joueur et de son bracelet de poignet |
| `name` | texte | Nom Minecraft (« Bot 1 »… pour un factice) |
| `uuid` | texte (UUID) | Faux pour un factice |
| `connected` | booléen | |
| `alive` | booléen | Faux = fantôme. Les morts sont révélés aux résultats (étape T1) |
| `is_bot` | booléen | Joueur factice |

`bracelets[]` : le bracelet `i` est celui du poignet du joueur `i`.

| Champ | Type | Notes |
|---|---|---|
| `index` | entier | 0 à 8 |
| `points` | entier | |
| `color` | texte ou `null` | `RED`, `GREEN` ou `BLUE` pour la manche en cours ; `null` avant la première manche |
| `status` | texte ou `null` | `PAIR` ou `SOLO` ; `null` avant la première manche |
| `holder` | entier ou `null` | Index du joueur qui le porte ; `null` s'il traîne sur un corps (étape T6, jamais en T1) |
| `owner_alive` | booléen | Faux quand son porteur d'origine est mort : il vote alors toujours `ALLY` |

`groups[]` (une fois la répartition connue) : le groupe qui passe la porte `door`.

| Champ | Type | Notes |
|---|---|---|
| `door` | entier | 0 à 2, index dans `doors` |
| `pair_color` | texte | Couleur de la paire |
| `solo_color` | texte | Couleur du solo |
| `pair` | liste de 2 entiers | Index des deux bracelets de la paire (croissants) |
| `solo` | entier | Index du bracelet solo |
| `room_solved` | booléen | La salle du groupe est résolue |

`viewer` : le destinataire.

| Champ | Type | Notes |
|---|---|---|
| `kind` | texte | `PLAYER`, `GHOST`, `SPECTATOR`, `GM` |
| `index` | entier ou `null` | Son index de joueur ; `null` pour `SPECTATOR` et `GM` |
| `group` | entier ou `null` | La porte de son groupe (0 à 2) ; `null` tant que la répartition n'est pas connue, ou s'il ne joue pas |
| `side` | texte ou `null` | `PAIR` ou `SOLO` dans son groupe ; mêmes cas de `null` que `group` |
| `door_passed` | entier ou `null` | La porte qu'il a passée dans la manche en cours |
| `side_voted` | booléen | Son camp (paire ou solo) a déjà voté. Il ne dit **rien** du vote de l'autre camp |
| `own_vote` | texte ou `null` | `ALLY` ou `BETRAY` : le vote qu'**il a lui-même** déposé. `null` s'il n'a pas voté, ou si son partenaire de paire a voté pour la paire (il n'apprend pas le sens du vote) |

`results` : la dernière manche comptée. Elle reste affichée pendant les étapes suivantes, jusqu'aux résultats suivants.

| Champ | Type | Notes |
|---|---|---|
| `round` | entier | Numéro de la manche : 1 à 3, puis 4, 5… pour les boucles de la porte finale |
| `final_loop` | booléen | |
| `votes` | liste de 3 | Un élément par porte, voir ci-dessous |
| `deltas` | liste de 9 entiers | Variation de points de chaque bracelet (avant le plancher à 0) |
| `newly_dead` | liste d'entiers | Index des joueurs devenus fantômes à ces résultats |

`results.votes[]` : `{ door, pair_color, solo_color, pair, solo }` comme `groups[]`, plus `pair_vote` et `solo_vote`
(`ALLY` ou `BETRAY`, `ALLY` si personne n'a voté), `pair_cast` et `solo_cast` (faux si le vote est le « Allier » par
défaut), `pair_voter` (index du membre dont le vote a engagé la paire, `null` si personne).

`gm` (maître du jeu seulement) :

| Champ | Type | Notes |
|---|---|---|
| `votes` | liste de 3 ou `[]` | Votes déposés dans la manche en cours, un élément par porte : `{ door, pair_vote, pair_voter, solo_vote }`. `pair_vote` et `solo_vote` valent `ALLY`, `BETRAY` ou `null` (pas encore). Liste vide tant que la répartition n'est pas connue |
| `settings` | objet | `{ mode, durations: { "PROLOGUE": 600, ... }, no_door_penalty }` : durées en secondes de chaque étape chronométrée |
| `bot_policy` | texte | `ALLY`, `BETRAY` ou `RANDOM` |
| `seed` | entier 64 bits | Graine de la partie |
| `game_masters` | liste de textes (UUID) | Les admins qui dirigent la partie |

Exemple (vue d'un joueur pendant le vote de la manche 2 ; deux joueurs et trois bracelets seulement, pour la place) :

```json
{
  "type": "EventsTrahisonView",
  "data": {
    "hub": "trahison", "game_id": "6f1c2e0a-8f0e-4a43-9a3b-2b1f7a6c9d10", "phase": "VOTE", "round": 2,
    "final_loop": false, "final_loops": 0, "mode": "AUTO",
    "deadline": 1794797005445, "paused": false, "auto_paused": false, "remaining_ms": 212000,
    "server_time": 1794796793445,
    "players": [
      { "index": 0, "name": "Alice", "uuid": "8a3efaf0-a823-45f5-8913-bc699da09809", "connected": true, "alive": true, "is_bot": false },
      { "index": 1, "name": "Bot 1", "uuid": "4aae22fb-222c-315b-b9b4-3c8463614971", "connected": true, "alive": true, "is_bot": true }
    ],
    "bracelets": [
      { "index": 0, "points": 5, "color": "RED", "status": "PAIR", "holder": 0, "owner_alive": true },
      { "index": 1, "points": 3, "color": "GREEN", "status": "SOLO", "holder": 1, "owner_alive": true }
    ],
    "doors": [
      { "index": 0, "colors": ["RED", "GREEN"] },
      { "index": 1, "colors": ["RED", "BLUE"] },
      { "index": 2, "colors": ["GREEN", "BLUE"] }
    ],
    "groups": [
      { "door": 0, "pair_color": "RED", "solo_color": "GREEN", "pair": [0, 4], "solo": 1, "room_solved": true },
      { "door": 1, "pair_color": "BLUE", "solo_color": "RED", "pair": [3, 6], "solo": 8, "room_solved": false },
      { "door": 2, "pair_color": "GREEN", "solo_color": "BLUE", "pair": [2, 5], "solo": 7, "room_solved": true }
    ],
    "doors_passed": [0, 0, 2, 1, 0, 2, 1, 2, 1],
    "viewer": { "kind": "PLAYER", "index": 0, "group": 0, "side": "PAIR", "door_passed": 0, "side_voted": true, "own_vote": "ALLY" },
    "results": {
      "round": 1, "final_loop": false,
      "votes": [
        { "door": 0, "pair_color": "RED", "solo_color": "GREEN", "pair": [0, 3], "solo": 8, "pair_vote": "ALLY", "pair_cast": true, "pair_voter": 3, "solo_vote": "BETRAY", "solo_cast": true }
      ],
      "deltas": [-2, 0, 2, -2, 0, 2, 0, 0, 3],
      "newly_dead": []
    },
    "winners": null,
    "gm": null
  }
}
```

(Dans un vrai message, `players`, `bracelets` et `results.votes` ont 9, 9 et 3 éléments.)

La vue du maître du jeu ajoute, à la place de `"gm": null` :

```json
"gm": {
  "votes": [
    { "door": 0, "pair_vote": "ALLY", "pair_voter": 0, "solo_vote": null },
    { "door": 1, "pair_vote": null, "pair_voter": null, "solo_vote": "BETRAY" },
    { "door": 2, "pair_vote": null, "pair_voter": null, "solo_vote": null }
  ],
  "settings": { "mode": "AUTO", "durations": { "PROLOGUE": 600, "DOORS": 300, "ROOMS": 1800, "FREE_TIME": 600, "VOTE": 300, "RESULTS": 180, "FINAL": 600 }, "no_door_penalty": -1 },
  "bot_policy": "RANDOM",
  "seed": 7,
  "game_masters": ["0d5c7a52-1b7e-4c76-8d02-3f6b1a9e4c11"]
}
```

- Erreurs : aucune (une vue ne se refuse pas ; sans partie, la réponse à `view` est la vue `NONE`).

### `EventsTrahisonPatch`

- Sens : S2C.
- **Réservé, jamais envoyé en T1.** L'état d'une partie est petit : le backend envoie toujours des `EventsTrahisonView`
  complètes. Le type reste réservé pour des changements ciblés si une étape ultérieure en a besoin (par exemple l'état
  d'une salle) ; le client l'ignore pour l'instant.

### `EventsTrahisonSay`

- Sens : S2C.
- Qui le reçoit : selon le genre. `announce` : les mêmes destinataires que les vues (joueurs, maîtres du jeu,
  spectateurs). `error` : l'expéditeur de la demande refusée, et lui seul.
- Champs : `kind` (texte) puis les champs du genre.

Genre `announce` : une réplique de l'animatrice (Mystaria, le premier fantôme), envoyée **une fois à chaque changement
d'étape** (et non à une pause, une reprise ou une correction), juste après la vue de la nouvelle étape. Le texte n'est pas
dans le message : le client traduit la clé.

| Champ | Type | Notes |
|---|---|---|
| `kind` | texte | `announce` |
| `key` | texte | `phantasmon_events.trahison.say.phase.<étape en minuscules>` : `lobby`, `prologue`, `doors`, `rooms`, `free_time`, `vote`, `results`, `final`, `ended` |
| `phase` | texte | L'étape, en majuscules |
| `round` | entier | La manche |

```json
{ "type": "EventsTrahisonSay", "data": { "kind": "announce", "key": "phantasmon_events.trahison.say.phase.vote", "phase": "VOTE", "round": 2 } }
```

Genre `error` : une demande refusée (voir « Codes d'erreur »).

| Champ | Type | Notes |
|---|---|---|
| `kind` | texte | `error` |
| `code` | texte | `ERROR_TRAHISON_…` |

```json
{ "type": "EventsTrahisonSay", "data": { "kind": "error", "code": "ERROR_TRAHISON_WRONG_PHASE" } }
```

Genres prévus pour plus tard : `whisper` (chuchotement privé, étapes T6 et T7).

- Erreurs : sans objet.

### `EventsTrahisonAction`

- Sens : C2S.
- Qui l'envoie : tout joueur connecté, selon l'action (voir la section 3).
- Champs : `action` (texte, obligatoire), `hub` (texte, voir les conventions) et les champs propres à l'action.
- Réponse : une vue à chaque changement (envoyée à tous, pas seulement à l'expéditeur), ou `EventsTrahisonSay` d'erreur à
  l'expéditeur si la demande est refusée.

```json
{ "type": "EventsTrahisonAction", "data": { "action": "vote", "vote": "betray" } }
```

### `EventsTrahisonGm`

- Sens : C2S.
- Qui l'envoie : un **admin** (la liste des admins de Phantasmon, `AdminService`). Un autre reçoit
  `ERROR_TRAHISON_ADMIN_REQUIRED` et rien ne change. L'admin qui envoie un message `Gm` à une partie devient maître du
  jeu de cette partie (il reçoit la vue `GM`, sauf s'il y joue).
- Champs : `action` (texte, obligatoire), `hub` (obligatoire pour `create`, et tant qu'il ne dirige aucune partie), et les
  champs propres à l'action (voir la section 3).
- Réponse : une vue à chaque changement, ou une erreur à l'expéditeur.

```json
{ "type": "EventsTrahisonGm", "data": { "action": "add_bots", "hub": "trahison", "count": 7, "policy": "ally" } }
```

### `EventsTrahisonRoom`

- Sens : S2C.
- Qui le reçoit : les membres du groupe qui joue la salle, les spectateurs et fantômes, les maîtres du jeu. Les deux premiers
  reçoivent le même contenu ; il ne contient jamais une solution.
- Quand : au début de la salle, à chaque étape ouverte ou résolue, à chaque objet ramassé, à l'ouverture de la sortie, à la
  remise à zéro.
- Champs :

| Champ | Type | Notes |
|---|---|---|
| `hub`, `group` | texte, entier | Le hub et le groupe (0 à 2) qui joue |
| `room`, `name`, `version` | texte, texte, entier | La définition utilisée |
| `members` | liste d'entiers | Indices des joueurs du groupe |
| `started_at`, `last_progress_at`, `finished_at` | ISO-8601, `finished_at` `null` tant que non finie | `last_progress_at` : dernière étape résolue ou objet ramassé |
| `exit_open` | booléen | Toutes les étapes sont résolues : la sortie est ouverte |
| `steps_done`, `steps_total` | entiers | |
| `steps` | liste | `{ id, open, solved }` ; le maître du jeu y ajoute `mechanism` et `hints_given` |
| `slots` | liste | Les emplacements **actifs** seulement (voir ci-dessous) |
| `inventory` | liste de textes | Identifiants des objets du sac commun du groupe |

Un emplacement : `{ id, kind, position {x,y,z}, orientation, size {x,y,z}, shows: [...] }`, la position étant relative à
l'origine de la place de la salle. `shows` dit ce qu'il montre maintenant :

| `type` | Champs | Sens |
|---|---|---|
| `puzzle` | `step`, `role`, `mechanism`, `solved` | Un mécanisme s'y utilise (étape ouverte ou résolue). `use_slot` l'ouvre |
| `clue` | `step`, `clue { role, type, value }` | Un indice visible. `type` : `text`, `digits` (`value` : texte de chiffres), `symbols` ou `colors` (`value` : liste de textes), `image` (identifiant), `item` (identifiant d'objet, ramassable par `pick_up`) |
| `exit` | `open` | L'emplacement de sortie |

Un indice devient visible quand son étape est ouverte (`gives[].when` = `open`, par défaut) ou résolue (`solved`). Un objet
ramassé disparaît de l'emplacement.

```json
{ "type": "EventsTrahisonRoom", "data": { "hub": "hall", "group": 1, "room": "atelier", "name": "Atelier", "version": 2,
  "members": [0, 3, 5], "started_at": "2026-11-16T20:00:00Z", "last_progress_at": "2026-11-16T20:04:10Z", "finished_at": null,
  "exit_open": false, "steps_done": 1, "steps_total": 3,
  "steps": [ { "id": "s1", "open": true, "solved": true }, { "id": "s2", "open": true, "solved": false }, { "id": "s3", "open": false, "solved": false } ],
  "slots": [
    { "id": "dials", "kind": "terminal", "position": { "x": 10, "y": 1, "z": 4 }, "orientation": "north", "size": { "x": 1, "y": 1, "z": 1 },
      "shows": [ { "type": "puzzle", "step": "s2", "role": "dials", "mechanism": "three_dials", "solved": false } ] },
    { "id": "wall", "kind": "wall_clue", "position": { "x": 20, "y": 2, "z": 0 }, "orientation": "north", "size": { "x": 1, "y": 1, "z": 1 },
      "shows": [ { "type": "clue", "step": "s2", "clue": { "role": "code", "type": "digits", "value": "371" } } ] } ],
  "inventory": [] } }
```

- Erreurs : aucune (message d'état).

### `EventsTrahisonPuzzle`

- Sens : S2C.
- Qui le reçoit : selon `reason` (ci-dessous). Les clients qui ont l'écran du mini-jeu ouvert se recalent dessus ; les autres
  ignorent le message.
- Quand : un joueur ouvre l'écran (`open`, à lui seul), un geste est appliqué (`input`), l'étape est résolue (`solved`), la salle
  est remise à zéro (`reset`).
- Champs :

| Champ | Type | Notes |
|---|---|---|
| `hub`, `group` | texte, entier | |
| `step`, `mechanism` | texte | L'étape et son mécanisme (le client choisit l'écran d'après `mechanism`) |
| `reason` | texte | `open`, `input`, `solved`, `reset` |
| `solved` | booléen | |
| `view` | objet | La vue du mécanisme : positions courantes, **jamais la solution** (propre à chaque mécanisme, voir ci-dessous) |
| `last` | objet ou `null` | `{ player, events: [...] }` : le dernier geste, pour animer le curseur de ce joueur chez les autres ; `null` pour `open` et `reset` |

Vues des mécanismes d'essai : `debug_button` `{ pressed }` ; `three_dials` `{ dials: [a, b, c], solved }` (les molettes de 0 à 9 ;
le maître du jeu reçoit en plus `target`). Événements : `debug_button` `{ "type": "press" }` ; `three_dials`
`{ "type": "dial", "dial": 1, "value": 7 }`.

```json
{ "type": "EventsTrahisonPuzzle", "data": { "hub": "hall", "group": 1, "step": "s2", "mechanism": "three_dials", "reason": "input",
  "solved": false, "view": { "dials": [3, 0, 9], "solved": false },
  "last": { "player": 5, "events": [ { "type": "dial", "dial": 0, "value": 3 } ] } } }
```

- Erreurs : aucune (message d'état).

### Messages prévus (à détailler par l'étape qui les crée)

`EventsTrahisonZone` (C2S). Contenu esquissé dans
`architecture.md` section 5.

## 3. Actions

Les actions des joueurs voyagent dans `EventsTrahisonAction` : `{ "action": "<nom>", ... }`. Celles du maître du jeu
voyagent dans `EventsTrahisonGm`. Dans les deux cas, `hub` suit les conventions de la section 1.

### Actions des joueurs (`EventsTrahisonAction`)

| `action` | Champs | Qui, quand | Effet |
|---|---|---|---|
| `view` | `hub` (si pas dans une partie) | Tout le monde, à tout moment. Sans partie dans le hub : la vue `NONE`. Pour un non-joueur, il faut être membre d'un hub | Renvoie la vue de l'expéditeur, et l'inscrit comme destinataire des vues de ce hub (spectateur). À envoyer à la connexion et à la reconnexion |
| `join` | `hub` | Membre d'un hub, en `LOBBY`, partie pas pleine | S'inscrit comme joueur (9 au plus). Son nom est celui de son dernier pseudo connu par Phantasmon |
| `leave` | | Un inscrit, en `LOBBY` | Se retire |
| `pass_door` | `door` : 0 à 2 | Un joueur vivant, en `DOORS`, qui n'a pas encore passé de porte | Passe cette porte avec son bracelet de poignet. Elle doit porter la couleur du bracelet et ne pas contredire les portes déjà prises (une seule porte suffit à décider de la répartition) |
| `vote` | `vote` : `ally` ou `betray` | Un joueur vivant, en `VOTE`, dont le groupe est connu | Vote pour son camp. **Une paire n'a qu'un vote** : le premier de ses membres qui vote engage les deux, le second reçoit `ERROR_TRAHISON_ALREADY_VOTED`. Le solo vote une fois. L'expéditeur voit son vote dans `viewer.own_vote` ; les autres n'apprennent que `side_voted` |
| `open_final_door` | | Un joueur vivant, en `FINAL`, dont le bracelet de poignet lit 9 points ou plus | Ouvre la porte finale : la partie passe à `ENDED`, `winners` = tous les vivants dont le bracelet lit 9 ou plus |
| `debug_solve` | | Un joueur vivant, en `ROOMS` | Provisoire (jusqu'à l'étape T3) : le groupe de l'expéditeur résout sa salle simulée |

Toutes les actions de jeu sont refusées en pause (`ERROR_TRAHISON_PAUSED`), aux fantômes (`ERROR_TRAHISON_DEAD`) et à qui ne
joue pas (`ERROR_TRAHISON_NOT_A_PLAYER`), hors de l'étape prévue (`ERROR_TRAHISON_WRONG_PHASE`).

### Actions du maître du jeu (`EventsTrahisonGm`)

| `action` | Champs | Effet |
|---|---|---|
| `create` | `hub` ; facultatifs : `seed` (entier), `mode` (`AUTO` ou `GAME_MASTER`), `fast` (entier : toutes les étapes durent ce nombre de secondes ; `true` = 10), `settings` (`{ mode, durations: { "VOTE": 120, ... }, no_door_penalty }`, durées en secondes par étape, `no_door_penalty` ≤ 0) | Crée la partie du hub en `LOBBY` et en fait le maître du jeu. Refusée si le hub a une partie non finie. Graine tirée au hasard si absente. Une partie finie ne bloque pas une nouvelle |
| `start` | | `LOBBY` → `PROLOGUE`. Exactement 9 inscrits (les factices comptent) |
| `pause` / `resume` | | Gèle / relance le chrono. `pause` sur une pause automatique la reprend à son compte (seul `resume` la lève ensuite) |
| `next` | | Force l'étape suivante tout de suite |
| `end` | | Termine la partie sur-le-champ (`winners` vide) |
| `extend` | `seconds` (entier, négatif pour raccourcir) | Ajoute du temps à l'étape en cours |
| `set_points` | `bracelet` : 0 à 8, `points` ≥ 0 | Corrige les points d'un bracelet (la mort n'est vérifiée qu'aux prochains résultats) |
| `add_bots` | `count` (entier ; absent ou ≤ 0 : remplit toutes les places libres), `policy` (facultatif) | En `LOBBY` seulement : ajoute des joueurs factices « Bot n » avec de faux UUID ; refusé s'il n'y a pas assez de places (`ERROR_TRAHISON_GAME_FULL`) |
| `set_bot_policy` | `policy` : `ally`, `betray` ou `random` | Règle le vote de tous les factices (défaut `random`) |

#### Les joueurs factices

Ils agissent par les mêmes opérations que les joueurs, après un délai tiré de la graine (10 à 60 % du temps de l'étape) :
`DOORS` : la porte que la répartition leur laisse, compatible avec les portes déjà prises (sans porte prise, la graine
choisit la répartition, la même pour tous les factices) ; `ROOMS` : `debug_solve` ; `VOTE` : le vote de la consigne ;
`FINAL` : `open_final_door` si leur bracelet lit 9. Ils ne reçoivent aucun message. Une pause les arrête, la reprise les
relance.

#### Déconnexion

Quand le WebSocket d'un joueur se ferme, il passe à `connected = false` et la partie continue. **En mode `AUTO`**, si c'est
un joueur vivant et que la partie tourne, le chrono se met en pause tout seul (`paused` et `auto_paused` vrais) pendant
cinq minutes au plus : la pause se lève dès que tous les joueurs vivants sont revenus, ou après cinq minutes (alors la
partie repart même si le joueur est encore absent, sans nouvelle pause pour lui). Un maître du jeu peut reprendre la main
(`pause` puis `resume`). En mode `GAME_MASTER`, aucune pause automatique.

### Actions de salle (étape T3.1)

Dans `EventsTrahisonAction` (`hub` comme pour les autres actions). Seuls les joueurs du groupe qui joue la salle (étape `ROOMS`) peuvent les envoyer ; une salle
finie (sortie ouverte) n'en accepte plus.

| Action | Champs | Effet | Réponse |
|---|---|---|---|
| `use_slot` | `slot` (texte) | Utiliser un emplacement actif. Si une étape ouverte et non résolue y est liée, son écran s'ouvre : `EventsTrahisonPuzzle` (`reason: open`) à ce joueur seul ; un mécanisme qui réagit à l'usage direct (levier, bouton) est actionné | Aucune réponse propre : `EventsTrahisonPuzzle` et `EventsTrahisonRoom` |
| `puzzle_input` | `step` (texte), `input` (objet propre au mécanisme) | Un geste sur l'écran du mini-jeu : `three_dials` attend `{ "dial": 0..2, "delta": -9..9 }`. Appliqué dans l'ordre d'arrivée | `EventsTrahisonPuzzle` (`input`, puis `solved` si résolu) à tous ceux qui regardent ; `EventsTrahisonRoom` si une étape est résolue |
| `pick_up` | `slot` (texte) | Ramasser les objets (`type: item`) visibles dans l'emplacement : ils vont dans l'inventaire commun du groupe | `EventsTrahisonRoom` |

```json
{ "type": "EventsTrahisonAction", "data": { "action": "puzzle_input", "step": "s2", "input": { "dial": 0, "delta": 3 } } }
```

Erreurs : `ERROR_TRAHISON_NO_ROOM`, `ERROR_TRAHISON_NOT_IN_ROOM`, `ERROR_TRAHISON_BAD_ACTION`, `ERROR_TRAHISON_BAD_INPUT`,
`ERROR_TRAHISON_SLOT_INACTIVE`, `ERROR_TRAHISON_STEP_NOT_OPEN`, `ERROR_TRAHISON_STEP_SOLVED`, `ERROR_TRAHISON_NOTHING_TO_PICK`,
`ERROR_TRAHISON_ROOM_FINISHED` (section 5).

## 4. Routes HTTP

Préfixe `/events/trahison/…` (définitions de salles, schématiques, plan). Chaque route ajoutée est aussi reportée
dans [`../backend-reference.md`](../backend-reference.md). Les champs sont en `snake_case`.

### 4.1 Le plan fixe (étape T2.1)

| Méthode | Route | Accès | Effet |
|---|---|---|---|
| `GET` | `/events/trahison/layout/{hub}` | Tout joueur connecté | Dernière version du plan du hub. Un hub sans plan donne `version: 0` et des listes vides (pas de 404). Le plan n'a pas de secret : les clients s'en servent pour connaître leur zone |
| `PUT` | `/events/trahison/layout/{hub}` | Admin | Enregistre une **nouvelle version** (le numéro monte de 1 à chaque enregistrement ; l'historique est gardé) |
| `POST` | `/events/trahison/layout/{hub}` | Admin | Identique au `PUT`, parce que l'aide HTTP de Phantasmon (`PhantasmonApi`) n'a ni `put` ni `patch` : le client de l'éditeur utilise ce `POST` |

Toutes les positions sont en **coordonnées de hub** : `x` et `z` du hub, `y` du monde. Une boîte (`box`) donne ses deux
coins **inclus** : `{ "min_x", "min_y", "min_z", "max_x", "max_y", "max_z" }`. Une position : `{ "x", "y", "z" }`.

Corps du `PUT` / `POST` (et réponse du `GET`, qui ajoute `hub`, `version` et `updated_at`) :

```json
{
  "base_version": 3,
  "draft": false,
  "zones": [
    { "id": "hall", "type": "hall", "boxes": [ { "min_x": -10, "min_y": 64, "min_z": -10, "max_x": 10, "max_y": 72, "max_z": 10 } ] }
  ],
  "doors": [
    { "id": "door_1_rg", "type": "color", "block": { "min_x": 11, "min_y": 64, "min_z": 0, "max_x": 11, "max_y": 66, "max_z": 2 },
      "zone_a": "doors_1", "zone_b": "slot_1_a", "round": 1, "colors": [ "red", "green" ] }
  ],
  "room_slots": [ { "id": "place_1_a", "round": 1, "origin": { "x": 40, "y": 64, "z": -24 }, "orientation": "north" } ],
  "cabins": [ { "id": "cabin_rg_pair", "serves": "red_green", "side": "pair", "zone": "cabin_rg_pair", "door": "cabin_door_rg_pair" } ],
  "hiding_spots": [ { "id": "spot_1", "position": { "x": 3, "y": 65, "z": -4 } } ]
}
```

| Champ | Type | Notes |
|---|---|---|
| `base_version` | entier | Facultatif, requête seulement : la version que l'éditeur avait chargée. Si une plus récente existe : `409 ERROR_TRAHISON_LAYOUT_CONFLICT` (`details.version` donne la version actuelle) |
| `draft` | booléen | Brouillon : saute les contrôles de complétude (voir ci-dessous). Un plan non brouillon est celui que le jeu utilisera |
| `zones[].type` | texte | `hall`, `corridor`, `doors`, `room_slot`, `vote`, `cabin`, `final`, `alcove`. Plusieurs boîtes permises par zone |
| `doors[].type` | texte | `color`, `room_exit`, `cabin`, `final`, `plain`. `block` : la boîte des faux blocs. `zone_a`, `zone_b` : les deux zones reliées |
| `doors[].round`, `doors[].colors` | entier, deux textes | Portes `color` seulement : manche 1 à 3 ; deux noms de couleur de `GameColor` en minuscules (`red`, `green`, `blue`), dans n'importe quel ordre |
| `room_slots[]` | | `round` 1 à 3 ; `origin` : le coin minimum de la place (48 x 48 x 24) ; `orientation` : `north`, `east`, `south`, `west` |
| `cabins[]` | | `serves` : la paire de couleurs dont elle sert le vote (`red_green`, `red_blue`, `green_blue`) ; `side` : `pair` ou `solo` ; `zone` : une zone de type `cabin` ; `door` : une porte de type `cabin` |
| `hiding_spots[]` | | Cachette des objets du temps libre |

**Pourquoi une cabine sert une paire de couleurs et non une porte précise.** La salle de vote est unique et sert aux trois
manches ; chaque vote oppose, pour une porte (une paire de couleurs), la paire de joueurs au solo. Il y a donc 3 portes
(paires de couleurs) x 2 côtés = 6 cabines, quelle que soit la manche. Les 9 portes de couleur du plan (3 par manche)
portent chacune leur paire de couleurs et leur manche.

**Validation à l'enregistrement.** Toutes les erreurs sont renvoyées d'un coup : `400 ERROR_TRAHISON_LAYOUT_INVALID` avec
`details.errors`, une liste de `{ "code", "ids", "info" }` (`ids` : les éléments concernés ; `info` : un complément
lisible par machine, par exemple `round=2;missing=red_blue`).

| Niveau | Contrôles | Brouillon |
|---|---|---|
| Structure (toujours) | Identifiants valides (`[A-Za-z0-9_-]`, 1 à 40) et uniques par genre ; types connus ; boîtes bien formées (minimum <= maximum) ; manches de 1 à 3 ; couleurs valides et différentes ; **aucune zone ne chevauche une autre** (des boîtes qui se touchent ne se chevauchent pas ; les boîtes d'une même zone peuvent se recouvrir) | Contrôlée |
| Complétude | Champs obligatoires remplis ; chaque référence (zones d'une porte, zone et porte d'une cabine) existe et a le bon type ; par manche, exactement 3 portes de couleur, une par paire de couleurs ; 6 cabines, une par paire de couleurs et par côté ; 3 places par manche (9) ; exactement une porte finale | Sautée |

Un hub reçoit au plus une écriture à la fois par numéro de version (clé primaire `hub, version`) : deux enregistrements
simultanés donnent un `409 ERROR_TRAHISON_LAYOUT_CONFLICT` au second.

### 4.2 Commande et touche de l'éditeur (client)

- `/phantasmon event trahison edit` : active ou coupe l'éditeur du plan (admins de Phantasmon, dans un hub rejoint).
  `/phantasmon event trahison ping` (et la commande seule) : le Ping de l'étape T0.
- Touche **K** par défaut (réglable dans les commandes, catégorie « Phantasmon Events », `key.phantasmon_events.trahison_edit`) :
  ouvre le panneau de l'éditeur quand il est actif.
- Clic gauche : coin A ; clic droit : coin B (le bloc visé, ou en étant accroupi le bloc devant la face visée, pour
  une ouverture). Ces clics n'atteignent pas le serveur tant que l'éditeur est actif.

### 4.3 Les salles (étape T3.1)

| Méthode | Route | Accès | Effet |
|---|---|---|---|
| `GET` | `/events/trahison/rooms` | Tout joueur connecté | Liste : `[{ id, name, author, version, difficulty, expected_seconds, step_count, slot_count, has_schematic, updated_at }]` |
| `GET` | `/events/trahison/rooms/{id}` | Tout joueur connecté | La définition (404 `ERROR_TRAHISON_ROOM_NOT_FOUND`) |
| `PUT` | `/events/trahison/rooms/{id}` | Admin | Crée ou remplace la salle. Le serveur numérote `version` (+1 à chaque enregistrement) et calcule `difficulty` et `expected_seconds` (sommes sur les étapes) : ceux du corps sont ignorés |
| `POST` | `/events/trahison/rooms/{id}` | Admin | Identique au `PUT` (le client n'a pas de `put`) |
| `DELETE` | `/events/trahison/rooms/{id}` | Admin | Supprime la définition et le fichier des blocs |
| `GET` | `/events/trahison/rooms/{id}/schematic` | Tout joueur connecté | Les blocs (`application/octet-stream`, format libre du client) ; 404 `ERROR_TRAHISON_ROOM_SCHEMATIC_MISSING` s'il n'y en a pas |
| `POST` / `PUT` | `/events/trahison/rooms/{id}/schematic` | Admin | Remplace les blocs (corps binaire, 16 Mio au plus, non vide ; la définition doit exister). `{ id, size }` |

Les blocs sont un fichier `<phantasmon.hub.schematics-dir>/trahison_rooms/<id>/room.bin` (écrit dans un fichier temporaire
puis déplacé), la définition est dans `trahison_rooms.data`. L'`id` est `[A-Za-z0-9_-]{1,40}` (`400 ERROR_TRAHISON_ROOM_BAD_ID`).

Corps d'une définition (`snake_case`) :

```json
{ "id": "atelier", "name": "Atelier", "author": "…",
  "slots": [ { "id": "dials", "kind": "terminal", "position": { "x": 10, "y": 1, "z": 4 }, "orientation": "north", "size": { "x": 1, "y": 1, "z": 1 } } ],
  "steps": [ { "id": "s2", "mechanism": "three_dials", "params": {}, "slots": { "dials": "dials" }, "needs": [ "s1" ],
               "gives": [ { "clue": "code", "slot": "wall", "when": "open" } ], "hints": [ "vague", "moins vague", "direct" ] } ],
  "secret": null }
```

`slots[].position` est relative à l'origine de la place, dans le gabarit 48 x 48 x 24 (x et z de 0 à 47, y de 0 à 23, la taille
incluse) ; `kind` : `terminal`, `keypad`, `pickup`, `wall_clue`, `document`, `lever`, `plate`, `pedestal`, `inner_door`, `exit`,
`secret` ; `orientation` : `north`, `east`, `south`, `west` ; `size` : un bloc par défaut. `steps[].slots` associe chaque rôle
du mécanisme à un emplacement (le rôle `exit` ouvre la sortie : son emplacement est de genre `exit`) ; `needs` : étapes à finir
avant ; `gives[].clue` : un rôle d'indice du mécanisme, `slot` : où il s'affiche, `when` : `open` (défaut) ou `solved` ; `hints` :
3 textes au plus, du plus vague au plus direct ; `secret` : l'emplacement de la cachette (facultatif).

**Validation** (400 `ERROR_TRAHISON_ROOM_INVALID`, `details.errors` : `[{ code, ids, info }]`, toutes les erreurs d'un coup) : voir
section 5. Mécanismes connus à ce jour : `debug_button` (un appui résout), `three_dials` (trois molettes de 0 à 9 ; indice
`code` de type `digits`).

## 5. Codes d'erreur

Préfixe `ERROR_TRAHISON_`. Tous sont envoyés par `EventsTrahisonSay` (genre `error`) à l'expéditeur seulement. Clé i18n :
`phantasmon_events.error.<code en minuscules>` (à ajouter dans `en_us.json` et `fr_fr.json` par le client, étape T1.4).

| Code | Canal | Cas | Clé i18n |
|---|---|---|---|
| `ERROR_TRAHISON_ADMIN_REQUIRED` | WS | `EventsTrahisonGm` envoyé par un non-admin | `phantasmon_events.error.error_trahison_admin_required` |
| `ERROR_TRAHISON_NO_HUB` | WS | `hub` absent, ou qui n'est pas un hub miroir, et l'expéditeur ne joue ni ne dirige aucune partie | `phantasmon_events.error.error_trahison_no_hub` |
| `ERROR_TRAHISON_NOT_IN_HUB` | WS | `join` ou `view` de quelqu'un qui n'est dans aucun hub | `phantasmon_events.error.error_trahison_not_in_hub` |
| `ERROR_TRAHISON_NO_GAME` | WS | Le hub n'a pas de partie (action autre que `view`, ou `Gm` autre que `create`) | `phantasmon_events.error.error_trahison_no_game` |
| `ERROR_TRAHISON_GAME_EXISTS` | WS | `create` alors que le hub a une partie non finie | `phantasmon_events.error.error_trahison_game_exists` |
| `ERROR_TRAHISON_BAD_ARGUMENT` | WS | Champ manquant ou invalide (`action`, `door`, `vote`, `policy`, `settings`…) | `phantasmon_events.error.error_trahison_bad_argument` |
| `ERROR_TRAHISON_UNKNOWN_ACTION` | WS | `action` inconnue | `phantasmon_events.error.error_trahison_unknown_action` |
| `ERROR_TRAHISON_NOT_IN_LOBBY` | WS | `join`, `leave`, `start` ou `add_bots` alors que la partie a commencé | `phantasmon_events.error.error_trahison_not_in_lobby` |
| `ERROR_TRAHISON_GAME_FULL` | WS | Neuf joueurs déjà, ou pas assez de places pour les factices | `phantasmon_events.error.error_trahison_game_full` |
| `ERROR_TRAHISON_ALREADY_JOINED` | WS | `join` d'un inscrit | `phantasmon_events.error.error_trahison_already_joined` |
| `ERROR_TRAHISON_NOT_A_PLAYER` | WS | Action de jeu de quelqu'un qui n'est pas inscrit, ou `leave` sans être inscrit | `phantasmon_events.error.error_trahison_not_a_player` |
| `ERROR_TRAHISON_NOT_ENOUGH_PLAYERS` | WS | `start` sans exactement 9 inscrits | `phantasmon_events.error.error_trahison_not_enough_players` |
| `ERROR_TRAHISON_WRONG_PHASE` | WS | Action hors de son étape, ou `next`, `pause`, `resume`, `extend`, `set_points` hors d'une partie en cours | `phantasmon_events.error.error_trahison_wrong_phase` |
| `ERROR_TRAHISON_PAUSED` | WS | Action de jeu pendant une pause, ou `pause` sur une pause du maître du jeu | `phantasmon_events.error.error_trahison_paused` |
| `ERROR_TRAHISON_NOT_PAUSED` | WS | `resume` alors que la partie n'est pas en pause | `phantasmon_events.error.error_trahison_not_paused` |
| `ERROR_TRAHISON_DEAD` | WS | Action de jeu d'un fantôme | `phantasmon_events.error.error_trahison_dead` |
| `ERROR_TRAHISON_WRONG_DOOR` | WS | `pass_door` : la couleur du bracelet n'est pas sur cette porte | `phantasmon_events.error.error_trahison_wrong_door` |
| `ERROR_TRAHISON_DOOR_INCOMPATIBLE` | WS | `pass_door` : cette porte contredit les portes déjà prises | `phantasmon_events.error.error_trahison_door_incompatible` |
| `ERROR_TRAHISON_ALREADY_PASSED` | WS | `pass_door` : une porte a déjà été passée dans cette manche | `phantasmon_events.error.error_trahison_already_passed` |
| `ERROR_TRAHISON_ALREADY_VOTED` | WS | `vote` : le camp (la paire entière, ou le solo) a déjà voté | `phantasmon_events.error.error_trahison_already_voted` |
| `ERROR_TRAHISON_CANNOT_OPEN_FINAL` | WS | `open_final_door` : le bracelet de poignet ne lit pas 9 | `phantasmon_events.error.error_trahison_cannot_open_final` |
| `ERROR_TRAHISON_GAME_ENDED` | WS | Action sur une partie finie | `phantasmon_events.error.error_trahison_game_ended` |
| `ERROR_TRAHISON_LAYOUT_*` | HTTP | Plan refusé, en conflit, hub invalide : voir la section 4.1 | (voir la section 4.1) |

Salles (étape T3.1) :

| Code | Canal | Cas | Clé i18n |
|---|---|---|---|
| `ERROR_TRAHISON_NO_ROOM` | action | Aucune salle en cours pour ce groupe | `phantasmon_events.error.*` |
| `ERROR_TRAHISON_NOT_IN_ROOM` | action | Le joueur n'est pas du groupe qui joue la salle | idem |
| `ERROR_TRAHISON_BAD_ACTION` | action | Action de salle mal formée ou étape inconnue | idem |
| `ERROR_TRAHISON_BAD_INPUT` | action | Geste refusé par le mécanisme (champ manquant, hors limites) | idem |
| `ERROR_TRAHISON_SLOT_INACTIVE` | action | Emplacement inconnu ou qui ne montre rien pour l'instant | idem |
| `ERROR_TRAHISON_STEP_NOT_OPEN` | action | L'étape attend encore d'autres étapes (`needs`) | idem |
| `ERROR_TRAHISON_STEP_SOLVED` | action | L'étape est déjà résolue | idem |
| `ERROR_TRAHISON_NOTHING_TO_PICK` | action | Rien à ramasser dans cet emplacement | idem |
| `ERROR_TRAHISON_ROOM_FINISHED` | action | La sortie est ouverte : la salle n'accepte plus d'action | idem |
| `ERROR_TRAHISON_ROOM_INVALID` | HTTP 400 | Définition refusée ; `details.errors` liste les problèmes ci-dessous | idem |
| `ERROR_TRAHISON_ROOM_NOT_FOUND` | HTTP 404 | Salle inconnue | idem |
| `ERROR_TRAHISON_ROOM_BAD_ID` | HTTP 400 ; dans `details.errors` | Identifiant hors `[A-Za-z0-9_-]{1,40}` (`info` : `room`, `slot` ou `step`) | idem |
| `ERROR_TRAHISON_ROOM_SCHEMATIC_MISSING` | HTTP 404 | La salle n'a pas encore de blocs | idem |
| `ERROR_TRAHISON_ROOM_SCHEMATIC_INVALID` | HTTP 400 | Blocs vides ou de plus de 16 Mio | idem |
| `ERROR_TRAHISON_ROOM_DUPLICATE_ID` | dans `details.errors` | Deux emplacements ou deux étapes de même identifiant | idem |
| `ERROR_TRAHISON_ROOM_BAD_KIND` | dans `details.errors` | Genre d'emplacement inconnu, ou rôle `exit` lié à un emplacement qui n'est pas de genre `exit` | idem |
| `ERROR_TRAHISON_ROOM_BAD_ORIENTATION` | dans `details.errors` | Orientation inconnue | idem |
| `ERROR_TRAHISON_ROOM_OUT_OF_BOUNDS` | dans `details.errors` | Emplacement (taille incluse) hors du gabarit 48 x 48 x 24 | idem |
| `ERROR_TRAHISON_ROOM_NO_STEPS` | dans `details.errors` | Aucune étape (ou plus de 24) | idem |
| `ERROR_TRAHISON_ROOM_UNKNOWN_MECHANISM` | dans `details.errors` | Mécanisme inconnu | idem |
| `ERROR_TRAHISON_ROOM_BAD_PARAMS` | dans `details.errors` | Paramètres refusés par le mécanisme (`info` : le détail) | idem |
| `ERROR_TRAHISON_ROOM_UNKNOWN_SLOT` | dans `details.errors` | Emplacement visé (rôle, `gives`, `secret`) inexistant | idem |
| `ERROR_TRAHISON_ROOM_UNKNOWN_STEP` | dans `details.errors` | `needs` vers une étape inexistante (ou elle-même) | idem |
| `ERROR_TRAHISON_ROOM_BAD_GIVE` | dans `details.errors` | `gives` : rôle d'indice inconnu du mécanisme, ou `when` autre que `open` / `solved` | idem |
| `ERROR_TRAHISON_ROOM_BAD_HINTS` | dans `details.errors` | Plus de 3 aides, ou une aide vide | idem |
| `ERROR_TRAHISON_ROOM_CYCLE` | dans `details.errors` | Les `needs` forment un cycle (`ids` : les étapes du cycle) | idem |
| `ERROR_TRAHISON_ROOM_UNREACHABLE` | dans `details.errors` | Une étape ne mène pas à la sortie | idem |
| `ERROR_TRAHISON_ROOM_NO_EXIT` | dans `details.errors` | Aucune étape au rôle `exit` et aucun emplacement de genre `exit` pour la dernière étape | idem |
