# Salle `archives` : Les Archives

> Brouillon à valider. Proposition de conception pour la propriétaire (T9.1) : rien ici n'est arrêté, tout se corrige.

| | |
|---|---|
| Manche | 1 (facile) |
| Difficulté visée | 2 sur 5 |
| Durée visée | 25 minutes pour un groupe moyen (somme des estimations du catalogue : environ 740 s, le reste est trajets, discussion et erreurs ; à mesurer, voir README) |
| Étapes | 6 (aucun mécanisme en double) |
| Étapes Cobblemon | 0 |
| Mécanismes à coder | `sliding_blocks` (à coder) |

## Thème et ambiance

Les archives du complexe : de hauts rayonnages métalliques, des fichiers à tiroirs, une lampe verte de bureau, de la
poussière. Un meuble central de classement et, au fond, un coffre-fort mural. Lumière basse, silence, papier qui craque.
On comprend en entrant qu'il faut **fouiller les rayons**, puis rouvrir le coffre du fond.

## Chaîne d'étapes

Légende : coop = étape qui oblige à se parler.

| # | Id | Mécanisme | Paramètres | Ce qu'elle demande | Ce qu'elle donne | Prérequis |
|---|----|-----------|------------|--------------------|------------------|-----------|
| 1 | `fiches` | `pickup_set` (recherche dans le décor) | `spots` 4, `filled` 3 | Trouver 3 fiches d'archives cachées dans 4 endroits des rayons et les ramasser | Le registre (page `document`) sur le bureau, dès l'ouverture | aucun |
| 2 | `casier` | `keypad` | `length` 4 | Taper le code du casier | Rien : ouvre l'étape 4 | aucun |
| 3 | `index` | `wiring` | `wires` 4, `colors` 3 | Relier les tiroirs du fichier à leurs étiquettes de couleur, d'après le schéma | Rien : ouvre l'étape 5 | `fiches` |
| 4 | `cadran` | `three_dials` | par défaut | Aligner les trois molettes de la date du coffre | Rien : ouvre l'étape 5 | `casier` |
| 5 | `rayons` | `sliding_blocks` (à coder) | par défaut | Faire glisser les chariots de classement pour dégager le chariot du coffre | Rien : ouvre l'étape 6 | `index`, `cadran` |
| 6 | `porte` | `three_levers` (coop) | `ordered` false, `gap` 5, `hold` 12 | Trois joueurs, trois manettes de rayonnage aux trois coins, en même temps | Ouvre la sortie (rôle `exit`) | `rayons` |

Les étapes 1 et 2 se jouent en parallèle ; 3 et 4 en parallèle ensuite ; 5 puis 6 sont communes.

### Indices donnés (qui montre quoi, où)

