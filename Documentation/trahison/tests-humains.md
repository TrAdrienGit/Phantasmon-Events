# Tests humains à faire en jeu

> Liste cumulée, à transformer en check-list cochable au premier déploiement. Les agents **ajoutent** leur bloc à la fin
> (`cat >> tests-humains.md`), sans relire le fichier.


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

- **Tests humains T3.5 three_levers** (backend déployé avec le mod ; **trois vrais joueurs** pour le jeu réel, un seul suffit hors ligne et pour l'essai `solo` ; une salle d'essai avec trois emplacements `lever` aux trois coins et
  une étape `three_levers` liée à `lever_1`, `lever_2`, `lever_3`, puis une étape `debug_button` sur la sortie) :
  - **Éditeur** : `three_levers` est listé sans « prévu » ; les trois rôles `lever_n` n'acceptent que des emplacements `lever` ; une étape où un levier n'est pas lié, ou deux rôles sur le même emplacement, est refusée à
    l'enregistrement ; paramètres hors intervalle en rouge (`gap` 9, `hold` 4, `hold` 15 avec `gap` 8 : « au moins deux fois »).
  - **Hors ligne** : `/phantasmon event trahison minigame three_levers` ouvre un aperçu 400 x 240 : trois leviers verticaux, une poignée en haut (rouge, « En attente ») qui descend (allumée, « Enclenché ») au clic ou à
    la touche 1, 2 ou 3 ; trois lumières en haut qui comptent les leviers tenus ; les deux joueurs fictifs enclenchent les autres ; la poignée clignote les deux dernières secondes du bail ; les trois tenus ensemble
    déverrouillent. L'aide (H) se lit en français comme en anglais.
  - **En jeu, trois joueurs** : au clic sur un levier la poignée descend et s'allume chez les trois, avec un cliquetis plus aigu à chaque levier ; sans autre levier la poignée remonte seule au bout de `hold` secondes (elle
    clignote avant) ; recliquer pendant le bail le prolonge ; le clic d'un autre joueur sur un levier tenu ne fait rien ; un joueur qui va à un deuxième levier lâche le premier ; trois leviers tenus par trois joueurs
    dans le bon ordre déverrouillent (déclic, poignées vertes, trois lumières vertes) et la sortie s'ouvre ; le mauvais ordre fait tout lâcher avec le bruit sourd, sans rien dire de l'ordre ; une attente plus longue
    que le délai fait tout lâcher aussi ; avec `ordered: false` n'importe quel ordre convient ; la latence (clic sur un serveur distant) ne fait pas tomber un levier.
  - **Un joueur seul** : il ne peut jamais tenir les trois (cliquer un deuxième lâche le premier) ; avec `solo: true` et `hold: 30` il y arrive en courant d'un coin à l'autre, quel que soit le délai.
  - **Réseau** : la vue d'un joueur ne contient ni `order` ni `gapMs` (seul l'écran du maître du jeu les reçoit) ; « Rejouer » tire un autre ordre et un autre délai.
  - **Cas limites à regarder** : un groupe de deux ne peut pas résoudre la salle (D42, D43) ; quitter la salle avec un levier tenu : il retombe au bout du bail ; un backend redémarré en pleine tenue garde les leviers tenus jusqu'à la fin de leur bail, pas au-delà.


## Porte finale, maître du jeu joueur, salles libres (D12, D17 à D22)

- Plan complet sans pupitre sur la porte finale : l'éditeur refuse l'enregistrement final (champ `threshold` manquant) ; un brouillon passe.
- Porte finale : un joueur à 9 points actionne le pupitre ; tous (joueurs, fantôme, spectateur, maître du jeu) voient le compte à rebours
  de 60 s et l'annonce « la porte 9 vient d'être ouverte » ; la hôtesse rappelle à 30, 10 et 5 secondes.
- Tous les openers passent avant la fin : la porte reste ouverte jusqu'à 0 s, puis la partie finit ; les gagnants sont ceux qui sont passés.
- Plan sans porte finale (essai) : même compte à rebours de 60 s sans rien à franchir, puis tous les openers gagnent.
- Maître du jeu joueur : l'admin nomme X dans le lobby, X s'inscrit ; les autres ne le voient ni dans la liste des maîtres du jeu ni
  ailleurs ; X ouvre l'écran MJ (touche habituelle) et agit (pause, points) sans que personne apprenne qui fait quoi.
