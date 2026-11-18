# Guide de l'éditeur de plan

> Pour la personne qui construit le **complexe fixe** du jeu de trahison (hall, salle de vote, ailes, couloirs...) puis le décrit au
> jeu avec l'éditeur de plan (étape T2.1, paquet client `edit/`). Les salles ont leur propre guide :
> [`guide-editeur-salle.md`](guide-editeur-salle.md). Ici, tout ce qui est **autour** des salles.
>
> Écrit le 2026-11-18 d'après le code (validateur `layout/LayoutValidator`, éditeur `edit/TrahisonEditor` et `EditorScreen`, `world/HubBlocks`,
> `game/GameRooms`, `game/DoorStates`). Ce qui n'est pas dans le code est dit à la fin, dans « Manques » : ne le devine pas.

## Construire et décrire le complexe, pas à pas

### 0. Ce qu'il faut comprendre avant de poser un bloc

- **On construit en vrais blocs, on décrit ensuite.** Le plan est une liste de boîtes (zones, portes, places...) enregistrée au backend,
  par hub, avec un numéro de version. Il ne change aucun bloc du monde.
- **Les portes sont de faux blocs.** Une porte est une **ouverture réelle** (de l'air, jamais un vrai mur) que le mod ferme en
  affichant des blocs fantômes chez chaque joueur. On ne peut pas faire traverser un vrai bloc. Donc : **construis chaque ouverture vide
  (air)**, et décris-la comme porte. Le mod choisit seul le bloc de fermeture (béton rouge / vert / bleu pour une porte de couleur, verre
  gris pour une sortie de salle, planches de chêne noir pour une cabine, obsidienne pour la porte finale, briques pour une porte simple).
  Tous sont pleins : l'ouverture doit être **entièrement** couverte par la boîte, sans trou.
- **Une zone sert à trois choses** : savoir où est chaque joueur, isoler le son, vérifier une action (« il faut être dans sa cabine »). Le
  client calcule la zone du joueur **d'après le bloc de ses pieds** (x, y, z), toutes les secondes au moins. Un joueur dont les pieds ne sont
  dans aucune boîte est « dans aucune zone » : les seuils, cabines et pupitres ne le voient pas.
- **Les zones ne se chevauchent jamais** (le validateur refuse, même en brouillon). Deux boîtes qui se touchent sans partager un bloc sont
  permises ; une même zone peut avoir plusieurs boîtes (un hall en L, par exemple). Les portes, elles, peuvent recouvrir une zone.

### 1. Où vit le complexe

- Dans un **hub miroir** de Phantasmon Events (un hub créé par un admin, avec un rayon, qui couvre toute la hauteur du monde et n'est jamais
  tourné). Le plan est enregistré **par nom de hub**. Le gestionnaire des hubs (touche H, onglet « Hubs Event ») crée le hub ; l'Anchor se pose
  avec `/phantasmon event hub anchor <hub>` (voir [`../plan.md`](../plan.md), section « La copie sur notre serveur dédié »).
- **Coordonnées du plan** (`world/HubBlocks`) : `x` et `z` sont **relatifs au centre du hub**, `y` est celui du monde.
  `bloc du monde = ( floor(origine X de l'Anchor) + x, y, floor(origine Z de l'Anchor) + z )`, quel que soit le lacet de l'Anchor.
  Tu n'as jamais à calculer : l'éditeur convertit les blocs que tu cliques. Mais **tout le complexe doit tenir dans le rayon du hub**, et
  les neuf places de 48 x 48 y prennent beaucoup de place (3 x 3 places d'au moins 48 blocs de côté, plus ailes et couloirs).
- **Tu dois être dans le hub** (Anchor rejointe) pour ouvrir l'éditeur, et avec un **compte admin** de Phantasmon. Sortir du hub coupe l'éditeur.
  L'éditeur marche donc dans le monde du hub (la source sur le serveur, ou une copie), pas dans un monde solo vierge comme l'éditeur de salle.
- Le plan **se rouvre à l'identique** depuis un autre compte admin. Une partie garde la version du plan qu'elle a au démarrage : un
  enregistrement fait pendant une partie ne la touche pas.

### 2. Ouvrir l'éditeur

1. Se placer dans le hub miroir, puis `/phantasmon event trahison edit` (la même commande l'éteint). Le chat répond « Éditeur du plan actif
   pour le hub ... ».
2. **Clic gauche = coin A, clic droit = coin B** d'une boîte, **sans outil en main** (tant que l'éditeur est actif, les clics ne partent
   pas au serveur : on ne casse ni ne pose plus rien). Accroupi, le clic prend le **bloc devant la face visée** (de l'air, par exemple) au
   lieu du bloc visé. Portée de 128 blocs. Un seul coin posé = une boîte d'un bloc.
3. **Touche K** : le panneau latéral (« Éditeur du plan »). Sept onglets : **Zones, Portes, Places, Cabines, Cachettes, Tableau, Étages**.
   « Nouveau » crée un élément avec un id libre (`zone_1`, `door_1`...), « Modifier » ouvre sa fiche, « Supprimer » l'efface (les portes et
   cabines qui le visaient l'oublient). L'id se renomme partout où il est utilisé : lettres, chiffres, `_` et `-`, 1 à 40 signes, unique
   dans son genre.
4. Dans le monde, chaque élément est dessiné (boîtes colorées par type, id flottant) ; le plus récemment choisi est plus marqué. Tout
   change de couleur par type : zone de hall (accent), couloir (clair), salle des portes (maître du jeu), place de salle (bleu), vote
   (violet), cabine (violet sombre), finale (vert), recoin (gris).
5. En-tête du panneau : la version et un voyant « non enregistré » tant que tu as des changements.

### 3. Les éléments à décrire, un par un

Procédé général : **sélectionne la boîte dans le monde** (deux clics), ouvre la fiche, puis bouton « Depuis la sélection » / « Ajouter la
sélection » / « Origine : coin de la sélection ».

#### 3.1 Les zones

Fiche : type, et une ou plusieurs boîtes (« Ajouter la sélection », « Remplacer par elle », « Retirer la dernière », « Tout retirer »).
Types (`hall`, `corridor`, `doors`, `room_slot`, `vote`, `cabin`, `final`, `alcove`) :

| Type | Ce que c'est | Ce que le code en fait |
|---|---|---|
| `hall` | le hall : réveil, résultats | la porte finale s'y ouvre (côté « avant ») ; le tableau des résultats y est ; sert de repli de seuil |
| `corridor` | un couloir | zone sonore à part (une porte ouverte ne mélange pas les sons) |
| `doors` | la salle des 3 portes d'une aile | zone d'où l'on présente son bracelet (`zone_a` d'une porte de couleur) |
| `room_slot` | l'intérieur d'une place de salle | côté « après » (`zone_b`) d'une porte de couleur : y entrer = avoir passé la porte ; sa sortie s'y rattache |
| `vote` | la salle de vote | côté « avant » (`zone_a`) des portes de cabine |
| `cabin` | l'intérieur d'une cabine | c'est la zone où l'on vote ; étanche pour le son quand la cabine est fermée |
| `final` | derrière la porte finale | côté « après » (`zone_b`) de la porte finale : y entrer = être sorti |
| `alcove` | un recoin (cachette, angle) | rien de particulier : un nom sur la carte, une zone sonore à part |

Conseils tirés du code, pas imposés par lui :

- Donne à chaque zone une **hauteur qui couvre tout ce qu'un joueur y fait** (du sol de la pièce au plafond, escaliers compris). Le test se
  fait sur le bloc des **pieds** : un sol d'un étage plus haut que la boîte = « aucune zone ».
- **Couvre tout le complexe** : les parties du hub sans zone ne sont pas des erreurs, mais le son et la carte n'y savent rien.
- Une zone `room_slot` = la boîte de la place (48 x 48 x 24, voir 3.3). Les couloirs qui y mènent sont des zones `corridor` à part, qui ne
  la recouvrent pas.
- **Un seuil par porte de couleur** : si les trois portes d'une aile ont la même `zone_a` (la salle des portes), les trois regardent les
  mêmes joueurs. Fais trois petites zones « seuil » devant chaque porte (type `doors`, par exemple), reliées par le champ « Zone de seuil »
  (voir 3.2). Le code accepte aussi le repli sur la `zone_a`.

#### 3.2 Les portes

Fiche : type, `Zone A`, `Zone B`, et la boîte des **faux blocs** (« Depuis la sélection » : **l'ouverture, pas le mur**). Une porte relie deux
zones ; elle se franchit de A vers B.

| Type | Obligatoire (enregistrement complet) | Particularités |
|---|---|---|
| `color` | **manche** (1 à 3), **couleurs** (deux différentes), `Zone A`, `Zone B`, boîte | `Zone A` = côté hall/salle des portes ; `Zone B` = la zone `room_slot` de la salle ; « Zone de seuil » facultative (sinon `Zone A`) |
| `room_exit` | `Zone A`, `Zone B`, boîte | fermée pendant les salles tant que la salle de son groupe n'est pas résolue ; ouverte le reste du temps |
| `cabin` | `Zone A` (vote), `Zone B` (cabine), boîte | s'ouvre/se ferme selon l'occupation de la cabine ; une par cabine |
| `final` | `Zone A` (hall), `Zone B` (zone `final`), boîte, **pupitre** | fermée toujours, sauf pour ceux qui ont 9 points quand elle s'ouvre. **Le « Pupitre »** (champ `threshold`) est la zone où se tenir pour l'actionner : obligatoire |
| `plain` | `Zone A`, `Zone B`, boîte | toujours ouverte (sauf si le maître du jeu la force) ; elle peut servir de porte de fermeture pour un objet (cadenas) |

Détails qui comptent :

- **Les symboles.** Une porte de couleur est moitié d'une couleur, moitié de l'autre : la boîte est coupée en deux dans sa plus longue
  dimension horizontale (le bloc de la première couleur d'un côté, de la seconde de l'autre ; une colonne d'un bloc de large est coupée de
  haut en bas). Il faut donc une boîte d'**au moins 2 blocs** de large. Au-dessus de la porte, pendant `DOORS`, un panneau flottant montre les deux
  couleurs avec leur **symbole** (rouge = triangle, vert = disque, bleu = carré), ce qu'il manque au seuil, et le compte à rebours.
  Aucun réglage de symbole dans le plan : ils viennent du mod. Laisse de la **hauteur libre** au-dessus de la porte (le panneau ne se voit
  pas à travers un mur).
- **La paire de couleurs** : les trois portes d'une manche sont `red_green`, `red_blue`, `green_blue`. Dans la fiche, choisis les deux couleurs
  (l'éditeur offre les trois paires ; l'ordre des deux couleurs n'a pas d'importance).
- **Quelle salle pour quelle porte (piège).** Le jeu donne aux groupes de la manche les places **dans l'ordre du plan** : la 1re place de la
  manche (dans la liste des places, onglet Places) va au groupe de la porte **rouge-vert**, la 2e au groupe **rouge-bleu**, la 3e au groupe
  **vert-bleu**. Il faut donc que la porte rouge-vert de la manche mène (par `Zone B`) à la zone de la **1re** place de la manche, etc. Le
  validateur ne le vérifie pas.
- **Sortie de salle.** Pour savoir à quel groupe appartient une porte `room_exit`, le jeu cherche une porte de couleur dont la `Zone B` est une
  zone `room_slot` que la `Zone A` ou la `Zone B` de la sortie nomme aussi. **Mets donc `Zone A` = la zone `room_slot` de la salle, `Zone B` = le
  couloir de sortie.** Sans ce lien, il se rabat sur l'ordre des portes de sortie du plan (la i-ième = place `i mod 3` de la manche `i / 3 + 1`).
- **Cabines.** Voir 3.4. Une porte de cabine est dans l'ouverture de la cabine : `Zone A` = la zone `vote`, `Zone B` = la zone `cabin`.

#### 3.3 Les places de salle

Fiche : manche (1 à 3), orientation, origine. Une place est un **carré de 48 x 48 blocs au sol sur 24 de haut** : l'éditeur dessine le cadre.

- **Origine** : « Origine : coin de la sélection » prend le **coin minimum** de la sélection (x et z les plus petits). Un seul clic (un bloc)
  suffit : clique le bloc **du sol**, au coin nord-ouest de la place. Le sol de la salle est à `y = origine.y` (le bloc d'origine est un bloc de sol),
  la salle monte de 23 blocs au-dessus.
- **Orientation = le côté où se trouve la SORTIE de la salle** (le « nord » de la salle ; son entrée est donc du côté opposé). Les ouvertures
  sont toujours 3 de large sur 3 de haut, au-dessus du sol (`y` origine + 1 à + 3). Les voici, à partir de l'origine (coin minimum) :

  | Orientation | Sortie | Entrée |
  |---|---|---|
  | `north` | côté nord : `z` + 0, `x` + 22 à + 24 | côté sud : `z` + 47, `x` + 22 à + 24 |
  | `east` | côté est : `x` + 47, `z` + 22 à + 24 | côté ouest : `x` + 0, `z` + 22 à + 24 |
  | `south` | côté sud : `z` + 47, `x` + 23 à + 25 | côté nord : `z` + 0, `x` + 23 à + 25 |
  | `west` | côté ouest : `x` + 0, `z` + 23 à + 25 | côté est : `x` + 47, `z` + 23 à + 25 |

  (Le décalage de 1 pour sud et ouest vient du quart de tour : la salle est un carré de 48, ses ouvertures sont à 22 à 24 dans son propre
  repère. Ce sont les mêmes blocs que ceux que la salle doit laisser en air, voir `guide-editeur-salle.md`, « Entrée et sortie imposées ».)
- **Ce que tu construis dans la place** : rien (ou seulement un sol et des murs provisoires). L'admin du serveur y **colle** la salle tirée
  avant chaque soirée (`export`, puis Axiom). Tout ce qui est dans la boîte sera écrasé. **Raccorde tes couloirs aux deux ouvertures**, et
  laisse la boîte de 48 x 48 x 24 **libre** de tout autre élément du complexe.
- **Les trois places d'une manche** (même numéro de manche) : l'ordre de leur création est l'ordre des groupes (voir le piège en 3.2). Les
  manches au-delà de la 3e rejouent le cycle (la 4e utilise les places de la 1re).
- Les places n'ont pas d'orientation « idéale » : les trois d'une manche peuvent regarder dans trois sens différents, tant que chaque
  entrée est reliée à son couloir.

#### 3.4 Les cabines et la salle de vote

- **La salle de vote** est une zone de type `vote` ; elle est **partagée par les trois manches**. Aucun champ propre.
- **Six cabines exactement**, chacune avec : `Sert` (une paire de couleurs : `red_green`, `red_blue`, `green_blue`), `Côté` (`pair` ou
  `solo`), `Zone` (une zone de type `cabin`) et `Porte` (une porte de type `cabin`). Il faut **une cabine côté paire et une côté solo pour
  chacune des trois paires** : 3 x 2 = 6, pas une de plus ni de moins, sans doublon.
- Une cabine = une **zone `cabin`** (l'intérieur, assez grand pour **deux** personnes côté paire) + une **porte `cabin`** (l'ouverture, entre la
  salle de vote et l'intérieur). Le joueur doit être dans **sa** cabine (celle de sa paire de couleurs, de son côté) pour voter ; la porte se
  ferme aux autres quand un membre de son groupe y est entré. Prévois une **sortie** praticable : la porte se rouvre quand le groupe a voté.
- **Les murs de la cabine sont de vrais blocs** ; seule l'ouverture est une porte (air + faux blocs).
- Le pupitre de vote n'est pas à construire : il s'ouvre tout seul quand la cabine est verrouillée.

#### 3.5 La porte finale

- Une **zone `final`** (derrière la porte) et **une seule porte de type `final`**, de `Zone A` = hall à `Zone B` = zone `final`.
- Son **pupitre** : une zone devant la porte (le joueur s'y tient pour l'actionner, avec 9 points). Le plus propre : **une petite zone propre**
  devant la porte (par exemple de type `hall`, découpée **hors** de la zone du hall principal puisque les zones ne se chevauchent pas), reliée au champ
  « Pupitre ». Sans pupitre, l'enregistrement complet est refusé.
- La porte s'ouvre quelques secondes (réglage `final_door_seconds`) **pour ceux qui ont 9** ; les autres voient toujours le mur. Les
  gagnants franchissent la porte jusqu'à la zone `final`. **Il n'y a rien à construire derrière la zone `final` pour le jeu**, mais
  c'est la sortie : fais-en un lieu où l'on a envie d'être.

#### 3.6 Le hall et le tableau des résultats

- Le **hall** : au moins une zone de type `hall` (le validateur ne l'exige pas, mais la porte finale et la carte y comptent).
- **Le tableau** (onglet Tableau, facultatif, un seul) : un pan de mur d'au moins 4 x 4 blocs (jusqu'à 64) où s'affichent les résultats
  des votes. Sélectionne le **pan de mur**, choisis le côté où se tiennent les lecteurs (« Côté lecteurs »), puis « Ajuster à la sélection » (Maj :
  tailles par 4). Sans tableau, les résultats s'affichent dans le HUD.

#### 3.7 Les cachettes

- Onglet Cachettes : un **point** par cachette (le coin minimum de la sélection : un bloc suffit). Les objets cachés (couteau, liens, cadenas,
  oreille, bouclier, passe, lettre : 7 sortes, un de chaque par défaut, 2 au plus de chaque) y sont répartis au hasard au début de la partie.
- **Un objet par cachette.** Au moins **7 cachettes** pour tout placer avec les réglages par défaut ; les objets en trop vont dans la
  cachette active des salles (D260) ou sont écartés. Le validateur n'impose aucun nombre, même complet.
- On les trouve à **3 blocs ou moins** : le joueur doit être près, au bon étage. Place-les dans les **recoins, les couloirs, derrière un meuble**,
  loin les unes des autres, et pas dans une place de salle (la place est collée, la cachette serait enterrée).

#### 3.8 Les étages (T11)

- Onglet Étages, facultatif : une **tranche de hauteurs** (`y` le plus bas, `y` le plus haut, bornes comprises) et un nom. Utilisés par la
  **carte** (touche M) : une image par étage. « Hauteurs de la sélection » prend les deux hauteurs de la boîte choisie.
- Les tranches **ne se chevauchent pas** (le validateur refuse). Un élément **à cheval** sur deux étages (un escalier) apparaît sur les deux. Un
  élément hors de toutes les tranches n'apparaît sur aucun. **Sans étage déclaré, la carte en a un seul.**
- Si tu déclares des étages, couvre **tout** le complexe (tous les sols).

### 4. Les minimums que le code exige (enregistrement « complet »)

| Quoi | Nombre | Détail |
|---|---|---|
| Portes de couleur | **9** : 3 par manche | une par paire (`red_green`, `red_blue`, `green_blue`) et par manche, pas deux de la même paire dans une manche |
| Places de salle | **9** : 3 par manche | numéros de manche 1, 2, 3 (la 4e manche rejoue la 1re) |
| Cabines | **6** | une côté `pair` et une côté `solo` pour chaque paire |
| Porte finale | **exactement 1** | avec son pupitre (`threshold`) |
| Cachettes | 0 imposé, **7 conseillées** | une par objet |
| Tableau des résultats | 0 ou 1 | |
| Étages | 0 ou plus | sans chevauchement |

Les autres éléments sont libres : zones (autant que tu veux), portes `room_exit` (en principe une par place, **neuf**), portes `cabin` (six),
portes `plain`. Chaque porte de cabine et chaque cabine fait référence à une zone/porte **du bon type**.

### 5. Ce que refuse l'enregistrement

Deux niveaux. **Toujours refusé, même en brouillon** : identifiant invalide ou en double (`ERROR_TRAHISON_LAYOUT_BAD_ID`, `_DUPLICATE_ID`),
type inconnu (`_BAD_TYPE`), boîte mal formée (`_BAD_BOX`), manche hors de 1 à 3 (`_BAD_ROUND`), couleurs d'une porte qui ne sont pas deux
couleurs différentes (`_BAD_COLORS`), **zones qui se chevauchent** (`_ZONE_OVERLAP`), **étages qui se chevauchent** (`_FLOOR_OVERLAP`).

**Refusé seulement en complet** : champ vide (`_MISSING_FIELD` : type, boîte, zone A ou B d'une porte ; manche ou couleurs d'une porte de couleur ;
manche, origine ou orientation d'une place ; `sert` et `côté` d'une cabine ; position d'une cachette ; nom ou hauteurs d'un étage ; pupitre de la
porte finale), référence vers une zone qui n'existe pas ou d'un autre type (`_BAD_REFERENCE`), **9 portes de couleur mal réparties**
(`_ROUND_DOORS`), **cabines** pas six ou mal réparties (`_CABINS`), **places** pas trois par manche (`_ROOM_SLOTS`), **porte finale** pas
exactement une (`_FINAL_DOOR`).

En cas de refus, le panneau affiche un bouton « N erreurs » : chaque ligne dit en français ce qui ne va pas et **les ids concernés** ; clique les éléments
nommés pour les corriger. Tout est rapporté d'un coup, pas une erreur à la fois.

### 6. Enregistrer et recharger

- **Enregistrer** : le plan est enregistré comme **complet** (version suivante). **Brouillon** : enregistre le plan tel quel, sans les contrôles
  de complétude (utile pour ne rien perdre en cours de route ; un jeu ne démarre que sur un plan complet).
- Chaque enregistrement crée une **nouvelle version**. Si quelqu'un a enregistré une version plus récente depuis que tu as chargé : refus
  `LAYOUT_CONFLICT` → **Recharger**, refais tes changements, enregistre.
- **Recharger** reprend le dernier plan enregistré (il demande confirmation s'il y a des changements non enregistrés). L'éditeur recharge tout
  seul le plan du hub quand tu l'allumes.
- Un plan enregistré **avant le 2026-11-16** avec une Anchor dont l'origine a une partie fractionnaire de `.5` ou plus est **décalé d'un bloc** :
  rouvre-le et vérifie (voir `architecture.md`, section 6). Sans importance pour un plan neuf.

### 7. Liste de contrôle avant la première partie

**Construction** (en vrais blocs, dans le hub) :

- [ ] Le hall (réveil, tableau), les **couloirs**, la **salle de vote** avec ses **6 cabines**, la **porte finale** et la zone derrière.
- [ ] Les **3 ailes** : une par manche, chacune avec sa salle des portes et **3 places** de 48 x 48 x 24 (laisse-les vides, sauf les
  ouvertures de chaque place, raccordées aux couloirs).
- [ ] Toutes les **ouvertures de porte sont de l'air** (3 x 3 pour les entrées et sorties de place, la taille voulue ailleurs), sans mur de
  vrai bloc dedans. Aucune porte « physique » (bois, fer) : elles ne servent à rien et peuvent bloquer.
- [ ] De la **hauteur libre** au-dessus de chaque porte de couleur (panneau flottant) ; des **recoins** pour les cachettes.

**Description** (éditeur de plan) :

- [ ] Zones : hall, couloirs, **3 salles des portes**, **9 `room_slot`**, `vote`, **6 `cabin`**, `final`, et le pupitre ; **aucune ne se chevauche**,
  chacune couvre les pieds des joueurs sur toute sa hauteur.
- [ ] **9 portes de couleur** (3 par manche, une par paire), chaque `Zone B` = la `room_slot` **du bon groupe** (1re place de la manche = rouge-vert,
  2e = rouge-bleu, 3e = vert-bleu), avec son **seuil** propre.
- [ ] **9 places** (3 par manche), origine au sol du coin minimum, orientation = côté de la sortie ; leurs ouvertures tombent sur des couloirs.
- [ ] **9 portes `room_exit`** (`Zone A` = la `room_slot`), **6 portes `cabin`**, **1 porte `final`** avec son pupitre.
- [ ] **6 cabines** (3 paires x `pair`/`solo`), chacune avec sa zone et sa porte.
- [ ] Au moins 7 **cachettes** ; le tableau des résultats ; les étages (si la carte doit en avoir plusieurs).
- [ ] **Enregistrer** (pas Brouillon) : aucune erreur, « Enregistré : version N (complet) ».

**Essai** (à deux comptes, voir [`premier-essai.md`](premier-essai.md), section 4) :

- [ ] `/phantasmon event trahison door <id> open` puis `close` : la porte apparaît et disparaît chez les deux comptes ; un joueur ne la traverse
  pas fermée, la traverse ouverte. Fais-le pour **chaque** porte du plan.
- [ ] Les couleurs et symboles d'une porte de couleur se lisent ; le panneau n'est pas caché.
- [ ] Une zone à la fois : marche d'une pièce à l'autre et vérifie que la zone affichée (écran du maître du jeu) change là où tu l'attends.
- [ ] Après un export, **colle une place** et vérifie que ses ouvertures tombent sur les couloirs et que la **vérification du collage** dit OK
  (`premier-essai.md`, section 5, et `procedure-soiree.md`).
- [ ] Recharge le plan depuis un second compte admin : il est identique.

## Manques

Ce que le code ne dit pas (ou ne vérifie pas) et qu'il faudra trancher ou constater en jeu :

1. **Aucun hub, aucun rayon ni aucune position du complexe n'est fixé.** Le code ne dit ni le nom du hub de la soirée, ni son rayon, ni où
   se place l'origine par rapport au monde du serveur dédié. À décider par le propriétaire.
2. **Pas de notion d'« aile » ni de « salle des portes » dans le plan** : ce sont des zones de type `doors`. Rien ne force trois ailes.
3. **Le lien « porte de couleur -> bonne place » est validé** (`ERROR_TRAHISON_LAYOUT_DOOR_PLACE`) : la N-ième paire d'une manche (rouge-vert, rouge-bleu, vert-bleu) mène à la N-ième place de cette manche, dans l'ordre de la liste ; une place manquante est refusée avec ce lien écrit dans le message. Ce qui reste non vérifié : que l'ordre des places corresponde à ce que tu as construit.
4. **La `Zone B` d'une porte de couleur n'a pas à être une `room_slot`** pour que le plan soit accepté (seule la `room_exit` en dépend pour son lien de groupe).
5. **Pas de contrôle** que les ouvertures d'une place soient raccordées, qu'il y ait un hall, un nombre de portes `room_exit`, ni que le complexe tienne dans un rayon de hub (**aucun rayon de hub n'existe**, voir le point 1 : le contrôle « place hors du hub » est donc sauté). Désormais contrôlés : deux places qui se chevauchent (erreur `PLACE_OVERLAP`), une boîte de place qui recouvre une zone autre que `room_slot` (avertissement `WARN_TRAHISON_LAYOUT_PLACE_ZONE`).
6. **Taille et position exactes des boîtes de porte** : aucune règle (ni qu'une porte de couleur soit à l'entrée de la place plutôt que dans le couloir). Seul
   conseil : couvrir toute l'ouverture, 2 blocs de large au moins pour les couleurs.
7. **Pas de réglage de symbole ni de bloc de fermeture par porte dans le plan** (tout est dans `DoorBlocks` et `DoorPanels`).
8. **La porte `plain` ne sert à rien de précis** dans le code (toujours ouverte, forçable par le maître du jeu). Le cadenas des objets agit sur « une porte ordinaire ou
   une cabine » ; je n'ai pas vérifié quelles portes il accepte.
9. **Un jeu ne démarre que sur un plan complet et valide** (corrigé) : `start` refuse sinon avec `ERROR_TRAHISON_PLAN_INVALID`, y compris un hub sans plan, sauf pour une partie d'essai d'une salle (`test_room`), qui se joue sans le complexe. Les **avertissements** (non bloquants) sont renvoyés avec le plan (`warnings[]`) : `WARN_TRAHISON_LAYOUT_PLACE_ZONE`, `WARN_TRAHISON_LAYOUT_FEW_SPOTS` (moins de cachettes dans le plan que d'objets au tirage par défaut), `WARN_TRAHISON_LAYOUT_PLACE_FLOOR` (des étages existent mais aucun ne contient la hauteur de l'origine d'une place). L'éditeur du client ne les affiche pas encore.
10. **Le rôle exact des types `corridor` et `alcove` pour le son** (T5) : lu seulement dans `plan.md` et `T5-vocal.md` (une zone = un groupe sonore) ; non vérifié dans le code du vocal.
11. **Sol de la place** : la salle est écrite avec son sol à `y = 0` ; que le sol de ton couloir soit au même niveau que `origine.y` reste une condition de raccordement non vérifiée (seul l'avertissement `PLACE_FLOOR` regarde la hauteur par rapport aux étages).
