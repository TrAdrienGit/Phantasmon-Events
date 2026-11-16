# Décisions à trancher

> Un point par section. Coche une case (`[x]`) ou écris ta réponse dans le champ « Ta réponse ». Le choix
> appliqué en attendant est marqué *(par défaut)*. Je relis ce fichier à chaque reprise ; une fois un point
> appliqué, je le déplace dans « Tranché ».

## D10. Une cabine libre : qui peut y entrer, et un intrus
Choix appliqué : une cabine ne se ferme que lorsqu'un membre de **son** groupe y entre (un solo la verrouille aussitôt, une paire attend le partenaire 30 s).
Tant que personne de son groupe n'y est, n'importe qui peut y entrer sans la bloquer. Un intrus déjà dedans quand elle se verrouille peut ressortir (sa porte reste
ouverte pour lui), mais ne peut pas y voter.
- [x] Comme appliqué *(par défaut)*
- [ ] Une cabine est réservée à son groupe dès le début de `VOTE` : sa porte est fermée à tous les autres même vide
- [ ] Un intrus est enfermé avec le groupe jusqu'à la fin de `VOTE`

Ta réponse :

## D11. Maître du jeu : aucun maître nommé en mode « Maître du jeu »
Choix appliqué : le mode `AUTO` marche sans maître du jeu ; le mode `GAME_MASTER` refuse de démarrer sans au moins un maître du jeu nommé (personne ne ferait avancer la partie).
- [x] Comme appliqué *(par défaut)*
- [ ] Le démarrage passe quand même et un admin du lobby peut faire avancer la partie
- [ ] Autre :

Ta réponse :

## D12. Qui peut être maître du jeu
Choix appliqué : un admin nomme n'importe quel compte autour du lobby (membre du hub, hors des 9 joueurs), admin ou non ; un maître du jeu ne joue pas et un joueur inscrit ne peut pas être nommé.
Seuls les admins nomment, et seulement en `LOBBY`. Un admin qui n'est pas nommé peut préparer le lobby (factices, démarrage) mais ne dirige plus la partie une fois lancée.
- [ ] Comme appliqué *(par défaut)*
- [x] Un maître du jeu peut aussi jouer
- [ ] On peut nommer ou retirer un maître du jeu en cours de partie

Ta réponse : un maitre du jeu peut aussi se jouer, ceci se passera sous la forme d'un rôle secret. C'est lui qui manipule le jeu, et si les autre participant le decouvre (le tue) alors ils gagnent automatiquement tous. Il peut ne pas y avoir de MJ qui joue mais qui reste en supervision hors jeu aussi.

## D13. Mort aux portes : ce qui reste de la personne
Choix appliqué : un joueur mort faute d'avoir passé une porte devient fantôme tout de suite (avant les salles), garde ses points sur son bracelet, reste dans son groupe de la
répartition (le groupe a un fantôme, son vote est « Allier » par défaut, son bracelet peut encore être trahi) et n'est plus compté aux portes des manches suivantes.
- [x] Comme appliqué *(par défaut)*
- [ ] Son bracelet tombe à 0 point
- [ ] Il perd aussi sa place dans le groupe : la salle et le vote se jouent sans lui

Ta réponse :

## D14. Carte : curseurs des autres pour les maîtres du jeu et les spectateurs
Le curseur d'un joueur est privé. Faut-il faire une exception pour ceux qui regardent la partie ?
- [ ] Personne ne voit le curseur d'un autre *(par défaut)*
- [ ] Les maîtres du jeu voient les curseurs de tous les joueurs
- [x] Les maîtres du jeu et les spectateurs les voient

Ta réponse :

## D15. Carte : annotations et bloc-notes à la fin de la partie
- [ ] Effacés à la fin de la partie *(par défaut)*
- [ ] Gardés, et visibles par leur auteur au bilan de la partie
- [x] Gardés et montrés à tout le monde au bilan

Ta réponse :

## D16. Carte : un fantôme garde-t-il sa carte ?
- [ ] Oui, il la garde, avec ses annotations *(par défaut)*
- [x] Il voit la carte complète, comme un spectateur
- [ ] Il la perd

