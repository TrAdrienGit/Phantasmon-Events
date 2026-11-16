# Protocole du jeu de trahison

> Référence unique de tous les messages et actions du jeu. Chaque étape y **ajoute** ce qu'elle crée (une section par
> message, une section par action) ; elle ne la contourne jamais. Vue d'ensemble : [`architecture.md`](architecture.md)
> section 5. État : étape T0 (Ping et Pong), T1.3 (View, Say, Action, Gm : moteur de partie), T2.1 (routes du plan), T3.1 (Room, Puzzle, actions de salle, branchées sur le moteur de partie). Patch et Zone restent à venir.

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
| `layout_version` | entier | Version du plan du hub que la partie utilise, fixée au démarrage (`start`) ; 0 si le hub n'avait pas de plan. Le client la lit pour récupérer ce plan (`GET /events/trahison/layout/{hub}?version=n`) |
| `doors_state` | objet | `{ "<id de porte du plan>": "OPEN" \| "CLOSED" }` : l'état de chaque porte **du plan**, tel que ce destinataire doit le voir (voir « États des portes » ci-dessous). `{}` si la partie n'a pas de plan |
| `cabins_required` | booléen | Les votes se font depuis une cabine fermée (T2.4). Faux si le réglage `cabins_required` est coupé **ou** si le plan n'a aucune cabine (hub sans plan) : le vote est alors libre |
| `cabins` | liste | Les cabines du plan, dans l'ordre du plan : `{ "id", "closed", "state", "window_until" }` (D4, D5). `state` : `FREE` (personne de son groupe n'y est), `WAITING` (un membre de son groupe y est entré et attend le partenaire d'une paire : la porte est fermée aux autres jusqu'à `window_until`, ms epoch du serveur), `LOCKED` (verrouillée : personne ne sort avant que le groupe ait voté), `RELEASED` (le groupe a voté : la porte s'ouvre pour ceux qui sont dedans). `closed` : la porte est fermée à ceux qui ne sont pas du groupe (tout sauf `FREE`) ; `window_until` : `null` hors de `WAITING`. Les mêmes valeurs pour tous les destinataires ; **qui est dedans n'est jamais donné** ; `[]` sans plan. Toutes `FREE` hors de `VOTE` |
| `doors_dead` | liste d'entiers | Les index des joueurs morts à la fermeture des portes de la manche en cours parce qu'ils n'avaient passé aucune porte de couleur (D2) ; `[]` sinon. Public |
| `game_masters` | liste | Les maîtres du jeu de la partie (D7) : `{ "uuid", "name" }`, dans l'ordre où ils ont été nommés ; `[]` s'il n'y en a pas (la partie se joue alors seule). Public, **sauf un maître du jeu qui joue** (D12, rôle secret) : il n'y figure que dans la vue des maîtres du jeu (ceux qui supervisent et celui qui joue) |
| `lobby_members` | liste | Seulement en `LOBBY` : les comptes autour du lobby (membres du hub) qui ne jouent pas, à qui un admin peut donner le rôle de maître du jeu : `{ "uuid", "name", "game_master" }` (D7) |
| `door_hints` | liste | Pendant `DOORS` : un élément par porte de couleur de la manche, ce que dit son seuil (voir « Seuils des portes de couleur » plus bas) ; `[]` dans les autres étapes. Les mêmes pour tous les destinataires |
| `final_door` | objet ou `null` | T2.5, la fenêtre de la porte finale pendant qu'elle court (D18, D21 : aussi sans porte dans le plan, pour le compte à rebours de tous) : `{ "open_until": ms epoch serveur, "openers": nombre de bracelets qu'elle laisse passer }` ; `null` le reste du temps. Le même pour tous les destinataires (qui sont les `openers` ne se dit pas : les points d'un bracelet sont privés). Si la porte est ouverte **pour ce destinataire**, c'est son `doors_state` qui le dit |
| `viewer` | objet | Qui reçoit la vue (voir ci-dessous) |
| `results` | objet ou `null` | Les résultats de la dernière manche **dont les votes sont comptés** (voir ci-dessous) ; `null` avant les premiers résultats |
| `winners` | liste d'entiers ou `null` | `null` tant que la partie n'est pas finie ; en `ENDED`, les index des joueurs qui ont **franchi** la porte finale pendant qu'elle était ouverte (T2.5 ; liste vide si personne n'est passé ou si le maître du jeu a terminé la partie) |
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
| `holder` | entier ou `null` | Index du joueur qui le porte ; `null` s'il traîne sur un corps (T6.1 : dès la mort de son porteur, tant que personne ne l'a pris ; sinon l'index de celui qui l'a pris). Pour qui ne connaît pas encore une mort, le champ montre le porteur d'origine (T1) |
| `owner_alive` | booléen | Faux quand son porteur d'origine est mort : il vote alors toujours `ALLY`. T6.1 : tant qu'un vivant ne connaît pas la mort (ni annoncée, ni corps vu), il lit `true`, comme `players[].alive` |

**Morts et corps (T6.1, T6.4)** : trois champs de plus dans la vue.

- `bodies` : les corps que ce destinataire peut connaître, `{player, zone, x, y, z, bracelet_on_body, cause}` (coordonnées du plan ; `cause` seulement pour le maître du jeu). Un fantôme et un maître du jeu les voient tous ; un vivant seulement les morts annoncées (portes, points : toujours, D2 ; un meurtre : si `announceDeaths`) et ceux dont il a vu le corps (`body_seen`, ou en ayant pris le bracelet).
- `viewer.carried` : les index des bracelets que ce joueur a pris sur des corps.
- `disconnected` : les joueurs vivants dont la connexion est coupée, `{player, zone, x, y, z}` (dernière position) ; ni bots, ni fantômes, ni le destinataire. Le client y pose un mannequin « déconnecté ».

Une mort (`GameService.kill` ou fin de manche) garde les points et la place dans le groupe (D13), fait voter le bracelet « Allier » par défaut, et rappelle `endIfGameMasterKilled` (D12).

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
| `my_cabin` | texte ou `null` | L'`id` de la cabine où il ferme et vote : celle qui sert la paire de couleurs de son groupe, du côté (paire ou solo) de son bracelet. `null` pour un non-joueur, ou tant que le groupe n'est pas connu |
| `side_voted` | booléen | Son camp (paire ou solo) a déjà voté. Il ne dit **rien** du vote de l'autre camp |
| `own_vote` | texte ou `null` | `ALLY` ou `BETRAY` : le vote qu'**il a lui-même** déposé. `null` s'il n'a pas voté, ou si son partenaire de paire a voté pour la paire (il n'apprend pas le sens du vote) |
| `can_roam` | booléen | D22 : vrai pour un joueur vivant qui est libre de visiter les salles hors du temps libre : l'étape est `ROOMS` ou `VOTE` et la salle de son groupe, dans la manche en cours, est terminée. Calculé par `GameService.canRoam` ; faux pour qui ne joue pas |
| `is_game_master` | booléen | D12 : vrai pour un maître du jeu (celui qui joue y compris, qui garde `kind` `PLAYER` et reçoit en plus le bloc `gm`) ; faux pour tous les autres, jamais révélé à un tiers |
| `can_open_final_door` | booléen | Vrai si son bracelet de poignet ouvre la porte finale : calculé par `rules.Outcome.canOpenFinalDoor` (bracelet à 9 points ou plus, joueur vivant). Faux pour qui ne joue pas. Il ne dit rien de l'étape : le client regarde aussi `phase` (`FINAL`). Aucun client ne connaît le seuil |

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
| `settings` | objet | `{ mode, durations: { "PROLOGUE": 600, ... }, door_open_seconds, final_door_seconds, cabin_partner_seconds, cabins_required }` : durées en secondes de chaque étape chronométrée ; `door_open_seconds` : le temps qu'une porte de couleur reste ouverte (défaut 6) ; `final_door_seconds` : le temps que la porte finale reste ouverte une fois le pupitre actionné (défaut 60, 1 à 120) ; `cabin_partner_seconds` : le temps que le partenaire d'une paire a pour rejoindre le premier entré dans la cabine (défaut 30) |
| `bot_policy` | texte | `ALLY`, `BETRAY` ou `RANDOM` |
| `seed` | entier 64 bits | Graine de la partie |
| `zones` | liste de 9 | Où est chaque joueur d'après son client : `{ index, zone, x, y, z }` (`zone` : identifiant de zone du plan, ou `null` ; `x`, `y`, `z` : coordonnées de hub, `null` tant qu'il n'a rien envoyé). Les zones ne sont dans **aucune autre vue** |

#### États des portes (`doors_state`)

Le backend décide quelle porte du plan est ouverte (une fonction pure de l'étape et de l'avancement des salles, `DoorStates`)
et le client n'affiche que cela : une porte `CLOSED` est remplie de faux blocs, une porte `OPEN` est laissée vide. Valeurs par
défaut, d'après le type de la porte du plan :

| Type | Par défaut |
|---|---|
| `color` | `CLOSED` ; pendant `DOORS`, `OPEN` quelques secondes quand les bracelets de son seuil la satisfont (voir « Seuils des portes de couleur ») |
| `room_exit` | `CLOSED` pendant `ROOMS` tant que la salle de son groupe n'est pas résolue, `OPEN` ensuite et dans toutes les autres étapes |
| `cabin` | `OPEN` ; pendant `VOTE`, `CLOSED` dès qu'un membre de son groupe y est entré (voir « Cabines et vote ») : fermée aux autres, mais ouverte pour les membres du groupe qui doivent encore la rejoindre (fenêtre du partenaire), pour ceux qui sont dedans une fois le groupe passé au vote, et pour un intrus déjà dedans (`DoorState.openFor`) ; de nouveau `OPEN` pour tous à la fin de `VOTE` |
| `final` | `CLOSED` ; quand un bracelet à 9 actionne le pupitre (T2.5, voir « La porte finale »), `CLOSED` pour tout le monde **sauf** les joueurs vivants dont le bracelet de poignet lit 9 ou plus, pour qui elle est `OPEN` pendant `final_door_seconds` (`DoorState.openFor`) ; **la fenêtre dure toujours tout son temps** (D19) |
| `plain` | `OPEN` |

Après la partie (`ENDED`), tout est `OPEN` (la porte finale comprise : la partie est finie, le hub se libère). Le maître du jeu peut forcer une porte avec l'action `set_door` : cela tient jusqu'au
changement d'étape, puis les valeurs par défaut reviennent. **À quel groupe appartient une porte `room_exit`** : le plan ne
le dit pas encore (le tirage des salles, étape T4, ne place pas encore les salles sur les places). Le groupe est celui dont la
porte de couleur mène à la même zone `room_slot` que la sortie (la manche et la paire de couleurs de cette porte de couleur
donnent la manche et le groupe, qui porte l'index de la porte dans `doors`). Sans ce lien (la sortie ne touche aucune zone
`room_slot` d'une porte de couleur), on prend l'ordre du plan : la i-ème porte `room_exit` est la place `i % 3` de la manche
`i / 3 + 1`. Une porte peut aussi n'être ouverte que pour certains joueurs (`DoorState.openFor` : la porte finale en T2.5, une cabine
tenue par un groupe) : la vue de chaque destinataire donne alors `OPEN` à ceux-là.

#### Cabines et vote (T2.4, D4, D5)

La salle de vote a six cabines (voir 4.1) : une par côté (paire ou solo) de chaque paire de couleurs. Quand `cabins_required` est
vrai (c'est le réglage par défaut), tout est décidé par le backend d'après les zones que les clients envoient
(`EventsTrahisonZone`) et les états de portes (`doors_state`), comme pour les seuils des portes de couleur :

1. **Entrer.** Pendant `VOTE`, un joueur vivant se rend dans **sa** cabine (`viewer.my_cabin` : la paire de couleurs de son
   groupe, son côté paire ou solo). Dès qu'un membre humain vivant de ce groupe, qui n'a pas encore voté, y entre, la cabine
   est tenue (`cabins[].state` ≠ `FREE`) :
   - un **solo** : elle se verrouille tout de suite (`LOCKED`) ;
   - une **paire** : `WAITING` pendant `cabin_partner_seconds` (30 s par défaut, réglage de `create`). La porte est fermée à
     tous ceux qui ne sont pas de la paire, ouverte aux deux membres. Si le partenaire entre dans le délai, la cabine se
     verrouille aussitôt (les deux sont dedans). Sinon, à la fin du délai, elle se verrouille avec celui qui est dedans, et le
     partenaire resté dehors ne peut plus entrer ni voter : on peut donc doubler son partenaire. Si le premier sort avant le
     verrouillage et que plus personne n'est dedans, la cabine redevient libre.
   Les membres morts ou factices du groupe ne sont pas attendus (les factices votent sans cabine).
2. **Verrouillée** : la porte est fermée pour tout le monde, ceux du groupe compris, qui ne peuvent pas sortir. Quelqu'un d'une
   autre cabine ou d'un autre groupe qui était déjà entré (une cabine libre n'est fermée à personne) garde la porte ouverte pour
   lui et peut ressortir ; il ne peut pas y voter (`WRONG_CABIN`). L'action `close_cabin` (touche V du client) verrouille tout de
   suite la cabine où l'on se tient quand c'est la sienne, sans attendre le partenaire ; sans effet si elle est déjà verrouillée.
3. **Voter** (`vote`) : refusé si le joueur n'est pas dans sa cabine (`NOT_IN_CABIN`, `WRONG_CABIN`) ou si elle n'est pas encore
   verrouillée (`CABIN_NOT_CLOSED`, par exemple pendant la fenêtre du partenaire). **Paire** : une seule voix, celle du premier
   des deux qui vote ; les deux, dedans, peuvent voter, le second reçoit `ALREADY_VOTED`.
4. **Sortir** (D5) : dès que le vote du groupe est déposé, la cabine passe `RELEASED` : sa porte s'ouvre pour ceux qui sont dedans
   et reste fermée aux autres tant que le groupe n'est pas sorti ; quand plus personne du groupe n'est dedans elle redevient
   libre (`FREE`, un groupe qui a voté ne la tient plus). Un joueur qui n'a pas voté sort **à la fin de `VOTE`** : à chaque début
   d'étape, toutes les cabines sont libérées (rien ne rend plus le maître du jeu nécessaire pour ouvrir).
5. **Absences** (D3) : un solo qui ne vote pas compte « Allier » ; dans une paire, si un membre est absent l'autre vote seul pour la
   paire ; si aucun des deux ne vote, « Allier ».

Exceptions : les joueurs factices votent sans cabine ; `cabins_required = false` (réglage de `create`, voir 3) redonne le vote libre
d'avant, sans aucun verrou ; un plan sans cabine (hub sans plan) n'exige rien non plus. L'état des cabines est gardé avec la partie
(`Game.cabinLocks`) : un redémarrage du backend le rétablit, et la fenêtre d'un partenaire encore attendu reprend.

Côté client (ni règle ni décision) : on entre simplement dans sa cabine. La touche **V** (`key.phantasmon_events.trahison_cabin`)
verrouille la cabine maintenant (pendant l'attente du partenaire) puis ouvre le **pupitre de vote** (`screen/VoteScreen`), qui s'ouvre
aussi tout seul dès que la vue dit que la cabine du joueur est verrouillée et qu'il est dedans. Une ligne du HUD (`hud/CabinPrompt`) dit
où aller, le temps qu'il reste au partenaire, quoi appuyer, que la cabine est verrouillée sans soi, ou qu'on peut sortir.

#### Tableau des résultats (T2.4)

Pendant `RESULTS`, le client affiche `results` (voir plus haut) sur le **tableau des résultats** du hall : l'élément `results_board`
du plan (4.1). Le rythme de révélation est un simple chrono d'affichage du client, démarré quand il voit les résultats : le titre,
puis les votes d'une porte à la fois (1,2 s, puis 2,5 s entre deux portes ; un vote caché montre « ? »), puis les nouveaux points
(`deltas`) et les nouveaux fantômes. Sans `results_board` dans le plan, le client dessine un panneau compact sur le HUD (les
votes des trois portes, puis les points du joueur).

#### La porte finale (T2.5)

La porte finale est la porte du plan de type `final`. **Le pupitre** est sa zone `threshold` (facultative, voir 4.1 ; l'éditeur la
propose pour ce type de porte) : le joueur s'y tient pour actionner la porte, comme on se tient sur le seuil d'une porte de couleur.
Le côté « après » de la porte est sa `zone_b` (la zone de type `final`), celui d'avant sa `zone_a` (le hall).

1. **Actionner** (`open_final_door`). Refusé hors de `FINAL` (`WRONG_PHASE`), en pause, à un fantôme (`DEAD`), à un non-joueur, et à
   un bracelet de poignet qui ne lit pas 9 points (`CANNOT_OPEN_FINAL`, c'est `viewer.can_open_final_door`). Si la porte finale du plan
   a un pupitre (`threshold` renseigné), le joueur doit s'y tenir d'après sa dernière zone (`NOT_AT_FINAL_DOOR` sinon) ; sans
   pupitre dans le plan, il n'y a pas de condition de lieu (essais, plan absent, factices : D17) ; **mais le validateur du plan
   refuse, dans un plan complet, une porte finale sans pupitre** (`ERROR_TRAHISON_LAYOUT_MISSING_FIELD`, `field=threshold`, D17).
   Les factices sont dispensés de se tenir quelque part. Actionner une porte déjà ouverte ne change rien.
2. **La fenêtre.** La partie reste en `FINAL` pendant `final_door_seconds` (réglage de `create`, **60 par défaut**, D18/D19 : une minute, toujours en entier).
   Pour cela, `Game.finalDoor` garde `{ untilMs, openers, openedBy, crossed }` avec la partie (un redémarrage la rétablit, et la
   referme à l'heure) ; l'échéance de l'étape devient la fin de la fenêtre (le compte à rebours de la vue est celui de la porte), et
   **aucune nouvelle boucle de votes ne démarre** tant qu'elle est ouverte. `doors_state` de la porte finale vaut `OPEN` pour les
   `openers` (les vivants dont le bracelet de poignet lit 9 ou plus à l'ouverture), `CLOSED` pour tous les autres : chaque client
   retire ou garde son faux mur d'après **sa** vue. La vue de chacun porte `final_door` (fin de la fenêtre, nombre d'`openers`) : c'est le **compte à rebours final**, pour tout le monde
   (HUD de chaque client, annonces à 30, 10 et 5 secondes), les fantômes, spectateurs et maîtres du jeu compris.
3. **Le passage.** Un opener qui entre dans la `zone_b` de la porte est compté (`final_cross` au journal). Un factice passe avec la
   porte. **La porte reste ouverte toute la fenêtre même quand tous les openers sont passés** (D19) : elle ne se ferme que
   à l'heure (ou si le maître du jeu force `next`).
4. **La fermeture, pour toujours.** La partie passe à `ENDED` : `winners` = les openers qui sont passés (ou qui se tiennent dans
   la `zone_b` à l'instant de la fermeture), liste vide si personne. Journal : `final_door` (à l'ouverture), `final_door_closed`
   (`cause` : `timeout`, ou le maître du jeu qui force `next` pendant la fenêtre), `ended`. Un plan **sans porte finale**
   (hub sans plan, essais) a la même fenêtre de `final_door_seconds`, sans porte à franchir (D21) : à son terme tous les openers gagnent.
5. **Tant qu'elle n'est pas ouverte**, le vote recommence à chaque fin de `FINAL` (10 minutes par défaut) : `FINAL` → `VOTE` →
   `RESULTS` → `FINAL` (déjà dans le moteur, décision D1).

`EventsTrahisonSay` `kind: announce` : `phantasmon_events.trahison.say.final_door_open` (`args` : le nom de celui qui a actionné, les
secondes ; le texte annonce à tous que **la porte 9 vient d'être ouverte**) à l'ouverture ; `say.final_countdown` (`args` : les secondes
restantes) est dit par chaque client à 30, 10 et 5 secondes (affichage seulement) ; à la fin, `say.final_door_escaped` (`args` : les noms de ceux qui sont passés) ou `say.final_door_nobody`.

Côté client (ni règle ni décision) : `world/FinalDoorFlow` lit la vue et la zone, la touche **V** (la même que celle des cabines)
ouvre le pupitre (`screen/FinalDoorScreen`, avec confirmation puisque la partie se termine), `hud/FinalDoorPrompt` dit où aller, quoi
appuyer, ou, la porte ouverte, qu'il faut passer (ou qu'elle n'est pas pour soi) ; les faux blocs sont ceux de `world/FakeBlocks`,
d'après `doors_state`.

#### Seuils des portes de couleur (`door_hints`, étape T2.3)

Chaque porte de couleur a, devant elle (côté hall), une zone « seuil » : le champ `threshold` de la porte dans le plan, ou
à défaut sa `zone_a`. La porte se traverse de la zone de seuil vers la `zone_b` (côté salle). Pendant `DOORS`, le backend
connaît la zone de chaque joueur (`EventsTrahisonZone`) et décide seul :

1. **Ouverture.** Les joueurs vivants qui n'ont pas encore passé de porte et qui se tiennent sur le seuil présentent leurs
   bracelets : celui du poignet et, plus tard (T6), ceux qu'ils portent (`PlayerState.carried`). Si, pour une répartition
   encore possible (compatible avec les portes déjà passées), ils réunissent une **paire** de la couleur de paire du groupe
   et le **solo** de sa couleur de solo (c'est `rules.Doors.canOpen`, restreint aux répartitions possibles), la porte passe
   `OPEN` pendant `door_open_seconds` (réglage de la partie, 6 par défaut, 1 à 60). Une paire et un solo suffisent : le
   second de la paire peut encore arriver pendant la fenêtre.
2. **Passage.** Un joueur vivant qui entre dans la `zone_b` d'une porte ouverte est enregistré comme ayant passé cette porte
   (même effet que l'action `pass_door`, mêmes contrôles : couleur de son bracelet, compatibilité avec les portes déjà
   passées). Un refus est **silencieux** : aucune erreur n'est envoyée, seul le panneau au-dessus de la porte explique. L'action
   `pass_door` reste, pour les factices et le débogage, avec ses codes d'erreur.
3. **Fermeture.** La porte se referme à la fin de la fenêtre, ou dès que tous les membres vivants du groupe l'ont passée. Si le
   seuil est encore satisfait ensuite, elle se rouvre au message de zone suivant (au plus 2 s). À la fin de `DOORS`, toutes se
   ferment ; **un vivant qui n'a passé aucune porte de couleur meurt** (D2) : il devient fantôme, ses points ne bougent pas (le
   réglage `no_door_penalty` n'existe plus). Il reste dans le groupe de la répartition (qui compte ses points et son vote
   « Allier »), `doors_dead` de la vue donne les index des morts de la manche, et l'animatrice le dit à tous : 
   `EventsTrahisonSay` `phantasmon_events.trahison.say.doors_dead` avec les noms en `args`.

Une vue est envoyée à chaque changement de ce que disent les seuils (un joueur qui monte sur un seuil ou en part, une porte
qui s'ouvre ou se referme).

`door_hints[]` :

| Champ | Type | Notes |
|---|---|---|
| `door` | texte | Identifiant de la porte du plan |
| `index` | entier | Index de la paire de couleurs dans `doors` (0 à 2) |
| `colors` | liste de 2 textes | Les deux couleurs de la porte |
| `present` | entier | Joueurs vivants qui se tiennent sur le seuil et n'ont pas passé de porte |
| `open_until` | entier (ms epoch serveur) ou `null` | Fin de la fenêtre d'ouverture ; `null` tant que la porte est fermée |
| `ready` | booléen | Les bracelets du seuil satisfont la porte (elle est ou vient d'être ouverte) |
| `incompatible` | booléen | Ils satisferaient `Doors.canOpen`, mais pas avec les portes déjà passées : la porte reste fermée |
| `missing` | liste | Ce qu'il manque pour la façon la plus proche d'ouvrir : `{ "color": "GREEN", "status": "SOLO" }` (`PAIR` ou `SOLO`) ; vide si `ready` |
| `alt_missing` | liste ou `null` | Une autre façon, aussi proche que `missing` (les deux répartitions sont encore possibles) ; `null` sinon |

```json
"door_hints": [
  { "door": "door_1_rg", "index": 0, "colors": ["RED", "GREEN"], "present": 1, "open_until": null, "ready": false,
    "incompatible": false, "missing": [ { "color": "RED", "status": "PAIR" } ], "alt_missing": null }
]
```

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
    "door_hints": [],
    "final_door": null,
    "doors_dead": [],
    "game_masters": [{ "uuid": "0d5c7a52-1b7e-4c76-8d02-3f6b1a9e4c11", "name": "Gwen" }],
    "cabins": [{ "id": "cab_rg_pair", "closed": true, "state": "WAITING", "window_until": 1794796823445 }],
    "layout_version": 3,
    "doors_state": { "door_1_rg": "CLOSED", "exit_1_rg": "OPEN", "cabin_door_rg_pair": "OPEN", "door_final": "CLOSED" },
    "viewer": { "kind": "PLAYER", "index": 0, "group": 0, "side": "PAIR", "door_passed": 0, "side_voted": true, "own_vote": "ALLY", "can_open_final_door": false },
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
  "settings": { "mode": "AUTO", "durations": { "PROLOGUE": 600, "DOORS": 300, "ROOMS": 1800, "FREE_TIME": 600, "VOTE": 300, "RESULTS": 180, "FINAL": 600 }, "door_open_seconds": 6, "final_door_seconds": 10, "cabin_partner_seconds": 30, "cabins_required": true },
  "bot_policy": "RANDOM",
  "seed": 7,
  "draw": null
}
```

