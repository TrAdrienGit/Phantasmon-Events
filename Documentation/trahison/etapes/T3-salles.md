# T3 : le cadre des salles

## Objectif

Tout ce qu'il faut pour qu'une salle d'escape game existe : sa définition, son éditeur, les objets cliquables du
monde, l'inventaire, les écrans de mini-jeu partagés, six premiers mécanismes et l'équilibrage automatique. Les
salles elles-mêmes viennent en T9 ; ici on construit l'outil, avec une salle d'essai.

**Fini quand :** une salle d'essai de 5 étapes se joue à trois (2 comptes et un factice), se remet à zéro, et
se rejoue avec d'autres solutions. Les indices automatiques tombent, les temps sont mesurés, et à 30 minutes la
sortie s'ouvre avec la pénalité.

## À lire avant

- [`plan.md`](../plan.md), section 5 en entier.
- [`architecture.md`](../architecture.md), sections 6 et 7.
- [`solutions-techniques.md`](../solutions-techniques.md) : S3 (objets cliquables), S12 (capture d'une salle), S13 (fichiers sur le
  backend), S14 (affichage dans le monde).
- Le code de T2 : `world/FakeBlocks`, `world/Zones`, l'éditeur du plan (même outil de sélection).
- `Phantasmon-Client/.../gui/PhantasmonCanvasScreen.java` : la toile à échelle fixe des écrans.
- `Cobblemon-Battle-Showtime/.../gui/ShowtimeMenuScreen.java` : la qualité visée pour un écran.
- `Phantasmon-Events/.../hub/MirrorCapture.java` : lire les blocs d'une zone côté client.
- `Phantasmon-Client/.../hub/HubSchematic.java` : le format de schématique déjà lu.

## Sous-tâches

### T3.1 Le modèle d'une salle et le moteur des mécanismes (Opus)

Backend, paquets `room` et `room.mechanism`.

- `RoomDefinition`, `Slot`, `Step` (architecture, section 7). Table `trahison_rooms` (`id`, `version`, `data
  jsonb`, schématique en fichier sur disque : solution S13). Routes `/events/trahison/rooms` : lister, lire, créer,
  modifier, supprimer (admins).
- Interface `Mechanism` :
  - `id()`, `difficulty(params)`, `expectedSeconds(params)` ;
  - `generate(Random, params)` : l'état initial, qui contient la solution et les **indices** produits ;
  - `onInput(state, joueur, input)` : nouvel état, événements à diffuser, résolu ou non ;
  - `view(state, spectateur)` : ce que le client reçoit, sans la solution.
- Un **indice** est une donnée typée (`text`, `digits`, `symbols`, `colors`, `image`, `item`) qu'une étape donne
  et qu'un emplacement d'affichage montre. `Step.gives` dit dans quel emplacement il apparaît.
- `RoomInstance` : une salle en cours. Graine, état de chaque étape, étapes ouvertes (celles dont les `needs`
  sont finis), inventaire commun de la salle, heure de début et de fin de chaque étape.
- `RoomService` : démarrer une instance pour un groupe, appliquer une action (`use_slot`, `puzzle_input`,
  `pick_up`), diffuser `EventsTrahisonRoom` et `EventsTrahisonPuzzle` aux membres et aux spectateurs, détecter la
  fin (dernière étape résolue → ouverture de la porte de sortie), remettre à zéro.
- Branchement sur le moteur de T1 : en `ROOMS`, chaque groupe reçoit son instance ; `debug_solve` reste pour les
  essais.
- Tests : une chaîne de 3 étapes factices ; déterminisme par graine ; la vue ne contient jamais la solution.
- **Fini quand :** un test déroule une salle factice de bout en bout et la rejoue avec une autre graine.

### T3.2 L'éditeur de salle (Opus)

Client, paquet `edit`.

- `/phantasmon trahison room new <id>` : dans un monde solo en créatif, affiche le gabarit d'une place
  (48 × 48 × 24), l'entrée et la sortie imposées, à partir de la position du joueur.
- Même outil de sélection que l'éditeur du plan pour poser des **emplacements** : genre (`terminal`, `keypad`,
  `pickup`, `wall_clue`, `document`, `lever`, `plate`, `pedestal`, `inner_door`, `exit`, `secret`), identifiant,
  orientation, taille.
- Un écran pour écrire la **chaîne** : ajouter une étape, choisir son mécanisme dans la liste, régler ses
  paramètres, lier ses rôles à des emplacements, dire ce qu'elle demande (`needs`) et ce qu'elle donne (`gives`).
  L'écran affiche la difficulté et la durée totales.
- Vérifications avant l'enregistrement : chaque étape est atteignable, la dernière ouvre la sortie, aucun
  emplacement n'est utilisé deux fois de façon incompatible, rien ne dépasse du gabarit.
- Enregistrer : capture des blocs du gabarit avec leurs entités de bloc (panneaux, bannières), par le monde du
  serveur intégré (solution S12), puis envoi de la définition et de la schématique au backend.
- `room load <id>` : recolle une salle existante dans le monde solo pour la retoucher.
- **Fini quand :** une salle construite à la main est enregistrée, rechargée dans un autre monde, et sa chaîne
  est intacte.

### T3.3 Les objets du monde, l'inventaire, les documents (Opus)

Client, paquets `world` et `room`.

