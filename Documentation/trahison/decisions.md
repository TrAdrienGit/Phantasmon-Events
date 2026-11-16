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

Ta réponse : ok

## D11. Maître du jeu : aucun maître nommé en mode « Maître du jeu »
Choix appliqué : le mode `AUTO` marche sans maître du jeu ; le mode `GAME_MASTER` refuse de démarrer sans au moins un maître du jeu nommé (personne ne ferait avancer la partie).
- [x] Comme appliqué *(par défaut)*
- [ ] Le démarrage passe quand même et un admin du lobby peut faire avancer la partie
- [ ] Autre :

Ta réponse : ok

## D13. Mort aux portes : ce qui reste de la personne
Choix appliqué : un joueur mort faute d'avoir passé une porte devient fantôme tout de suite (avant les salles), garde ses points sur son bracelet, reste dans son groupe de la
répartition (le groupe a un fantôme, son vote est « Allier » par défaut, son bracelet peut encore être trahi) et n'est plus compté aux portes des manches suivantes.
- [x] Comme appliqué *(par défaut)*
- [ ] Son bracelet tombe à 0 point
- [ ] Il perd aussi sa place dans le groupe : la salle et le vote se jouent sans lui

Ta réponse : ok

## D44. Maître du jeu qui joue : qui gagne quand les autres le tuent
Choix appliqué : les huit autres participants gagnent, vivants ou morts (« tous les autres gagnent automatiquement »).
- [ ] Les huit autres, vivants ou morts *(par défaut)*
- [x] Seulement les autres encore vivants à ce moment

Ta réponse : fait

## D45. Maître du jeu qui joue : comment il devient joueur
Choix appliqué : l'admin le nomme dans le lobby (avant ou après son `join`) ; il s'inscrit comme tout le monde et prend un des neuf sièges. Tant qu'il n'a pas rejoint, son rôle de maître du jeu est visible dans la liste du lobby (il y reste public pour ceux qui supervisent).
- [x] Nommé puis inscrit, visible seulement avant son inscription *(par défaut)*
- [ ] Un admin le désigne en secret : il n'apparaît dans aucune liste, même avant son inscription
- [ ] Il s'inscrit d'abord, puis seul un message à l'admin le fait maître du jeu

Ta réponse : fait

## D46. Maître du jeu qui joue : plusieurs maîtres du jeu qui jouent
Choix appliqué : tout maître du jeu inscrit est un rôle secret ; la partie finit dès qu'un seul d'entre eux meurt, et les gagnants sont tous les participants qui ne sont pas maîtres du jeu.
- [ ] La première mort d'un maître du jeu qui joue finit la partie *(par défaut)*
- [x] Un seul maître du jeu peut jouer à la fois (refusé sinon)
- [ ] La partie finit quand tous les maîtres du jeu qui jouent sont morts

Ta réponse : fait

## D47. Maître du jeu qui joue : sa vue de salle
Choix appliqué : il reçoit la vue de salle d'un joueur (pas celle du maître du jeu, qui montre les solutions), pour ne pas se trahir par ce qu'il sait.
- [x] Vue de joueur, la sienne seule *(par défaut)*
- [ ] Vue maître du jeu de toutes les salles (il voit les solutions)
- [ ] Un bouton pour passer de l'une à l'autre

Ta réponse : fait

