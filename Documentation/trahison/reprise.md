# Reprise du chantier (pause du 2026-11-16, soir)

> **État :** tout est sur `main`, poussé. Dernier contrôle de l'orchestrateur : client compilé avec ses tests,
> 354 tests backend verts, 1 047 clés de langue identiques. **Rien n'a encore tourné en jeu.**
>
> - **Fait :** T0 à T4 entiers, T6.1, T6.2, T6.4 et T6.3 (fantômes, spectateurs par la commande `spectate`, voir D110), et les décisions
>   jusqu'à D43 appliquées.
> - **Agents :** ils lisent d'abord [`contexte-agent.md`](contexte-agent.md). Les tests humains sont dans
>   [`tests-humains.md`](tests-humains.md), les décisions réglées dans [`decisions-tranchees.md`](decisions-tranchees.md).
> - **Questions ouvertes** dans [`decisions.md`](decisions.md) : D110 à D114, D120, D121.
>
> **Suite, dans l'ordre :**
> 1. relire `decisions.md` et appliquer les réponses ;
> 2. T5 vocal, T7 rôles et objets, T11 carte ;
> 3. T5 vocal, T7 rôles et objets, T11 carte, T8 Cobblemon, T9 contenu, T10 ;
> 4. la check-list cochable des tests humains au premier déploiement.

> Point d'arrêt de l'orchestration du développement. À lire en premier à la reprise, avec
> [`etapes/README.md`](etapes/README.md).

## Ce qui est fait (sur `main`, poussé)

| Commit | Contenu | Vérifié |
|---|---|---|
| 1b90ce8 | T0 socle (Ping/Pong, `trahison_games` V6), charte UI (`charte-ui.md`, `TrahisonPalette`, `TrahisonUi`), règles pures T1.1 | Tests backend, build client |
| debff5e | T1.2 machine à états (`GameService`, étapes, chronos, persistance) | Tests |
| fd82afc | T1.3 vues par destinataire, actions, maître du jeu, joueurs factices, pause auto ; T2.1 plan et éditeur (V7) ; T3.1 moteur des salles (V8) | 131 tests verts |
| e393708 | T3.4 cadre des mini-jeux (client) ; T1.4 client provisoire (HUD bracelet, animatrice, écrans joueur et MJ) | Build client, clés de langue identiques EN/FR |
| 56c12d3 | Salles branchées sur le moteur, salles persistées, routes des salles réservées aux admins | 138 tests verts |

Rien n'a encore tourné **en jeu** : aucun déploiement n'a été fait (ni mod, ni module backend).

## Travail interrompu

- **T3.2 éditeur de salle** (client) : écrit et compilé (fichiers `edit/Room*.java`, `ChainScreen`), pas encore essayé en jeu.
  Voir `guide-editeur-salle.md` pour le test à faire à la main.
- ~~**T2.2 faux blocs et zones**~~ : faite le 2026-11-16 (non déployée, à valider en jeu) : `MirrorGhosts.putLocal` /
  `removeLocal` / `clearLocal`, états des portes calculés par le backend (`DoorStates`, `doors_state`, action MJ `set_door`,
  commande `/phantasmon event trahison door <id> open|close`), zones (`world/Zones`, `EventsTrahisonZone`), `world/FakeBlocks`.

## Reste à faire, dans l'ordre

