# Phantasmon Events

> Add-on client de Phantasmon (jar séparé, qui dépend de Phantasmon et de Cobblemon Battle Showtime). Commencé le
> 2026-10-29 : voir le [plan](Documentation/plan.md).

Outils pour organiser des événements communautaires Cobblemon (tournois, ligue, jeux) au-dessus de Phantasmon, sur
un serveur où l'on ne peut **installer aucun mod serveur**.

## Ce qu'il fait (état au 2026-11-25)

| Partie | Contenu | État | Doc |
|---|---|---|---|
| Hubs miroirs | Une zone d'un serveur copiée dans le monde solo de l'organisateur (blocs, deltas en direct, blocs fantômes), joueurs en avatars, vocal, combats Ghost ; onglet « Hubs Event » du gestionnaire des hubs ; une ambiance de Showtime par hub ; entrées et sorties silencieuses | Validé en jeu | [`plan.md`](Documentation/plan.md) |
| Copie sur notre serveur dédié | Mod serveur qui tient une copie du hub sur un serveur à nous | Écrit, pas encore essayé en jeu | [`plan.md`](Documentation/plan.md) |
| Dresseurs PNJ | Posés par un admin dans un événement, défiés par la roue de Cobblemon ; niveaux d'IA (dont expert et Reborn), intro, ambiance et format imposés, éditeur à listes déroulantes, debug de l'IA | Validé en jeu ; IA Reborn à réglages en cours (pas encore validée ni commitée) | [`plan.md`](Documentation/plan.md) |
| Tournois | Ronde suisse et double élimination, check-in, formulaire de création, annonces, pages web | Validé à deux comptes ; reste un essai grandeur nature | [`plan-tournois.md`](Documentation/plan-tournois.md) |
| Récompenses de la Ligue | Intros réservées (champion d'arène par type, Conseil 4, Maître de la Ligue) et ambiance « Arène du champion » | Validé en jeu | [`plan.md`](Documentation/plan.md) |
| League Reborn | Tous les terrains de Pokémon Reborn comme règle Showdown, graines et Roche Amplifield, une ambiance et un stade par terrain, transitions entre terrains, combats vus de loin | Validé en jeu | [`terrains/conception.md`](Documentation/terrains/conception.md) |
| Site public « Village 14 » | `/home`, `/tournament`, `/download` (`/d`), `/wiki` (graphes des terrains) ; `/league` masquée, avec des données d'exemple à remplacer | En ligne | [`backend-reference.md`](Documentation/backend-reference.md) |
| Jeu de trahison | Soirée à 9 joueurs : salles d'escape game, votes secrets, rôles, mort et fantômes, vocal, maître du jeu, éditeurs du plan et des salles | Code fini et déployé, **pas encore essayé en jeu** | [`trahison/README.md`](Documentation/trahison/README.md), [`trahison/reprise.md`](Documentation/trahison/reprise.md) |
| Compatibilité LeagueWay | Skin des Ghost, touches du jeu de trahison | Validé | |

Idées pas encore commencées : [`idees-events.md`](Documentation/idees-events.md). Publication sur Modrinth et
CurseForge : [`publication/`](Documentation/publication/).

## Principes

- Un **hub miroir** se pose sans vérification d'espace vide et reprend le terrain réel du serveur où il est posé ;
  sens **unique** : serveur → monde solo, rien n'est jamais renvoyé vers le serveur.
- Toute la logique d'un événement vit dans le backend (état, chronos, points, portes, votes), dans le module
  `backend/` chargé par le backend Phantasmon depuis son dossier `addons/` (D-41) ; Minecraft ne sert qu'à
  l'affichage, côté client.

## Limites connues dès maintenant

- Le contenu des coffres n'est pas envoyé au client : la copie du terrain ne peut pas l'inclure.
- Sans mod serveur, tout ce que les joueurs voient d'ajouté est client (faux blocs, entités locales).

## License

Apache License 2.0 — see [`LICENSE`](./LICENSE). Free to use, modify and redistribute; forks and redistributions must keep [`NOTICE`](./NOTICE) and credit the original author.
