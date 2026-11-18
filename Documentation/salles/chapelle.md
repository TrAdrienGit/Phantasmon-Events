# Salle `chapelle` : La Chapelle

> Brouillon à valider. Proposition de conception pour la propriétaire (T9.1) : rien ici n'est arrêté, tout se corrige.

| | |
|---|---|
| Manche | 3 (difficile) |
| Difficulté visée | 4 sur 5 |
| Durée visée | 25 minutes pour un groupe moyen (somme des estimations du catalogue : environ 970 s ; à mesurer) |
| Étapes | 6 |
| Étapes Cobblemon | 2 (`unown_text`, `weigh_in`) |
| Mécanismes à coder | `sliding_blocks`, `safe_dial` (à coder) |

## Thème et ambiance

Une petite chapelle : bancs de bois, vitraux ternes, orgue, trois cloches qui pendent aux coins, des statues de saints de
tailles inégales. Les inscriptions sur les murs sont en alphabet étrange. Grave, silencieux, un peu solennel : la salle la plus
« rituelle ». La salle se lit en cercle : statues, cloches, orgue.

## Chaîne d'étapes

| # | Id | Mécanisme | Paramètres | Ce qu'elle demande | Ce qu'elle donne | Prérequis |
|---|----|-----------|------------|--------------------|------------------|-----------|
| 1 | `inscriptions` | `unown_text` (Cobblemon, recherche dans le décor) | `length` 4 | Déchiffrer les inscriptions du mur avec l'alphabet caché dans la sacristie, taper 4 chiffres | Rien : ouvre l'étape 3 | aucun |
| 2 | `saints` | `weigh_in` (Cobblemon) | `count` 5, `by` height | Ranger cinq statues de Pokémon du plus petit au plus grand | Rien : ouvre l'étape 4 | aucun |
| 3 | `bancs` | `sliding_blocks` (à coder) | par défaut | Faire glisser les bancs pour dégager le passage de l'autel | Rien : ouvre l'étape 5 | `inscriptions` |
| 4 | `reliquaire` | `safe_dial` (à coder) | par défaut | Ouvrir le reliquaire à molette, au son des cloches | Rien : ouvre l'étape 5 | `saints` |
| 5 | `orgue` | `code_breaker` | `length` 5, `alphabet` 8, `kind` symbols, `maxTries` 14 | Trouver la combinaison des tuyaux d'orgue par déduction | Rien : ouvre l'étape 6 | `bancs`, `reliquaire` |
| 6 | `cloches` | `three_levers` (coop) | `ordered` true, `gap` 4, `hold` 10 | Trois cloches aux trois coins, tirées dans le bon ordre, avec peu de marge | Ouvre la sortie (rôle `exit`) | `orgue` |

### Indices donnés

