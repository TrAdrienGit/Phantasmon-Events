# Guide de l'éditeur de salle

> Pour la personne qui construit les salles du jeu de trahison (étape T3.2). Tout se fait dans un monde **solo en créatif**,
> avec un compte admin de Phantasmon. Le mod ne marche pas sur un serveur : les blocs et leurs entités (panneaux, bannières)
> ne se lisent en entier que sur le serveur intégré du monde solo.

## En bref

1. Créer un monde solo plat, créatif, et s'y placer dans une grande zone vide.
2. `/phantasmon event trahison room new atelier` : la boîte de la salle apparaît là où tu te tiens.
3. Construire la salle dans la boîte, en laissant l'entrée et la sortie libres.
4. Marquer les **emplacements** (terminaux, digicodes, indices muraux…) avec le bâton, puis `K`.
5. Écrire la **chaîne** d'étapes (bouton « Chaîne » du panneau).
6. **Enregistrer** : le mod lit les blocs, vérifie la salle, envoie la définition et les blocs au backend.
7. Plus tard, dans n'importe quel monde solo : `/phantasmon event trahison room load atelier` pour la retoucher.

## Les commandes

| Commande | Effet |
|---|---|
| `/phantasmon event trahison room new <id>` | Commence une salle. `<id>` : lettres, chiffres, `_` et `-`, 40 au plus. |
| `/phantasmon event trahison room load <id>` | Colle une salle enregistrée là où tu te tiens, et ramène ses emplacements et sa chaîne. |
| `/phantasmon event trahison room list` | Les salles du backend : id, nom, version, difficulté, étapes, emplacements, blocs présents ou non. |
| `/phantasmon event trahison room off` | Quitte l'éditeur. Rien n'est enregistré par cela ; les blocs restent dans le monde. |

Refus clairs : pas admin, pas en solo, pas en créatif, id invalide, changements non enregistrés (utiliser `room off` pour les jeter),
gabarit qui sort de la hauteur du monde.

**Attention.** `room new` ne touche à rien. Mais `room load` **vide d'abord toute la boîte du gabarit** (48 x 48 x 24 blocs) avant
de coller : choisis un endroit vide ou sans importance.

## Où se pose la boîte

- Le bloc **sous tes pieds** devient le bloc de sol au milieu de **l'entrée** de la salle.
- La salle s'étend **devant toi** : l'entrée est derrière toi, la sortie à l'autre bout (48 blocs plus loin).
- Ton regard (nord, est, sud ou ouest) fixe l'orientation ; la salle est **enregistrée toujours dans le même repère** (le
  repère canonique, ci-dessous), quel que soit le sens où tu l'as construite.

La boîte est dessinée en monde : cadre cyan sombre pour le gabarit, **vert** pour l'entrée (étiquette ENTREE), **orange** pour la
sortie (SORTIE), un cadre coloré par genre pour chaque emplacement, avec son id.

## Entrée et sortie imposées (à ne pas changer sans coordonner avec le plan)

Elles sont définies à **un seul endroit** : `RoomTemplate` (`edit/RoomTemplate.java`). Le plan fixe devra faire arriver ses
couloirs et ses portes sur ces ouvertures quand il collera une salle dans une place (étape T4, collage).

Repère canonique, en blocs, depuis le coin minimal de la boîte :

| | |
|---|---|
| Taille | `x` 0 à 47 (est), `y` 0 à 23 (haut), `z` 0 à 47 (sud) ; `y = 0` est le sol |
| **Entrée** | côté **sud** (`z = 47`), `x` 22 à 24, `y` 1 à 3 : 3 de large, 3 de haut |
| **Sortie** | côté **nord** (`z = 0`), `x` 22 à 24, `y` 1 à 3 : même taille, dans l'axe de l'entrée |
| Point d'ancrage du constructeur | `x = 23`, `y = 0`, `z = 47` (milieu de l'entrée, au sol) |