- X meurt (n'a passé aucune porte, ou bracelet à 0 après un vote) : la partie finit, les huit autres gagnent, son nom est annoncé.
- Un groupe termine sa salle avant la fin de `ROOMS` : ses membres entrent dans les autres salles (celles d'autres trios, des manches
  précédentes), y jouent et peuvent les rejouer, sans point ; pareil en `VOTE`. Un groupe qui n'a pas fini reste cantonné à sa salle.

## T3.5 retouches des décisions D30 à D39 (digicode, objets cachés, décodeur, câblage)

- **Digicode (D30)** : taper trois mauvais codes de suite : à la troisième erreur l'afficheur devient rouge et dit « Verrouillé : 10 s » avec un décompte ; les touches ne font rien (au clavier non plus) ; au bout de 10 s
  l'afficheur redevient normal et le bon code résout ; deux erreurs puis le bon code : aucun verrou. Le verrou est le même pour tout le groupe (un coéquipier voit le décompte). Hors ligne
  (`/phantasmon event trahison minigame keypad`) : trois erreurs verrouillent aussi.
- **Objets cachés (D32)** : ramasser le dernier objet résout l'étape (la suite de la chaîne s'ouvre) sans rien déposer ; les objets restent dans le sac ; une salle sans emplacement `deposit` s'enregistre sans erreur ;
  une ancienne salle qui lie encore `deposit` reste valide (le compteur s'ouvre dessus).
- **Décodeur (D36)** : avec `maxTries: 6`, six essais faux effacent l'historique ; la combinaison est **la même** (retaper un essai déjà tenté redonne la même réponse).
- **Câblage (D37)** : la même salle avec un `document` portant l'indice `scheme_text` : lu en français, il donne « 1 : Rouge », « 2 : Bleu »... et en anglais « 1 : Red »... ; une salle qui ne donne que `scheme_text`
  (sans `scheme`) s'enregistre ; une salle qui ne donne ni l'un ni l'autre est refusée.
- **Câblage (D38)** : avec `colors: 5` et `wires: 5` ou 6, le panneau montre des bornes jaunes (losange) et blanches (anneau) ; l'indice mural, l'écran de lecture et l'écran de câblage les dessinent avec leur symbole ;
  `colors` plus grand que `wires` est refusé dans l'éditeur. Les portes et les votes n'ont pas changé de couleurs.
- **Câblage (D39)** : poser tous les fils de travers : le panneau tremble et **tous** les fils sautent, le compteur de courts-circuits monte ; on recommence du début.

## T3.6 aides automatiques, mesure des temps, fin de `ROOMS`

- **Aides** (salle d'essai avec 3 textes d'aide sur une étape ; démarrer le backend avec `phantasmon.trahison.hint-minutes=1,2,3` pour ne pas attendre 25 minutes) : sans rien faire, au bout d'une minute l'hôtesse lit
  « Indice : (texte vague) » à tout le groupe seulement ; une minute plus tard le deuxième, puis le troisième ; ensuite plus rien. Ramasser un objet ou résoudre une étape relance la série (la première aide revient
  une minute après le progrès). Un joueur d'un autre groupe n'entend rien ; l'écran du maître du jeu montre `hints_given` de l'étape. Reconnexion en cours de partie : les aides déjà données ne sont pas relues.
- **Mesures** : résoudre une salle de bout en bout, puis `GET /events/trahison/telemetry/step-times` (admin) : une ligne par mécanisme et par salle avec les médianes ; un joueur non admin reçoit 403. Rejouer
  une salle en temps libre ou utiliser `debug_solve` n'ajoute aucune ligne. Après trois parties d'une même salle, la liste des salles (éditeur) montre le temps mesuré (`measured: true`).
- **Fin de `ROOMS`** : laisser un groupe ne pas finir sa salle : à la fin de l'étape sa sortie s'ouvre (sa porte aussi), chaque membre perd 1 point sur son bracelet (jamais sous 0 ; à 0 il devient fantôme) ; les
  groupes qui avaient fini ne perdent rien ; avec `roomTimeout: false` rien ne se passe.

## T4.2 Écriture des fichiers de collage

- Monde d'essai solo, une salle sauvée (`/phantasmon event trahison room list`) : lancer `/phantasmon event trahison export <9 ids>` (même id répété possible) ; le chat donne le dossier, un clic l'ouvre ; il contient `place_*.schem`, `place_*.litematic`, `all_places.*` et `paste_sheet.txt`.
- Coller `place_<id>.schem` avec Axiom, coin minimum du fichier sur le coin de la fiche, sans rotation : la salle remplit le carré de 48 × 48 de la place, sans décalage, et la sortie (nord de la salle) regarde l'orientation de la place.
- Faire l'essai pour les quatre orientations (`north`, `east`, `south`, `west`) : escaliers, portes, panneaux muraux et têtes tournent avec la salle ; le texte des panneaux est conservé.
- Même essai avec le `.litematic` (Litematica ou Axiom) : même résultat que le `.schem`.
- Coller `all_places.schem` : les 9 places se retrouvent à leurs positions relatives.
- Comparer les coins d'emplacements de la fiche avec les emplacements affichés par le plan une fois collé.
- Dans le panneau du maître du jeu (lobby), le bouton « Exporter les salles » explique la commande tant que le tirage n'est pas lisible.

## T4.1 et T4.3 : le tirage et les places

- Avoir au moins neuf salles avec leurs blocs dans la banque. Créer une partie : dans l'écran du maître du jeu (nommé dans le lobby), le panneau « Salles tirées » montre trois manches de trois salles, la difficulté de chacune, le total de la manche, la place.
- Les trois salles d'une même manche ont des difficultés proches (écart d'au plus 1 par défaut) et des mécanismes différents.
- « Tirer à nouveau » donne un autre tirage ; avec la même graine (`redraw` avec `seed`), le même.
- Cliquer une salle puis une autre de la liste : elle prend sa place, marquée d'une étoile. Une fois les portes de la manche fermées, le remplacement est refusé.
- Avec moins de neuf salles dans la banque, le tirage marche et affiche la ligne « La banque n'a pas pu respecter ... ».
- Une salle à trois leviers n'apparaît jamais dans un groupe de moins de trois joueurs (aujourd'hui tous les groupes sont des trios : elle peut apparaître partout).
- Jouer la manche 1 : chaque groupe est dans la salle annoncée par le tirage, à la place prévue, tournée selon l'orientation de la place.
- Redémarrer le backend en plein lobby : le tirage est le même.
- Créer une partie avec `test_room` : pas de tirage, tous les groupes jouent la salle d'essai.

## T4.3 : la vérification du collage

- Coller les neuf fichiers de l'export (Axiom), aller près des places, cliquer « Vérifier le collage » : chaque place est « bien collée ».
- Ne pas coller une place : elle est « pas collée ». Coller une place avec un décalage d'un bloc ou la mauvaise rotation : « mal collée ».
- S'éloigner de plusieurs places (chunks non chargés) : « chunks non chargés », jamais « bien collée ».
- Le tirage avec un plan dont les places ne sont pas dans l'ordre alphabétique de leurs identifiants : chaque fichier exporté va bien à la place de sa salle (la fiche de collage et la vérification sont d'accord).

