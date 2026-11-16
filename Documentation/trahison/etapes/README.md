# Les étapes de réalisation

> Écrit le 2026-11-13. Chaque étape a sa fiche. Une fiche se découpe en **sous-tâches** : chacune est faite pour
> être confiée à un agent, seule, avec la fiche et l'[architecture](../architecture.md) comme seul contexte.

## Ordre et dépendances

```
T0 socle ─► T1 moteur ─► T2 plan, portes, cabines ─► T3 salles ─► T4 tirage et collage ─► T11 carte ─► T9 contenu ─► T10 répétition
                              │                          │
                              ├─► T5 vocal               └─► T8 Cobblemon
                              └─► T6 mort, fantômes ─► T7 rôles, objets, maître du jeu
```

| Étape | Fiche | Dépend de | Peut se faire en même temps que | État |
|---|---|---|---|---|
| T0 | [Le socle dans Events](T0-socle.md) | — | — | À faire |
| T1 | [Le moteur de partie](T1-moteur.md) | T0 | — | Backend fait (partie, vues, factices, tests) ; client à faire |
| T2 | [Le plan, les portes, les cabines](T2-plan-portes.md) | T1 | — | En cours : T2.1 écrite, à valider en jeu ; T2.2 faite (faux blocs `MirrorGhosts.putLocal`, états des portes par le backend, zones), à valider en jeu ; T2.3 faite le 2026-11-16 (portes de couleur ouvertes par le seuil, `door_hints`, panneau flottant `world/DoorPanels`), à valider en jeu ; T2.4 faite le 2026-11-16 (cabines : `close_cabin`, vote seulement depuis sa cabine fermée, pupitre `screen/VoteScreen`, invite `hud/CabinPrompt`, tableau des résultats `world/ResultsBoardWorld` avec repli `hud/ResultsHud`, onglet Tableau de l'éditeur), à valider en jeu ; T2.5 faite le 2026-11-16 (porte finale : pupitre = `threshold` de la porte `final`, action `open_final_door` seulement à 9 points et en `FINAL`, fenêtre `final_door_seconds` pendant laquelle seuls les bracelets à 9 passent, puis `ENDED` avec `winners` = ceux qui sont passés ; écran `screen/FinalDoorScreen`, invite `hud/FinalDoorPrompt`, `world/FinalDoorFlow`), à valider en jeu |
| T3 | [Le cadre des salles](T3-salles.md) | T2 | T5, T6 | En cours : T3.1 faite et branchée sur le moteur de partie (une salle par groupe, actions, diffusion, reprise après redémarrage) ; T3.2 (éditeur de salle) écrite et compilée, pas encore essayée en jeu ; T3.3 faite le 2026-11-16 (objets du monde `world/Interactables` et leurs rendus, inventaire virtuel `room/Inventory` avec barre d'objets et écran, lecture `room/Documents`, donner / utiliser / combiner par le backend, temps libre D8 : visiter et rejouer les salles, **toutes les salles ouvertes jusqu'ici, celles des manches précédentes comprises** depuis le 2026-11-16 : gardées avec la partie, adressées par (manche, groupe)), à valider en jeu ; T3.5 : `ring_dial` (backend, écran, glyphes, tests), `keypad` et `pickup_set` faits le 2026-11-16 (backend, écrans `KeypadScreen` et `PickupSetScreen`, `Mechanism.slotRoles()` exposé par `GET /events/trahison/mechanisms` et lu par l'éditeur, tests de génération sur 1 000 tirages), à valider en jeu ; `code_breaker` (décodeur, `CodeBreakerScreen`) et `wiring` (câblage, `WiringScreen`) faits le 2026-11-16 (backend, écrans, indices, 1 000 générations résolues par le test, décisions D36 à D39), à valider en jeu ; `three_levers` (trois leviers à tenir ensemble, joué dans le monde : bail de `hold` secondes, ordre et délai tirés, un levier par joueur, aperçu hors ligne `ThreeLeversScreen`, rendu `coopLever`, 1 000 générations résolues par trois joueurs simulés, un joueur seul refusé, décisions D40 à D43) fait le 2026-11-16, à valider en jeu : **les six mécanismes de T3.5 sont faits** |
| T4 | [Le tirage et le collage](T4-tirage-collage.md) | T3 | T5, T6, T7 | À faire |
| T5 | [Le vocal par zones](T5-vocal.md) | T2 | T3, T4, T6 | À faire |
| T6 | [La mort, les bracelets, les fantômes](T6-mort-fantomes.md) | T2 | T3, T4, T5 | À faire |
| T7 | [Les rôles, les objets, le maître du jeu](T7-roles-objets.md) | T6, T3 (sous-tâche 3) | T4, T8 | À faire |
| T8 | [Cobblemon](T8-cobblemon.md) | T3 | T4, T7 | À faire |
| T9 | [Le contenu de la première soirée](T9-contenu.md) | T3 (sous-tâche 5), puis tout | En continu | À faire |
| T11 | [La carte du lieu](T11-carte.md) (décision D9) | T2, T4 | T5, T6, T7, T8 | À faire |
| T10 | [La répétition générale](T10-repetition.md) | Tout | — | À faire |

Deux étapes ne se font en même temps que si elles ne touchent pas les mêmes fichiers. Les sous-tâches d'une même
étape se font dans l'ordre, sauf mention contraire.

## Comment confier une sous-tâche à un agent

1. Donner à l'agent, dans cet ordre : [`Documentation/trahison/README.md`](../README.md),
   [`architecture.md`](../architecture.md), la fiche de l'étape, et le nom de la sous-tâche.
2. Lui dire de lire d'abord la rubrique « À lire avant » de la fiche, et les solutions techniques qu'elle cite
   ([`solutions-techniques.md`](../solutions-techniques.md)) : elles s'appliquent, elles ne se recherchent pas de
   nouveau.
3. Il s'arrête quand le « Fini quand » de la sous-tâche est vrai, tests compris, et dit ce qu'il n'a pas pu
   vérifier.
4. Il ne sort pas du périmètre. Il ne modifie jamais Phantasmon ni Showtime : s'il lui manque quelque chose
   chez eux, il s'arrête et le signale.
5. La validation en jeu reste humaine : déploiement, essai, puis commit.

### Quel modèle pour quelle sous-tâche

Chaque sous-tâche porte un conseil :

| Conseil | Quand |
|---|---|
| **Sonnet** | Travail bien cadré : un écran, un mécanisme sur un modèle existant, des tests, un script, de la documentation |
| **Opus** | Conception à l'intérieur d'un cadre : machine à états, protocole, synchronisation, mixins délicats |
| **Fable** | Ce qui engage toute la suite ou demande du goût : arbitrages d'architecture, conception des salles, écrans phares |

Dans le doute, prendre le modèle au-dessus pour la première sous-tâche d'une étape (elle pose les fondations),
puis descendre pour les suivantes, qui suivent le modèle posé.

### Ce que toute sous-tâche doit rendre

- Le code, avec ses tests quand la fiche en demande.
- Les textes du jeu dans `en_us.json` et `fr_fr.json`.
- La documentation touchée, mise à jour : `protocole.md` pour tout message ou action ajouté, cette page pour l'état.
- Un compte rendu court : ce qui est fait, ce qui est vérifié et comment, ce qui reste à voir en jeu.

## Vérifier sans 9 joueurs

Dès T1, des **joueurs factices** (réservés aux admins) complètent une partie : ils passent les portes, « résolvent »
leur salle après un délai et votent selon une consigne. Toute étape doit rester jouable à 2 comptes plus 7 factices.
C'est la base de chaque validation en jeu.

## Questions à trancher en route

Reprises du plan (section 12). À poser au moment où une étape en a besoin, pas avant :

| Question | Étape qui en a besoin |
|---|---|
| ~~Pénalité d'un joueur resté devant les portes~~ (tranché : la mort, D2) | T2 |
| Habillage Feu / Plante / Eau des couleurs | T1 (noms), T2 (affichage) |
| La mort est-elle annoncée ou découverte | T6 |
| Durées des liens et du verrou | T7 |
| Noms du jeu tirés du lore (vote, porte finale, bracelets) | T9 |
| Le gardien (vrai combat à équipe imposée) : le veut-on plus tard, et par quelle piste | Après la première soirée (solution S10) |
