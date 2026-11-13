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

Notre mod ne règle pas le serveur de la communauté. On agit donc **chez celui qui écoute** : son mod coupe ou
baisse la voix d'un joueur précis. La recherche du 2026-11-13 a montré que c'est possible, pour les deux chemins,
sans toucher à Phantasmon : voir [`solutions-techniques.md`](../solutions-techniques.md), S4 et S5. Toute l'étape applique ces
deux solutions.

## À lire avant

- [`plan.md`](../plan.md), section 8.
- [`architecture.md`](../architecture.md), section 6 (zones).
- `Phantasmon-Client/.../voice/HubVoice.java` et `compat/voicechat/PhantasmonVoicechatPlugin.java`.
- `Phantasmon-Backend/.../hub/HubVoice.java` : le relais des trames (à lire, pas à modifier).
- `Phantasmon-Backend/Documentation/architecture/decisions.md`, D-36 ; `project/known-issues.md`, LIM-15 et
  TODO-34.
- L'API de Simple Voice Chat (dépôt `simple-voice-chat` présent sur la machine, lecture seule) :
  `api/.../events/ClientReceiveSoundEvent.java`.

## Sous-tâches

### T5.1 Le greffon et la confirmation en jeu (Opus)

La faisabilité est établie par lecture du code (solutions S4 et S5). Il reste à la voir tourner.

- Ajouter à Events son greffon Simple Voice Chat (`trahison/voice/TrahisonVoicePlugin`), comme décrit en S4 :
  point d'entrée `voicechat` dans `fabric.mod.json`, dépendance `compileOnly` sur l'API, abonnement aux trois
  variantes de `ClientReceiveSoundEvent`, table des gains lue depuis le fil audio.
- Une commande d'essai pour admins, `/phantasmon event trahison voice <joueur> <gain>`, qui force le gain d'un
  joueur (0 = muet, 0.5, 1).
- Confirmer en jeu, à deux comptes, et noter le résultat dans `Documentation/trahison/vocal-essai.md` :
  1. gain 0 coupe bien la voix de proximité d'un joueur, sans grésillement ;
  2. un gain intermédiaire la baisse ;
  3. la même chose pour une voix venant d'une copie du hub (clé = identifiant de l'avatar, S5) ;
  4. l'icône « parle » d'un joueur coupé.
- **Fini quand :** les quatre points sont confirmés. Si l'un échoue, s'arrêter et le signaler : c'est toute la
  suite de l'étape qui en dépend.

### T5.2 Les zones sonores (Opus)

- Le backend connaît la zone de chaque joueur (T2). Il ajoute à la vue, pour chaque joueur, sa **zone sonore** :
  sa zone, ou sa cabine si elle est fermée.
- Client, paquet `voice` : à chaque tick, recalculer la table des gains (règles de S4) d'après la vue de la
  partie : 0 hors de ma zone sonore, sinon selon la distance.
- Portée dans la zone : gain 1 jusqu'à 4 blocs, qui tombe à 0 à 8 (réglable). Chuchotement (la touche de Simple
  Voice Chat, lue sur `EntitySound.isWhispering`) : 0 à 3 blocs.
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

- Tout se fait dans la table des gains de T5.2, sans toucher à Phantasmon (solution S5) : les voix des copies
  passent par le même événement, sous l'identifiant de l'avatar. Ajouter cette seconde clé pour chaque joueur.
- Un vivant met à 0 le gain des fantômes et des spectateurs. Un fantôme ou un spectateur entend tout le monde.
- L'annonce du maître du jeu : une touche tenue envoie `EventsTrahisonGm { action: "announce", on }` ; le backend
  le porte dans la vue ; tous les clients mettent son gain à 1 pendant ce temps.
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
- Pas de doublon à craindre : Phantasmon ne joue la voix du hub que pour un joueur dont l'avatar est affiché,
  jamais pour un joueur présent en vrai sur le même serveur.
- L'événement arrive sur le fil audio : ne rien lire du monde ni de la partie depuis là (S4).
- Le filtrage est chez celui qui écoute : un client sans le mod entend tout. C'est accepté (joueurs de confiance).
