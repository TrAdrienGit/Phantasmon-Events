# Salle `crypte` : La Crypte

> Brouillon à valider. Proposition de conception pour la propriétaire (T9.1) : rien ici n'est arrêté, tout se corrige.

| | |
|---|---|
| Manche | 3 (difficile) |
| Difficulté visée | 4 sur 5 |
| Durée visée | 25 minutes pour un groupe moyen (somme des estimations du catalogue : environ 950 s ; à mesurer) |
| Étapes | 6 |
| Étapes Cobblemon | 2 (`pc_box`, `battle_puzzle`) |
| Mécanismes à coder | `twin_screens` (à coder) |

## Thème et ambiance

Les sous-sols du complexe : une crypte basse de plafond, voûtes de pierre, niches de caveaux, bougies éteintes, un
vieux PC de combat posé sur un sarcophage. Humide et sombre : la dernière salle, celle où l'on sent que le « premier »
est proche. Pensée pour une fin de soirée : peu de texte, beaucoup de pièces à trouver.

## Chaîne d'étapes

| # | Id | Mécanisme | Paramètres | Ce qu'elle demande | Ce qu'elle donne | Prérequis |
|---|----|-----------|------------|--------------------|------------------|-----------|
| 1 | `os` | `pickup_set` (recherche dans le décor) | `spots` 5, `filled` 4 | Retrouver 4 clefs d'os dans les caveaux et les ramasser | Rien : ouvre l'étape 2 | aucun |
| 2 | `grille` | `wiring` | `wires` 5, `colors` 4 | Câbler la grille d'entrée du caveau d'après le schéma | Rien : ouvre l'étape 3 | `os` |
| 3 | `caveau` | `three_dials` | par défaut | Aligner les trois molettes du caveau central | Rien : ouvre l'étape 5 | `grille` |
| 4 | `boite` | `pc_box` (Cobblemon) | `count` 8, `fixed` 3 | Ranger la boîte du PC de la crypte d'après une règle cachée | Rien : ouvre l'étape 5 | aucun |
| 5 | `duel` | `battle_puzzle` (Cobblemon) | `turns` 3, `mons` 2, `moves` 3 | Trouver la seule suite d'actions qui gagne le duel en 3 tours | Un code à 4 chiffres, dès que résolu | `caveau`, `boite` |
| 6 | `sarcophage` | `twin_screens` (coop, à coder) | par défaut | Deux pupitres de chaque côté de la crypte : chacun une moitié du sceau, à aligner en parlant | Ouvre la sortie (rôle `exit`) | `duel` |

### Indices donnés

- `os` : `spot_1..5` sur `os_1..5` (dès l'ouverture).
- `grille` : `scheme` sur `mur_grille` ; `scheme_text` sur `pierre_grille` (dès l'ouverture).
- `caveau` : `code` sur `mur_caveau` (dès l'ouverture).
- `duel` : `reward` (code) sur `mur_sceau` une fois résolue ; ce code est le coup d'envoi de `sarcophage` (le lien est à décider avec `twin_screens`).
- `sarcophage` : à fixer au codage de `twin_screens`.

### Étape de coopération

`sarcophage` : deux pupitres de chaque côté de la crypte (plus de 25 blocs), le dernier effort d'équipe. Voir D223.

## Les trois aides de chaque étape

| Étape | Aide 1 (vague) | Aide 2 | Aide 3 (directe) |
|-------|----------------|--------|------------------|
| `os` | Les caveaux ne sont pas tous vides. | Regardez dans les niches et derrière les sarcophages. | 5 endroits, 4 clefs : un secteur par joueur. |
| `grille` | Chaque fil a sa couleur. | Le schéma est sur le mur et sur la pierre. | La n-ième couleur est celle de la borne de gauche n. |
| `caveau` | Trois molettes, trois chiffres. | Le chiffre est gravé près du caveau. | Alignez sur les 3 chiffres du mur du caveau. |
| `boite` | Les Pokémon déjà rangés montrent la règle. | Comparez leurs numéros, types, poids ou tailles. | La règle se lit sur les trois bloqués ; rangez les autres de même. |
| `duel` | Un seul enchaînement gagne. | Chaque tour compte : testez dans l'ordre. | Un échec remet la position de départ : réessayez en variant un tour à la fois. |
| `sarcophage` | Deux pupitres, deux moitiés. | Chacun décrit son sceau. | Alignez les deux sceaux en parlant. |

## Emplacements prévus dans le décor

- Caveaux en demi-cercle : 5 spots d'os, par secteur.
- Grille d'entrée du caveau : mur sud ; caveau central : centre.
- PC de la crypte : sarcophage nord-ouest ; arène de duel : sarcophage nord-est.
- Deux pupitres du sceau : un à l'ouest, un à l'est.

## Secret de la salle

Cachette du temps libre : dans une niche scellée au fond du caveau central. Emplacement `cachette`.

## Fragment de lore

> Placeholder : texte à écrire dans `Documentation/salles/lore.md` (T9.4). Emplacement prévu `F-crypte`, porté par une
> plaque funéraire (décor construit). Prémisse générique : fantômes ; le premier appelle les autres à quitter le monde des vivants.

## Vérification de composition

- 6 étapes, 6 mécanismes différents. Écran : oui. Recherche : `os`. Coop : `sarcophage`. Cobblemon : 2 (le maximum).

## Liste de construction (T9.2)

| Id | Genre | Zone | Taille | Sert à |
|----|-------|------|--------|--------|
| `sortie` | `exit` | mur nord, centre | 3 × 3 × 1 | rôle `exit` de `sarcophage` |
| `os_1` à `os_5` | `pickup` | niches et caveaux | 1 × 1 × 1 | `spot_1..5` de `os` |
| `pad_grille` | `terminal` | mur sud | 1 × 1 × 1 | rôle `panel` de `grille` |
| `mur_grille` | `wall_clue` | mur sud, haut | 2 × 2 × 1 | `scheme` |
| `pierre_grille` | `document` | dalle devant la grille | 1 × 1 × 1 | `scheme_text` |
| `pad_caveau` | `terminal` | centre | 1 × 1 × 1 | rôle `dials` de `caveau` |
| `mur_caveau` | `wall_clue` | gravure du caveau | 2 × 2 × 1 | `code` |
| `pad_boite` | `terminal` | sarcophage nord-ouest | 1 × 1 × 1 | rôle `terminal` de `boite` |
| `arene` | `terminal` | sarcophage nord-est | 1 × 1 × 1 | rôle `arena` de `duel` |
| `mur_sceau` | `wall_clue` | mur est | 2 × 2 × 1 | `reward` de `duel` (une fois résolue) |
| `pupitre_a`, `pupitre_b` | `terminal` | ouest et est | 1 × 1 × 1 | rôles de `sarcophage` (à confirmer) |
| `cachette` | `secret` | niche scellée | 1 × 1 × 1 | cachette |

- **Ids des étapes** : `os`, `grille`, `caveau`, `boite`, `duel`, `sarcophage`.
- **À laisser vide** : air devant chaque emplacement ; les cases `pickup`.
- **Décor construit sans emplacement** : voûtes, niches, bougies, sarcophages, plaque du lore.
- **Contrôles** : `scheme`, `code` et `reward` donnés ; deux pupitres distants d'au moins 25 blocs ; une sortie ; durée estimée proche de 950 s.
