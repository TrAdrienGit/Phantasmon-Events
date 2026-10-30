# Phantasmon Events

> Add-on client de Phantasmon (jar séparé, qui dépend de Phantasmon et de Cobblemon Spectacle). Commencé le
> 2026-10-29 : voir le [plan](Documentation/plan.md).

Outils pour organiser des événements communautaires Cobblemon (tournois, ligue, jeux) au-dessus de Phantasmon, sur
un serveur où l'on ne peut **installer aucun mod serveur**.

## Idées retenues

### Hubs blancs et miroir vers le monde solo

- Un **hub blanc** se pose sans vérification d'espace vide et reprend le terrain réel du serveur où il est posé.
- Le client qui le pose lit les blocs de la zone et les envoie au backend ; le monde **solo** de l'organisateur les
  reconstruit en vrais blocs.
- Sens **unique** : serveur → monde solo. L'organisateur s'adapte au monde du serveur, les joueurs du serveur
  gardent toute leur liberté. Rien n'est jamais renvoyé vers le serveur.
- L'organisateur anime depuis son monde solo (tous les droits) ; les joueurs du serveur le voient en avatar, comme
  dans les hubs actuels.

### Moteur d'événements

- Toute la logique d'un événement vit dans le backend (état, chronos, points, portes, votes) ; Minecraft ne sert
  qu'à l'affichage, côté client.
- Panneau de maître du jeu : pause, forcer une étape, corriger, rejouer une annonce.
- Premiers usages : tournois aux règles Smogon, ligue (champions d'arène, Conseil 4), puis le jeu de trahison
  (`Phantasmon-Trahison`).

## Limites connues dès maintenant

- Le contenu des coffres n'est pas envoyé au client : la copie du terrain ne peut pas l'inclure.
- Sans mod serveur, tout ce que les joueurs voient d'ajouté est client (faux blocs, entités locales).

## License

Apache License 2.0 — see [`LICENSE`](./LICENSE). Free to use, modify and redistribute; forks and redistributions must keep [`NOTICE`](./NOTICE) and credit the original author.