## T6.1, T6.2 et T6.4 : corps, bracelet ramassé, déconnexion (à deux comptes)
- Tuer un joueur (commande ou outil de test appelant `GameService.kill`) pendant `ROOMS` : son corps apparaît couché, à son skin, là où il se tenait chez le compte qui est dans la zone ; rien chez un joueur loin, tant qu'il n'a pas vu le corps.
- Un second joueur approche (moins de 8 blocs, sans mur entre) : il apprend la mort (`players[].alive` à faux pour lui seul), le corps apparaît chez lui.
- Viser le corps à moins de 3 blocs : l'invite « Clic droit : prendre le bracelet » apparaît ; clic droit : le bracelet s'affiche, plus petit, à côté du sien ; le corps n'a plus d'invite.
- Viser à plus de 3 blocs ou depuis une autre zone : message d'erreur « trop loin du corps ».
- En `DOORS`, avec le bracelet pris dans un groupe, la porte de ce groupe s'ouvre avec lui ; à la porte finale, un bracelet pris à 9 ne suffit pas si le poignet est plus bas.
- Un mort aux portes ou à 0 point : le corps est visible de tous dès l'annonce ; son bracelet vote « Allier » seul.
- Réglage `announceDeaths` vrai : tous apprennent un meurtre tout de suite.
- Couper la connexion d'un joueur vivant : chez les autres, un mannequin debout « (déconnecté) » à sa dernière position ; en mode automatique l'horloge attend 5 minutes au plus ; au retour le mannequin disparaît, la vue complète revient, la salle et l'inventaire sont intacts.
- Tuer un maître du jeu qui joue : la partie finit, les huit autres gagnent.

## T6.3 : fantômes (à deux comptes, un mort)
- Mode `SILHOUETTE` : le mort, vu par un vivant, a un skin en niveaux de gris et « (fantôme) » après son pseudo ; son corps couché garde ses couleurs. Le mort traverse les portes fermées et ne peut cliquer aucun objet.
- Mode `HIDDEN` : le vivant ne voit plus du tout le fantôme (ni corps, ni pseudo, ni ombre) ; un autre fantôme le voit encore en gris.
- Chez le fantôme : voile gris léger et rappel « Tu es un fantôme » en haut de l'écran.
- Vérifier que le jeu ne plante pas au lancement (les trois mixins sont nouveaux) et que le skin gris ne se fabrique qu'une fois (pas de ralentissement).

## T3.6 retouches (D61, D62, D66, D67, D69)