`gm.settings` porte aussi `draw_max_spread`, `draw_rise` et `draw_avoid_games` (le tirage, voir « Tirage des salles »). `gm.draw` est
`null` sans tirage ; sinon (T4.1, T4.3) :

```json
"draw": { "seed": 123, "relaxed": ["variety"],
  "picks": [ { "round": 1, "group": 0, "room": "atelier", "name": "Atelier", "difficulty": 4, "expected_seconds": 900,
               "mechanism": "keypad", "manual": false, "place": "place_1a", "orientation": "east" } ] }
```

Neuf `picks` (manche 1 à 3, groupe 0 à 2 = porte et place). `place` et `orientation` sont ceux du plan (`null` sans plan ou sans
place) : `orientation` est le quart de tour que la place applique à la salle. `relaxed` : les contraintes que la banque n'a pas pu
tenir, parmi `rise`, `variety`, `recent`, `spread`, `repeat`. `manual` : la salle a été remplacée par le maître du jeu. Le bloc ne
part qu'aux maîtres du jeu de la partie.

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
| `key` | texte | `phantasmon_events.trahison.say.phase.<étape en minuscules>` : `lobby`, `prologue`, `doors`, `rooms`, `free_time`, `vote`, `results`, `final`, `ended` ; ou `phantasmon_events.trahison.say.doors_dead` (D2, à l'entrée en `ROOMS` quand des joueurs sont morts faute d'avoir passé une porte) ; ou `say.final_door_open`, `say.final_door_escaped`, `say.final_door_nobody` (T2.5, voir « La porte finale ») |
| `args` | liste de textes | Facultatif : les arguments de la clé (`doors_dead` : les noms des morts, séparés par des virgules ; `final_door_open` : le nom de celui qui a ouvert, la durée en secondes ; `final_door_escaped` : les noms de ceux qui sont passés) |
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
- Qui l'envoie (D7) : les **maîtres du jeu de la partie**, c'est-à-dire les comptes qu'un admin a nommés dans son lobby (aucun,
  un ou plusieurs, en plus des 9 joueurs et des spectateurs). Plus personne ne devient maître du jeu en envoyant un message :
  un autre reçoit `ERROR_TRAHISON_NOT_GAME_MASTER` et rien ne change. Exceptions : `create` est réservé aux admins
  (`ERROR_TRAHISON_ADMIN_REQUIRED` pour un autre), et **en `LOBBY` un admin peut préparer la partie** même sans en être maître du
  jeu : `add_bots`, `set_bot_policy`, `set_game_master`, `start`, `end`. Celui qui crée la partie n'en est pas maître du jeu
  d'office. Un maître du jeu reçoit la vue `GM` ; il ne joue pas.
