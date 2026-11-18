# Salle `serre` : La Serre

> Brouillon à valider. Proposition de conception pour la propriétaire (T9.1) : rien ici n'est arrêté, tout se corrige.

| | |
|---|---|
| Manche | 1 (facile) |
| Difficulté visée | 2 sur 5 |
| Durée visée | 25 minutes pour un groupe moyen (somme des estimations du catalogue : environ 730 s ; à mesurer) |
| Étapes | 6 |
| Étapes Cobblemon | 2 (`dex_code`, `weigh_in`) |
| Mécanismes à coder | `twin_screens` (à coder) |

## Thème et ambiance

Une serre abandonnée : vitres verdies, terre sèche, tuyaux d'arrosage, bacs de plantes devenues géantes. Une lueur
froide de lampes de culture. Des statues de Pokémon de type Plante parmi les bacs. On lit la salle à l'œil : tuyaux
d'un côté, statues de l'autre, deux postes d'arrosage éloignés.

## Chaîne d'étapes

| # | Id | Mécanisme | Paramètres | Ce qu'elle demande | Ce qu'elle donne | Prérequis |
|---|----|-----------|------------|--------------------|------------------|-----------|
| 1 | `graines` | `pickup_set` (recherche dans le décor) | `spots` 4, `filled` 3 | Trouver 3 sachets de graines dans les bacs et pots | Rien : ouvre l'étape 4 | aucun |
| 2 | `statues` | `dex_code` (Cobblemon) | `count` 2 | Lire deux statues, retrouver leurs numéros de Pokédex, taper les 6 chiffres | Rien : ouvre l'étape 5 | aucun |
| 3 | `hauteur` | `weigh_in` (Cobblemon) | `count` 3, `by` height | Ranger trois piédestaux du plus petit au plus grand | Rien : ouvre l'étape 5 | aucun |
| 4 | `digicode` | `keypad` | `length` 3 | Taper le code du local technique | Rien : ouvre l'étape 6 | `graines` |
| 5 | `thermo` | `three_dials` | par défaut | Régler les trois molettes du thermostat | Rien : ouvre l'étape 6 | `statues`, `hauteur` |
| 6 | `arrosage` | `twin_screens` (coop, à coder) | par défaut | Deux postes éloignés : chacun voit la moitié du circuit d'arrosage, ils doivent se dicter les valeurs | Ouvre la sortie (rôle `exit`) | `digicode`, `thermo` |

Trois branches parallèles au départ (`graines`, `statues`, `hauteur`), qui se rejoignent. Les trois joueurs peuvent se
répartir. Le temps total vient du dernier mini-jeu à deux.

### Indices donnés

