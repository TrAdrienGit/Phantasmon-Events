# Salle `salle_des_machines` : La Salle des machines

> Brouillon à valider. Proposition de conception pour la propriétaire (T9.1) : rien ici n'est arrêté, tout se corrige.

| | |
|---|---|
| Manche | 2 (moyenne) |
| Difficulté visée | 3 sur 5 |
| Durée visée | 25 minutes pour un groupe moyen (somme des estimations du catalogue : environ 865 s ; à mesurer) |
| Étapes | 5 |
| Étapes Cobblemon | 1 (`type_chain`) |
| Mécanismes à coder | `twin_screens` (à coder) |

## Thème et ambiance

Les entrailles du complexe : chaudière, tuyaux fumants, engrenages géants derrière une grille, ampoules nues. Bruit
sourd, vapeur. Quatre fusibles manquent au tableau électrique ; sans eux, rien ne tourne. Le décor se lit à l'œil :
tuyaux colorés, manettes marquées de types, deux pupitres de contrôle aux deux bouts de la halle.

## Chaîne d'étapes

| # | Id | Mécanisme | Paramètres | Ce qu'elle demande | Ce qu'elle donne | Prérequis |
|---|----|-----------|------------|--------------------|------------------|-----------|
| 1 | `fusibles` | `pickup_set` (recherche dans le décor) | `spots` 5, `filled` 4 | Trouver 4 fusibles cachés dans la halle et les ramasser | Rien : ouvre les étapes 2 et 4 | aucun |
| 2 | `tableau` | `wiring` | `wires` 5, `colors` 4 | Câbler le tableau électrique d'après le schéma | Rien : ouvre l'étape 3 | `fusibles` |
| 3 | `engrenages` | `ring_dial` | `rings` 3, `symbols` 6, `links` 1 | Tourner les anneaux d'engrenage jusqu'à la séquence du mur (un lien caché entre deux anneaux) | Rien : ouvre l'étape 5 | `tableau` |
| 4 | `vannes` | `type_chain` (Cobblemon) | `count` 4 | Tirer quatre manettes marquées d'un type, du plus fort contre le suivant | Rien : ouvre l'étape 5 | `fusibles` |
| 5 | `pression` | `twin_screens` (coop, à coder) | par défaut | Deux pupitres éloignés : l'un voit la pression, l'autre commande les soupapes | Ouvre la sortie (rôle `exit`) | `engrenages`, `vannes` |

### Indices donnés

