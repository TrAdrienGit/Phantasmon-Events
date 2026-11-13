# T1 : le moteur de partie

## Objectif

Une partie complète, de l'inscription à la porte finale, sans aucun décor : tout se joue dans des écrans
provisoires. C'est le cœur du jeu, et la référence des règles pour toutes les étapes suivantes.

**Fini quand :** à 2 comptes et 7 joueurs factices, dans un hub vide, on déroule 3 manches (répartition, « salle »
simulée, vote, résultats), puis la porte finale, la boucle de votes et la fin. Les points suivent le barème, et
un redémarrage du backend en cours de partie ne perd rien.

## À lire avant

- [`plan.md`](../plan.md), sections 2 et 3 : les règles.
- [`architecture.md`](../architecture.md), sections 4, 5 et 9.
- `backend/.../events/tournament/TournamentRunService.java` : un exemple de déroulé tenu par le backend.
- `Phantasmon-Backend/.../websocket/SessionRegistry.java`, `WsMessage` : envoyer un message à un joueur.
- `Phantasmon-Client/.../gui/PhantasmonCanvasScreen.java` et `gui/menu/MenuUi.java` : dessiner un écran.

## Sous-tâches

### T1.1 Les règles pures (Sonnet)

Paquet `rules`, Java sans Spring, tout testé.

- `Scoring` : le barème du vote (plan, section 3). Entrées : vote de la paire, vote du solo, absents (« Allier »
  par défaut). Sortie : variation de points de chaque bracelet. Un bracelet ne descend pas sous 0.
- `Assignment` : pour une manche, donne à 9 bracelets une couleur (3 couleurs) et un statut (pour chaque couleur,
  une paire et un solo). Contraintes, par ordre de priorité : ne pas remettre ensemble deux joueurs déjà en paire ;
  ne pas refaire s'affronter deux joueurs qui se sont déjà affrontés ; déterministe pour une graine.
- `Doors` : les 3 portes (mélanges de deux couleurs). `canOpen(porte, bracelets présentés)` : vrai s'il y a un
  bracelet « paire » d'une couleur et un bracelet « solo » de l'autre. `validSplits(assignment)` : les deux
  répartitions possibles des 9.
- `Outcome` : qui peut ouvrir la porte finale (bracelet du poignet à 9 ou plus, vivant), qui est mort (0 point).
- Tests : tous les cas du barème ; 1 000 graines d'`Assignment` sans violation de contrainte tant qu'une solution
  existe ; les deux répartitions de `Doors`.
- **Fini quand :** `./gradlew test` passe dans `backend/`, sans Docker (ces tests n'ont pas besoin de base).

### T1.2 La machine à états (Opus)

Paquet `game`.

- `Game`, `Phase`, `Settings` (durées par étape, mode automatique ou maître du jeu) selon l'architecture,
  section 4. `Game` se sérialise en JSON (Jackson) sans perte.
- `GameService` : une partie par hub. Opérations : créer, inscrire ou retirer un joueur (lobby), démarrer,
  avancer, mettre en pause, reprendre, terminer. Chaque opération est synchronisée sur la partie.
- Une horloge injectée (`java.time.Clock`) et un planificateur : à l'échéance d'une étape, on passe à la
  suivante. En pause, l'échéance est gelée et recalculée à la reprise.
- Enchaînement exact : voir le schéma de l'architecture. En `ROOMS`, à cette étape, une salle est « résolue » par
  une action `debug_solve` ou à la fin du chrono (la vraie salle vient en T3). En `FINAL`, un vote recommence
  toutes les 10 minutes tant que personne n'ouvre la porte.
- Persistance : à chaque changement d'étape et à chaque vote, écrire `state` dans `trahison_games`. Au démarrage
  du backend, recharger les parties non terminées et reprogrammer leur échéance.
- Journal `log[]` : chaque événement horodaté.
- Tests avec une horloge factice : une partie entière sans attendre ; pause et reprise ; rechargement depuis le
  JSON.
- **Fini quand :** les tests passent et couvrent les 3 manches, la boucle finale et la fin.

### T1.3 Le protocole, les vues et les joueurs factices (Opus)

- `Views` : fabrique la vue d'un destinataire (joueur, fantôme ou spectateur, maître du jeu) selon le tableau
  « Qui voit quoi » de l'architecture. Un test vérifie qu'aucun vote de la manche en cours n'apparaît dans la vue
  d'un joueur avant `RESULTS`.
- `TrahisonWsHandler` : `EventsTrahisonAction` (`join`, `leave`, `pass_door`, `vote`, `open_final_door`,
  `debug_solve`), `EventsTrahisonGm` (`create`, `start`, `pause`, `resume`, `next`, `end`, `set_points`,
  `add_bots`), refusé si le joueur n'est pas admin. Envoi de `EventsTrahisonView` à chaque changement d'étape et à
  la connexion, `EventsTrahisonPatch` pour le reste.
- `onDisconnect` : le joueur passe à `connected = false`, la partie continue.
- `Bots` : joueurs factices avec un faux identifiant. Ils passent la porte que la répartition leur laisse,
  « résolvent » après un délai tiré, votent selon une consigne (`ally`, `betray`, `random`) réglable par le maître
  du jeu.
- Tout message et toute action sont décrits dans `Documentation/protocole.md`.
- **Fini quand :** un test d'intégration WebSocket déroule une partie avec 2 clients de test et 7 factices.

### T1.4 Le client provisoire (Sonnet)

- `net/GameView` : garde la dernière vue reçue ; `net/Messages` : envoi des actions.
- `hud/BraceletHud` : en bas de l'écran, le bracelet du joueur (points, couleur, paire ou solo), le chrono et le
  nom de l'étape. Dessin simple, on l'embellira en T9.
- `hud/Announcer` : affiche les répliques `EventsTrahisonSay` (texte qui s'écrit, en haut de l'écran).
- Un écran provisoire (touche réglable) : la liste des 9 avec leurs points, et les boutons de l'étape en cours
  (s'inscrire, passer telle porte, voter Allier ou Trahir, ouvrir la porte finale).
- Un écran provisoire du maître du jeu : créer, ajouter des factices, démarrer, pause, suivant, terminer.
- **Fini quand :** le « Fini quand » de l'étape, en jeu.

## Hors périmètre

Les portes et cabines dans le monde (T2), les salles (T3), les rôles et objets (T7), la mort autrement que par
0 point (T6).

## Pièges

- Une paire a **un** vote : le premier membre qui vote engage les deux. La cabine qui matérialise cela arrive en
  T2 ; ici, le premier `vote` reçu d'un membre de la paire compte et les suivants sont refusés.
- Les noms des couleurs sont des identifiants neutres (`RED`, `GREEN`, `BLUE`) dans le code ; l'habillage en types
  n'est qu'un texte de langue.
- Ne pas mettre de logique de règles dans le client provisoire : il sera jeté.
