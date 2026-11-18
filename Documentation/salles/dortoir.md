# Salle `dortoir` : Le Dortoir

> Brouillon à valider. Proposition de conception pour la propriétaire (T9.1) : rien ici n'est arrêté, tout se corrige.

| | |
|---|---|
| Manche | 1 (facile) |
| Difficulté visée | 2 sur 5 |
| Durée visée | 25 minutes pour un groupe moyen (somme des estimations du catalogue : environ 690 s ; à mesurer) |
| Étapes | 5 |
| Étapes Cobblemon | 1 (`unown_text`) |
| Mécanismes à coder | `rotating_tiles` (à coder) |

## Thème et ambiance

Un long dortoir : deux rangées de lits de fer, couvertures grises, une veilleuse qui clignote, des dessins d'enfants
punaisés au mur. Les griffonnages au-dessus des lits sont écrits dans un alphabet étrange. Doux et un peu triste : la
salle la plus calme de la soirée.

## Chaîne d'étapes

| # | Id | Mécanisme | Paramètres | Ce qu'elle demande | Ce qu'elle donne | Prérequis |
|---|----|-----------|------------|--------------------|------------------|-----------|
| 1 | `peluches` | `pickup_set` (recherche dans le décor) | `spots` 4, `filled` 3 | Trouver 3 jouets perdus sous les lits et dans les malles | Une page de journal (`document`) | aucun |
| 2 | `dessins` | `unown_text` (Cobblemon) | `length` 3 | Déchiffrer un message dessiné au mur, avec l'alphabet du journal, et taper 3 chiffres | Rien : ouvre l'étape 4 | `peluches` |
| 3 | `veilleuse` | `rotating_tiles` (à coder) | par défaut | Faire tourner les tuyaux de la veilleuse pour reconstituer le circuit | Rien : ouvre l'étape 4 | aucun |
| 4 | `malle` | `keypad` | `length` 4 | Taper le code de la malle au pied du lit | Rien : ouvre l'étape 5 | `dessins`, `veilleuse` |
| 5 | `lampes` | `three_levers` (coop) | `ordered` false, `gap` 6, `hold` 14 | Trois interrupteurs de lits, aux trois coins, tirés ensemble par trois joueurs | Ouvre la sortie (rôle `exit`) | `malle` |

### Indices donnés

- `peluches` : `spot_1..4` sur `jouet_1..4` ; `document` (journal) sur `chevet_journal` (dès l'ouverture).
- `dessins` : `wall_text` sur `mur_dessins` ; `alphabet` sur `chevet_alphabet` (dès l'ouverture).
- `malle` : `code` sur `mur_malle` (dès l'ouverture), par exemple une date de naissance sous un cadre.

### Étape de coopération

`lampes` : trois interrupteurs aux trois coins, trois joueurs différents, tirés presque ensemble (délai de 6 s) : il faut se
compter et se répartir les coins. L'ordre n'est pas imposé en première manche.

## Les trois aides de chaque étape

| Étape | Aide 1 (vague) | Aide 2 | Aide 3 (directe) |
|-------|----------------|--------|------------------|
| `peluches` | Des jouets manquent aux lits. | Regardez sous les lits et dans les malles. | 4 endroits, 3 jouets : un lit chacun. |
| `dessins` | Le dessin au mur est une phrase. | L'alphabet est sur la page du journal. | Chaque signe est une lettre ; la phrase épelle 3 chiffres en toutes lettres. |
| `veilleuse` | Le circuit est coupé. | Tournez un tuyau à la fois, de la prise à l'ampoule. | Reliez la prise (haut gauche) à l'ampoule (bas droite). |
| `malle` | Le code est une date. | Cherchez une date derrière un cadre. | 4 chiffres, sur le cadre du mur du fond. |
| `lampes` | Trois interrupteurs, trois coins. | Il faut trois personnes, une par coin. | Comptez à voix haute et tirez ensemble, chacun le sien. |

## Emplacements prévus dans le décor

- Lits en deux rangées (zones est et ouest), 4 spots de jouets sous les lits.
- Mur des dessins au sud ; chevet du fond (journal et alphabet) à l'ouest.
- Veilleuse : mur nord-est, panneau de tuyaux au niveau des yeux.
- Malle au pied d'un lit, au centre ; trois interrupteurs aux coins.

## Secret de la salle

Cachette du temps libre : une latte du plancher sous le dernier lit de l'est.

Candidats à la cachette (D228, genre `secret`, la graine en tire **un seul** par partie) : `cachette_1`, `cachette_2`. Ils sont déjà dans le gabarit (`room new <id> dortoir`) ; la fiche en propose 2 ; 2 ou 3 candidats au total conviennent.

## Fragment de lore

> Placeholder : texte à écrire dans `Documentation/salles/lore.md` (T9.4). Emplacement prévu `F-dortoir`, porté par un
> dessin d'enfant (décor construit). Prémisse générique : fantômes ; le premier appelle les autres à quitter le monde des vivants.

## Vérification de composition

- 5 étapes, 5 mécanismes différents. Mini-jeu à l'écran : oui. Recherche : `peluches`. Coop : `lampes`. Cobblemon : 1. Lecture dans le décor : dessins, journal.

## Liste de construction (T9.2)

| Id | Genre | Zone | Taille | Sert à |
|----|-------|------|--------|--------|
| `sortie` | `exit` | mur nord, centre | 3 × 3 × 1 | rôle `exit` de `lampes` |
| `jouet_1` à `jouet_4` | `pickup` | sous les lits, dans les malles | 1 × 1 × 1 | `spot_1..4` de `peluches` |
| `chevet_journal` | `document` | chevet ouest, fond | 1 × 1 × 1 | `document` de `peluches` |
| `pad_dessins` | `keypad` | mur sud, près des dessins | 1 × 1 × 1 | rôle `terminal` de `dessins` |
| `mur_dessins` | `wall_clue` | mur sud, au-dessus du lit | 3 × 2 × 1 | `wall_text` |
| `chevet_alphabet` | `document` | chevet est | 1 × 1 × 1 | `alphabet` |
| `pad_veilleuse` | `terminal` | mur nord-est | 1 × 1 × 1 | rôle de `veilleuse` (à confirmer au codage) |
| `pad_malle` | `keypad` | au pied d'un lit, centre | 1 × 1 × 1 | rôle `keypad` de `malle` |
| `mur_malle` | `wall_clue` | derrière un cadre, fond | 2 × 2 × 1 | `code` de `malle` |
| `lampe_1` à `lampe_3` | `lever` | trois coins de la salle | 1 × 1 × 1 | `lever_1..3` de `lampes` |
| `cachette_1` | `secret` | Une latte du plancher sous le dernier lit de l'est | 1 × 1 × 1 | candidat de cachette (D228) |
| `cachette_2` | `secret` | Dans l'oreiller du lit du fond, au nord | 1 × 1 × 1 | candidat de cachette (D228) |

- **Ids des étapes** : `peluches`, `dessins`, `veilleuse`, `malle`, `lampes`.
- **À laisser vide** : air devant chaque emplacement, les cases `pickup`, l'espace sous les lits (au moins 1 bloc de haut).
- **Décor construit sans emplacement** : lits, couvertures, veilleuse, dessins d'enfants hors étape, pancarte du lore.
- **Contrôles** : `alphabet` donné ; les trois interrupteurs aux trois coins ; une sortie ; durée estimée proche de 690 s.
