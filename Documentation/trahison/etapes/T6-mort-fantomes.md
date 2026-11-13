# T6 : la mort, les bracelets, les fantômes

## Objectif

Un joueur peut mourir. Son corps reste, son bracelet se ramasse, il devient un fantôme. Les spectateurs et les
joueurs déconnectés sont traités ici aussi, parce que l'affichage est le même travail.

**Fini quand :** en jeu, un joueur tombe à 0 point : son corps apparaît où il était, il devient un fantôme en
noir et blanc qui traverse les portes ; un autre joueur ramasse son bracelet et passe grâce à lui une porte qu'il
ne pouvait pas passer ; le bracelet du mort vote « Allier » ; un joueur déconnecté laisse un mannequin marqué
« déconnecté ».

## À lire avant

- [`plan.md`](../plan.md), section 3 (« La mort, les bracelets et les fantômes », « Spectateurs, fantômes et
  déconnexions »).
- [`architecture.md`](../architecture.md), sections 4 et 6 (« Joueurs dans un état spécial »).
- `Phantasmon-Events/.../npc/EventNpcEntity.java`, `EventNpcs.java` : une entité joueur locale avec un skin.
- `Phantasmon-Client/.../hub/HubAvatars.java` (`skinOf`, avatars des copies).
- Le code de T1 (`Game`, `Views`) et de T2 (`FakeBlocks`, `Zones`).

## Question à poser avant de commencer

La mort est-elle annoncée à tous, ou découverte (corps trouvé, ou résultats suivants) ? Le plan propose
« découverte ». Le réglage doit exister dans les deux sens (`settings.announceDeaths`).

## Sous-tâches

### T6.1 La mort dans le moteur (Opus)

Backend.

- `GameService.kill(joueur, cause)` : `alive = false`, lieu du corps = dernière position connue (le client
  l'envoie avec ses changements de zone ; ajouter la position au message `EventsTrahisonZone`), bracelet du poignet
  marqué « sur le corps ».
- Mort par 0 point : appliquée à la fin de l'étape `RESULTS`.
- Le bracelet d'un mort : garde points, couleur et statut ; reçoit une couleur à chaque nouvelle manche comme les
  autres ; vote « Allier » ; ses points varient encore avec les votes.
- Action `take_bracelet` : un vivant, dans la zone du corps et à moins de 3 blocs, prend le bracelet. Il passe
  dans `carried`.
- `Doors.canOpen` compte déjà les bracelets présentés : y ajouter les bracelets portés.
- La porte finale ne lit que le bracelet du poignet (déjà vrai en T1 : le vérifier par un test avec un bracelet
  porté à 9).
- Vues : un vivant n'apprend une mort que selon le réglage (annonce, corps vu, ou résultats).
- Tests : mort à 0 ; bracelet ramassé qui ouvre une porte ; vote par défaut du mort ; porteur de deux bracelets
  devant la porte finale.
- **Fini quand :** les tests passent.

### T6.2 Le corps et le bracelet dans le monde (Sonnet)

Client, paquet `world`.

- `world/Bodies` : une entité joueur locale, couchée, au skin du mort, à l'endroit du corps. Elle n'apparaît que
  chez les clients à qui le backend l'a envoyée.
- En visant le corps : « Prendre le bracelet » (l'action de T6.1). Le corps est ensuite affiché sans bracelet.
- Voir un corps (être dans sa zone, à moins de 8 blocs, avec une ligne de vue) envoie `body_seen` : le backend
  révèle alors la mort à ce joueur.
- `hud/BraceletHud` : affiche les bracelets portés à côté du sien, plus petits, avec leur couleur et leurs points.
- **Fini quand :** le corps, la découverte et le ramassage marchent en jeu à deux comptes.

### T6.3 Les fantômes et les spectateurs (Opus)

Client.

- Chez un vivant, le vrai personnage d'un mort n'est plus affiché normalement : selon le réglage du maître du
  jeu, silhouette translucide en noir et blanc, ou rien du tout (ni corps, ni pseudo, ni ombre, ni bruit de pas).
  Mixin sur le rendu des joueurs, limité aux identifiants concernés.
- Chez le fantôme : aucun faux bloc (il traverse les portes), aucun objet cliquable, ses actions de jeu sont
  refusées. Un filtre gris léger sur son écran, et le rappel « tu es un fantôme ».
- Le skin en noir et blanc : conversion de la texture du skin en niveaux de gris, côté client.
- Spectateurs : inscrits comme tels au lobby (action `spectate`), ou tout membre du hub non inscrit. Même
  traitement que les fantômes, sans corps. Dans une copie du hub, ils sont des avatars : appliquer le même réglage
  d'affichage aux avatars.
- Bien les distinguer des joueurs : étiquette « spectateur » ou « fantôme » sous le pseudo, visible seulement des
  autres fantômes et spectateurs si les vivants ne doivent pas les voir.
- **Fini quand :** un vivant ne peut pas confondre un fantôme avec un joueur, dans les deux réglages.

### T6.4 Les déconnexions (Sonnet)

- Quand un joueur passe à `connected = false` : chez les autres, un mannequin debout à son skin, à sa dernière
  position, avec « déconnecté » sous son pseudo.
- Avec un maître du jeu : rien d'automatique, il met en pause s'il le veut. En mode automatique : les chronos
  s'arrêtent 5 minutes au plus (réglable), puis reprennent.
- Au retour du joueur : le mannequin disparaît, il reçoit sa vue complète et reprend là où il en était (salle,
  inventaire, cabine).
- Aucune sanction, aucun remplacement par un factice.
- **Fini quand :** une déconnexion puis une reconnexion en pleine salle ne cassent rien.

## Hors périmètre

Le couteau et les entraves (T7). Le filtrage de la voix (T5).

## Pièges

- Le mort reste un vrai joueur sur le serveur : il peut encore pousser, ouvrir une vraie porte, poser un bloc.
  On ne peut pas l'empêcher ; on le lui interdit par la règle.
- Ne pas envoyer la liste des morts à tous les clients « pour l'affichage » : un vivant ne doit pas pouvoir
  l'apprendre avant l'heure.
- Le dernier lieu connu doit être assez frais : envoyer la position au moins toutes les 2 secondes pendant une
  partie.