Les deux ouvertures doivent rester **vides** (air) dans la salle construite : c'est le plan qui les ferme avec de faux blocs, jamais
la salle elle-même. L'enregistrement est refusé si l'une est obstruée. Il n'y a pas d'autre entrée ni sortie : les `inner_door` sont
des portes intérieures de la salle, la sortie « logique » est l'emplacement de genre `exit` que la dernière étape ouvre.

Orientations : une salle collée dans une place tournée d'un quart de tour tourne avec elle (blocs, entités de bloc et emplacements).
Les orientations des emplacements (`north`, `east`, `south`, `west`) sont dans le **repère de la salle** : le nord est le côté de la
sortie. Le bouton « Face à moi » du formulaire d'un emplacement calcule pour toi l'orientation qui le tourne vers toi.

## Sélectionner dans le monde

Même outil que l'éditeur du plan, mais avec un **bâton en main** (sinon tu construis normalement) :

- clic gauche : coin A ; clic droit : coin B ;
- accroupi : le bloc **devant** la face visée (utile pour de l'air) ;
- `K` ouvre le panneau ; Échap le ferme sans rien perdre.

## Les emplacements

Un emplacement est une boîte marquée dans la salle où le jeu affichera quelque chose. Genres : `terminal`, `keypad`, `pickup`,
`wall_clue`, `document`, `lever`, `plate`, `pedestal`, `inner_door`, `exit`, `secret`.

1. Sélectionne la boîte (le bloc où poser un digicode, une zone de mur pour un indice…).
2. Panneau, onglet **Emplacements** : choisis le genre, **Nouveau**. L'emplacement prend la boîte sélectionnée (coin minimal et
   taille, relatifs à l'origine de la boîte de la salle).
3. Dans sa fiche : id (modifiable, ce qui le renomme partout où il est utilisé), genre, orientation, position et taille
   (« Placer sur la sélection » pour les refaire), « En faire la cachette » (l'objet caché du temps libre ; un seul par salle).
4. La fiche dit quelles étapes l'utilisent.

Un emplacement ne remplace jamais un vrai bloc interactif : le jeu y affiche une entité locale. Ne mets pas de vrai coffre, vrai
levier ou vrai bouton pour « faire vrai ».

## La chaîne

Bouton **Chaîne** du panneau (ou un clic sur un problème qui nomme une étape). À gauche, les étapes dans l'ordre (Ajout, Supp, Haut,
Bas) ; à droite, l'étape choisie :

- **Id** de l'étape (renommable) ; **Mécanisme** (liste, lue sur le backend à l'ouverture de l'éditeur). Le backend a aujourd'hui `debug_button`,
  `three_dials`, `ring_dial`, `keypad`, `pickup_set`, `wiring`, `code_breaker` et `three_levers` (les six mécanismes de T3.5).
- **Paramètres** : des lignes clé / valeur (entier, `true` ou `false`, ou texte). Une valeur hors de l'intervalle connu s'affiche
  en rouge. Changer de mécanisme remet les paramètres à leurs valeurs par défaut.
- **Emplacements des rôles** : chaque rôle du mécanisme est lié à un emplacement. Les rôles et les genres d'emplacement que chacun accepte viennent du
  backend (`slot_roles`) : la liste ne propose que les emplacements d'un genre accepté (`keypad` : un `keypad` ou un `terminal` ; `pickup_set` n'a plus de rôle d'emplacement). Le rôle `exit` ouvre la sortie de la salle : son emplacement doit être de genre `exit`.
- **Prérequis** : les étapes à finir avant celle-ci (cliquer pour cocher). Ce sont les prérequis qui décident de l'ordre d'ouverture ; l'ordre
  de la liste ne sert qu'à la lecture. Une nouvelle étape demande par défaut la précédente.
- **Indices donnés** : un indice du mécanisme (son rôle d'indice), l'emplacement où il s'affiche, et quand (« dès que l'étape est
  ouverte » ou « une fois résolue »).
- **Aides** : trois textes au plus, du plus vague au plus direct.
- En haut à droite : difficulté et durée **estimées** (somme des estimations du catalogue du mod ; le serveur recalcule les vraies
  à l'enregistrement).

### Deux recettes (T3.5)

- **Digicode** : un emplacement `keypad` au mur, une étape `keypad` (paramètre `length`, 3 à 6) liée à lui par le rôle `keypad`, et un **indice donné** `code`
  « dès que l'étape est ouverte » sur un emplacement `wall_clue` ou `document` : sans lui le code n'est montré nulle part et le backend refuse l'enregistrement.
  Les joueurs trouvent le code, reviennent au digicode et le tapent. Trois mauvais essais de suite le verrouillent 10 secondes, puis 20, puis 40 à chaque nouvelle série (l'afficheur le dit).
- **Objets cachés** : autant d'emplacements `pickup` que de rôles d'indice `spot_1`..`spot_n` (réglez `spots` sur ce nombre : **chaque** `spot_n` doit être donné à un
  emplacement, dès l'ouverture), et les paramètres `filled` (combien de cases garnies, tirées
  par la graine à chaque partie) et `items` (la réserve d'objets). Pour joindre un document, remplissez le paramètre `document` (pages séparées par une ligne `---`) et
  donnez l'indice `document` à un emplacement `document`. Les joueurs ramassent les objets là où la graine les a mis : l'étape est
  résolue quand ils sont tous ramassés (aucun dépôt, aucun emplacement à lier à l'étape).
- **Câblage** (`wiring`) : un emplacement `terminal` ou `pedestal` lié au rôle `panel`, une étape `wiring` (paramètres `wires` de 3 à 6 et `colors` de 2 à 5, au plus `wires` : rouge, vert, bleu, puis jaune et blanc), et un **indice donné** « dès que l'étape est
  ouverte » : `scheme` (les couleurs dans l'ordre, la n-ième est celle de la borne de gauche n° n ; chaque couleur a son symbole) sur un `wall_clue` ou un `document`, et/ou `scheme_text` (le même schéma en texte,
  « 1 : rouge, 2 : bleu… », dans la langue du joueur) sur un emplacement `document`. Vous pouvez placer l'un, l'autre ou les deux ; sans aucun des deux le schéma n'est montré
  nulle part et le backend refuse l'enregistrement. Un mauvais câblage complet est un court-circuit : tous les fils sautent. Les joueurs lisent le schéma, retournent au panneau et tirent les fils : une borne de gauche doit arriver à une borne de droite de la bonne couleur.
- **Décodeur** (`code_breaker`) : un emplacement `terminal` ou `pedestal` lié au rôle `terminal`, une étape `code_breaker` (paramètres `length` 3 à 5, `alphabet` 4 à 9, `kind` `symbols` ou `digits`, `repeats`, `maxTries`).
  **Aucun indice** : la logique suffit, les joueurs déduisent la combinaison des réponses « bien placé / mal placé » (les essais restent à l'écran). Avec `maxTries` (6 à 30), un terminal qui manque d'essais
  efface l'historique (la combinaison ne change pas).
- **Trois leviers** (`three_levers`) : trois emplacements `lever` **aux trois coins** de la salle, liés aux rôles `lever_1`, `lever_2` et `lever_3` d'une même étape (chacun son emplacement), et les paramètres
  `ordered` (la graine tire l'ordre des leviers), `gap` (délai maximal entre deux, 2 à 8 s) et `hold` (durée pendant laquelle un clic tient le levier, au moins deux fois `gap`). **Aucun indice** : les joueurs
  cliquent, trois personnes, chacune sur un levier ; l'ordre se trouve par essais (un mauvais ordre ou un retard fait tout lâcher). Il faut **trois joueurs différents** : prévoir la salle pour un groupe de trois.
  Pour l'essayer seul dans la salle d'essai, mettez `solo` à `true` et `hold` haut (jusqu'à 30) : une seule personne peut alors enclencher les trois (à ne jamais laisser dans une vraie salle).

Règles que la salle doit tenir : une étape qui ouvre la sortie, toutes les autres mènent à elle (rien ne mène nulle part), pas de
cycle de prérequis, rien ne dépasse de la boîte, un emplacement ne sert qu'une seule énigme.

## Enregistrer

Bouton **Enregistrer** (panneau ou chaîne) :

1. contrôles locaux (mêmes codes que le backend, plus quelques-uns qui n'existent qu'ici : entrée ou sortie obstruée, salle vide,
   emplacements qui se chevauchent ou recouvrent une ouverture) ; les **erreurs** bloquent, les **avertissements** non ;
2. lecture des blocs et des entités de bloc de la boîte par le serveur intégré (la salle est remise dans le repère canonique) ;
3. `POST /events/trahison/rooms/<id>` avec la définition ; si le backend la refuse, ses erreurs s'affichent comme celles du plan
   (cliquer pour aller à l'élément) ;
4. `POST /events/trahison/rooms/<id>/schematic.json` avec les blocs (base64 dans du JSON).

La version de la salle monte à chaque enregistrement. Si l'envoi des blocs échoue après celui de la définition, le panneau le dit et la
salle reste « non enregistrée » : enregistre de nouveau.

### Format du fichier de blocs

Sponge `.schem` version 2, gzip, racine nommée `Schematic` (solution S11), **toujours en repère canonique** et toujours de
taille 48 x 24 x 48 :
`Version` 2, `DataVersion`, `Width` 48, `Height` 24, `Length` 48, `Palette` (texte de l'état de bloc vers indice, l'air est 0),
`PaletteMax`, `BlockData` (varints, ordre `x + z * Width + y * Width * Length`), `BlockEntities` (`Pos`, `Id`, puis les données),
`Metadata`. Le backend le garde tel quel dans `room.bin` (octets opaques, 16 Mio au plus). Il est relu avec le lecteur de
Phantasmon (`HubSchematic`) pour vérifier ce qui vient d'être écrit.

## Recharger une salle

`room load <id>` : récupère la définition puis les blocs, vide la boîte du gabarit, colle (tournée selon ton regard), et recharge
emplacements et chaîne. Si le backend n'a pas encore de blocs pour cette salle, seuls emplacements et chaîne reviennent.

## Ce qu'il faut essayer à la main (rien n'a encore tourné en jeu)

1. Monde solo plat en créatif, `room new essai`, construire une pièce avec un panneau écrit, une bannière, un escalier orienté.
2. Placer 3 emplacements, une chaîne de 2 étapes (`three_dials` puis `debug_button`, la seconde liée à un `exit`) ; enregistrer.
   Constater : version 1, nombre de blocs et d'entités de bloc affichés.
3. `room list` : la salle y est, « blocs : oui ».
4. Nouveau monde solo, regarder dans une **autre direction**, `room load essai` : la pièce est collée tournée (escalier, panneau et
   bannière bien orientés), la chaîne et les emplacements sont intacts, les emplacements dessinés au bon endroit.
5. Enregistrer sans rien changer, recharger, comparer.
6. Essais de refus : salle sans sortie, étape non liée, emplacement hors boîte, entrée obstruée, id invalide, hors solo ou hors créatif.
7. Option : ouvrir `room.bin` récupéré (route `GET .../schematic`) dans Axiom ou Litematica-convertisseur pour vérifier le
   format (point « à confirmer en jeu » de S11).

## Ce qui manque ailleurs (signalé, non fait ici)

- **Rôles d'emplacement des mécanismes** : `GET /events/trahison/mechanisms` (lu à l'ouverture de l'éditeur par `MechanismCatalog`)
  donne identifiants, paramètres, rôles d'indices, difficulté et durée, mais pas les rôles d'emplacement ; ils viennent de la table
  intégrée du mod. Un mécanisme nouveau du backend n'offre donc que le rôle `exit` tant que la table n'est pas complétée. Si la route
  échoue, la liste intégrée sert et l'écran de la chaîne l'indique.
- ~~**API de Phantasmon** : octets des schématiques par réflexion.~~ Réglé : routes `schematic.json` (base64), `RoomFiles` passe par
  `PhantasmonApi.get` et `post`. Limite : le client HTTP de Phantasmon coupe un appel JSON après 5 s ; un très gros envoi (16 Mio décodés,
  22 Mo de base64) peut dépasser ce délai sur une liaison lente. Les salles réelles (compressées) restent bien en dessous.