Ta réponse : comme un spectateur, il garde ces annotations

## D17. Porte finale : faut-il se tenir au pupitre pour l'actionner ?
Choix appliqué : `open_final_door` exige d'être dans la zone du pupitre (le champ `threshold` de la porte `final` du plan) **seulement quand le plan en nomme une** ; sans pupitre dans le plan, on actionne
de n'importe où (c'est le cas des essais sans plan). Les factices sont dispensés. Refus : `ERROR_TRAHISON_NOT_AT_FINAL_DOOR`.
- [x] Seulement si le plan nomme un pupitre *(par défaut)*
- [ ] Toujours : un plan sans pupitre est refusé à l'enregistrement final, et le bouton du menu du jeu n'ouvre plus la porte
- [ ] Jamais : la zone du pupitre ne sert qu'à l'affichage, on peut actionner de partout

Ta réponse :

## D18. Porte finale : qui est gagnant ?
Choix appliqué : sont gagnants les joueurs vivants à 9 points ou plus au moment de l'ouverture **qui ont réellement franchi la porte** (ils sont entrés dans la `zone_b` pendant la fenêtre, ou s'y tiennent
à la fermeture) ; un joueur à 9 qui n'est pas passé reste derrière et la partie se termine quand même. Les factices passent avec la porte.
- [x] Seuls ceux qui sont passés *(par défaut)*
- [ ] Tous les joueurs à 9 points au moment de l'ouverture, qu'ils soient passés ou non (comme avant T2.5)
- [ ] Ceux qui sont passés, et la fenêtre se prolonge tant qu'un joueur à 9 est encore dans l'antichambre (pas de fin forcée)

Ta réponse :

## D19. Porte finale : quand se ferme-t-elle ?
Choix appliqué : à la fin de `final_door_seconds` (10 par défaut, réglage de la partie), ou **tout de suite** quand tous ceux qu'elle laisse passer sont passés ; le maître du jeu peut aussi la fermer avec `next`.
La fenêtre suit l'horloge, pas la pause de la partie.
- [x] Délai ou tous passés, la pause ne l'arrête pas *(par défaut)*
- [ ] Toujours le délai complet, même si tout le monde est passé
- [ ] Une pause (automatique ou du maître du jeu) gèle aussi la fenêtre

Ta réponse :

## D20. Après la fin de la partie par la porte finale
Choix appliqué : à `ENDED` toutes les portes du plan sont `OPEN`, la porte finale comprise (la partie est finie : le hub se libère ; le client retire tous les faux blocs).
- [x] Tout s'ouvre à la fin *(par défaut)*
- [ ] La porte finale reste fermée pour ceux qui ne sont pas passés (elle « se ferme pour toujours » aussi dans le monde, jusqu'à ce que le maître du jeu libère le hub)

Ta réponse :

## D21. Porte finale sans porte dans le plan
Choix appliqué : un plan sans porte `final` (hub sans plan, essais, factices) n'a rien à attendre : la partie finit tout de suite à l'action et tous les joueurs à 9 sont gagnants, comme avant T2.5.
- [x] Fin immédiate *(par défaut)*
- [ ] La même fenêtre de `final_door_seconds` secondes, sans porte à franchir, puis tous les openers gagnent

Ta réponse :

## D22. Salles des manches précédentes : jouables hors du temps libre ?
Choix appliqué : les salles des manches précédentes ne servent qu'à `FREE_TIME` : pendant `ROOMS` d'une manche on ne joue que la salle de son groupe de la manche en cours, et les salles anciennes n'envoient
rien aux joueurs (elles restent visibles des fantômes, spectateurs et maîtres du jeu). Rien n'y rapporte de point ni n'ouvre de porte, même en temps libre.
- [x] Seulement en temps libre *(par défaut)*
- [ ] Lisibles (documents, indices) en `ROOMS` aussi, sans y agir
- [ ] Jouables aussi pendant `ROOMS` et `VOTE`

Ta réponse :

## D30. Digicode : droit à l'erreur
Choix appliqué : le digicode `keypad` ne se verrouille jamais : on peut taper autant de codes qu'on veut, le compteur « Essais ratés » est seulement affiché. Un code de 3 chiffres se force donc en quelques
minutes ; la difficulté vient de la longueur du code (3 à 6) et de l'endroit où il est caché. Un verrouillage demanderait une horloge dans le mécanisme (aujourd'hui pur et sans temps).
- [x] Pas de verrouillage *(par défaut)*
- [ ] Verrouillage de 10 s après 3 essais ratés d'affilée
- [ ] Après 5 essais ratés, l'étape redonne un code neuf (nouveau tirage) et l'ancien indice change

