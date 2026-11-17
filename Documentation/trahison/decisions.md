# Décisions à trancher

> Un point par section. Coche une case (`[x]`) ou écris ta réponse dans le champ « Ta réponse ». Le choix
> appliqué en attendant est marqué *(par défaut)*. Je relis ce fichier à chaque reprise ; une fois un point
> appliqué, je le déplace dans « Tranché ».

## D120. Compteur d'objets : qui le voit
Choix appliqué : le compteur « Objets : n / total » est dans la vue de la salle (`steps[].progress`) donc il arrive aussi aux spectateurs et au maître du jeu, mais le client ne le dessine que pour un joueur dans sa propre salle (en haut à droite, hors mini-jeu).
- [ ] Seulement le joueur dans sa propre salle *(par défaut)*
- [x] Aussi le maître du jeu et les spectateurs, pour la salle qu'ils regardent
- [ ] Aussi pendant les visites des autres salles (temps libre)

Ta réponse : fait

## Tranché

Voir [`decisions-tranchees.md`](decisions-tranchees.md).

## D111. Spectateurs non inscrits
Choix appliqué : seuls les comptes qui ont envoyé `spectate` (commande `/phantasmon event trahison spectate`, `off` pour arrêter) ont la marque et le traitement ; un membre du hub qui n'a rien fait reste un simple observateur sans marque, et les vivants ne reçoivent jamais la liste (ils ne cachent donc pas les spectateurs).
- [x] Inscription par commande, pas de bouton *(par défaut)*
- [ ] Tout membre du hub non inscrit est spectateur d'office (le backend lit les membres du hub)
- [x] Un bouton « Regarder » dans l'écran du lobby

Ta réponse : fait

