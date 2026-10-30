# Plan : les tournois (Phantasmon Events)

> Rédigé le 2026-10-29 à la demande d'Adrien. Première brique du moteur d'événements, après les hubs miroirs (D-38).

## 1. Deux types de tournois

| | Weekly | Tournoi à arbre |
|---|---|---|
| Fréquence | Chaque semaine environ | Événements plus rares, plus sérieux |
| Forme | **Rondes suisses** | **Double élimination** : winner bracket et loser bracket (repêchage) |
| Inscrits | Sans limite | Sans limite (l'arbre s'adapte, exemptions au 1er tour) |
| Durée | ~3 h de jeu (4 h avec présentation et check-in) | Selon le nombre d'inscrits |
| Affichage | Rondes et classement par points | Arbre |

## 2. Choix d'Adrien (2026-10-29)

| Sujet | Choix |
|---|---|
| Combats | Combats **Ghost** de Phantasmon ; le backend relève lui-même la victoire ou la défaite |
| Format d'un match | **Réglable par tournoi** : Bo1, Bo3 ou Bo5 |
| Lancement | **Invitation automatique** : à l'annonce d'une ronde, le backend invite les deux joueurs de chaque match ; le lobby Ghost habituel s'ouvre |
| Rondes d'un weekly | **Nombre calculé** (log₂ du nombre de joueurs, arrondi au-dessus), que l'admin peut changer : ajouter une ronde, arrêter plus tôt |
| Passage d'une ronde à l'autre | **Jamais automatique** : un admin l'annonce |
| Interfaces | **En jeu** (écrans Phantasmon) **et page web** publique en lecture seule (classement, rondes, arbre), pour Discord ou un stream |
| Format de combat | **Un format Smogon obligatoire** par tournoi, parmi les 19 du lobby Ghost (National Dex en tête : NatDex, Ubers, UU, RU, Monotype, LC, 1v1, AG ; puis OU, Ubers, UU, RU, NU, PU, ZU, LC, Monotype, 1v1, AG) ; imposé dans le lobby, équipes vérifiées |
| Grande finale | Le joueur venu du loser bracket doit battre **deux fois** celui du winner bracket (finale de remise à zéro) |
| Forfaits | Déclarés **par un admin** seulement |
| Annonces | Dans le chat des inscrits **et au milieu de l'écran** (titre et sous-titre façon `/title`) |
| Classement de saison | Non |
| Admins | Ceux de Phantasmon (`admins.txt` : MystAria_, TheMashen) |

## 3. Règles

### Ronde suisse (weekly)

- Victoire de match : 1 point, défaite : 0.
- Ronde 1 : appariement au hasard. Ensuite, les joueurs à égalité de points s'affrontent (« groupes de points ») ; si
  un groupe est impair, le dernier descend dans le groupe suivant.
- Deux joueurs ne se rencontrent jamais deux fois tant que c'est possible.
- Nombre impair de joueurs : un joueur est **exempté** (bye), il marque 1 point ; jamais deux fois le même, et en
  priorité le moins bien classé.
- Départage du classement, dans l'ordre : points, puis **Buchholz** (somme des points des adversaires rencontrés),
  puis pourcentage de victoires des adversaires, puis confrontation directe.
- Un joueur retiré (kick, abandon) n'est plus apparié ; ses matchs déjà joués comptent pour ses adversaires.

### Double élimination (tournoi à arbre)

- Placement au hasard (ou têtes de série plus tard). Arbre complété à la puissance de 2 supérieure par des
  exemptions au 1er tour.
- Le perdant d'un match du winner bracket descend dans le loser bracket ; deux défaites : éliminé.
- **Grande finale** : le vainqueur du winner bracket contre celui du loser bracket. Si ce dernier gagne, une
  **finale de remise à zéro** se joue (les deux ont alors une défaite).
- Les matchs s'enchaînent dès que les deux joueurs sont connus, mais un admin lance chaque tour (même règle que
  les rondes : rien d'automatique).

