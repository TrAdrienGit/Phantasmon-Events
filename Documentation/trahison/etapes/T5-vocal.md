# T5 : le vocal par zones

## Objectif

On n'entend que ceux qui sont dans la même pièce. Une cabine fermée est étanche. Les fantômes et les spectateurs
entendent les joueurs, se parlent entre eux, et les joueurs ne les entendent pas.

**Fini quand :** en jeu, deux joueurs dans deux salles voisines ne s'entendent pas ; dans la même salle, la voix
porte à 8 blocs environ et le chuchotement à 3 ; une cabine fermée ne laisse rien passer ; un spectateur dans une
copie du hub entend les joueurs sans être entendu.

## Ce qu'il faut comprendre d'abord

Il y a **deux chemins de voix** dans un hub miroir :

| Chemin | Entre qui | Qui décide de ce qu'on entend |
|---|---|---|
| Simple Voice Chat du serveur | Les joueurs présents sur le serveur de la communauté | Le serveur (portée fixe), puis le client de chacun |
| Vocal du hub de Phantasmon | Entre le serveur et les copies du hub | Le backend Phantasmon, qui relaie les trames |

Notre mod ne règle pas le serveur de la communauté. Pour le premier chemin, on ne peut donc agir que **chez celui
qui écoute** : son mod coupe ou baisse la voix d'un joueur précis. C'est le point à vérifier en premier.

## À lire avant

- [`plan.md`](../plan.md), section 8.
- [`architecture.md`](../architecture.md), section 6 (zones).
- `Phantasmon-Client/.../voice/HubVoice.java` et `compat/voicechat/PhantasmonVoicechatPlugin.java`.
- `Phantasmon-Backend/.../hub/HubVoice.java` : le relais des trames (à lire, pas à modifier).
- `Phantasmon-Backend/Documentation/architecture/decisions.md`, D-36 ; `project/known-issues.md`, LIM-15 et
  TODO-34.
- L'API de Simple Voice Chat (dépôt `simple-voice-chat` présent sur la machine, lecture seule) : les événements
  côté client.

## Sous-tâches

### T5.1 L'essai de faisabilité (Opus)

Un essai court, jeté ensuite, pour répondre à trois questions :

1. Un greffon client de Simple Voice Chat peut-il **couper** la voix de proximité reçue d'un joueur donné ?
   (Chercher un événement de réception du son côté client qui permet de modifier ou d'annuler l'audio.)
2. Peut-il la **baisser** selon la distance, pour imposer une portée plus courte que celle du serveur ?
3. Le vocal du hub tient-il 9 voix à la fois sans retard gênant (TODO-34) ?

- Rendre un court rapport dans `Documentation/vocal-essai.md` : oui ou non pour chaque question, avec la preuve
  (classe et méthode de l'API, mesure).
- **Fini quand :** les trois réponses sont écrites. La suite de l'étape dépend d'elles :
  - si 1 et 2 sont possibles : voie A (filtrer chez celui qui écoute), décrite ci-dessous ;
  - sinon : voie B, à concevoir avec la propriétaire avant de continuer (par exemple tout faire passer par le
    vocal du hub et demander aux joueurs de couper la proximité du serveur le temps de la partie).

### T5.2 Les zones sonores, voie A (Opus)

- Le backend connaît la zone de chaque joueur (T2). Il ajoute à la vue, pour chaque joueur, sa **zone sonore** :
  sa zone, ou sa cabine si elle est fermée.
- Client, paquet `voice` : à la réception d'une voix de proximité, la laisser passer seulement si celui qui parle
  est dans la même zone sonore que celui qui écoute. Sinon, silence.
- Portée dans la zone : volume plein jusqu'à 4 blocs, qui tombe à zéro à 8 (réglable). Chuchotement (la touche de
  Simple Voice Chat) : zéro à 3 blocs.
- Les portes ouvertes ne changent rien : c'est la zone qui compte. Un couloir est une zone à part.
- Hors partie : rien n'est filtré.
- **Fini quand :** les trois premiers cas du « Fini quand » de l'étape.

### T5.3 Fantômes, spectateurs et copies (Opus)

Règles à obtenir pendant une partie :

| Qui parle | Qui entend |
|---|---|
| Joueur vivant | Les vivants de sa zone sonore ; tous les fantômes et spectateurs |
| Fantôme ou spectateur | Les fantômes et spectateurs seulement |
| Maître du jeu | Tout le monde quand il tient la touche « annonce », sinon comme un spectateur |

- **Sans modifier Phantasmon.** Le vocal du hub relaie les voix aux membres du hub sans connaître le jeu. Le
  filtrage se fait donc chez celui qui écoute, dans Events : à la réception d'une voix (proximité du serveur ou
  vocal du hub), le mod la joue ou non d'après la vue de la partie (zone sonore, vivant ou fantôme).
- Chercher d'abord comment Events peut se placer entre la réception et la lecture d'une voix du hub avec ce que
  `PhantasmonApi` et le greffon de Simple Voice Chat offrent. Si ce n'est pas possible sans toucher à Phantasmon,
  s'arrêter et le signaler, avec ce qui manque exactement.
- **Fini quand :** le dernier cas du « Fini quand » de l'étape, et un fantôme sur le serveur n'est plus entendu
  des vivants.

### T5.4 L'Écoute (Sonnet, après T7)

L'objet « Écoute » permet d'entendre une cabine pendant un vote : son porteur reçoit, pour la durée du vote, la
voix de la zone sonore de la cabine visée. À faire quand les objets existent (T7).

## Hors périmètre

Changer la configuration du serveur de la communauté. Remplacer Simple Voice Chat.

## Pièges

- Ne charger aucune classe de Simple Voice Chat hors de son greffon : le mod reste facultatif (règle de D-36).
- Un joueur sans Simple Voice Chat n'a pas de voix du tout : le lobby doit le signaler avant le démarrage.
- La même voix peut arriver par les deux chemins : vérifier comment Phantasmon évite déjà le doublon avant d'y
  toucher.
- Le filtrage est chez celui qui écoute : un client sans le mod entend tout. C'est accepté (joueurs de confiance).