- Champs : `action` (texte, obligatoire), `hub` (obligatoire pour `create`, pour un admin qui n'est maître d'aucune partie, et
  tant qu'on ne dirige aucune partie), et les champs propres à l'action (voir la section 3).
- Réponse : une vue à chaque changement, ou une erreur à l'expéditeur.

```json
{ "type": "EventsTrahisonGm", "data": { "action": "add_bots", "hub": "trahison", "count": 7, "policy": "ally" } }
```

### `EventsTrahisonRoom`

- Sens : S2C.
- Qui le reçoit : les membres **vivants** du groupe qui joue la salle (vue joueur), les maîtres du jeu qui ne jouent pas (vue
  maître du jeu), les fantômes et tous ceux qui ne jouent pas, spectateurs du hub compris (vue spectateur, **de toutes les
  salles**). Un joueur vivant d'un autre groupe ne reçoit rien de cette salle. Joueurs et spectateurs reçoivent le même
  contenu ; il ne contient jamais une solution ni un texte d'aide. Un maître du jeu qui joue est un joueur (même règle que
  pour `EventsTrahisonView`). Les factices n'en reçoivent aucun.
- Quand : au début de la salle (à la fermeture des portes, juste après la `View` de l'étape `ROOMS`), à chaque étape ouverte ou
  résolue, à chaque objet ramassé, à l'ouverture de la sortie, à la remise à zéro ; et en réponse à l'action `view` (une
  `Room` par salle que le destinataire a le droit de voir : reconnexion). Il n'y a pas de message de fin : les salles
  **restent** quand la manche suivante commence (D8 : le temps libre visite toutes les salles ouvertes jusqu'ici) et disparaissent à la
  fin de la partie ; le client les oublie alors. Au début de chaque `FREE_TIME`, chacun reçoit de nouveau toutes les salles qu'il peut voir,
  celles des manches précédentes comprises.
- Champs :

| Champ | Type | Notes |
|---|---|---|
| `hub`, `round`, `group` | texte, entier, entier | Le hub, la manche (1 à 3, D8) et le groupe (0 à 2) qui joue. **Une salle s'adresse par (`round`, `group`)** : les salles des manches précédentes restent dans le hub. `round` vaut 0 pour une salle jouée hors d'une partie (essais) |
| `room`, `name`, `version` | texte, texte, entier | La définition utilisée |
| `members` | liste d'entiers | Indices des joueurs du groupe |
| `started_at`, `last_progress_at`, `finished_at` | ISO-8601, `finished_at` `null` tant que non finie | `last_progress_at` : dernière étape résolue ou objet ramassé |
| `exit_open` | booléen | Toutes les étapes sont résolues : la sortie est ouverte |
| `steps_done`, `steps_total` | entiers | |
| `steps` | liste | `{ id, open, solved, hints }` : `hints` = les textes d'aide déjà donnés à l'étape, du plus vague au plus direct (T3.6, jamais une solution : c'est le texte écrit par l'auteur de la salle) ; le maître du jeu y ajoute `mechanism` et `hints_given` |
| `slots` | liste | Les emplacements **actifs** seulement (voir ci-dessous) |
| `inventory` | liste de textes | Identifiants des objets du sac commun du groupe (tous les porteurs, dans l'ordre de ramassage) |
| `items` | liste | Le même sac avec son porteur : `{ id, holder }`, `holder` étant l'indice du joueur qui porte l'objet (`-1` : n'importe quel membre). **L'inventaire d'un joueur est la part de `items` dont il est le porteur** : lui seul donne, utilise ou combine un objet |
| `place` | objet ou `null` | Où la salle se tient dans le hub : `{ id, origin {x,y,z}, orientation }` (voir « Place d'une salle ») |

Un emplacement : `{ id, kind, position {x,y,z}, orientation, size {x,y,z}, shows: [...] }`, la position étant relative à
l'origine de la place de la salle. `shows` dit ce qu'il montre maintenant :

| `type` | Champs | Sens |
|---|---|---|
| `puzzle` | `step`, `role`, `mechanism`, `solved` | Un mécanisme s'y utilise (étape ouverte ou résolue). `use_slot` l'ouvre |
| `clue` | `step`, `clue { role, type, value }` | Un indice visible. `type` : `text`, `digits` (`value` : texte de chiffres), `symbols` ou `colors` (`value` : liste de textes), `image` (identifiant), `document` (`value` : liste de pages, chaque page un texte ; une ligne `@image:<id>` y montre l'image `textures/trahison/docs/<id>.png`), `item` (identifiant d'objet, ramassable par `pick_up`) |
| `exit` | `open` | L'emplacement de sortie |

Un indice devient visible quand son étape est ouverte (`gives[].when` = `open`, par défaut) ou résolue (`solved`). Un objet
ramassé disparaît de l'emplacement.

#### Place d'une salle

Une salle se joue dans un carré de 48 x 48 blocs (24 de haut) de la place du plan que le backend lui a donnée (`place`). La
position d'un emplacement est donnée dans le repère propre de la salle (x vers l'est, z vers le sud, le nord de la salle étant
son côté sortie) ; le client la tourne de `k` quarts de tour dans le sens horaire vu d'en haut (`k` = 0 pour `north`, 1 `east`,
2 `south`, 3 `west`) **à l'intérieur du même carré**, puis pose le coin minimum du carré à `origin` (coordonnées de hub, `y` du
monde ; `world/HubBlocks` fait le passage au bloc du monde). Un emplacement est la boîte de sa position à sa position plus sa
taille ; son `orientation` est le côté vers lequel il regarde (vers les joueurs), tourné de la même façon. Le calcul est
`world/RoomFrame`.

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
- Qui le reçoit : selon `reason` (ci-dessous). `open` : au seul joueur qui a ouvert l'écran (vue joueur). Les autres raisons :
  comme `EventsTrahisonRoom` (membres vivants du groupe, maîtres du jeu, fantômes et spectateurs de toutes les salles), chacun avec
  sa vue ; seul le maître du jeu reçoit les solutions (`target`). Les clients qui ont l'écran du mini-jeu ouvert se recalent
  dessus ; les autres ignorent le message.
- Quand : un joueur ouvre l'écran (`open`, à lui seul), un geste est appliqué (`input`), l'étape est résolue (`solved`), la salle
  est remise à zéro (`reset`).
- Champs :

| Champ | Type | Notes |
|---|---|---|
| `hub`, `round`, `group` | texte, entier, entier | Comme pour `EventsTrahisonRoom` |
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

#### Vue du mécanisme `ring_dial` (cadran à anneaux)

Anneaux concentriques de symboles ; il faut amener sur le rayon marqué (le haut de l'écran) la suite cible, un symbole par
anneau, de l'extérieur vers le centre. Tourner un anneau peut en tourner d'autres (une **liaison** `a -> b, k` : quand
l'anneau `a` tourne d'un cran, `b` tourne de `k` crans, signé ; pas de chaîne). Les liaisons sont cachées par défaut :
les joueurs les découvrent en tournant.

- Paramètres (`params` de l'étape) : `rings` 3 à 5 (défaut 3), `symbols` 6 à 12 (défaut 8 : les symboles de l'anneau
  extérieur, chaque anneau intérieur en a un de moins, jamais moins de 6), `links` 0 à 3 (défaut 2), `showLinks`
  (défaut `false`).
- Symboles : identifiants de `SymbolSet` (backend) = `Glyphs.IDS` (client) : `eye`, `spiral`, `trident`, `crescent`,
  `hourglass`, `key`, `dot_triangle`, `cross_circle`, `wave`, `fork`, `star`, `diamond`, `gear`, `bolt`, `hexagon`,
  `loop_arrow`, `ladder`, `trio`, `arch`, `chevrons`. Distincts à l'intérieur d'un anneau.
- Vue : `{ "symbols": [[id...]...], "offsets": [n...], "showLinks": false, "links": [...], "solved": false }`. `symbols[i]`
  = les symboles de l'anneau `i` (0 = extérieur) dans le sens des aiguilles d'une montre. `offsets[i]` = nombre de crans
  tournés dans le sens des aiguilles depuis la position où `symbols[i][0]` est en haut : le symbole sur le rayon marqué est
  `symbols[i][(-offsets[i]) mod n]`. `links` (`[{ "from", "to", "steps" }]`) n'est présent que si `showLinks`. **Jamais la cible**
  dans la vue d'un joueur ni d'un spectateur ; le maître du jeu reçoit en plus `target` (`[id...]`) et `links`.
- Geste (`puzzle_input`) : `{ "ring": 0..rings-1, "delta": 1 | -1 }` (+1 = un cran dans le sens des aiguilles). Un geste
  mal formé : `ERROR_TRAHISON_BAD_INPUT`. Une étape résolue ignore les gestes.
- Événement : `{ "type": "turn", "ring": 1, "delta": -1 }` ; les anneaux entraînés se lisent dans la vue (`offsets`).
- Indice : rôle `symbols`, type `symbols`, valeur `[id...]` = la cible, de l'extérieur vers le centre ; la définition de la
  salle le pose ailleurs (mur, document).
- Génération : voir `RingDial` (javadoc). L'énigme est résoluble par construction (brouillage par des coups légaux depuis
  l'état résolu) et ne commence jamais résolue ; les tests le vérifient par parcours en largeur sur tout l'espace d'états.

#### Vue du mécanisme `keypad` (digicode mural)

Un digicode : le groupe tape un code et le valide. Le code est tiré par la graine et produit comme indice, que la salle place ailleurs ; le digicode ne le dit jamais.
Ce qui est tapé est partagé par tous ceux qui sont devant l'écran.

- Paramètres : `length` 3 à 6 (défaut 4) : le nombre de chiffres du code (un code peut commencer par 0).
- Rôle d'emplacement : `keypad` (genre `keypad` ou `terminal`). Indice : rôle `code`, type `digits`, valeur `"0482"` : à placer par `gives` dès l'ouverture de l'étape (`when: open`).
- Vue : `{ "length": 4, "entry": "04", "wrong": 1, "solved": false, "lock_ms": 0 }` : `entry` = les chiffres déjà tapés (tout le monde les voit), `wrong` = nombre d'essais ratés depuis le début, `lock_ms` = les
  millisecondes de verrouillage qui restent (0 : libre ; le client décompte depuis la réception de la vue).
  **Jamais le code** dans la vue d'un joueur ni d'un spectateur ; le maître du jeu reçoit en plus `code`.
- Geste (`puzzle_input`) : `{ "key": "0".."9" | "del" | "clear" | "enter" }`. Un chiffre s'ajoute à `entry` (ignoré si elle a déjà la longueur du code), `del` retire le dernier chiffre,
  `clear` vide, `enter` compare : le bon code résout l'étape ; sinon `entry` est vidée et `wrong` monte de 1 (un code trop court est faux aussi). **Verrouillage** (décision D30) : le 3e essai raté d'affilée verrouille le digicode 10 s : toute touche est ignorée (pas d'événement), le compteur d'échecs repart de 0, et le
  verrou expire à la demande (le mécanisme garde une horloge injectée, comme `three_levers`).
  Un geste mal formé : `ERROR_TRAHISON_BAD_INPUT`. Une étape résolue ignore les gestes.
- Événements : `{ "type": "key", "key": "5" }` (aussi pour `del` et `clear`), `{ "type": "wrong" }` (avec `"locked": true, "ms": 10000` quand cet essai verrouille), `{ "type": "unlock" }`.
- Écran : `KeypadScreen` (l'afficheur passe au rouge et dit « Verrouillé : n s » pendant le verrou) (fenêtre 400 x 240) ; hors ligne : `/phantasmon event trahison minigame keypad`.

#### Vue du mécanisme `pickup_set` (objets cachés)

Des objets cachés dans la salle, à trouver et à ramasser. La salle a jusqu'à 6 « cases » : les rôles d'indice `spot_1` à `spot_n`, chacune donnée (`gives`) à un emplacement (en
général de genre `pickup`). La graine tire **quelles** `filled` cases contiennent un objet et **quel** objet : une case vide ne montre rien (son emplacement n'est pas actif, donc pas
d'entité cliquable).

- Paramètres : `spots` 2 à 6 (défaut 6, nombre de rôles `spot_n` : chacun doit être donné à un emplacement, sinon l'objet serait hors de portée et le validateur refuse), `filled` 1 à `spots`
  (défaut 3), `items` (texte, défaut `fuse,gear,lens,valve,badge,crystal` : la réserve d'identifiants d'objets séparés par des virgules, au moins `filled` distincts ; éviter les
  identifiants que d'autres étapes donnent), `document` (texte, défaut vide : si non vide, l'étape produit aussi un indice `document` ; pages séparées par une ligne `---`).
- Rôle d'emplacement : aucun n'est nécessaire (le mécanisme ne déclare aucun rôle, rien n'est contrôlé : une salle ancienne qui lie encore `deposit` reste valide, l'écran compteur s'ouvre alors sur cet emplacement). Indices : `spot_1`..`spot_n` de type `item`
  (ramassable par `pick_up`), `document` de type `document` (lu sur place : on ne le ramasse pas).
- **Résolution** (décision D32) : l'étape est résolue quand **tous les objets posés par la graine ont été ramassés** : un simple `pick_up` suffit. `RoomService` prévient le mécanisme à chaque ramassage
  d'un objet donné par l'étape ouverte (crochet `Mechanism.onPickUp(state, player, role, item)`, vide par défaut) ; l'objet reste dans le sac, on peut le donner ou le combiner sans effet sur l'étape. Plus de dépôt :
  `use_item` d'un objet de l'étape est refusé (`ERROR_TRAHISON_ITEM_REFUSED`). La réponse du dernier `pick_up` porte `step` et `step_solved`.
- Vue : `{ "total": 3, "delivered": ["gear"], "solved": false }`. `total` = le nombre d'objets cachés (décision D33), `delivered` = ceux qui ont été ramassés. **Jamais les objets encore cachés** ;
  le maître du jeu reçoit en plus `required` et `filled` (les cases garnies).
- Geste : aucun (`puzzle_input` est ignoré). Événement : `{ "type": "collect", "item": "gear" }`.
- Écran : `PickupSetScreen` (un compteur, ouvert seulement si la salle lie un emplacement `deposit`) ; hors ligne : `/phantasmon event trahison minigame pickup_set`.

#### Vue du mécanisme `code_breaker` (décodeur « bien placé / mal placé »)

Un terminal cache une combinaison (jetons tirés par la graine) ; le groupe tape un essai, le terminal répond combien de jetons sont **bien placés** (bon jeton, bonne place) et **mal placés** (bon jeton, mauvaise place), et
l'historique des essais reste à l'écran. Aucun indice : la logique suffit.

- Paramètres : `length` 3 à 5 (défaut 4) : jetons de la combinaison ; `alphabet` 4 à 9 (défaut 6) : jetons différents (au moins `length` si les répétitions sont interdites) ; `kind` `symbols` (défaut : des glyphes de
  `SymbolSet`, tirés par la graine) ou `digits` (0 à `alphabet - 1`) ; `repeats` (défaut `false`) : un jeton peut revenir ; `maxTries` 0 (défaut, aucune limite) ou 6 à 30 : à l'épuisement le terminal efface l'historique et garde **la même combinaison**
  (décision D36 ; `resets` compte les remises à zéro).
- Rôle d'emplacement : `terminal` (genre `terminal` ou `pedestal`). Aucun indice. Le validateur n'exige qu'un emplacement lié (`missing=slot:terminal`).
- Vue : `{ "alphabet": ["eye","key",...], "kind": "symbols", "length": 4, "repeats": false, "maxTries": 0, "entry": "01", "history": [{ "guess": [0,3,1,2], "exact": 1, "misplaced": 2 }], "tries": 1, "resets": 0, "solved": false }`.
  Un jeton est son rang dans `alphabet` ; `entry` = les rangs déjà tapés (partagés). **Jamais la combinaison** (ni la graine) dans la vue d'un joueur ni d'un spectateur ; le maître du jeu reçoit en plus `code`
  (les rangs).
- Geste (`puzzle_input`) : `{ "key": "0".."9" | "del" | "clear" | "enter" }` : un chiffre est le rang d'un jeton, ajouté à `entry` (ignoré si elle est pleine, ou si le jeton y est déjà et que les répétitions sont interdites ;
  un rang hors de l'alphabet : `ERROR_TRAHISON_BAD_INPUT`), `del` et `clear` corrigent, `enter` envoie (ignoré si `entry` est plus courte que la combinaison). Réponse : `exact` = positions au même jeton,
  `misplaced` = jetons en commun (multiplicité comprise) moins `exact`. `exact == length` résout. Une étape résolue ignore les gestes.
- Événements : `{ "type": "key", "key": "3" }` (aussi `del`, `clear`), `{ "type": "feedback", "exact": 1, "misplaced": 2 }`, `{ "type": "reset", ... }` (essais épuisés, même contenu que `feedback`), `{ "type": "unlock" }`.
- Écran : `CodeBreakerScreen` (fenêtre 400 x 240) : à gauche l'écran du terminal (historique tapé, ligne de saisie partagée, disque plein = bien placé, anneau = mal placé, chacun avec son nombre), à droite les touches
  de jetons (au clavier : 1 à 9 pour des symboles, 0 à 9 pour des chiffres) ; hors ligne : `/phantasmon event trahison minigame code_breaker` (combinaison d'essai affichée en orange).

#### Vue du mécanisme `wiring` (câblage)

Un panneau de bornes à relier par des fils. Les bornes de gauche sont numérotées de 1 à `wires` ; celles de droite portent une couleur et son symbole (triangle rouge, disque vert, carré bleu, et deux couleurs réservées aux fils : losange jaune, anneau blanc ; jamais la couleur seule).
La graine tire le **schéma** : la couleur de la borne de droite que chaque borne de gauche doit atteindre. Ce schéma est un indice que la salle place ailleurs (décision D37).

- Paramètres : `wires` 3 à 6 (défaut 4) : bornes de chaque côté ; `colors` 2 à 5 (défaut 3, au plus `wires`) : combien de couleurs le schéma emploie (chacune au moins une fois ; plus de fils que de couleurs : des couleurs répétées). 2 ou 3 : rouge, vert, bleu (tirées par la graine) ; 4 ajoute
  `yellow`, 5 ajoute `white` (décision D38 : couleurs de fil seulement, les trois couleurs du jeu ne changent pas).
- Rôle d'emplacement : `panel` (genre `terminal` ou `pedestal`). Indice : rôle `scheme`, type `colors`, valeur `["blue","red","red","green"]` : la n-ième couleur est celle de la borne de gauche n° n ; à placer par `gives` dès
  l'ouverture de l'étape (`when: open`). **Aussi** un indice `scheme_text` de type `document` (une page, une ligne `1 : @color:red` par borne ; le client remplace `@color:<id>` par le nom de la couleur dans la langue du joueur,
  clés `phantasmon_events.trahison.color.<id>`) : la salle place l'un ou l'autre, ou les deux (décision D37). Le validateur exige l'un des deux dès l'ouverture (`missing=scheme;when=open`).
- Vue : `{ "wires": 4, "right": ["green","red","blue","red"], "links": [-1,2,-1,0], "wrong": 0, "solved": false }` : `right` = les couleurs des bornes de droite (publiques), `links[i]` = la borne de droite où arrive le fil de
  la borne de gauche `i` (-1 : aucun), `wrong` = courts-circuits. **Jamais le schéma** dans la vue d'un joueur ni d'un spectateur ; le maître du jeu reçoit en plus `scheme`.
- Gestes (`puzzle_input`) : `{ "op": "connect", "left": 0, "right": 2 }` pose un fil (une borne tient un fil : ceux qu'elle avait sautent) ; `{ "op": "disconnect", "left": 0 }` ou `{ "op": "disconnect", "right": 2 }` en
  enlève un. Quand tous les fils sont posés : chaque borne de gauche reliée à une borne de droite de la couleur demandée résout l'étape ; sinon c'est un **court-circuit** : `wrong` monte de 1 et **tous les fils sautent**, le panneau repart vide (décision D39). Hors intervalle ou mal
  formé : `ERROR_TRAHISON_BAD_INPUT`. Une étape résolue ignore les gestes.
- Événements : `{ "type": "connect", "left": 0, "right": 2 }`, `{ "type": "disconnect", "left": 0, "right": 2 }`, puis `{ "type": "wrong", "cleared": true }` (court-circuit, la vue suivante a tous les `links` à -1) ou `{ "type": "unlock" }` quand le panneau est plein.
- Écran : `WiringScreen` (fenêtre 400 x 240) : fils qu'on tire d'une borne à l'autre (ou clic sur deux bornes), un fil repris par son bout se déplace ou s'enlève (lâché dans le vide), clic droit sur une borne enlève
  son fil ; le fil prend la couleur de sa borne de droite ; les fils des autres joueurs arrivent par la vue et leur curseur s'allume à leur couleur ; hors ligne :
  `/phantasmon event trahison minigame wiring` (schéma d'essai affiché en orange).

#### Vue du mécanisme `three_levers` (trois leviers à tenir ensemble)

Trois leviers aux trois coins d'une salle, à tenir **en même temps par trois joueurs différents**. Joué **dans le monde**, sans écran : un clic sur un emplacement `lever` envoie `use_slot` ; le backend appelle
`onUse` avec le rôle de l'emplacement. Aucun écran ne s'ouvre (le routeur `PuzzleScreens` n'a pas d'écran pour lui, seulement un aperçu hors ligne).

- **Tenir = un bail** (décision D40) : un clic enclenche le levier pour `hold` secondes ; recliquer (même joueur) renouvelle ; aucun message de relâchement ni de battement de cœur. L'expiration est évaluée à la demande
  par le backend (horloge du mécanisme) ; le client décompte depuis les `ms` de la vue.
- Règles : un joueur ne tient qu'**un** levier à la fois (en enclencher un autre lâche le premier) ; le clic d'un autre joueur sur un levier tenu ne fait rien ; la graine tire l'**ordre** (si `ordered`) et le **délai**
  (`gap-2..gap` s, au moins 2) ; un levier hors de l'ordre fait tout lâcher (`wrong`, `resets` + 1) ; plus de délai que tiré depuis le dernier enclenchement : tout lâche (`slow`) et l'enclenchement
  repart comme le premier ; le troisième levier enclenché pendant que les deux autres tiennent résout l'étape (décisions D41 à D43).
- Paramètres : `ordered` (défaut `true`), `gap` 2 à 8 (défaut 5), `hold` 5 à 30 (défaut 12 ; au moins `2 * gap`), `solo` (défaut `false` ; essai de l'éditeur : un joueur peut enclencher les trois, délai ignoré).
- Rôles d'emplacement : `lever_1`, `lever_2`, `lever_3` (genre `lever`) ; les trois doivent être liés, chacun à son emplacement (`missing=slot:lever_2`, `duplicate=slot:lever_3`). Aucun indice.
- Vue : `{ "levers": [ { "on": true, "ms": 8200, "by": 1 }, ... ], "count": 1, "resets": 0, "solved": false }` : `ms` = bail restant à la construction de la vue, `by` = joueur qui tient (-1 : personne),
  `count` = leviers tenus. **Jamais l'ordre ni le délai** pour un joueur ou un spectateur ; le maître du jeu reçoit en plus `order` (leviers de 1 à 3, dans l'ordre à suivre) et `gapMs`. Résolu : les trois `on`.
- Geste `puzzle_input` (outils et tests, le jeu passe par `use_slot`) : `{ "lever": 1..3 }`, même effet qu'un clic. Autre chose : `ERROR_TRAHISON_BAD_INPUT`.
- Événements : `{ "type": "engage", "lever": 2, "count": 2 }`, `{ "type": "hold", ... }` (bail renouvelé), `{ "type": "wrong", "lever": 3 }`, `{ "type": "slow" }`, `{ "type": "unlock" }`.
- Rendu (`world/InteractableRender.coopLever`, état lu par `world/LeverState`) : poignée en haut et sombre pendant l'attente, en bas et allumée une fois enclenchée (elle clignote pendant les deux dernières secondes
  du bail), trois petites lumières sous la plaque pour le nombre de leviers tenus ensemble, tout vert une fois résolu. Sons (interface, pas de position) : cliquetis plus aigu à chaque levier, bruit sourd pour `wrong` et `slow`,
  déverrouillage. Hors ligne : `/phantasmon event trahison minigame three_levers` ouvre un **aperçu** (trois leviers, le bail, les lumières ; boutons 1 à 3 au clavier ; les deux joueurs fictifs enclenchent les autres ;
  trois tenus ensemble déverrouillent ; ni l'ordre ni le délai ne sont simulés).

### `EventsTrahisonZone`

- Sens : C2S.
- Qui l'envoie : le client d'un joueur de la partie (vivant ou mort : le corps d'un mort reste où il est tombé). Rien d'un
  spectateur ni du maître du jeu qui ne joue pas : le backend les ignore.
- Quand : à chaque changement de zone, et au moins toutes les 2 secondes pendant la partie (de `LOBBY` à `FINAL`).
- Champs :

| Champ | Type | Notes |
|---|---|---|
| `zone` | texte | Identifiant de la zone du plan où se tient le joueur (calculée par le client d'après le plan, solution S2) ; `""` : dans aucune zone |
| `x`, `y`, `z` | nombres | Sa position en coordonnées de hub : `x` et `z` du hub (le bloc du plan `(hx, hz)` est le bloc du monde `floor(origine) + (hx, hz)`, voir `architecture.md` section 6), `y` du monde, fractionnaires |

```json
{ "type": "EventsTrahisonZone", "data": { "zone": "hall", "x": 3.52, "y": 65.0, "z": -7.1 } }
```

- Le backend garde la zone et la position dans `PlayerState` **sans écrire en base** (ce n'est pas un changement d'état de
  la partie : cela tombe toutes les 2 s par joueur). La zone ne se voit que dans la vue du maître du jeu (`gm.zones`) ; un
  changement de zone renvoie la vue **aux maîtres du jeu seulement**, au plus une fois par seconde et par partie.
- Erreurs : aucune, pas de réponse. Un message d'un non-joueur, ou aux nombres absents ou non finis, est ignoré.
  Comme tout message d'un joueur, il le marque connecté s'il était tombé.

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
| `vote` | `vote` : `ally` ou `betray` | Un joueur vivant, en `VOTE`, dont le groupe est connu ; avec `cabins_required` (T2.4), depuis sa cabine verrouillée (sinon `NOT_IN_CABIN`, `WRONG_CABIN` ou `CABIN_NOT_CLOSED`) | Vote pour son camp. **Une paire n'a qu'un vote** : le premier de ses membres qui vote engage les deux, le second reçoit `ERROR_TRAHISON_ALREADY_VOTED`. Le solo vote une fois. L'expéditeur voit son vote dans `viewer.own_vote` ; les autres n'apprennent que `side_voted` |
| `close_cabin` | | Un joueur vivant, en `VOTE`, dans **sa** cabine (T2.4, D4) | Verrouille tout de suite sa cabine, sans attendre le partenaire d'une paire (qui est alors enfermé dehors). Sans effet si elle est déjà verrouillée ou si son groupe a voté. La simple entrée dans sa cabine la tient déjà (voir « Cabines et vote ») : cette action n'est qu'un raccourci. Refusée : `NOT_IN_CABIN` (aucune cabine à sa position), `WRONG_CABIN` (ce n'est pas la sienne, ou le plan n'en a pas pour lui) |
| `take_bracelet` | `body` : index du mort | Un joueur vivant, partie en cours, hors pause (T6.1) | Prend le bracelet du corps de `body` : même zone et à 3 blocs au plus de la dernière position du mort (`NO_BODY` si pas de corps ou plus de bracelet, `BODY_TOO_FAR`). Le bracelet passe dans `viewer.carried` ; il compte aux seuils des portes (`DoorThresholds`) mais jamais à la porte finale, qui ne lit que le poignet. Montre aussi le corps à celui qui le prend |
| `body_seen` | `body` | Un joueur vivant (T6.1) | Le client a vu le corps (zone, 8 blocs au plus, ligne de vue) ; le backend vérifie zone et distance, puis révèle la mort à cet expéditeur seul |
| `open_final_door` | | Un joueur vivant, en `FINAL`, dont le bracelet de poignet lit 9 points ou plus (`viewer.can_open_final_door`) et, si le plan a un pupitre devant sa porte finale, qui s'y tient (T2.5) | Ouvre la porte finale `final_door_seconds` pour les vivants à 9 points ou plus, puis la ferme pour toujours : la partie passe alors à `ENDED`, `winners` = ceux qui sont passés (voir « La porte finale »). Refusée : `CANNOT_OPEN_FINAL`, `NOT_AT_FINAL_DOOR`, `WRONG_PHASE` |
| `debug_solve` | | Un joueur vivant, en `ROOMS` | Pour les essais et les factices : la salle du groupe de l'expéditeur est résolue d'un coup (toutes ses étapes, sortie ouverte, `EventsTrahisonRoom` et `EventsTrahisonPuzzle` envoyés) et le groupe est marqué résolu, comme si les joueurs l'avaient fait |
| `use_slot`, `puzzle_input`, `pick_up`, `give_item`, `use_item`, `combine` | voir « Actions de salle » ci-dessous | Un joueur vivant, en `ROOMS`, membre du groupe de la salle ; **en `FREE_TIME`, tout joueur vivant, dans toute salle ouverte jusqu'ici, celles des manches précédentes comprises** (D8) ; **en `ROOMS` et `VOTE` aussi, pour un joueur dont le groupe a terminé sa propre salle de la manche en cours** (D22, `viewer.can_roam`) | Actions dans la salle du groupe de l'expéditeur, ou, en temps libre, dans celle que disent `group` et `round` |
| `replay` | `group`, `round` (facultatifs) | Un joueur vivant, en `FREE_TIME` (D8), ou libre de visiter (D22 : `ROOMS` ou `VOTE`, `viewer.can_roam`) | Remet la salle à un état neuf (voir « Temps libre : visiter et rejouer ») |

Toutes les actions de jeu sont refusées en pause (`ERROR_TRAHISON_PAUSED`), aux fantômes (`ERROR_TRAHISON_DEAD`) et à qui ne
joue pas (`ERROR_TRAHISON_NOT_A_PLAYER`), hors de l'étape prévue (`ERROR_TRAHISON_WRONG_PHASE`).

### Actions du maître du jeu (`EventsTrahisonGm`)

| `action` | Champs | Effet |
|---|---|---|
| `create` | `hub` ; facultatifs : `seed` (entier), `mode` (`AUTO` ou `GAME_MASTER`), `fast` (entier : toutes les étapes durent ce nombre de secondes ; `true` = 10), `test_room` (identifiant d'une salle de la banque, voir ci-dessous), `settings` (`{ mode, durations: { "VOTE": 120, ... }, door_open_seconds, final_door_seconds, cabin_partner_seconds, test_room, draw_max_spread, draw_rise, draw_avoid_games }`, durées en secondes par étape, `draw_max_spread` (0 à 20, défaut 1) : écart maximal de difficulté entre les trois salles d'une manche, `draw_rise` (0 à 20, défaut 0) : montée minimale du total d'une manche à la suivante, `draw_avoid_games` (0 à 20, défaut 3) : nombre de parties passées dont les salles sont évitées, `cabin_partner_seconds` : 1 à 300, `cabins_required` : booléen, vrai par défaut) | **Admin seulement.** Crée la partie du hub en `LOBBY`, **sans maître du jeu** (D7 : on les nomme ensuite). Refusée si le hub a une partie non finie. Graine tirée au hasard si absente. Une partie finie ne bloque pas une nouvelle |
| `start` | | `LOBBY` → `PROLOGUE`. Exactement 9 inscrits (les factices comptent). En mode `GAME_MASTER`, au moins un maître du jeu nommé (`ERROR_TRAHISON_NO_GAME_MASTER`) : c'est lui qui fait avancer la partie |
| `set_game_master` | `account` : UUID du compte, `on` : booléen | **Admin seulement, en `LOBBY` seulement** (D7) : donne (`on: true`) ou reprend le rôle de maître du jeu de cette partie. Aucun, un ou plusieurs ; **D12 : un joueur inscrit peut l'être, et un maître du jeu peut s'inscrire (`join`) : il joue alors comme un des neuf, en rôle secret (voir « Le maître du jeu qui joue »)**. Les candidats sont donnés dans `lobby_members` de la vue |
| `pause` / `resume` | | Gèle / relance le chrono. `pause` sur une pause automatique la reprend à son compte (seul `resume` la lève ensuite) |
| `next` | | Force l'étape suivante tout de suite |
| `end` | | Termine la partie sur-le-champ (`winners` vide) |
| `extend` | `seconds` (entier, négatif pour raccourcir) | Ajoute du temps à l'étape en cours |
| `set_door` | `door` : identifiant d'une porte du plan de la partie, `open` : booléen | Ouvre ou ferme cette porte tout de suite (`doors_state`, vues renvoyées). Tient jusqu'au changement d'étape ; enregistrée avec la partie. Hors `ENDED` ; `ERROR_TRAHISON_BAD_ARGUMENT` si la porte n'existe pas dans le plan que la partie utilise (ou si `open` n'est pas un booléen) |
| `set_points` | `bracelet` : 0 à 8, `points` ≥ 0 | Corrige les points d'un bracelet (la mort n'est vérifiée qu'aux prochains résultats) |
| `add_bots` | `count` (entier ; absent ou ≤ 0 : remplit toutes les places libres), `policy` (facultatif) | En `LOBBY` seulement : ajoute des joueurs factices « Bot n » avec de faux UUID ; refusé s'il n'y a pas assez de places (`ERROR_TRAHISON_GAME_FULL`) |
| `set_bot_policy` | `policy` : `ally`, `betray` ou `random` | Règle le vote de tous les factices (défaut `random`) |
| `redraw` | facultatif : `seed` (entier) | Tire à nouveau les neuf salles (graine donnée, sinon la suivante). Seulement en `LOBBY` ou `PROLOGUE`, sinon `ERROR_TRAHISON_WRONG_PHASE` ; `ERROR_TRAHISON_NO_DRAW` si le backend ne tire pas |
| `replace_room` | `round` (1 à 3), `group` (0 à 2), `room` (identifiant d'une salle de la banque) | Met cette salle à la place de la salle tirée, marquée `manual`. Refusée (`ERROR_TRAHISON_WRONG_PHASE`) si les salles de cette manche ont déjà démarré, `ERROR_TRAHISON_BAD_ARGUMENT` si la salle est inconnue ou veut un groupe plus grand que celui de la place |

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

#### Le maître du jeu qui joue (D12)

Un compte à la fois dans `Game.gameMasters` et dans `players` est un **maître du jeu qui joue** : un rôle secret.

- **Secret.** `game_masters` de la vue l'omet pour tous sauf les maîtres du jeu ; `viewer.is_game_master` ne vaut vrai que pour lui-même ;
  `lobby_members` ne liste pas les comptes qui jouent ; un message `EventsTrahisonGm` d'un maître du jeu ne produit aucun texte qui
  porte son nom (les vues ne contiennent que l'état, le journal reste pour les maîtres du jeu). Un maître du jeu nommé avant d'avoir
  rejoint reste visible comme tel jusqu'à son `join` (`game_masters` public pour ceux qui supervisent seulement).
- **Pouvoirs.** Il envoie les mêmes actions `EventsTrahisonGm` que les autres maîtres du jeu. Sa vue reste une vue de joueur
  (`viewer.kind` = `PLAYER`) avec en plus le bloc `gm` (votes, zones, réglages, graine). Les vues de salle sont celles d'un joueur.
- **Victoire des autres.** Si un maître du jeu qui joue meurt (n'importe quelle mort), tous les autres participants gagnent et la
  partie finit : `winners` = les huit autres (vivants ou non), journal `game_master_killed` puis `ended` (`cause` :
  `game_master_killed`), annonce `say.game_master_killed` (son nom est alors révélé). La règle pure est dans `rules.Outcome`
  (`gameMasterKilled`, `winnersWhenGameMasterKilled`). **Point d'accroche** : `GameService.endIfGameMasterKilled(g)` est appelée après
  chaque mort existante (portes fermées : `closeDoors` ; bracelet à 0 : `resolveVotes`). **T6 (tuer entre joueurs) doit l'appeler juste
  après avoir passé un joueur à `alive = false`**, avant toute autre suite.
- Un maître du jeu qui ne fait que superviser reste possible comme avant (D7).

### Tirage des salles (T4.1) et places (T4.3)

À la création de la partie (`create`), le backend tire les neuf salles dans la banque (les salles qui ont leurs blocs : sans eux, rien
à coller), à partir de la graine de la partie et des salles jouées lors des dernières parties (table `trahison_games`, les salles
vraiment démarrées). Pas de tirage si `test_room` est réglé ou si la banque est vide : la partie joue alors la salle d'essai ou la
salle intégrée. Contraintes, de la plus stricte à la plus relâchable (le tirage les relâche dans cet ordre et dit lesquelles il a
rompues dans `relaxed`) : `rise` (le total d'une manche dépasse celui de la précédente d'au moins `draw_rise`), `variety` (deux salles
d'une manche n'ont pas le même mécanisme principal : celui qui compte le plus d'étapes), `recent` (pas de salle des `draw_avoid_games`
dernières parties), `spread` (écart de difficulté d'au plus `draw_max_spread` dans une manche ; relâché en l'élargissant au minimum),
`repeat` (neuf salles différentes). Jamais relâché : une salle qui demande un groupe de `n` joueurs (`three_levers` : 3) n'est mise que
là où le groupe a au moins `n` joueurs. Une banque vide est refusée (`ERROR_TRAHISON_DRAW_EMPTY_BANK`, journalisé, pas de tirage).

Les places : à `start` puis à chaque début de manche, chaque salle tirée reçoit la place du plan de sa manche (la k-ième place du
plan, dans l'ordre du plan, pour le groupe k) et l'orientation de cette place : le tirage garde ce couple (`place`, `orientation`).
Les positions des emplacements restent relatives à la salle et c'est le client qui les tourne et les place (voir « Place d'une
salle ») ; le backend dispose de la même conversion (`RoomPlace.toHub`) pour ses vérifications.

### Salles d'une manche (branchement sur le moteur de partie)

Quand les portes se ferment (passage de `DOORS` à `ROOMS`), le backend démarre **une salle par groupe** (les trois groupes de la
répartition de la manche) : membres = les indices de joueur du groupe (le bracelet de poignet `i` est celui du joueur `i`),
graine = tirée de la graine de la manche et du numéro du groupe (les trois salles ont des solutions différentes, et une
manche rejouée avec la même graine de partie retrouve les mêmes). La salle de chaque groupe est celle du **tirage** de la partie
(`game.draw`, voir « Tirage des salles » ; les manches au-delà de la troisième reprennent le cycle). Sans tirage, tous les groupes jouent la salle du réglage **`test_room`** (identifiant d'une salle de la banque,
réglage de `create`, valide s'il suit `[A-Za-z0-9_-]{1,40}`), ou, quand il n'y en a pas (ou que la salle est introuvable),
une **salle intégrée** : une seule étape `debug_button` et la sortie, de sorte qu'une partie marche toujours.

- Une salle dont la sortie s'ouvre marque le groupe « résolu » (`roomSolved`, entrée `room_solved` du journal, `changed` `room`),
  comme `debug_solve`. Les factices continuent de « résoudre » par `debug_solve` après leur délai.
- **Fin de `ROOMS` (T3.6)** : à l'échéance (ou quand le maître du jeu passe à l'étape suivante), pour chaque salle de la manche **non finie** : toutes ses étapes sont résolues d'office et sa sortie s'ouvre (le
  groupe est marqué « résolu » : sa porte s'ouvre, entrée `room_solved` du journal), et chaque membre du groupe perd `Scoring.ROOM_TIMEOUT_PENALTY` = 1 point (jamais sous 0 ; un bracelet à 0 tue son
  propriétaire comme après un vote ; entrée `rooms_timeout` du journal avec `groups` et `points`). Réglage `roomTimeout` (vrai par défaut) : faux = rien ne se passe pour une salle non finie. Ces salles n'ont aucune mesure de temps.
- **Aides automatiques (T3.6)** : une étape porte jusqu'à 3 textes d'aide (`hints`, du plus vague au plus direct). Toutes les 15 s le backend (`RoomHintTicker`) donne **une aide** à chaque salle en
  cours (sortie fermée, pas un rejeu) dont le groupe n'a pas progressé depuis 10, puis 18, puis 25 minutes (`last_progress_at` : étape résolue, objet ramassé, objet utilisé ou combiné ; un progrès
  relance la série). L'aide va à la première étape ouverte non résolue qui en a encore une. Délais réglables par la propriété `phantasmon.trahison.hint-minutes` (défaut `10,18,25`). Aucun message
  dédié : la vue `EventsTrahisonRoom` est renvoyée avec `steps[].hints` mis à jour, et le client lit la nouvelle aide par la voix de l'hôtesse (clé `phantasmon_events.trahison.room.hint`).
- Les actions de salle ne sont acceptées qu'en `ROOMS` (hors pause, d'un joueur vivant membre du groupe) et en `FREE_TIME`
  (tout joueur vivant, toute salle : D8, voir « Temps libre : visiter et rejouer ») ; **D22 : aussi en `ROOMS` et en `VOTE` pour un
  joueur dont le groupe a terminé sa propre salle de la manche en cours (il est « libre de naviguer » : toute salle, manches
  précédentes comprises, mêmes règles que le temps libre : aucun point, rien ne s'ouvre, `replay` possible ; en `VOTE`, pas d'action
  dans sa propre salle autrement que par visite) ; pour les autres, `ERROR_TRAHISON_WRONG_PHASE` en `VOTE`** ; ailleurs elles reçoivent
  `ERROR_TRAHISON_WRONG_PHASE`. Les salles restent lisibles (messages, `view`) pendant `VOTE` et `RESULTS` de leur manche ; en
  `ROOMS`, une salle finie refuse toute action (`ERROR_TRAHISON_ROOM_FINISHED`).
- L'état de chaque salle (définition utilisée, graine, état de chaque étape avec ses solutions, inventaire, heures) est
  enregistré dans la partie (`rounds[].rooms`, jamais envoyé tel quel) à chaque action : un redémarrage du backend la remet
  telle quelle. Une salle déjà démarrée garde la définition d'origine même si la banque est modifiée ensuite.

### Actions de salle (étape T3.1)

Dans `EventsTrahisonAction` (`hub` comme pour les autres actions). Seuls les joueurs vivants du groupe qui joue la salle
(étape `ROOMS`, partie non en pause) peuvent les envoyer ; une salle finie (sortie ouverte) n'en accepte plus. Le temps libre
change cela pour tout le monde (D8, plus bas).

| Action | Champs | Effet | Réponse |
|---|---|---|---|
| `use_slot` | `slot` (texte) | Utiliser un emplacement actif. Si une étape ouverte et non résolue y est liée, son écran s'ouvre : `EventsTrahisonPuzzle` (`reason: open`) à ce joueur seul ; un mécanisme qui réagit à l'usage direct (levier, bouton) est actionné | Aucune réponse propre : `EventsTrahisonPuzzle` et `EventsTrahisonRoom` |
| `puzzle_input` | `step` (texte), `input` (objet propre au mécanisme) | Un geste sur l'écran du mini-jeu : `three_dials` attend `{ "dial": 0..2, "delta": -9..9 }`, `ring_dial` `{ "ring": 0..4, "delta": 1 | -1 }`, `keypad` `{ "key": "0".."9" | "del" | "clear" | "enter" }`, `pickup_set` aucun geste, `code_breaker` `{ "key": "0".."9" | "del" | "clear" | "enter" }`, `wiring` `{ "op": "connect", "left": 0..5, "right": 0..5 }` ou `{ "op": "disconnect", "left" | "right": 0..5 }`. Appliqué dans l'ordre d'arrivée | `EventsTrahisonPuzzle` (`input`, puis `solved` si résolu) à tous ceux qui regardent ; `EventsTrahisonRoom` si une étape est résolue |
| `pick_up` | `slot` (texte) | Ramasser les objets (`type: item`) visibles dans l'emplacement : ils vont dans le sac de la salle, **au nom de celui qui ramasse** (`items[].holder`) | `EventsTrahisonRoom` |
| `give_item` | `item` (texte), `to` (indice de joueur) | Remettre un objet qu'on porte à un autre joueur. Le backend vérifie la portée : 4 blocs (plus 1 de tolérance pour l'âge des positions) d'après les positions que les clients envoient (`EventsTrahisonZone`) ; sans position, être dans la même zone connue suffit ; sinon `ERROR_TRAHISON_TOO_FAR`. Le destinataire doit être vivant et, hors temps libre, membre du groupe | `EventsTrahisonRoom` (le porteur change) |
| `use_item` | `item`, `slot` | Utiliser un objet qu'on porte sur un emplacement actif : l'étape ouverte qui y est liée doit le prendre (`Mechanism.onItem`), l'objet est alors consommé et l'étape peut se résoudre. Sinon `ERROR_TRAHISON_ITEM_REFUSED` et l'objet reste | `EventsTrahisonPuzzle` / `EventsTrahisonRoom` |
| `combine` | `a`, `b` | Combiner deux objets qu'on porte (deux exemplaires de la même chose si `a` = `b`) d'après les **recettes** de la salle (`combines[] { a, b, result }`, dans l'un ou l'autre ordre) : les deux sont consommés, le résultat est à celui qui combine. Sans recette : `ERROR_TRAHISON_NO_COMBINATION` | `EventsTrahisonRoom` |
| `replay` | | Temps libre seulement (D8), voir ci-dessous | `EventsTrahisonRoom` et `EventsTrahisonPuzzle` (`reason: reset`) |

Toute action de salle peut porter `group` (0 à 2) et `round` (1 à 3) : la salle visée. `group` absent : la salle du groupe de
l'expéditeur dans cette manche. `round` absent : la manche en cours (**les clients d'avant D8 n'ont donc rien à changer** : sans `round`,
tout marche comme avant). Un autre groupe ou une autre manche n'est accepté qu'en `FREE_TIME` ou pour un joueur libre de visiter (D22, `viewer.can_roam`), sinon `ERROR_TRAHISON_NOT_IN_ROOM` ;
une salle qui n'existe pas (manche pas encore jouée, `group` > 2) : `ERROR_TRAHISON_NO_ROOM` ; un `round` qui n'est pas un numéro de manche : `ERROR_TRAHISON_BAD_ACTION`.

#### Temps libre : visiter et rejouer (décision D8)

En `FREE_TIME`, tout joueur vivant peut explorer **toutes les salles ouvertes depuis le début de la partie** (celles de la manche en
cours, et celles des manches précédentes : au temps libre de la manche 3, neuf salles) et y jouer :

- **Les salles des manches précédentes sont gardées** : le moteur de salles les indexe par (hub, manche, groupe), le backend ne les
  efface plus quand la manche suivante démarre (seulement à la fin de la partie), et leur état est enregistré avec la manche qui les a
  jouées (`rounds[].rooms`). Un redémarrage du backend remet **toutes** les salles de toutes les manches (un état enregistré avant D8,
  sans manche, prend le numéro de la manche qui le porte). Elles servent au temps libre et, D22, à un groupe qui a terminé sa salle en `ROOMS` ou `VOTE` : sinon une action sur
  une salle d'une autre manche est refusée (`ERROR_TRAHISON_NOT_IN_ROOM`), et un joueur ne reçoit aucun message des salles des manches
  passées ;
- l'**adressage** : `group` et `round` de l'action (voir plus haut), ou `round` seul (le groupe de l'expéditeur dans cette manche) ; un
  client sans `round` vise la manche en cours (compatible avec les clients d'avant). Les messages `EventsTrahisonRoom` et
  `EventsTrahisonPuzzle` portent `round` ; le client range ses salles par (manche, groupe) ;
- il reçoit `EventsTrahisonRoom` et `EventsTrahisonPuzzle` de ces salles (en jeu, un joueur vivant d'un autre groupe n'en reçoit
  aucun) ; les vues sont celles d'un joueur, sans solution ;
- toutes les actions de salle lui sont ouvertes, une salle finie comprise (elle accepte `pick_up`, etc., rien de plus) ;
  `puzzle_input` porte le `group` et le `round` de la salle ; un objet qu'il ramasse est à lui dans le sac de cette salle ;
- `replay` remet la salle visée à un état neuf : mêmes membres et même place, sac vide, heures remises à zéro, étapes
  rouvertes, **nouvelle graine** (donc de nouvelles solutions : un joueur qui rejoue une salle qu'il vient de finir ne rejoue pas
  de mémoire). La graine d'un rejeu se déduit de la graine précédente et du nombre de rejeux (`RoomService.replaySeed`) : même
  histoire, mêmes solutions, et la salle (graine, compte `replays`) est enregistrée avec la partie. `EventsTrahisonPuzzle`
  (`reason: reset`) est envoyé pour chaque étape ;
- **aucun point, rien ne s'ouvre** : une salle terminée en temps libre n'est jamais marquée « résolue » (`roomSolved`, journal
  `room_solved`) et n'ouvre aucune porte, même celle d'une manche passée ; le groupe qui n'avait pas fini sa salle ne gagne rien non
  plus à la voir finie par un visiteur.

```json
{ "type": "EventsTrahisonAction", "data": { "action": "puzzle_input", "step": "s2", "input": { "dial": 0, "delta": 3 } } }
```

Erreurs : `ERROR_TRAHISON_NO_ROOM`, `ERROR_TRAHISON_NOT_IN_ROOM`, `ERROR_TRAHISON_BAD_ACTION`, `ERROR_TRAHISON_BAD_INPUT`,
`ERROR_TRAHISON_SLOT_INACTIVE`, `ERROR_TRAHISON_STEP_NOT_OPEN`, `ERROR_TRAHISON_STEP_SOLVED`, `ERROR_TRAHISON_NOTHING_TO_PICK`,
`ERROR_TRAHISON_ROOM_FINISHED`, `ERROR_TRAHISON_NO_SUCH_ITEM` (on ne porte pas l'objet), `ERROR_TRAHISON_TOO_FAR`,
`ERROR_TRAHISON_NO_COMBINATION`, `ERROR_TRAHISON_ITEM_REFUSED` (section 5).

## 4. Routes HTTP

Préfixe `/events/trahison/…` (définitions de salles, schématiques, plan). Chaque route ajoutée est aussi reportée
dans [`../backend-reference.md`](../backend-reference.md). Les champs sont en `snake_case`.

### 4.1 Le plan fixe (étape T2.1)

| Méthode | Route | Accès | Effet |
|---|---|---|---|
| `GET` | `/events/trahison/layout/{hub}` | Tout joueur connecté | Dernière version du plan du hub (ou, avec `?version=n`, cette version : le client d'une partie lit celle de `layout_version` ; version inconnue : la dernière). Un hub sans plan donne `version: 0` et des listes vides (pas de 404). Le plan n'a pas de secret : les clients s'en servent pour connaître leur zone |
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
  "hiding_spots": [ { "id": "spot_1", "position": { "x": 3, "y": 65, "z": -4 } } ],
  "results_board": { "position": { "x": -6, "y": 65, "z": 12 }, "facing": "south", "width": 12, "height": 6 }
}
```

| Champ | Type | Notes |
|---|---|---|
| `base_version` | entier | Facultatif, requête seulement : la version que l'éditeur avait chargée. Si une plus récente existe : `409 ERROR_TRAHISON_LAYOUT_CONFLICT` (`details.version` donne la version actuelle) |
| `draft` | booléen | Brouillon : saute les contrôles de complétude (voir ci-dessous). Un plan non brouillon est celui que le jeu utilisera |
| `zones[].type` | texte | `hall`, `corridor`, `doors`, `room_slot`, `vote`, `cabin`, `final`, `alcove`. Plusieurs boîtes permises par zone |
| `doors[].type` | texte | `color`, `room_exit`, `cabin`, `final`, `plain`. `block` : la boîte des faux blocs. `zone_a`, `zone_b` : les deux zones reliées |
| `doors[].threshold` | texte ou absent | Portes `color` et porte `final`, facultatif. Porte `color` : la zone devant la porte (côté hall) où les joueurs présentent leurs bracelets (T2.3) ; sans elle, c'est `zone_a`. Une zone par porte : si trois portes partagent la même `zone_a`, il faut un seuil propre à chacune. Porte `final` (T2.5) : la zone du **pupitre**, devant la porte ; sans elle, la porte finale s'actionne de n'importe où. Doit exister (`BAD_REFERENCE`, `field=threshold`) |
| `doors[].round`, `doors[].colors` | entier, deux textes | Portes `color` seulement : manche 1 à 3 ; deux noms de couleur de `GameColor` en minuscules (`red`, `green`, `blue`), dans n'importe quel ordre |
| `room_slots[]` | | `round` 1 à 3 ; `origin` : le coin minimum de la place (48 x 48 x 24) ; `orientation` : `north`, `east`, `south`, `west` |
| `cabins[]` | | `serves` : la paire de couleurs dont elle sert le vote (`red_green`, `red_blue`, `green_blue`) ; `side` : `pair` ou `solo` ; `zone` : une zone de type `cabin` ; `door` : une porte de type `cabin` |
| `hiding_spots[]` | | Cachette des objets du temps libre |
| `results_board` | objet ou absent | Le tableau des résultats du hall (T2.4), facultatif : `position` (le bloc le plus bas, **à gauche quand on fait face au tableau**), `facing` (`north`, `east`, `south`, `west` : le côté où se tiennent les lecteurs, c'est-à-dire la direction que regarde le tableau), `width` et `height` en blocs (4 à 64 : `BAD_BOX` sinon). Il s'étend de `width` blocs vers la droite des lecteurs (vers +x pour `south`, -x pour `north`, +z pour `west`, -z pour `east`) et de `height` blocs vers le haut, et se dessine sur la face des blocs côté `facing`. Les quatre champs sont obligatoires dans un plan complet (`MISSING_FIELD`, id `results_board`). Sans lui, les résultats vont sur le HUD |

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

#### Tableau des résultats dans l'éditeur (T2.4)

Le panneau a un onglet **Tableau** (au plus un élément, d'identifiant fixe `results_board`). Son formulaire : le côté des lecteurs
(`facing`), la largeur et la hauteur (clic : ±1, Maj : ±4), et **Ajuster à la sélection** : le tableau prend la boîte
sélectionnée (face du côté des lecteurs, largeur le long de leur main droite, hauteur depuis le bas de la boîte). Il se dessine
dans le monde comme une boîte couleur d'accent, avec son identifiant et son côté. Le plan est enregistré avec lui.

### 4.2 ter Éditeur de salle (client, étape T3.2)

- `/phantasmon event trahison room new <id>` : active l'éditeur de salle (admins, monde **solo** en créatif). Le gabarit de 48 x 48 x 24
  est posé là où se tient le joueur, tourné dans le sens de son regard. `room load <id>` colle une salle enregistrée au même endroit (la
  boîte du gabarit est vidée d'abord) et ramène emplacements et chaîne. `room list` liste les salles du backend. `room off` quitte.
- Touche **K** : le panneau (emplacements, salle) et, de là, l'écran de la chaîne. Les clics de sélection (A gauche, B droit) ne sont pris
  que **bâton en main** : sans bâton, le joueur construit normalement.
- Enregistrer : `POST /events/trahison/rooms/{id}` (la définition) puis `POST /events/trahison/rooms/{id}/schematic.json` (les blocs, Sponge v2
  en repère canonique : voir `guide-editeur-salle.md`, en base64 dans un corps JSON). `PhantasmonApi.get` et `post` suffisent : plus de réflexion.
  La liste des mécanismes vient de `GET /events/trahison/mechanisms` (lue à l'ouverture de l'éditeur ; liste intégrée seulement en cas d'échec).

### 4.2 bis Commandes et touche du client provisoire (étape T1.4)

- `/phantasmon event trahison menu` : l'écran du joueur (liste des 9, étape, chrono, boutons de l'étape). Touche réglable
  `key.phantasmon_events.trahison_menu` (catégorie « Phantasmon Events »), **non assignée** par défaut.
- `/phantasmon event trahison gm` (admins) : l'écran du maître du jeu (créer, factices, démarrer, pause, suivant, prolonger,
  terminer, points d'un bracelet, votes en cours).
- `/phantasmon event trahison door <id> open|close` (admins) : l'action `set_door` du maître du jeu, pour ouvrir ou fermer une
  porte du plan à la main (essais, secours un soir de partie).
- À l'entrée dans un hub, le client envoie `view` (avec `hub`), la renvoie tant que le WebSocket n'est pas prêt, puis toutes
  les 30 s (une reconnexion du WebSocket n'est pas signalée au module). Le HUD (bracelet, étape, chrono) et les répliques
  (`Say`) sont dessinés par `hud/BraceletHud` et `hud/Announcer`.

### 4.3 Les salles (étape T3.1)

Toutes les routes de la banque sont **réservées aux admins**, lecture comprise : une définition contient les textes d'aide et
toute la chaîne (spoilers). Un joueur n'en reçoit rien : il n'a besoin que des messages `EventsTrahisonRoom` de sa salle (403
pour un non-admin).

| Méthode | Route | Accès | Effet |
|---|---|---|---|
| `GET` | `/events/trahison/rooms` | Admin | Liste : `[{ id, name, author, version, difficulty, expected_seconds, step_count, slot_count, has_schematic, updated_at, measured }]` : `expected_seconds` est la somme, sur les étapes, de la médiane **mesurée** (T3.6, table `trahison_step_times`) pour les étapes qui ont au moins 3 mesures, et de l'estimation du mécanisme pour les autres ; `measured` vaut `true` quand au moins une étape a utilisé une mesure. `difficulty` reste la somme des notes des mécanismes |
| `GET` | `/events/trahison/rooms/{id}` | Admin | La définition (404 `ERROR_TRAHISON_ROOM_NOT_FOUND`) |
| `PUT` | `/events/trahison/rooms/{id}` | Admin | Crée ou remplace la salle. Le serveur numérote `version` (+1 à chaque enregistrement) et calcule `difficulty` et `expected_seconds` (sommes sur les étapes) : ceux du corps sont ignorés |
| `POST` | `/events/trahison/rooms/{id}` | Admin | Identique au `PUT` (le client n'a pas de `put`) |
| `DELETE` | `/events/trahison/rooms/{id}` | Admin | Supprime la définition et le fichier des blocs |
| `GET` | `/events/trahison/rooms/{id}/schematic` | Admin | Les blocs (`application/octet-stream`, format libre du client) ; 404 `ERROR_TRAHISON_ROOM_SCHEMATIC_MISSING` s'il n'y en a pas |
| `POST` / `PUT` | `/events/trahison/rooms/{id}/schematic` | Admin | Remplace les blocs (corps binaire, 16 Mio au plus, non vide ; la définition doit exister). `{ id, size }` |
| `GET` | `/events/trahison/rooms/{id}/schematic.json` | Admin | Les mêmes blocs en JSON : `{ id, version, data }`, `data` en base64 (404 comme la route binaire) |
| `POST` | `/events/trahison/rooms/{id}/schematic.json` | Admin | Corps `{ "data": base64 }` ; mêmes limites (16 Mio **décodés**) et mêmes erreurs (`400 ERROR_TRAHISON_ROOM_SCHEMATIC_INVALID` aussi pour un base64 invalide ou une donnée absente). `{ id, size }` |
| `GET` | `/events/trahison/telemetry/step-times` | Admin | T3.6 : les temps mesurés des étapes réellement résolues : `{ mechanisms: [{ mechanism, count, median_seconds, average_hints }], rooms: [{ room, median_seconds, steps: [{ step, mechanism, count, median_seconds }] }] }` ; `median_seconds` d'une salle = la somme des médianes de ses étapes mesurées. Un rejeu du temps libre, `debug_solve`, les factices et l'ouverture de fin de `ROOMS` ne comptent pas |
| `GET` | `/events/trahison/mechanisms` | Admin | Les mécanismes du backend, triés par id : `[{ id, clue_roles, slot_roles: [{ role, kinds: [...] }], params: [{ name, type, min, max, def, description }], difficulty, expected_seconds }]` (difficulté et durée pour les paramètres par défaut ; `type` : `int`, `bool` ou `text`). `slot_roles` : les rôles qu'une étape lie à des emplacements, avec les genres d'emplacement que chacun accepte (le rôle `exit` est implicite : genre `exit`) ; le client n'a plus de table de rôles, la liste vide d'un mécanisme veut dire « rien n'est contrôlé ». Les `clue_roles` sont ceux des paramètres par défaut |

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
section 5. Mécanismes connus à ce jour (rôle d'emplacement : genres acceptés) : `debug_button` (un appui résout ; `press` : `terminal`, `keypad`, `lever`,
`plate`, `pedestal`, `secret`, `inner_door`), `three_dials` (trois molettes de 0 à 9 ; indice `code` de type `digits` ; `dials` : `terminal`, `pedestal`),
`ring_dial` (anneaux de symboles liés ; indice `symbols` de type `symbols`, voir la vue plus haut ; `terminal` : `terminal`, `pedestal`), `keypad` (digicode mural ;
indice `code` de type `digits` ; `keypad` : `keypad`, `terminal` ; voir plus haut), `pickup_set` (objets cachés ; indices `spot_1`..`spot_6` de type `item` et `document` de type
`document` ; aucun rôle d'emplacement : on ramasse seulement ; voir plus haut), `code_breaker` (décodeur ; aucun indice ; `terminal` : `terminal`, `pedestal` ; voir plus haut), `wiring` (câblage ; indices `scheme` de type `colors` et `scheme_text` de type `document`, l'un ou l'autre ; `panel` : `terminal`, `pedestal` ; voir plus haut), `three_levers` (trois leviers ; aucun indice ; `lever_1`, `lever_2`, `lever_3` : `lever` ; voir plus haut).

Le validateur contrôle en plus les liaisons des rôles : un rôle que le mécanisme ne déclare pas (hors `exit`), ou lié à un emplacement d'un genre qu'il n'accepte pas, donne
`ERROR_TRAHISON_ROOM_BAD_KIND` (`info` : `role=<rôle>;unknown` ou `role=<rôle>;needs=<genre>|<genre>`) ; et `keypad`, `pickup_set`, `wiring` et `three_levers` (rôle `lever_n` non lié : `missing=slot:lever_n` ; deux rôles sur le même emplacement : `duplicate=slot:lever_n`) refusent une étape dont un indice dont les joueurs
ont besoin n'est placé nulle part ou seulement à la résolution (`ERROR_TRAHISON_ROOM_BAD_GIVE`, `info` : `missing=<indice>;when=open`, `missing=slot:<rôle>` ou `missing=param:document`).

## 5. Codes d'erreur

Préfixe `ERROR_TRAHISON_`. Tous sont envoyés par `EventsTrahisonSay` (genre `error`) à l'expéditeur seulement. Clé i18n :
`phantasmon_events.error.<code en minuscules>` (à ajouter dans `en_us.json` et `fr_fr.json` par le client, étape T1.4).

| Code | Canal | Cas | Clé i18n |
|---|---|---|---|
| `ERROR_TRAHISON_ADMIN_REQUIRED` | WS | `create` ou `set_game_master` envoyé par un non-admin | `phantasmon_events.error.error_trahison_admin_required` |
| `ERROR_TRAHISON_NOT_GAME_MASTER` | WS | `EventsTrahisonGm` d'un compte qui n'est pas maître du jeu de la partie (et, en `LOBBY`, n'est pas admin) | `phantasmon_events.error.error_trahison_not_game_master` |
| `ERROR_TRAHISON_NO_DRAW` | WS | `redraw` ou `replace_room` quand la partie n'a pas de tirage | `phantasmon_events.error.error_trahison_no_draw` |
| `ERROR_TRAHISON_NO_GAME_MASTER` | WS | `start` en mode `GAME_MASTER` sans aucun maître du jeu nommé | `phantasmon_events.error.error_trahison_no_game_master` |
| `ERROR_TRAHISON_GAME_MASTER_PLAYS` | WS | **Plus émise depuis D12** (un maître du jeu peut jouer) ; le code reste défini | `phantasmon_events.error.error_trahison_game_master_plays` |
| `ERROR_TRAHISON_NO_HUB` | WS | `hub` absent, ou qui n'est pas un hub miroir, et l'expéditeur ne joue ni ne dirige aucune partie | `phantasmon_events.error.error_trahison_no_hub` |
| `ERROR_TRAHISON_NOT_IN_HUB` | WS | `join` ou `view` de quelqu'un qui n'est dans aucun hub | `phantasmon_events.error.error_trahison_not_in_hub` |
| `ERROR_TRAHISON_NO_GAME` | WS | Le hub n'a pas de partie (action autre que `view`, ou `Gm` autre que `create`) | `phantasmon_events.error.error_trahison_no_game` |
| `ERROR_TRAHISON_GAME_EXISTS` | WS | `create` alors que le hub a une partie non finie | `phantasmon_events.error.error_trahison_game_exists` |
| `ERROR_TRAHISON_BAD_ARGUMENT` | WS | Champ manquant ou invalide (`action`, `door`, `vote`, `policy`, `settings`…) | `phantasmon_events.error.error_trahison_bad_argument` |
| `ERROR_TRAHISON_UNKNOWN_ACTION` | WS | `action` inconnue | `phantasmon_events.error.error_trahison_unknown_action` |
| `ERROR_TRAHISON_NOT_IN_LOBBY` | WS | `join`, `leave`, `start`, `add_bots` ou `set_game_master` alors que la partie a commencé | `phantasmon_events.error.error_trahison_not_in_lobby` |
| `ERROR_TRAHISON_GAME_FULL` | WS | Neuf joueurs déjà, ou pas assez de places pour les factices | `phantasmon_events.error.error_trahison_game_full` |
| `ERROR_TRAHISON_ALREADY_JOINED` | WS | `join` d'un inscrit | `phantasmon_events.error.error_trahison_already_joined` |
| `ERROR_TRAHISON_NOT_A_PLAYER` | WS | Action de jeu de quelqu'un qui n'est pas inscrit, ou `leave` sans être inscrit | `phantasmon_events.error.error_trahison_not_a_player` |
| `ERROR_TRAHISON_NOT_ENOUGH_PLAYERS` | WS | `start` sans exactement 9 inscrits | `phantasmon_events.error.error_trahison_not_enough_players` |
| `ERROR_TRAHISON_WRONG_PHASE` | WS | Action hors de son étape, ou `next`, `pause`, `resume`, `extend`, `set_points` hors d'une partie en cours | `phantasmon_events.error.error_trahison_wrong_phase` |
| `ERROR_TRAHISON_PAUSED` | WS | Action de jeu pendant une pause, ou `pause` sur une pause du maître du jeu | `phantasmon_events.error.error_trahison_paused` |
| `ERROR_TRAHISON_NOT_PAUSED` | WS | `resume` alors que la partie n'est pas en pause | `phantasmon_events.error.error_trahison_not_paused` |
| `ERROR_TRAHISON_DEAD` | WS | Action de jeu d'un fantôme | `phantasmon_events.error.error_trahison_dead` |
| `ERROR_TRAHISON_NO_BODY` | WS | `take_bracelet` ou `body_seen` sans corps, ou bracelet déjà pris | `phantasmon_events.error.error_trahison_no_body` |
| `ERROR_TRAHISON_BODY_TOO_FAR` | WS | `take_bracelet` à plus de 3 blocs, `body_seen` à plus de 8, ou hors de la zone du corps | `phantasmon_events.error.error_trahison_body_too_far` |
| `ERROR_TRAHISON_WRONG_DOOR` | WS | `pass_door` : la couleur du bracelet n'est pas sur cette porte | `phantasmon_events.error.error_trahison_wrong_door` |
| `ERROR_TRAHISON_DOOR_INCOMPATIBLE` | WS | `pass_door` : cette porte contredit les portes déjà prises | `phantasmon_events.error.error_trahison_door_incompatible` |
| `ERROR_TRAHISON_ALREADY_PASSED` | WS | `pass_door` : une porte a déjà été passée dans cette manche | `phantasmon_events.error.error_trahison_already_passed` |
| `ERROR_TRAHISON_ALREADY_VOTED` | WS | `vote` : le camp (la paire entière, ou le solo) a déjà voté | `phantasmon_events.error.error_trahison_already_voted` |
| `ERROR_TRAHISON_CANNOT_OPEN_FINAL` | WS | `open_final_door` : le bracelet de poignet ne lit pas 9 | `phantasmon_events.error.error_trahison_cannot_open_final` |
| `ERROR_TRAHISON_NOT_AT_FINAL_DOOR` | WS | `open_final_door` hors de la zone du pupitre, quand la porte finale du plan en a un (T2.5) | `phantasmon_events.error.error_trahison_not_at_final_door` |
| `ERROR_TRAHISON_NOT_IN_CABIN` | WS | `close_cabin` hors de toute cabine ; `vote` depuis l'extérieur d'une cabine (`cabins_required`) | `phantasmon_events.error.error_trahison_not_in_cabin` |
| `ERROR_TRAHISON_WRONG_CABIN` | WS | `close_cabin` ou `vote` depuis une cabine qui n'est pas la sienne (paire de couleurs ou côté), ou plan sans cabine pour lui | `phantasmon_events.error.error_trahison_wrong_cabin` |
| `ERROR_TRAHISON_CABIN_NOT_CLOSED` | WS | `vote` depuis sa cabine pas encore verrouillée (le partenaire d'une paire a encore du temps pour entrer) | `phantasmon_events.error.error_trahison_cabin_not_closed` |
| `ERROR_TRAHISON_GAME_ENDED` | WS | Action sur une partie finie | `phantasmon_events.error.error_trahison_game_ended` |
| `ERROR_TRAHISON_LAYOUT_*` | HTTP | Plan refusé, en conflit, hub invalide : voir la section 4.1 | (voir la section 4.1) |

Salles (étape T3.1) :

| Code | Canal | Cas | Clé i18n |
|---|---|---|---|
| `ERROR_TRAHISON_NO_ROOM` | action | Aucune salle pour ce groupe dans cette manche (manche pas jouée, groupe hors 0 à 2) | `phantasmon_events.error.*` |
| `ERROR_TRAHISON_NOT_IN_ROOM` | action | Le joueur n'est pas du groupe qui joue la salle | idem |
| `ERROR_TRAHISON_BAD_ACTION` | action | Action de salle mal formée ou étape inconnue | idem |
| `ERROR_TRAHISON_BAD_INPUT` | action | Geste refusé par le mécanisme (champ manquant, hors limites) | idem |
| `ERROR_TRAHISON_SLOT_INACTIVE` | action | Emplacement inconnu ou qui ne montre rien pour l'instant | idem |
| `ERROR_TRAHISON_STEP_NOT_OPEN` | action | L'étape attend encore d'autres étapes (`needs`) | idem |
| `ERROR_TRAHISON_STEP_SOLVED` | action | L'étape est déjà résolue | idem |
| `ERROR_TRAHISON_NOTHING_TO_PICK` | action | Rien à ramasser dans cet emplacement | idem |
| `ERROR_TRAHISON_ROOM_FINISHED` | action | La sortie est ouverte : la salle n'accepte plus d'action (sauf en temps libre, D8) | idem |
| `ERROR_TRAHISON_NO_SUCH_ITEM` | action | L'objet n'est pas porté par l'expéditeur | `phantasmon_events.error.error_trahison_no_such_item` |
| `ERROR_TRAHISON_TOO_FAR` | action | Le destinataire d'un `give_item` est à plus de 4 blocs (et 1 de tolérance) | idem |
| `ERROR_TRAHISON_NO_COMBINATION` | action | Aucune recette de la salle pour ces deux objets | idem |
| `ERROR_TRAHISON_ITEM_REFUSED` | action | L'emplacement ne prend pas cet objet : il n'est pas consommé | idem |
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