## D48. Maître du jeu qui joue : ses actions de supervision restent-elles discrètes
Choix appliqué : aucun texte ni vue ne nomme l'auteur d'une action de maître du jeu ; en revanche une pause, une prolongation ou une porte forcée se voient (l'état change), ce qui montre qu'un maître du jeu existe sans dire qui.
- [x] L'état change visiblement, l'auteur reste secret *(par défaut)*
- [ ] Aucune trace visible : pas de bandeau « pause », la pause est dite « technique » sans cause
- [ ] Les pouvoirs qui se voient (pause, porte forcée) sont retirés au maître du jeu qui joue

Ta réponse : fait

## D49. Maître du jeu qui joue : révéler son nom à la fin
Choix appliqué : quand il est tué, l'annonce finale dit son nom (`say.game_master_killed`). S'il n'est pas tué, il n'est pas révélé (la partie finit par la porte finale ou un `end`).
- [x] Nom annoncé seulement s'il est tué *(par défaut)*
- [ ] Nom annoncé à la fin de toute partie
- [ ] Jamais annoncé

Ta réponse : fait

## D50. Maître du jeu qui joue : sa propre mort par ses pouvoirs
Choix appliqué : ses pouvoirs ne peuvent pas le tuer : `set_points` à 0 sur son propre bracelet ne tue qu'au prochain vote, comme pour tout joueur, et la victoire des autres s'applique alors aussi.
- [ ] Pas d'exception, la règle s'applique comme pour un autre mort *(par défaut)*
- [x] Sa propre mort par ses pouvoirs ne donne pas la victoire aux autres
- [ ] Ses pouvoirs ne peuvent pas toucher son propre bracelet

Ta réponse : fait

## D51. Maître du jeu qui joue : un maître du jeu mort peut-il encore agir
Choix appliqué : la mort d'un maître du jeu qui joue finit la partie : il n'y a donc pas de fantôme-maître. Le point reste à reprendre si une autre règle (T6) le laisse en vie après sa « mort ».
- [ ] La partie finit toujours à sa mort *(par défaut)*
- [x] Il devient fantôme et garde ses pouvoirs, les autres ont gagné quand même

Ta réponse : fait

## D52. Porte finale : durée maximale du réglage
Choix appliqué : `final_door_seconds` vaut 60 par défaut et le backend le borne à 1..120 secondes (réglage de la partie).
- [ ] Borné à 120 secondes *(par défaut)*
- [x] Borné à 300 secondes
- [ ] Aucun plafond

Ta réponse : fait

## D53. Porte finale : les rappels du compte à rebours
Choix appliqué : l'hôtesse rappelle le compte à rebours chez chacun à 30, 10 et 5 secondes (affichage du client, sur l'heure du serveur) en plus de l'annonce d'ouverture ; le HUD affiche les secondes tout du long.
- [ ] 30, 10 et 5 secondes *(par défaut)*
- [x] Tous les 10 secondes puis chaque seconde sur les 5 dernières
- [ ] Aucun rappel, le HUD suffit

Ta réponse : fait

## D54. Porte finale : qui est « passé »
Choix appliqué : est passé celui qui est entré dans la `zone_b` de la porte pendant la fenêtre, ou qui s'y tient à la fermeture ; un opener qui y est entré puis en est ressorti reste compté.
- [x] Entré à un moment de la fenêtre, même s'il est ressorti *(par défaut)*
- [ ] Seulement ceux qui s'y tiennent à la fermeture

Ta réponse : fait

## D55. Porte finale : un deuxième actionnement pendant la fenêtre
Choix appliqué : actionner de nouveau le pupitre pendant la fenêtre ne fait rien (ni prolongation ni nouvelle fenêtre), même par un joueur qui n'était pas opener et qui a gagné des points depuis.
- [x] Sans effet *(par défaut)*
- [ ] Il ajoute un opener (un 9 tardif peut passer lui aussi)

Ta réponse : fait

## D56. Porte finale : plan sans porte, annonce de la fin
Choix appliqué : sans porte dans le plan, la fenêtre est la même mais rien n'est à franchir : au terme, tous les openers gagnent. La phrase d'ouverture parle de « la porte 9 » même sans porte construite (l'essai est un banc d'essai).
- [x] Même annonce, tous gagnent à la fin *(par défaut)*
- [ ] Annonce adaptée « le compte à rebours commence » sans parler d'une porte

Ta réponse : fait

## D57. Salles libres : une salle non terminée à l'échéance
Choix appliqué : un groupe dont la salle n'est pas terminée ne peut pas visiter les autres salles avant le temps libre, même si l'échéance de `ROOMS` approche ; la salle jamais terminée n'ouvre donc pas la libre circulation.
- [x] Pas de libre circulation sans salle terminée *(par défaut)*
- [ ] Libre circulation dès qu'il reste moins de N minutes de `ROOMS`
- [ ] Libre circulation pour tous dès que la première salle est finie

Ta réponse : fait

