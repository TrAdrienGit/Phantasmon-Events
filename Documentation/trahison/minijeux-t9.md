# Mini-jeux de T9.3 : notes de conception

Chaque note tient en cinq lignes : règle, ce qui est dessiné, garantie de résolubilité, boutons de difficulté.
Code sur le modèle de `ring_dial` ; le protocole est dans `protocole.md`, sous `EventsTrahisonPuzzle`.

## `sliding_blocks` (blocs coulissants)

- **Règle** : une grille carrée, des blocs de 2 ou 3 cases qui ne glissent que dans leur axe ; la pièce marquée (horizontale, sur la ligne de sortie) doit sortir par le bord droit. Un geste `{block, delta}` glisse un bloc de `delta` cases si le chemin est libre.
- **Dessiné** : la grille, les blocs (la pièce à sortir en `ACCENT`), l'ouverture de sortie à droite, le compteur de coups.
- **Résolubilité** : on part de l'état résolu (pièce contre la sortie), on pose les autres blocs sans chevauchement, puis on rejoue des glissements légaux au hasard ; chaque glissement est réversible. Le test le prouve par un parcours en largeur indépendant sur 1000 tirages.
- **Difficulté** : `size` 5..7, `blocks` 3..10 (autres que la pièce), `shuffle` 6..60 glissements de mélange.

## `rotating_tiles` (tuiles tournantes)

- **Règle** : une grille de tuiles (droite, coude, té, bout, vide) ; un clic tourne une tuile d'un quart de tour. Le courant entre par le bord gauche (ligne source) et la lampe, au bord droit, s'allume quand le circuit les relie.
- **Dessiné** : les tuiles avec leurs branches, les cases alimentées en `ACCENT_HOT`, la source et la lampe ; une tuile fixe porte un rivet.
- **Résolubilité** : un chemin aléatoire sans croisement est tracé de la source à la lampe, ses tuiles reçoivent droite ou coude, le reste est rempli de leurres ; puis toutes les rotations sont mélangées (jamais déjà relié). La vue du MJ porte les rotations résolues : le test les rejoue en clics et vérifie l'allumage avec son propre parcours.
- **Difficulté** : `size` 3..5, `fixed` 0..6 tuiles du chemin déjà bien placées et verrouillées.

## `safe_dial` (coffre à molette, au son)

- **Règle** : un cadran circulaire de `positions` crans ; une combinaison de `numbers` nombres, chacun à atteindre en tournant dans un sens imposé (droite, gauche, droite...), puis à verrouiller. Chaque cran renvoie un repère (`far`, `mid`, `near`, `hit`) selon la distance au nombre attendu ; un verrouillage faux remet la combinaison à zéro.
- **Dessiné** : le cadran et son repère, le compteur de nombres validés, un témoin lumineux du dernier repère (doublure visuelle du son). Le son vient des sons vanilla (clic de comparateur plus aigu en approchant, cloche près du but, clunk sur le bon cran), côté client, depuis le repère de la vue.
- **Résolubilité** : le cadran est circulaire, on peut donc toujours atteindre un cran dans un sens donné ; le repère `hit` signale le cran exact. Le test fait jouer un solveur qui n'utilise que les repères (balayage d'un tour) et ouvre 1000 coffres.
- **Difficulté** : `positions` 24..60, `numbers` 3..5, `wide` (bande `mid` annoncée, sinon silence au loin).

## `twin_screens` (écrans jumeaux)

- **Règle** : deux terminaux éloignés (rôles `left`, `right`), chacun affichant dans la salle, comme indice, la moitié du problème (une suite de symboles). Il faut saisir à un terminal la suite lue à l'autre : deux joueurs différents, un par terminal. Une suite exacte verrouille son terminal.
- **Dessiné** : l'écran de saisie du terminal pris (alphabet, suite en cours, effacer, envoyer), l'état de l'autre terminal et qui le tient.
- **Résolubilité** : les deux suites sont tirées de l'alphabet de la vue, et la saisie attendue à un terminal est exactement l'indice de l'autre ; ce secret ne part que par l'indice donné au terminal. Le test vérifie les saisies attendues, le refus d'un même joueur aux deux terminaux et les 1000 tirages.
- **Difficulté** : `length` 3..6 symboles, `alphabet` 6..16 symboles proposés.
