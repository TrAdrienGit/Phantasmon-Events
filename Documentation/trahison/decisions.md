# Décisions à trancher

> Un point par section. Coche une case (`[x]`) ou écris ta réponse dans le champ « Ta réponse ». Le choix
> appliqué en attendant est marqué *(par défaut)*. Je relis ce fichier à chaque reprise ; une fois un point
> appliqué, je le déplace dans « Tranché ».

## D120. Compteur d'objets : qui le voit
Choix appliqué : le compteur « Objets : n / total » est dans la vue de la salle (`steps[].progress`) donc il arrive aussi aux spectateurs et au maître du jeu, mais le client ne le dessine que pour un joueur dans sa propre salle (en haut à droite, hors mini-jeu).
- [x] Seulement le joueur dans sa propre salle *(par défaut)*
- [ ] Aussi le maître du jeu et les spectateurs, pour la salle qu'ils regardent
- [ ] Aussi pendant les visites des autres salles (temps libre)

Ta réponse :

## D121. Pause et redémarrage du backend
Choix appliqué : l'heure de début de pause (D66) n'est gardée qu'en mémoire : un redémarrage du backend pendant une pause fait perdre la durée écoulée (le compte d'aide reprend comme si la pause n'avait pas eu lieu).
- [x] Mémoire seulement, cas rare *(par défaut)*
- [ ] Enregistrer l'heure de pause dans la partie (`Game`), pour la reprise exacte

Ta réponse :

## Tranché

Voir [`decisions-tranchees.md`](decisions-tranchees.md).

## D110. D102 : case cochée « reporté », consigne « faire »
La case cochée de D102 était « Reporté à une étape suivante » (le défaut) avec « fait » en réponse ; la consigne reçue demandait de faire l'option appliquée. J'ai donc fait la seconde option (le backend envoie la liste des spectateurs aux seuls fantômes, spectateurs et maîtres du jeu) : action `spectate`, marque « (spectateur) », même traitement que les fantômes (gris, caché en mode `HIDDEN`), avatars des copies compris (ce sont des joueurs pour le rendu).
- [x] Spectateurs inscrits par `spectate` (liste envoyée aux seuls observateurs) *(par défaut)*
- [ ] Revenir à « reporté » (retirer `spectate` et la marque)

Ta réponse :

## D111. Spectateurs non inscrits
Choix appliqué : seuls les comptes qui ont envoyé `spectate` (commande `/phantasmon event trahison spectate`, `off` pour arrêter) ont la marque et le traitement ; un membre du hub qui n'a rien fait reste un simple observateur sans marque, et les vivants ne reçoivent jamais la liste (ils ne cachent donc pas les spectateurs).
- [x] Inscription par commande, pas de bouton *(par défaut)*
- [ ] Tout membre du hub non inscrit est spectateur d'office (le backend lit les membres du hub)
- [ ] Un bouton « Regarder » dans l'écran du lobby

Ta réponse :

## D112. D50 : quels pouvoirs comptent
Choix appliqué : seule la mise à 0 de son propre bracelet par `set_points` (envoyée par lui-même) évite la victoire des autres. Un maître du jeu qui joue et qui fait avancer les portes (`next`) sans avoir passé de porte meurt d'un mécanisme normal : les autres gagnent.
- [x] Seulement `set_points` sur son propre bracelet *(par défaut)*
- [ ] Aussi `next` et `end` quand ils le tuent

Ta réponse :

## D113. Bracelets déposés : l'ordre du ramassage
Choix appliqué : `take_bracelet` sans champ `bracelet` prend d'abord le bracelet du mort, puis ceux qu'il portait, dans l'ordre où il les avait pris ; avec `bracelet`, on choisit. Le client n'envoie que le corps : il les prend donc un à un.
- [x] Un à un, le sien d'abord *(par défaut)*
- [ ] Tous d'un coup

Ta réponse :

## D114. Réglages d'une partie venus du message `create`
Choix appliqué : `settings.announce_deaths`, `settings.ghost_display` (`HIDDEN`) et `settings.auto_pause_seconds` (1 à 1800) sont lus par le message de création ; `final_door_seconds` est borné à 300 (D52) par le message et par `GameService.create`.
- [x] À la création seulement *(par défaut)*
- [ ] Changeables en cours de partie par le maître du jeu

Ta réponse :

