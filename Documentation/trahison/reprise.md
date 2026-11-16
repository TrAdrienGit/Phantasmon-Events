# Reprise du chantier (pause du 2026-11-16, après-midi)

> **À la reprise, d'abord :** `git checkout wip/trahison-t24-t33`. La branche `main` s'arrête à 7de54b9 (backend
> partiel de T2.4 et T3.3, tests verts). La branche wip contient :
> - **T2.4 cabines et vote, terminée** par l'agent (pupitre `screen/VoteScreen`, `world/CabinFlow`, touche V,
>   tableau des résultats `world/ResultsBoardWorld` + repli HUD, onglet Tableau de l'éditeur, 36 clés de langue,
>   docs). Build client et 193 tests backend verts selon l'agent, **pas encore recontrôlés par l'orchestrateur**.
>   Corrigé au passage : un plan sans cabines laisse le vote libre même avec `cabins_required`.
> - **T3.3 objets, inventaire, documents, interrompue** (pause à 84 % de la limite) : `GiveRangeTest`, `world/RoomFrame`,
>   dossier client `room/` commencé. État inconnu, le client peut ne pas compiler.
>
> À faire à la reprise : vérifier que le client compile, relancer un agent T3.3 « reprendre depuis l'arbre de
> travail » (brief : section T3.3 de `etapes/T3-salles.md`), puis contrôle, tests, fusion dans `main`.


> Point d'arrêt de l'orchestration du développement. À lire en premier à la reprise, avec
> [`etapes/README.md`](etapes/README.md).

## Ce qui est fait (sur `main`, poussé)

| Commit | Contenu | Vérifié |
|---|---|---|
| 1b90ce8 | T0 socle (Ping/Pong, `trahison_games` V6), charte UI (`charte-ui.md`, `TrahisonPalette`, `TrahisonUi`), règles pures T1.1 | Tests backend, build client |
| debff5e | T1.2 machine à états (`GameService`, étapes, chronos, persistance) | Tests |
| fd82afc | T1.3 vues par destinataire, actions, maître du jeu, joueurs factices, pause auto ; T2.1 plan et éditeur (V7) ; T3.1 moteur des salles (V8) | 131 tests verts |
| e393708 | T3.4 cadre des mini-jeux (client) ; T1.4 client provisoire (HUD bracelet, animatrice, écrans joueur et MJ) | Build client, clés de langue identiques EN/FR |
| 56c12d3 | Salles branchées sur le moteur, salles persistées, routes des salles réservées aux admins | 138 tests verts |

Rien n'a encore tourné **en jeu** : aucun déploiement n'a été fait (ni mod, ni module backend).

## Travail interrompu

- **T3.2 éditeur de salle** (client) : écrit et compilé (fichiers `edit/Room*.java`, `ChainScreen`), pas encore essayé en jeu.
  Voir `guide-editeur-salle.md` pour le test à faire à la main.
- ~~**T2.2 faux blocs et zones**~~ : faite le 2026-11-16 (non déployée, à valider en jeu) : `MirrorGhosts.putLocal` /
  `removeLocal` / `clearLocal`, états des portes calculés par le backend (`DoorStates`, `doors_state`, action MJ `set_door`,
  commande `/phantasmon event trahison door <id> open|close`), zones (`world/Zones`, `EventsTrahisonZone`), `world/FakeBlocks`.

## Reste à faire, dans l'ordre