1. ~~T2.4 cabines et vote, T2.5 porte finale~~ (faites le 2026-11-16 : T2.5 avec pupitre, fenêtre `final_door_seconds`, `winners` = ceux qui passent ; à valider en jeu, non déployées).
2. T3.2 (finir), ~~T3.3 objets du monde, inventaire, documents~~ (faite le 2026-11-16, à valider en jeu), T3.5 six mécanismes (`ring_dial` d'abord), T3.6 indices automatiques, mesure des temps,
   pénalité des 30 minutes (V9).
3. T4, T5, T6, T7, T8, T9, puis T10 avec des humains.

## Correctifs relevés au contrôle, pas encore faits

- ~~L'éditeur lit par réflexion les champs privés de `PhantasmonApi` pour les octets, et le catalogue des mécanismes est écrit à la
  main.~~ Fait : routes `schematic.json` (base64) et `GET /events/trahison/mechanisms` ; plus de réflexion.

- ~~La vue doit donner `viewer.can_open_final_door` (calculé par `rules.Outcome`) ; retirer la constante
  `GameScreen.FINAL_DOOR_POINTS` du client.~~ Fait en T2.2.
- ~~Une erreur `EventsTrahisonSay` reçue pendant qu'un écran de mini-jeu est ouvert doit appeler son `fail()`.~~ Fait en T2.2
  (`Announcer.onSay`).
- ~~Le client doit oublier les salles quand la vue `DOORS` d'une nouvelle manche arrive.~~ Fait en T2.2 (`PuzzleScreens`
  vide ses états et ferme l'écran de mini-jeu ouvert).
- ~~Coordonnées : `edit/HubGrid` arrondit l'origine fractionnaire de l'ancre au bloc le plus proche.~~ Fait en T2.2 : `HubGrid`
  arrondissait au plus proche et appliquait le `yaw`, alors que les miroirs prennent `floor(origine)` sans rotation ; un seul
  calcul, `world/HubBlocks`, sert maintenant à l'éditeur, aux zones et aux faux blocs (voir `architecture.md` section 6).
  **Un plan déjà enregistré avec une ancre à origine fractionnaire `>= .5` est décalé d'un bloc : à vérifier et à redessiner.**
- Les actions de salle écrivent toute la partie en base à chaque geste : prévoir un regroupement des écritures si
  cela pèse.

## Décisions prises par défaut (à trancher dans [`decisions.md`](decisions.md))

> D1 à D7 ont été tranchées par la propriétaire et appliquées le 2026-11-16 (voir « Tranché » dans `decisions.md`) ; restent D8 et D9
> (autre chantier) et les questions D10 à D13 ouvertes par cette application.

| Sujet | Choix par défaut | Autres possibilités |
|---|---|---|
| Salles pendant le temps libre | Visibles, mais une salle finie refuse les actions | Rejouables |

## Tests humains

Voir [`tests-humains.md`](tests-humains.md).

## Notes de contrôle de l'orchestrateur (2026-11-16, après 27b1d8c)

- **Points en suspens** :
  - ajouter `Mechanism.slotRoles()` au backend, pour que le catalogue des mécanismes du client n'ait plus de table
    écrite en dur (à faire avec les mécanismes suivants) ;
  - vérifier que les étiquettes flottantes de l'éditeur (`edit/WorldDraw.label`, `scale(s,-s,s)`) ne sont pas en
    miroir, à comparer avec `world/DoorPanels` ;
  - un joueur qui se trouve dans la boîte d'une porte au moment où elle se ferme n'est pas repoussé.
- **Tests humains en plus** :
  - **T3.2 éditeur de salle** (suivre `guide-editeur-salle.md`) :
    - construire une petite salle (panneau, bannière, escalier), poser 3 emplacements et une chaîne de 2 étapes, puis
      enregistrer : version 1 attendue ;
    - `room list`, puis `room load` dans un autre monde, face à une autre direction : vérifier l'orientation des
      blocs, et que les emplacements et la chaîne sont intacts ;
    - les refus : pas admin, pas en solo, pas en créatif, entrée ou sortie bouchée, pas d'étape de sortie, emplacement
      hors gabarit ;
    - la schématique s'ouvre dans Axiom ;
    - il faut un bâton en main pour sélectionner.
  - **`ring_dial`** (`/phantasmon event trahison minigame ring_dial`) :
    - la lisibilité des 20 symboles entre 9 et 16 pixels ;
    - 5 anneaux de 12 symboles ;
    - le rayon marqué en haut ;
    - la rotation des anneaux à l'ouverture ;
    - les anneaux liés ;
    - le clavier et les sons.
