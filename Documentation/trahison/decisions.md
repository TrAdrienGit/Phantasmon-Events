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

## D270 : démarrer une partie sans aucun plan

`start` refuse désormais tout hub dont le plan n'est pas complet et valide, même sans plan du tout. Les essais sans plan ne sont plus possibles en production.

- [x] Refuser aussi un hub sans plan *(par défaut)*
- [ ] Laisser démarrer un hub sans aucun plan (essais), refuser seulement un plan partiel

Ta réponse :

## D271 : place hors des limites du hub

Aucun rayon ni limite de hub n'existe dans le plan : le contrôle « place hors du hub » est sauté.

- [x] Sauter le contrôle tant qu'aucune limite n'est enregistrée *(par défaut)*
- [ ] Ajouter au plan une boîte de limites du hub (`bounds`) et refuser une place qui en sort

Ta réponse :

## D272 : recouvrements de zones

Tout recouvrement entre deux zones est déjà une erreur bloquante ; l'avertissement porte donc sur la boîte d'une place qui recouvre une zone autre que `room_slot`.

- [x] Avertir seulement pour les zones qui ne sont pas `room_slot` *(par défaut)*
- [ ] Autoriser l'imbrication volontaire de zones (une zone dans une autre) et la signaler par un avertissement

Ta réponse :

## D273 : où voir les avertissements du plan

Le backend renvoie `warnings[]` avec le plan, mais l'éditeur du client ne les affiche pas encore.

- [x] Backend seulement pour l'instant *(par défaut)*
- [ ] Les afficher dans l'écran d'enregistrement de l'éditeur

Ta réponse :

## D274 : lien porte de couleur, place

Le lien est implicite : la N-ième paire d'une manche mène à la N-ième place de la manche (ordre de la liste).

- [x] Garder l'ordre de la liste, avec un message explicite *(par défaut)*
- [ ] Ajouter un champ `place` à chaque porte de couleur

Ta réponse :

## D280 : ce que l'essai joue

`room try` démarre le backend sur la salle **enregistrée** de l'éditeur (la version du backend, pas le brouillon du client) ; l'éditeur refuse donc si la salle a des changements non enregistrés.

- [x] La salle enregistrée, refus s'il y a des changements non enregistrés *(par défaut)*
- [ ] Envoyer le brouillon du client sans l'enregistrer (le backend n'a pas d'entrée pour cela aujourd'hui)

Ta réponse :

## D281 : modifier pendant l'essai

- [x] Toute modification de l'éditeur (ou `room off`, `room load`, `room new`) arrête l'essai *(par défaut)*
- [ ] L'édition est suspendue pendant l'essai (panneau et sélection bloqués jusqu'à `room try stop`)

Ta réponse :

## D282 : coopération jouée seule

- [x] Un drapeau de l'instance d'essai, que `Mechanism.solo` traduit (`three_levers`, `twin_screens`) ; jamais présent dans une partie *(par défaut)*
- [ ] Garder le paramètre `solo` des étapes (le rend modifiable dans la chaîne, au risque de l'oublier dans une vraie salle)

Ta réponse :

## D283 : graine de l'essai

- [x] Au hasard à chaque `try` et `try reset` : les solutions changent, comme en partie *(par défaut)*
- [ ] Une graine fixe par salle, pour reproduire une erreur

Ta réponse :

## D284 : `room try skip`

- [x] Résout les étapes ouvertes à cet instant, la chaîne continue *(par défaut)*
- [ ] Résout toute la salle d'un coup (comme `solve_room` du maître du jeu)

Ta réponse :

## D285 : aides pendant l'essai

- [x] Les aides automatiques (10, 18, 25 min sans progrès) arrivent comme en partie ; pas de commande pour en demander une *(par défaut)*
- [ ] Ajouter `room try hint` qui donne l'aide suivante tout de suite

Ta réponse :

## D286 : où l'essai tourne

- [x] Seulement hors d'un hub de partie (monde solo de construction) *(par défaut)*
- [ ] Aussi dans un hub, avec une salle posée sur une place du plan

Ta réponse :

## D287 : mémoire de l'essai

- [x] En mémoire seulement : arrêté à la déconnexion, ni sauvegardé ni compté comme temps d'étape *(par défaut)*
- [ ] Garder l'essai sur le disque pour le reprendre après un redémarrage du backend

Ta réponse :

## D288 : pouvoirs des partenaires (`power_spot`)

L'emplacement répond en essai, mais les décors du pouvoir (rocher, feuilles, barrières, voile) et le compagnon dépendent de la partie et ne sont pas dessinés.

- [x] Laisser tel quel pour cette version *(par défaut)*
- [ ] Les dessiner aussi dans l'essai (ancrer `PowerWorld` et `PartnerEntities` sur le cadre de l'éditeur)

Ta réponse :

## D289 : contenu du panneau d'essai

- [x] Id de la salle, étape(s) en cours avec leur mécanisme, « sortie ouverte » à la fin, rappel de `room try stop` *(par défaut)*
- [ ] Ajouter le chrono de l'essai et le nombre d'aides données

Ta réponse :