- **Compteur d'objets (D61)** : dans une salle d'objets cachés, une ligne « Objets : 0 / 3 » apparaît en haut à droite du HUD ; chaque objet ramassé la fait monter ; elle disparaît quand l'étape est résolue ; les autres groupes ne voient pas la tienne.
- **Digicode (D62)** : trois erreurs de suite : verrou de 10 s ; trois nouvelles erreurs : 20 s ; puis 40 s à chaque fois.
- **Pause (D66)** : mettre la partie en pause 5 minutes pendant un compte d'aide : à la reprise, l'aide ne tombe pas plus tôt (le temps de pause n'est pas compté).
- **Fin de `ROOMS` (D67)** : un joueur à 1 point dans un groupe qui n'a pas fini reste à 1 point et vivant.
- **Aides reçues (D69)** : après une aide, l'inventaire (rien de sélectionné) liste « Indices reçus » dans l'ordre ; ils restent relisibles après reconnexion.

## D80 à D83 : fichiers de collage
- Après un export, le chat dit « fichiers envoyés au backend ». `/phantasmon event trahison exports` (admin) liste la partie ; un clic sur un fichier ou « Tout télécharger » l'écrit dans `.minecraft/phantasmon_events/trahison/export/<partie>/`.
- `paste_sheet.txt` s'ouvre avec ses accents (UTF-8) ; dans le hub, chaque coin a aussi « (monde : x=… z=…) » qui tombe bien sur le coin réel ; hors du hub, la fiche le dit.
- Le fichier `all_places` est écrit même quand les places sont très éloignées (jusqu'à la limite des formats).
- « Vérifier le collage » signale une place dont un panneau porte un autre texte.

## T7.1 et T7.2 : rôles secrets et objets cachés (2026-11-17)

À faire avec 9 comptes (ou des factices), un plan qui a des cachettes et une porte ordinaire.

- Démarrer la partie : au prologue, chaque joueur qui a un rôle reçoit une ligne privée de l'animatrice avec son rôle (3 joueurs par défaut). Le maître du jeu qui joue reçoit « Tu es le maître du jeu ». Les autres ne reçoivent rien.
- Écran du joueur : un bloc « Ton rôle secret » avec le nom et le texte du rôle. Aucun autre joueur ne le voit ; seuls les maîtres du jeu (superviseur ou qui joue) ont tous les rôles, dans leur bloc `gm`.
- Gardien : cliquer « Trahir » est refusé avec un message privé ; « Allier » passe.
- Infiltré : trahir à la manche 1 donne +1 aux résultats (le tableau montre +4 face à un « Allier »), pas à la manche 2.
- Voyant (en `VOTE`) : après qu'un camp a voté, un bouton « Cabine n » par cabine fermée (pas la sienne) lui montre ce vote, une seule fois ; refus tant que la cabine n'est pas fermée ou n'a pas voté.
- Parieur : un pari par manche avant le vote du camp d'en face ; un pari juste donne +1, une seule fois dans la partie.
- Fin de partie (porte finale, ou le maître du jeu termine) : l'écran liste tous les rôles avec « (objectif atteint) » pour ceux qui l'ont rempli.
- Cachettes : en temps libre, près d'une cachette, « Fouiller ici » donne un objet (visible seulement de soi). Une cachette vide est refusée. Trop loin est refusé.
- Couteau : choisir la cible dans la liste ; à moins de 3 blocs dans la même zone, elle meurt (corps, cause `knife` chez le maître du jeu) ; derrière une porte fermée (autre zone), refusé et le couteau reste.
- Liens : la cible attachée ne peut plus marcher ni sauter (mixin), peut tourner la tête ; un bandeau rouge avec les secondes ; un autre joueur à côté clique « Détacher » et reste 5 secondes : libérée. Sinon libérée seule après 10 minutes. Un attaché au vote vote « Allier » par défaut.
- Verrou : près d'une porte ordinaire, « Utiliser » le ferme pour tous ; de l'extérieur, « Déverrouiller la porte proche » la rouvre ; la Passe la rouvre de partout ; sinon elle s'ouvre seule après 5 minutes. Verrou sur la cabine d'un groupe : ses membres ne peuvent pas voter (« Allier »).
- Bouclier : le prochain -2 est annulé, l'objet disparaît. Lettre : `/phantasmon event trahison letter 3 coucou` arrive à la place 3 sans nom.
- Lobby : le bouton « Regarder » inscrit comme spectateur (comme la commande), « Arrêter de regarder » le retire.
- D120 : un maître du jeu ou un spectateur qui se tient dans le carré d'une salle voit « Objets : n / total » en haut à droite.

## T11 : la carte du lieu

À faire avec deux joueurs, un maître du jeu et un spectateur, après un tirage des salles. Plan : au moins deux étages déclarés dans l'onglet « Étages » de l'éditeur (par exemple y 60 à 69 et y 70 à 79).

1. **Étages.** Éditeur du plan, onglet « Étages » : créer deux étages, essayer de les chevaucher (par exemple 60..70 et 70..80) puis enregistrer : la liste des erreurs cite « Des étages se chevauchent ». Corriger, enregistrer, recharger (`reload`) : les deux étages sont identiques.
2. **Ouverture.** Dans une partie sortie du lobby, appuyer sur M (touche réglable) : la carte s'ouvre sur l'étage où l'on se tient, avec une flèche à la couleur de son bracelet, orientée comme le regard (nord en haut). Dans le lobby, M affiche seulement « La carte n'est pas encore disponible ». Z (ou Échap) la referme.
3. **Étages.** Cliquer les onglets d'étage, puis tourner la molette sur eux : l'étage change. Un escalier à cheval sur deux étages apparaît sur les deux. « Suivre » recentre la carte sur soi et prend l'étage où l'on est.
4. **Exploration.** Au début, les places de salle sont hachurées et nommées ; entrer dans une place : elle perd ses hachures. L'autre joueur voit **les siennes** seulement. Se déconnecter, revenir : l'exploration est restée. Redémarrer le backend en pleine partie : elle est restée aussi.
5. **Noms.** Dès que le tirage est fait, tout le monde voit les noms de toutes les salles, manches commencées ou non (D151).
6. **Portes.** Ouvrir une porte (par la barre des bracelets ou `set_door` du maître du jeu) : sur la carte ouverte, elle passe de la barre rouge pleine à deux montants verts, sans rouvrir l'écran. Une porte de couleur montre les symboles de ses deux couleurs quand on zoome. Survol : « Porte rouge et verte : ouverte ».
7. **Zoom et déplacement.** Molette sur la carte : zoom autour du curseur ; clic droit tiré : déplacement ; « Cadrer » : tout l'étage. Les noms trop petits disparaissent, l'infobulle les donne.
8. **Dessin.** Crayon : tracer, changer d'encre ; zoomer : les traits restent à leur place. Gomme : effacer un trait. « Tout effacer » demande confirmation, annuler ne perd rien. Tracer sur chaque étage : les traits sont par étage.
9. **Bloc-notes.** Onglet « Bloc-notes » : écrire plusieurs lignes, Entrée, flèches, Début, Fin, Suppr, coller (Ctrl+V), cliquer pour placer le curseur. À 8 000 signes la saisie s'arrête.
10. **Persistance.** Dessiner et écrire, attendre 3 secondes, se déconnecter, revenir (ou se connecter depuis un autre ordinateur) : traits et notes sont revenus.
11. **Secret.** L'autre joueur n'a ni ton curseur, ni tes traits, ni tes notes. Le maître du jeu, le spectateur et le fantôme voient les curseurs de tous (disques à la couleur du bracelet, sans direction), toutes les salles sans hachures, et gardent leurs propres traits. Un joueur vivant ne voit aucun autre curseur.
12. **Bilan (D15).** À la fin de la partie, ouvrir la carte : le bouton « Mes notes / Notes de … » fait défiler les traits et les notes de tout le monde ; on ne peut plus écrire.
13. **Limites.** Dessiner longtemps sur un étage : à 2 000 points la pastille « Limite de dessin atteinte » apparaît et le crayon s'arrête.
14. **Petits écrans.** Vérifier à 320 x 180 et 854 x 480 pixels d'interface (taille de l'interface réglée dans les options) : la barre d'outils passe sur deux lignes, rien ne déborde, la légende devient le « ? ».
15. **Fond fait à la main.** Copier `textures/trahison/map/example.png` en `<id d'étage>.png` (par exemple `main.png`) dans un pack : il remplace les aplats de cet étage (calé sur la boîte englobante, D153).

## T7.3 et T7.4 : tentations automatiques et panneau du maître du jeu
1. **Offre.** Partie de 9 avec au moins 3 humains, vote lancé : un seul joueur reçoit « Une offre, pour toi seul(e) » dès que sa cabine est fermée, tout le monde entend « Une offre vient d'être faite à l'un de vous », sans nom. L'élu trahit : aux résultats il a 1 point de plus que sans l'offre ; il alliait : rien de plus. Journal : `offer`, `offer_taken`.
2. **Offre désactivée.** Dans l'onglet Réglages du lobby, « Offres de cabine » sur Non : plus aucune offre, plus d'annonce.
3. **Fuite.** À l'écran des résultats, un seul joueur reçoit « Une rumeur, et elle est vraie » : le fait est exact (comparer avec l'onglet Journal) et ne parle jamais de lui.
4. **Course.** La première salle résolue par ses joueurs est annoncée avec les noms de son groupe ; une salle résolue avec « Résoudre » ne déclenche pas l'annonce.
5. **Panneau, onglets.** `/phantasmon event trahison gm` (et la page du menu, et la touche une fois liée) : cinq onglets. Joueurs : points, couleur, zone, rôle, objets (infobulle), attaché, hors ligne, factice. Salles : trois cartes, nombre d'aides données, tirage dessous. Votes : ceux qui tombent, plus l'état de l'offre. Journal : les filtres Tout, Votes, Salles, Objets, Morts, Tentations, Maître du jeu.
6. **Gestes.** Cliquer un joueur : Chuchoter (le chat s'ouvre prérempli, le joueur reçoit la ligne en privé), Offre (pendant un vote seulement), Tuer (avec confirmation) puis Ressusciter. Salles : Aide donne le texte suivant, Résoudre ouvre la sortie sans perdre de point. « Parler à tous » : tous entendent la phrase.
7. **Rien ne fuit.** Un joueur ordinaire ne voit ni rôle, ni objets des autres, ni journal ; seul le maître du jeu a ces onglets remplis.
8. **Lobby.** Réglages : rôles (nombre et liste), objets (0 à 2 chacun), annonce des morts ; en cours de partie les boutons sont inactifs avec l'explication.
9. **Petits écrans.** Vérifier le panneau à 320 x 180 et à 854 x 480 pixels d'interface : les onglets, les pastilles d'un joueur (elles disparaissent quand la place manque) et le défilement.
10. **Pause.** Pendant une pause du maître du jeu, les gestes marchent encore (chuchoter, aide, points).

## T8 Cobblemon (backend seulement, rien à jouer en jeu pour l'instant)

- Rien n'est encore visible en jeu : les mécanismes Pokémon n'ont pas d'écran client et la salle d'essai « Cobblemon » n'existe pas.
- Vérifier au backend (`./gradlew test --tests '*Pokemon*' --tests '*BattlePuzzleTest'`) : catalogue, quatre mécanismes et duel verts.

## T8 Cobblemon : écrans des mécanismes Pokémon (non vus en jeu, à valider)

Tous les écrans se testent d'abord hors ligne : `/phantasmon event trahison minigame <dex_code|unown_text|weigh_in|cry_match|pc_box|battle_puzzle|type_chain>`. Vérifier à 320 x 180 et à 854 x 480 pixels d'interface : rien ne sort du cadre, texte lisible.

1. **Clavier Pokédex (`dex_code`).** Chiffres groupés par trois, neuf chiffres tiennent ; code d'essai 025133 déverrouille ; trois erreurs verrouillent.
2. **Mur Zarbi (`unown_text`).** Hors ligne : la phrase s'affiche en signes Zarbi au-dessus du clavier, plus petite sur petit écran ; code d'essai 123. En partie : ouvrir d'abord le texte du mur (page de lecture en Zarbi), puis le terminal : la phrase y est recopiée ; sans lecture, le message « Lisez d'abord… ».
3. **Police Zarbi.** Les 26 signes sont différents ; la page `alphabet` montre chaque signe avec sa lettre. Remplacer `glyphs.png` par une feuille dessinée (13 x 2, autre taille de cellule) : le jeu la prend sans autre changement.
4. **Socles (`weigh_in`).** Cliquer deux socles les échange ; mis dans l'ordre : déverrouille ; « Le plus léger / le plus lourd » (ou petit / grand si `by = height`) ; les modèles Cobblemon s'affichent (sinon un « ? » : signaler).
5. **Boîte PC (`pc_box`).** Les cartes cadenassées ne bougent pas ; la ligne du bas détaille la carte sous la souris (utile sur petit écran) ; la règle se déduit des cartes verrouillées.
6. **Cris (`cry_match`).** Un clic sur « Cri n » joue le cri (silence : signaler, l'id de son `cobblemon:pokemon.<espèce>.cry` est peut-être différent) ; relier cris et Pokémon ; Valider affiche le nombre de paires justes ; les touches 1 à 4 jouent, Entrée valide.
7. **Duel (`battle_puzzle`).** Équipes, PV, attaques en « type + puissance » (jamais un nom), réservistes cliquables, attaque suivante de l'adversaire, tour n sur N, échecs, lignes de la table des types. En partie : un échec remet la position de départ. Hors ligne c'est un aperçu : les boutons ne jouent rien.
8. **Leviers de types (`type_chain`).** Aperçu hors ligne : trois leviers, ordre eau, feu, plante ; un mauvais levier remet à zéro. En jeu, pas d'écran : les marques se lisent en page (symbole et nom du type).
9. **Pages de lecture.** Portrait ou socle : le modèle de l'espèce et son nom ; marque de type : symbole et nom. Si les modèles n'apparaissent pas, signaler (repli sur un « ? »).
10. **Éditeur.** Les sept mécanismes Pokémon sont proposés dans l'éditeur de salle (liste de repli et réponse du backend).

## T8.3 Le partenaire à pouvoir (non vu en jeu, à valider)

Cette étape n'a été vérifiée que par la compilation et `./gradlew test --tests '*PowerSpotTest' --tests '*TrialRoomTest'`. Vérifier à 320 x 180 et à 854 x 480 pixels d'interface : la carte du partenaire (en haut à gauche) ne chevauche rien, le bandeau « Trop sombre » tient dans l'écran.

**Salle d'essai.** Enregistrer `Documentation/trahison/exemples/salle-essai-cobblemon.json` avec `PUT /events/trahison/rooms/essai_cobblemon` (corps = le fichier, en administrateur ; ou ouvrir l'éditeur de salle et recopier ses emplacements), puis construire ses blocs à la main avec `room new essai_cobblemon`. Les positions sont dans le fichier (aile ouest : écrans Pokémon ; aile est : pouvoirs) ; les boîtes des trois emplacements de pouvoir **doivent rester vides d'air** : `lamp` 5 x 3 x 5 (zone sombre de Flash, au sol), `rock` 4 x 1 x 1 (la piste du rocher, Force, 2 poussées : il faut 3 cases libres de long), `bush` 2 x 2 x 2 (les feuilles de Coupe). Les étapes sont toutes ouvertes dès le début ; la sortie s'ouvre quand elles sont toutes résolues. Lancer une partie avec `test_room` = `essai_cobblemon` (réglage du maître du jeu). Essayer Surf en changeant le paramètre `power` de l'étape `cut` en `SURF` (la boîte de `bush` devient alors un mur invisible pour qui n'a pas le pouvoir ; ne pas demander plus de trois pouvoirs différents).