## D58. Salles libres : sa propre salle pendant `VOTE`
Choix appliqué : pendant `VOTE`, un joueur libre de visiter peut rejouer les autres salles et aussi la sienne, comme en temps libre (rien ne compte) ; la salle de sa manche ne rapporte plus rien.
- [x] Toutes les salles, la sienne comprise, sans effet sur la partie *(par défaut)*
- [ ] Seulement les autres salles pendant `VOTE`

Ta réponse : fait

## D59. Salles libres : cabine de vote et salles
Choix appliqué : un joueur libre de visiter une salle pendant `VOTE` peut en même temps avoir à voter dans sa cabine : rien ne l'empêche de visiter une salle avant ou après son vote.
- [x] Aucune restriction de lieu *(par défaut)*
- [ ] Un joueur enfermé dans sa cabine (verrouillée) ne peut pas visiter ailleurs jusqu'au vote

Ta réponse : fait

## D60. Câblage : `colors` plus grand que `wires`
Choix appliqué : avec D38 (5 couleurs), `colors` ne peut pas dépasser `wires` (chaque couleur du schéma est employée au moins une fois, donc il faut au moins autant de fils que de couleurs). L'éditeur et le validateur refusent `colors > wires` (`colors: at most wires`) ; le moteur, lui, borne en silence.
- [x] Refuser `colors > wires` dans le validateur *(par défaut)*
- [ ] Accepter et borner en silence (`colors` devient `wires`)
- [ ] Autoriser des couleurs absentes du schéma mais présentes à droite (leurres)

Ta réponse : fait

## D61. Objets cachés : un compteur sans dépôt
Choix appliqué : sans emplacement de dépôt (D32), l'écran compteur « n / total » de `pickup_set` ne s'ouvre plus par un emplacement ; il ne reste ouvert que pour une salle ancienne qui lie encore `deposit`. Les joueurs ne voient donc plus combien d'objets restent à trouver (le total annoncé par D33 n'est plus visible).
- [ ] Rien de plus : on cherche sans compteur *(par défaut)*
- [x] Une ligne du bracelet / de la salle (HUD) « objets : 2 / 3 », portée par la vue de l'étape
- [ ] L'objet ramassé s'annonce dans la discussion de la salle (« un objet de plus, il en reste 1 »)

Ta réponse : fait

## D62. Digicode : réglage du verrouillage
Choix appliqué : 3 essais et 10 s sont des constantes du mécanisme (`LOCK_AFTER`, `LOCK_SECONDS`), pas des paramètres de l'étape ; le verrou est commun à tout le groupe et le compteur d'échecs repart de 0 au verrouillage.
- [ ] Constantes, identiques pour toutes les salles *(par défaut)*
- [ ] Paramètres `lockAfter` (2 à 6) et `lockSeconds` (5 à 60) de l'étape, pour régler la difficulté salle par salle
- [x] Le verrou s'allonge à chaque répétition (10 s, 20 s, 40 s)

Ta réponse : fait

## D63. Câblage : le schéma en texte et la langue
Choix appliqué : l'indice `scheme_text` est un document dont les lignes portent des jetons neutres (`1 : @color:red`) ; le client les remplace par le nom de la couleur dans la langue du joueur (clés `phantasmon_events.trahison.color.<id>`). Un texte fixe écrit par le backend serait dans une seule langue.
- [x] Jetons `@color:<id>` traduits par le client *(par défaut)*
- [ ] Texte fixe en français écrit par le backend
- [ ] Le concepteur de la salle écrit lui-même le texte (paramètre `schemeText`), sans lien avec la graine

Ta réponse : fait

## D64. Salles : ce que la mesure change à la difficulté affichée
Choix appliqué : dès 3 mesures d'une étape, le **temps attendu** affiché (`expected_seconds` de la liste des salles) prend la médiane mesurée de cette étape ; la **difficulté** (1 à 3 par mécanisme, sommée) ne change pas et un champ `measured` dit que le temps est mesuré.
- [x] Seul le temps attendu devient mesuré ; la note de difficulté reste celle des mécanismes *(par défaut)*
- [ ] La difficulté affichée est aussi déduite du temps mesuré (par seuils, ex. moins de 5 min = 1, plus de 12 min = 3)
- [ ] La mesure remplace aussi les valeurs enregistrées dans la définition à chaque enregistrement

