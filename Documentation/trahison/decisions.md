# Décisions à trancher

> Un point par section. Coche une case (`[x]`) ou écris ta réponse dans le champ « Ta réponse ». Le choix
> appliqué en attendant est marqué *(par défaut)*. Je relis ce fichier à chaque reprise ; une fois un point
> appliqué, je le déplace dans « Tranché ».

## Tranché

Voir [`decisions-tranchees.md`](decisions-tranchees.md).


## D200 : ce que le vocal sait des morts cachées et des inconnus

Le client filtre la voix d'après la vue : un mort (`players[].alive` faux) est coupé pour les vivants. Or une mort cachée (D97)
paraît « vivant » dans la vue d'un vivant qui ne l'a pas apprise ; et la partie ne liste pas les spectateurs aux vivants (D102).

- [x] Un mort caché reste entendu tant que sa mort est secrète pour celui qui écoute ; un spectateur ou quelqu'un du serveur qui n'est pas dans la partie est coupé pour les vivants (tout ce que la partie ne liste pas) *(par défaut)*
- [ ] Un mort caché est coupé tout de suite (le silence trahirait la mort, mais le fantôme ne parlerait pas à ses anciens alliés)
- [ ] Les inconnus du serveur restent entendus pendant la partie (les spectateurs ne le seraient plus que par le biais de D102)

Ta réponse :

## D201 : la touche « annonce » du maître du jeu

- [x] Non assignée par défaut, comme les autres touches du jeu (le maître du jeu la règle une fois) *(par défaut)*
- [ ] Assignée par défaut à une touche (dis laquelle)

Ta réponse :

## D202 : portées dans la salle, chuchotement compris

Le sujet dit « 1 jusqu'à 4 blocs, 0 à 8 » pour la voix et « 0 à 3 blocs » pour le chuchotement, sans la courbe. Appliqué : voix
pleine jusqu'à 4 blocs puis décroissance linéaire jusqu'à 0 à 8 ; chuchotement pleine jusqu'à 1,5 bloc puis linéaire jusqu'à 0 à 3.
Les portées sont des constantes (`VoiceGains.Ranges.DEFAULT`), pas encore un réglage.

- [x] Courbe linéaire, chuchotement à pleine voix jusqu'à 1,5 bloc *(par défaut)*
- [ ] Chuchotement pleine voix jusqu'à 3 blocs puis coupé net (pas de décroissance)
- [ ] Portées réglables par le maître du jeu dans le panneau (dis les valeurs voulues)

Ta réponse :

## D203 : l'Écoute et la portée du serveur de Simple Voice Chat

Le filtre est chez celui qui écoute : il baisse ou coupe ce qui arrive, il ne peut pas faire arriver une voix que le serveur de
Simple Voice Chat n'envoie pas (sa portée de proximité, souvent 48 blocs). Si deux cabines sont plus éloignées que cette portée,
l'Écoute n'entendra pas. Rien n'est codé pour contourner cela (changer la configuration du serveur est hors périmètre de T5).

- [x] Laisser tel quel ; la portée du serveur est notée dans `vocal-essai.md` et vérifiée en jeu *(par défaut)*
- [ ] Demander à régler la portée de proximité du serveur au-dessus de la distance entre les cabines les plus éloignées

Ta réponse :

## D220 : les neuf thèmes de la première soirée

Les fiches `Documentation/salles/` proposent : manche 1 archives, serre, dortoir ; manche 2 salle des machines, infirmerie, salle de bal ; manche 3 observatoire, chapelle, crypte.

- [x] Ce jeu de neuf thèmes, des lieux d'un même complexe hanté, très différents à l'œil *(par défaut)*
- [ ] Autres thèmes (dis lesquels, ou lesquels remplacer)

Ta réponse :

## D221 : l'ordre des manches et la montée de la difficulté

Proposé : difficulté visée 2, 3 puis 4 sur 5. Dans une manche, les trois salles ont la même estimation (à 8 % près) ; d'une manche à l'autre l'estimation monte (environ 720, 860, 1 000 s) avec des paramètres plus durs et plus de Cobblemon. Les 25 minutes visées supposent que les trajets, les discussions et les erreurs doublent à peu près l'estimation : à mesurer à trois.

- [x] Montée 2, 3, 4 par les paramètres et la longueur, durée visée de 25 minutes partout *(par défaut)*
- [ ] Montée plus douce (manche 3 à 3,5 sur 5) ou plus raide
- [ ] Durée visée plus courte en manche 1 (20 minutes) et plus longue en manche 3 (30 minutes)

Ta réponse :

## D222 : des salles qui dépendent des mécanismes à coder

Les neuf fiches utilisent au moins un des quatre mécanismes à coder de T9.3 : `sliding_blocks` (archives, chapelle), `rotating_tiles` (dortoir, infirmerie, observatoire), `safe_dial` (bal, chapelle), `twin_screens` (serre, salle des machines, observatoire, crypte). Les rôles d'emplacement de ces quatre mécanismes sont marqués « à confirmer au codage ». Si un mécanisme n'est pas prêt, on le remplace temporairement par un mécanisme déjà codé absent de la salle.

