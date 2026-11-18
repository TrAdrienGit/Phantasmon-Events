# Salle `bal` : La Salle de bal

> Brouillon à valider. Proposition de conception pour la propriétaire (T9.1) : rien ici n'est arrêté, tout se corrige.

| | |
|---|---|
| Manche | 2 (moyenne) |
| Difficulté visée | 3 sur 5 |
| Durée visée | 25 minutes pour un groupe moyen (somme des estimations du catalogue : environ 830 s ; à mesurer) |
| Étapes | 6 |
| Étapes Cobblemon | 2 (`power_spot`, `dex_code`) |
| Mécanismes à coder | `safe_dial` (à coder) |

## Thème et ambiance

Un ancien salon de réception : parquet ciré, lustres éteints, miroirs voilés, portraits d'invités sur les murs, une
boîte à musique sur un piédestal. La moitié de la salle est plongée dans le noir. Élégance fanée, musique lointaine :
la salle la plus « théâtrale » de la soirée.

## Chaîne d'étapes

| # | Id | Mécanisme | Paramètres | Ce qu'elle demande | Ce qu'elle donne | Prérequis |
|---|----|-----------|------------|--------------------|------------------|-----------|
| 1 | `lumiere` | `power_spot` (Cobblemon, recherche dans le décor) | `power` FLASH, `radius` 4 | Éclairer l'aile noire de la salle avec le Pokémon qui a Flash ; on y trouve l'indice | Rien : la zone devient éclairée et ouvre l'étape 2 | aucun |
| 2 | `portraits` | `dex_code` (Cobblemon) | `count` 2 | Lire deux portraits de la galerie, retrouver leurs numéros, taper 6 chiffres | Rien : ouvre l'étape 4 | `lumiere` |
| 3 | `vestiaire` | `keypad` | `length` 4 | Taper le code du vestiaire, inscrit sur un carnet de bal | Rien : ouvre l'étape 5 | aucun |
| 4 | `musique` | `safe_dial` (à coder) | par défaut | Régler la molette de la boîte à musique au son, un joueur écoute pendant qu'un autre tourne | Rien : ouvre l'étape 6 | `portraits` |
| 5 | `miroirs` | `ring_dial` | `rings` 3, `symbols` 6, `links` 1 | Tourner les anneaux du cadran de la pendule jusqu'à la séquence gravée dans la glace | Rien : ouvre l'étape 6 | `vestiaire` |
| 6 | `lustres` | `three_levers` (coop) | `ordered` true, `gap` 5, `hold` 12 | Trois chaînes de lustres aux trois coins, dans le bon ordre | Ouvre la sortie (rôle `exit`) | `musique`, `miroirs` |

### Indices donnés

- `portraits` : `portrait_1`, `portrait_2` sur `cadre_1`, `cadre_2` (dans la zone sombre) ; `dex_page` sur `table_dex`.
- `vestiaire` : `code` sur `carnet_bal` (dès l'ouverture).
- `miroirs` : `symbols` sur `mur_miroir` (dès l'ouverture).
- `musique` : pas d'indice (au son) ; détail à fixer au codage de `safe_dial`.

### Étape de coopération

`lustres` : trois chaînes aux trois coins, ordre tiré par la graine. Et `musique` : l'un écoute au piédestal, l'autre
tourne : à confirmer au codage de `safe_dial` (voir D222 et D223).

## Les trois aides de chaque étape

| Étape | Aide 1 (vague) | Aide 2 | Aide 3 (directe) |
|-------|----------------|--------|------------------|
| `lumiere` | Un côté de la salle reste noir. | Le Pokémon qui éclaire doit marcher dans le noir. | Le porteur de Flash éclaire la zone, les autres cherchent dedans. |
| `portraits` | Chaque portrait est un Pokémon connu. | Le numéro est dans le Pokédex ; la page de la table aide. | Tapez 3 chiffres par portrait, dans l'ordre de la galerie. |
| `vestiaire` | Le code est écrit quelque part dans la salle. | Cherchez un carnet de bal. | Le code de 4 chiffres est sur le carnet posé sur le piano. |
| `musique` | Écoutez le tic. | Le son change près du bon réglage. | Tournez lentement : le tic s'accélère près de la bonne valeur. |
| `miroirs` | Les anneaux de la pendule se gênent. | Tournez un anneau et regardez les autres. | Un anneau en entraîne un autre : repérez le lien. |
| `lustres` | Trois chaînes, trois coins. | Un mauvais ordre fait tout lâcher. | Un joueur par coin ; essayez les ordres l'un après l'autre. |

## Emplacements prévus dans le décor

- Zone sombre (aile ouest, environ 5 × 3 × 5 blocs) : galerie de portraits.
- Piano et carnet : centre ; table du Pokédex : près de l'entrée.
- Boîte à musique : piédestal au nord ; pendule : mur est.
- Trois chaînes de lustre aux coins sauf un.

## Secret de la salle

Cachette du temps libre : derrière un miroir voilé, mur est. Emplacement `cachette`.

## Fragment de lore

> Placeholder : texte à écrire dans `Documentation/salles/lore.md` (T9.4). Emplacement prévu `F-bal`, porté par un faire-part
> (décor construit). Prémisse générique : fantômes ; le premier appelle les autres à quitter le monde des vivants.

## Vérification de composition

- 6 étapes, 6 mécanismes différents. Écran : oui. Recherche : `lumiere` (zone sombre) et le carnet. Coop : `lustres`. Cobblemon : 2 (le maximum).

## Liste de construction (T9.2)

| Id | Genre | Zone | Taille | Sert à |
|----|-------|------|--------|--------|
| `sortie` | `exit` | mur nord, centre | 3 × 3 × 1 | rôle `exit` de `lustres` |
| `zone_sombre` | `power_spot` | aile ouest | 5 × 3 × 5 | rôle `spot` de `lumiere` |
| `pad_dex` | `keypad` | mur ouest, hors zone sombre | 1 × 1 × 1 | rôle `terminal` de `portraits` |
| `cadre_1`, `cadre_2` | `wall_clue` | dans la zone sombre | 2 × 2 × 1 | `portrait_1`, `portrait_2` |
| `table_dex` | `document` | table près de l'entrée | 1 × 1 × 1 | `dex_page` |
| `pad_vestiaire` | `keypad` | mur sud | 1 × 1 × 1 | rôle `keypad` de `vestiaire` |
| `carnet_bal` | `document` | piano, centre | 1 × 1 × 1 | `code` |
| `boite_musique` | `terminal` | piédestal nord | 1 × 1 × 1 | rôle de `musique` (à confirmer au codage) |
| `pad_pendule` | `terminal` | mur est | 1 × 1 × 1 | rôle `terminal` de `miroirs` |
| `mur_miroir` | `wall_clue` | grand miroir, est | 2 × 2 × 1 | `symbols` |
| `chaine_1` à `chaine_3` | `lever` | trois coins | 1 × 1 × 1 | `lever_1..3` de `lustres` |
| `cachette` | `secret` | derrière un miroir voilé | 1 × 1 × 1 | cachette |

- **Ids des étapes** : `lumiere`, `portraits`, `vestiaire`, `musique`, `miroirs`, `lustres`.
- **À laisser vide** : **toute la boîte de `zone_sombre`** (rien de construit dedans : le jeu y pose la pénombre) ; air devant chaque emplacement.
- **Décor construit sans emplacement** : lustres, miroirs voilés, piano, parquet, faire-part du lore.
- **Contrôles** : `portrait_1..2` donnés dans la zone sombre ; un seul `power_spot` ; une sortie ; durée estimée proche de 830 s.
