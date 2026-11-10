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

Events ne doit pas toucher au code de Phantasmon, comme Showtime (D-37). Il faut donc une petite API publique dans
Phantasmon-Client, à ajouter et valider avant :

- déclarer une zone comme membre d'un hub, sans construction par schematic ni contrôle de zone vide ;
- recevoir et envoyer des messages WebSocket propres à Events, avec le JWT déjà ouvert.

Côté backend, une nouvelle décision **D-38** (hubs miroirs), dans les deux docs miroirs.

## 6. Phases

| Phase | Contenu | Fini quand |
|---|---|---|
| E0 | Projet Fabric (comme Showtime), dépendance à Phantasmon et Showtime, dépôt GitHub, déploiement dans les deux instances | Le jar vide se charge avec Phantasmon |
| E1 | Phantasmon : API d'extension (hub sans schematic, messages Events). Backend : D-38, table des hubs miroirs, commandes admin `create` / `delete` / `list` | Un admin crée un hub miroir, il apparaît dans la liste |
| E2 | Capture : instantanés et deltas envoyés par les clients sur le serveur, copie de référence au backend | Le backend a une copie juste de la zone, mise à jour quand on pose ou casse un bloc |
| E3 | Copie solo : pose de la copie, construction en vrais blocs, deltas en direct, rotation | Un bloc posé sur le serveur apparaît dans le monde solo en quelques secondes |
| E4 | Hub Phantasmon complet : avatars, vocal, combats Ghost entre la zone et les copies | Validation en jeu par Adrien (serveur + monde solo) |

Ensuite : le moteur d'événements (portes, chronos, points, panneau de maître du jeu), puis `Phantasmon-Trahison`.

## 7. État (2026-10-29)

E0 à E3 faites et validées en jeu par Adrien, avec ses ajustements (décision D-38) : une seule commande
`/phantasmon event hub anchor` (source sur un serveur, copie en solo), taille par rayon sans limite haute, toute la hauteur
du monde, Anchors jamais tournés, terrain par colonnes de 16 × 16. E4 : avatars, vocal et combats Ghost validés.

## Gestionnaire des hubs : onglet « Hubs Event » (à faire, Adrien 2026-11-10)

Phantasmon a un gestionnaire des hubs pour les admins (touche H). Events doit y ajouter son onglet « Hubs Event »
(hubs miroirs compris) par `PhantasmonApi.registerHubManagerTab` : `shows` = type `MIRROR`, détails (rayon,
terrain), boutons (agrandir / réduire, effacer les blocs fantômes) et création d'un hub miroir.

## Dresseurs PNJ (Adrien 2026-11-02)

