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

## D13. Mort aux portes : ce qui reste de la personne
Choix appliqué : un joueur mort faute d'avoir passé une porte devient fantôme tout de suite (avant les salles), garde ses points sur son bracelet, reste dans son groupe de la
répartition (le groupe a un fantôme, son vote est « Allier » par défaut, son bracelet peut encore être trahi) et n'est plus compté aux portes des manches suivantes.
- [x] Comme appliqué *(par défaut)*
- [ ] Son bracelet tombe à 0 point
- [ ] Il perd aussi sa place dans le groupe : la salle et le vote se jouent sans lui

Ta réponse :

## D44. Maître du jeu qui joue : qui gagne quand les autres le tuent
Choix appliqué : les huit autres participants gagnent, vivants ou morts (« tous les autres gagnent automatiquement »).
- [x] Les huit autres, vivants ou morts *(par défaut)*
- [ ] Seulement les autres encore vivants à ce moment

Ta réponse :

## D45. Maître du jeu qui joue : comment il devient joueur
Choix appliqué : l'admin le nomme dans le lobby (avant ou après son `join`) ; il s'inscrit comme tout le monde et prend un des neuf sièges. Tant qu'il n'a pas rejoint, son rôle de maître du jeu est visible dans la liste du lobby (il y reste public pour ceux qui supervisent).
- [x] Nommé puis inscrit, visible seulement avant son inscription *(par défaut)*
- [ ] Un admin le désigne en secret : il n'apparaît dans aucune liste, même avant son inscription
- [ ] Il s'inscrit d'abord, puis seul un message à l'admin le fait maître du jeu

Ta réponse :

## D46. Maître du jeu qui joue : plusieurs maîtres du jeu qui jouent
Choix appliqué : tout maître du jeu inscrit est un rôle secret ; la partie finit dès qu'un seul d'entre eux meurt, et les gagnants sont tous les participants qui ne sont pas maîtres du jeu.
- [x] La première mort d'un maître du jeu qui joue finit la partie *(par défaut)*
- [ ] Un seul maître du jeu peut jouer à la fois (refusé sinon)
- [ ] La partie finit quand tous les maîtres du jeu qui jouent sont morts

Ta réponse :

## D47. Maître du jeu qui joue : sa vue de salle
Choix appliqué : il reçoit la vue de salle d'un joueur (pas celle du maître du jeu, qui montre les solutions), pour ne pas se trahir par ce qu'il sait.
- [x] Vue de joueur, la sienne seule *(par défaut)*
- [ ] Vue maître du jeu de toutes les salles (il voit les solutions)
- [ ] Un bouton pour passer de l'une à l'autre

Ta réponse :

## D48. Maître du jeu qui joue : ses actions de supervision restent-elles discrètes
Choix appliqué : aucun texte ni vue ne nomme l'auteur d'une action de maître du jeu ; en revanche une pause, une prolongation ou une porte forcée se voient (l'état change), ce qui montre qu'un maître du jeu existe sans dire qui.
- [x] L'état change visiblement, l'auteur reste secret *(par défaut)*
- [ ] Aucune trace visible : pas de bandeau « pause », la pause est dite « technique » sans cause
- [ ] Les pouvoirs qui se voient (pause, porte forcée) sont retirés au maître du jeu qui joue

Ta réponse :

## D49. Maître du jeu qui joue : révéler son nom à la fin
Choix appliqué : quand il est tué, l'annonce finale dit son nom (`say.game_master_killed`). S'il n'est pas tué, il n'est pas révélé (la partie finit par la porte finale ou un `end`).
- [x] Nom annoncé seulement s'il est tué *(par défaut)*
- [ ] Nom annoncé à la fin de toute partie
- [ ] Jamais annoncé

Ta réponse :

## D50. Maître du jeu qui joue : sa propre mort par ses pouvoirs
Choix appliqué : ses pouvoirs ne peuvent pas le tuer : `set_points` à 0 sur son propre bracelet ne tue qu'au prochain vote, comme pour tout joueur, et la victoire des autres s'applique alors aussi.
- [x] Pas d'exception, la règle s'applique comme pour un autre mort *(par défaut)*
- [ ] Sa propre mort par ses pouvoirs ne donne pas la victoire aux autres
- [ ] Ses pouvoirs ne peuvent pas toucher son propre bracelet

Ta réponse :

## D51. Maître du jeu qui joue : un maître du jeu mort peut-il encore agir
Choix appliqué : la mort d'un maître du jeu qui joue finit la partie : il n'y a donc pas de fantôme-maître. Le point reste à reprendre si une autre règle (T6) le laisse en vie après sa « mort ».
- [x] La partie finit toujours à sa mort *(par défaut)*
- [ ] Il devient fantôme et garde ses pouvoirs, les autres ont gagné quand même

Ta réponse :

## D52. Porte finale : durée maximale du réglage
Choix appliqué : `final_door_seconds` vaut 60 par défaut et le backend le borne à 1..120 secondes (réglage de la partie).
- [x] Borné à 120 secondes *(par défaut)*
- [ ] Borné à 300 secondes
- [ ] Aucun plafond

Ta réponse :

## D53. Porte finale : les rappels du compte à rebours
Choix appliqué : l'hôtesse rappelle le compte à rebours chez chacun à 30, 10 et 5 secondes (affichage du client, sur l'heure du serveur) en plus de l'annonce d'ouverture ; le HUD affiche les secondes tout du long.
- [x] 30, 10 et 5 secondes *(par défaut)*
- [ ] Tous les 10 secondes puis chaque seconde sur les 5 dernières
- [ ] Aucun rappel, le HUD suffit

