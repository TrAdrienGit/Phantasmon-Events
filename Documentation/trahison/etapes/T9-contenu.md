# T9 : le contenu de la première soirée

## Objectif

Remplir le jeu : le plan fixe du complexe, une banque de 9 salles (puis davantage), d'autres mini-jeux, le lore,
les textes de l'animateur, les fins, et l'habillage final du bracelet.

Cette étape se fait en continu dès que T3.5 est livrée. Elle mêle trois métiers : concevoir (une salle sur le
papier), construire (dans Minecraft, à la main, par la propriétaire du projet) et coder (un mini-jeu nouveau).

**Fini quand :** 9 salles sont dans la banque, chacune jouée au moins une fois à trois avec ses temps mesurés ;
le plan fixe est construit et décrit ; une partie complète a son prologue, ses répliques et ses fins.

## À lire avant

- [`plan.md`](../plan.md), sections 5, 7 et 9.
- [`architecture.md`](../architecture.md), section 7.
- La liste des mécanismes disponibles (T3.5, T8.2) et leurs paramètres.

## Sous-tâches

### T9.1 Les fiches de salle (Fable)

Une fiche par salle dans `Documentation/salles/<id>.md`, écrite **avant** de construire. C'est un travail de
conception : il se fait avec la propriétaire, pas dans son dos.

Chaque fiche contient :

- le thème et l'ambiance (un lieu du complexe : archives, serre, salle des machines, dortoir…) ;
- la chaîne d'étapes, dans l'ordre, avec pour chacune le mécanisme, ce qu'il demande et ce qu'il donne ;
- au moins une étape qui **oblige à se parler** (coopération à trois) ;
- les emplacements à prévoir dans le décor, et où ;
- les trois aides de chaque étape ;
- la difficulté et la durée visées (25 minutes pour un groupe moyen) ;
- le secret de la salle (cachette du temps libre) et le fragment de lore qu'elle porte.

Règles de composition :

- 4 à 6 étapes ; pas deux fois le même mécanisme dans une salle ;
- au moins un mini-jeu à l'écran, au moins une recherche dans le décor, au moins une étape de coopération ;
- au plus deux étapes Cobblemon ;
- une salle se comprend sans texte long : l'énigme se lit dans le décor.

Pour la première soirée : 9 fiches, 3 par manche, de difficulté égale par manche et montante d'une manche à
l'autre.

- **Fini quand :** les 9 fiches sont validées.

### T9.2 La construction (hors code)

Faite en jeu par la propriétaire, avec l'éditeur de T3.2 :

- le plan fixe (hall, salle de vote et ses 6 cabines, porte finale, 3 ailes, couloirs, recoins, cachettes), puis
  sa description avec l'éditeur de T2.1 ;
- chaque salle dans son gabarit de 48 × 48 × 24, avec ses emplacements et sa chaîne.

Un agent peut aider en préparant, pour chaque fiche, la liste de contrôle de construction (emplacements à poser,
identifiants à donner) et en relisant la définition enregistrée contre la fiche.

### T9.3 Les mini-jeux suivants (Sonnet, un par agent ; Fable pour la conception de chacun)

Le catalogue du plan (section 5) en compte une quarantaine. Après les six de T3.5 et les six de T8.2, les ajouter
selon les besoins des fiches de salle, par ordre de priorité :

1. `sliding_blocks` : dégager la pièce qui doit sortir.
2. `rotating_tiles` : reconstituer un circuit.
3. `safe_dial` : coffre à molette, au son.
4. `twin_screens` : deux terminaux éloignés, chacun la moitié du problème.
5. `radio_console` : superposer deux ondes.
6. `torn_document` : assembler des morceaux.
7. `old_terminal` : un petit système à fouiller.
8. `deduction_grid` : indices logiques générés.
9. `invisible_maze` : un seul joueur voit les murs.
10. `memory_trio` : chacun ne voit que sa couleur d'une séquence.

Chaque mini-jeu : une courte note de conception (règle, ce qui est tiré, comment on garantit qu'il est
résoluble, difficulté), puis le code sur le modèle de `ring_dial`.

- **Fini quand :** chaque mini-jeu demandé par une fiche de salle existe et passe son test de génération.

### T9.4 Le lore, l'animateur, les fins (Fable)

- `Documentation/lore.md` : ce qu'on retient du lore de la communauté pour le jeu (les fantômes, le premier
  d'entre eux, l'appel à quitter le monde des vivants), écrit avec la propriétaire. Les noms du jeu y sont choisis :
  le vote, la porte finale, les bracelets, le complexe.
- Les répliques de l'animateur, par moment du jeu (prologue, chaque étape, mort, trahison, alliance, porte
  finale), en plusieurs variantes tirées au hasard. Dans un fichier de textes à part, facile à changer d'une
  soirée à l'autre, en français et en anglais.
- Les fragments de lore à trouver (documents des salles et des cachettes) : une histoire qui se complète de
  partie en partie.
- Les fins : sortie solitaire, sortie de quelques-uns, sortie de tous, personne. Chacune a son texte et sa mise
  en scène (annonce, révélation des rôles, récapitulatif des votes manche par manche).
- **Fini quand :** une partie d'essai se déroule avec tous ses textes, et les quatre fins se déclenchent.

### T9.5 L'habillage final (Fable)

- Le bracelet à l'écran : sa forme définitive, ses animations (gain, perte, changement de couleur, mort).
- L'écran de vote en cabine, l'écran des résultats, le prologue : les trois moments forts, à soigner.
- Les sons et, si la propriétaire le souhaite, une ambiance de combat Showtime réservée à l'événement.
- **Fini quand :** validé en jeu.

## Hors périmètre

Changer une règle du jeu : cela repasse par le plan.

## Pièges

- Une salle trop bavarde ou trop dépendante d'une seule idée bloque un groupe : toujours deux pistes ouvertes en
  parallèle quand c'est possible.
- L'équilibre se mesure, il ne se devine pas : aucune salle n'entre dans la banque sans une partie d'essai à trois.
- Les textes de lore parlent de personnes réelles de la communauté : les faire relire par la propriétaire avant
  toute soirée.