- `fusibles` : `spot_1..5` sur `fusible_1..5` (dès l'ouverture).
- `tableau` : `scheme` sur `mur_schema` ; `scheme_text` sur `bureau_notice` (dès l'ouverture).
- `engrenages` : `symbols` sur `mur_engrenages` (dès l'ouverture).
- `vannes` : `mark_1..4` sur `marque_1..4` ; `type_sheet` sur `bureau_types` (dès l'ouverture).
- `pression` : à fixer au codage de `twin_screens`.

### Étape de coopération

`pression` : deux pupitres aux deux bouts de la halle. Chacun voit la moitié de l'information ; la voix sert. Le
troisième joueur fait la navette ou tient la notice. Voir D223.

## Les trois aides de chaque étape

| Étape | Aide 1 (vague) | Aide 2 | Aide 3 (directe) |
|-------|----------------|--------|------------------|
| `fusibles` | Le tableau manque de pièces. | Cherchez derrière les tuyaux et sur les passerelles. | 5 endroits, 4 fusibles : balayez la halle en trois. |
| `tableau` | Chaque fil a sa couleur. | Le schéma est sur le mur et la notice du bureau. | La n-ième couleur est celle de la borne de gauche n. |
| `engrenages` | Les anneaux se gênent entre eux. | Tournez un anneau et regardez les autres. | Un anneau en entraîne un autre : tenez-en compte avant de tourner. |
| `vannes` | Chaque manette porte un type. | Le plus fort contre le suivant ; la fiche aide. | Tirez dans l'ordre : chaque type bat le suivant ; la fiche le dit. |
| `pression` | Deux pupitres, deux moitiés. | L'un lit, l'autre commande. | Dites-vous les valeurs à voix haute. |

## Emplacements prévus dans le décor

- Passerelles et tuyaux : 5 spots de fusibles, un peu partout (en hauteur et au sol).
- Tableau électrique : mur ouest ; notice et fiche de types : bureau sud-ouest.
- Engrenages géants derrière une grille : mur nord ; leur pupitre en face.
- Quatre manettes sur le mur est, marquées chacune d'un type.
- Deux pupitres de pression : l'un au nord-est, l'autre au sud-ouest.

## Secret de la salle

Cachette du temps libre : un compartiment de maintenance sous la chaudière, au centre.

Candidats à la cachette (D228, genre `secret`, la graine en tire **un seul** par partie) : `cachette_1`, `cachette_2`. Ils sont déjà dans le gabarit (`room new <id> salle_des_machines`) ; la fiche en propose 2 ; 2 ou 3 candidats au total conviennent.

## Fragment de lore

> Placeholder : texte à écrire dans `Documentation/salles/lore.md` (T9.4). Emplacement prévu `F-machines`, porté par
> un registre de maintenance (décor construit). Prémisse générique : fantômes ; le premier appelle les autres à quitter le monde des vivants.

## Vérification de composition

- 5 étapes, 5 mécanismes différents. Mini-jeu à l'écran : oui. Recherche : `fusibles`. Coop : `pression`. Cobblemon : 1.

## Liste de construction (T9.2)

| Id | Genre | Zone | Taille | Sert à |
|----|-------|------|--------|--------|
| `sortie` | `exit` | mur nord, centre | 3 × 3 × 1 | rôle `exit` de `pression` |
| `fusible_1` à `fusible_5` | `pickup` | passerelles, tuyaux, sols | 1 × 1 × 1 | `spot_1..5` de `fusibles` |
| `pad_tableau` | `terminal` | mur ouest | 1 × 1 × 1 | rôle `panel` de `tableau` |
| `mur_schema` | `wall_clue` | mur ouest, au-dessus | 2 × 2 × 1 | `scheme` |
| `bureau_notice` | `document` | bureau sud-ouest | 1 × 1 × 1 | `scheme_text` |
| `pad_engrenages` | `terminal` | mur nord | 1 × 1 × 1 | rôle `terminal` de `engrenages` |
| `mur_engrenages` | `wall_clue` | cadre sur la grille, est | 2 × 2 × 1 | `symbols` |
| `manette_1` à `manette_4` | `lever` | mur est, alignés | 1 × 1 × 1 | `lever_1..4` de `vannes` |
| `marque_1` à `marque_4` | `wall_clue` | au-dessus de chaque manette | 1 × 1 × 1 | `mark_1..4` |
| `bureau_types` | `document` | bureau sud-ouest | 1 × 1 × 1 | `type_sheet` |
| `poste_a`, `poste_b` | `terminal` | nord-est et sud-ouest | 1 × 1 × 1 | rôles de `pression` (à confirmer) |
| `cachette_1` | `secret` | Compartiment de maintenance sous la chaudière, au centre | 1 × 1 × 1 | candidat de cachette (D228) |
| `cachette_2` | `secret` | Derrière un tuyau de la passerelle ouest | 1 × 1 × 1 | candidat de cachette (D228) |

- **Ids des étapes** : `fusibles`, `tableau`, `engrenages`, `vannes`, `pression`.
- **À laisser vide** : air devant chaque emplacement ; les cases `pickup` ; les emplacements `marque_*` (le jeu y affiche le type).
- **Décor construit sans emplacement** : chaudière, tuyaux, engrenages (en bloc), vapeur, registre du lore.
- **Contrôles** : `mark_1..4` et `type_sheet` donnés ; les deux postes distants d'au moins 20 blocs ; une sortie ; durée estimée proche de 865 s.