- `inscriptions` : `wall_text` sur `mur_inscriptions` ; `alphabet` sur `sacristie` (dès l'ouverture) : la sacristie est cachée derrière l'autel, c'est la recherche dans le décor.
- `saints` : `pedestal_1..5` sur `niche_1..5` ; `scale_sheet` sur `pupitre_mesures` (dès l'ouverture).
- `reliquaire` : au son ; à fixer au codage.
- `orgue` : aucun : la logique suffit.

### Étape de coopération

`cloches` : trois cloches aux trois coins, `gap` serré (4 s), ordre tiré par la graine : ils doivent se coordonner et
s'annoncer. Voir D223.

## Les trois aides de chaque étape

| Étape | Aide 1 (vague) | Aide 2 | Aide 3 (directe) |
|-------|----------------|--------|------------------|
| `inscriptions` | Les murs parlent un autre alphabet. | La clé de l'alphabet est cachée derrière l'autel. | Chaque signe est une lettre : la phrase épelle 4 chiffres en toutes lettres. |
| `saints` | Les statues sont de tailles inégales. | Le plus petit à gauche. | La fiche du pupitre donne les tailles. |
| `bancs` | Un banc en bloque un autre. | Dégagez d'abord les bancs sur le rail. | Faites sortir le banc marqué en le glissant pas à pas. |
| `reliquaire` | Écoutez les cloches. | Le son change près du bon réglage. | Tournez lentement : la cloche sonne plus fort près de la bonne valeur. |
| `orgue` | « Bien placé » et « mal placé » disent tout. | 14 essais : n'en gaspillez pas. | Cherchez d'abord les symboles présents. |
| `cloches` | Trois cloches, trois coins. | Un mauvais ordre ou un retard fait tout lâcher. | Un joueur par coin ; annoncez chaque tirage à voix haute. |

## Emplacements prévus dans le décor

- Mur des inscriptions : mur ouest ; sacristie cachée derrière l'autel au nord.
- Cinq niches de saints : mur est ; pupitre de mesures : près des niches.
- Bancs au centre (rails invisibles) ; reliquaire : devant l'autel ; orgue : mur sud.
- Trois cloches aux coins.

## Secret de la salle

Cachette du temps libre : à l'intérieur du pupitre de l'orgue, sous le clavier.

Candidats à la cachette (D228, genre `secret`, la graine en tire **un seul** par partie) : `cachette_1`, `cachette_2`. Ils sont déjà dans le gabarit (`room new <id> chapelle`) ; la fiche en propose 2 ; 2 ou 3 candidats au total conviennent.

## Fragment de lore

> Placeholder : texte à écrire dans `Documentation/salles/lore.md` (T9.4). Emplacement prévu `F-chapelle`, porté par
> un missel posé sur l'autel (décor construit). Prémisse générique : fantômes ; le premier appelle les autres à quitter le monde des vivants.

## Vérification de composition

- 6 étapes, 6 mécanismes différents. Écran : oui. Recherche : `inscriptions` (alphabet caché). Coop : `cloches`. Cobblemon : 2 (le maximum).

## Liste de construction (T9.2)

| Id | Genre | Zone | Taille | Sert à |
|----|-------|------|--------|--------|
| `sortie` | `exit` | mur nord, centre | 3 × 3 × 1 | rôle `exit` de `cloches` |
| `pad_inscriptions` | `keypad` | mur ouest | 1 × 1 × 1 | rôle `terminal` de `inscriptions` |
| `mur_inscriptions` | `wall_clue` | mur ouest, haut | 3 × 2 × 1 | `wall_text` |
| `sacristie` | `document` | derrière l'autel | 1 × 1 × 1 | `alphabet` |
| `pad_saints` | `terminal` | mur est | 1 × 1 × 1 | rôle `row` de `saints` |
| `niche_1` à `niche_5` | `pedestal` | mur est, alignés | 1 × 1 × 1 | `pedestal_1..5` |
| `pupitre_mesures` | `document` | près des niches | 1 × 1 × 1 | `scale_sheet` |
| `pad_bancs` | `terminal` | devant l'autel | 1 × 1 × 1 | rôle de `bancs` (à confirmer au codage) |
| `reliquaire` | `terminal` | devant l'autel | 1 × 1 × 1 | rôle de `reliquaire` (à confirmer) |
| `pad_orgue` | `terminal` | mur sud | 1 × 1 × 1 | rôle `terminal` de `orgue` |
| `cloche_1` à `cloche_3` | `lever` | trois coins | 1 × 1 × 1 | `lever_1..3` de `cloches` |
| `cachette_1` | `secret` | Dans le pupitre de l'orgue, sous le clavier | 1 × 1 × 1 | candidat de cachette (D228) |
| `cachette_2` | `secret` | Dans le tabernacle de l'autel | 1 × 1 × 1 | candidat de cachette (D228) |

- **Ids des étapes** : `inscriptions`, `saints`, `bancs`, `reliquaire`, `orgue`, `cloches`.
- **À laisser vide** : air devant chaque emplacement ; les niches (statues dessinées par le jeu) ; le rail des bancs (à préciser au codage de `sliding_blocks`).
- **Décor construit sans emplacement** : bancs, vitraux, autel, orgue, missel du lore.
- **Contrôles** : `alphabet`, `scale_sheet` donnés ; trois cloches aux trois coins ; une sortie ; durée estimée proche de 970 s.