Ta réponse : fait

## D65. Aides : où se règlent les délais
Choix appliqué : les délais (10, 18, 25 minutes sans progrès) sont une propriété du backend (`phantasmon.trahison.hint-minutes`), valable pour toutes les parties ; pas un réglage de partie.
- [x] Propriété du backend, commune à toutes les parties *(par défaut)*
- [ ] Réglage de la partie (comme `door_open_seconds`), modifiable par l'organisateur avant la partie
- [ ] Délai propre à chaque salle (champ de la définition)

Ta réponse : fait

## D66. Aides : pause et maître du jeu
Choix appliqué : le compteur d'aide est celui des salles (heure du dernier progrès) : une partie mise en pause **n'arrête pas** le compte, et un maître du jeu ne peut pas donner une aide à la main (le moteur sait le faire, aucune action ne l'expose).
- [ ] Le compte continue pendant la pause ; pas d'aide manuelle *(par défaut)*
- [x] Le compte s'arrête pendant la pause (le délai de pause est ajouté à l'heure du dernier progrès)
- [ ] Une action MJ `give_hint` donne l'aide suivante à un groupe tout de suite

Ta réponse : fait

## D67. Fin de `ROOMS` : qui perd le point
Choix appliqué : chaque membre du groupe (vivant ou non) d'une salle non finie perd 1 point, jamais sous 0 ; un bracelet à 0 tue son propriétaire comme après un vote. Aucune mesure de temps n'est enregistrée pour ces salles.
- [ ] Tous les membres, mort possible à 0 *(par défaut)*
- [x] Tous les membres, mais le point perdu ne peut pas faire tomber un bracelet à 0 (reste à 1)
- [ ] Seulement les membres encore vivants

Ta réponse : fait

## D68. Mesures : « médiane d'une salle »
Choix appliqué : la table garde une ligne par étape ; la médiane d'une salle est la **somme des médianes de ses étapes mesurées** (pas la médiane des durées totales des parties).
- [x] Somme des médianes des étapes *(par défaut)*
- [ ] Une ligne de plus par salle jouée (durée totale), médiane réelle des parties
- [ ] Ne compter que les parties sans aucune aide donnée

Ta réponse : fait

## D69. Aides : à qui et comment
Choix appliqué : l'aide va à la première étape ouverte non résolue (ordre de la définition) qui en a encore une ; elle voyage dans la vue de la salle (`steps[].hints`) donc seul le groupe la reçoit (plus les spectateurs et le maître du jeu, comme toute vue de salle), et le client de ses membres la fait lire par l'hôtesse. Elle n'est pas relue à la reconnexion.
- [x] Aide dans la vue de la salle, lue par l'hôtesse chez les membres *(par défaut)*
- [x] Aussi une liste « aides reçues » relisible dans l'inventaire
- [ ] Une annonce `say.*` du backend (comme les portes), en plus

Ta réponse : fait

## D70. Tirage : la taille des groupes et `three_levers`
Constat : chaque groupe de manche est toujours un trio (une paire plus un solo = 3 bracelets, neuf joueurs en tout), donc `three_levers` peut aller partout aujourd'hui. Choix appliqué : le tirage sait quand même qu'une salle demande un groupe d'au moins `n` joueurs (`RoomDraw.MIN_GROUP`, `three_levers` : 3) et ne la met jamais là où le groupe est plus petit ; il reçoit les tailles par manche et par place (3 partout par défaut) et ne relâche jamais cette règle.
- [x] Règle générale dans le tirage, tailles 3 partout *(par défaut)*
- [ ] Rien : les groupes sont toujours des trios
- [ ] Aussi refuser dans la validation d'une salle qu'elle serve à moins de 3

Ta réponse : fait

## D71. Tirage : le « mécanisme principal » d'une salle
Choix appliqué : celui qui compte le plus d'étapes dans la salle, le premier rencontré en cas d'égalité ; une salle sans étape n'en a pas (jamais en conflit de variété).
- [x] Le plus utilisé, premier en cas d'égalité *(par défaut)*
- [ ] Celui de la première étape
- [ ] Celui de la plus grosse difficulté (demande la difficulté des mécanismes dans le tirage)