1. ~~T2.4 cabines et vote, T2.5 porte finale~~ (faites le 2026-11-16 : T2.5 avec pupitre, fenêtre `final_door_seconds`, `winners` = ceux qui passent ; à valider en jeu, non déployées).
2. T3.2 (finir), ~~T3.3 objets du monde, inventaire, documents~~ (faite le 2026-11-16, à valider en jeu), T3.5 six mécanismes (`ring_dial` d'abord), T3.6 indices automatiques, mesure des temps,
   pénalité des 30 minutes (V9).
3. T4, T5, T6, T7, T8, T9, puis T10 avec des humains.

## Correctifs relevés au contrôle, pas encore faits

- ~~L'éditeur lit par réflexion les champs privés de `PhantasmonApi` pour les octets, et le catalogue des mécanismes est écrit à la
  main.~~ Fait : routes `schematic.json` (base64) et `GET /events/trahison/mechanisms` ; plus de réflexion.

- ~~La vue doit donner `viewer.can_open_final_door` (calculé par `rules.Outcome`) ; retirer la constante
  `GameScreen.FINAL_DOOR_POINTS` du client.~~ Fait en T2.2.
- ~~Une erreur `EventsTrahisonSay` reçue pendant qu'un écran de mini-jeu est ouvert doit appeler son `fail()`.~~ Fait en T2.2
  (`Announcer.onSay`).
- ~~Le client doit oublier les salles quand la vue `DOORS` d'une nouvelle manche arrive.~~ Fait en T2.2 (`PuzzleScreens`
  vide ses états et ferme l'écran de mini-jeu ouvert).
- ~~Coordonnées : `edit/HubGrid` arrondit l'origine fractionnaire de l'ancre au bloc le plus proche.~~ Fait en T2.2 : `HubGrid`
  arrondissait au plus proche et appliquait le `yaw`, alors que les miroirs prennent `floor(origine)` sans rotation ; un seul
  calcul, `world/HubBlocks`, sert maintenant à l'éditeur, aux zones et aux faux blocs (voir `architecture.md` section 6).
  **Un plan déjà enregistré avec une ancre à origine fractionnaire `>= .5` est décalé d'un bloc : à vérifier et à redessiner.**
- Les actions de salle écrivent toute la partie en base à chaque geste : prévoir un regroupement des écritures si
  cela pèse.

## Décisions prises par défaut (à trancher dans [`decisions.md`](decisions.md))

> D1 à D7 ont été tranchées par la propriétaire et appliquées le 2026-11-16 (voir « Tranché » dans `decisions.md`) ; restent D8 et D9
> (autre chantier) et les questions D10 à D13 ouvertes par cette application.

| Sujet | Choix par défaut | Autres possibilités |
|---|---|---|
| Salles pendant le temps libre | Visibles, mais une salle finie refuse les actions | Rejouables |

## Tests humains à faire (à transformer en check-list cochable à la reprise)

À faire après le déploiement du mod (`--events`) et du module backend :

- **T0** : `/phantasmon event trahison ping` affiche le Pong.
- **Charte UI** : rendu des composants (panneaux, boutons dans leurs 4 états, pastilles, symboles, bracelet) aux
  échelles d'interface 2, 3, 4 et en 1280 × 720 ; palette agréable et lisible.
- **T2.1 éditeur du plan** :
  - la commande `edit` est réservée aux admins ;
  - la touche K, et son réglage ;
  - les clics gauche et droit posent les coins sans que rien n'atteigne le serveur ;
  - le rendu dans le monde, avec et sans Iris ;
  - le panneau en 320 × 180 ;
  - un plan enregistré, puis rechargé à l'identique depuis un autre compte admin ;
  - le conflit de version ;
  - les erreurs affichées, et le saut vers l'élément fautif.
- **T3.4 mini-jeux** : `/phantasmon event trahison minigame three_dials` (hors ligne) :
  - l'impression générale face au menu de Showtime ;
  - la force du grain, des lignes et du vignettage ;
  - la prise en main des molettes ;
  - le cadenas et le rythme de la réussite ;
  - l'aide ;
  - le rendu en 320 × 180 ;
  - les sons.
- **T1.4 client** : partie complète avec l'écran MJ (`/phantasmon event trahison gm`), avec des factices de
  chaque consigne :
  - le HUD : sa place, le chrono orange puis rouge, l'effet sur les points, masqué par F1 et par les écrans ;
  - les répliques de l'animatrice, et une erreur traduite ;
  - l'écran joueur (`menu`) en 320 × 180 et en 854 × 480 ;
  - le vote d'une paire, des deux côtés ;
  - la pause, par le MJ et automatique.
- **T2.3 portes de couleur** (mod et module backend déployés ; plan avec un `threshold` par porte de couleur, sinon la
  `zone_a` sert de seuil et trois portes qui la partagent s'ouvrent ensemble) :
  - le formulaire d'une porte de couleur de l'éditeur a la ligne « Zone de seuil » ; la zone choisie s'éclaire quand la
    porte est sélectionnée ; un seuil qui n'existe pas est refusé à l'enregistrement ;
  - un joueur seul sur un seuil : la porte reste fermée, **aucun message d'erreur**, le panneau au-dessus dit « Il faut … »
    (couleur, symbole et nom) ; il se lit à environ 10 blocs, pas à travers un mur, et fait face à la caméra (**vérifier qu'il
    n'est pas en miroir** : l'orientation de `DoorPanels` est déduite de la caméra, pas encore vue en jeu) ;
  - une paire et un solo qui vont ensemble ouvrent la porte (6 s, réglable avec `door_open_seconds`), le panneau montre
    « Ouverte : n s » et la barre qui se vide ; passer dans la zone côté salle enregistre le passage, la porte se referme
    quand tout le groupe est passé ou au bout des secondes ;
  - les deux répartitions fonctionnent (essayer les deux sur deux manches) ; après un premier passage, la combinaison de
    l'autre répartition devant une autre porte ne l'ouvre pas et le panneau l'explique (« ne vont pas avec les portes
    déjà prises ») ;
  - un joueur mort ou fantôme sur le seuil ne compte pas ;
  - à la fin de `DOORS` toutes les portes se ferment, **un joueur qui n'a passé aucune porte de couleur meurt** (D2, plus de
    pénalité de points : voir les tests D1 à D7 plus bas) ;
  - le panneau a-t-il le bon rendu (lisible en 1280 x 720, glyphes nets, rien ne se superpose) ? Les polices et les formes
    sont celles du monde (`Font.drawInBatch`, `RenderType.debugQuads`), pas celles de la charte des écrans.
