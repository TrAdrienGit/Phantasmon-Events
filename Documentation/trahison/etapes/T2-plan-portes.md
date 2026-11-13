# T2 : le plan, les portes, les cabines

## Objectif

Le jeu quitte les écrans provisoires et entre dans le monde : on passe une porte en marchant, on vote en
s'enfermant dans une cabine, on sort par la porte finale.

**Fini quand :** dans un décor provisoire construit à la main, les 3 manches se jouent avec les jambes : les
portes de couleur ne s'ouvrent que pour une bonne combinaison de bracelets, un joueur peut fermer une cabine au
nez de son partenaire, et la porte finale ne laisse passer que les bracelets à 9.

## À lire avant

- [`plan.md`](../plan.md), section 3 (portes, vote) et section 7 (le plan fixe).
- [`architecture.md`](../architecture.md), section 6.
- [`solutions-techniques.md`](../solutions-techniques.md) : S1 (faux blocs), S2 (position des joueurs), S14 (affichage dans le monde).
- `Phantasmon-Events/.../hub/MirrorGhosts.java`, `MirrorEdits.java` et les mixins `ClientLevelMirrorMixin`,
  `ClientPacketListenerMirrorMixin`, `LevelGhostEditMixin`, `MultiPlayerGameModeGhostMixin` : comment un faux bloc
  tient face aux mises à jour du serveur.
- `Phantasmon-Client/.../hub/HubCoordinates.java`, `HubController.java` (`joinedAnchor`, `members`),
  `HubDebugBorders.java` (dessiner des boîtes dans le monde).

## Sous-tâches

### T2.1 Le plan et son éditeur (Opus)

- Backend, paquet `layout` : `Layout` (zones, portes, places, cabines, cachettes : architecture, section 6),
  table `trahison_layouts` (`hub`, `version`, `data jsonb`), routes `GET` et `PUT /events/trahison/layout/{hub}` (écriture
  réservée aux admins). Validation à l'enregistrement : 3 portes de couleur par manche, 6 cabines, 9 places, une
  porte finale, aucune zone qui en chevauche une autre.
- Client, paquet `edit` : un mode éditeur pour les admins (`/phantasmon trahison edit`).
  - Un outil tenu en main : clic gauche et clic droit posent les deux coins d'une boîte, comme une sélection.
  - Un écran latéral : liste des éléments, création (type, identifiant, champs), suppression, enregistrement.
  - Les éléments sont dessinés dans le monde : boîtes colorées par type, identifiant flottant.
- Toutes les positions sont converties en coordonnées de hub avant l'envoi.
- **Fini quand :** un plan complet se dessine en jeu, s'enregistre, et se recharge à l'identique depuis un autre
  compte admin.

### T2.2 Les faux blocs et les zones (Opus)

- Solution S1, à appliquer telle quelle : ajouter à `hub/MirrorGhosts` d'Events les trois méthodes publiques
  `putLocal`, `removeLocal`, `clearLocal` (une source nommée, en plus des hubs). C'est la seule retouche au code
  existant d'Events dans cette étape.
- `world/FakeBlocks` : traduit l'état des portes reçu du backend en appels à ces méthodes (propriétaire
  `"trahison"`). Bloc d'une porte fermée : réglable par type de porte.
- `world/Zones` (solution S2) : à chaque tick, la zone du joueur d'après le plan ; `EventsTrahisonZone { zone, x,
  y, z }` au changement, et au moins toutes les 2 secondes pendant une partie. Le backend garde la zone et la
  position de chaque joueur dans `Game`.
- Les portes s'ouvrent et se ferment sur ordre du backend (état des portes dans la vue).
- Un fantôme ou un spectateur ne reçoit pas les faux blocs : il traverse.
- **Fini quand :** une porte fermée arrête un joueur, s'ouvre chez tous en même temps, et survit à un
  rechargement des tronçons (s'éloigner, revenir).

### T2.3 Les portes de couleur (Sonnet)

- Devant chaque porte de couleur, une zone « seuil ». Le backend ouvre la porte quand les bracelets des joueurs
  présents sur le seuil satisfont `Doors.canOpen`, et la referme après leur passage (ou après quelques secondes).
- Affichage au-dessus de la porte : ses deux couleurs, et ce qu'il manque (« il faut un solo vert »).
- À la fin de l'étape `DOORS`, toutes les portes se ferment. Un joueur resté dans le hall subit la pénalité
  (question ouverte : demander avant de coder ; valeur par défaut −1, réglable).
- Le groupe d'une porte = les bracelets qui l'ont passée. C'est lui qui recevra la salle (T3) et qui votera.
- **Fini quand :** les deux répartitions possibles fonctionnent en jeu, et une mauvaise combinaison est refusée
  avec le bon message.

### T2.4 Les cabines et le vote (Opus)

- Une cabine a une zone et une porte. Action `close_cabin` : possible seulement depuis l'intérieur ; la porte se
  ferme (faux blocs) pour tout le monde.
- Dans une cabine fermée, un écran de vote s'ouvre sur un pupitre : Allier ou Trahir, puis confirmation. Le vote
  n'est accepté par le backend que si le joueur est dans **sa** cabine (celle de sa porte et de son côté) et
  qu'elle est fermée.
- Paire : celui des deux qui ferme et vote engage la paire. L'autre, resté dehors, ne peut plus rien.
- La cabine se rouvre à la fin de l'étape `VOTE`.
- Écran des résultats dans le hall : un grand tableau affiché dans le monde, face à tous, qui révèle les votes
  porte par porte puis les nouveaux points.
- **Fini quand :** en jeu, un joueur double son partenaire ; sans vote, « Allier » s'applique ; les résultats
  s'affichent à tous.

### T2.5 La porte finale (Sonnet)

- Devant la porte finale, un pupitre : action `open_final_door`, acceptée si le bracelet du poignet a 9 points
  ou plus et que l'étape est `FINAL`.
- La porte s'ouvre un court instant (durée réglable). Passent ceux qui ont 9 ; le faux mur reste en place chez
  les autres. Puis elle se referme pour toujours et la partie passe à `ENDED`.
- Tant qu'elle n'est pas ouverte : un vote recommence toutes les 10 minutes (déjà dans le moteur).
- **Fini quand :** en jeu, une sortie à plusieurs, une sortie solitaire, et une montée collective par la boucle
  de votes.

## Hors périmètre

Les salles (les portes de couleur mènent pour l'instant à des pièces vides avec un bouton « résolu »), le vocal,
la mort au couteau, les objets.

## Pièges

- On ne peut pas faire traverser un vrai bloc : toute porte est une ouverture réelle fermée par de faux blocs.
- Le serveur peut renvoyer les blocs à tout moment (rechargement, mise à jour voisine) : les faux blocs doivent
  être réappliqués, sinon une porte « s'ouvre » toute seule.
- Un joueur qui se déconnecte dans une cabine fermée : la cabine se rouvre à la fin de l'étape comme les autres.
- La porte finale « ouverte seulement pour certains » est un cas particulier : chaque client décide de retirer ou
  non son faux mur d'après la vue reçue.
