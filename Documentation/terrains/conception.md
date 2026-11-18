# Terrains Reborn : conception

> 2026-11-18. Les combats de la Ligue (arènes, Conseil 4, Maître) se jouent dans un **format maison** avec les
> **terrains de Pokémon Reborn**, à l'identique. Les combats « vanilla » restent pour les tournois.
> Source : les scripts de Reborn (`C:\Jeux\Pokémon Reborn\Scripts`) et le wiki
> (https://pokemon-reborn.fandom.com/wiki/Field_Effects), pour vérifier.

## 1. Ce que veut Adrien

- Des combats avec nos règles en plus de celles de Smogon : les terrains. Devenu le modificateur **League Reborn**
  (§4).
- Dans ce format, un **terrain de base** est posé au début du combat. Il est **permanent**, comme dans Reborn, tant
  qu'une capacité ne le transforme pas (une capacité Eau qui éteint le terrain Brûlant, par exemple).
- Le terrain est **imposé par un PNJ** (comme l'intro et l'ambiance), ou **choisi en PvP** par un joueur, un champion
  d'arène par exemple.
- Les terrains sont **exactement ceux de Reborn** : mêmes chiffres, mêmes interactions.

## 2. Ce que contient Reborn

37 terrains (42 entrées dans les données, dont « pas de terrain » et les 5 stades du Jardin fleuri) :

Electric Terrain, Grassy Terrain, Misty Terrain, Psychic Terrain, Dark Crystal Cavern, Chess Board, Big Top Arena,
Burning Field, Swamp Field, Rainbow Field, Corrosive Field, Corrosive Mist Field, Desert Field, Icy Field, Rocky Field,
Forest Field, Super-Heated Field, Factory Field, Short-Circuit Field, Wasteland, Ashen Beach, Water Surface,
Underwater, Cave, Glitch Field, Crystal Cavern, Murkwater Surface, Mountain, Snowy Mountain, Holy Field, Mirror Arena,
Fairy Tale Field, Dragon's Den, Flower Garden (5 stades), Starlight Arena, New World, Inverse Field.

Un terrain y est fait de deux parties.

**Les données** (`Reborn/fieldtext.rb`, déclaratif), pour chaque terrain :

| Donnée | Exemple (Electric Terrain) |
|---|---|
| Bonus par type, avec condition | Électrik ×1,5 si l'attaquant touche le sol |
| Bonus / malus par capacité (0 = échoue) | Explosion, Surf, Ouragan… ×1,5 ; Bombe Aimant ×2 |
| Précision forcée de capacités | (Grassy : Siffl'Herbe 80 %) |
| Type ajouté à des capacités ou à des types entiers | Surf, Explosion… deviennent aussi Électrik |
| Transformations : capacité → nouveau terrain, avec condition | Lance-Boue, Tectonic Rage → plus de terrain |
| Compteurs vers une transformation | (Cave : 2 séismes font s'effondrer le plafond) |
| Effets après une capacité, effets de transformation | (Corrosive Mist : l'explosion du brouillard) |
| Graine activée et son effet | Graine Élément : Vitesse +1 et Chargeur 2 tours |
| Force Nature, Force Cachée, Mimétisme | Tonnerre, Onde de Choc, type Électrik |
| Capacités de statut renforcées | Chargeur, Ondes Étranges, Vol Magnétik |

Ces données se **convertissent automatiquement** (script : `fieldtext.rb` → JSON), sans les recopier à la main.

**Le code** : tout ce qui n'est pas dans les données est écrit en dur dans le moteur de combat de Reborn, environ
950 endroits (`Battle.rb`, `Battler.rb`, `Battle_Move.rb`, `Battle_MoveEffects.rb`, `Battle_Effects.rb`,
`Battle_Field.rb`) : talents modifiés, statuts bloqués (Misty empêche les statuts au sol, Electric empêche le sommeil),
effets de fin de tour (Burning brûle ceux qui touchent le sol, Underwater noie les faibles à l'Eau), défenses
(Dragon's Den : Défense des Dragons ×1,3), météo bloquée (New World, Underwater), dégâts d'effondrement (Cave),
de bris (Mirror Arena), stades qui montent et descendent (Flower Garden), tirages (Crystal Cavern, Short-Circuit).
Ceux-là se **portent à la main**, terrain par terrain. Taille par terrain, pour se faire une idée (endroits dans le
moteur / dans l'IA) :

| Terrain | Moteur | IA | | Terrain | Moteur | IA |
|---|---|---|---|---|---|---|
| Electric Terrain | 88 | 39 | | Corrosive Mist | 33 | 18 |
| Glitch | 62 | 18 | | Swamp | 29 | 21 |
| Psychic Terrain | 52 | 29 | | Snowy Mountain | 29 | 19 |
| Grassy Terrain | 49 | 29 | | Chess Board | 28 | 26 |
| Flower Garden | 48 | 31 | | Desert | 28 | 19 |
| Rainbow | 45 | 26 | | Fairy Tale | 28 | 41 |
| Starlight Arena | 40 | 28 | | Short-Circuit | 26 | 11 |
| Misty Terrain | 37 | 23 | | Ashen Beach | 24 | 22 |
| Murkwater Surface | 37 | 18 | | Forest | 23 | 22 |
| Wasteland | 36 | 15 | | Icy | 21 | 19 |
| Underwater | 36 | 27 | | Big Top | 20 | 18 |
| Dragon's Den | 36 | 23 | | Crystal Cavern | 20 | 7 |
| New World | 36 | 22 | | Mirror Arena | 20 | 22 |
| Water Surface | 30 | 40 | | Burning | 19 | 19 |
| Holy | 19 | 15 | | Factory | 19 | 13 |
| Corrosive | 18 | 20 | | Mountain | 17 | 13 |
| Cave | 16 | 23 | | Dark Crystal Cavern | 10 | 9 |
| Inverse | 10 | 14 | | Rocky | 10 | 12 |
| Super-Heated | 6 | 11 | | | | |

**Ce qu'on ne reprend pas** : ce qui n'existe que dans Rejuvenation ou Desolation (les autres jeux du même moteur,
gardés par `Rejuv` / `Desolation` dans le code), les Pokémon boss, l'Arceus « Pulse », les modes de difficulté
(« Field Frenzy » : on garde les multiplicateurs normaux), les terrains tirés au hasard par mot de passe. Les
**Crests** (objets propres à Reborn) sont repris (§8).

## 3. Où ça tourne

Les combats Phantasmon tournent dans le moteur Showdown de Cobblemon, sur le jeu de l'hôte (GraalJS,
`GraalShowdownService.context`, public). Le mod Phantasmon Events y **ajoute du code Showdown** au démarrage du moteur
(`BattleThread.ensureShowdown`), sur chaque client qui l'a :

- une **règle Showdown** `Reborn Field` : sans elle, rien ne change ; les combats vanilla et les tournois ne la
  portent pas ;
- un **effet de terrain** (une « pseudo-météo » Showdown, sans durée) par terrain Reborn, branché sur les événements
  du moteur : `onBasePower` (bonus par type et par capacité), `onModifyType` / `onEffectiveness` (types ajoutés),
  `onModifyAccuracy`, `onSetStatus` (statuts bloqués), `onResidual` (fin de tour), `onAfterMove` (transformations,
  compteurs), `onModifyDef` / `onModifySpD`, `onWeather` (météo bloquée)… ;
- la **pile de terrains** de Reborn : le terrain de base en bas, les transformations par-dessus, les terrains
  temporaires (Électrik/Herbu/Brumeux/Psychique posés par une capacité ou un talent : 5 tours, comme dans Reborn) qui
  rendent la main au terrain d'en dessous ;
- les **capacités liées au terrain** : Force Nature, Camouflage, Force Cachée, Mimétisme, les graines, les Pledges
  (Swamp / Rainbow / Burning).

Les terrains « officiels » de Showdown (Électrik, Herbu, Brumeux, Psychique) sont remplacés par ceux de Reborn dans
ce format, comme dans Reborn.

Le code est le même pour l'hôte et l'autre joueur : seul l'hôte calcule, l'autre reçoit le combat comme aujourd'hui.

## 4. League Reborn : un modificateur, pas un format

Décision d'Adrien (2026-11-18) : ce n'est **pas un format de plus**. C'est un **modificateur**, « **League Reborn** »,
qui se greffe sur n'importe quel format Smogon du lobby, par un **interrupteur** :

- format Smogon seul (ses règles, rien d'autre) : les tournois, les combats vanilla ;
- format Smogon **+ League Reborn** : les mêmes règles, plus les terrains Reborn (la règle `Reborn Field`) et les
  Crests.
- Le format de base de la Ligue sera surtout **National Dex Gen 9**, mais rien n'y est figé : le modificateur marche
  avec tous les formats, et la Ligue pourra changer de règlement plus tard sans toucher aux terrains.
- Le backend vérifie les équipes avec les règles du format de base (cercles rouges dans le lobby), plus les Crests
  quand League Reborn est actif.

## 5. Qui choisit le terrain

- **PNJ d'event** : un champ « Terrain » dans l'éditeur, liste déroulante, à côté de l'intro et de l'ambiance.
  Un terrain imposé active League Reborn. Les deux sont verrouillés dans le lobby, comme l'intro.
- **PvP** : c'est le **champion d'arène** qui choisit. Le terrain d'une arène se donne comme récompense (comme
  l'intro de son arène) ; dans le lobby, un joueur qui a une récompense « terrain » peut activer League Reborn et
  choisir parmi ses terrains. L'autre joueur le voit, verrouillé.
- **Ambiances** : on réutilise les ambiances Showtime qui collent ; les autres sont à créer, terrain par terrain
  (il y a bien plus de terrains que d'ambiances). Les ambiances **suivent le terrain en plein combat** : quand une
  capacité transforme le terrain, l'ambiance change avec lui.

## 6. Ce que voit le joueur

- Au début du combat : le message du terrain, avec son nom.
- Pendant le combat : le nom du terrain en cours, toujours visible ; à chaque bonus, transformation ou effet, son
  message ; à chaque changement de terrain, l'annonce et l'ambiance qui suit, si le nouveau terrain en a une.
- Une **fiche du terrain** (comme les Field Notes de Reborn) ouverte d'une touche : ses effets, en clair.
- Nos propres textes, en français et en anglais, écrits à partir de ceux de Reborn, pas recopiés. Crédit à Pokémon
  Reborn sur la page du mod.

## 7. L'IA

L'IA Reborn d'Events est une copie fidèle de celle de Reborn, dont on avait mis de côté la partie terrains (666
endroits dans `Battle_AI.rb`). On la remet, pour que les champions jouent avec leur terrain. L'IA experte apprend les
multiplicateurs (dégâts estimés avec le terrain), pas plus.

## 8. Décisions (Adrien, 2026-11-18)

- [x] **Pas de format maison** : un modificateur **League Reborn**, interrupteur sur un format Smogon (§4).
- [x] **Règles de base** : celles du format Smogon choisi ; surtout National Dex Gen 9 pour l'instant, un règlement
      à nous plus tard peut-être.
- [x] **Ordre des terrains** : aucun. Tous sont à faire.
- [x] **PvP** : le champion d'arène choisit le terrain (§5).
- [x] **Crests** : repris, en objets virtuels (comme les Méga-gemmes des Ghosts).
- [x] **Ambiances** : réutiliser celles qui collent, créer les autres ; elles changent avec le terrain en plein
      combat.

## 9. Étapes

1. **Socle.** Le script de conversion des données ; l'injection dans le moteur ; la règle `Reborn Field` ; la pile
   de terrains, les transformations, les compteurs, les graines, Force Nature et compagnie ; l'affichage (nom,
   messages, fiche) ; l'interrupteur League Reborn dans le lobby ; le champ « Terrain » des PNJ ; la récompense
   « terrain » des champions. Avec les 4 terrains de base (Electric, Grassy, Misty, Psychic), qui servent aussi de
   terrains temporaires partout.
2. **Tous les autres terrains**, par lots (données + code + IA), sans ordre imposé.
3. **Les Crests**, en objets virtuels.
4. **Les ambiances** : la table terrain → ambiance, les nouvelles ambiances, le changement d'ambiance en combat.

Chaque terrain est **testé contre Reborn** : les mêmes situations rejouées dans le moteur Showdown en Node (déjà
installé), et les dégâts comparés au calcul de Reborn, avant d'aller en jeu.

## 10. Avancement

**2026-11-18 : le moteur commun, testé hors jeu.**

- `tools/reborn-fields/convert.js` : `fieldtext.rb` → `src/main/resources/assets/phantasmon_events/league_reborn/fields.json`
  (chiffres seulement, messages par numéro) et `reference-texts.json` (les textes de Reborn, pour écrire les nôtres :
  **ne pas livrer**, ne pas commiter). Relancer après chaque mise à jour de Reborn.
- `league_reborn/engine.js` : la règle `League Reborn = <terrain>`, la pile de terrains (permanents, transformations,
  terrains temporaires de 5 tours qui rendent la main), les bonus par capacité (puissance) et par type (dégâts, avec
  leurs conditions), le bonus ×1,3 d'une capacité qui transforme le terrain, les types ajoutés (efficacité et
  immunité), les précisions forcées, les compteurs, les capacités qui échouent (×0), les effets nommés (explosion du
  brouillard, effondrement de la Caverne, bris des miroirs, pollution de l'eau, pics de glace, perte de précision),
  Force Nature, les terrains de Showdown remplacés (capacités Champ, talents Surf…), la météo bloquée (Underwater,
  New World). Le code propre à chaque terrain va dans `HOOKS`.
- `tools/reborn-fields/test.js <dossier showdown>` : 11 tests dans le Showdown de Cobblemon (dossier `showdown/` d'une
  instance), tous verts.

**2026-11-18 (suite) : branché dans le jeu (pas encore testé en jeu).**

- Backend : récompense `FIELD` (valeur = id du terrain), `BattleLobbySetField`, `field` / `field_fixed` / `fields` dans
  `BattleLobbyUpdated`, la règle ajoutée à `battle_rules`, et **celui qui pose le terrain héberge** (l'autre n'a pas
  besoin du mod Events : il ne fait qu'afficher). Interface `BattleFieldProvider` du core, fournie par
  `LeagueRebornFields` du module Events (liste des ids, à garder alignée sur `fields.json`).
- PNJ : colonne `event_npcs.field` (V12 du module), champ « Terrain (League Reborn) » dans l'éditeur ; le terrain est
  alors imposé et verrouillé dans le lobby.
- Client core : `ShowdownExtensions` (registre de code JS installé dans le Showdown de Cobblemon avant chaque combat
  hébergé, réinstallé si le moteur l'a perdu). Lobby : bouton « Terrain : … » en haut à droite de sa fiche, grille des
  terrains permis ; visible seulement avec le mod Events et un terrain permis, ou quand un terrain est posé.
- Mod Events : `reborn/LeagueReborn` installe `engine.js` + `fields.json`, lit les lignes `-leaguereborn` et les dit
  dans le chat du combat. Textes FR/EN à nous : noms des 41 terrains, une phrase d'intro par terrain, messages
  génériques (bonus de capacité, de type, transformation, fin d'un terrain temporaire, effets nommés).
  Un texte propre à un message de Reborn se branche par la clé `league_reborn.<terrain>.move|type|change.<n>` :
  s'il existe dans le fichier de langue, il remplace le générique (238 messages à écrire, lot suivant).

Essai en jeu validé par Adrien (PNJ sur Cave, Séisme fait s'effondrer la grotte). Affichage du terrain en haut au centre
(`LeagueRebornHud`, style Battle Extras, sous ses lignes) avec les tours restants d'un terrain temporaire.

Reste à faire : les textes propres
à chaque message ; puis l'étape 2 (le code propre à chaque terrain dans `HOOKS`).

Écarts connus avec Reborn, à traiter : l'efficacité combinée de Reborn ne descend pas sous ×0,25 (arrondi
`ceil` de `fieldTypeChange`) ; Showdown arrondit les multiplicateurs au 4096e, Reborn calcule en flottants (écarts
d'un point de dégâts possibles).