1. **Carte du partenaire.** Au début de la salle, en haut à gauche : « Espèce : Pouvoir » avec le symbole du pouvoir (jamais la couleur seule). Chaque membre a un pouvoir différent dans le groupe de trois de la salle d'essai (Flash, Force, Coupe).
2. **Entité.** Chaque membre vivant a son Pokémon derrière lui, visible de tous ses coéquipiers ; il suit en marchant, se téléporte s'il est loin ; on ne peut pas le cliquer ni le frapper. Si le modèle manque : aucun Pokémon, et le jeu continue (signaler).
3. **Fantôme.** Un joueur mort voit un Fantominus (Spectre) qui le suit, et lui seul ; la carte de pouvoir ne s'affiche plus.
4. **Pad de pouvoir.** Un disque plat avec une bille flottante ; la teinte est celle du pouvoir (jaune Flash, rouge Force, vert Coupe, bleu Surf) et le libellé au regard dit « Emplacement Flash ». Vert quand l'étape est résolue.
5. **Mauvais pouvoir.** Cliquer le pad d'un pouvoir que son partenaire n'a pas : message rouge « Ton partenaire ne peut pas le faire : il lui faut Flash », rien ne change. Seul celui qui a cliqué est prévenu (message et bruit d'erreur).
6. **Flash.** Se tenir dans la zone du pad Flash sans être le porteur : l'écran s'assombrit, plus fort à mesure qu'on s'éloigne du porteur ; à moins de 4 blocs du porteur l'écran est clair ; le porteur ne voit jamais le voile ; le bandeau « Trop sombre ici » apparaît en haut. Le porteur clique le pad : l'étape est résolue et le voile disparaît pour tous.
7. **Force.** Le rocher (cobblestone moussue) est au début de la piste ; le porteur clique : il avance d'une case, le compteur « 1 / 2 » apparaît ; au deuxième clic il est au bout et l'étape est résolue. Un autre joueur qui clique n'y change rien.
8. **Coupe.** La boîte est pleine de feuilles tant que l'étape n'est pas résolue ; le porteur clique : les feuilles disparaissent chez tous.
9. **Surf.** Avec `power = SURF` : le porteur traverse la boîte, les autres sont bloqués par un mur invisible (on les sent, on ne les voit pas), avant et après la résolution.
10. **Redémarrage du backend en cours de salle.** Les partenaires et leurs pouvoirs sont les mêmes (ils se retirent de la graine) ; l'avancement du rocher reste.
11. **Groupe trop petit.** Avec deux joueurs (le troisième est un factice) : si un pouvoir demandé n'est tenu par personne, n'importe qui peut cliquer ce pad.
12. **Éditeur.** `power_spot` est dans la liste des mécanismes, les emplacements de genre « emplacement de pouvoir » s'ajoutent, `spot` n'accepte que ce genre (et plate, pedestal, lever) ; un `power` inconnu ou plus de trois pouvoirs différents est refusé à l'enregistrement.

## Décisions D143, D144, D150, D151 (Infiltré, Voyant, touche de la carte, noms des salles)

1. **Infiltré en paire.** Infiltré dans une paire à la manche 1 : son partenaire vote « Trahir » pour la paire, lui ne vote pas. Aux résultats il reçoit le point du rôle (D143) ; son panneau de rôle dit « fait ». Avec un vote « Allier » de la paire, rien.
2. **Voyant : cabine.** Voyant, en `VOTE`, panneau du rôle : un bouton « Cabine n » n'apparaît que pour les cabines fermées (verrouillées ou libérées) autres que la sienne. Cliquer sur une cabine qui a voté montre son vote, une seule fois ; plus aucun bouton ensuite (D144).
3. **Voyant : refus.** Tant qu'une cabine n'est pas fermée, pas de bouton ; une cabine fermée mais sans vote déposé est refusée avec le message « pas encore voté », sans dépenser le pouvoir (réessayer après le vote).
4. **Touche de la carte.** Sans rien rebinder, M ouvre la carte (D150) ; la touche reste réglable dans les commandes (catégorie Phantasmon Events). Si Cobblemon répond aussi à M, rebinder l'une des deux.
5. **Noms des salles.** Au début de la partie, ouvrir la carte en manche 1 : les noms des salles des manches 2 et 3 sont déjà affichés sur leurs places (D151).

## T5 : le vocal par zones (jamais essayé en jeu ; commencer par `vocal-essai.md`)

1. **T5.1 d'abord.** Faire les quatre points de [`vocal-essai.md`](vocal-essai.md) (gain 0, gain intermédiaire, voix d'une copie du hub, icône « parle »). Ne passer aux suivants que s'ils sont confirmés.
2. **Hors partie.** Sans partie, ou en `LOBBY`, ou après `ENDED`, tout le monde s'entend comme avant (rien n'est filtré). `voice reset` fait par chacun.
3. **Deux salles voisines.** En partie, deux joueurs dans deux zones voisines, porte ouverte : ils ne s'entendent pas. Dans le même couloir (zone à part) ils s'entendent.
4. **Même salle.** Voix : pleine à 4 blocs, qui baisse jusqu'à 8 blocs où elle disparaît. Chuchotement (la touche de Simple Voice Chat) : pleine à 1,5 bloc, disparue à 3 blocs.
5. **Cabine fermée.** Un joueur ferme sa cabine (V) : ni lui ni ceux du dehors ne s'entendent, même collés au mur ; en la rouvrant (fin du vote) ils s'entendent de nouveau. Deux joueurs dans la même cabine fermée s'entendent.
6. **Fantômes.** Un joueur mort (mort connue du joueur qui écoute) n'est plus entendu des vivants ; il entend tout le monde et les autres fantômes. Une mort cachée (D97) : le mort reste entendu tant que la mort est secrète pour celui qui écoute (D200).
7. **Spectateurs.** Un spectateur (commande `spectate`) sur le serveur ou dans une copie du hub entend les joueurs, et eux ne l'entendent pas. Quelqu'un du serveur qui n'est pas dans la partie n'est pas entendu des joueurs pendant la partie.
8. **Annonce du maître du jeu.** Un maître du jeu règle la touche « Trahison : annoncer à tout le monde » (non assignée par défaut), la tient et parle : tous les joueurs, vivants, cabines fermées comprises, l'entendent à plein volume. Il lâche la touche : il redevient un spectateur (silencieux pour les vivants). La touche ne fait rien pour un autre joueur.
9. **Écoute.** Un joueur avec l'objet Écoute, pendant un vote, l'utilise sur une cabine fermée : il entend ceux qui sont dedans, même loin ; le vote fini, plus rien. Si la cabine est hors de la portée du serveur de Simple Voice Chat, il n'entend rien (D203).
10. **Sans Simple Voice Chat.** Events sans Simple Voice Chat se lance et joue normalement (aucun plantage, aucun filtre).

