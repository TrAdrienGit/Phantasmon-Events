# Plan : le jeu de trahison

> Brouillon du 2026-11-13 (troisième version, après les réponses du même jour), à valider. Il rassemble les décisions prises ce jour-là, les propositions qui en
> découlent et les questions encore ouvertes. Les propositions sont marquées **(proposition)**.

## 1. Objectif

Une soirée pour 9 joueurs de la communauté : coopérer dans des salles d'escape game, puis voter en secret
« s'allier » ou « trahir » contre ceux avec qui on vient de coopérer. On veut le doute permanent : « est-ce qu'il
me trahit ? ». Le jeu doit être rejouable plusieurs soirs, avec des salles et des solutions qui changent.

Le déroulé suit de près celui du jeu qui nous inspire (étapes, ordre, durées). Le scénario, les noms, les salles
et les personnages sont les nôtres : ils viennent du lore de la communauté, pas du jeu d'origine.

Priorité : d'abord le jeu de trahison, ensuite l'habillage Cobblemon.

## 2. Décisions prises (2026-11-13)

| Sujet | Décision |
|---|---|
| Joueurs | Exactement 9, ni plus ni moins |
| Spectateurs | Autorisés, clairement distingués des joueurs |
| Manches | 3, puis la porte finale |
| Une manche | Choix des portes, salle d'escape game d'environ 30 minutes, temps libre (discussion, exploration, retour dans les salles finies, secrets cachés), vote de 5 minutes, résultats |
| Maître du jeu | Possible mais pas obligatoire : tout le déroulé tourne seul, le maître du jeu peut reprendre la main |
| À 0 point, ou tué | Le joueur devient un fantôme (comme dans le lore et dans la partie faite à la main). Un fantôme ne vote plus et passe du côté des spectateurs |
| Le meurtre | Par un objet caché, le couteau : un seul usage, il tue à coup sûr celui sur qui on l'utilise. Sa rareté empêche le massacre |
| Entraver | Les joueurs peuvent aussi neutraliser quelqu'un sans le tuer : l'attacher quelque part, l'enfermer dans une pièce |
| La porte finale | Elle ne lit que le bracelet du poignet, celui reçu au début, qu'on ne peut pas retirer. Un bracelet ramassé ne fait pas sortir |
| Spectateurs et fantômes | Ils entendent les joueurs et se parlent entre eux. Les joueurs ne les entendent pas |
| Fichiers à coller | L'admin utilise Axiom : fichiers `.litematic` (Litematica) ou `.schem` (WorldEdit) |
| Les points | Ils sont sur le bracelet, pas sur le joueur. Le bracelet d'un mort garde ses points, vote toujours « Allier », et peut être ramassé : son porteur a alors une couleur de plus, donc d'autres portes |
| Victoire | Seuls ceux qui ont 9 points passent la porte finale, qui ne s'ouvre qu'une fois |
| Fin collective | Possible : tant que personne n'ouvre la porte finale, les votes reprennent en boucle, et un groupe qui se fait confiance peut faire monter tout le monde à 9. Aux joueurs de le découvrir |
| Rôles secrets | Oui, 1 à 3 par partie, dont un infiltré qui doit trahir à la première manche. Tous les rôles sont secrets |
| Information | Points et votes de tous révélés après chaque vote |
| Vote d'une paire | En cabine : celui qui entre et ferme la cabine vote. On peut donc doubler son partenaire en l'enfermant dehors |
| Construction | Salles construites à la main dans Minecraft, à un format standard, enregistrées en schématiques |
| Le complexe | Son plan ne change jamais (hall, salle de vote, couloirs, places des salles). Seul le contenu des 9 places change d'une soirée à l'autre. Un admin du serveur colle les salles avant chaque soirée |
| Taille des salles | Une place de 48 × 48 blocs au sol sur 24 de haut, avec couloirs, embranchements et recoins, pour qu'on s'y disperse et qu'on s'y perde un peu |
| Énigmes | Pas limitées aux mécaniques de Minecraft : de vrais mini-jeux à l'écran, avec leurs propres menus, comme dans le jeu d'origine |
| Salle non finie à 30 minutes | La sortie s'ouvre pour garder le rythme, et chaque membre du groupe perd 1 point |
| Défi à 1 point entre joueurs | Pas pour la première version |
| Animateur | Mystaria, le premier fantôme |
| Rejouabilité | Solutions tirées au hasard et grande banque de salles, avec des façons de résoudre variées |
| Lieu | Un hub miroir de Phantasmon Events, sur le serveur de la communauté (qui a Simple Voice Chat) |
| Vocal | Contrôlé par le jeu : portée courte, salles isolées les unes des autres, cabines étanches |
| Déconnexion | Aucune sanction, on attend. Un mannequin au skin du joueur reste sur place, marqué « déconnecté ». Pas d'IA de remplacement |