- Un admin pose un dresseur PNJ où il se tient, dans un événement : `/phantasmon event npc create <événement> <nom>`
  (l'événement est créé fermé s'il n'existe pas). Les joueurs ne voient et ne défient les PNJ d'un événement que s'il
  est ouvert (`/phantasmon event open|close <événement>`, `list`) ; les admins les voient toujours, grisés s'il est fermé.
- La roue de Cobblemon (touche R maintenue), comme sur un joueur : « Combattre avec mon équipe Ghost » ou « avec mon
  équipe Cobblemon » ; pour un admin, aussi « Modifier le dresseur ». Un clic droit ouvre l'éditeur (admins) : nom, skin
  d'un pseudo Minecraft, événement, IA, format, équipe copiée de son équipe Ghost ou Cobblemon, déplacer ici,
  ouvrir/fermer l'événement, l'affronter, supprimer. `/phantasmon event npc move` amène le dernier PNJ édité (sinon le
  plus proche) exactement là où se tient l'admin, tourné comme lui.
- Il regarde le joueur le plus proche dans un rayon de 10 blocs et le suit des yeux, en tournant lentement (tête, puis
  corps au-delà de 45°) ; personne : il revient doucement à son orientation.
- **Dans un hub** (Adrien 2026-11-03) : posé ou déplacé à l'intérieur de la zone d'un hub, le PNJ appartient à ce hub ;
  sa place est gardée en coordonnées du hub et chaque client le montre dans chaque zone de ce hub (la source, les
  copies, les ancres ordinaires), donc tous ceux qui ont ce hub le voient et peuvent le défier. Hors de tout hub, il
  reste à sa place dans son monde.
- Le combat : comme le combat solo admin — le client du joueur fait tourner le moteur, l'IA joue le PNJ ; un vrai combat
  Ghost (Showtime, intro, ambiance), regardable, jamais enregistré.
- IA : Aléatoire, puis l'« IA forte » de Cobblemon (Facile 1, Normale 3, Difficile 4, Très difficile 5), puis
  **Expert**, l'IA maison (`ai/ExpertBattleAI` dans Phantasmon Events depuis le 2026-11-10, TODO-35 ; combats simples seulement, les seuls joués) :
  - v1 (2026-11-03) : calcul de dégâts proche des jeux, un tour d'avance contre la meilleure réponse adverse ;
  - v2 (2026-11-03) : ne lit plus la vraie équipe adverse — ce qui est visible (Pokémon sur le terrain, PV, boosts,
    statut, attaques déjà utilisées, Pokémon déjà vus) et le reste tiré des statistiques Smogon d'octobre 2026
    (`assets/phantasmon_events/ai/smogon_sets.json`, `tools/smogon_sets.py` d'Events : attaques courantes, objet, talent, EV et nature) ;
    les deux camps choisissent en même temps : réponses adverses pondérées par leur usage et par ce qu'elles valent
    contre notre attaque évidente (prédiction des changements : frapper le Pokémon qui entre) ; Méga-Évolution dès que
    possible, Téracristallisation et capacité Z quand elles gagnent l'échange (une seule fois) ; meilleur duel après un KO ;
  - v3 (2026-11-03, après la v2 trop faible : aucun changement, aucune anticipation) : lecture **d'équipe** — tableau
    des duels de chaque Pokémon contre chaque Pokémon adverse ; un Pokémon seule réponse à une menace vaut plus (on le
    préserve, quitte à perdre un tour à changer) ; un Pokémon qui bat tout ce qui reste est une condition de victoire
    (gardée jusqu'à ce que les réponses adverses tombent), de même côté adverse (ses réponses et sa condition de victoire
    deviennent des cibles) ; plateau noté (PV pondérés par l'importance, menaces encore couvertes, conditions de victoire,
    duel sur le terrain, boosts, pièges, statuts) ; recherche sur **deux tours**, choix simultanés, réponses adverses
    prédites ; tour joué : changements avec Piège de Roc et Picots, priorité et Vitesse, KO qui annule l'attaque du plus
    lent, boosts, soins, statuts, Abri, poison et brûlure, Restes, remplacement après un KO ; lead choisi au lobby ;
  - v4 (2026-11-03, après la v3 : changements « en panique » quand tout semblait perdu, sweeper adverse laissé se
    booster, pas de course à la priorité) : objets, talents et Téra révélés lus dans le journal Showdown du combat
    (`-item`, `-enditem`, `-ability`, `-terastallize`) et appliqués à l'adversaire ; chaque changement d'affilée coûte
    plus cher ; les boosts comptent dans les duels du Pokémon sur le terrain (un sweeper boosté devient une condition de
    victoire adverse à arrêter) et pèsent plus ; une attaque prioritaire qui achève gagne le duel quelle que soit la
    Vitesse ; Buée Noire, Hurlement, Draco-Queue… effacent les boosts ;
  - à venir : régler les poids d'après les parties.
- Côté client : entité joueur locale (`npc/EventNpcEntity`) ; liste relue à l'arrivée, au changement de dimension, toutes les 30 s et sur `EventsNpcsChanged`.

## La copie sur notre serveur dédié (Adrien 2026-11-02, voie 3 choisie)

La copie d'un hub miroir peut maintenant être **la map de notre serveur dédié** (server-linux, Cobblemon Academy, monde
issu de Mirror) au lieu d'un monde solo :
- un admin connecté à ce serveur pose la copie : `/phantasmon event hub anchor <hub> copy` (sans le mot, rien ne
  change : copie en solo, source sur un serveur ; `source` force une source) ;
- le **mod serveur** `mirror-server/` (`phantasmon-mirror-server`, seulement sur ce serveur, `environment: server`) lit
  toutes les 10 s les copies posées sur ce serveur (`GET /server/mirror/copies?fingerprint=`, empreinte calculée comme
  sur le client depuis `address`), charge leur terrain tuile par tuile puis suit les changements en interrogeant le
  backend toutes les secondes (`/server/mirror/{hub}/feed?since=`) — sans joueur connecté ; il écrit les blocs sur le
  fil du serveur, 15 ms par tick au plus. Configuration `config/phantasmon-mirror-server.json` (`backend_url`, `token`,
  `address`) ;
- le backend garde pour chaque hub les 2 000 derniers messages envoyés aux copies ; s'il en manque, le mod reconstruit
  la copie. Routes `/server/mirror/**` protégées par un jeton partagé (`PHANTASMON_MIRROR_SERVER_TOKEN`), 404 sans jeton
  configuré ;
- les blocs fantômes des autres copies y sont écrits ; les blocs qu'un **opérateur** pose ou casse à la main dans la
  copie deviennent des blocs fantômes (2026-11-03) : le mod les voit sur le serveur (mêmes mixins que la copie solo),
  les envoie toutes les 0,5 s (`POST /server/mirror/{hub}/ghosts`) et ils apparaissent dans la zone source et les
  autres copies ; les autres joueurs ne peuvent rien y peindre.

Pas encore essayé en jeu : le serveur Minecraft reste éteint pour l'instant (mémoire).
