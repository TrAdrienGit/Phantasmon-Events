# Architecture du jeu de trahison

> Écrit le 2026-11-13, après la validation du [plan](plan.md). Ce document fixe **comment** on construit le jeu.
> Les règles du jeu sont dans le plan ; le découpage du travail est dans [`etapes/`](etapes/README.md).
> Toute étape se lit avec ce document.

## 1. Vue d'ensemble

```
Serveur Minecraft de la communauté          Backend Phantasmon (un seul processus)        Copies du hub
(aucun mod serveur, Simple Voice Chat)      + module Events (qui contient ce jeu)          (monde solo, serveur dédié)
                                                                                            
9 joueurs, vrais blocs du complexe   ◄────►  toute la logique du jeu :              ◄────►  maître du jeu,
mod Phantasmon + Events                     étapes, chronos, bracelets, portes,            spectateurs
= affichage et saisie seulement             votes, points, rôles, salles, tirages
```

- **Le backend décide de tout.** Un client n'affiche que ce que le backend lui envoie et n'envoie que des actions
  (« je vote », « je tourne l'anneau 2 »). Aucune règle n'est calculée côté client.
- **Le serveur Minecraft ne sait rien du jeu.** Tout ce que le jeu ajoute au monde est affiché par le mod de
  chaque joueur : portes fermées (faux blocs), objets, corps, panneaux d'indices, écrans.
- **Les joueurs sont de confiance.** On ne se protège pas d'un client modifié. On protège quand même les secrets :
  un vote, un rôle ou une solution ne sont jamais envoyés à un client qui n'a pas à les connaître.

## 2. Où vit le code

Le jeu de trahison **fait partie de Phantasmon Events**. Ce n'est ni un autre dépôt, ni un autre mod, ni un autre
module : il est dans le même jar client et dans le même jar backend qu'Events, et s'installe avec lui (décision du
2026-11-13).

```
Phantasmon-Events/
  src/                        mod client phantasmon_events
    .../events/trahison/      ◄── le jeu, côté client
  backend/                    module chargé par le backend de Phantasmon (dossier addons/)
    .../events/trahison/      ◄── le jeu, côté backend
    resources/db/events/      ◄── ses tables, à la suite des migrations d'Events
  Documentation/trahison/     ce plan, cette architecture, les étapes
```

| Sujet | Choix | Pourquoi |
|---|---|---|
| Mod client | Le mod `phantasmon_events`, paquet `com.mystaria.events.trahison` | Un seul jar à installer ; le jeu utilise tout Events (hubs miroirs, blocs fantômes, PNJ, menus) |
| Backend | Le module `phantasmon-events-backend`, paquet `com.mystaria.phantasmon_backend.events.trahison` | Events s'injecte déjà dans le backend de Phantasmon ; le jeu passe par le même chemin |
| Base de données | L'historique Flyway d'Events (`db/events`, numéro suivant le dernier fichier présent) | Une seule suite de migrations pour Events |
| Mixins | Dans `com.mystaria.events.mixin`, déclarés dans `phantasmon_events.mixins.json` | Comme le reste d'Events |
| Textes | `assets/phantasmon_events/lang/en_us.json` et `fr_fr.json`, clés `phantasmon_events.trahison.*` | Idem |
| Commandes | Sous `/phantasmon event trahison …` (`EventsCommands`) | Idem |
| Code générique | Ce qui peut resservir à d'autres événements (zones, faux blocs, écrans de mini-jeu, vocal par zones) reste dans `trahison` tant que le jeu n'existe pas, dans des paquets séparables | On le remontera d'un cran dans Events quand un second événement en aura besoin |

### Phantasmon ne se modifie pas

Events est un add-on de Phantasmon, côté client comme côté backend. Le jeu de trahison passe **uniquement** par ce
que Phantasmon offre déjà aux add-ons :

- côté client : `PhantasmonApi` (messages `Events*`, appels HTTP, hubs, menus, PNJ, IA de combat) ;
- côté backend : les interfaces du paquet `addon` (`WsMessageHandler`, `PublicRoutesProvider`,
  `NpcOpponentProvider`, `HubKind`, `TournamentHook`) et les services du cœur qu'Events utilise déjà
  (`SessionRegistry`, `AdminService`, les hubs).

Si une sous-tâche bute sur quelque chose que ces points d'entrée ne permettent pas, l'agent **s'arrête et le
signale** : on cherche d'abord une autre façon de faire dans Events, et un nouveau point d'extension dans
Phantasmon ne se décide qu'avec la propriétaire.

La recherche du 2026-11-13 ([`solutions-techniques.md`](solutions-techniques.md)) a trouvé une solution dans
Events pour tout, sauf un point : un vrai combat où l'équipe du joueur est imposée (le « gardien »). Il sort de la
première version ; le combat-énigme le remplace, sous la forme d'un mini-jeu à nous.

Showtime ne se modifie pas non plus : on utilise son API publique (`ShowtimeHooks`).

### Paquets

Client (`com.mystaria.events.trahison`) :

| Paquet | Contenu |
|---|---|
| (racine) | `Trahison` (enregistrement, appelé par `PhantasmonEvents`), commandes |
| `net` | Envoi et réception des messages, `GameView` (dernier état reçu) |
| `hud` | Bracelet, chrono, annonces de l'animateur |
| `world` | Faux blocs, zones, objets cliquables, corps et mannequins, joueurs masqués |
| `room` | Salle en cours, inventaire virtuel, documents, indices muraux |
| `minigame` | Cadre des écrans de mini-jeu, un fichier par mini-jeu |
| `edit` | Éditeur du plan, éditeur de salle, export des schématiques |
| `voice` | Zones sonores |
| `gm` | Panneau du maître du jeu |

Backend (`com.mystaria.phantasmon_backend.events.trahison`) :

| Paquet | Contenu |
|---|---|
| (racine) | `TrahisonWsHandler` (un `WsMessageHandler` de plus, à côté d'`EventsWsHandler`) |
| `rules` | Règles pures, sans Spring : barème, couleurs, portes valides, conditions de victoire |
| `game` | `Game` (état), `Phase`, `GameService` (machine à états, chronos), vues envoyées aux clients, joueurs factices |
| `layout` | Le plan fixe : zones, portes, cabines, places |
| `room` | Définition d'une salle, salle en cours, interface `Mechanism`, tirage, indices automatiques |
| `room.mechanism` | Un fichier par mécanisme |
| `admin` | Routes et actions du maître du jeu |
| `telemetry` | Temps mesurés par étape de salle |

## 3. Les points d'appui existants

À lire avant d'écrire du code qui les utilise.

| Besoin | Ce qui existe | Où |
|---|---|---|
| Points d'extension du backend | `WsMessageHandler`, `PublicRoutesProvider`, `NpcOpponentProvider` | `Phantasmon-Backend/.../addon/` ; leur usage : `backend/.../events/EventsWsHandler.java` |
| Migrations d'Events | `EventsDatabase`, dossier `db/events` | `backend/.../events/EventsDatabase.java` |
| Enregistrement côté client | `PhantasmonEvents` (point d'entrée), `EventsCommands` | `src/main/java/com/mystaria/events/` |
| Messages WebSocket d'un add-on | `PhantasmonApi.send`, `PhantasmonApi.onEventsMessage` (le type doit commencer par `Events`) | `Phantasmon-Client/.../api/PhantasmonApi.java` |
| Appels HTTP avec le jeton du joueur | `PhantasmonApi.get / post / delete` | idem |
| Savoir si le joueur est admin | `PhantasmonApi.isAdmin()` ; côté backend `AdminService.requireAdmin` | idem |
| Coordonnées de hub | `HubCoordinates.toHub / toLocal`, `HubController.joinedAnchor()` | `Phantasmon-Client/.../hub/` |
| Membres du hub et avatars | `HubController.members()`, `HubAvatars` | idem |
| Faux blocs affichés chez tous | Blocs fantômes des hubs miroirs | `Phantasmon-Events/.../hub/MirrorGhosts.java` et ses mixins |
| Entité joueur locale (PNJ, corps) | `EventNpcEntity` | `Phantasmon-Events/.../npc/` |
| Lire un `.schem` ou un `.litematic` | `HubSchematic.read` | `Phantasmon-Client/.../hub/HubSchematic.java` |
| Écran dessiné sur une toile à échelle fixe | `PhantasmonCanvasScreen`, `MenuUi` | `Phantasmon-Client/.../gui/` |
| Vocal du hub | `HubVoice` (client et backend), décision D-36 | `Phantasmon-Client/.../voice/`, `Phantasmon-Backend/.../hub/HubVoice.java` |
| Combat contre un PNJ | `PhantasmonApi.startNpcBattle`, `NpcOpponentProvider` | cœur et `Phantasmon-Events/.../npc/` |
| Pokémon affiché à côté du joueur | Les Ghosts de Phantasmon | `Phantasmon-Client/.../ghost/` |
| Page dans le menu Phantasmon, onglet du gestionnaire de hubs | `PhantasmonApi.registerMenuPage`, `registerHubManagerTab` | `PhantasmonApi` |

## 4. Le modèle du jeu (backend)

Une partie à la fois par hub. L'état complet tient dans un objet `Game`, gardé en mémoire et enregistré en JSON
dans la table `trahison_games` à chaque changement d'étape : si le backend redémarre en pleine soirée, la partie
reprend.

```
Game
  id, hub, layoutVersion, seed, settings (durées, nombre d'objets, rôles actifs, maître du jeu ou automatique)
  phase, round (1..3, puis les votes de la porte finale), deadline, paused
  players[9]
    uuid, name, connected
    alive, restrained (jusqu'à quand, où), zone
    wrist      : Bracelet { id, points, color, pairOrSolo }      jamais retiré de son vivant
    carried[]  : identifiants de bracelets ramassés
    role, items[], inventaire de la salle en cours
  bracelets[9]  (points, couleur, statut, porteur ou lieu du corps)
  rounds[]
    assignment  : couleur et statut de chaque bracelet
    doors[3]    : bracelets passés, salle attribuée
    rooms[3]    : RoomInstance (définition, graine, état de chaque étape, temps)
    votes[3]    : vote de la paire, vote du solo, qui a voté, résultat
  log[]         : tout ce qui s'est passé, horodaté (pour la fin de partie et les réglages)
```

### Les étapes (`Phase`)

```
LOBBY ─► PROLOGUE ─► DOORS ─► ROOMS ─► FREE_TIME ─► VOTE ─► RESULTS ─┐
                       ▲                                              │ manches 1 à 3
                       └──────────────────────────────────────────────┘
                                                        après la manche 3
FINAL ◄──────────────────────────────────────────────────────────────┘
  │  toutes les 10 minutes : VOTE ─► RESULTS ─► FINAL
  └─► ENDED   (quelqu'un ouvre la porte finale, ou le maître du jeu termine)
```

- Chaque étape a une durée dans `settings`. En mode automatique, la fin du chrono fait passer à la suivante. Avec
  un maître du jeu, il peut aussi mettre en pause, prolonger, forcer la suite.
- Les chronos sont tenus par le backend. Il envoie l'heure de fin ; le client affiche le temps restant.
- Les règles pures (`rules`) ne connaissent ni Spring ni l'horloge : elles se testent seules.

### Qui voit quoi

Le backend fabrique une vue **par destinataire**. C'est la seule porte de sortie de l'état.

| Information | Joueur vivant | Fantôme, spectateur | Maître du jeu |
|---|---|---|---|
| Étape, chrono, points et couleurs de tous | Oui | Oui | Oui |
| Votes de la manche en cours | Non, jusqu'aux résultats | Non, jusqu'aux résultats | Oui |
| Son rôle, ses objets, ses offres | Oui | — | Oui |
| Rôles et objets des autres | Non | Non | Oui |
| État de sa salle | Oui | Oui, de toutes | Oui |
| Solutions des salles | Jamais | Jamais | Oui |
| Qui est mort | Quand le corps est trouvé, ou aux résultats | Oui | Oui |

## 5. Le protocole

Tous les types commencent par `EventsTrahison` (le client de Phantasmon n'accepte que le préfixe `Events`).

| Message | Sens | Contenu |
|---|---|---|
| `EventsTrahisonView` | S→C | La vue complète du destinataire. Envoyée à chaque changement d'étape et à la connexion |
| `EventsTrahisonPatch` | S→C | Un changement ciblé : points, porte, objet, joueur, chrono |
| `EventsTrahisonRoom` | S→C | État d'une salle : étapes faites, emplacements actifs, indices visibles |
| `EventsTrahisonPuzzle` | S→C | État ou changement d'un mini-jeu ouvert (diffusé à ceux qui regardent l'écran) |
| `EventsTrahisonSay` | S→C | Une réplique de l'animateur, ou un chuchotement privé |
| `EventsTrahisonAction` | C→S | Une action du joueur : `{ "action": "...", ... }` |
| `EventsTrahisonZone` | C→S | Le joueur a changé de zone |
| `EventsTrahisonGm` | C→S | Une action du maître du jeu (refusée si le joueur n'est pas admin) |

- Les actions (`vote`, `pass_door`, `enter_cabin`, `close_cabin`, `use_slot`, `puzzle_input`, `pick_up`,
  `use_item`, `open_final_door`…) sont décrites dans l'étape qui les crée, puis reportées dans
  `Documentation/protocole.md`, que chaque étape tient à jour.
- Le backend répond à une action refusée par `EventsTrahisonSay` privé avec un code (`ERROR_TRAHISON_…`), traduit
  dans les fichiers de langue.
- Les routes HTTP (`/events/trahison/…`) servent aux gros contenus : définitions de salles, schématiques, plan.

## 6. Le monde côté client

### Coordonnées

Toutes les positions enregistrées (plan, salles, emplacements) sont en **coordonnées de hub** : `x` et `z` du hub
(`HubCoordinates.toHub`), `y` du monde. Les hubs miroirs ne sont jamais tournés et couvrent toute la hauteur. Une
salle garde ses positions **relatives à l'origine de sa place**.

### Le plan fixe (`layout`)

Décrit une fois par le maître du jeu avec l'éditeur, gardé par le backend, versionné :

| Élément | Champs |
|---|---|
| Zone | `id`, type (`hall`, `corridor`, `doors`, `room_slot`, `vote`, `cabin`, `final`, `alcove`), boîtes |
| Porte | `id`, type (`color`, `room_exit`, `cabin`, `final`, `plain`), boîte des faux blocs, les deux zones reliées |
| Place de salle | `id`, manche, origine, orientation |
| Cabine | `id`, porte du vote qu'elle sert, côté (`pair` ou `solo`), zone, porte |
| Cachette | `id`, position (pour les objets cachés du temps libre) |

Une **zone** sert à trois choses : savoir où est chaque joueur, isoler le son, et vérifier une action (« il faut
être dans la cabine pour voter »).

### Faux blocs

Une porte fermée est un mur de faux blocs posé dans une ouverture réelle. Un faux bloc arrête le joueur parce que
son propre jeu calcule ses collisions. L'inverse est impossible : retirer un vrai bloc à l'affichage ne permet pas
de le traverser, le serveur renvoie le joueur en arrière. Donc : **ouvertures réelles, fermetures fausses**.
On réutilise les blocs fantômes d'Events, ouverts à une source de plus (solution S1).

### Objets du jeu dans le monde

Un **emplacement** est une position marquée dans une salle ou dans le plan. Le client y affiche ce que le backend
demande (terminal, digicode, objet à ramasser, panneau d'indice, levier) sous la forme d'une entité locale, et
capte le clic du joueur avant qu'il parte vers le serveur (solution S3). Rien n'utilise de vrais coffres, panneaux ou leviers du serveur : le miroir ne copie pas le
contenu des coffres, et on ne pourrait pas les remettre à zéro.

### Joueurs dans un état spécial

| État | Chez les autres | Chez lui |
|---|---|---|
| Mort (fantôme) | Son vrai personnage n'est plus affiché ni entendu comme un vivant ; un corps reste au lieu de la mort | Vue libre, il traverse les faux blocs, ne clique sur rien |
| Attaché | Affiché attaché | Ses déplacements sont bloqués par son propre mod |
| Déconnecté | Un mannequin debout à son skin, « déconnecté » | — |
| Spectateur | Silhouette translucide ou invisible | Comme un fantôme |

## 7. Les salles

### Définition d'une salle

Créée dans l'éditeur en jeu, envoyée au backend :

```
RoomDefinition
  id, nom, auteur, version, difficulté (somme de ses étapes), durée estimée
  schematic        : les blocs, 48 × 48 × 24, relatifs à l'origine
  slots[]          : { id, kind, position, orientation, taille }      les emplacements
  steps[]          : la chaîne
    { id, mechanism, params, slots: { rôle → id d'emplacement }, needs: [étapes à finir avant], gives: [...] }
  secret           : la cachette de la salle, pour le temps libre
```

### Un mécanisme

Deux moitiés, une par côté, reliées par un identifiant (`ring_dial`, `keypad`…) :

- **Backend** (`Mechanism`) : `generate(seed, params)` tire la solution et fabrique les indices ;
  `onInput(state, player, input)` applique un geste et dit si c'est résolu ; `view(state, viewer)` donne ce
  qu'un client a le droit de voir (jamais la solution). Tout est déterministe pour une graine donnée, et testé seul.
- **Client** (`MechanismView`) : ce qui s'affiche à l'emplacement, et l'écran de mini-jeu s'il y en a un.

Un **indice** produit par un mécanisme est posé dans un emplacement d'un autre genre (mur, document, objet), choisi
dans la définition de la salle. C'est ce lien qui fait une chaîne.

### Écrans de mini-jeu partagés

- Un écran plein, dessiné sur une toile à échelle fixe, à la qualité du menu de Showtime.
- Tous ceux qui ouvrent le même écran voient le même état : chaque geste part au backend, qui le rediffuse.
- Le client affiche tout de suite son propre geste, puis se recale sur la réponse.
- Fermer l'écran ne perd rien : l'état est au backend.

### Équilibre

- Chaque mécanisme a une difficulté (1 à 3) et une durée estimée, corrigées par les temps mesurés (`telemetry`).
- Le tirage d'une manche prend 3 salles de même total.
- Indices automatiques à 10, 18 et 25 minutes sans progrès. À 30 minutes, la sortie s'ouvre et chacun perd 1 point.

## 8. Avant une soirée

1. Le maître du jeu crée une partie : le backend tire 9 salles et leur place.
2. Son client fabrique les fichiers à coller (`.schem` et `.litematic`), en tournant chaque salle selon sa place.
   C'est le client qui les écrit, parce que lui seul sait tourner un bloc de Minecraft.
3. L'admin du serveur les colle avec Axiom.
4. Le hub miroir reçoit les blocs ; le backend associe chaque place à sa salle ; les clients affichent les
   emplacements.
5. Le maître du jeu ouvre le lobby : 9 joueurs s'inscrivent, les autres sont spectateurs.

## 9. Règles pour quiconque code ici

1. Le backend décide, le client affiche. Aucune règle côté client.
2. Un secret ne quitte le backend que pour celui qui a le droit de le voir. Toute sortie passe par les vues.
3. Aucun mod serveur, aucune commande serveur, aucun vrai coffre.
4. Ne jamais modifier Phantasmon ni Showtime. Tout passe par leurs points d'entrée pour add-ons ; s'il en manque
   un, s'arrêter et le signaler. Dans Events, ne toucher au code existant (hubs, tournois, PNJ, IA) que si l'étape
   le dit.
5. Code, commentaires et messages de commit en anglais. Documentation en français. Textes du jeu dans les
   fichiers de langue `en_us.json` et `fr_fr.json`, toujours complets tous les deux.
6. Tout tirage part d'une graine : même graine, même résultat. Les règles pures ont des tests.
7. Types de messages préfixés `EventsTrahison` ; codes d'erreur préfixés `ERROR_TRAHISON_`.
8. Gradle du mod client tourne avec JDK 25 (Loom) ; le code vise Java 21. Le backend se compile en Java 21.
9. Après chaque changement : déployer (le mod avec `Phantasmon-Client/scripts/deploy-to-prod-server.sh --events`,
   le module backend avec `Phantasmon-Backend/scripts/deploy-ubuntu.sh`), puis attendre la validation en jeu.
   Commit et push seulement après cette validation.
10. Pas de nom de personne dans le code ni la documentation. Pas de contenu repris du jeu qui nous inspire : ni
    noms, ni textes, ni images, ni salles.
11. Les écrans visent la qualité du menu de Showtime.
12. Chaque étape met à jour la documentation qu'elle touche (`protocole.md`, ce fichier, l'état dans
    `etapes/README.md`, et `Documentation/backend-reference.md` d'Events pour toute route ou table ajoutée).