- [x] Garder ces mécanismes dans les fiches ; remplacement de secours : `sliding_blocks` par `three_dials`, `rotating_tiles` par `wiring`, `safe_dial` par `ring_dial`, `twin_screens` par `three_levers` (seulement dans une salle qui n'a pas déjà ce mécanisme, sinon par un autre) *(par défaut)*
- [ ] Refaire les fiches sans aucun mécanisme à coder (les neuf salles ne jouent plus qu'avec les quinze mécanismes déjà codés)
- [ ] Limiter les mécanismes à coder aux manches 2 et 3 (les fiches de la manche 1 sont à refaire)

Ta réponse :

## D223 : la coopération à trois, et ses deux sortes

Chaque salle a une étape qui oblige à se parler. Cinq salles utilisent `three_levers` (trois joueurs, trois coins) ; quatre utilisent `twin_screens` (deux postes éloignés, chacun une moitié du problème, ce qui n'occupe que deux joueurs : le troisième relaie ou tient la notice).

- [x] Garder ce partage : `three_levers` à la manche 1 (archives, dortoir) et en 2 et 3 (infirmerie, bal, chapelle), `twin_screens` ailleurs *(par défaut)*
- [ ] Mettre `three_levers` partout (aucun mécanisme à coder pour la coopération, mais la même gestuelle neuf fois)
- [ ] Une troisième sorte de coopération à concevoir pour que les trois joueurs soient toujours tous occupés (dis laquelle)

Ta réponse :

## D224 : où loge le fragment de lore dans une salle

Le lore est écrit plus tard (T9.4, `lore.md`). Les fiches le placent dans un décor construit (pancarte, missel, plaque) sans emplacement ni étape, pour ne pas ajouter de mécanisme.

- [x] Décor construit seulement (pancarte, livre), lu en jouant, sans mécanisme *(par défaut)*
- [ ] Un document donné par le mécanisme `pickup_set` (page `document`), seulement dans les salles qui l'ont
- [ ] Une récompense de fin de salle (un texte affiché à la sortie)

Ta réponse :

## D225 : ce qui compte comme « recherche dans le décor »

La règle demande au moins une recherche par salle. Cinq salles utilisent `pickup_set` ; les quatre autres cachent un indice dans le décor : zone sombre de Flash (bal), code derrière un rideau (infirmerie), code sur une carte parmi d'autres (observatoire), alphabet derrière l'autel (chapelle).

- [x] Les deux formes comptent comme recherche *(par défaut)*
- [ ] Seul `pickup_set` compte : à ajouter dans les quatre salles concernées (une étape de plus ou un remplacement)

Ta réponse :

## D226 : la part de Cobblemon et ce que les joueurs doivent savoir

Treize étapes Cobblemon sur 52 : 3 en manche 1, 5 en manche 2, 5 en manche 3, deux au plus par salle. Chaque étape Cobblemon a ses aides (fiches de numéros, de poids, de types) pour un joueur qui ne connaît pas le jeu.

- [x] Garder 3, 5, 5 : peu de Cobblemon pour commencer, le gros ensuite *(par défaut)*
- [ ] Autant de Cobblemon par manche (5, 4, 4)
- [ ] Moins de Cobblemon partout (la moitié) pour des joueurs qui le connaissent peu

Ta réponse :

## D227 : la dernière étape ouvre la sortie

Dans toutes les fiches, la dernière étape (coopérative) porte le rôle `exit` lié à l'emplacement de sortie, sans étape `debug_button` supplémentaire. La dernière étape est donc toujours un moment collectif et visible.

- [x] La dernière étape porte la sortie ; la coopération est toujours la dernière *(par défaut)*
- [ ] Une étape finale séparée (comme dans la salle d'essai Cobblemon) qui ne fait qu'ouvrir la porte
- [ ] La coopération au milieu de la chaîne et un mini-jeu à l'écran pour finir

Ta réponse :

## D228 : une cachette du temps libre par salle, où

Chaque fiche propose une cachette (genre `secret`) dans un endroit de la salle qui se découvre sans énigme : livre vide, bac renversé, tiroir, miroir voilé, pied du télescope.

- [x] Une cachette par salle, trouvable en fouillant le décor, sans mécanisme *(par défaut)*
- [ ] Les cachettes sont tirées par la graine parmi plusieurs emplacements (à coder)
- [ ] Les cachettes sont liées au lore de la salle (à écrire avec `lore.md`)

Ta réponse :

## D229 : noms des fiches et ids des salles

Les ids sont en minuscules sans accents, avec des soulignés (`salle_des_machines`) ; le nom du fichier est l'id. Les textes de la fiche sont en français. Les ids d'étapes et d'emplacements sont en français sans accents (`fiches`, `pad_casier`).

- [x] Ids en français sans accents, un fichier par id *(par défaut)*
- [ ] Ids en anglais (cohérents avec le code)
- [ ] Ids numérotés (`salle_1_1` ...) pour que l'ordre ressorte

Ta réponse :
