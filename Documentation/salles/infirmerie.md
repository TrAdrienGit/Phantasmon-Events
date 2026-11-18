# Salle `infirmerie` : L'Infirmerie

> Brouillon à valider. Proposition de conception pour la propriétaire (T9.1) : rien ici n'est arrêté, tout se corrige.

| | |
|---|---|
| Manche | 2 (moyenne) |
| Difficulté visée | 3 sur 5 |
| Durée visée | 25 minutes pour un groupe moyen (somme des estimations du catalogue : environ 900 s ; à mesurer) |
| Étapes | 6 |
| Étapes Cobblemon | 2 (`cry_match`, `pc_box`) |
| Mécanismes à coder | `rotating_tiles` (à coder) |

## Thème et ambiance

Une infirmerie aux rideaux tirés : lits blancs, armoire à pharmacie, un moniteur cardiaque qui bip-bip dans le vide, un
PC de soins de Pokémon dans un coin. Froid, propre, inquiétant. Le son compte : cris de Pokémon dans les haut-parleurs,
bips du moniteur.

## Chaîne d'étapes

| # | Id | Mécanisme | Paramètres | Ce qu'elle demande | Ce qu'elle donne | Prérequis |
|---|----|-----------|------------|--------------------|------------------|-----------|
| 1 | `dossier` | `keypad` (recherche dans le décor) | `length` 4 | Taper le code de l'armoire ; le code est sur un dossier caché derrière un rideau de lit | Rien : ouvre l'étape 3 | aucun |
| 2 | `cris` | `cry_match` (Cobblemon) | `count` 3 | Associer chaque cri du haut-parleur à la silhouette qui lui correspond | Rien : ouvre l'étape 4 | aucun |
| 3 | `perfusion` | `rotating_tiles` (à coder) | par défaut | Faire tourner les tuyaux de perfusion pour reconstituer le circuit | Rien : ouvre l'étape 5 | `dossier` |
| 4 | `pc_soins` | `pc_box` (Cobblemon) | `count` 6, `fixed` 3 | Ranger la boîte du PC de soins d'après une règle que personne ne dit | Rien : ouvre l'étape 5 | `cris` |
| 5 | `pharmacie` | `code_breaker` | `length` 4, `alphabet` 6, `kind` symbols | Trouver la combinaison de l'armoire à produits par déduction | Rien : ouvre l'étape 6 | `perfusion`, `pc_soins` |
| 6 | `bips` | `three_levers` (coop) | `ordered` true, `gap` 5, `hold` 12 | Trois interrupteurs de lits aux trois coins, dans le bon ordre | Ouvre la sortie (rôle `exit`) | `pharmacie` |

### Indices donnés

- `dossier` : `code` sur `mur_dossier` (dès l'ouverture).
- `cris` : aucun (écran seul).
- `pc_soins` : aucun ; la règle se déduit des Pokémon déjà rangés.
- `pharmacie` : aucun : la logique suffit.

### Étape de coopération

`bips` : trois interrupteurs aux trois coins, trois joueurs, ordre tiré par la graine : un mauvais ordre fait tout
lâcher, il faut se parler pour essayer sans se couper.

## Les trois aides de chaque étape

| Étape | Aide 1 (vague) | Aide 2 | Aide 3 (directe) |
|-------|----------------|--------|------------------|
| `dossier` | Un dossier manque sur le bureau. | Regardez derrière les rideaux des lits. | Le code de 4 chiffres est sur le dossier du dernier lit du fond. |
| `cris` | Écoutez, puis regardez les formes. | Les silhouettes sont de Pokémon connus. | Associez cri et silhouette, puis validez : l'écran donne le nombre de bonnes paires. |
| `perfusion` | La perfusion est coupée. | Tournez un tuyau à la fois du sac au lit. | Reliez le sac (en haut) au bras du lit (en bas). |
| `pc_soins` | Les Pokémon déjà rangés montrent la règle. | Comparez leurs numéros, types, poids ou tailles. | La règle se lit sur les trois Pokémon bloqués ; rangez les autres de même. |
| `pharmacie` | Chaque essai répond « bien placé » et « mal placé ». | Notez les essais ; ils restent à l'écran. | Cherchez d'abord les symboles présents, puis leur place. |
| `bips` | Trois interrupteurs, trois coins. | Un mauvais ordre fait tout lâcher. | Essayez : 1-2-3, puis les autres ordres, un joueur par coin. |

## Emplacements prévus dans le décor

- Lits et rideaux en deux rangées : dossier caché derrière le rideau du fond (zone nord-ouest).
- Haut-parleur et pupitre des cris : mur sud ; PC de soins : angle sud-est.
- Panneau de perfusion : mur ouest ; armoire à pharmacie : mur nord-est.
- Trois interrupteurs de lit aux trois coins sauf le sud-est.

## Secret de la salle

Cachette du temps libre : un tiroir sous le moniteur cardiaque. Emplacement `cachette`.

## Fragment de lore

> Placeholder : texte à écrire dans `Documentation/salles/lore.md` (T9.4). Emplacement prévu `F-infirmerie`, porté par
> une feuille de soin (décor construit). Prémisse générique : fantômes ; le premier appelle les autres à quitter le monde des vivants.

## Vérification de composition

- 6 étapes, 6 mécanismes différents. Écran : oui. Recherche : `dossier` (le code est caché dans le décor). Coop : `bips`. Cobblemon : 2 (le maximum).

## Liste de construction (T9.2)

| Id | Genre | Zone | Taille | Sert à |
|----|-------|------|--------|--------|
| `sortie` | `exit` | mur nord, centre | 3 × 3 × 1 | rôle `exit` de `bips` |
| `pad_armoire` | `keypad` | mur nord-est | 1 × 1 × 1 | rôle `keypad` de `dossier` |
| `mur_dossier` | `wall_clue` | derrière le rideau, nord-ouest | 2 × 2 × 1 | `code` |
| `pupitre_cris` | `terminal` | mur sud | 1 × 1 × 1 | rôle `console` de `cris` |
| `pad_perfusion` | `terminal` | mur ouest | 1 × 1 × 1 | rôle de `perfusion` (à confirmer au codage) |
| `pad_pc` | `terminal` | angle sud-est | 1 × 1 × 1 | rôle `terminal` de `pc_soins` |
| `pad_pharmacie` | `terminal` | mur nord-est, près de l'armoire | 1 × 1 × 1 | rôle `terminal` de `pharmacie` |
| `bip_1` à `bip_3` | `lever` | trois coins | 1 × 1 × 1 | `lever_1..3` de `bips` |
| `cachette` | `secret` | sous le moniteur | 1 × 1 × 1 | cachette |

- **Ids des étapes** : `dossier`, `cris`, `perfusion`, `pc_soins`, `pharmacie`, `bips`.
- **À laisser vide** : air devant chaque emplacement ; rien dans les zones d'écran.
- **Décor construit sans emplacement** : lits, rideaux, moniteur, armoire, feuille de lore.
- **Contrôles** : `code` donné ; pas de `pickup` dans cette salle ; une sortie ; durée estimée proche de 900 s.