## 4. Déroulement

1. **Création** (admin) : nom, type (weekly / arbre), Bo1/Bo3/Bo5, format Smogon, date et heure
   de début, ouverture et fermeture des inscriptions.
2. **Inscriptions** : les joueurs s'inscrivent dans la fenêtre prévue, depuis l'écran en jeu ; liste des inscrits
   visible par tous.
3. **Check-in** (avant le début) : chaque inscrit confirme sa présence ; les absents sont retirés au lancement.
4. **Ronde / tour** : l'admin annonce ; le backend apparie, invite les deux joueurs de chaque match (lobby Ghost,
   format imposé) et relève les résultats. Les matchs en Bo3/Bo5 relancent un combat jusqu'à la victoire.
5. **Fin de ronde** : quand tous les matchs ont un résultat, l'admin voit le classement à jour et annonce la
   suivante.
6. **Fin du tournoi** : classement final (weekly) ou vainqueur (arbre), gardé dans l'historique.

### Pouvoirs des admins

Inscrire ou désinscrire un joueur, le retirer (kick) en plein tournoi, **modifier le résultat d'un match**, déclarer
un forfait (joueur absent à son match), relancer une invitation, annoncer la ronde suivante, ajouter ou supprimer
une ronde, mettre le tournoi en pause, le clore ou l'annuler.

### Annonces

Ronde annoncée, son adversaire, résultats et fin du tournoi : dans le chat des inscrits, et en titre au milieu de
l'écran pour ce qui concerne le joueur (« Ronde 3 » / « contre MystAria_ »).

### Cas particuliers

- **Joueur absent à son match** : l'admin déclare le forfait.
- **Déconnexion en plein combat** : le combat Ghost se termine par un match nul ; l'admin tranche ou fait rejouer.

## 5. Architecture

- **Backend** (`Phantasmon-Backend`, module `events/tournament`) : tables `tournaments`, `tournament_players`,
  `tournament_rounds`, `tournament_matches`, `tournament_games` ; appariements suisses et arbre calculés côté
  backend ; il écoute les fins de combats Ghost (`LiveBattleService`) pour remplir les matchs ; il pousse les
  changements aux clients par WebSocket (`EventsTournament*`).
- **Client** (`Phantasmon-Events`) : écrans en jeu (liste des tournois, inscription, inscrits, rondes, classement,
  arbre, panneau admin) ; commandes `/events tournament …` en secours.
- **Page web** : servie par le backend (accessible publiquement par l'adresse Tailscale Funnel déjà en place),
  lecture seule, mise à jour en direct.

## 6. Phases

| Phase | Contenu |
|---|---|
| T1 | Backend : modèle de données, création, inscriptions et check-in, commandes admin, tests unitaires des appariements suisses |
| T2 | Weekly jouable : annonce de ronde, invitations automatiques, relevé des résultats (Bo1/3/5), classement et départages, pouvoirs admin |
| T3 | Écrans en jeu : liste, inscription, rondes, classement, panneau admin |
| T4 | Tournoi à arbre : double élimination, écran de l'arbre |
| T5 | Page web publique (classement, rondes, arbre, en direct) |

Chaque phase se termine par une validation en jeu par Adrien.

## 7. État (2026-10-30)

- T1 (création, inscriptions, check-in) et T3 partiel (menu des tournois, formulaire de création) : **validés**.
- T2 (weekly jouables) : **validé à deux comptes**. Ajustements d'Adrien : équipe vérifiée au check-in ; une ronde
  **appaire** les joueurs sans ouvrir de lobby, ils lancent leur combat eux-mêmes (seulement entre eux) ; adversaire en
  surbrillance et flèche à l'écran ; dates JJ-MM-AAAA ; effacement d'un tournoi annulé.
- Reste : test grandeur nature (plus de joueurs : exemptions, appariements par points), T4 (double élimination),
  T5 (page web).