## 3. Déroulé d'une partie

Durée visée : environ 3 heures.

| Étape | Durée | Ce qui se passe |
|---|---|---|
| Prologue | 10 min | Les 9 se réveillent dans le hall. L'animateur explique les règles. Chacun découvre son bracelet et, en privé, son rôle secret s'il en a un |
| Manche 1 à 3 : les portes | 5 min | Les bracelets reçoivent leur couleur et leur statut (paire ou solo). Les joueurs négocient qui passe quelle porte, puis les portes se ferment |
| La salle | 30 min | Chaque groupe de 3 résout sa salle d'escape game |
| Temps libre | 10 min | Les portes se rouvrent. Discussions, exploration, recherche de secrets, retour dans les salles déjà finies |
| Le vote | 5 min | Dans les cabines : paire contre solo, pour chaque porte |
| Les résultats | 3 min | Votes et points affichés à tous, dans le hall |
| La porte finale | libre | Quiconque a 9 points peut l'ouvrir. Sinon, un nouveau vote recommence toutes les 10 minutes |

### Les bracelets et les portes

- Chaque joueur commence avec **3 points**.
- À chaque manche, le jeu attribue 3 couleurs. Pour chaque couleur : une **paire** (2 joueurs) et un **solo**.
- Il y a 3 portes. Chacune mélange deux couleurs et laisse passer une paire d'une couleur et le solo de l'autre.
  Il existe donc exactement deux répartitions possibles, et les 9 joueurs doivent se mettre d'accord sur l'une.
- **(proposition)** Les couleurs sont habillées en types Pokémon, faciles à lire : Feu (rouge), Plante (vert), Eau
  (bleu). Les portes portent deux types : Feu-Eau, Feu-Plante, Plante-Eau.
- **(proposition)** Le jeu compose les paires et les solos pour qu'on change de partenaires à chaque manche et
  qu'on n'affronte pas deux fois la même personne tant que c'est possible.
- Un joueur qui n'a passé aucune porte à la fermeture perd des points. **(proposition)** −1 point, à trancher.

### Le vote

- Pour chaque porte, la paire affronte le solo. Deux cabines par porte, donc 6 cabines.
- Barème :

| Mon vote | Vote adverse | Moi | L'adversaire |
|---|---|---|---|
| Allier | Allier | +2 | +2 |
| Trahir | Allier | +3 | −2 |
| Allier | Trahir | −2 | +3 |
| Trahir | Trahir | 0 | 0 |

- Les deux membres d'une paire reçoivent le même résultat, quel que soit celui des deux qui a voté.
- Pas de vote à la fin du temps : « Allier » par défaut, comme dans le jeu d'origine. C'est une faiblesse voulue :
  enfermer son partenaire hors de la cabine, ou retenir un solo, devient une arme.
- Le bracelet d'un mort vote toujours « Allier ». On peut donc le trahir sans risque : +3 pour soi, −2 pour lui.
- Trois alliances de suite donnent exactement 9. Une seule trahison subie rend la sortie impossible en 3 manches.

### La mort, les bracelets et les fantômes