## T9.3 : sliding_blocks, rotating_tiles, safe_dial, twin_screens

Chaque mini-jeu se teste d'abord hors ligne (`/phantasmon event trahison minigame <id>`), puis dans une salle d'essai avec deux joueurs.

- [ ] `sliding_blocks` hors ligne : faire glisser un bloc le long de son axe, le bloc suit la souris sans traverser un autre bloc, flèches après un clic, Tab change de bloc ; sortir la pièce marquée déclenche l'animation de réussite.
- [ ] `sliding_blocks` en ligne, deux joueurs : un glissement de l'un se voit chez l'autre avec le curseur de couleur et le son ; le compteur de coups suit.
- [ ] `sliding_blocks` : un bloc glissé pendant qu'un autre joueur en bouge un autre en même temps ne laisse pas l'écran dans un état faux après la vue suivante.
- [ ] `rotating_tiles` hors ligne : clic gauche tourne dans un sens, clic droit dans l'autre, molette idem, flèches et Entrée au clavier ; les cases atteintes s'éclairent, la lampe s'allume à la fin ; la tuile au rivet refuse avec l'erreur.
- [ ] `rotating_tiles` en ligne : l'éclairage suit la vue du backend (pas d'éclairage tant que la réponse n'est pas arrivée est normal), jamais de solution visible.
- [ ] `safe_dial` hors ligne : tourner la molette fait un clic de plus en plus aigu en approchant, une cloche près du nombre, un bruit sourd dessus ; Verrouiller dans le bon sens valide un rond ; dans le mauvais sens (ou à côté) l'erreur sonne et les ronds se vident.
- [ ] `safe_dial` en ligne : les sons viennent bien de la réponse du backend (écouter avec le son coupé côté serveur ne change rien), le voyant suit ; le sens demandé s'affiche et alterne.
- [ ] `twin_screens` dans une salle : poser les deux terminaux loin l'un de l'autre, chacun avec son indice (`left_code` au terminal gauche, `right_code` au droit). Le joueur A ouvre le gauche, le joueur B le droit ; chacun lit à l'autre ce que son terminal montre, saisit ce qu'on lui dicte ; le premier terminal juste se verrouille, le second ouvre la porte.
- [ ] `twin_screens` : le même joueur ouvre les deux terminaux : le second refuse (aucun siège) ; une saisie fausse vide l'entrée avec l'erreur.
- [ ] `twin_screens` : la définition sans indice au bon emplacement est refusée à l'enregistrement dans l'éditeur (`BAD_GIVE`).
- [ ] Les quatre écrans à 320 × 180 et à 854 × 480 : rien ne déborde du cadre, le texte est lisible, l'aide (`?`) s'ouvre ; Échap ferme sans rien perdre.

