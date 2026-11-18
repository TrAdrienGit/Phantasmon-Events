# Décisions à trancher

> Un point par section. Coche une case (`[x]`) ou écris ta réponse dans le champ « Ta réponse ». Le choix
> appliqué en attendant est marqué *(par défaut)*. Je relis ce fichier à chaque reprise ; une fois un point
> appliqué, je le déplace dans « Tranché ».

## Tranché

Voir [`decisions-tranchees.md`](decisions-tranchees.md).

## D260 : les objets sans cachette du plan vont dans les cachettes des salles

D141 disait : cachettes du plan seulement, objets en trop écartés (`unplaced_items`). Maintenant que chaque salle a une cachette active (D228), elle devient une cachette de plus : un objet par salle, posé au début de la manche, fouillable à l'identique (candidats inertes compris).

- [x] Les cachettes actives des salles s'ajoutent aux cachettes du plan, pour les objets qui n'y tenaient pas ; le reste est écarté *(par défaut)*
- [ ] Les cachettes des salles reçoivent des objets avant ceux du plan (tirage commun, plan et salles)
- [ ] Comme avant : plan seulement (D141)

Ta réponse :

## D250 : où se posent les emplacements muraux proposés par une fiche

Les 9 gabarits (`room new <id> <fiche>`) proposent chaque emplacement mural dans la rangée d'air collée à un mur d'un bloc posé sur le bord de la boîte (x = 1 ou 46, z = 1 ou 46).

- [x] Murs d'un bloc au bord de la boîte, emplacements dans la rangée d'air devant *(par défaut)*
- [ ] Murs de deux blocs : décaler tous les emplacements muraux d'un bloc vers l'intérieur
- [ ] Emplacements dans l'épaisseur du mur (x = 0 ou 47)

Ta réponse :

## D251 : l'emplacement `sortie` dans les gabarits

La fiche place `sortie` (genre `exit`, 3 x 3 x 1) « mur nord, centre » : le gabarit le pose sur l'ouverture de sortie (x 22 à 24, z = 0), comme l'exemple de la salle d'essai, et l'éditeur n'avertit plus d'un emplacement sur l'ouverture pour ce genre-là seulement.

- [x] `sortie` sur l'ouverture, sans avertissement pour le genre `exit` *(par défaut)*
- [ ] `sortie` un bloc devant l'ouverture (z = 1), avertissement gardé pour les autres genres
- [ ] `sortie` déjà marquée comme placée dans le gabarit (une validation de moins)

Ta réponse :

## D252 : le nom de la salle créée d'après une fiche

`room new <id> <fiche>` : l'id de la salle est le premier mot (obligatoire), la fiche le second ; pour garder le nom de la fiche il faut le répéter (`room new archives archives`).

- [x] Id obligatoire, fiche facultative en second *(par défaut)*
- [ ] Un seul mot suffit quand c'est le nom d'une fiche (`room new archives` charge la fiche `archives`)
- [ ] Commande séparée : `room from <fiche> [id]`

Ta réponse :

## D253 : l'ordre de la liste « À placer »

La liste suit la chaîne : emplacements de la première étape (ses rôles, puis où ses indices s'affichent), puis de la suivante, la sortie avec les leviers de sa dernière étape, les cachettes en dernier.

- [x] Ordre de la chaîne *(par défaut)*
- [ ] Par zone du monde (nord vers sud), pour construire en marchant
- [ ] Par genre d'emplacement

Ta réponse :

## D254 : enregistrer avec des emplacements non confirmés

Tant qu'un emplacement d'une fiche n'est pas confirmé (position gardée ou placée sur la sélection), l'enregistrement est refusé avec un message local clair.

- [x] Refus (erreur), la liste des emplacements restants dans les problèmes *(par défaut)*
- [ ] Avertissement seulement : on peut enregistrer une salle à moitié placée
- [ ] Refus, mais avec un bouton « Tout garder tel quel » pour confirmer d'un coup les positions proposées

Ta réponse :

## D255 : quand un fantôme devient un emplacement placé

Un fantôme (cadre en pointillés qui pulse) cesse d'en être un seulement par un geste : « Garder la position proposée » ou « Placer sur la sélection » (aussi depuis la fiche de l'emplacement).

- [x] Geste explicite seulement *(par défaut)*
- [ ] Aussi dès qu'on modifie sa position ou sa taille à la main dans la fiche
- [ ] Aussi dès qu'on construit un bloc dans sa boîte (déconseillé : la boîte doit rester vide)

Ta réponse :

## D256 : la ligne « Prochain »

La ligne « Prochain : id — repère (n restants) » est en haut à gauche, masquée quand un écran est ouvert (le panneau dit la même chose).

- [x] En haut à gauche, masquée quand un écran est ouvert *(par défaut)*
- [ ] Toujours affichée, au-dessus du panneau quand il est ouvert
- [ ] Dans la barre d'action au-dessus de la barre d'objets

Ta réponse :

## D257 : la difficulté écrite dans les gabarits

Les gabarits portent la difficulté visée de la fiche (2, 3 ou 4) ; le serveur la remplace à l'enregistrement par la somme des étapes, l'éditeur affiche son propre calcul.

- [x] Difficulté visée de la fiche dans le gabarit, recalculée à l'enregistrement *(par défaut)*
- [ ] Pas de difficulté dans les gabarits (le champ reste vide jusqu'à l'enregistrement)
- [ ] Remplacer la difficulté visée par la somme des étapes dès le gabarit

Ta réponse :

## D258 : combien de cachettes candidates par gabarit

D228 demande deux ou trois candidats par salle. Les gabarits en proposent 3 pour `archives`, `bal` et `crypte`, 2 pour les six autres.

- [x] 2 ou 3 selon la salle, d'après les endroits évoqués dans la fiche *(par défaut)*
- [ ] Toujours 3 (plus de variété d'une partie à l'autre, plus de construction)
- [ ] Toujours 2

Ta réponse :

## D259 : essayer une étape de coopération seul

Pour essayer une salle seule, la propriétaire règle à la main `solo` et `hold` de `three_levers` dans l'écran de la chaîne, joue, puis les retire (procédure du guide).

- [x] À la main, avec la procédure du guide *(par défaut)*
- [ ] Bouton « Essai seul » dans l'écran de la chaîne : met `solo` et `hold` de façon temporaire et les retire à l'enregistrement
- [ ] Une salle d'essai séparée par fiche (`<id>_essai`) qui garde `solo` en permanence

Ta réponse :
