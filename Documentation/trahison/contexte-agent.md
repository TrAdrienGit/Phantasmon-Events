# Contexte d'agent (à lire en premier, et souvent suffisant)

> Fiche compacte pour un agent qui code une sous-tâche du jeu de trahison. Elle remplace la lecture complète
> d'`architecture.md`, de `charte-ui.md`, de `plan.md` et de `protocole.md` : **ne lis ces fichiers que par
> sections**, avec `grep -n` puis `sed -n 'a,bp'`, et seulement celles dont ta tâche a besoin. Le code existant est
> la meilleure documentation : copie le modèle le plus proche.

## Dépôt et commandes

- Dépôt `W:\GitHub\Phantasmon-Events`, branche `main`. **Ne commite pas** : l'orchestrateur contrôle, puis commite.
- Client : mod Fabric 1.21.1 `phantasmon_events`, paquet `com.mystaria.events.trahison`, sous `src/`. Gradle tourne
  sous JDK 25 (Loom), le code vise Java 21. Build : `./gradlew build -x test -q` à la racine du dépôt.
- Backend : module `backend/`, paquet `com.mystaria.phantasmon_backend.events.trahison`. Il utilise Spring Boot 4,
  Jackson 3 (`tools.jackson`), JdbcTemplate, Flyway `db/events` (dernier fichier = numéro suivant) et Testcontainers
  (Docker Desktop).
  - **Pendant le travail**, lance seulement tes classes de test : `./gradlew test --tests '*MaClasseTest' -q`
    dans `backend/`.
  - **Une seule fois à la fin**, lance toute la suite : `./gradlew test --tests '*trahison*' -q`.
  - Une erreur Gradle de verrou, `EOFException` ou « in progress » vient d'un autre build : attends une minute et
    relance.
- Langues : `src/main/resources/assets/phantasmon_events/lang/en_us.json` et `fr_fr.json`, clés
  `phantasmon_events.trahison.*`. Les deux fichiers ont toujours les mêmes clés. Ne lis pas le fichier entier :
  `grep` pour trouver le bloc voisin, puis insère.

## Règles

1. Le backend décide, le client affiche : aucune règle de jeu côté client.
2. Un secret (une solution, un rôle, la vue du MJ) ne part qu'à qui a le droit de le voir. Tout passe par les vues
   (`game/Views`, `room/RoomViews`). Le backend renvoie la vue complète à chaque changement.
3. Ne jamais modifier Phantasmon-Client, Phantasmon-Backend ni Cobblemon-Battle-Showtime : ils ne se lisent que
   comme référence. S'il manque un point d'entrée, s'arrêter et le signaler.
4. Code, commentaires et commits en anglais ; documentation en français ; textes du jeu en clés de langue.
5. Tout tirage part d'une graine. Les règles pures (`rules`) ont des tests.
6. Messages préfixés `EventsTrahison`, codes d'erreur `ERROR_TRAHISON_`.
7. Pas de nom de personne ; rien de repris du jeu qui inspire le nôtre.
8. Ordre des verrous : partie, puis salle.

## Carte du code

- **Backend**
  - Racine : `TrahisonWsHandler` (actions et création), `Broadcaster` (envoi des vues et des annonces `say.*`),
    `HubGate`.
  - `rules` : `Scoring`, `Assignment`, `Doors`, `Outcome`, `Vote`, `History`.
  - `game` :
    - `GameService` : la machine à états. Étapes LOBBY, DOORS, ROOMS, FREE_TIME, VOTE, RESULTS, FINAL, ENDED.
    - `Game` et `RoundState` : l'état, persisté en JSON.
    - `Settings` et `Views`.
    - Les portes : `DoorStates`, `DoorThresholds`, `DoorWindow`, `Cabins`, `CabinLock`, `FinalDoorWindow`.
    - `GameRooms`, `Bots`, `GiveRange`.
  - `layout` : le plan fixe et son validateur.
  - `room` : `RoomDefinition`, `RoomInstance`, `RoomService`, `RoomViews`, `RoomValidator`, `RoomAction`,
    `Combine`, `RoomPlace`.
  - `room.mechanism` : un fichier par mécanisme, plus l'interface `Mechanism` (avec `slotRoles` et
    `validateStep`), `SlotRole`, `SymbolSet` et `MechanismParam`. Les mécanismes sont des `@Component` Spring.
