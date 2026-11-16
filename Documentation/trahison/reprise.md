# Reprise du chantier (pause du 2026-11-16)

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

1. T2.4 cabines et vote, T2.5 porte finale (T2.3 portes de couleur faite le 2026-11-16, non déployée).
2. T3.2 (finir), T3.3 objets du monde, inventaire, documents (demande des actions backend : donner, combiner,
   utiliser un objet), T3.5 six mécanismes (`ring_dial` d'abord), T3.6 indices automatiques, mesure des temps,
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

## Décisions prises par défaut, à confirmer par la propriétaire

| Sujet | Choix par défaut | Autres possibilités |
|---|---|---|
| Votes de la boucle finale | Nouvelle répartition tirée, groupes choisis par la graine, sans étape des portes | A : garder les groupes de la manche 3 ; B : une vraie étape des portes à chaque boucle |
| Joueur resté devant les portes | −1 point (proposition du plan), et il reste lié au groupe de la répartition pour le vote (« Allier » s'il ne vote pas) | Autre valeur, ou exclu du vote |
| Cabines | La salle de vote sert les 3 manches : une cabine sert une paire de couleurs (3 paires × 2 côtés = 6) | — |
| Maître du jeu | Tout admin qui envoie une action MJ devient maître du jeu de la partie | Désignation explicite |
| Salles pendant le temps libre | Visibles, mais une salle finie refuse les actions | Rejouables |

## Tests humains à faire (à transformer en check-list cochable à la reprise)

À faire après le déploiement du mod (`--events`) et du module backend :

- **T0** : `/phantasmon event trahison ping` affiche le Pong.
- **Charte UI** : rendu des composants (panneaux, boutons dans leurs 4 états, pastilles, symboles, bracelet) aux
  échelles d'interface 2, 3, 4 et en 1280 × 720 ; palette agréable et lisible.
- **T2.1 éditeur du plan** :
  - la commande `edit` est réservée aux admins ;
  - la touche K, et son réglage ;
  - les clics gauche et droit posent les coins sans que rien n'atteigne le serveur ;
  - le rendu dans le monde, avec et sans Iris ;
  - le panneau en 320 × 180 ;
  - un plan enregistré, puis rechargé à l'identique depuis un autre compte admin ;
  - le conflit de version ;
  - les erreurs affichées, et le saut vers l'élément fautif.
- **T3.4 mini-jeux** : `/phantasmon event trahison minigame three_dials` (hors ligne) :
  - l'impression générale face au menu de Showtime ;
  - la force du grain, des lignes et du vignettage ;
  - la prise en main des molettes ;
  - le cadenas et le rythme de la réussite ;
  - l'aide ;
  - le rendu en 320 × 180 ;
  - les sons.
- **T1.4 client** : partie complète avec l'écran MJ (`/phantasmon event trahison gm`), avec des factices de
  chaque consigne :
  - le HUD : sa place, le chrono orange puis rouge, l'effet sur les points, masqué par F1 et par les écrans ;
  - les répliques de l'animatrice, et une erreur traduite ;
  - l'écran joueur (`menu`) en 320 × 180 et en 854 × 480 ;
  - le vote d'une paire, des deux côtés ;
  - la pause, par le MJ et automatique.
- **T2.3 portes de couleur** (mod et module backend déployés ; plan avec un `threshold` par porte de couleur, sinon la
  `zone_a` sert de seuil et trois portes qui la partagent s'ouvrent ensemble) :
  - le formulaire d'une porte de couleur de l'éditeur a la ligne « Zone de seuil » ; la zone choisie s'éclaire quand la
    porte est sélectionnée ; un seuil qui n'existe pas est refusé à l'enregistrement ;
  - un joueur seul sur un seuil : la porte reste fermée, **aucun message d'erreur**, le panneau au-dessus dit « Il faut … »
    (couleur, symbole et nom) ; il se lit à environ 10 blocs, pas à travers un mur, et fait face à la caméra (**vérifier qu'il
    n'est pas en miroir** : l'orientation de `DoorPanels` est déduite de la caméra, pas encore vue en jeu) ;
  - une paire et un solo qui vont ensemble ouvrent la porte (6 s, réglable avec `door_open_seconds`), le panneau montre
    « Ouverte : n s » et la barre qui se vide ; passer dans la zone côté salle enregistre le passage, la porte se referme
    quand tout le groupe est passé ou au bout des secondes ;
  - les deux répartitions fonctionnent (essayer les deux sur deux manches) ; après un premier passage, la combinaison de
    l'autre répartition devant une autre porte ne l'ouvre pas et le panneau l'explique (« ne vont pas avec les portes
    déjà prises ») ;
  - un joueur mort ou fantôme sur le seuil ne compte pas ;
  - à la fin de `DOORS` toutes les portes se ferment, les absents perdent `no_door_penalty` (−1 : question ouverte à la
    propriétaire) ;
  - le panneau a-t-il le bon rendu (lisible en 1280 x 720, glyphes nets, rien ne se superpose) ? Les polices et les formes
    sont celles du monde (`Font.drawInBatch`, `RenderType.debugQuads`), pas celles de la charte des écrans.
- **T2.2 faux blocs et zones** (mod `--events` et module backend déployés, plan complet enregistré) :
  - une porte de couleur fermée arrête le joueur, `/phantasmon event trahison door <id> open` l'ouvre chez tous en même temps,
    `close` la referme ; la porte revient à son état par défaut au changement d'étape ;
  - elle survit à un éloignement puis un retour (rechargement des tronçons), et à une mise à jour de bloc voisine ; on ne peut
    ni la casser ni poser un bloc contre elle ;
  - les deux moitiés d'une porte de couleur sont des deux couleurs, les cabines en chêne noir, la porte finale en obsidienne ;
    rien de réel n'est remplacé (une porte dont la boîte touche un vrai bloc n'a que ses cases d'air) ;
  - un fantôme et un spectateur traversent ; tout disparaît à la fin de la partie et en quittant le hub ;
  - les sorties de salle sont fermées pendant `ROOMS` et s'ouvrent quand la salle du groupe est résolue ;
  - le plan se dessine dans l'éditeur sur les mêmes blocs que les faux blocs (ancre à origine fractionnaire, le cas de l'ancienne
    erreur d'un bloc) ;
  - la zone de chaque joueur apparaît dans la vue du maître du jeu (`gm.zones`, pas encore affichée par l'écran provisoire).

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
