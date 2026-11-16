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
- [x] Comme appliqué *(par défaut)*
- [ ] Un maître du jeu peut aussi jouer
- [ ] On peut nommer ou retirer un maître du jeu en cours de partie

Ta réponse :

## D13. Mort aux portes : ce qui reste de la personne
Choix appliqué : un joueur mort faute d'avoir passé une porte devient fantôme tout de suite (avant les salles), garde ses points sur son bracelet, reste dans son groupe de la
répartition (le groupe a un fantôme, son vote est « Allier » par défaut, son bracelet peut encore être trahi) et n'est plus compté aux portes des manches suivantes.
- [x] Comme appliqué *(par défaut)*
- [ ] Son bracelet tombe à 0 point
- [ ] Il perd aussi sa place dans le groupe : la salle et le vote se jouent sans lui

Ta réponse :

## D14. Carte : curseurs des autres pour les maîtres du jeu et les spectateurs
Le curseur d'un joueur est privé. Faut-il faire une exception pour ceux qui regardent la partie ?
- [x] Personne ne voit le curseur d'un autre *(par défaut)*
- [ ] Les maîtres du jeu voient les curseurs de tous les joueurs
- [ ] Les maîtres du jeu et les spectateurs les voient

Ta réponse :

## D15. Carte : annotations et bloc-notes à la fin de la partie
- [x] Effacés à la fin de la partie *(par défaut)*
- [ ] Gardés, et visibles par leur auteur au bilan de la partie
- [ ] Gardés et montrés à tout le monde au bilan

Ta réponse :

## D16. Carte : un fantôme garde-t-il sa carte ?
- [x] Oui, il la garde, avec ses annotations *(par défaut)*
- [ ] Il voit la carte complète, comme un spectateur
- [ ] Il la perd

Ta réponse :

## Tranché

- **D1 (votes de la boucle finale)** : nouvelle répartition tirée par la graine, sans étape des portes. Vérifié dans le code (`GameService.advance`, cas `FINAL` : `startRound(g, true)` fixe `splitIndex` par `seedSplit`, puis entrée directe en `VOTE`) : rien à changer.
- **D2 + D3, première phrase (pas de porte de couleur = la mort)** : fait. `closeDoors` tue (fantôme, `RoundState.diedAtDoors`, `doors_dead` dans la vue, annonce de l'animatrice `say.doors_dead`) ; le réglage `no_door_penalty` et la constante `Scoring.NO_DOOR_PENALTY` sont supprimés.
- **D3, vote absent** : le comportement des règles était déjà le bon (solo sans vote = « Allier » ; paire : un membre absent, l'autre vote seul pour elle ; aucun des deux = « Allier »). Test ajouté (`CabinsTest.anAbsentVoteIsAllyForASoloAndTheOtherMemberVotesAloneForAPair`).
- **D4 (cabines)** : fait. Une cabine par côté d'une paire de couleurs (inchangé). Entrer dans sa cabine la tient : un solo la verrouille aussitôt, une paire a `cabin_partner_seconds` (30 s, réglable) pour que le partenaire entre, puis elle se verrouille ; tant qu'elle est occupée personne d'autre n'entre (porte fermée aux autres, ouverte pour le groupe pendant la fenêtre). Calculé par le backend (`CabinLock`, `GameService.updateCabins`, `DoorState.openFor`) d'après les zones ; la touche V (`close_cabin`) reste comme « verrouiller maintenant » (le partenaire est alors laissé dehors).
- **D5 (sortie de cabine)** : fait. Dès que le vote du groupe est déposé la porte de la cabine s'ouvre pour ceux qui sont dedans ; sans vote, ils sortent à la fin de `VOTE`. Plus de « seul le maître du jeu ouvre ».
- **D6 (cabines obligatoires)** : gardé tel quel (`cabins_required`, vrai par défaut ; sans plan ou sans cabine le vote reste libre).
- **D7 (maîtres du jeu)** : fait. Un admin nomme dans le lobby, de 0 à autant de maîtres du jeu qu'on veut (`set_game_master`, `lobby_members` dans la vue, boutons Nommer / Retirer dans l'écran MJ) ; seuls les maîtres nommés envoient des actions MJ (`NOT_GAME_MASTER`) ; en `LOBBY` un admin prépare la partie ; la vue MJ va à chaque maître du jeu ; sans maître du jeu la partie se joue seule.
- **D8 (salles pendant le temps libre)** : fait. En `FREE_TIME`, tout joueur vivant agit dans toute salle de la manche (champ `group` des actions de salle) et reçoit leurs vues ; `replay` remet une salle à un état neuf avec une nouvelle graine (déduite de la précédente et du nombre de rejeux), sans point ni porte ouverte (une salle finie là n'est jamais « résolue ») ; bouton « Rejouer cette salle » dans l'inventaire. Les salles des manches précédentes ne sont pas gardées. Voir `protocole.md`, « Temps libre : visiter et rejouer ».

- **D9 carte** : prévue comme une nouvelle étape, [`etapes/T11-carte.md`](etapes/T11-carte.md), placée après T4 (tirage et collage). Questions ouvertes : D14 à D16.