## T9.4 : textes de l'animatrice, fins, fragments

- Lancer une partie complète avec des bots : à chaque étape (salon, prologue, portes, salles, temps libre, vote, résultats, final, fin) l'animatrice parle ; deux joueurs connectés lisent **la même phrase**.
- Aux résultats : une réplique `betrayal` ou `alliance` ; si un joueur meurt aux points, une réplique `death` avec son nom.
- Ajouter une variante à `phase.vote` dans `fr_fr.json` et `en_us.json`, reconstruire : sur plusieurs parties de graines différentes, les deux variantes sortent ; une même partie rejouée dit la même.
- Passer le client en anglais : les mêmes moments parlent en anglais ; supprimer un moment du fichier : la clé de langue sert de secours.
- Faire les quatre fins (le MJ termine sans sortis = personne ; un seul sort par la porte finale = solo ; plusieurs = quelques-uns ; tous les sièges = tous) : bandeau de fin de la bonne couleur, réplique `ending.*`, rôles révélés, puis les votes manche par manche, lisibles à 320 × 180 et 854 × 480 (défilement de la colonne).
- Mettre `@fragment:2` dans le texte d'un document de salle, puis un fragment sans texte (`@fragment:50`) : le premier affiche son texte, le second « Fragment 50 — à écrire. ».

## D200 et D228 : vocal des morts cachées, cachette tirée au sort (2026-11-18)

- Vocal (deux comptes vivants dans la même zone, plus un troisième) : le MJ tue en secret le troisième (mort cachée, D97). Le joueur qui ignore sa mort ne l'entend plus tout de suite, alors que sa vue le montre encore « vivant » ; l'autre non plus. Rien dans l'écran ne dit qu'il est mort.
- Le mort caché qui parle à côté d'un vivant : silence pour le vivant. Un fantôme, un spectateur ou le MJ l'entend toujours.
- Une cabine écoutée (objet Écoute) pendant un vote : un mort caché de la cabine n'est pas entendu non plus.
- Éditeur de salle : une salle avec un seul emplacement de genre cachette donne un avertissement jaune (« Une seule cachette ») mais s'enregistre. Avec deux, plus d'avertissement.
- Lancer deux parties avec des graines différentes sur la même salle à deux cachettes : l'écran du MJ montre « cachette : id » dans la carte de la salle, et l'id change d'une graine à l'autre ; rejouer la même graine donne le même id. Un joueur ne voit jamais cette ligne.

## Cachettes des salles (D228, D260)