- `statues` : `portrait_1`, `portrait_2` sur `statue_1`, `statue_2` ; `dex_page` sur `bureau_dex` (dès l'ouverture).
- `hauteur` : `pedestal_1..3` sur `socle_1..3` ; `scale_sheet` sur `bureau_mesures` (dès l'ouverture).
- `digicode` : `code` sur `mur_local` (dès l'ouverture).
- `thermo` : `code` sur `mur_thermo` (dès l'ouverture).
- `arrosage` : à fixer au codage de `twin_screens` (un indice par poste, probablement).

### Étape de coopération

`arrosage` : deux terminaux séparés de plus de 20 blocs, chacun voit une moitié du circuit. Les trois doivent se parler
(le troisième joueur relit la fiche d'entretien posée entre les deux). Voir D223.

## Les trois aides de chaque étape

| Étape | Aide 1 (vague) | Aide 2 | Aide 3 (directe) |
|-------|----------------|--------|------------------|
| `graines` | Les graines ne sont pas toutes sur le sol. | Regardez dans les pots et sous les bacs. | 4 endroits, 3 sachets : fouillez chaque bac. |
| `statues` | Chaque statue est un Pokémon connu. | Le numéro est dans le Pokédex ; la page abîmée du bureau aide. | Tapez les 3 chiffres de chaque statue, dans l'ordre des statues. |
| `hauteur` | Les piédestaux ne sont pas dans l'ordre. | Le plus petit à gauche. | La fiche du bureau donne les tailles. |
| `digicode` | Le code est inscrit quelque part dans la serre. | Cherchez sur le mur du local technique. | Le code de 3 chiffres est sur la plaque du local. |
| `thermo` | Trois molettes, trois chiffres. | Un thermomètre cassé porte le nombre. | Alignez les molettes sur les 3 chiffres du mur du thermostat. |
| `arrosage` | Deux postes, deux moitiés. | Chacun décrit à l'autre ce qu'il voit. | Associez chaque vanne d'un écran à son tuyau sur l'autre. |

## Emplacements prévus dans le décor

- Bacs en trois allées, 4 spots de graines : zone centrale.
- Statues de Plantes : mur ouest ; socles d'exposition : mur est.
- Local technique avec digicode : coin sud-est ; thermostat : mur nord-est.
- Deux postes d'arrosage : un au nord-ouest, un au sud-est.

## Secret de la salle

Cachette du temps libre : sous un bac renversé, au pied de la plus grosse plante, en zone ouest. Emplacement `cachette`.

## Fragment de lore

> Placeholder : texte à écrire dans `Documentation/salles/lore.md` (T9.4). Emplacement prévu `F-serre`, porté par
> une étiquette de plante (décor construit). Prémisse générique : fantômes ; le premier d'entre eux appelle les autres à quitter le monde des vivants.

## Vérification de composition

- 6 étapes, 6 mécanismes différents. Mini-jeu à l'écran : oui. Recherche : `graines`. Coop : `arrosage`. Cobblemon : 2 (le maximum). Pas de texte long : l'énigme se lit dans les statues et les tuyaux.

## Liste de construction (T9.2)

| Id | Genre | Zone | Taille | Sert à |
|----|-------|------|--------|--------|
| `sortie` | `exit` | mur nord, centre | 3 × 3 × 1 | rôle `exit` de `arrosage` |
| `graine_1` à `graine_4` | `pickup` | bacs et pots | 1 × 1 × 1 | `spot_1..4` de `graines` |
| `pad_dex` | `keypad` | mur ouest, milieu | 1 × 1 × 1 | rôle `terminal` de `statues` |
| `statue_1`, `statue_2` | `wall_clue` | mur ouest, au-dessus des bacs | 2 × 2 × 1 | `portrait_1`, `portrait_2` |
| `bureau_dex` | `document` | bureau ouest | 1 × 1 × 1 | `dex_page` |
| `pad_hauteur` | `terminal` | mur est, milieu | 1 × 1 × 1 | rôle `row` de `hauteur` |
| `socle_1` à `socle_3` | `pedestal` | mur est, alignés | 1 × 1 × 1 | `pedestal_1..3` |
| `bureau_mesures` | `document` | bureau est | 1 × 1 × 1 | `scale_sheet` |
| `pad_local` | `keypad` | coin sud-est | 1 × 1 × 1 | rôle `keypad` de `digicode` |
| `mur_local` | `wall_clue` | derrière une plante, sud | 2 × 2 × 1 | `code` de `digicode` |
| `pad_thermo` | `terminal` | mur nord-est | 1 × 1 × 1 | rôle `dials` de `thermo` |
| `mur_thermo` | `wall_clue` | mur nord, au-dessus des tuyaux | 2 × 2 × 1 | `code` de `thermo` |
| `poste_a`, `poste_b` | `terminal` | nord-ouest et sud-est | 1 × 1 × 1 | rôles de `arrosage` (à confirmer au codage) |
| `cachette` | `secret` | sous un bac, ouest | 1 × 1 × 1 | cachette |

- **Ids des étapes** : `graines`, `statues`, `hauteur`, `digicode`, `thermo`, `arrosage`.
- **À laisser vide** : air devant chaque emplacement, la case des 4 `pickup`, les socles (rien de posé dessus : le jeu affiche les statues).
- **Décor construit sans emplacement** : verre, bacs, tuyaux, lampes, plantes géantes, étiquette du lore.
- **Contrôles** : `scale_sheet` et `dex_page` donnés ; les deux postes sont distants (au moins 20 blocs) ; une sortie ; durée estimée proche de 730 s.