- **T2.2 faux blocs et zones** (mod `--events` et module backend déployés, plan complet enregistré) :
  - une porte de couleur fermée arrête le joueur, `/phantasmon event trahison door <id> open` l'ouvre chez tous en même temps,
    `close` la referme ; la porte revient à son état par défaut au changement d'étape ;
  - elle survit à un éloignement puis un retour (rechargement des tronçons), et à une mise à jour de bloc voisine ; on ne peut
    ni la casser ni poser un bloc contre elle ;
  - les deux moitiés d'une porte de couleur sont des deux couleurs, les cabines en chêne noir, la porte finale en obsidienne ;
    rien de réel n'est remplacé (une porte dont la boîte touche un vrai bloc n'a que ses cases d'air) ;
  - un fantôme et un spectateur traversent ; tout disparaît à la fin de la partie et en quittant le hub ;
  - les sorties de salle sont fermées pendant `ROOMS` et s'ouvrent quand la salle du groupe est résolue ;
  - le plan se dessine dans l'éditeur sur les mêmes blocs que les faux blocs (ancre à origine fractionnaire, le cas de l'ancienne
    erreur d'un bloc) ;
  - la zone de chaque joueur apparaît dans la vue du maître du jeu (`gm.zones`, pas encore affichée par l'écran provisoire).

- **T2.4 cabines et vote** (mod `--events` et module backend déployés ; plan avec 6 cabines, chacune une zone de type `cabin` et une
  porte de type `cabin`, et idéalement un `results_board`) :
  - l'onglet **Tableau** de l'éditeur : créer, choisir le côté des lecteurs, **Ajuster à la sélection** sur un pan de mur ; la boîte
    d'accent est bien sur le mur voulu, avec le bon côté dans l'étiquette ; enregistrer puis recharger : identique ; un tableau de
    moins de 4 blocs est refusé ;
  - en `VOTE`, la ligne du HUD dit où aller (cabine, couleurs, côté) ; dans une cabine qui n'est pas la sienne : « Ce n'est pas ta
    cabine » ; dans la sienne : « Appuie sur V pour fermer ta cabine et voter » ;
  - la mécanique des cabines a été refondue (D4, D5) : voir les tests « Cabines » de la section D1 à D7 plus bas, qui remplacent ceux
    qui parlaient de fermer la cabine avec V ;
  - le pupitre en 320 x 180 et en 854 x 480 : or pour S'allier, violet pour Trahir, symboles nets, chrono, texte qui ne déborde pas ;
    flèches gauche et droite, Entrée, clic ; le choix validé reste allumé et l'autre s'assombrit ; « Fermer » cache le pupitre sans
    rien changer, V le rouvre ;
  - voter sans être dans sa cabine verrouillée (par l'écran `menu`) est refusé ; pas de vote : « Allier » ;
  - `cabins_required` coupé dans `create` : le vote libre de l'écran `menu` revient ;
  - `RESULTS` : le tableau se lit en face, **pas en miroir** (texte de gauche à droite, du bon côté du mur, vu de l'autre côté il est
    simplement derrière le mur), les votes se révèlent porte par porte (« ? » avant), puis les points ; lisible à 10 et 30 blocs, pas
    à travers un mur, pas de scintillement entre le fond et le texte (si oui : augmenter `LAYER` dans `ResultsBoardWorld`) ;
  - sans `results_board` dans le plan : le panneau du HUD en haut à gauche (il peut chevaucher le bracelet en 320 x 180).

- **Décisions D1 à D7 (2026-11-16 ; mod `--events` et module backend déployés ; à faire avec 9 joueurs, ou 2 humains et des factices)** :
  - **D2, mort aux portes** : en `DOORS`, un joueur ne passe aucune porte de couleur. À la fin de l'étape il devient fantôme (traverse
    les faux blocs, ne voit plus de faux blocs, n'agit plus), ses points sont **inchangés**, l'animatrice dit à tout le monde
    « <nom> n'a passé aucune porte. La maison le garde. » (en anglais en `en_us`) ; sa salle et son vote existent quand même (« Allier »
    par défaut pour lui) ; à la manche suivante il n'a pas de porte à passer et ne « remeurt » pas ; avec tous les autres joueurs
    qui passent, seul lui est mort. Contrôler que le HUD le montre en fantôme et que l'écran MJ le liste mort.
  - **Cabines, un solo** : en `VOTE`, entrer dans **sa** cabine solo la verrouille aussitôt (la porte se ferme, on ne peut plus
    sortir, le pupitre s'ouvre tout seul) ; un autre joueur ne peut pas y entrer ; V ne fait rien de plus. Voter ouvre la porte **pour soi
    seul** (la porte se rouvre devant soi, un autre qui s'approche la voit fermée) ; en sortir la libère pour tous.
  - **Cabines, une paire** : le premier entré voit sur la ligne du HUD « Ton partenaire a N s pour te rejoindre » avec un compte à
    rebours ; son partenaire voit « Ton partenaire attend dans la cabine : N s pour le rejoindre » ; la porte reste ouverte aux deux
    seulement (un tiers la voit fermée). Si le partenaire entre dans les 30 s : la cabine se verrouille aussitôt pour les deux, les
    deux peuvent voter (un seul vote compte pour la paire ; le second reçoit « déjà voté »). Si le partenaire n'arrive pas : au bout de
    30 s elle se verrouille avec le premier, le partenaire arrivé en retard trouve la porte fermée (« Ta cabine est verrouillée : ton
    partenaire vote pour la paire ») et ne peut pas voter ; V avant les 30 s verrouille tout de suite. Si le premier ressort avant
    le verrouillage, la cabine redevient libre.
  - **D5, sortir** : une fois que le groupe a voté, la porte s'ouvre pour ceux qui sont dedans (la pastille du pupitre passe à
    « Porte ouverte ») et le HUD dit « Vote enregistré » ; sans avoir voté on reste enfermé jusqu'à la fin de `VOTE`, puis toutes les
    cabines s'ouvrent à `RESULTS`. Un joueur déconnecté dans une cabine : elle s'ouvre à la fin de l'étape.
  - **Intrus** (voir D10) : se mettre dans la cabine d'un autre groupe ne la verrouille pas et on ne peut pas y voter ; si le bon
    joueur entre ensuite, l'intrus peut ressortir, lui seul.
  - **D3, vote absent** : un solo qui ne vote pas compte « Allier » ; une paire dont un membre ne vote pas, l'autre vote seul pour
    la paire ; une paire où personne ne vote : « Allier » (vérifier les points sur le tableau des résultats).
  - **Réglage** : `settings.cabin_partner_seconds` dans `create` (1 à 300, 30 par défaut), visible dans la vue MJ.
  - **D7, maîtres du jeu** (écran `/phantasmon event trahison gm`, avec un compte admin et un compte non admin dans le hub) :
    l'admin crée la partie : il n'est **pas** maître du jeu ; dans le lobby la colonne de droite liste les comptes autour du lobby
    avec « Nommer maître du jeu » / « Retirer » (réservé aux admins, un non-admin voit le bouton grisé) ; nommer le compte non admin :
    il reçoit la vue MJ, peut ouvrir l'écran MJ (`gm`) et envoyer pause, suivant, etc. ; l'admin non nommé ne peut plus rien faire dès que
    la partie est lancée (« Tu n'es pas maître du jeu de cette partie » dans l'écran, refus côté backend) mais peut encore démarrer et
    ajouter des factices en lobby ; un joueur inscrit ne peut pas être nommé et un maître du jeu ne peut pas s'inscrire ; plusieurs maîtres
    du jeu reçoivent tous la vue MJ ; en mode « Maître du jeu » sans personne de nommé le démarrage est refusé (« …demande au moins un
    maître du jeu ») ; en mode Automatique avec 0 maître du jeu la partie va jusqu'au bout seule.
  - **D1** : après la manche 3, une boucle de la porte finale tire de nouveaux groupes au hasard sans étape des portes (personne ne meurt
    faute d'avoir passé une porte).
- **Tests humains T3.3** (objets du monde, inventaire, documents, temps libre D8 ; une partie à 9 avec des factices, une salle d'essai
  qui a des emplacements `terminal`, `keypad`, `pickup`, `wall_clue`, `lever`, `pedestal`, un objet à ramasser et une recette `combines` ;
  `test_room` dans `create`) :
  - **Rendus** : chaque genre d'emplacement actif est dessiné à sa place et dans le bon sens, pour les 4 orientations de place de la salle
    (terminal : bloc et écran qui brille, digicode : plaque murale et 12 touches, objet : octaèdre qui tourne et flotte, panneau d'indice :
    texte, chiffres, symboles des mini-jeux, carrés de couleur avec leur forme, levier : poignée qui tombe quand l'étape est résolue,
    socle). Le texte n'est pas en miroir ; un mur cache le panneau ; les symboles sont bien cachés derrière un mur ; sans Iris et avec.
  - **Visée** : contour clair et nom de l'action (Examiner, Ramasser, Utiliser) à 4 blocs, rien au-delà ; un clic droit ou gauche ne
    déclenche rien d'autre (aucun bloc posé ni cassé, rien dans la console du serveur) à 2, 3 et 4 blocs ; un emplacement logé dans un
    bloc réel se vise quand même ; un mur ou un bloc devant l'objet empêche de le viser.
  - **Ramasser** : clic sur l'objet, il disparaît du monde **chez tous les membres du groupe**, apparaît dans la barre d'objets du seul
    ramasseur et dans son inventaire (touche I) ; le coéquipier le voit dans « Tes coéquipiers portent ».
  - **Donner** : écran d'inventaire, « Donner à... » : refusé à plus de 4 blocs (message « trop loin » du backend), accepté à moins ; l'objet
    passe dans la barre de l'autre joueur seulement ; un joueur d'un autre groupe n'est pas proposé pendant `ROOMS`.
  - **Utiliser** : « Tenir » (ou la touche G) puis clic sur le terminal ou le digicode attendu : l'étape se résout et l'objet disparaît ;
    sur un mauvais emplacement : « cela ne fait rien ici », l'objet reste. « Utiliser sur... » dans l'écran fait la même chose.
  - **Combiner** : deux objets d'une recette de la salle donnent le résultat chez celui qui combine ; deux objets sans recette : refus.
  - **Documents** : « Examiner » sur un panneau ou un document ouvre l'écran de lecture (pages précédente / suivante, flèches, Échap),
    une image absente montre l'image de remplacement `textures/trahison/docs/placeholder.png` avec son identifiant ; déposer un PNG
    `textures/trahison/docs/<id>.png` la remplace. Le texte long est coupé proprement en 320 x 180.
  - **Synchronisation** : un second joueur du groupe voit les mêmes changements (objet ramassé, indice qui apparaît au mur quand l'étape
    d'avant est résolue, levier tombé) ; un spectateur ou un fantôme voit les objets mais ne peut ni les viser ni cliquer dessus.
  - **D8, temps libre** : à `FREE_TIME`, aller dans la salle d'un autre groupe (les trois salles sont dessinées pour tous) : on peut viser et
    y jouer, y ramasser un objet, ouvrir ses mini-jeux et y faire des gestes qui bougent chez ceux qui y sont ; « Rejouer cette salle »
    (inventaire, confirmation) remet la salle à zéro avec **d'autres solutions** (l'ancien code ne marche plus) ; aucun point ne change, aucune
    porte ne s'ouvre, `roomSolved` ne bouge pas (voir la vue MJ) même si on finit la salle rejouée ; en `VOTE` plus rien n'est accepté.
  - **D8, ajout : les salles des manches précédentes** (une partie à 9, au moins deux manches ; garder les salles de la manche 1 jusqu'à la manche 2) :
    - au temps libre de la manche 2, les **six** salles sont dessinées (les trois places de la manche 1 et celles de la manche 2), et l'on peut viser et
      y jouer dans celles de la manche 1 : ramasser, ouvrir un mini-jeu, faire un geste (il bouge chez ceux qui sont dans la même salle) ;
    - pendant `ROOMS` de la manche 2, les salles de la manche 1 ne sont **pas** jouables (aucune visée, aucun message d'erreur parasite) ; elles le redeviennent à `FREE_TIME` ;
    - « Rejouer cette salle » dans une salle de la manche 1 : nouvelles solutions, `roomSolved` de la manche 1 et de la manche 2 inchangé, aucune porte ne bouge, aucun point ;
    - au temps libre de la manche 3 : neuf salles ; un mini-jeu ouvert dans une salle de la manche 1 envoie ses gestes à **cette** salle (pas à celle de la manche 3 du même groupe) ;
    - un redémarrage du backend entre la manche 2 et son temps libre : les salles des deux manches reviennent avec leur avancement, leur sac, leur nombre de rejeux ;
    - un joueur qui arrive en cours de route (`view`) reçoit toutes les salles qu'il a le droit de voir, des deux manches.
  - **Backend** : le message `EventsTrahisonRoom` d'un joueur ne contient jamais de solution (`target`, etc.) ; un redémarrage du backend en
    pleine salle garde le sac, les porteurs et le nombre de rejeux.

- **Tests humains T2.5** (la porte finale ; une partie à 9 avec des factices, un plan avec une porte `final` dont `zone_b` est la zone de type `final` après elle et dont le champ
  **Pupitre** de l'éditeur est une zone devant la porte ; régler `final_door_seconds` à 10 à la création, passer en `FINAL` avec `next`, donner 9 points avec `set_points`) :
  - **Éditeur** : une porte de type `final` montre la ligne « Pupitre » (liste des zones, « aucun » permis) ; elle s'enregistre, se recharge à l'identique, et la zone du pupitre est
    éclairée avec la porte ; changer le type d'une porte `final` en autre chose efface le pupitre ; une zone inexistante est refusée à l'enregistrement (`BAD_REFERENCE`).
  - **Pupitre** : en `FINAL`, un joueur à 9 points qui se tient dans la zone du pupitre voit la ligne « Appuie sur V pour ouvrir le pupitre de la porte finale » ; hors de la zone, « Va au
    pupitre devant la porte finale » ; un joueur sous 9 points n'a rien loin du pupitre et « Ton bracelet ne lit pas assez... » sur le pupitre. V ouvre l'écran (couleurs bleu nuit et
    accent turquoise, bracelet, texte, bouton « Ouvrir la porte finale ») ; le bouton demande une confirmation (la partie se termine) ; Entrée la confirme ; « Fermer » cache l'écran
    sans rien perdre ; s'éloigner du pupitre le ferme tout seul ; sous 9 points le bouton est grisé avec son infobulle ; en pause aussi.
  - **Ouverture** : l'animatrice annonce qui a ouvert et pour combien de secondes ; **chez les joueurs à 9 points** le faux mur d'obsidienne disparaît et ils passent ; **chez un joueur sous 9**
    et chez un spectateur le mur reste (essayer de le traverser) ; le HUD du joueur à 9 dit « La porte finale est ouverte pour toi : N s. Passe ! », celui d'un autre « ouverte, mais pas
    pour toi » ; le compte à rebours de l'étape est celui de la porte ; un second appui sur le pupitre ne change rien.
  - **Fermeture** : à la fin du délai la partie passe à `ENDED` et la porte ne se rouvre pas dans cette partie ; la liste des gagnants (écran de partie, onglet « Terminée ») ne contient
    que ceux qui **sont passés** ; l'animatrice les nomme (ou dit que personne n'est passé) ; si tous les joueurs à 9 passent avant le délai, la partie se termine tout de suite.
  - **Sortie solitaire** : un seul joueur à 9 (les autres corrigés avec `set_points`) : seul lui voit la porte s'ouvrir, il passe seul, gagnant unique.
  - **Boucle de votes** : si personne n'actionne le pupitre, au bout de la durée de `FINAL` un nouveau vote recommence (`VOTE`, `RESULTS`, retour à `FINAL`) ; si la porte est ouverte, aucun
    vote ne démarre pendant qu'elle l'est.
  - **Refus** : à 9 points mais hors de la zone du pupitre, le message « va au pupitre devant la porte finale » ; sous 9 points depuis le pupitre, « ton bracelet ne lit pas assez » ; le bouton
    du menu du jeu (touche libre, `/phantasmon event trahison menu`) suit les mêmes règles.
  - **Au passage** : un fantôme et un spectateur traversent la porte finale sans rien faire (comme les autres portes) ; un redémarrage du backend pendant que la porte est ouverte la referme à l'heure.
  - **Annonces de la phase `doors_dead`** (corrigée en même temps : sa clé de langue était fausse) : « Mystaria : X n'a passé aucune porte. La maison le garde. » apparaît bien à l'entrée en `ROOMS`.

- **Tests humains T3.5 keypad/pickup_set** (backend déployé avec le mod ; hors ligne : `/phantasmon event trahison minigame keypad` et `... pickup_set`, puis une partie
  avec une salle d'essai qui a un emplacement `keypad`, un `wall_clue`, trois `pickup`, un `pedestal` et un `document` ; **la définition doit se laisser enregistrer** : chaque
  `spot_n` donné, l'indice `code` donné dès l'ouverture) :
  - **Éditeur** : le choix de mécanisme liste `keypad` et `pickup_set` sans « prévu » ; les rôles viennent du backend (`keypad`, `deposit`...) ; la liste des emplacements d'un
    rôle ne propose que les genres acceptés ; lier `keypad` à un `lever` est refusé ; un digicode dont le code n'est donné nulle part est refusé à l'enregistrement
    (« indice dont les joueurs ont besoin... »), un `pickup_set` dont un `spot_n` n'est pas donné aussi ; la liste de mécanismes reste utilisable si le backend ne répond pas.
  - **`keypad` hors ligne** : fenêtre 400 x 240 lisible ; les 12 touches à la souris et au clavier (chiffres du clavier et du pavé numérique, Retour arrière, Suppr, Entrée) ;
    le clic des touches ; le curseur des autres joueurs qui s'allume sur leur touche ; un mauvais code (secousse, flash rouge, son sourd, afficheur vidé, compteur « Essais ratés ») ;
    le code d'essai (affiché en orange) ouvre le cadenas ; l'aide (H).
  - **`keypad` en jeu** : cliquer le digicode ouvre l'écran ; deux joueurs voient le même afficheur et les touches de l'autre ; un mauvais code montre l'erreur aux deux ; le code
    trouvé sur le panneau mural ouvre le pas et la suite de la chaîne ; le digicode mural est dessiné comme avant ; la longueur du code (3, 4, 6) change l'afficheur ; rien dans la
    vue réseau ne contient le code avant la résolution (le panneau mural le montre, c'est voulu).
  - **`pickup_set` en jeu** : seules les cases tirées montrent un objet (les autres emplacements `pickup` sont invisibles et non cliquables) ; d'une partie à l'autre, ou après « Rejouer »,
    ce ne sont pas les mêmes cases ni les mêmes objets ; ramasser un objet le met dans le sac (nom lisible : « Fusible », « Engrenage »...) ; l'utiliser sur le dépôt le consomme et fait
    avancer le compteur « 1 / 3 » ; un objet qui n'est pas attendu est refusé et reste dans le sac ; cliquer le dépôt les mains vides ouvre le compteur ; le dernier objet déposé
    résout l'étape ; le document (paramètre `document`) se lit sur son emplacement dès le début.
  - **`ring_dial` en jeu** : l'écran s'ouvre maintenant par un clic sur son emplacement (il n'était pas enregistré dans le routeur des écrans avant ce lot).

- **Tests humains T3.5 code_breaker/wiring** (backend déployé avec le mod ; hors ligne : `/phantasmon event trahison minigame code_breaker` et `... wiring`, puis une partie avec une salle d'essai qui a un
  `terminal` pour le décodeur, un `terminal` pour le câblage et un `wall_clue` ; **la définition doit se laisser enregistrer** : l'indice `scheme` du câblage donné dès l'ouverture) :
  - **Éditeur** : le choix de mécanisme liste `wiring` et `code_breaker` sans « prévu » ; les rôles `panel` et `terminal` viennent du backend ; un câblage dont le schéma n'est donné nulle part est refusé à
    l'enregistrement ; un décodeur sans emplacement lié aussi ; paramètres hors intervalle en rouge (`length` 6, `alphabet` 10, `maxTries` 3).
  - **`code_breaker` hors ligne** : fenêtre 400 x 240 lisible, six touches de glyphes avec leur numéro dans le coin (1 à 6 au clavier), Retour arrière, Suppr, Entrée ; la ligne de saisie avec son curseur ;
    un essai s'ajoute à l'historique avec une courte lueur, disque plein (bien placé) et anneau (mal placé) chacun avec son nombre ; la combinaison d'essai (orange) ouvre le cadenas ; un jeton déjà tapé est
    grisé et refusé (répétitions interdites) ; l'aide (H) ; les curseurs des deux joueurs fictifs sur les touches.
  - **`code_breaker` en jeu** : cliquer le terminal ouvre l'écran ; deux joueurs voient la même ligne de saisie et le même historique ; un essai n'est envoyé qu'avec la bonne longueur ; avec `kind: digits`
    les touches sont des chiffres ; avec `maxTries: 6` le terminal se réinitialise au sixième essai raté (secousse, « Terminal réinitialisé ! », historique vide) ; avec `repeats: true` un jeton peut revenir ;
    rien dans la vue réseau ne contient la combinaison ; une partie rejouée (« Rejouer ») tire une autre combinaison.
  - **`wiring` hors ligne** : quatre bornes numérotées à gauche, quatre bornes colorées à droite avec leur symbole (triangle, disque, carré : lisibles sans la couleur) ; glisser d'une borne à l'autre pose un fil à la
    couleur de la borne de droite ; cliquer une borne puis l'autre aussi ; tirer un fil par son bout le déplace, le lâcher dans le vide l'enlève ; clic droit enlève ; une borne ne tient qu'un fil ; le schéma d'essai
    (orange) donne la bonne solution (la couleur seule compte : deux bornes de droite rouges conviennent) ; un panneau plein mais faux fait trembler l'écran et monter « Courts-circuits » sans enlever les fils ;
    une solution juste ouvre le cadenas.
  - **`wiring` en jeu** : le schéma (carrés de couleur avec symboles) se lit sur le panneau mural et dans l'écran de lecture, dans l'ordre des bornes de gauche ; deux joueurs voient les fils de l'autre et son
    curseur à sa couleur ; un panneau juste résout l'étape et la suite de la chaîne s'ouvre ; la vue réseau ne contient pas le schéma ; une partie rejouée donne un autre schéma et d'autres couleurs à droite.

## Notes de contrôle de l'orchestrateur (2026-11-16, après 27b1d8c)

- **Points en suspens** :
  - ajouter `Mechanism.slotRoles()` au backend, pour que le catalogue des mécanismes du client n'ait plus de table
    écrite en dur (à faire avec les mécanismes suivants) ;
  - vérifier que les étiquettes flottantes de l'éditeur (`edit/WorldDraw.label`, `scale(s,-s,s)`) ne sont pas en
    miroir, à comparer avec `world/DoorPanels` ;
  - un joueur qui se trouve dans la boîte d'une porte au moment où elle se ferme n'est pas repoussé.
- **Tests humains en plus** :
  - **T3.2 éditeur de salle** (suivre `guide-editeur-salle.md`) :
    - construire une petite salle (panneau, bannière, escalier), poser 3 emplacements et une chaîne de 2 étapes, puis
      enregistrer : version 1 attendue ;
    - `room list`, puis `room load` dans un autre monde, face à une autre direction : vérifier l'orientation des
      blocs, et que les emplacements et la chaîne sont intacts ;
    - les refus : pas admin, pas en solo, pas en créatif, entrée ou sortie bouchée, pas d'étape de sortie, emplacement
      hors gabarit ;
    - la schématique s'ouvre dans Axiom ;
    - il faut un bâton en main pour sélectionner.
  - **`ring_dial`** (`/phantasmon event trahison minigame ring_dial`) :
    - la lisibilité des 20 symboles entre 9 et 16 pixels ;
    - 5 anneaux de 12 symboles ;
    - le rayon marqué en haut ;
    - la rotation des anneaux à l'ouverture ;
    - les anneaux liés ;
    - le clavier et les sons.
