# T4 : le tirage et le collage

## Objectif

Préparer une soirée : tirer 9 salles équilibrées, fabriquer les fichiers que l'admin du serveur colle, puis
brancher chaque place sur sa salle.

**Fini quand :** depuis l'écran du maître du jeu, on crée une partie, on obtient les fichiers `.schem` et
`.litematic` des 9 places, on les colle (dans un monde d'essai, avec WorldEdit ou Litematica), et la partie se
joue avec ces 9 salles au bon endroit.

## À lire avant

- [`plan.md`](../plan.md), section 7.
- [`architecture.md`](../architecture.md), sections 7 et 8.
- Le code de T3 : `RoomDefinition`, l'éditeur de salle (capture des blocs).
- `Phantasmon-Client/.../hub/HubSchematic.java` : lecture des deux formats ; l'écriture est à faire.
- Les formats : Sponge Schematic version 2 (`.schem`, lu par WorldEdit et Axiom) et Litematica (`.litematic`).

## Sous-tâches

### T4.1 Le tirage (Sonnet)

Backend, paquet `room`.

- `RoomDraw.draw(banque, graine, historique)` : 9 salles, 3 par manche.
  - Les 3 salles d'une manche ont des difficultés totales proches (écart maximal réglable).
  - La difficulté peut monter d'une manche à l'autre (réglage).
  - Une salle jouée lors des dernières parties est évitée tant que la banque le permet (table `trahison_games`).
  - Variété : pas deux salles d'une même manche avec le même mécanisme principal.
- Si la banque ne permet pas de respecter une contrainte, le tirage la relâche et le dit dans son résultat.
- Le maître du jeu peut remplacer une salle tirée par une autre, à la main.
- Tests : équilibre sur 1 000 graines avec une banque factice ; cas d'une banque trop petite.
- **Fini quand :** les tests passent et l'écran du maître du jeu montre le tirage avec ses difficultés.

### T4.2 L'écriture des fichiers (Opus)

Client, paquet `edit`.

- `SchematicWriter` : écrit une salle en `.schem` (Sponge version 2) et en `.litematic`, blocs et entités de bloc
  compris.
- Rotation : si l'orientation de la place diffère de celle de la salle, tourner la schématique par quarts de tour
  avec les outils de Minecraft (`BlockState.rotate`, positions, entités de bloc), puis recalculer les positions
  des emplacements de la même façon.
- Sortie dans `.minecraft/phantasmon_events/trahison/export/<partie>/` : un fichier par place, nommé par la place
  (`place_1A.schem`), plus un fichier unique des 9 places à leurs positions relatives, et une fiche texte : pour
  chaque place, les coordonnées du coin où coller.
- Test en jeu : relire chaque fichier écrit avec `HubSchematic.read` et comparer bloc à bloc.
- **Fini quand :** un fichier écrit se colle sans décalage dans un monde d'essai, dans les quatre orientations.

### T4.3 Le branchement des places (Sonnet)

- À la création de la partie, le backend garde l'association place → salle, et l'orientation appliquée.
- `RoomInstance` convertit les positions des emplacements : relatives à la salle → coordonnées de hub de la place.
- Vérification avant de démarrer : pour chaque place, le client du maître du jeu compare un échantillon de blocs
  du monde avec la schématique attendue et signale une place non collée ou mal collée.
- **Fini quand :** le « Fini quand » de l'étape.

### T4.4 La procédure de soirée (Sonnet)

- `Documentation/soiree.md` : la marche à suivre, pas à pas, pour la maîtresse du jeu. Avant (tirage, envoi des
  fichiers à l'admin, vérification du collage, essai du vocal), pendant (lobby, démarrage, pause, incidents), après
  (fin de partie, sauvegarde des temps mesurés).
- Un écran « préparation » dans le panneau du maître du jeu, qui suit cette liste et coche ce qui est prêt.
- **Fini quand :** quelqu'un qui n'a pas codé le jeu prépare une partie en suivant le document.

## Hors périmètre

La construction du plan fixe lui-même (c'est un travail de construction en jeu, hors code) et les salles (T9).

## Pièges

- Les états de blocs orientés (escaliers, portes, panneaux muraux) doivent tourner avec la salle : ne pas tourner
  les seules positions.
- Les deux formats n'ordonnent pas les axes de la même façon : s'appuyer sur la lecture existante pour vérifier.
- Le collage est fait par quelqu'un d'autre, à la main : la fiche des coordonnées doit être sans ambiguïté (quel
  coin, quelle orientation).