Ta réponse :

## D54. Porte finale : qui est « passé »
Choix appliqué : est passé celui qui est entré dans la `zone_b` de la porte pendant la fenêtre, ou qui s'y tient à la fermeture ; un opener qui y est entré puis en est ressorti reste compté.
- [x] Entré à un moment de la fenêtre, même s'il est ressorti *(par défaut)*
- [ ] Seulement ceux qui s'y tiennent à la fermeture

Ta réponse :

## D55. Porte finale : un deuxième actionnement pendant la fenêtre
Choix appliqué : actionner de nouveau le pupitre pendant la fenêtre ne fait rien (ni prolongation ni nouvelle fenêtre), même par un joueur qui n'était pas opener et qui a gagné des points depuis.
- [x] Sans effet *(par défaut)*
- [ ] Il ajoute un opener (un 9 tardif peut passer lui aussi)

Ta réponse :

## D56. Porte finale : plan sans porte, annonce de la fin
Choix appliqué : sans porte dans le plan, la fenêtre est la même mais rien n'est à franchir : au terme, tous les openers gagnent. La phrase d'ouverture parle de « la porte 9 » même sans porte construite (l'essai est un banc d'essai).
- [x] Même annonce, tous gagnent à la fin *(par défaut)*
- [ ] Annonce adaptée « le compte à rebours commence » sans parler d'une porte

Ta réponse :

## D57. Salles libres : une salle non terminée à l'échéance
Choix appliqué : un groupe dont la salle n'est pas terminée ne peut pas visiter les autres salles avant le temps libre, même si l'échéance de `ROOMS` approche ; la salle jamais terminée n'ouvre donc pas la libre circulation.
- [x] Pas de libre circulation sans salle terminée *(par défaut)*
- [ ] Libre circulation dès qu'il reste moins de N minutes de `ROOMS`
- [ ] Libre circulation pour tous dès que la première salle est finie

Ta réponse :

## D58. Salles libres : sa propre salle pendant `VOTE`
Choix appliqué : pendant `VOTE`, un joueur libre de visiter peut rejouer les autres salles et aussi la sienne, comme en temps libre (rien ne compte) ; la salle de sa manche ne rapporte plus rien.
- [x] Toutes les salles, la sienne comprise, sans effet sur la partie *(par défaut)*
- [ ] Seulement les autres salles pendant `VOTE`

Ta réponse :

## D59. Salles libres : cabine de vote et salles
Choix appliqué : un joueur libre de visiter une salle pendant `VOTE` peut en même temps avoir à voter dans sa cabine : rien ne l'empêche de visiter une salle avant ou après son vote.
- [x] Aucune restriction de lieu *(par défaut)*
- [ ] Un joueur enfermé dans sa cabine (verrouillée) ne peut pas visiter ailleurs jusqu'au vote

Ta réponse :

## D60. Câblage : `colors` plus grand que `wires`
Choix appliqué : avec D38 (5 couleurs), `colors` ne peut pas dépasser `wires` (chaque couleur du schéma est employée au moins une fois, donc il faut au moins autant de fils que de couleurs). L'éditeur et le validateur refusent `colors > wires` (`colors: at most wires`) ; le moteur, lui, borne en silence.
- [x] Refuser `colors > wires` dans le validateur *(par défaut)*
- [ ] Accepter et borner en silence (`colors` devient `wires`)
- [ ] Autoriser des couleurs absentes du schéma mais présentes à droite (leurres)

Ta réponse :

## D61. Objets cachés : un compteur sans dépôt
Choix appliqué : sans emplacement de dépôt (D32), l'écran compteur « n / total » de `pickup_set` ne s'ouvre plus par un emplacement ; il ne reste ouvert que pour une salle ancienne qui lie encore `deposit`. Les joueurs ne voient donc plus combien d'objets restent à trouver (le total annoncé par D33 n'est plus visible).
- [ ] Rien de plus : on cherche sans compteur *(par défaut)*
- [ ] Une ligne du bracelet / de la salle (HUD) « objets : 2 / 3 », portée par la vue de l'étape
- [ ] L'objet ramassé s'annonce dans la discussion de la salle (« un objet de plus, il en reste 1 »)

Ta réponse :