- `fiches` : `spot_1` à `spot_4` sur les emplacements `fiche_1` à `fiche_4` (dès l'ouverture) ; `document` (registre) sur `bureau_registre`.
- `casier` : `code` sur `mur_casier` (dès l'ouverture) : le code est inscrit sur une plaque derrière un rayonnage.
- `index` : `scheme` sur `mur_index` et `scheme_text` sur `bureau_index` (dès l'ouverture).
- `cadran` : `code` sur `mur_cadran` (dès l'ouverture) : une date sur le dos d'une affiche.

### Étape de coopération

`porte` : trois manettes aux trois coins de la salle, trois joueurs différents. L'ordre n'est pas imposé (`ordered`
false) pour une première manche : le groupe doit seulement s'organiser et se compter.

## Les trois aides de chaque étape

| Étape | Aide 1 (vague) | Aide 2 | Aide 3 (directe) |
|-------|----------------|--------|------------------|
| `fiches` | Les rayons n'ont pas tous la même poussière. | Cherchez des fiches qui dépassent des étagères, pas sur le bureau. | Il y a 4 endroits, 3 fiches : regardez tous les rayons, chacun le sien. |
| `casier` | Le casier ne parle pas : un chiffre est ailleurs. | Cherchez une plaque chiffrée contre un mur. | Le code de 4 chiffres est sur la plaque derrière le rayonnage ouest. |
| `index` | Les étiquettes de couleur de l'index sont une consigne. | Le schéma est sur le mur et sur le bureau. | La n-ième couleur du schéma est celle de la borne de gauche n. |
| `cadran` | Trois molettes, trois chiffres : une date. | Une affiche porte la date au dos. | Alignez les trois molettes sur les trois chiffres de l'affiche. |
| `rayons` | Un chariot en bloque un autre. | Dégagez d'abord les chariots qui gênent le rail. | Faites sortir le chariot marqué en le glissant pas à pas hors du rail. |
| `porte` | Trois manettes, trois personnes. | Chacune à un coin de la salle, en même temps. | Comptez à voix haute, tirez ensemble. |

## Emplacements prévus dans le décor

Repère : la sortie est au nord, l'entrée au sud, l'ouest est à gauche en entrant.

- Rayonnages en deux allées nord-sud, au centre : les 4 emplacements de fiches.
- Bureau de l'archiviste, à l'ouest, en entrée : registre, schéma de l'index.
- Fichier à tiroirs et panneau de câblage, à l'est, au milieu : `pad_index`.
- Casier numérique, au mur sud-ouest ; coffre à molettes, au mur est du fond ; mobile de rayonnage au nord ; manettes aux quatre coins sauf un.

## Secret de la salle

Cachette du temps libre : un livre vide, en haut du rayonnage le plus à l'est, derrière les fiches ramassées. Emplacement
`cachette` (genre `secret`, un seul par salle).

## Fragment de lore

> Placeholder : texte à écrire dans `Documentation/salles/lore.md` (T9.4, pas encore écrit : le lore de la communauté
> est à la propriétaire). Emplacement prévu `F-archives`, porté par un décor construit (pancarte ou livre), pas par une
> étape. Prémisse générique : les occupants sont des fantômes ; l'un d'eux, le premier, appelle les autres à quitter le monde des vivants.

## Vérification de composition

- 6 étapes, 6 mécanismes différents. Mini-jeu à l'écran : oui (`keypad`, `wiring`, `three_dials`, `sliding_blocks`). Recherche dans le décor : oui (`fiches`). Coopération : oui (`porte`). Cobblemon : 0. Aucun texte long.

## Liste de construction (T9.2)

Gabarit 48 × 48 × 24, sortie au nord (z = 0), entrée au sud. Positions : zones approximatives, à régler en construisant.

| Id | Genre | Zone | Taille | Sert à |
|----|-------|------|--------|--------|
| `sortie` | `exit` | mur nord, centre | 3 × 3 × 1 | rôle `exit` de `porte` |
| `fiche_1` à `fiche_4` | `pickup` | quatre endroits dans les rayons | 1 × 1 × 1 | `spot_1..4` de `fiches` |
| `bureau_registre` | `document` | bureau ouest, entrée | 1 × 1 × 1 | `document` de `fiches` |
| `pad_casier` | `keypad` | mur sud-ouest | 1 × 1 × 1 | rôle `keypad` de `casier` |
| `mur_casier` | `wall_clue` | derrière un rayonnage ouest | 2 × 2 × 1 | `code` de `casier` |
| `pad_index` | `terminal` | est, milieu | 1 × 1 × 1 | rôle `panel` de `index` |
| `mur_index` | `wall_clue` | mur est, près du fichier | 2 × 2 × 1 | `scheme` de `index` |
| `bureau_index` | `document` | bureau, sud | 1 × 1 × 1 | `scheme_text` de `index` |
| `pad_cadran` | `terminal` | mur est, fond (coffre) | 1 × 1 × 1 | rôle `dials` de `cadran` |
| `mur_cadran` | `wall_clue` | dos d'une affiche, centre | 2 × 2 × 1 | `code` de `cadran` |
| `pad_rayons` | `terminal` | mur nord, devant le mobile | 1 × 1 × 1 | rôle `terminal` de `rayons` (à confirmer au codage) |
| `levier_1` à `levier_3` | `lever` | trois coins de la salle | 1 × 1 × 1 | `lever_1..3` de `porte` |
| `cachette` | `secret` | haut du rayonnage est | 1 × 1 × 1 | cachette (« En faire la cachette ») |

- **Ids des étapes** : `fiches`, `casier`, `index`, `cadran`, `rayons`, `porte`.
- **À laisser vide** : l'air devant chaque emplacement (au moins 1 bloc), les 4 emplacements `pickup` (une case vide n'affiche rien), la hauteur d'un bloc au-dessus de `pad_*`. Aucun vrai coffre, levier ni bouton.
- **Décor construit sans emplacement** : rayonnages, bureau, lampe, coffre mural (fermé), pancarte du lore.
- **Contrôles à l'enregistrement** : une seule étape qui ouvre la sortie ; aucune étape qui ne mène nulle part ; les 4 `spot_n` sont donnés ; `code` de `casier` et de `cadran` donnés ; les trois leviers sont à trois coins ; durée estimée proche de 740 s.
