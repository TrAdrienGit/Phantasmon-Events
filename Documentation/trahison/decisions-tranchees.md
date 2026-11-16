# Décisions tranchées

> Points réglés par la propriétaire et appliqués. Un agent qui applique une décision **ajoute** une ligne ici
> (`cat >>`) et retire la section de [`decisions.md`](decisions.md).


- **D1 (votes de la boucle finale)** : nouvelle répartition tirée par la graine, sans étape des portes. Vérifié dans le code (`GameService.advance`, cas `FINAL` : `startRound(g, true)` fixe `splitIndex` par `seedSplit`, puis entrée directe en `VOTE`) : rien à changer.
- **D2 + D3, première phrase (pas de porte de couleur = la mort)** : fait. `closeDoors` tue (fantôme, `RoundState.diedAtDoors`, `doors_dead` dans la vue, annonce de l'animatrice `say.doors_dead`) ; le réglage `no_door_penalty` et la constante `Scoring.NO_DOOR_PENALTY` sont supprimés.
- **D3, vote absent** : le comportement des règles était déjà le bon (solo sans vote = « Allier » ; paire : un membre absent, l'autre vote seul pour elle ; aucun des deux = « Allier »). Test ajouté (`CabinsTest.anAbsentVoteIsAllyForASoloAndTheOtherMemberVotesAloneForAPair`).
- **D4 (cabines)** : fait. Une cabine par côté d'une paire de couleurs (inchangé). Entrer dans sa cabine la tient : un solo la verrouille aussitôt, une paire a `cabin_partner_seconds` (30 s, réglable) pour que le partenaire entre, puis elle se verrouille ; tant qu'elle est occupée personne d'autre n'entre (porte fermée aux autres, ouverte pour le groupe pendant la fenêtre). Calculé par le backend (`CabinLock`, `GameService.updateCabins`, `DoorState.openFor`) d'après les zones ; la touche V (`close_cabin`) reste comme « verrouiller maintenant » (le partenaire est alors laissé dehors).
- **D5 (sortie de cabine)** : fait. Dès que le vote du groupe est déposé la porte de la cabine s'ouvre pour ceux qui sont dedans ; sans vote, ils sortent à la fin de `VOTE`. Plus de « seul le maître du jeu ouvre ».
- **D6 (cabines obligatoires)** : gardé tel quel (`cabins_required`, vrai par défaut ; sans plan ou sans cabine le vote reste libre).
- **D7 (maîtres du jeu)** : fait. Un admin nomme dans le lobby, de 0 à autant de maîtres du jeu qu'on veut (`set_game_master`, `lobby_members` dans la vue, boutons Nommer / Retirer dans l'écran MJ) ; seuls les maîtres nommés envoient des actions MJ (`NOT_GAME_MASTER`) ; en `LOBBY` un admin prépare la partie ; la vue MJ va à chaque maître du jeu ; sans maître du jeu la partie se joue seule.
- **D8 (salles pendant le temps libre)** : fait. En `FREE_TIME`, tout joueur vivant agit dans toute salle de la manche (champ `group` des actions de salle) et reçoit leurs vues ; `replay` remet une salle à un état neuf avec une nouvelle graine (déduite de la précédente et du nombre de rejeux), sans point ni porte ouverte (une salle finie là n'est jamais « résolue ») ; bouton « Rejouer cette salle » dans l'inventaire. Voir `protocole.md`, « Temps libre : visiter et rejouer ». **Ajout le 2026-11-16 : les salles des manches précédentes sont gardées** (indexées par manche et groupe, enregistrées avec leur manche, remises après un redémarrage), visitables et rejouables en temps libre avec les mêmes règles ; une action de salle porte `group` et `round` (sans `round`, la manche en cours). Voir D22.

- **D9 carte** : prévue comme une nouvelle étape, [`etapes/T11-carte.md`](etapes/T11-carte.md), placée après T4 (tirage et collage). Questions ouvertes : D14 à D16.
- **D14 à D16 carte** : curseurs visibles des MJ et spectateurs ; notes montrées au bilan ; un fantôme voit comme un spectateur et garde ses notes. Reporté dans `etapes/T11-carte.md`.
- **D12 (le maître du jeu peut jouer)** : fait. Un compte peut être maître du jeu et joueur : rôle secret, `game_masters` ne le montre qu'aux maîtres du jeu, `viewer.is_game_master`, vue de joueur plus bloc `gm`, mort d'un maître du jeu qui joue = victoire des huit autres (`Outcome.gameMasterKilled`, crochet `GameService.endIfGameMasterKilled`, à rappeler dans T6). Le maître du jeu qui ne fait que superviser reste possible.
- **D17 (pupitre de la porte finale)** : fait. Un plan complet refuse une porte finale sans pupitre (`MISSING_FIELD`, `field=threshold`) ; sans plan (essais, factices) on actionne de partout.
- **D18 + D19 (gagnants et fermeture de la porte finale)** : fait. `final_door_seconds` vaut 60 par défaut, la fenêtre dure toujours en entier même si tous sont passés ; compte à rebours pour tous (HUD et hôtesse à 30, 10, 5 s), annonce globale « la porte 9 est ouverte » ; gagnants = ceux qui sont passés.
- **D20 (fin de partie par la porte finale)** : fait (option par défaut) : toutes les portes s'ouvrent à `ENDED`.
- **D21 (porte finale absente du plan)** : fait. Même fenêtre de `final_door_seconds` sans porte à franchir, puis tous les openers gagnent.
- **D22 (salles hors du temps libre)** : fait. En `ROOMS` et `VOTE`, un joueur dont le groupe a terminé sa salle de la manche en cours est libre de visiter et rejouer toutes les salles (`viewer.can_roam`), règles du temps libre inchangées (aucun point, rien ne s'ouvre).
- **D30 (digicode)** : fait. Trois essais ratés d'affilée verrouillent le digicode 10 s (état et horloge injectée dans `Keypad`, vue `lock_ms`, événement `wrong` avec `locked` et `ms`, l'afficheur le montre).
- **D31 (code du digicode)** : défaut gardé : le code est celui de l'étape `keypad`, placé par `gives`.
- **D32 (objets cachés)** : fait, ramasser suffit. Crochet `Mechanism.onPickUp(state, player, role, item)` appelé par `RoomService` à chaque `pick_up` d'un objet d'une étape ouverte ; plus de dépôt, plus de rôle d'emplacement, événement `collect`. Voir D61.
- **D33 (total annoncé)** : défaut gardé : le total est dans la vue.
- **D34 (document des objets cachés)** : défaut gardé : indice à part placé par `gives`.
- **D35 (contrôle à l'enregistrement)** : défaut gardé : rôles et indices contrôlés.
- **D36 (décodeur)** : fait. Essais épuisés : même combinaison, historique effacé.
- **D37 (schéma du câblage)** : fait. L'indice `colors` `scheme` reste, et un indice `document` `scheme_text` (« 1 : @color:red ») s'y ajoute ; la salle place l'un ou l'autre, ou les deux. Voir D63.
- **D38 (couleurs de fil)** : fait. `yellow` (losange) et `white` (anneau) ajoutés pour les fils seulement (`WireColor`, `TrahisonPalette`, charte section 3) ; `colors` va de 2 à 5. Voir D60.
- **D39 (câblage plein mais faux)** : fait. Un court-circuit fait sauter tous les fils.
- **D40 à D43 (trois leviers)** : défauts gardés (bail renouvelable, ordre et délai tirés, `solo` pour l'essai, pas de contrôle de la taille du groupe).
- **D76 (ordre des places)** : c'était un défaut, pas une question. L'export et le backend utilisent le même ordre, celui du backend (ordre du plan) : le tirage porte l'identifiant de place de chaque salle et l'export associe salle et place par cet identifiant (`TrahisonExport.pair`, test `PlaceCheckTest`). Seule la commande manuelle `export <salles>` garde l'ordre « manche puis identifiant de place ».
- **D10** Une cabine libre : qui peut y entrer, et un intrus : choix par défaut confirmé (2026-11-16).
- **D11** Maître du jeu : aucun maître nommé en mode « Maître du jeu » : choix par défaut confirmé (2026-11-16).
- **D13** Mort aux portes : ce qui reste de la personne : choix par défaut confirmé (2026-11-16).
- **D45** Maître du jeu qui joue : comment il devient joueur : choix par défaut confirmé (2026-11-16).
- **D47** Maître du jeu qui joue : sa vue de salle : choix par défaut confirmé (2026-11-16).
- **D48** Maître du jeu qui joue : ses actions de supervision restent-elles discrètes : choix par défaut confirmé (2026-11-16).
- **D49** Maître du jeu qui joue : révéler son nom à la fin : choix par défaut confirmé (2026-11-16).
- **D54** Porte finale : qui est « passé » : choix par défaut confirmé (2026-11-16).
- **D55** Porte finale : un deuxième actionnement pendant la fenêtre : choix par défaut confirmé (2026-11-16).
- **D56** Porte finale : plan sans porte, annonce de la fin : choix par défaut confirmé (2026-11-16).
- **D57** Salles libres : une salle non terminée à l'échéance : choix par défaut confirmé (2026-11-16).
- **D58** Salles libres : sa propre salle pendant `VOTE` : choix par défaut confirmé (2026-11-16).
- **D59** Salles libres : cabine de vote et salles : choix par défaut confirmé (2026-11-16).
- **D60** Câblage : `colors` plus grand que `wires` : choix par défaut confirmé (2026-11-16).
- **D63** Câblage : le schéma en texte et la langue : choix par défaut confirmé (2026-11-16).
- **D64** Salles : ce que la mesure change à la difficulté affichée : choix par défaut confirmé (2026-11-16).
- **D65** Aides : où se règlent les délais : choix par défaut confirmé (2026-11-16).
- **D68** Mesures : « médiane d'une salle » : choix par défaut confirmé (2026-11-16).
- **D70** Tirage : la taille des groupes et `three_levers` : choix par défaut confirmé (2026-11-16).
- **D71** Tirage : le « mécanisme principal » d'une salle : choix par défaut confirmé (2026-11-16).
- **D72** Tirage : dans quel ordre relâcher les contraintes : choix par défaut confirmé (2026-11-16).
- **D74** Tirage : réglages par défaut : choix par défaut confirmé (2026-11-16).
- **D77** Places : qui convertit les positions des emplacements : choix par défaut confirmé (2026-11-16).
- **D79** Vérification du collage : quand : choix par défaut confirmé (2026-11-16).
- **D90** Soirée : l'outil de collage : choix par défaut confirmé (2026-11-16).
- **D91** Soirée : quand tirer et coller : choix par défaut confirmé (2026-11-16).
- **D84** Source du tirage pour le bouton « Exporter » : choix par défaut confirmé (2026-11-16).
- **D96** Mort par 0 point : quand elle s'applique : choix par défaut confirmé (2026-11-16).
- **D98** Position des joueurs déconnectés : choix par défaut confirmé (2026-11-16).
- **D100** Réglage `announceDeaths` : où le changer : choix par défaut confirmé (2026-11-16).
- **D101** Durée d'attente d'un déconnecté en mode automatique : choix par défaut confirmé (2026-11-16).
- **D103** Réglage de l'affichage des fantômes : choix par défaut confirmé (2026-11-16).
- **D104** Fantôme en mode caché : se voit-il lui-même et voit-il les autres ? : choix par défaut confirmé (2026-11-16).
- **D105** Marque « (fantôme) » visible de tous : choix par défaut confirmé (2026-11-16).
