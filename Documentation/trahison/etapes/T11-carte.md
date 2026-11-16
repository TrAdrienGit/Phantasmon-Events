# T11 : la carte du lieu

> Ajoutée le 2026-11-16 à la demande de la propriétaire (décision D9 de [`decisions.md`](../decisions.md)).

## Objectif

Une carte du complexe que tout le monde peut ouvrir une fois les salles générées : participants, maîtres du jeu et
spectateurs. Pour ses fonctions, elle prend modèle sur les cartes de donjon des Zelda en 3D (Ocarina of Time) : une
image par étage, on passe d'un étage à l'autre, et un curseur marque où l'on est. Un bloc-notes y est joint.

**Fini quand :**
- en jeu, un joueur ouvre la carte, passe d'un étage à l'autre, et se voit dessus ;
- il voit les salles qu'il a explorées et celles qu'il n'a pas explorées, et les portes ouvertes et fermées qui
  changent en direct ;
- il écrit sur la carte, efface, tape une note dans l'onglet bloc-notes, se déconnecte, revient et retrouve tout ;
- un autre joueur ne voit ni son curseur, ni ses traits, ni ses notes.

## Dépend de

- T2 : le plan (salles, portes, zones, `HubBlocks`) et les états des portes (`doors_state`).
- T4 : les salles placées, qui donnent leur nom à chaque place.

On peut commencer les sous-tâches 1 et 2 sur le plan seul, avec les noms des places à la place des noms des salles.

## À lire avant

- [`architecture.md`](../architecture.md), section 6 (coordonnées du hub).
- [`charte-ui.md`](../charte-ui.md) : la carte est un écran phare. Elle suit la palette, et le moteur décide de tout.
- Le code de T2 : `world/HubBlocks`, `world/Zones`, `DoorStates`, l'éditeur du plan (`edit/`).

## Ce que la carte montre

- **Les salles**, d'après le plan : leur contour et leur nom (le nom de la salle tirée, sinon celui de la place).
  Les couloirs, le hall des portes et la salle de vote apparaissent aussi.
- **Les portes** entre les salles, en deux couleurs bien distinctes selon qu'elles sont ouvertes ou fermées.
  Une porte de couleur porte en plus son symbole (triangle, disque, carré). Ces états changent en direct.
- **L'exploration, propre à chaque joueur** : une salle où il n'est jamais entré est hachurée. Le backend retient les
  salles visitées grâce aux zones que le client lui envoie déjà (`EventsTrahisonZone`), et la vue n'envoie à chacun
  que ses propres salles.
- **Le curseur du joueur** : sa position et la direction de son regard, à l'étage où il se trouve. Il est calculé sur
  le client, ce qui ne demande aucun réseau, et personne d'autre ne le voit.
- **Les étages** : chaque étage a sa propre image. On change d'étage par des onglets ou par la molette, et la carte
  s'ouvre sur l'étage du joueur.

## Sous-tâches

### T11.1 Les étages dans le plan (Sonnet)

- Un plan déclare ses étages : identifiant, nom affiché, et la tranche de hauteur `y_min` à `y_max`, en coordonnées du hub.
- L'éditeur du plan reçoit un onglet « Étages ». À l'enregistrement, il vérifie que les tranches ne se chevauchent pas.
- Chaque élément du plan se range dans l'étage de sa boîte. Un élément à cheval sur deux étages, comme un escalier,
  apparaît sur les deux.
- Un plan sans étage déclaré compte un seul étage.
- Une migration Flyway s'impose seulement si le schéma change. Le plan étant en `jsonb`, il n'en faut pas.
- **Fini quand :** les tests du validateur passent, et un plan à deux étages s'enregistre puis se recharge à l'identique.

### T11.2 L'exploration et les données de la carte (Sonnet)

- Backend : un ensemble `explored` par joueur, rempli quand sa zone désigne une salle ou une place. Il est enregistré
  avec la partie, survit à un redémarrage, et part dans la vue du joueur (`viewer.explored`).
- Les maîtres du jeu et les spectateurs voient toutes les salles sans hachures. Voir aussi la question D14.
- Les noms des salles, par place, sont dans la vue dès que le tirage est fait.
- **Fini quand :** des tests couvrent la visite, la persistance, et le fait qu'un joueur ne reçoive jamais
  l'exploration d'un autre.

### T11.3 L'écran de la carte (Fable : écran phare)

- C'est un écran à toile fixe, qui s'ouvre par une touche réglable (M par défaut, à vérifier contre les conflits avec
  les autres mods).
- Il a deux onglets :
  - **Carte** : le dessin de l'étage, une légende, et le choix de l'étage ;
  - **Bloc-notes** : une page blanche de texte libre, sur plusieurs lignes.
- **Le dessin** est tracé à partir du plan, sans image faite à la main :
  - un fond à la couleur des surfaces de la charte ;
  - les salles en aplats, et des hachures sur celles qui ne sont pas explorées ;
  - les portes en traits épais, avec deux couleurs pour ouvert et fermé ;
  - les noms des salles, qui se masquent quand elles sont trop petites à l'écran.
  - Un **fond optionnel par étage**, une image PNG faite à la main dans les assets, peut remplacer les aplats. Un
    exemple modifiable sert d'exemple de départ.
- On peut zoomer à la molette et se déplacer en tirant avec le clic droit. La molette sur les onglets d'étage change
  d'étage.
- **Annoter** : un crayon trace au clic gauche, une gomme efface, et il y a 3 ou 4 couleurs de crayon. Le bouton
  « Tout effacer » demande confirmation. Les traits sont enregistrés par étage, en coordonnées du hub : ils restent
  donc en place quand on zoome.
- Le curseur du joueur est une flèche à sa couleur de bracelet. On peut ajouter un mode « suivre », qui centre la
  carte sur le joueur.
- La carte doit rester lisible en 320 × 180 et en 854 × 480.
- **Fini quand :** le « Fini quand » de l'étape, partie écran.

### T11.4 Garder les annotations et les notes (Sonnet)

- Les traits et les notes appartiennent à un joueur dans une partie. Le backend les garde, avec une petite route
  WebSocket pour lire et écrire, afin qu'un joueur les retrouve après une déconnexion ou depuis un autre ordinateur.
  Un client n'en reçoit jamais d'autres que les siens.
- Les écritures sont regroupées, au plus une toutes les 2 secondes, et la taille est limitée (par exemple
  2 000 points de trait par étage et 8 000 caractères de notes).
- Tout est effacé à la fin de la partie, ou gardé pour le bilan : voir la question D15.
- **Fini quand :** le « Fini quand » de l'étape, partie persistance.

## Questions ouvertes

Elles sont portées dans [`decisions.md`](../decisions.md) :
- **D14** : un spectateur ou un maître du jeu voit-il les curseurs de tous les joueurs ?
- **D15** : que deviennent les annotations à la fin de la partie ?
- **D16** : un fantôme garde-t-il sa carte ?

## Pièges

- La carte ne doit rien révéler que le joueur ne puisse savoir, en particulier les noms et l'exploration des autres.
  Les portes ne donnent que leur état ouvert ou fermé, jamais qui les a passées.
- Le dessin est fait côté client, à partir du plan que le client a déjà. Il ne faut pas envoyer une image du serveur.
