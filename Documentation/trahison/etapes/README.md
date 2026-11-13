# Les étapes de réalisation

> Écrit le 2026-11-13. Chaque étape a sa fiche. Une fiche se découpe en **sous-tâches** : chacune est faite pour
> être confiée à un agent, seule, avec la fiche et l'[architecture](../architecture.md) comme seul contexte.

## Ordre et dépendances

```
T0 socle ─► T1 moteur ─► T2 plan, portes, cabines ─► T3 salles ─► T4 tirage et collage ─► T9 contenu ─► T10 répétition
                              │                          │
                              ├─► T5 vocal               └─► T8 Cobblemon
                              └─► T6 mort, fantômes ─► T7 rôles, objets, maître du jeu
```

| Étape | Fiche | Dépend de | Peut se faire en même temps que | État |
|---|---|---|---|---|
| T0 | [Le socle dans Events](T0-socle.md) | — | — | À faire |
| T1 | [Le moteur de partie](T1-moteur.md) | T0 | — | À faire |
| T2 | [Le plan, les portes, les cabines](T2-plan-portes.md) | T1 | — | À faire |
| T3 | [Le cadre des salles](T3-salles.md) | T2 | T5, T6 | À faire |
| T4 | [Le tirage et le collage](T4-tirage-collage.md) | T3 | T5, T6, T7 | À faire |
| T5 | [Le vocal par zones](T5-vocal.md) | T2 | T3, T4, T6 | À faire |
| T6 | [La mort, les bracelets, les fantômes](T6-mort-fantomes.md) | T2 | T3, T4, T5 | À faire |
| T7 | [Les rôles, les objets, le maître du jeu](T7-roles-objets.md) | T6, T3 (sous-tâche 3) | T4, T8 | À faire |
| T8 | [Cobblemon](T8-cobblemon.md) | T3 | T4, T7 | À faire |
| T9 | [Le contenu de la première soirée](T9-contenu.md) | T3 (sous-tâche 5), puis tout | En continu | À faire |
| T10 | [La répétition générale](T10-repetition.md) | Tout | — | À faire |

Deux étapes ne se font en même temps que si elles ne touchent pas les mêmes fichiers. Les sous-tâches d'une même
étape se font dans l'ordre, sauf mention contraire.

## Comment confier une sous-tâche à un agent

1. Donner à l'agent, dans cet ordre : [`Documentation/trahison/README.md`](../README.md),
   [`architecture.md`](../architecture.md), la fiche de l'étape, et le nom de la sous-tâche.
2. Lui dire de lire d'abord la rubrique « À lire avant » de la fiche.
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
| Pénalité d'un joueur resté devant les portes | T2 |
| Habillage Feu / Plante / Eau des couleurs | T1 (noms), T2 (affichage) |
| La mort est-elle annoncée ou découverte | T6 |
| Durées des liens et du verrou | T7 |
| Noms du jeu tirés du lore (vote, porte finale, bracelets) | T9 |
| Le mod peut-il filtrer la voix de proximité côté client | T5 (première sous-tâche) |
