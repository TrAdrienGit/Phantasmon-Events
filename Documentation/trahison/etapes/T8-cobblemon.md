# T8 : Cobblemon

## Objectif

Donner au jeu sa couleur Pokémon, sans toucher aux règles de trahison : des énigmes qui parlent Pokémon, un
Pokémon partenaire à pouvoir, et un duel tactique qui est une épreuve de salle.

**Fini quand :** une salle d'essai « Cobblemon » se joue à trois : un texte en Zarbi à déchiffrer, un passage que
seul le partenaire d'un des joueurs peut ouvrir, et un duel tactique dont la réussite donne le code de la sortie.

## À lire avant

- [`plan.md`](../plan.md), section 5 (mécanismes Cobblemon) et section 6.
- [`architecture.md`](../architecture.md), section 7 (mécanismes).
- Le code de T3 : `Mechanism`, `MiniGameScreen`, un mécanisme existant comme modèle.
- [`solutions-techniques.md`](../solutions-techniques.md) : S8 (partenaire), S9 (le duel tactique), S10 (pourquoi le vrai combat
  à équipe imposée est écarté).
- `Phantasmon-Client/.../ghost/GhostEntityManager.java` : comment une entité Pokémon locale est créée et suit un
  joueur (à lire comme modèle, pas à appeler).
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
- Le partenaire suit le joueur, visible de tous : une entité Pokémon locale créée par chaque client, comme décrit
  en S8 (ne pas passer par les Ghosts du joueur, qui sont ses vrais Pokémon).
- Des emplacements d'un nouveau genre (`power_spot`) : n'agit que le joueur dont le partenaire a le bon pouvoir.
  - Flash : une zone est noire chez tous (voile sombre à l'écran) sauf dans un rayon autour du porteur.
  - Force : pousse un rocher (faux blocs qui se déplacent d'une case).
  - Coupe : retire un obstacle (faux blocs).
  - Surf : traverse un bassin que les autres ne peuvent pas franchir (faux blocs invisibles au bord, retirés
    pour lui).
- Les fantômes reçoivent un partenaire de type Spectre, sans pouvoir.
- **Fini quand :** en jeu, une salle demande deux pouvoirs tenus par deux joueurs différents.

### T8.4 Le duel tactique (Opus)

Le combat-énigme est un mini-jeu à nous, pas un vrai combat Pokémon : solution S9, à appliquer.

- Mécanisme `battle_puzzle`, côté backend : les règles du duel (S9), la génération d'une position, et sa
  **validation par recherche exhaustive** : ne garder qu'une position qui a exactement une suite de coups
  gagnante en N tours. Difficulté réglée par N, le nombre de Pokémon et le nombre d'attaques.
- Côté client : un écran de mini-jeu (cadre de T3.4) qui montre les deux équipes avec les modèles de Cobblemon,
  les PV, les attaques, la règle affichée de l'adversaire, et le compteur de tours. Un échec remet la position de
  départ.
- Réussir donne un indice (un code), comme tout mécanisme.
- Tests : 1 000 positions générées ont toutes exactement une solution ; l'adversaire joue toujours la même réponse
  à la même situation.
- **Fini quand :** le « Fini quand » de l'étape.

### Le gardien : écarté de la première version

Un vrai combat contre un PNJ, avec une équipe imposée au joueur et un résultat connu du jeu, n'est pas faisable
sans modifier Phantasmon (solution S10). Ne pas le coder. Les deux pistes pour plus tard sont notées en S10 ; la
décision revient à la propriétaire.

## Hors périmètre

Le défi à 1 point entre joueurs. Toute modification des règles de vote. Le gardien (voir ci-dessus).

## Pièges

- Les énigmes ne doivent pas exiger de connaître la stratégie Pokémon : une à deux étapes Cobblemon par salle, et
  toujours un indice dans la salle pour qui ne connaît pas.
- Le duel tactique doit rester lisible par quelqu'un qui ne joue pas à Pokémon : la table des types utile est
  affichée dans l'écran.
- Aucune image, aucun son, aucun texte tiré des jeux Pokémon : seulement ce que Cobblemon et Minecraft
  fournissent, ou ce que nous dessinons.
- Le duel tactique ne dépend pas du moteur de combat de Cobblemon : seules ses images de Pokémon sont
  utilisées.
