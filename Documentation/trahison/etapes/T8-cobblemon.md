# T8 : Cobblemon

## Objectif

Donner au jeu sa couleur Pokémon, sans toucher aux règles de trahison : des énigmes qui parlent Pokémon, un
Pokémon partenaire à pouvoir, et des combats qui sont des épreuves de salle.

**Fini quand :** une salle d'essai « Cobblemon » se joue à trois : un texte en Zarbi à déchiffrer, un passage que
seul le partenaire d'un des joueurs peut ouvrir, et un combat-énigme dont la réussite donne le code de la sortie.

## À lire avant

- [`plan.md`](../plan.md), section 5 (mécanismes Cobblemon) et section 6.
- [`architecture.md`](../architecture.md), section 7 (mécanismes).
- Le code de T3 : `Mechanism`, `MiniGameScreen`, un mécanisme existant comme modèle.
- `Phantasmon-Client/.../ghost/` : les Pokémon affichés à côté d'un joueur et vus des autres.
- `Phantasmon-Client/.../api/PhantasmonApi.java` (`startNpcBattle`), `battle/LiveBattleController.java`.
- `Phantasmon-Backend/.../addon/NpcOpponentProvider.java`, `battle/LiveBattleService.java` (lobby et combat
  contre un PNJ ; à lire, pas à modifier), et `backend/.../events/npc/EventNpcService.java` qui l'implémente.
- Les données de Cobblemon (espèces, numéros, types, poids, tailles, cris) : dépôt `cobblemon` présent sur la
  machine, lecture seule.

## Sous-tâches

### T8.1 Les données Pokémon pour les énigmes (Sonnet)

- Côté backend, un petit catalogue en lecture seule : pour chaque espèce retenue, numéro de Pokédex, types, poids,
  taille, lignée d'évolution. Produit par un script (`tools/pokemon_data.py`) depuis les données de Cobblemon, et
  rangé dans les ressources du module backend d'Events.
- La table des types (efficacités).
- Une liste d'espèces « connues de tous » (les plus célèbres), pour que les énigmes ne demandent pas d'être
  expert.
- **Fini quand :** le catalogue se charge au démarrage et un test vérifie quelques valeurs connues.

### T8.2 Les mécanismes Pokémon (Sonnet, un par agent)

Sur le modèle des mécanismes de T3.5.

| Mécanisme | Forme | Ce qu'il tire |
|---|---|---|
| `dex_code` | Des statues ou portraits ; le code est fait de leurs numéros | Les espèces |
| `type_chain` | Des leviers marqués d'un type, à actionner dans l'ordre « qui bat qui » | La chaîne |
| `unown_text` | Un texte en alphabet Zarbi, sur un mur ou un document | Le texte (parmi des modèles) |
| `cry_match` | Mini-jeu : écouter des cris et les associer à des silhouettes | Les espèces |
| `weigh_in` | Ordonner des socles du plus léger au plus lourd | Les espèces |
| `pc_box` | Mini-jeu : ranger une Boîte PC selon une règle à deviner | La règle et le contenu |

- Le rendu Zarbi : une police dessinée par nous, lettre par lettre (aucune image tirée des jeux).
- Les Pokémon montrés (statues, silhouettes) utilisent les modèles de Cobblemon, déjà présents chez les joueurs.
- **Fini quand :** chacun se joue dans la salle d'essai, et 1 000 générations donnent toutes une énigme résoluble.

### T8.3 Le partenaire à pouvoir (Opus)

- Au début d'une salle, le backend attribue à chaque membre du groupe un Pokémon partenaire avec un **pouvoir de
  terrain** : `FLASH`, `STRENGTH`, `CUT`, `SURF`. La définition de la salle dit quels pouvoirs elle demande ; le
  tirage garantit que le groupe les a, répartis sur des joueurs différents.
- Le partenaire suit le joueur, visible de tous : réutiliser l'affichage des Ghosts de Phantasmon s'il s'y prête
  (à vérifier dans le paquet `ghost`), sinon une entité locale sur le même principe.
- Des emplacements d'un nouveau genre (`power_spot`) : n'agit que le joueur dont le partenaire a le bon pouvoir.
  - Flash : une zone est noire chez tous (voile sombre à l'écran) sauf dans un rayon autour du porteur.
  - Force : pousse un rocher (faux blocs qui se déplacent d'une case).
  - Coupe : retire un obstacle (faux blocs).
  - Surf : traverse un bassin que les autres ne peuvent pas franchir (faux blocs invisibles au bord, retirés
    pour lui).
- Les fantômes reçoivent un partenaire de type Spectre, sans pouvoir.
- **Fini quand :** en jeu, une salle demande deux pouvoirs tenus par deux joueurs différents.

### T8.4 Le combat avec équipe imposée (Opus)

- Besoin : lancer un combat contre un PNJ où **l'équipe du joueur est imposée** par le jeu (pas son équipe Ghost
  ni son équipe Cobblemon), et savoir qui a gagné.
- **Sans modifier Phantasmon.** Chercher ce que les points d'entrée existants permettent : `NpcOpponentProvider`
  (déjà implémenté par Events pour ses PNJ), `PhantasmonApi.startNpcBattle`, le combat miroir des admins (qui
  impose une équipe aux deux côtés), les formats. Par exemple : donner au joueur, pour la durée de la salle, une
  équipe Ghost préparée par le jeu.
- Rendre d'abord une courte note (`Documentation/trahison/combat-impose.md`) : la voie retenue, ou, si aucune ne
  marche sans toucher à Phantasmon, ce qui manque exactement. Dans ce second cas, s'arrêter : la décision revient à
  la propriétaire.
- **Fini quand :** un essai en jeu lance un combat à équipes imposées depuis un emplacement de salle, et le
  backend d'Events sait qui a gagné.

### T8.5 Le combat-énigme et le gardien (Opus, après T8.4)

- `battle_puzzle` : une situation fixe (équipes de 1 à 3 Pokémon, attaques, objets, PV), un adversaire au script
  déterministe (il joue toujours la même réponse à la même situation), et un nombre de tours maximal. Une seule
  suite de coups gagne. Réussir donne un indice (un code). Échouer permet de recommencer.
  - Les situations sont écrites à la main et vérifiées par simulation dans le moteur de combat de Cobblemon
    (outil existant : voir la note sur les essais du moteur Showdown).
  - L'aléatoire du combat doit être neutralisé (attaques à précision totale, pas d'effets à chance) pour que la
    difficulté soit la même pour tous.
- `guardian` : un dresseur PNJ barre la sortie ; équipe imposée aux joueurs, IA de niveau réglable. Chaque membre
  du groupe peut tenter ; une victoire suffit.
- **Fini quand :** le « Fini quand » de l'étape.

## Hors périmètre

Le défi à 1 point entre joueurs. Toute modification des règles de vote.

## Pièges

- Les énigmes ne doivent pas exiger de connaître la stratégie Pokémon : une à deux étapes Cobblemon par salle, et
  toujours un indice dans la salle pour qui ne connaît pas.
- Un combat dure : compter son temps dans la durée estimée de la salle.
- Aucune image, aucun son, aucun texte tiré des jeux Pokémon : seulement ce que Cobblemon et Minecraft
  fournissent, ou ce que nous dessinons.
- Le combat-énigme dépend du moteur de combat : une mise à jour de Cobblemon peut changer une solution. Garder les
  simulations comme tests.