## D62. Digicode : réglage du verrouillage
Choix appliqué : 3 essais et 10 s sont des constantes du mécanisme (`LOCK_AFTER`, `LOCK_SECONDS`), pas des paramètres de l'étape ; le verrou est commun à tout le groupe et le compteur d'échecs repart de 0 au verrouillage.
- [x] Constantes, identiques pour toutes les salles *(par défaut)*
- [ ] Paramètres `lockAfter` (2 à 6) et `lockSeconds` (5 à 60) de l'étape, pour régler la difficulté salle par salle
- [ ] Le verrou s'allonge à chaque répétition (10 s, 20 s, 40 s)

Ta réponse :

## D63. Câblage : le schéma en texte et la langue
Choix appliqué : l'indice `scheme_text` est un document dont les lignes portent des jetons neutres (`1 : @color:red`) ; le client les remplace par le nom de la couleur dans la langue du joueur (clés `phantasmon_events.trahison.color.<id>`). Un texte fixe écrit par le backend serait dans une seule langue.
- [x] Jetons `@color:<id>` traduits par le client *(par défaut)*
- [ ] Texte fixe en français écrit par le backend
- [ ] Le concepteur de la salle écrit lui-même le texte (paramètre `schemeText`), sans lien avec la graine

Ta réponse :

## D64. Salles : ce que la mesure change à la difficulté affichée
Choix appliqué : dès 3 mesures d'une étape, le **temps attendu** affiché (`expected_seconds` de la liste des salles) prend la médiane mesurée de cette étape ; la **difficulté** (1 à 3 par mécanisme, sommée) ne change pas et un champ `measured` dit que le temps est mesuré.
- [x] Seul le temps attendu devient mesuré ; la note de difficulté reste celle des mécanismes *(par défaut)*
- [ ] La difficulté affichée est aussi déduite du temps mesuré (par seuils, ex. moins de 5 min = 1, plus de 12 min = 3)
- [ ] La mesure remplace aussi les valeurs enregistrées dans la définition à chaque enregistrement

Ta réponse :

## D65. Aides : où se règlent les délais
Choix appliqué : les délais (10, 18, 25 minutes sans progrès) sont une propriété du backend (`phantasmon.trahison.hint-minutes`), valable pour toutes les parties ; pas un réglage de partie.
- [x] Propriété du backend, commune à toutes les parties *(par défaut)*
- [ ] Réglage de la partie (comme `door_open_seconds`), modifiable par l'organisateur avant la partie
- [ ] Délai propre à chaque salle (champ de la définition)

Ta réponse :

## D66. Aides : pause et maître du jeu
Choix appliqué : le compteur d'aide est celui des salles (heure du dernier progrès) : une partie mise en pause **n'arrête pas** le compte, et un maître du jeu ne peut pas donner une aide à la main (le moteur sait le faire, aucune action ne l'expose).
- [x] Le compte continue pendant la pause ; pas d'aide manuelle *(par défaut)*
- [ ] Le compte s'arrête pendant la pause (le délai de pause est ajouté à l'heure du dernier progrès)
- [ ] Une action MJ `give_hint` donne l'aide suivante à un groupe tout de suite

Ta réponse :

## D67. Fin de `ROOMS` : qui perd le point
Choix appliqué : chaque membre du groupe (vivant ou non) d'une salle non finie perd 1 point, jamais sous 0 ; un bracelet à 0 tue son propriétaire comme après un vote. Aucune mesure de temps n'est enregistrée pour ces salles.
- [x] Tous les membres, mort possible à 0 *(par défaut)*
- [ ] Tous les membres, mais le point perdu ne peut pas faire tomber un bracelet à 0 (reste à 1)
- [ ] Seulement les membres encore vivants

Ta réponse :

## D68. Mesures : « médiane d'une salle »
Choix appliqué : la table garde une ligne par étape ; la médiane d'une salle est la **somme des médianes de ses étapes mesurées** (pas la médiane des durées totales des parties).
- [x] Somme des médianes des étapes *(par défaut)*
- [ ] Une ligne de plus par salle jouée (durée totale), médiane réelle des parties
- [ ] Ne compter que les parties sans aucune aide donnée

Ta réponse :

## D69. Aides : à qui et comment
Choix appliqué : l'aide va à la première étape ouverte non résolue (ordre de la définition) qui en a encore une ; elle voyage dans la vue de la salle (`steps[].hints`) donc seul le groupe la reçoit (plus les spectateurs et le maître du jeu, comme toute vue de salle), et le client de ses membres la fait lire par l'hôtesse. Elle n'est pas relue à la reconnexion.
- [x] Aide dans la vue de la salle, lue par l'hôtesse chez les membres *(par défaut)*
- [ ] Aussi une liste « aides reçues » relisible dans l'inventaire
- [ ] Une annonce `say.*` du backend (comme les portes), en plus

Ta réponse :

## Tranché

Voir [`decisions-tranchees.md`](decisions-tranchees.md).