- Les points appartiennent au **bracelet**. Un joueur meurt quand son bracelet tombe à 0, ou quand il est tué au
  couteau.
- Le bracelet du poignet ne se retire pas. Sur un mort, on peut le prendre (dans l'histoire : lui couper la main).
- Un corps reste là où le joueur est mort : un mannequin à son skin, couché. C'est sur lui qu'on ramasse le
  bracelet. **(proposition)** La mort n'est pas annoncée : on la découvre en trouvant le corps, ou au plus tard
  aux résultats suivants.
- Le bracelet d'un mort garde ses points et sa couleur :
  - il vote « Allier » à chaque manche, à sa place dans sa paire ou comme solo ;
  - son porteur a une couleur de plus, donc le choix entre plus de portes ;
  - il ne fait pas passer la porte finale.
- **(proposition)** Une porte s'ouvre dès qu'un bracelet « paire » d'une couleur et un bracelet « solo » de l'autre
  sont présentés, quel que soit le nombre de porteurs. Les bracelets des morts gardent donc leur utilité.
- Le mort devient un fantôme : skin en noir et blanc, et il rejoint les spectateurs.

### Le couteau et les entraves

Ce sont des objets cachés dans le complexe, à trouver pendant le temps libre ou dans les salles.

| Objet | Usage | Effet |
|---|---|---|
| Le couteau | Une fois, à bout portant | Tue la cible à coup sûr |
| Les liens | Une fois, à bout portant | Attache la cible sur place. Elle ne bouge plus tant qu'un autre joueur ne la détache pas |
| Le verrou | Une fois, sur une porte ou une cabine | La ferme. Elle se rouvre de l'extérieur seulement, ou après un délai |

- **(proposition)** Un seul couteau par partie par défaut ; le maître du jeu règle le nombre de chaque objet (0 à 2).
- Un joueur attaché ou enfermé au moment du vote ne vote pas : « Allier » par défaut. C'est tout l'intérêt.
- **(proposition)** Les liens tiennent 10 minutes au plus, et le verrou 5, pour que personne ne passe la soirée
  immobile.
- Limite technique : le mod ne commande pas le serveur. Le mort reste un vrai joueur connecté ; c'est le mod des
  autres qui cesse de l'afficher et de l'entendre comme un vivant, et le mod de l'attaché qui bloque ses
  déplacements. Cela suffit entre joueurs de confiance.

### Spectateurs, fantômes et déconnexions

- Spectateurs et fantômes sont traités pareil :
  - ils entendent les joueurs et se parlent entre eux ;
  - les joueurs ne les entendent pas ;
  - ils se déplacent librement, traversent les portes fermées et ne touchent à rien.
- Les joueurs les voient en silhouettes translucides (noir et blanc pour les fantômes), ou pas du tout : réglage
  du maître du jeu.
- Les spectateurs qui n'ont jamais joué regardent depuis une copie du hub ; les fantômes restent où ils sont.
- Un joueur déconnecté laisse un mannequin debout à son skin, avec « déconnecté » près de son pseudo. Les chronos
  se mettent en pause si le maître du jeu le demande, ou tout seuls pendant 5 minutes en mode automatique.

## 4. Casser la confiance

C'est le travail le plus difficile de la partie faite à la main : des amis se font confiance. Le jeu doit tenter
et semer le doute à la place du maître du jeu. Tout ce qui suit est **(proposition)**.

### Rôles secrets

Le jeu en tire 2 ou 3 par partie. Les autres joueurs n'ont pas de rôle. Tous savent que des rôles existent, pas
lesquels. Ils sont révélés à la fin.

| Rôle | Contrainte | Récompense |
|---|---|---|
| L'Infiltré | Doit trahir à la manche 1 | +1 point s'il le fait |
| Le Passeur | Allié des fantômes | Gagne si au moins 2 joueurs sont fantômes à la fin, même s'il ne sort pas |
| Le Gardien | Ne peut jamais trahir | Gagne si au moins 5 joueurs passent la porte finale |
| Le Voyant | Une fois dans la partie, voit le vote d'une cabine avant les résultats | Aucune, c'est un pouvoir |
| Le Parieur | Avant chaque vote, parie en secret sur le vote de son adversaire | +1 point par pari juste, une fois par partie au plus |
| Le Solitaire | Veut gagner seul | Gagne seulement s'il est le seul joueur à atteindre 9 points |

### Tentations automatiques

- **L'offre en cabine :** à chaque vote, un joueur tiré au sort reçoit une offre privée (« Trahis maintenant :
  +1 point de plus »). Tous savent qu'une offre a été faite, personne ne sait à qui.
