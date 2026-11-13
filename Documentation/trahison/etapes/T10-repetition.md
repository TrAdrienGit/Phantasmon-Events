# T10 : la répétition générale

## Objectif

Jouer une vraie partie à 9 avant la soirée, pour régler ce qui ne se voit qu'avec du monde : les durées, la
difficulté, le vocal, la charge, et la tentation de trahir.

**Fini quand :** une partie complète s'est jouée à 9 personnes sans intervention technique, et la liste des
réglages qui en sort est appliquée.

## À lire avant

- `Documentation/soiree.md` (écrit en T4.4).
- [`plan.md`](../plan.md), section 4 : ce qui doit casser la confiance.

## Sous-tâches

### T10.1 La partie à blanc (Sonnet)

Avant de déranger 9 personnes : une partie entière à 2 comptes et 7 factices, dans le vrai complexe, avec les 9
vraies salles, en suivant `soiree.md` ligne à ligne.

- Vérifier chaque étape, chaque porte, chaque cabine, la porte finale, la boucle de votes.
- Faire résoudre chaque salle par une personne qui ne l'a pas conçue.
- Noter tout écart dans `Documentation/repetition.md`.
- **Fini quand :** aucune erreur bloquante ne reste.

### T10.2 La répétition à 9 (hors code)

Menée par la propriétaire. Pendant la partie, le backend enregistre tout (journal, temps par étape de salle,
aides données). À observer en plus :

- le vocal : s'entend-on bien dans une salle, pas du tout d'une salle à l'autre ?
- les durées : une étape trop longue, un temps mort ?
- la trahison : y en a-t-il eu ? Les tentations ont-elles servi ? Les rôles ont-ils pesé ?
- la charge : le backend et le vocal tiennent-ils à 9, plus les spectateurs ?

### T10.3 Les réglages (Opus)

D'après le journal, les temps mesurés et les retours :

- corriger la difficulté et la durée estimées des salles et des mécanismes ;
- ajuster les durées des étapes, les quantités d'objets, les rôles actifs, la force des tentations ;
- corriger les défauts relevés ;
- mettre à jour `soiree.md` et le plan si une règle a changé (avec l'accord de la propriétaire).
- **Fini quand :** le « Fini quand » de l'étape.

## Après la première soirée

- Verser les temps mesurés dans la banque de salles.
- Ajouter des salles (objectif : 15 à 18) et des mini-jeux, par le même chemin que T9.
- Reprendre les idées écartées de la première version : le défi à 1 point entre joueurs, le « rappel » d'un
  fantôme par un combat.
- Sortir du paquet `trahison` ce qui resservira à d'autres événements d'Events (zones, faux blocs, vocal par
  zones, écrans de mini-jeu).