Ta réponse :

## D31. Digicode : d'où vient le code
Choix appliqué : comme `ring_dial` et `three_dials`, le code est tiré **par l'étape `keypad` elle-même** et produit un indice `code` (type `digits`) que la salle place où elle veut (`gives`, à l'ouverture
de l'étape : le validateur refuse un digicode dont le code n'est montré nulle part). Le code d'un digicode ne peut pas être lu dans l'indice d'une *autre* étape : il faudrait des étapes qui partagent
leur tirage, ce que le moteur ne fait pas (chaque étape tire seule, de la graine et de son identifiant).
- [x] Le code est celui de l'étape `keypad` et se place par `gives` *(par défaut)*
- [ ] Un paramètre `from: <étape>.<indice>` fait lire au digicode le code `digits` d'une autre étape (à construire dans le moteur)

Ta réponse :

## D32. Objets cachés : ce que « collecté » veut dire
Choix appliqué : `pickup_set` est résolu quand **tous les objets que la graine a posés ont été rapportés** : on les ramasse dans le monde (`pick_up`), puis on les utilise sur un emplacement lié au rôle
`deposit` (socle, terminal...) ; l'objet déposé est consommé. Un objet que l'étape n'attend pas est refusé et reste dans le sac. Le simple ramassage ne suffit pas, parce que le mécanisme n'est pas
prévenu d'un ramassage (c'est `RoomService` qui le traite). Sans emplacement lié, l'étape est insoluble : le validateur le refuse.
- [x] Ramassés **et** déposés sur l'emplacement `deposit` *(par défaut)*
- [ ] Ramassés suffit (demande que `RoomService` prévienne le mécanisme à chaque `pick_up`)
- [ ] Déposés dans l'ordre où la salle les a posés

Ta réponse :

## D33. Objets cachés : le total est-il annoncé ?
Choix appliqué : l'écran du dépôt affiche « 2 / 3 » : le nombre total d'objets cachés est connu des joueurs (jamais lesquels). Sans cela, on ne sait pas quand on a fini de chercher.
- [x] Le total est affiché *(par défaut)*
- [ ] Seul le nombre déjà rapporté est affiché
- [ ] Rien : l'écran ne dit que « rapporté » ou « pas encore »

Ta réponse :

## D34. Objets cachés : le document
Choix appliqué : le document est un indice à part (rôle `document`, texte donné en paramètre, pages séparées par `---`), toujours produit quand le paramètre `document` n'est pas vide, et placé par
`gives` (à un emplacement de genre `document`, dès le début ou à la résolution). La graine ne tire que les emplacements `pickup` garnis et les objets, pas la place du document. Un document n'est pas ramassé :
on le lit sur place.
- [x] Document à part, placé par `gives` *(par défaut)*
- [ ] Le document occupe un des emplacements garnis, tiré par la graine (il remplace un objet)
- [ ] Le document se ramasse dans le sac et se relit à volonté

Ta réponse :

## D35. Éditeur : rôles et indices contrôlés à l'enregistrement
Choix appliqué : `GET /events/trahison/mechanisms` donne maintenant `slot_roles` (chaque rôle d'étape et les genres d'emplacement qu'il accepte). Le validateur refuse un rôle inconnu du mécanisme, un emplacement
d'un genre non accepté (`ERROR_TRAHISON_ROOM_BAD_KIND`) et, pour `keypad` et `pickup_set` seulement, une étape dont l'indice nécessaire n'est placé nulle part (`ERROR_TRAHISON_ROOM_BAD_GIVE`,
`info` `missing=...`). `ring_dial` et `three_dials` ne sont pas contrôlés sur l'indice (leurs salles d'essai ne le placent pas toujours).
- [x] Refuser à l'enregistrement *(par défaut)*
- [ ] Seulement avertir dans l'éditeur, le backend accepte
- [ ] Refuser aussi pour `ring_dial` et `three_dials` (indice `symbols` / `code` obligatoire)

