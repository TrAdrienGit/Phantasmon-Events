# Salle `observatoire` : L'Observatoire

> Brouillon à valider. Proposition de conception pour la propriétaire (T9.1) : rien ici n'est arrêté, tout se corrige.

| | |
|---|---|
| Manche | 3 (difficile) |
| Difficulté visée | 4 sur 5 |
| Durée visée | 25 minutes pour un groupe moyen (somme des estimations du catalogue : environ 1 080 s ; à mesurer, la plus longue de la manche) |
| Étapes | 6 |
| Étapes Cobblemon | 1 (`cry_match`) |
| Mécanismes à coder | `rotating_tiles`, `twin_screens` (à coder) |

## Thème et ambiance

Une coupole en haut du complexe : un grand télescope de cuivre, des cartes du ciel punaisées, un plafond ouvert sur
un ciel qui ne bouge plus. Deux lunettes éloignées sur deux balcons. Silence, froid, étoiles peintes. Une radio grésille :
les cris de Pokémon sont des signaux captés dans la nuit.

## Chaîne d'étapes

| # | Id | Mécanisme | Paramètres | Ce qu'elle demande | Ce qu'elle donne | Prérequis |
|---|----|-----------|------------|--------------------|------------------|-----------|
| 1 | `carte` | `keypad` (recherche dans le décor) | `length` 5 | Taper le code du tiroir de la table des cartes ; le code est caché sur une carte du ciel parmi plusieurs | Rien : ouvre l'étape 3 | aucun |
| 2 | `radio` | `cry_match` (Cobblemon) | `count` 4 | Associer quatre cris captés aux silhouettes, en tenant compte du bruit | Rien : ouvre l'étape 4 | aucun |
| 3 | `astrolabe` | `ring_dial` | `rings` 4, `symbols` 7, `links` 2 | Tourner les anneaux de l'astrolabe jusqu'à la constellation du mur (deux liens cachés) | Rien : ouvre l'étape 5 | `carte` |
| 4 | `constellation` | `rotating_tiles` (à coder) | par défaut | Faire tourner les tuiles du plafond pour relier les étoiles | Rien : ouvre l'étape 5 | `radio` |
| 5 | `serrure` | `code_breaker` | `length` 4, `alphabet` 7, `kind` symbols, `maxTries` 12 | Déduire la combinaison de la lunette de visée | Rien : ouvre l'étape 6 | `astrolabe`, `constellation` |
| 6 | `lunettes` | `twin_screens` (coop, à coder) | par défaut | Deux lunettes éloignées : chacune voit la moitié du ciel, il faut aligner les deux | Ouvre la sortie (rôle `exit`) | `serrure` |

### Indices donnés

- `carte` : `code` sur `carte_ciel` (dès l'ouverture), parmi plusieurs cartes de décor qui ne servent à rien.
- `astrolabe` : `symbols` sur `mur_constellation` (dès l'ouverture).
- `lunettes` : à fixer au codage de `twin_screens`.

### Étape de coopération

`lunettes` : deux balcons à plus de 25 blocs ; la moitié du ciel par balcon. Le troisième joueur fait le relais ou la
carte. Voir D223.

## Les trois aides de chaque étape

| Étape | Aide 1 (vague) | Aide 2 | Aide 3 (directe) |
|-------|----------------|--------|------------------|
| `carte` | Plusieurs cartes, une seule compte. | Cherchez des chiffres écrits à l'encre rouge. | Le code de 5 chiffres est sur la carte épinglée sous le télescope. |
| `radio` | Le bruit couvre un cri sur deux. | Associez d'abord ceux dont vous êtes sûrs. | L'écran donne le nombre de bonnes paires : changez une paire à la fois. |
| `astrolabe` | Les quatre anneaux ne tournent pas seuls. | Tournez-en un et regardez les autres. | Deux liens cachés : notez qui entraîne qui. |
| `constellation` | Les étoiles sont reliées par des tuiles. | Tournez une tuile à la fois. | Reliez l'étoile du haut à celle du bas. |
| `serrure` | « Bien placé » et « mal placé » disent tout. | 12 essais : n'en gaspillez pas. | Cherchez d'abord les symboles présents. |
| `lunettes` | Deux lunettes, deux moitiés. | Chacun décrit ce qu'il voit. | Alignez les deux étoiles marquées. |

## Emplacements prévus dans le décor

- Table des cartes au sud-ouest, plusieurs cartes décor, une seule porte le code.
- Radio : mur ouest ; astrolabe : centre ; constellation au plafond (mur nord, très haut) et son pupitre.
- Pupitre de la serrure : mur est. Deux lunettes : balcons nord-ouest et sud-est.

## Secret de la salle

Cachette du temps libre : dans le pied du grand télescope, centre. Emplacement `cachette`.

## Fragment de lore

> Placeholder : texte à écrire dans `Documentation/salles/lore.md` (T9.4). Emplacement prévu `F-observatoire`, porté par
> une carte du ciel (décor construit). Prémisse générique : fantômes ; le premier appelle les autres à quitter le monde des vivants.

## Vérification de composition

- 6 étapes, 6 mécanismes différents. Écran : oui. Recherche : `carte`. Coop : `lunettes`. Cobblemon : 1. Dépendances multiples pour tenir 25 minutes avec trois branches.

## Liste de construction (T9.2)

| Id | Genre | Zone | Taille | Sert à |
|----|-------|------|--------|--------|
| `sortie` | `exit` | mur nord, centre | 3 × 3 × 1 | rôle `exit` de `lunettes` |
| `pad_carte` | `keypad` | mur sud-ouest | 1 × 1 × 1 | rôle `keypad` de `carte` |
| `carte_ciel` | `document` | table des cartes | 1 × 1 × 1 | `code` |
| `radio` | `terminal` | mur ouest | 1 × 1 × 1 | rôle `console` de `radio` |
| `pad_astrolabe` | `terminal` | centre | 1 × 1 × 1 | rôle `terminal` de `astrolabe` |
| `mur_constellation` | `wall_clue` | mur est, haut | 2 × 2 × 1 | `symbols` |
| `pad_plafond` | `terminal` | mur nord | 1 × 1 × 1 | rôle de `constellation` (à confirmer au codage) |
| `pad_serrure` | `terminal` | mur est | 1 × 1 × 1 | rôle `terminal` de `serrure` |
| `lunette_a`, `lunette_b` | `terminal` | balcons nord-ouest et sud-est | 1 × 1 × 1 | rôles de `lunettes` (à confirmer) |
| `cachette` | `secret` | pied du télescope | 1 × 1 × 1 | cachette |

- **Ids des étapes** : `carte`, `radio`, `astrolabe`, `constellation`, `serrure`, `lunettes`.
- **À laisser vide** : air devant chaque emplacement ; rien dans les écrans.
- **Décor construit sans emplacement** : coupole, télescope, cartes du ciel inutiles, balcons, carte du lore.
- **Contrôles** : `code` donné ; les deux lunettes à plus de 25 blocs ; une sortie ; durée estimée proche de 1 080 s.
