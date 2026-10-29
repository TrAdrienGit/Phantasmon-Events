# Plan : Phantasmon Events, hubs miroirs

> Rédigé le 2026-10-29 à la demande d'Adrien.

## 1. Objectif

Phantasmon Events est un add-on de Phantasmon (jar client séparé, qui dépend de Phantasmon) pour qu'Adrien crée
ses propres événements. Première brique : les **hubs miroirs**.

- Un admin de Phantasmon (MystAria_, TheMashen : `admins.txt` du backend) délimite une zone sur le **serveur
  multijoueur**. Seuls les admins créent ou suppriment ces hubs.
- Le même hub se pose dans un **monde solo**, et les blocs de la zone du serveur y sont recopiés.
- Les mises à jour se font en temps réel, **dans un seul sens** : un bloc posé ou cassé sur le serveur change aussi
  dans le monde solo. Rien ne remonte vers le serveur (aucun mod serveur, D-01).
- Tout ce que Phantasmon offre dans un hub reste disponible : avatars des joueurs de l'autre côté, vocal (D-36),
  combats Ghost.

## 2. Choix d'Adrien (2026-10-29)

| Sujet | Choix |
|---|---|
| Qui crée les hubs miroirs | Les admins de Phantasmon seulement |
| Qui alimente la copie | **Tout joueur Phantasmon** présent près de la zone sur le serveur : il envoie les blocs qu'il reçoit et voit changer. Le backend garde la dernière copie, donc le monde solo se construit même sans personne sur le serveur |
| Forme dans le monde solo | **Vrais blocs**, écrits par le serveur intégré. Une modification locale est écrasée par la prochaine mise à jour du bloc venant du serveur |
| Backend | **Module `events` de Phantasmon-Backend** : il réutilise le WebSocket, les comptes, les admins et les hubs |

## 3. Fonctionnement

```
Serveur multi (zone source)          Backend (module events)              Monde solo (copie)
joueurs Phantasmon proches  ──────►  copie de référence de la zone  ────►  vrais blocs posés
  - chunk reçu : instantané          (palette + blocs, versionnée)         par le serveur intégré
  - bloc changé : delta              diffuse les deltas aux copies         (rotation selon la copie)
```

- **Zone source** : nom, empreinte du serveur, dimension, coin, taille, orientation. L'admin la crée en se plaçant
  dessus, comme un Anchor, mais **sans exiger de zone vide**.
- **Capture** (client sur le serveur) :
  - à la réception d'un chunk qui touche la zone, envoi d'un instantané des sections concernées si sa version
    diffère de celle du backend (empreinte par section, pour éviter de renvoyer ce qui est déjà connu) ;
  - à chaque changement de bloc (`ClientboundBlockUpdatePacket`, `ClientboundSectionBlocksUpdatePacket`), envoi
    d'un delta, regroupé et limité en débit ;
  - plusieurs clients peuvent envoyer la même chose : le backend dédoublonne, la dernière valeur l'emporte.
- **Copie** (monde solo) : l'organisateur pose sa copie du hub. À l'entrée, le monde solo charge la copie de
  référence, puis applique les deltas reçus. L'écriture des blocs se fait en tâches limitées par tick pour ne pas
  geler le jeu.
- **Hub Phantasmon** : la zone source et chaque copie forment un même hub. Les joueurs du serveur dans la zone et
  l'organisateur dans sa copie se voient en avatars, s'entendent et peuvent lancer des combats Ghost.

## 4. Limites connues

- **Contenu des coffres et inventaires** : le serveur ne l'envoie pas au client, il ne peut donc pas être copié.
  Le texte des panneaux et les bannières sont envoyés, donc copiés.
- **Entités** (PNJ, cadres, armures, Pokémon) : non copiées dans un premier temps.
- **Zone non chargée** : quand personne n'est près de la zone sur le serveur, la copie reste figée sur la dernière
  version connue.
- **Confiance** : n'importe quel joueur Phantasmon peut envoyer des blocs. Le backend n'accepte que ceux qui sont
  dans la zone et sur le bon serveur (empreinte), mais un client modifié pourrait mentir. À surveiller si cela pose
  problème un jour (par exemple en exigeant que deux clients soient d'accord).

## 5. Ce qu'il faut dans Phantasmon

Events ne doit pas toucher au code de Phantasmon, comme Spectacle (D-37). Il faut donc une petite API publique dans
Phantasmon-Client, à ajouter et valider avant :

- déclarer une zone comme membre d'un hub, sans construction par schematic ni contrôle de zone vide ;
- recevoir et envoyer des messages WebSocket propres à Events, avec le JWT déjà ouvert.

Côté backend, une nouvelle décision **D-38** (hubs miroirs), dans les deux docs miroirs.

## 6. Phases

| Phase | Contenu | Fini quand |
|---|---|---|
| E0 | Projet Fabric (comme Spectacle), dépendance à Phantasmon et Spectacle, dépôt GitHub, déploiement dans les deux instances | Le jar vide se charge avec Phantasmon |
| E1 | Phantasmon : API d'extension (hub sans schematic, messages Events). Backend : D-38, table des hubs miroirs, commandes admin `create` / `delete` / `list` | Un admin crée un hub miroir, il apparaît dans la liste |
| E2 | Capture : instantanés et deltas envoyés par les clients sur le serveur, copie de référence au backend | Le backend a une copie juste de la zone, mise à jour quand on pose ou casse un bloc |
| E3 | Copie solo : pose de la copie, construction en vrais blocs, deltas en direct, rotation | Un bloc posé sur le serveur apparaît dans le monde solo en quelques secondes |
| E4 | Hub Phantasmon complet : avatars, vocal, combats Ghost entre la zone et les copies | Validation en jeu par Adrien (serveur + monde solo) |

Ensuite : le moteur d'événements (portes, chronos, points, panneau de maître du jeu), puis `Phantasmon-Trahison`.

## 7. État (2026-10-29)

E0 à E3 faites et validées en jeu par Adrien, avec ses ajustements (décision D-38) : une seule commande
`/events hub anchor` (source sur un serveur, copie en solo), taille par rayon sans limite haute, toute la hauteur
du monde, Anchors jamais tournés, terrain par colonnes de 16 × 16. E4 : avatars, vocal et combats Ghost validés.