- `world/Interactables` (solution S3) : pour chaque emplacement actif reçu du backend, une entité locale sur le
  modèle d'`EventNpcEntity`, cliquable par `UseEntityCallback` (le clic ne part pas au serveur), portée 4 blocs.
  Un contour léger quand elle est visée, et le nom de l'action (« Examiner », « Ramasser », « Utiliser »).
- Rendus de base, sans modèle externe : un terminal (bloc et écran lumineux), un digicode mural, un objet qui
  flotte et tourne, un panneau d'indice (texte ou symboles dessinés sur un mur), un levier, un socle.
- `room/Inventory` : l'inventaire **virtuel** du joueur pour la salle (une barre d'objets à l'écran, touche pour
  l'ouvrir). Ramasser, examiner, donner à un joueur proche, utiliser sur un emplacement, combiner deux objets.
  Tout passe par le backend.
- `room/Documents` : un écran de lecture (pages, images, texte en Zarbi possible plus tard).
- **Fini quand :** en jeu, on ramasse un objet, on le donne, on lit un document, on voit un indice mural, et un
  second joueur voit les mêmes changements.

### T3.4 Le cadre des écrans de mini-jeu (Fable)

Client, paquet `minigame`. C'est ce cadre qui donnera leur cachet à toutes les salles.

- `MiniGameScreen` : écran plein sur toile fixe (comme `PhantasmonCanvasScreen`), fond, cadre, titre, bouton de
  sortie, zone d'aide, et la liste des joueurs qui regardent le même écran (têtes des joueurs).
- Synchronisation : chaque geste part en `puzzle_input` ; l'écran applique tout de suite son propre geste, puis
  se recale sur `EventsTrahisonPuzzle`. Les gestes des autres s'animent (un curseur à leur couleur).
- Une petite bibliothèque d'éléments : pièce qu'on tourne, pièce qu'on glisse, bouton, molette, fil qu'on tire,
  texte qui s'écrit, sons de clic, de verrou, d'erreur et de réussite.
- Animation de réussite commune (le mécanisme se déverrouille), et retour au monde.
- Le style visuel est posé ici, une fois : couleurs, typographie, grain « vieux complexe abandonné » en accord
  avec le lore. À valider en jeu avant de produire les mini-jeux.
- **Fini quand :** un mini-jeu d'essai (trois molettes à aligner) se joue à deux sur le même écran, chacun voyant
  les gestes de l'autre.

### T3.5 Six premiers mécanismes (Sonnet, un par agent, après T3.4)

Chacun a ses deux moitiés (backend et client), ses paramètres, ses indices, ses tests de génération.

| Mécanisme | Forme | Ce qu'il tire au hasard | Ce qu'il demande ou donne |
|---|---|---|---|
| `keypad` | Digicode mural, petit écran | Un code de 3 à 6 chiffres | Demande un indice `digits` |
| `pickup_set` | Objets cachés dans la salle | Quels emplacements `pickup` sont garnis | Donne des objets, un document |
| `ring_dial` | Mini-jeu : anneaux concentriques liés | Symboles, liaisons, position de départ | Demande un indice `symbols` |
| `wiring` | Mini-jeu : relier des bornes | Le schéma des liaisons | Demande un indice `colors` ou un document |
| `code_breaker` | Mini-jeu : terminal « bien placé / mal placé » | La combinaison | Aucun indice : la logique suffit |
| `three_levers` | Trois leviers à tenir en même temps | Ordre et délai | Coopération à trois, aux trois coins |

- Chaque mécanisme est écrit sur le modèle du premier livré. `ring_dial` est fait en premier, par le modèle le
  plus fort, et sert d'exemple aux autres.
- **Fini quand :** chacun est jouable dans la salle d'essai, et 1 000 générations aléatoires donnent toutes une
  énigme résoluble (test).

### T3.6 Indices automatiques, mesure des temps, 30 minutes (Sonnet)

- Chaque étape d'une définition peut porter jusqu'à 3 textes d'aide, du plus vague au plus direct. Sans progrès
  depuis 10, 18 puis 25 minutes (réglable), l'animateur donne le suivant au groupe.
- `telemetry` : table `trahison_step_times` (salle, étape, mécanisme, paramètres, durée, aides données, taille du
  groupe). Une route admin donne la médiane par mécanisme et par salle.
- La difficulté affichée d'une salle utilise la durée mesurée dès qu'il y a au moins 3 mesures.
- À la fin de l'étape `ROOMS` : la sortie d'une salle non finie s'ouvre, et chaque membre du groupe perd 1 point.
- **Fini quand :** le « Fini quand » de l'étape.

## Hors périmètre

La banque de salles et les autres mécanismes (T9), les mécanismes Cobblemon (T8), le tirage des salles et leur
collage (T4).

## Pièges

- Jamais de solution dans un message envoyé à un client, même « pour afficher la réussite ».
- Un emplacement ne doit pas dépendre d'un vrai bloc interactif du serveur (coffre, levier, bouton) : le clic est
  capté par le mod, sinon le serveur réagit aussi.
- Les entités de bloc ne sont lisibles en entier que dans un monde solo : l'éditeur de salle s'utilise en solo.
- Deux joueurs agissent en même temps sur le même mini-jeu : c'est l'ordre d'arrivée au backend qui tranche.