- **Client**
  - `net` : `GameView`, `Messages`.
  - `hud` : bracelet, `Announcer`, invites.
  - `screen` : écran du joueur, écran du MJ, `VoteScreen`, `FinalDoorScreen`, inventaire, documents.
  - `world` : `FakeBlocks`, `Zones`, `HubBlocks` (monde = `floor(origine de l'ancre)` + hub, sans rotation),
    `DoorPanels`, `Interactables`, `InteractableRender`, `RoomFrame`, `CabinFlow`, `FinalDoorFlow`, `LeverState`.
  - `room` : salles reçues, inventaire, documents.
  - `minigame` : `MiniGameScreen`, `PuzzleScreens` (enregistrement), un écran par mécanisme, `Glyphs`.
  - `edit` : éditeur du plan, éditeur de salle, `MechanismCatalog`.
  - `ui` : `TrahisonPalette` et `TrahisonUi`.

## Charte des écrans (résumé)

- **Couleurs** : seulement les jetons de `TrahisonPalette`, jamais d'ARGB écrit en dur.
  - Surfaces bleu encre (`BG`, `PANEL`) ; accent sarcelle `ACCENT` (`0xFF5FE0D0`).
  - Les trois couleurs de jeu ont toujours leur symbole : RED avec un triangle, GREEN avec un disque, BLUE avec un
    carré.
  - Vote « Allier » `ALLY` (or), vote « Trahir » `BETRAY` (violet) ; `DANGER`, `SUCCESS`, `GHOST`, `GAME_MASTER`.
- **Dessin** :
  - les composants sont dans `TrahisonUi` : panneaux, boutons (4 états), onglets, lignes, pastilles et
    `glyphPill`, barres, infobulles, bracelet, modales ;
  - la police est `uniform` (via `MenuUi.text`), à l'échelle 1, l'échelle 2 étant réservée aux chiffres ;
  - les espacements sont des multiples de 4, avec une marge de sécurité de 6 ;
  - un écran se vérifie à 320 × 180 et à 854 × 480 pixels d'interface ;
  - les mini-jeux sont en mode fenêtre, 400 × 240.
- **Comportement** : Échap ferme sans rien perdre ; un écran ne met pas le jeu en pause ; un geste s'affiche tout
  de suite chez soi puis se recale sur la vue. Le détail est dans `charte-ui.md` : la section 3 pour les jetons, la
  section 11 pour la liste de contrôle d'un écran, la section 12 pour les mini-jeux.

## Protocole

`protocole.md` est gros (environ 100 Ko) : ne lis que tes sections.

- `grep -n "^## \|^### " Documentation/trahison/protocole.md` donne le sommaire. On y trouve :
  - la vue `EventsTrahisonView` ;
  - `EventsTrahisonSay` ;
  - les actions des joueurs et celles du MJ ;
  - `EventsTrahisonRoom`, `EventsTrahisonPuzzle` (une sous-section par mécanisme) et `EventsTrahisonZone` ;
  - les routes HTTP ;
  - les codes d'erreur.
- Mets à jour **seulement** les sections que tu changes.

## Ce que tu rends

- **Le code et ses tests.** Toute la suite `*trahison*` est verte, le build client est vert, et les deux fichiers de
  langue ont les mêmes clés.
- **La documentation en français, sans relire les gros fichiers en entier :**
  - **Protocole** : les sections touchées de `protocole.md`.
  - **État** : la ligne de l'étape dans `etapes/README.md`.
  - **Tests humains** : un bloc **ajouté à la fin** de `tests-humains.md` (`cat >> … <<'EOF'`, sans le lire). Il
    porte un titre `## <étape ou sujet>` et une liste de tests concrets.
  - **Questions** : un point de conception douteux devient une section **ajoutée à la fin** de `decisions.md`.
    - Numéro : celui que l'orchestrateur t'a donné.
    - Contenu : des options en cases à cocher, l'option appliquée cochée et marquée *(par défaut)*, puis
      `Ta réponse :`.
    - Tu ne t'arrêtes jamais pour demander.
  - **Décision appliquée** : retire sa section de `decisions.md` et ajoute une ligne à `decisions-tranchees.md`.
- **Le rapport final : 250 mots au plus**, en anglais. Il dit :
  - les fichiers touchés, groupés par dossier ;
  - les choix faits ;
  - les questions ajoutées ;
  - ce qui n'est pas fait ;
  - les résultats des builds et des tests.

  Ne recopie pas les tests humains, ils sont dans le fichier.
