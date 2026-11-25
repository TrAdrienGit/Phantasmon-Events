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

**2026-11-18 (suite) : branché dans le jeu (validé en jeu par Adrien le même jour).**

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

**2026-11-18 (suite) : le code propre aux 4 terrains de base (validé en jeu par Adrien : Spore, Vive-Attaque, soin Herbu, Cage-Éclair).**

Le code écrit à la main dans les scripts de Reborn (hors Rejuvenation) pour Electric, Grassy, Misty et Psychic, dans
`engine.js` :

- `HOOKS.<terrain>` et `ALL` (tous terrains : capacités qui posent un terrain, effets partagés par une liste de
  terrains). Points d'accroche : `setStatus`, `tryAddVolatile`, `tryMove`, `tryHit`, `modifyMove`, `basePower`,
  `modifyDamage`, `modifySpD`, `entry`, `afterMove`, `residual`.
- Tables : `BOOSTS` (capacités de statut renforcées : Enroulement, Croissance, Chargeur, Ondes Étranges, Brume Aromatique,
  Doux Parfum, Force Cosmique, Plénitude, Machination, Yoga, Kinésie, Lévikinésie), `SELF_BOOSTS`,
  `SECONDARY_BOOSTS` (Sprint Bouclier, Aile Psychique, Force Mystique), `LONG_ROOMS` (Distorsion, Zone Magique,
  Zone Étrange, Gravité : 8 tours), Vœu à 75 %. Les listes reprennent tous les terrains que nomme Reborn, pas
  seulement les 4.
- Electric : pas de sommeil au sol (Bâillement compris), Mitra-Poing échoue, Turbo +1 Vitesse par tour, Début Calme
  compte double, Comatose n'est pas endormi (Blabla Dodo, Ronflement, Châtiment, Réveil Forcé… et il peut être
  statué), Galvanisation ×1,5, Transistor reçoit Sol ×0,5, Éclair Fou sans contrecoup, Vol Magnétik 8 tours, Cage
  Éclair serre plus fort, Déluge Plasmique et Poing Plasma posent Electric 3 tours, Gobe-Bombe en forme Gobe-Tout.
- Grassy : soin de 1/16 au sol en fin de tour, Ire de la Nature 75 %, Soin Floral complet, Effilochage −2,
  Troquenard serre plus fort.
- Misty : pas de statut ni de confusion au sol, Fée Déf. Spé. ×1,5, Peau Sèche soigne 1/16, Anneau Hydro double,
  Écaille Spéciale toujours active, Voile Pastel contre Poison, Peau Féérique ×1,5, Animacœur +1 Déf. Spé., Sable
  Humide +2 Déf. à l'entrée, Brume pose Misty 3 tours.
- Psychic : priorité bloquée contre une cible au sol, Force Pure double l'Atq. Spé. au lieu de l'Atq., Télépathie
  double la Vitesse, Power Spot ×1,5, Anticipation +2 Atq. Spé. à l'entrée, Force Cachée confond, Confidence a
  son texte.
- Mimétisme prend le type du terrain (donnée `mimicry`, tous terrains) ; un changement de terrain relance
  `TerrainChange` de Showdown (Charge Quantique, Mimétisme, graines).
- Le code de Showdown modifié (talents, capacités, conditions) l'est par `PATCHES`, une fois par Dex : Showdown gèle
  ses entrées, on remplace donc l'entrée brute et on vide son cache. Hors League Reborn, chaque patch rend le
  comportement de Showdown (testé).
- `test.js` : 28 tests, tous verts.

**2026-11-18 (suite) : lot 2, les terrains aquatiques (validé en jeu par Adrien le même jour).**

Water Surface, Underwater, Murkwater Surface et Swamp, plus ce que Reborn partage entre terrains :

- `ALL` devient une liste de groupes (`shared({...})`) ; nouveaux points d'accroche `modifyType`, `modifySpe`,
  `chargeMove`, et `effectiveness` (propre au terrain, remplace le `typeMod`).
- Force Cachée : la table complète de Reborn (30 %, tous les terrains, tirage au hasard sur Rainbow, Wasteland,
  Crystal Cavern, Bewitched ; paralysie ailleurs).
- Les Aires (Feu/Herbe/Eau) : deux Aires différentes posent un terrain 4 tours (7 avec la Roche Amplichamp) : Eau +
  Plante → Swamp, Feu + Eau → Rainbow, Feu + Plante → Burning.
