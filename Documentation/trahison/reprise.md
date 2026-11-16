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

- **T3.2 éditeur de salle** (client) : à moitié écrit, sur la branche `wip/trahison-t3.2-editeur-salle` (poussée,
  ne compile pas encore). À reprendre depuis cette branche : finir, compiler, puis fusionner dans `main`.
- **T2.2 faux blocs et zones** : interrompu avant d'avoir écrit du code. À relancer tel quel. Le cadrage donné à
  l'agent comprenait :
  - les méthodes `putLocal`, `removeLocal` et `clearLocal` sur `MirrorGhosts` (S1) ;
  - l'état des portes calculé par le backend et envoyé dans la vue (`doors_state`), avec l'action MJ `set_door` ;
  - les zones côté client (`EventsTrahisonZone`), sans écrire en base à chaque mise à jour ;
  - `world/FakeBlocks` ;
  - deux correctifs d'intégration (voir plus bas).

## Reste à faire, dans l'ordre

1. T2.2 (ci-dessus), puis T2.3 portes de couleur, T2.4 cabines et vote, T2.5 porte finale.
2. T3.2 (finir), T3.3 objets du monde, inventaire, documents (demande des actions backend : donner, combiner,
   utiliser un objet), T3.5 six mécanismes (`ring_dial` d'abord), T3.6 indices automatiques, mesure des temps,
   pénalité des 30 minutes (V9).
3. T4, T5, T6, T7, T8, T9, puis T10 avec des humains.

## Correctifs relevés au contrôle, pas encore faits

- La vue doit donner `viewer.can_open_final_door` (calculé par `rules.Outcome`) ; retirer la constante
  `GameScreen.FINAL_DOOR_POINTS` du client (règle dupliquée côté client).
- Une erreur `EventsTrahisonSay` reçue pendant qu'un écran de mini-jeu est ouvert doit appeler son `fail()`.
- Le client doit oublier les salles quand la vue `DOORS` d'une nouvelle manche arrive (aucun message ne dit qu'une
  salle a disparu).
- Coordonnées : `edit/HubGrid` arrondit l'origine fractionnaire de l'ancre au bloc le plus proche. Vérifier que le
  plan et les faux blocs tombent sur les mêmes blocs que le système des miroirs (`MirrorTerrain.Zone`), et partager
  un seul calcul.
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
