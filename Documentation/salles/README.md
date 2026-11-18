# Les 9 salles de la première soirée (brouillons)

> Brouillon à valider. Les 9 fiches ont été écrites le 2026-11-18 comme **propositions** pour la propriétaire (T9.1 :
> la conception se fait avec elle). Rien n'est arrêté ; les choix de fond sont dans `trahison/decisions.md` (D220 à D229).
> Le lore est un renvoi vers un futur `lore.md` (T9.4, pas encore écrit : le lore de la communauté est à la propriétaire).

## Les 9 salles

Difficulté : visée sur 5. Durée : somme des estimations du catalogue de mécanismes (secondes) ; la durée visée est de
25 minutes pour tous, le reste est trajets, discussion et erreurs (à mesurer à trois, T9 « fini quand »).
`♣` = mécanisme à coder (T9.3). `*` = étape de coopération. `§` = étape Cobblemon.

| Manche | Id (fiche) | Thème | Mécanismes (ordre de la chaîne) | Difficulté | Estimation | Cobblemon |
|--------|------------|-------|---------------------------------|-----------|------------|-----------|
| 1 | [`archives`](archives.md) | Les Archives | `pickup_set`, `keypad`, `wiring`, `three_dials`, `sliding_blocks`♣, `three_levers`* | 2 | 743 s | 0 |
| 1 | [`serre`](serre.md) | La Serre | `pickup_set`, `dex_code`§, `weigh_in`§, `keypad`, `three_dials`, `twin_screens`♣* | 2 | 733 s | 2 |
| 1 | [`dortoir`](dortoir.md) | Le Dortoir | `pickup_set`, `unown_text`§, `rotating_tiles`♣, `keypad`, `three_levers`* | 2 | 693 s | 1 |
| 2 | [`salle_des_machines`](salle_des_machines.md) | La Salle des machines | `pickup_set`, `wiring`, `ring_dial`, `type_chain`§, `twin_screens`♣* | 3 | 865 s | 1 |
| 2 | [`infirmerie`](infirmerie.md) | L'Infirmerie | `keypad`, `cry_match`§, `rotating_tiles`♣, `pc_box`§, `code_breaker`, `three_levers`* | 3 | 898 s | 2 |
| 2 | [`bal`](bal.md) | La Salle de bal | `power_spot`§, `dex_code`§, `keypad`, `safe_dial`♣, `ring_dial`, `three_levers`* | 3 | 828 s | 2 |
| 3 | [`observatoire`](observatoire.md) | L'Observatoire | `keypad`, `cry_match`§, `ring_dial`, `rotating_tiles`♣, `code_breaker`, `twin_screens`♣* | 4 | 1 078 s | 1 |
| 3 | [`chapelle`](chapelle.md) | La Chapelle | `unown_text`§, `weigh_in`§, `sliding_blocks`♣, `safe_dial`♣, `code_breaker`, `three_levers`* | 4 | 970 s | 2 |
| 3 | [`crypte`](crypte.md) | La Crypte | `pickup_set`, `wiring`, `three_dials`, `pc_box`§, `battle_puzzle`§, `twin_screens`♣* | 4 | 945 s | 2 |

- **Égalité dans une manche** : écart d'estimation de 8 % au plus (manche 1 : 693 à 743 s ; manche 2 : 828 à 898 s ;
  manche 3 : 945 à 1 078 s). La montée d'une manche à l'autre vient à la fois du nombre d'étapes, des paramètres plus durs
  (longueurs, liens cachés, nombre d'essais limité, délais serrés) et de la part de Cobblemon.
- 52 étapes en tout : 6 par salle, sauf `dortoir` et `salle_des_machines` qui en ont 5 ; aucune salle n'a deux fois un même mécanisme.
- Chaque salle a au moins un mini-jeu à l'écran, une recherche dans le décor (`pickup_set`, zone sombre de Flash, code
  caché, alphabet caché), une étape de coopération, au plus deux étapes Cobblemon.
- Les 9 salles dépendent chacune d'au moins un mécanisme à coder (voir D222) ; la manche 3 en dépend le plus.

## Couverture des mécanismes

| Mécanisme | Salles | Nombre |
|-----------|--------|--------|
| `pickup_set` | archives, serre, dortoir, salle_des_machines, crypte | 5 |
| `keypad` | archives, serre, dortoir, infirmerie, bal, observatoire | 6 |
| `wiring` | archives, salle_des_machines, crypte | 3 |
| `three_dials` | archives, serre, crypte | 3 |
| `three_levers` | archives, dortoir, infirmerie, bal, chapelle | 5 |
| `ring_dial` | salle_des_machines, bal, observatoire | 3 |
| `code_breaker` | infirmerie, observatoire, chapelle | 3 |
| `dex_code` | serre, bal | 2 |
| `weigh_in` | serre, chapelle | 2 |
| `unown_text` | dortoir, chapelle | 2 |
| `type_chain` | salle_des_machines | 1 |
| `cry_match` | infirmerie, observatoire | 2 |
| `pc_box` | infirmerie, crypte | 2 |
| `battle_puzzle` | crypte | 1 |
| `power_spot` | bal | 1 |
| `sliding_blocks` ♣ | archives, chapelle | 2 |
| `rotating_tiles` ♣ | dortoir, infirmerie, observatoire | 3 |
| `safe_dial` ♣ | bal, chapelle | 2 |
| `twin_screens` ♣ | serre, salle_des_machines, observatoire, crypte | 4 |

`debug_button` n'est jamais utilisé (c'est un outil d'essai). Les 15 mécanismes déjà codés servent tous ; les 4
mécanismes à coder de T9.3 (priorités 1 à 4) servent tous au moins deux fois. `radio_console`, `torn_document`,
`old_terminal`, `deduction_grid`, `invisible_maze` et `memory_trio` ne sont pas utilisés dans cette première soirée.

## Répartition par manche

| | Manche 1 | Manche 2 | Manche 3 |
|--|---------|----------|----------|
| Difficulté visée | 2 | 3 | 4 |
| Étapes Cobblemon | 3 sur 17 | 5 sur 17 | 5 sur 18 |
| Étapes de coopération | 3 | 3 | 3 |
| Mécanismes à coder | 3 étapes | 3 étapes | 5 étapes |

## Ce qui reste à faire pour chaque fiche

1. La propriétaire valide ou corrige la fiche (retirer « Brouillon à valider »).
2. Construction (T9.2) d'après la « Liste de construction » en bas de chaque fiche ; les rôles d'emplacement des
   mécanismes à coder sont marqués « à confirmer au codage ».
3. Le lore (T9.4) dans `lore.md`, puis chaque fiche reçoit son vrai fragment à la place du placeholder.
4. Un essai à trois par salle, avec les temps mesurés.