Ta réponse :

## Tranché

- **D1 (votes de la boucle finale)** : nouvelle répartition tirée par la graine, sans étape des portes. Vérifié dans le code (`GameService.advance`, cas `FINAL` : `startRound(g, true)` fixe `splitIndex` par `seedSplit`, puis entrée directe en `VOTE`) : rien à changer.
- **D2 + D3, première phrase (pas de porte de couleur = la mort)** : fait. `closeDoors` tue (fantôme, `RoundState.diedAtDoors`, `doors_dead` dans la vue, annonce de l'animatrice `say.doors_dead`) ; le réglage `no_door_penalty` et la constante `Scoring.NO_DOOR_PENALTY` sont supprimés.
- **D3, vote absent** : le comportement des règles était déjà le bon (solo sans vote = « Allier » ; paire : un membre absent, l'autre vote seul pour elle ; aucun des deux = « Allier »). Test ajouté (`CabinsTest.anAbsentVoteIsAllyForASoloAndTheOtherMemberVotesAloneForAPair`).
- **D4 (cabines)** : fait. Une cabine par côté d'une paire de couleurs (inchangé). Entrer dans sa cabine la tient : un solo la verrouille aussitôt, une paire a `cabin_partner_seconds` (30 s, réglable) pour que le partenaire entre, puis elle se verrouille ; tant qu'elle est occupée personne d'autre n'entre (porte fermée aux autres, ouverte pour le groupe pendant la fenêtre). Calculé par le backend (`CabinLock`, `GameService.updateCabins`, `DoorState.openFor`) d'après les zones ; la touche V (`close_cabin`) reste comme « verrouiller maintenant » (le partenaire est alors laissé dehors).
- **D5 (sortie de cabine)** : fait. Dès que le vote du groupe est déposé la porte de la cabine s'ouvre pour ceux qui sont dedans ; sans vote, ils sortent à la fin de `VOTE`. Plus de « seul le maître du jeu ouvre ».
- **D6 (cabines obligatoires)** : gardé tel quel (`cabins_required`, vrai par défaut ; sans plan ou sans cabine le vote reste libre).
- **D7 (maîtres du jeu)** : fait. Un admin nomme dans le lobby, de 0 à autant de maîtres du jeu qu'on veut (`set_game_master`, `lobby_members` dans la vue, boutons Nommer / Retirer dans l'écran MJ) ; seuls les maîtres nommés envoient des actions MJ (`NOT_GAME_MASTER`) ; en `LOBBY` un admin prépare la partie ; la vue MJ va à chaque maître du jeu ; sans maître du jeu la partie se joue seule.
- **D8 (salles pendant le temps libre)** : fait. En `FREE_TIME`, tout joueur vivant agit dans toute salle de la manche (champ `group` des actions de salle) et reçoit leurs vues ; `replay` remet une salle à un état neuf avec une nouvelle graine (déduite de la précédente et du nombre de rejeux), sans point ni porte ouverte (une salle finie là n'est jamais « résolue ») ; bouton « Rejouer cette salle » dans l'inventaire. Voir `protocole.md`, « Temps libre : visiter et rejouer ». **Ajout le 2026-11-16 : les salles des manches précédentes sont gardées** (indexées par manche et groupe, enregistrées avec leur manche, remises après un redémarrage), visitables et rejouables en temps libre avec les mêmes règles ; une action de salle porte `group` et `round` (sans `round`, la manche en cours). Voir D22.

- **D9 carte** : prévue comme une nouvelle étape, [`etapes/T11-carte.md`](etapes/T11-carte.md), placée après T4 (tirage et collage). Questions ouvertes : D14 à D16.