Ta réponse : fait

## D72. Tirage : dans quel ordre relâcher les contraintes
Choix appliqué : montée de difficulté, puis variété des mécanismes, puis salles récentes, puis écart de difficulté (élargi au minimum nécessaire), puis salles toutes différentes. Le résultat dit lesquelles il rompt vraiment.
- [x] montée, variété, récentes, écart, répétition *(par défaut)*
- [ ] récentes avant la variété (on préfère rejouer une salle récente qu'avoir deux mécanismes pareils)
- [ ] l'écart de difficulté en premier

Ta réponse : fait

## D73. Tirage : banque trop petite
Choix appliqué : avec moins de neuf salles utilisables, le tirage rejoue des salles (jamais deux fois la même dans une manche si la banque en a trois ou plus) et le signale (`repeat`). Seule une banque vide ne tire rien : la partie joue alors la salle d'essai ou la salle intégrée.
- [ ] Répéter des salles et le dire *(par défaut)*
- [x] Refuser la création de la partie tant que la banque a moins de neuf salles
- [ ] Compléter avec la salle intégrée

Ta réponse : fait

## D74. Tirage : réglages par défaut
Choix appliqué : écart maximal 1 entre les trois salles d'une manche, montée 0 (aucune contrainte), 3 parties passées évitées. Réglables à la création (`draw_max_spread`, `draw_rise`, `draw_avoid_games`).
- [x] Écart 1, montée 0, 3 parties *(par défaut)*
- [ ] Montée 1 (la manche 3 plus dure que la 1) par défaut
- [ ] Écart 0 (difficultés identiques)

Ta réponse : fait

## D75. Tirage : qui le voit, quand il est tiré
Choix appliqué : le tirage se fait à la création de la partie (`create`), se refait avec « Tirer à nouveau » avant la manche 1, et la salle d'une place se remplace tant que sa manche n'a pas démarré. Seuls les maîtres du jeu nommés le voient (bloc `gm`) : un admin qui n'est pas maître du jeu ne voit pas le tirage dans le lobby.
- [x] Maîtres du jeu seulement *(par défaut)*
- [x] Les admins aussi, dans le lobby
- [ ] Tirer seulement au démarrage (`start`), pas à la création

Ta réponse : fait

## D77. Places : qui convertit les positions des emplacements
Choix appliqué : la conversion « relatif à la salle vers coordonnées de hub » reste faite par le client (`world/RoomFrame`, déjà en jeu depuis T3) ; le backend garde seulement le couple (place, orientation) dans le tirage et une copie testée de la règle (`RoomPlace.toHub`). `RoomInstance` ne change pas.
- [x] Client convertit, backend garde place et orientation *(par défaut)*
- [ ] Le backend convertit et envoie des positions de hub dans `EventsTrahisonRoom`

Ta réponse : fait

## D78. Vérification du collage : échantillon et seuils
Choix appliqué : le client du maître du jeu compare jusqu'à 600 blocs non vides par place (répartis sur toute la salle tournée), état de bloc seulement (pas les données des panneaux ni des têtes). Place « bien collée » si tout concorde, « pas collée » si moins de 5 % concordent, « mal collée » entre les deux, « chunks non chargés » si plus de la moitié des blocs sont hors des chunks chargés (il faut aller près des places).
- [x] 600 blocs, état seulement, seuil 5 % *(par défaut)*
- [ ] Tous les blocs (plus lent, plus sûr)
- [x] Comparer aussi les données de bloc (texte des panneaux)

Ta réponse : fait

## D79. Vérification du collage : quand
Choix appliqué : à la demande (bouton « Vérifier le collage » du lobby et commande `check`), jamais bloquant : `start` ne la lance pas et ne la refuse pas.
- [x] À la demande, jamais bloquant *(par défaut)*
- [ ] `start` refuse tant que la dernière vérification n'est pas entièrement bonne
- [ ] Vérification automatique à chaque ouverture du lobby

Ta réponse : fait

## D90. Soirée : l'outil de collage
Choix appliqué : la procédure demande à l'admin de coller avec Axiom un fichier par place, `.schem` (ou `.litematic`), sans rotation ni miroir, coin minimum sur le coin de la fiche ; le fichier unique `all_places` est donné comme raccourci.
- [x] Fichiers par place d'abord, fichier unique en raccourci *(par défaut)*
- [ ] Fichier unique d'abord
- [ ] WorldEdit en plus d'Axiom

Ta réponse : fait

## D91. Soirée : quand tirer et coller
Choix appliqué : la procédure fait tirer et coller la veille ou au moins une heure avant, puis vérifier le jour même ; le lobby n'est ouvert qu'une fois la vérification bonne.
- [x] Tirer et coller à l'avance, vérifier le jour même *(par défaut)*
- [ ] Tout le même soir, juste avant le lobby

Ta réponse : fait

## Tranché

Voir [`decisions-tranchees.md`](decisions-tranchees.md).

## D80. Fichiers de collage : où ils sont écrits et comment l'admin les reçoit
Choix appliqué : le client du maître du jeu écrit dans `.minecraft/phantasmon_events/trahison/export/<partie>/` ; le maître du jeu envoie le dossier à l'admin lui-même (la fiche et les fichiers y sont).
- [x] Dossier local, envoi à la main *(par défaut)*
- [ ] En plus, téléversement au backend et téléchargement par l'admin depuis un menu
- [ ] En plus, une copie dans un dossier partagé (cloud du serveur)

Ta réponse :

## D81. Coordonnées de la fiche de collage
Choix appliqué : la fiche donne les coordonnées du **plan (hub)**, y du monde, avec le coin minimum du fichier. L'admin doit connaître la conversion hub → monde réel de la place d'origine du hub.
- [x] Coordonnées du plan seulement *(par défaut)*
- [ ] Ajouter les coordonnées du monde du client qui exporte (le hub miroir), s'il est dans le hub
- [ ] Faire saisir à l'exportateur le décalage hub → monde de l'original

Ta réponse :

## D82. Fichier unique des 9 places
Choix appliqué : `all_places.schem` et `.litematic` couvrent la boîte englobant les 9 places ; au-delà de 64 millions de blocs de boîte, il n'est pas écrit (la fiche le dit) et seuls les fichiers par place servent.
- [x] Limite 64 M blocs, sinon pas de fichier unique *(par défaut)*
- [ ] Toujours l'écrire, quitte à produire un très gros fichier
- [ ] Ne jamais l'écrire

Ta réponse :

## D83. Langue de la fiche de collage
Choix appliqué : `paste_sheet.txt` est en français, sans accents (lue par l'admin dans un éditeur simple).
- [x] Français sans accents *(par défaut)*
- [ ] Français avec accents (UTF-8)
- [ ] Anglais

Ta réponse :

## D84. Source du tirage pour le bouton « Exporter »
Choix appliqué : tant que le client ne reçoit pas le tirage de T4.1, le bouton demande d'utiliser la commande `/phantasmon event trahison export <9 ids de salle>` (ids dans l'ordre des places : manche puis id) ; il suffira de renseigner `ExportFlow.drawn` quand le backend exposera le tirage.
- [x] Commande avec ids à la main en attendant *(par défaut)*
- [ ] Le backend envoie le tirage dans la vue du maître du jeu (`gm.draw`), le bouton l'utilise tout de suite
- [ ] Un écran de choix des 9 salles dans le client

Ta réponse :

## D96. Mort par 0 point : quand elle s'applique
Choix appliqué : la mort par 0 point reste appliquée à la fin de `VOTE` (au dépouillement), comme en T1 : le fantôme existe dès `RESULTS`, le tableau des résultats la montre, la mort est publique (D2 pour les portes, résultats pour les points). La fiche T6.1 disait « à la fin de `RESULTS` » ; cela aurait laissé un vivant à 0 point pendant les résultats.
- [x] Fin de `VOTE`, publique dans les résultats *(par défaut)*
- [ ] Fin de `RESULTS` (le tableau montre un joueur à 0 encore vivant)

Ta réponse :

## D97. Bracelets portés par un joueur qui meurt
Choix appliqué : un mort ne rend pas les bracelets qu'il portait ; ils restent à son nom (`holder`) mais ne servent plus à personne (il ne présente plus rien aux portes). Un seul bracelet « sur le corps » par corps.
- [x] Ils sont perdus avec lui *(par défaut)*
- [ ] Ils retombent sur son corps et se ramassent de nouveau
- [ ] Ils retournent sur le corps de leur propriétaire d'origine

Ta réponse :

## D98. Position des joueurs déconnectés
Choix appliqué : la vue envoie à tous (sauf au déconnecté) la dernière position des vivants déconnectés (`disconnected`), pour poser un mannequin. Un client lointain pourrait donc savoir qu'un joueur est tombé hors ligne ; ni bots ni fantômes ne sont listés.
- [x] À tous, sans filtre de distance *(par défaut)*
- [ ] Seulement à ceux de la même zone ou à moins de N blocs (le backend filtre par destinataire à chaque changement de position)

Ta réponse :

## D99. Qui voit qui a pris un bracelet
Choix appliqué : `bracelets[].holder` donne l'index de celui qui a pris le bracelet, à tous ceux qui connaissent déjà la mort. Le porteur est donc public (il se voit aussi sur son bracelet porté).
- [x] Public pour qui connaît la mort *(par défaut)*
- [ ] Caché : `holder` reste celui du poignet d'origine pour tout le monde sauf le porteur

Ta réponse :

## D100. Réglage `announceDeaths` : où le changer
Choix appliqué : `Settings.announceDeaths` existe (faux par défaut : mort découverte) et peut s'écrire à la création de la partie ; aucun bouton dans l'écran du maître du jeu n'est fait en T6.1/T6.2. Ne concerne que les morts autres que les portes et les points (publiques).
- [x] Réglage de création seulement pour l'instant *(par défaut)*
- [ ] Un interrupteur dans l'écran du maître du jeu, changeable en cours de partie

Ta réponse :

## D101. Durée d'attente d'un déconnecté en mode automatique
Choix appliqué : `Settings.autoPauseSeconds` (300 par défaut, réglable à la création) remplace la constante de 5 minutes. Pas de champ dans l'écran du maître du jeu.
- [x] Réglage de création *(par défaut)*
- [ ] Champ de l'écran du maître du jeu

Ta réponse :

## D102. Spectateurs : action `spectate` et étiquette
Choix appliqué : pas fait en T6.3. Un membre du hub non inscrit reçoit déjà une vue `SPECTATOR` (comme un fantôme, D16), traverse les portes et ne clique rien, mais aucune action `spectate` ni étiquette « spectateur » n'existe : le client ne sait pas qui est spectateur (la liste des membres du hub n'est pas dans la vue).
- [x] Reporté à une étape suivante *(par défaut)*
- [ ] Le backend envoie aux seuls fantômes, spectateurs et maîtres du jeu la liste des spectateurs, pour l'étiquette

Ta réponse :

## D103. Réglage de l'affichage des fantômes
Choix appliqué : `Settings.ghostDisplay` (`SILHOUETTE` par défaut, ou `HIDDEN`), fixé à la création ; pas de bouton dans l'écran du maître du jeu.
- [x] Réglage de création *(par défaut)*
- [ ] Bouton dans l'écran du maître du jeu, changeable en cours de partie

Ta réponse :

## D104. Fantôme en mode caché : se voit-il lui-même et voit-il les autres ?
Choix appliqué : `HIDDEN` ne cache les fantômes qu'aux vivants. Un fantôme, un spectateur et un maître du jeu voient les autres fantômes en noir et blanc.
- [x] Cachés aux vivants seulement *(par défaut)*
- [ ] Cachés aussi aux spectateurs

Ta réponse :

## D105. Marque « (fantôme) » visible de tous
Choix appliqué : la marque sous le pseudo s'affiche pour tous ceux qui voient le fantôme (en mode silhouette, les vivants aussi), pour qu'on ne le prenne jamais pour un joueur.
- [x] Pour tous ceux qui le voient *(par défaut)*
- [ ] Seulement pour les fantômes et spectateurs

Ta réponse :