- Dans l'eau (Underwater partout, les deux surfaces au sol) : Glissade ×2 Vitesse, Surf Caudal ×2, Torrent toujours
  actif, Hélice Caudale ×1,5 sur la priorité, Hydratation et Ignifu-Voile soignent, Machine à Vapeur +1 Vitesse,
  Sable Humide +2 Déf. par tour, Plongée en un tour, Siphon confond et serre plus fort, Vague Fracassante recul 1/4,
  Ardeur +2. Les non-Eau au sol ralentissent (×0,75 en surface, ×0,5 sous l'eau, où le physique non-Eau fait ×0,5).
- Water Surface : Absorb-Eau et Peau Sèche soignent 1/16, l'eau lave le Goudronnage, Trempette baisse la Précision,
  Rosée Vitale donne l'Anneau Hydro, Picots et Pics Toxik coulent.
- Underwater : Eau contre Eau neutre, Électrik ne rate jamais, les Pokémon que l'Eau touche fort « se débattent »
  (dégâts selon l'efficacité, doublés pour Corps Ardent et Armumagma).
- Murkwater Surface : l'eau toxique blesse au sol (bien plus en Plongée), sauf Acier, Poison et quelques talents ;
  un Poison avec Absorb-Eau ou Peau Sèche soigne 1/8 ; les capacités de boue deviennent Eau ; Acidarmure +3.
- Swamp : −1 Vitesse par tour au sol (sauf Grosses Bottes et quelques talents), un dormeur perd 1/16, Peau Sèche
  soigne 1/16, Hurlement a son texte.
- Terrains toxiques (Corrosive, Corrosive Mist, Wasteland, Murkwater) : Choc Venin, Baryline et Piège de Venin
  agissent comme sur une cible empoisonnée, Cruauté fait toujours mouche, Rage Poison ×1,5 en physique, Soin Poison
  soigne 1/8. Suintement double, Puanteur 20 %, Visco-Corps −2 (Swamp, Murkwater), Vole-Force ×1,3 (Swamp,
  Forest), Banc reste en forme Banc dans l'eau.
- `test.js` : 37 tests, tous verts.

Reste à faire : les textes propres à chaque message ; les autres terrains ; les graines de Reborn (les graines de
Showdown réagissent pour l'instant à ses 4 terrains) ; Gobe-Bombe sous l'eau. Depuis : les autres terrains sont faits
(lots 3 à 8, le jour même), les graines aussi (2026-11-19).

Écarts connus avec Reborn, à traiter : l'efficacité combinée de Reborn ne descend pas sous ×0,25 (arrondi
`ceil` de `fieldTypeChange`) ; Showdown arrondit les multiplicateurs au 4096e, Reborn calcule en flottants (écarts
d'un point de dégâts possibles).

**2026-11-18 (suite) : lots 3 à 8, tous les autres terrains (validés en jeu par Adrien le même jour, poussés : `f2371b9`, `5f64984`).**

Le code propre de Reborn pour les 37 terrains restants, en un passage (même méthode : `HOOKS`, `shared`, `PATCHES`) :

- Lot 3, poison (Corrosive, Corrosive Mist, Wasteland) : entrée et sommeil corrosifs, la brume empoisonne tout le
  monde chaque tour, Moiteur bloque Éruption et Aire de Feu, Wasteland recrache les pièges en fin de tour (Piège de
  Roc, Picots, Pics Toxik, Toile Gluante), Avale de Wasteland, Racines et Anneau Hydro pourris, Vampigraine /4,
  Boom Final, Point Poison, Pose Spore, Visco-Corps, Corrosion ×1,5 et statut au hasard.
- Lot 4, feu (Burning, Superheated, Dragon's Den) : table `fieldDefense` de tous les terrains, pas de gel,
  Armumagma immunisé au Feu, Rage Brûlure, Brasier, Colère en un tour, la grêle fond, pluie ou sable éteignent
  Burning, brûlure passive, Multiécaille, Mue, Danse Draco et Rugissement +2.
- Lot 5, glace, montagne, roche, grotte, sable (Icy, Snowy Mountain, Mountain, Rocky, Cave, Desert, Ashen Beach) :
  météo de 8 tours, vents forts, Vol/Rebond sans charge, Force devient Combat, Voix Enjôleuse devient Glace, Sol
  touche tout dans la grotte, Piège de Roc ×2, Voile Aurore, Voile Sable, Rideau Neige, Ailes Bourrasque, glissade
  sur la glace, chute sur les rochers, Écaille Glace, Puissance Cachée de la montagne enneigée…
- Lot 6, nature et machines (Forest, Factory, Short-Circuit, Inverse, Flower Garden 1 à 5, Indoor) : Croissance,
  Défense Ordre, Allègement, Changement Vitesse, Cri Métal, Doux Parfum, Feuille Garde, Soin Ordre, Champlification
  ×2, Vol Magnétik 8 tours, Malédiction Sylvestre, Renversement pose Inverse 3 tours, Rouleau d'Acier rase le
  terrain, tables d'efficacité inversées, tirages Short-Circuit, Technicien, Motorisé, Plus/Minus, Statik, Télécharge.
- Lot 7, lumières (Rainbow, Starlight, Holy, Crystal Cavern, Dark Crystal Cavern, Mirror) : pluie + soleil font un
  arc-en-ciel, le soleil allume les cristaux noirs, Normal touche Spectre et Ténèbres sur Holy, types ajoutés au
  hasard (arc-en-ciel, cristaux), miroirs (une attaque ratée heurte un miroir, écrans 8 tours), Lune, Aurore et
  Synthèse, Victorieux, Spectro-Bouclier, Peau Miracle, Justice +2.
- Lot 8 (Glitch, New World, Fairy Tale, Chess, Big Top) :
  - Glitch : Ténèbres, Acier et Fée deviennent Normal, catégorie selon le type (3e génération), une seule stat
    Spéciale (la plus haute des deux), ancienne table (Dragon neutre partout, Insecte et Poison super efficaces
    l'un sur l'autre, Spectre sans effet sur Psy), critique +1 si plus rapide, Explosion ×2, Ultralaser sans
    recharge après un K.O. Conversion puis Conversion 2 posent Glitch 5 tours.
  - New World : Ire de la Nature 50 %, Cœur de Rechange partage les PV, Gravité pose Starlight et le monde se brise
    à nouveau quand elle s'arrête, Danse Lune +1 partout.
  - Fairy Tale : Acier super efficace sur Dragon, Lame Sainte, Coupe, Tranche deviennent Acier, Bisou et
    Vampibaiser réveillent, Buée Mystique 100 %, Aura Féérique ne rate jamais, Majesté ×1,5, Esprit d'Acier ×2,
    Bouclier Royal bloque le statut et baisse aussi l'Atq. Spé., Vigilance et Garde Florale +1 Déf./Déf. Spé.,
    armures et magie à l'entrée, Vœu Soin +1 Atq.
  - Chess : pièces attribuées à chaque équipe (dernier = Reine, premier = Pion, plus fragile = Roi, puis selon la
    meilleure stat : Cavalier, Fou, Tour) avec leurs bonus d'entrée ; le Roi a +1 en priorité, le Pion tient à 1 PV
    depuis ses PV max et esquive les K.O. en un coup, la Reine ×1,5, le Cavalier ×3 sur la Reine et ×1,25 en
    attaque de zone ; capacités d'échecs ×0,5 ou ×2 selon le talent de la cible ; Ultime Bastion +2/−1.
  - Big Top : le marteau de force (Combat physique et capacités de frappe : ×0,5 à ×3 selon le tirage et l'Atq.),
    son ×1,5, Danse-Lames +3, Papillodanse et Danse Victoire +2, Danse Plumes −3, Cognobidon +1 Déf., Âme Martiale
    50 % des PV pour +2, Encore 3 tours de plus, Acrobatie toujours doublée, Projecteur, Danseuse, pluie 8 tours.
- Textes FR/EN à nous pour chaque nouveau message (`league_reborn.text.*`). `test.js` : environ 75 tests, tous verts.

Écarts comblés ensuite (même jour) : Piège de Roc cristallisé sur Crystal Cavern, Géo-Contrôle en un tour sur
Starlight, Mimétisme (type des cristaux, type au hasard sur New World, ??? sur Glitch), Arceus et Silvally tirent un
type sur New World, Silvally devient ??? sur Glitch et Ténèbres sur Holy, critiques du Miroir selon esquive et
précision, Voile Miroir +1 Esquive/Déf./Déf. Spé. sur Mirror, Métronome limité à 70 de puissance et objets/talents
spéciaux désactivés sur Glitch (Lunettes Choix, Veste de Combat, Évoluroc en Déf. Spé., Balle Lumière, Dent et
Écaille Océan, Force Soleil, Batterie), Bénédiction Lunaire 1/3, Grelot Coque 1/4 sur Ashen Beach, Mode Transe de
Darumacho (Ashen Beach, et Burning/Superheated pour la forme de Galar).

Restent hors de portée : Frénésie et Repli Factice (absents de Showdown) ; Boule Roc qui continue sur Crystal Cavern (pas
de coup à 0 dégât dans Showdown) ; le second Pion d'un combat double sauvage ; sur Glitch, en attaque de zone, la Déf.
Spé. retenue est celle de la dernière cible. Médic Nature, Régé-Force et les « 5 capacités de zone » de Flower Garden
n'ont en fait pas de code de terrain dans Reborn.

**2026-11-19 : les objets propres à Reborn.**

- Graines Élémentaire, Magique, Tellurique, Synthétique et Roche Amplifield : déclarées dans le Dex de Showdown
  (`ITEMS` dans `engine.js`, nom, Dégommage) et tenues « virtuellement » comme les Méga-Gemmes de Phantasmon : Events
  les ajoute à la liste de l'éditeur de PC par `VirtualItems.register` (point d'extension du client, le cœur ne les
  nomme pas). Icônes : `tools/reborn_item_icons.py`.
- `seedCheck` (Battle.rb) : à l'entrée d'un Pokémon et à chaque changement de terrain, chaque porteur de la graine du
  terrain l'utilise, du plus rapide au plus lent : ses stats (`seed.stats` de fields.json, Contestation comprise), le
  code propre au terrain (Vœu de 3/4 des PV sur Misty/Rainbow/Starlight, Toxik grave sur Corrosive Mist et Murkwater,
  pics de glace sur Icy selon les Picots, chute de pierres sur Rocky et Cave selon l'efficacité Roche, Piège de Roc
  des deux côtés sur Wasteland, type Eau sous l'eau, ??? sur Glitch, confusion sur Psychic Terrain), puis l'effet
  (Chargeur, Racines, Reflet Magik, Coup d'Main, Anneau Hydro, Affilage, Vol Magnétik, Puissance, Torche, Blockhaus,
  Pico-Défense, Bouclier Royal, Danse Flamme / Tourbi-Sable sur soi, recharge façon Ultralaser), la graine consommée,
  et Flower Garden qui pousse d'un stade.
- Écart : Carapiège (Superheated) ne donne que sa Déf. +1 (le piège demande la capacité choisie) ; Puissance donne le
  +2 de Showdown.
- Textes `league_reborn.text.seed_*` FR/EN. `test.js` : 5 tests de plus, tous verts.

**2026-11-19 : une ambiance et un stade pour chaque terrain (validé en jeu par Adrien, poussé).**

- `reborn/RebornAmbiance` : la table terrain → ambiance de Cobblemon Battle Showtime, imposée au combat
  (`ShowtimeHooks.setAmbianceOverride`) ; le stade du terrain (mer, fond marin, grottes, landes, halls…) quand le
  joueur l'a demandé. Les ambiances et les stades eux-mêmes sont dans Showtime (`Documentation/stadiums.md`).
- Le PNJ peut imposer sa scène ; le lobby propose les préréglages, stades et ambiances (Phantasmon).

**2026-11-20 : les quatre derniers stades, le terrain lu dans les règles du combat (validé, poussé).**

- Quatre halls de plus dans Showtime pour les terrains qui n'en avaient pas (Electric, Psychic, Glitch, Inverse).
- Le client lit le terrain dans les règles du combat (`League Reborn = <terrain>`), aussi pour un spectateur.

**2026-11-20 et 2026-11-21 : les transitions entre terrains (validées en jeu, poussées).**

- Quand un terrain se transforme en combat, une scène de deux à trois secondes remplace la coupure : moteur dans
  Showtime (`ambiance/Transitions`, format dans son `data-formats.md`), contenu ici (`assets/phantasmon_events/transitions/`,
  62 fichiers : cristaux qui poussent, s'éteignent ou s'effondrent, eau polluée, purifiée, gelée, plongée et remontée,
  herbe, brume, poison, lave qui refroidit, arrivée et départ d'un terrain).
- Référence de qualité : Crystal Cavern → Cave (2026-11-20 : caméra large qui tourne, secousses, débris).
- Les graphes des changements de terrain sont sur la page `/wiki` du site public (Village 14).

**2026-11-22 : les combats vus de loin (validé en jeu par Adrien).**

- `reborn/DistantFields` : un joueur qui regarde de loin un combat League Reborn voit l'ambiance de son terrain
  (`ShowtimeHooks.addDistantAmbianceOverride`) ; les vrais blocs autour du terrain sont habillés de ceux du stade.

**En cours (2026-11-20, pas encore validé ni commité) :** l'IA Reborn avec réglages, dont la prise en compte des
terrains (voir `ia-reborn-terrains.md`).