- **Les fuites :** après les résultats, l'animateur glisse à un joueur une information vraie et troublante
  (« Ton partenaire a mis 40 secondes à voter », « Quelqu'un a cherché à entrer dans ta cabine »).
- **Les secrets du temps libre :** des objets cachés dans le complexe, à usage unique. Les chercher oblige à
  s'isoler, ce qui rend suspect.
  - Le couteau, les liens, le verrou (section 3).
  - L'Écoute : entendre une cabine pendant un vote.
  - Le Bouclier : annuler un −2, une fois.
  - Le Passe : rouvrir une cabine fermée.
  - La Lettre : faire parvenir un message anonyme à un joueur.
- **La course :** le premier groupe sorti de sa salle a plus de temps libre, donc plus de secrets. Les autres le
  savent.
- **L'affichage :** les points de tous sont sur les bracelets. Un joueur à 1 ou 2 points est dos au mur, et tout
  le monde le voit.

Le maître du jeu garde ses outils : chuchoter à un joueur, déclencher une offre, donner un indice, corriger des
points, mettre en pause, forcer une étape.

## 5. Les salles d'escape game

### Le problème à résoudre

Dans la partie faite à la main, le plus dur était de construire 9 salles variées et de même difficulté pour les 3
groupes d'une même manche. Le plan répond par quatre choses :

1. La salle (le décor) est séparée de l'énigme (la logique). On construit des décors avec des emplacements ; le
   jeu y installe des mécanismes.
2. Chaque mécanisme a une difficulté et une durée estimées. Les 3 salles d'une manche ont le même total.
3. Le jeu mesure le temps réel de chaque étape à chaque partie, et corrige ses estimations.
4. Des indices automatiques rattrapent un groupe en retard (à 10, 18 et 25 minutes sans progrès).

### Une salle

- Un décor construit à la main, enregistré en schématique.
- Des **emplacements** marqués dans le décor avec un outil du mod : digicode, terminal, cachette, mur d'indice,
  socle, levier, plaque, porte intérieure, sortie.
- Une **chaîne d'étapes** : 4 à 6 mécanismes du catalogue, reliés (la sortie de l'un donne l'entrée du suivant).
  Elle est écrite une fois par salle, dans un éditeur en jeu. Les valeurs (codes, ordres, positions des indices)
  sont tirées au hasard à chaque partie.
- Un état gardé par le serveur du jeu : remettre une salle à zéro est instantané.
- Rien ne repose sur le contenu de vrais coffres (le miroir ne le copie pas) : l'inventaire est virtuel.

Si un groupe n'a pas fini à 30 minutes, la sortie s'ouvre quand même et chaque membre du groupe perd 1 point.

### Catalogue des mécanismes

Chaque mécanisme sait tirer une solution au hasard et produire les indices qui vont avec.

**Mini-jeux à l'écran** (le cœur du catalogue : des menus dédiés, un jeu dans le jeu)

Un terminal, une machine ou un objet du décor ouvre un écran plein, dessiné pour l'énigme, à la qualité du menu de
Showtime. Plusieurs joueurs peuvent ouvrir le même écran et le manipuler ensemble : chacun voit les gestes des
autres en direct.

- Cadran à anneaux : des anneaux concentriques à tourner pour aligner des symboles ; tourner l'un entraîne un autre.
- Tuiles à pivoter : reconstituer un circuit ou une image.
- Blocs coulissants : dégager la pièce qui doit sortir.
- Câblage : relier des bornes sans croiser les fils, selon un schéma trouvé ailleurs.
- Coffre à molette : sentir le bon cran au son et à la vibration de l'écran.
- Dé à faire rouler sur une grille pour qu'il arrive sur la bonne face.
- Balance à plateaux, jerricans à transvaser, dosage de liquides.
- Console radio : régler deux molettes pour superposer deux ondes.
- Carte du ciel : relier des étoiles selon un indice.
- Document déchiré : assembler les morceaux à la souris.
- Vieux terminal : un petit système à fouiller, avec des mots de passe cachés dans les documents de la salle.
- Écrans jumeaux : deux terminaux éloignés montrent chacun la moitié du problème.
- Version Pokémon : Boîte PC à trier selon une règle, Pokédex abîmé à compléter, décodeur Zarbi à molettes,
  machine de soin à remettre en ordre.

**Codes et serrures**
- Digicode à chiffres, cadenas à lettres, cadenas directionnel (haut, bas, gauche, droite), coffre à code.
- Serrure à symboles : reproduire une suite de symboles vue ailleurs dans la salle.

**Recherche et collecte**
- Objets cachés à ramasser, clés, document déchiré à rassembler.
- Lampe spéciale : un objet qui fait apparaître un texte invisible sur un mur, pour celui qui la tient seulement.
- Objets à combiner dans l'inventaire.

**Observation**
- Compter des éléments du décor (tableaux, statues, couleurs).
- Deux pièces presque identiques : trouver les différences.
- Point de vue : un dessin qui ne se lit que depuis un endroit précis.
- Ordre caché : une frise dont l'ordre donne un code.

**Logique**
- Grille de déduction : « Le socle rouge n'est pas à côté du bleu… », indices générés.
- Mastermind : un terminal répond « bien placé / mal placé ».
- Lumières à éteindre (chaque interrupteur inverse ses voisins).
- Taquin, tours de Hanoï, tuyaux ou circuits à tourner.
- Pesée : trouver l'objet plus lourd parmi 9 en deux pesées.
- Carré latin, dés à orienter.

**Coopération à trois** (le cœur : la paire et le solo doivent se parler)
- Celui qui voit et celui qui agit : un manuel d'un côté d'une vitre, la machine de l'autre.
- Salle scindée : un joueur enfermé décrit à la voix ce que les autres ne voient pas.
- Trois leviers à tenir en même temps, aux trois coins de la salle.
- Labyrinthe invisible : un seul joueur voit les murs et guide les autres.
- Mémoire à trois : chacun ne voit que sa couleur d'une séquence commune.
- Relais chronométré : une porte ne reste ouverte que 10 secondes.

**Son**
- Mélodie à rejouer sur des blocs musicaux.
- Morse, ou un son à localiser dans la salle.

**Espace et adresse**
- Sol piégé : mémoriser un chemin sûr.
- Cibles à toucher à l'arc dans le bon ordre.
- Rayon à faire rebondir sur des miroirs à orienter.
- Parkour léger.

**Cobblemon** (voir aussi la section 6)
- Numéros de Pokédex comme codes ; ordonner des statues par poids ou taille ; compléter une lignée d'évolution.
- Table des types : actionner des leviers dans l'ordre « qui bat qui ».
- Alphabet Zarbi : textes chiffrés.
- Cris de Pokémon à reconnaître ; silhouettes ; empreintes.
- Pokémon partenaires à pouvoir : chaque joueur du groupe reçoit un Pokémon avec une capacité de terrain (Flash
  éclaire une salle noire, Force pousse un rocher, Coupe ouvre un passage, Surf traverse un bassin). Aucun ne
  suffit seul.
- Combat-énigme : une équipe imposée, une situation donnée, trouver la suite de coups qui gagne en N tours
  (comme un problème d'échecs). C'est un mini-jeu de duel tactique à nous, aux règles simples et sans hasard, et
  non un vrai combat : il se génère et se vérifie tout seul.
- Gardien : un dresseur PNJ barre la sortie, équipes imposées. Écarté de la première version (il demanderait
  de modifier Phantasmon).

### Première banque

Pour la première soirée, il faut 9 salles. Objectif ensuite : 15 à 18, pour que deux soirées ne se ressemblent
pas. Chaque salle est conçue à deux : la chaîne d'étapes est proposée ici, le décor est construit en jeu.

## 6. La place de Cobblemon

Tout est **(proposition)**.

- **Dans les salles :** les mécanismes Cobblemon du catalogue. Viser une à deux étapes Cobblemon par salle, jamais
  une salle qui demande de bien connaître la stratégie Pokémon.
- **Les bracelets :** couleurs habillées en types (section 3).
- **Le partenaire :** chaque joueur reçoit pour la soirée un Pokémon imposé, qui le suit et porte son pouvoir de
  terrain. Les fantômes reçoivent un Pokémon Spectre.
- **Le défi (plus tard) :** pendant le temps libre, un joueur en défie un autre, 1 point en jeu, équipes imposées
  et identiques. Écarté de la première version : il permet d'atteindre 9 autrement que par les votes.

## 7. Le complexe et les salles

### Un plan fixe

Le plan du complexe ne change jamais. Il est construit une fois, à la main, et collé une fois sur le serveur :

- le hall (réveil, résultats) ;
- la salle de vote et ses 6 cabines ;
- la porte finale ;
- 3 ailes, une par manche, chacune avec sa salle des 3 portes et **3 places de salle** ;
- des couloirs, des embranchements, des zigzags et des recoins entre tout cela, pour qu'on s'y perde un peu et
  qu'on puisse s'isoler.

Comme rien ne bouge, les zones sonores, les portes et les cabines sont réglées une fois pour toutes.

### Les 9 places

- Toutes les places ont la même taille, et toutes les salles sont construites à cette taille.
- Une place fait **48 × 48 blocs au sol (9 chunks) sur 24 de haut**, soit deux niveaux possibles. Une salle n'a
  pas à tout remplir : elle peut contenir ses propres couloirs, sous-pièces et cachettes.
- Entrée et sortie à des positions fixes sur le bord de la place, pour se raccorder aux couloirs du plan.
- Les ouvertures sont réelles, et fermées par de faux blocs affichés par le mod : un faux bloc arrête bien un
  joueur, alors qu'on ne peut pas faire traverser un vrai bloc.

### Avant chaque soirée

1. Le jeu tire 9 salles dans la banque, de difficulté égale par manche, et leur attribue une place.
2. Il produit les fichiers à coller, en `.schem` ou `.litematic` : un par place, ou un seul pour les 9.
3. L'admin du serveur les colle aux 9 places avec Axiom.
4. Le hub miroir reçoit les nouveaux blocs, et le jeu installe les mécanismes aux emplacements de chaque salle.

## 8. Le vocal

- Chaque pièce du plan (hall, couloir, salle, cabine) a un identifiant de **zone sonore**. On n'entend que les joueurs de sa zone. Une porte fermée
  isole. Les cabines sont étanches.
- Portée courte dans une zone (environ 8 blocs en voix normale), et un chuchotement à 2 ou 3 blocs.
- Deux sources de voix existent dans un hub miroir : le vocal du serveur (Simple Voice Chat, entre joueurs du même
  serveur) et le vocal du hub de Phantasmon (entre le serveur et les copies). Ils ne doivent pas se doubler.
- **(proposition)** Pendant la partie, toute la voix passe par le vocal du hub, où le serveur du jeu décide qui
  entend qui, et le mod coupe la voix de proximité du serveur entre participants.
- Le serveur de la communauté a Simple Voice Chat : le micro est donc capté.
- À vérifier avant de s'engager :
  - Le mod peut-il couper ou atténuer, côté client, la voix de proximité d'un joueur précis ?
  - Le vocal du hub tient-il 9 voix à la fois sans retard (limite connue TODO-34) ?

## 9. Lore et narration

- L'animateur est Mystaria, le premier fantôme du lore, celui qui annonçait la fin du serveur et appelait les
  vivants à le rejoindre : le jeu est son piège, la porte finale son défi.
- Narration légère : un prologue, une phrase de l'animateur à chaque étape, et des fragments de lore à trouver
  dans les salles et les alcôves (documents). Une fin différente selon le résultat : sortie solitaire, sortie de
  quelques-uns, sortie de tous, personne.
- Les textes sont écrits dans des fichiers, faciles à changer d'une soirée à l'autre.
- Les noms du jeu (le vote, la porte finale, les bracelets) sont à choisir dans le lore.

## 10. Technique

- **Où :** dans Phantasmon Events lui-même (décision du 2026-11-13). Le jeu fait partie du mod `phantasmon_events`
  et de son module backend : même jar, même installation. Phantasmon n'est pas modifié.
- **Serveur du jeu (backend) :** il porte toute la logique : étapes, chronos, bracelets, portes, votes, points,
  rôles, état des salles, tirages. Les clients ne font qu'afficher et envoyer des actions.
- **Client :** bracelet à l'écran, portes et cabines (faux blocs), mécanismes des salles, inventaire virtuel,
  documents, éditeur de salle, panneau du maître du jeu, fantômes et spectateurs, mannequins des déconnectés.
- **Confiance :** les joueurs sont des amis, pas de protection contre un client modifié. Les votes et les rôles
  restent quand même côté serveur, jamais envoyés aux autres clients avant la révélation.
- **Essais :** des joueurs factices (réservés aux admins) pour dérouler une partie à 2 comptes.

## 11. Étapes de réalisation

| Étape | Contenu | Fini quand |
|---|---|---|
| T0 | Ce plan validé, questions ouvertes tranchées | Les règles sont figées par écrit |
| T1 | Moteur de partie sans décor : étapes, chronos, bracelets, votes, points, résultats, joueurs factices | Une partie complète se déroule à 2 comptes, dans un hub vide |
| T2 | Portes, cabines, zones : faux blocs, fermeture de cabine, doublage du partenaire, porte finale et boucle de votes | Les 3 manches se jouent dans un décor provisoire |
| T3 | Salles : emplacements, éditeur, inventaire virtuel, écrans de mini-jeux partagés, 6 premiers mécanismes, indices automatiques, mesure des temps | Une salle complète se joue et se remet à zéro avec une autre solution |
| T4 | Plan fixe et places : taille standard, tirage des 9 salles, fichiers à coller par l'admin | 9 salles tirées sont collées et jouables dans le plan fixe |
| T5 | Vocal par zones | Deux joueurs dans deux salles ne s'entendent pas ; une cabine est étanche |
| T6 | Mort et corps, bracelets ramassés, fantômes et spectateurs, déconnexions | Les quatre cas se voient en jeu |
| T7 | Rôles secrets, tentations, objets cachés (couteau, liens, verrou et les autres), panneau du maître du jeu | Une partie avec rôles se déroule seule, et avec maître du jeu |
| T8 | Cobblemon : partenaires à pouvoir, combat-énigme, gardien, mécanismes Pokémon | Une salle Cobblemon complète se joue |
| T9 | Banque de 9 salles, lore, textes, fins | La soirée est prête sur le papier |
| T10 | Répétition générale avec 9 personnes | Réglages faits d'après les temps mesurés |

## 12. Questions ouvertes

Plus aucune question ne bloque le début du travail. Restent à trancher en route :

1. Les propositions encore marquées **(proposition)** dans ce plan, en particulier : la mort non annoncée, les
   durées des liens et du verrou, la pénalité d'un joueur resté devant les portes, l'habillage Feu / Plante / Eau.
2. Les noms du jeu (le vote, la porte finale, les bracelets), à choisir dans le lore.
3. La confirmation en jeu du vocal : la solution est trouvée (`solutions-techniques.md`, S4 et S5), il reste à
   la voir tourner.
