# Décisions à trancher

> Un point par section. Coche une case (`[x]`) ou écris ta réponse dans le champ « Ta réponse ». Le choix
> appliqué en attendant est marqué *(par défaut)*. Je relis ce fichier à chaque reprise ; une fois un point
> appliqué, je le déplace dans « Tranché ».

## D44. Maître du jeu qui joue : qui gagne quand les autres le tuent
Choix appliqué : les huit autres participants gagnent, vivants ou morts (« tous les autres gagnent automatiquement »).
- [ ] Les huit autres, vivants ou morts *(par défaut)*
- [x] Seulement les autres encore vivants à ce moment

Ta réponse : fait

## D46. Maître du jeu qui joue : plusieurs maîtres du jeu qui jouent
Choix appliqué : tout maître du jeu inscrit est un rôle secret ; la partie finit dès qu'un seul d'entre eux meurt, et les gagnants sont tous les participants qui ne sont pas maîtres du jeu.
- [ ] La première mort d'un maître du jeu qui joue finit la partie *(par défaut)*
- [x] Un seul maître du jeu peut jouer à la fois (refusé sinon)
- [ ] La partie finit quand tous les maîtres du jeu qui jouent sont morts

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

## D69. Aides : à qui et comment
Choix appliqué : l'aide va à la première étape ouverte non résolue (ordre de la définition) qui en a encore une ; elle voyage dans la vue de la salle (`steps[].hints`) donc seul le groupe la reçoit (plus les spectateurs et le maître du jeu, comme toute vue de salle), et le client de ses membres la fait lire par l'hôtesse. Elle n'est pas relue à la reconnexion.
- [x] Aide dans la vue de la salle, lue par l'hôtesse chez les membres *(par défaut)*
- [x] Aussi une liste « aides reçues » relisible dans l'inventaire
- [ ] Une annonce `say.*` du backend (comme les portes), en plus

Ta réponse : fait

## D73. Tirage : banque trop petite
Choix appliqué : avec moins de neuf salles utilisables, le tirage rejoue des salles (jamais deux fois la même dans une manche si la banque en a trois ou plus) et le signale (`repeat`). Seule une banque vide ne tire rien : la partie joue alors la salle d'essai ou la salle intégrée.
- [ ] Répéter des salles et le dire *(par défaut)*
- [x] Refuser la création de la partie tant que la banque a moins de neuf salles
- [ ] Compléter avec la salle intégrée

Ta réponse : fait

## D75. Tirage : qui le voit, quand il est tiré
Choix appliqué : le tirage se fait à la création de la partie (`create`), se refait avec « Tirer à nouveau » avant la manche 1, et la salle d'une place se remplace tant que sa manche n'a pas démarré. Seuls les maîtres du jeu nommés le voient (bloc `gm`) : un admin qui n'est pas maître du jeu ne voit pas le tirage dans le lobby.
- [x] Maîtres du jeu seulement *(par défaut)*
- [x] Les admins aussi, dans le lobby
- [ ] Tirer seulement au démarrage (`start`), pas à la création

Ta réponse : fait

## D78. Vérification du collage : échantillon et seuils
Choix appliqué : le client du maître du jeu compare jusqu'à 600 blocs non vides par place (répartis sur toute la salle tournée), état de bloc seulement (pas les données des panneaux ni des têtes). Place « bien collée » si tout concorde, « pas collée » si moins de 5 % concordent, « mal collée » entre les deux, « chunks non chargés » si plus de la moitié des blocs sont hors des chunks chargés (il faut aller près des places).
- [x] 600 blocs, état seulement, seuil 5 % *(par défaut)*
- [ ] Tous les blocs (plus lent, plus sûr)
- [x] Comparer aussi les données de bloc (texte des panneaux)

Ta réponse : fait

## Tranché

Voir [`decisions-tranchees.md`](decisions-tranchees.md).

## D80. Fichiers de collage : où ils sont écrits et comment l'admin les reçoit
Choix appliqué : le client du maître du jeu écrit dans `.minecraft/phantasmon_events/trahison/export/<partie>/` ; le maître du jeu envoie le dossier à l'admin lui-même (la fiche et les fichiers y sont).
- [ ] Dossier local, envoi à la main *(par défaut)*
- [x] En plus, téléversement au backend et téléchargement par l'admin depuis un menu
- [ ] En plus, une copie dans un dossier partagé (cloud du serveur)

Ta réponse : fait

## D81. Coordonnées de la fiche de collage
Choix appliqué : la fiche donne les coordonnées du **plan (hub)**, y du monde, avec le coin minimum du fichier. L'admin doit connaître la conversion hub → monde réel de la place d'origine du hub.
- [ ] Coordonnées du plan seulement *(par défaut)*
- [x] Ajouter les coordonnées du monde du client qui exporte (le hub miroir), s'il est dans le hub
- [ ] Faire saisir à l'exportateur le décalage hub → monde de l'original

Ta réponse : fait

## D82. Fichier unique des 9 places
Choix appliqué : `all_places.schem` et `.litematic` couvrent la boîte englobant les 9 places ; au-delà de 64 millions de blocs de boîte, il n'est pas écrit (la fiche le dit) et seuls les fichiers par place servent.
- [ ] Limite 64 M blocs, sinon pas de fichier unique *(par défaut)*
- [x] Toujours l'écrire, quitte à produire un très gros fichier
- [ ] Ne jamais l'écrire

Ta réponse : fait

## D83. Langue de la fiche de collage
Choix appliqué : `paste_sheet.txt` est en français, sans accents (lue par l'admin dans un éditeur simple).
- [ ] Français sans accents *(par défaut)*
- [x] Français avec accents (UTF-8)
- [ ] Anglais

Ta réponse : fait

## D97. Bracelets portés par un joueur qui meurt
Choix appliqué : un mort ne rend pas les bracelets qu'il portait ; ils restent à son nom (`holder`) mais ne servent plus à personne (il ne présente plus rien aux portes). Un seul bracelet « sur le corps » par corps.
- [ ] Ils sont perdus avec lui *(par défaut)*
- [x] Ils retombent sur son corps et se ramassent de nouveau
- [ ] Ils retournent sur le corps de leur propriétaire d'origine

Ta réponse : fait

## D99. Qui voit qui a pris un bracelet
Choix appliqué : `bracelets[].holder` donne l'index de celui qui a pris le bracelet, à tous ceux qui connaissent déjà la mort. Le porteur est donc public (il se voit aussi sur son bracelet porté).
- [ ] Public pour qui connaît la mort *(par défaut)*
- [x] Caché : `holder` reste celui du poignet d'origine pour tout le monde sauf le porteur

Ta réponse : fait

## D102. Spectateurs : action `spectate` et étiquette
Choix appliqué : pas fait en T6.3. Un membre du hub non inscrit reçoit déjà une vue `SPECTATOR` (comme un fantôme, D16), traverse les portes et ne clique rien, mais aucune action `spectate` ni étiquette « spectateur » n'existe : le client ne sait pas qui est spectateur (la liste des membres du hub n'est pas dans la vue).
- [x] Reporté à une étape suivante *(par défaut)*
- [ ] Le backend envoie aux seuls fantômes, spectateurs et maîtres du jeu la liste des spectateurs, pour l'étiquette

Ta réponse : fait

