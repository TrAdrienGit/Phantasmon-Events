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