- Plan avec moins de cachettes que d'objets : au début de la partie, l'écran du MJ montre des objets « sans cachette » ; une fois les salles de la manche 1 lancées, ce nombre baisse de trois au plus (un objet par salle posée) et la pastille de cachette de chaque salle du MJ affiche l'objet qui s'y trouve.
- En temps libre, aller près d'un candidat de cachette d'une salle : la visée affiche « Fouiller » sans nom d'objet ; tous les candidats (le vrai et les inertes) s'affichent de la même façon (rien de dessiné, même libellé).
- Fouiller un candidat inerte : « rien ici », comme une cachette vide. Fouiller le candidat actif : l'objet arrive dans ton inventaire personnel, la pastille du MJ n'affiche plus d'objet.
- Fouiller de loin (plus de 3 blocs du volume de l'emplacement) : refus « trop loin » ; pendant la manche d'une salle qu'on n'a pas finie : « pas maintenant ».
- Salle jouée en rotation est/sud/ouest : la distance de fouille suit bien l'emplacement vu dans le monde.
- Rejouer une salle en temps libre (rejeu) puis redémarrer le serveur : l'objet reste dans le même candidat.

## Créer une salle d'après une fiche (gabarits, éditeur de salle)

Guide pas à pas : [`guide-editeur-salle.md`](guide-editeur-salle.md), section « Créer une salle à partir d'une fiche ». Monde solo plat en créatif, compte admin.

- `room templates` : les 9 fiches avec nom, difficulté, nombre d'étapes et d'emplacements ; aucune « fichier illisible ».
- `room new` puis Tab : après l'id, Tab propose les 9 fiches. Une fiche inconnue (`room new x truc`) est refusée avec la liste.
- `room new archives archives` : la boîte, l'entrée verte, la sortie orange ; un fantôme par emplacement (20 pour `archives`), en **pointillés qui avancent**, au remplissage qui pulse, avec l'étiquette « id : genre ». Le premier (`fiche_1`) est le plus marqué.
- Le panneau s'ouvre sur l'onglet **À placer (20)** ; la ligne en haut à gauche dit « Prochain : fiche_1 — ... (20 restants) » quand aucun écran n'est ouvert ; elle disparaît quand le panneau s'ouvre.
- Onglet **À placer** : l'ordre est celui de la chaîne (fiches, registre, digicode du casier...), les cachettes en dernier ; survoler une ligne montre son repère en entier ; à 320 x 180 la liste défile et les deux boutons restent lisibles ; à 854 x 480 chaque ligne montre le repère sous l'id.
- **Garder la position proposée** : le fantôme devient un emplacement normal (plein, sans pointillés), le suivant est sélectionné, le compteur baisse de 1, la ligne « Prochain » avance.
- **Placer sur la sélection** : grisé tant qu'aucune zone n'est sélectionnée (la bulle explique comment) ; avec une sélection au bâton, le fantôme saute sur la zone et compte comme placé.
- Depuis l'onglet Emplacements, la fiche d'un fantôme montre son repère et « Garder la position proposée » ; « Placer sur la sélection » de la fiche le confirme aussi ; renommer ou supprimer un fantôme garde la liste juste.
- **Enregistrer** avec des emplacements restants : refusé, « Non enregistré : il reste n emplacement(s) à placer », la liste est dans les problèmes (un seul problème, aucune ligne de la liste d'emplacements n'est rougie à cause de lui). À zéro, la liste dit « Tout est placé », l'enregistrement part.
- Une salle faite sans fiche (`room new essai`) ne montre aucun fantôme ni « Prochain », et l'onglet affiche « À placer (0) ».
- Construire `archives` en entier d'après sa fiche (décor, 20 emplacements dont 3 cachettes), enregistrer (version 1), puis `room load archives` dans un autre monde : tout revient placé (aucun fantôme).
- Essai seul : étape `porte` (`three_levers`), `solo` = `true` et `hold` = 30, enregistrer ; partie avec `test_room` = `archives` ; jouer la chaîne ; retirer `solo`, remettre `hold` 12, enregistrer.
- Mêmes gestes pour `serre` (deux postes `twin_screens`), `bal` (zone sombre `power_spot`, boîte vide) et `crypte` (récompense `battle_puzzle` donnée à la résolution) : chacune s'enregistre sans autre erreur que ce qu'on n'a pas construit.

## Plan : démarrage refusé, places qui se chevauchent, portes sans place

- Sur un hub sans plan (ou avec un brouillon incomplet), inscrire 9 joueurs et lancer `start` : la partie reste au salon, message « le plan du hub est incomplet ou invalide ».
- Dans l'éditeur, décaler une place de salle de moins de 48 blocs de sa voisine puis enregistrer en complet : refus `PLACE_OVERLAP` avec les deux identifiants. À 48 blocs exactement : accepté.
- Retirer la 3e place de la manche 2 : refus `DOOR_PLACE` sur la porte vert-bleu de la manche 2, qui dit « place 3 de la manche 2 ».
- Enregistrer un plan complet avec moins de 12 cachettes, ou une zone de couloir dans la boîte d'une place : l'enregistrement passe et `warnings[]` (GET du plan) contient `FEW_SPOTS` / `PLACE_ZONE`.
- Déclarer un étage 0..10 alors qu'une place est à y = 64 : avertissement `PLACE_FLOOR`.

## Essai de salle de l'éditeur (`room try`)
- Dans le monde solo où une salle est construite et **enregistrée** (éditeur ouvert dessus), `/phantasmon event trahison room try` : message « Essai lancé », panneau en haut à gauche « Essai en cours : <id> » avec « Étape 1/n : … ».
- Avant d'enregistrer (ou après une retouche non enregistrée) : refus « Enregistre d'abord la salle ». Hors éditeur : « Ouvre d'abord l'éditeur ».
- Les terminaux, leviers, documents et objets de la salle réagissent au clic, exactement là où l'éditeur les dessinait, quel que soit le sens où tu t'es tenue pour construire (refaire l'essai avec une salle tournée à l'est ou au sud).
- Jouer la chaîne entière : mini-jeux, indices lus, ramasser et utiliser des objets, `I` ouvre le sac ; une aide automatique arrive après 10 min sans progrès ; à la dernière étape : « la sortie est ouverte » et le panneau passe au vert.
- Une étape à trois leviers (`three_levers`) et une étape à deux postes (`twin_screens`) se jouent **seule**, sans toucher `solo` ni `hold`.
- `room try skip` : l'étape en cours se résout, la suivante s'ouvre. `room try reset` : nouvelles solutions, retour à l'étape 1. `room try stop` : le panneau et les objets disparaissent, les emplacements fantômes de l'éditeur reviennent.
- Modifier quoi que ce soit dans l'éditeur pendant l'essai (renommer un emplacement, ajouter une étape) : l'essai s'arrête avec un message.
- Quitter le monde puis revenir : aucun essai ne reste ; relancer `room try` repart de zéro. Un compte non admin n'obtient pas la commande.
- Pendant un essai, une partie en cours sur le serveur (autres joueurs) n'est ni modifiée ni signalée.
