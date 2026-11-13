# Solutions techniques

> Recherche du 2026-11-13, faite en lisant le code de Phantasmon, d'Events, de Simple Voice Chat et de Cobblemon.
> But : qu'aucun agent n'ait à refaire ce travail. Chaque point dit **quoi faire**, **sur quoi cela repose**, et
> **ce qui reste à confirmer en jeu** (rien ici n'a encore tourné).
>
> Règle rappelée : on ne modifie ni Phantasmon ni Showtime. Tout ce qui suit tient dans Events.

## Résumé

| Sujet | Étape | Solution | Statut |
|---|---|---|---|
| S1. Faux blocs (portes, cabines) | T2.2 | Ouvrir `MirrorGhosts` d'Events à d'autres sources que les hubs miroirs | Trouvée |
| S2. Savoir où est chaque joueur | T2.2 | Le client envoie sa zone et sa position ; le backend ne peut pas les lire dans le hub | Trouvée |
| S3. Objets cliquables, corps, mannequins | T3.3, T6 | Entités locales, clic capté par `UseEntityCallback` | Trouvée |
| S4. Couper ou baisser une voix chez celui qui écoute | T5 | Greffon Simple Voice Chat propre à Events, événement `ClientReceiveSoundEvent` | Trouvée, à confirmer en jeu |
| S5. Voix venant des copies du hub | T5 | Le même événement la voit passer ; identifiant de l'avatar à reconvertir | Trouvée, à confirmer en jeu |
| S6. Masquer un mort, skin noir et blanc | T6.3 | Mixin sur le rendu des entités ; texture de skin recalculée | Trouvée |
| S7. Attacher un joueur | T7.2 | Mixin sur la lecture du clavier de déplacement | Trouvée |
| S8. Pokémon partenaire | T8.3 | Entité Pokémon locale, comme les Ghosts de Phantasmon | Trouvée |
| S9. Combat-énigme | T8.4 | Un mini-jeu de duel tactique à nous, simulé par le backend. Pas de vrai combat | Trouvée (change la fiche) |
| S10. Combat réel à équipe imposée (gardien) | Écarté | **Impossible sans toucher à Phantasmon.** Reporté ; deux pistes notées | Bloqué, décision à prendre |
| S11. Écrire `.schem` et `.litematic` | T4.2 | Formats décrits ici ; rotation par les outils de Minecraft | Trouvée |
| S12. Capturer une salle avec ses entités de bloc | T3.2 | Lire le monde du serveur intégré, en solo | Trouvée |
| S13. Garder les fichiers de salle sur le backend | T3.1 | Le dossier des schématiques de hub, sous-dossier à part | Trouvée |
| S14. Afficher du texte et des tableaux dans le monde | T2.3, T2.4, T3.3 | Rendu dans le monde par `WorldRenderEvents` | Trouvée |

## S1. Faux blocs

**Quoi faire.** Ne pas réécrire de système de faux blocs. Events en a déjà un, complet : les blocs fantômes des hubs
miroirs. Il suffit de lui ajouter une entrée publique pour qu'une autre partie d'Events pose et retire les siens.

**Sur quoi cela repose.** `src/main/java/com/mystaria/events/hub/MirrorGhosts.java` :

- un faux bloc est un vrai bloc du monde du client (même rendu, même lumière, mêmes collisions), que le serveur
  ignore ;
- `interceptServerBlock` (appelé par `ClientLevelMirrorMixin`, sur `ClientLevel.setServerVerifiedBlockState`) met de
  côté ce que le serveur envoie sous un faux bloc et le restitue quand le faux bloc part ;
- `onChunkLoad` les repose quand un tronçon revient ;
- `MultiPlayerGameModeGhostMixin` empêche de les casser et de cliquer dessus vers le serveur.

Aujourd'hui tout y est privé et piloté par le message `EventsMirrorGhosts` d'un hub.

**À coder.** Dans `MirrorGhosts`, une source nommée en plus des hubs :

```java
/** Client-only blocks of another feature (the betrayal game's doors): shown and kept like a mirror hub's ghosts. */
public static void putLocal(String owner, BlockPos pos, BlockState state)
public static void removeLocal(String owner, BlockPos pos)
public static void clearLocal(String owner)
```

Elles réutilisent `set`, `remove`, `show` et les tables existantes (`GHOSTS`, `SERVER`, `BY_CHUNK`), avec un
propriétaire (`"trahison"`) à la place du nom de hub. `world/FakeBlocks` du jeu ne fait qu'appeler ces trois
méthodes d'après l'état des portes reçu du backend. C'est la seule retouche au code existant d'Events pour T2.

**À savoir.**

- L'emplacement d'un faux bloc doit être de l'air (ou traversable) sur le serveur : une porte est une ouverture
  réelle.
- Un maître du jeu dans une copie du hub peut déjà poser de vrais blocs fantômes à la main : ils apparaissent chez
  les joueurs. C'est un secours utile un soir de partie, sans code.
- Un fantôme ou un spectateur ne reçoit simplement pas les faux blocs des portes.

## S2. Où est chaque joueur

**Constat.** Le client envoie déjà sa position au backend dans le hub (`HubController`, message `HubMove` : `x`, `z`
en coordonnées de hub, et `y` exact dans un hub miroir). Mais côté backend, `HubService` ne donne pas ces positions
aux add-ons : seuls `isMember(joueur)` et `sameHubMembers(joueur)` sont publics.

**Quoi faire.**

- Le client calcule sa **zone** d'après le plan et envoie `EventsTrahisonZone { zone, x, y, z }` à chaque changement
  de zone, et au moins toutes les 2 secondes pendant une partie (position du corps en cas de mort).
- Le backend utilise `HubService.sameHubMembers` pour savoir qui est dans le hub : un membre non inscrit à la
  partie est un spectateur.
- Côté client, la position des autres est connue sans rien demander : `HubController.members()` (état de chaque
  membre) et `HubAvatars.playerOrAvatar(level, uuid)` (le vrai joueur s'il est là, sinon son avatar).

## S3. Objets cliquables, corps, mannequins

**Quoi faire.** Tout objet du jeu posé dans le monde est une **entité locale**, comme les PNJ d'Events. On y gagne
la visée au réticule, la boîte de collision, la portée et le rendu, sans rien inventer.

**Sur quoi cela repose.** `src/main/java/com/mystaria/events/npc/` :

- `EventNpcEntity` : une entité créée par le client, avec un identifiant **négatif** (`NEXT_ID.getAndDecrement()`),
  ajoutée par `level.addEntity(entity)`. Le serveur ne la connaît pas.
- `EventNpcs` : `UseEntityCallback.EVENT.register(...)` capte le clic droit sur l'entité et renvoie un résultat
  qui empêche l'envoi au serveur. Pour le clic gauche : `AttackEntityCallback`.
- `PhantasmonApi.registerWheelTarget` si l'on veut la roue de Cobblemon (touche R) sur une entité.

**À coder.**

- `world/Interactables` : une petite entité (`InteractableEntity`) par emplacement actif, de la taille de
  l'emplacement, sans gravité ni physique, avec son propre rendu (terminal, digicode, objet qui tourne, levier,
  socle, panneau). Le nom de l'action s'affiche quand elle est visée (`Minecraft.crosshairPickEntity`).
- Les **corps** et les **mannequins** : `EventNpcEntity` sert de modèle direct (entité joueur locale avec un
  skin). Corps couché : `setPose(Pose.SLEEPING)`. Skin : `HubAvatars.skinOf(uuid)` de Phantasmon.
- Toutes ces entités sont retirées à la sortie du hub, au changement de dimension et à la déconnexion (comme
  `EventNpcs` le fait).

**Piège.** Ne jamais s'appuyer sur un vrai bloc cliquable du serveur (coffre, levier, bouton) : le serveur
réagirait aussi, et on ne pourrait pas le remettre à zéro.

## S4. Couper ou baisser une voix chez celui qui écoute

**Réponse aux deux premières questions de T5.1 : oui, c'est possible.**

**Sur quoi cela repose.** L'API de Simple Voice Chat a un événement côté client, `ClientReceiveSoundEvent`
(`api/src/main/java/de/maxhenkel/voicechat/api/events/`), émis **avant** qu'un son soit joué :

- `getId()` : l'identifiant de celui qui parle ;
- `getRawAudio()` / `setRawAudio(short[])` : la trame, en PCM 16 bits, qu'on peut remplacer ;
- trois variantes : `EntitySound` (proximité, avec `isWhispering()` et `getDistance()`), `LocationalSound`,
  `StaticSound` (groupes).

L'événement n'est pas annulable, mais ce qu'on remet dans `setRawAudio` est ce qui est joué
(`ClientPluginManager.onReceiveEntityClientSound`, appelé dans `AudioChannel`). Il existe depuis mars 2022 : la
version installée dans le modpack (2.5.35 pour 1.21.1) l'a, et Phantasmon compile déjà contre l'API 2.5.0.

**À coder.**

- Events déclare **son propre greffon** Simple Voice Chat : dans `fabric.mod.json` d'Events, le point d'entrée
  `"voicechat": ["com.mystaria.events.trahison.voice.TrahisonVoicePlugin"]`, et dans `build.gradle` d'Events
  `compileOnly "de.maxhenkel.voicechat:voicechat-api:2.5.0"` avec le dépôt Maven
  `https://maven.maxhenkel.de/repository/public` (copier le bloc de `Phantasmon-Client/build.gradle`).
  La classe du greffon n'est chargée que par Simple Voice Chat : Events reste utilisable sans lui. Ne référencer
  aucune classe `de.maxhenkel` ailleurs.
- Dans `registerEvents`, s'abonner aux **trois** variantes (la diffusion se fait par classe exacte).
- Pour chaque trame : chercher le **gain** de celui qui parle (0 à 1) et multiplier les échantillons. Gain 0 :
  remplacer par un tableau de zéros **de la même longueur** (ne pas mettre `null`, ne pas toucher une trame vide,
  qui signale la fin de la parole).
- **Fil d'exécution :** l'événement arrive sur le fil audio, pas sur le fil du jeu. Ne lire ni le monde ni la vue
  de la partie depuis là. Le fil du jeu recalcule à chaque tick une table `identifiant → gain`
  (`ConcurrentHashMap` ou référence `volatile` vers une table immuable), et le greffon ne fait que la lire.
- Le calcul du gain, sur le fil du jeu, pour chaque joueur de la partie :
  - 0 si celui qui parle est fantôme ou spectateur et que j'écoute en vivant ;
  - 0 s'il n'est pas dans ma zone sonore ;
  - sinon selon la distance : 1 jusqu'à 4 blocs, puis décroissant jusqu'à 0 à 8 blocs (3 blocs s'il chuchote) ;
  - 1 pour tout le monde si celui qui parle est le maître du jeu en mode annonce ;
  - hors partie, ou joueur inconnu : 1 (rien n'est filtré).

**À confirmer en jeu.** Le silence est-il net (pas de grésillement) ? L'icône « parle » de Simple Voice Chat
disparaît-elle bien pour une voix coupée (elle suit le niveau sonore de la trame) ?

## S5. La voix venant des copies du hub

**Constat.** Le vocal du hub de Phantasmon ne joue une voix que pour un joueur dont on voit l'**avatar** (un joueur
présent en vrai sur le même serveur passe déjà par la proximité : pas de doublon). Il la joue par un canal de
Simple Voice Chat créé sur l'entité de l'avatar (`PhantasmonVoicechatPlugin.play` :
`createEntityAudioChannel(source.getUUID())`).

**Conséquence.** Ces trames passent par le **même** `ClientReceiveSoundEvent.EntitySound` que la proximité (le
canal fabrique un `PlayerSoundPacket`, traité par `AudioChannel` comme les autres). Le greffon d'Events les voit
donc, sans rien changer à Phantasmon.

**Le détail à connaître.** Pour ces trames, `getId()` n'est pas l'identifiant du joueur mais celui de l'entité
avatar. Il vaut `UUID.nameUUIDFromBytes(("phantasmon-hub-avatar:" + uuidDuJoueur).getBytes(UTF_8))`
(`HubAvatarEntity.entityUuid`). La table des gains doit donc contenir **les deux clés** pour chaque joueur : son
identifiant, et l'identifiant de son avatar (le lire sur `HubAvatars.shownAvatar(uuid).getUUID()` quand l'avatar
existe, plutôt que de recopier la formule).

**Ce que cela donne.**

- Un spectateur dans une copie entend les joueurs : le relais du hub lui envoie déjà leurs voix. Rien à faire.
- Les joueurs n'entendent pas les spectateurs : gain 0 chez les vivants.
- Le maître du jeu en mode annonce : son client envoie `EventsTrahisonGm { action: "announce", on }` ; le backend
  le met dans la vue ; les clients passent son gain à 1.

**Charge (troisième question de T5.1).** Les 9 joueurs sont sur le même serveur et se parlent par la proximité de
Simple Voice Chat. Le vocal du hub ne porte que les voix entre le serveur et les copies : les joueurs vers les
spectateurs, et le maître du jeu. La charge à 9 est donc à mesurer en jeu, mais elle ne pèse pas sur les échanges
entre joueurs.

## S6. Masquer un mort, skin en noir et blanc

Le mort reste un vrai joueur connecté ; c'est l'affichage chez les autres qui change.

- **Invisible** (réglage « les vivants ne voient pas les fantômes ») : mixin sur
  `EntityRenderDispatcher.shouldRender`, qui répond faux pour les identifiants des fantômes quand la vue locale
  est celle d'un vivant. Le corps, le pseudo et l'ombre disparaissent d'un coup. Les bruits de pas restent : accepté.
- **Visible en noir et blanc** : mixin sur `AbstractClientPlayer.getSkin` (au retour), qui remplace la texture par
  une copie en niveaux de gris. Fabriquer la copie une fois par joueur, sur le fil de rendu : lire la texture du
  skin (`TextureManager.getTexture`, puis `NativeImage.downloadTexture`), convertir, enregistrer une
  `DynamicTexture` sous un identifiant propre. Même traitement pour l'avatar d'un fantôme vu depuis une copie.
- Modèle de mixin déjà présent dans Events : `mixin/EntityGlowOpponentMixin.java`. Déclarer les nouveaux dans
  `phantasmon_events.mixins.json`.

## S7. Attacher un joueur

- Mixin sur `KeyboardInput.tick`, à la fin : si le joueur local est attaché, remettre à zéro
  `forwardImpulse`, `leftImpulse`, `jumping` et `shiftKeyDown`. Il peut encore tourner la tête et parler.
- Empêcher aussi, pendant ce temps, l'usage d'objets et l'ouverture des écrans du jeu (les actions sont de toute
  façon refusées par le backend).
- C'est le mod de l'attaché qui le retient : cela suppose un joueur de bonne foi, comme tout le reste.

## S8. Le Pokémon partenaire

- Ne pas passer par les Ghosts du joueur (ce sont ses vrais Pokémon Phantasmon, sa propriété).
- Créer une entité Pokémon **locale**, de la même façon que Phantasmon affiche un Ghost :
  `new PokemonEntity(clientLevel, pokemon, CobblemonEntities.POKEMON)`
  (`Phantasmon-Client/.../ghost/GhostEntityManager.java`, ligne 225 et autour : création, identifiant, ajout au
  monde, suivi du joueur). Lire ce fichier comme modèle, ne pas l'appeler.
- Chaque client crée le partenaire de chaque membre de son groupe (l'espèce et le pouvoir viennent de la vue de la
  salle) et le fait suivre le joueur ou son avatar (`HubAvatars.playerOrAvatar`).

## S9. Le combat-énigme : un mini-jeu à nous

**Décision technique.** Le combat-énigme n'utilise **pas** un vrai combat Pokémon. C'est un mini-jeu de duel
tactique, dans le cadre des écrans de mini-jeu (T3.4), simulé par le backend comme tous les mécanismes.

**Pourquoi.**

- Un vrai combat passe par Phantasmon, qui laisse le joueur choisir son équipe : on ne peut pas l'imposer (voir S10).
- Un problème « trouve la suite de coups gagnante » doit être déterministe. Le vrai moteur a du hasard partout.
- Simulé par nous, il se **génère** et se **vérifie** : le backend cherche toutes les suites de coups et ne garde
  qu'une situation à solution unique. On y gagne la rejouabilité et un équilibre exact.

**Règles du duel (volontairement petites).**

- 1 à 3 Pokémon par camp, chacun avec des PV, une Vitesse, un ou deux types et 2 à 4 attaques.
- Une attaque : un type, une puissance fixe, parfois un effet simple (priorité, soin, échange forcé, protection
  d'un tour, baisse de Vitesse).
- Dégâts = puissance × efficacité du type (×2, ×1, ×0,5, ×0), sans hasard. Le plus rapide frappe d'abord.
- À chaque tour, le joueur choisit une attaque ou un changement. L'adversaire suit une règle fixe et **affichée**
  (« il attaque toujours le plus efficacement possible »), pour que tout se déduise.
- But : mettre K.O. l'équipe adverse en N tours au plus. Un échec remet la position de départ.

**À coder.** Mécanisme `battle_puzzle` : `generate` tire une position et la valide par recherche exhaustive
(l'arbre est minuscule) ; `onInput` joue un tour ; l'écran affiche les Pokémon avec les modèles de Cobblemon
(l'afficheur de modèles de ses écrans, à repérer dans `cobblemon/.../client/gui/`), les PV, les attaques et la
règle de l'adversaire. La table des types vient du catalogue de T8.1.

## S10. Le combat réel à équipe imposée : bloqué

**Constat, après lecture de `LiveBattleService` et de l'API client.**

- Un combat contre un PNJ passe par `NpcOpponentProvider` : l'add-on fournit l'équipe **du PNJ**. L'équipe du
  joueur, elle, est choisie par le joueur dans le lobby (son équipe Ghost ou son équipe Cobblemon). Aucun point
  d'entrée ne permet de la fixer.
- `PhantasmonApi.startNpcBattle(npc, cobblemonTeam)` ne prend pas d'équipe. `PhantasmonApi.send` n'accepte que
  les messages `Events*` : Events ne peut pas envoyer lui-même le message de début de combat.
- Le backend ne prévient pas l'add-on de la fin d'un combat contre un PNJ (seuls les tournois ont un retour,
  `TournamentHook.onGameEnded`).

**Conclusion.** Le « gardien » (un vrai combat, équipe imposée, résultat connu du jeu) ne peut pas se faire sans
toucher à Phantasmon. Il sort de la première version. Le combat-énigme (S9) le remplace.

**Si on le veut plus tard, deux pistes, à décider par la propriétaire :**

| Piste | Ce qu'il faudrait | Coût |
|---|---|---|
| A. Sans toucher à Phantasmon | Un combat contre un PNJ d'Events avec l'équipe du joueur (non imposée), et une IA d'Events (`PhantasmonApi.registerBattleAi`) qui observe le combat pour en déduire le vainqueur et le dire au backend | Équipe non imposée, donc épreuve inégale entre groupes ; détection de la victoire à prouver en jeu |
| B. Deux petits ajouts à Phantasmon | Dans `NpcOpponent`, une équipe facultative pour le joueur ; dans `NpcOpponentProvider`, un retour de fin de combat | Propre et court, mais c'est une modification de Phantasmon |

## S11. Écrire `.schem` et `.litematic`

Le lecteur existe (`Phantasmon-Client/.../hub/HubSchematic.java`) : il sert de référence et de vérification
(relire ce qu'on a écrit et comparer bloc à bloc). L'écriture se fait côté client, avec `NbtIo.writeCompressed`.

**Sponge version 2 (`.schem`)** : un NBT compressé, racine nommée `Schematic`.

| Champ | Type | Valeur |
|---|---|---|
| `Version` | int | 2 |
| `DataVersion` | int | `SharedConstants.getCurrentVersion().getDataVersion().getVersion()` |
| `Width`, `Height`, `Length` | short | tailles en x, y, z |
| `Palette` | compound | texte de l'état de bloc (`minecraft:oak_stairs[facing=north,half=bottom,...]`) → indice (int) |
| `PaletteMax` | int | nombre d'entrées |
| `BlockData` | byte array | les indices en varint, dans l'ordre `x + z × Width + y × Width × Length` |
| `BlockEntities` | list | pour chacune : `Pos` (int array x, y, z relatifs), `Id`, puis ses données |

Le texte d'un état : `BlockStateParser.serialize(state)`.

**Litematica (`.litematic`)** : un NBT compressé.

| Champ | Valeur |
|---|---|
| `Version` | 6 |
| `MinecraftDataVersion` | comme ci-dessus |
| `Metadata` | `Name`, `Author`, `EnclosingSize` (x, y, z), `RegionCount` 1, `TotalBlocks`, `TotalVolume`, `TimeCreated`, `TimeModified` |
| `Regions.<nom>` | `Position` (0, 0, 0), `Size` (x, y, z), `BlockStatePalette`, `BlockStates`, `TileEntities`, `Entities` (vide), `PendingBlockTicks` (vide), `PendingFluidTicks` (vide) |

- `BlockStatePalette` : liste d'états en NBT (`NbtUtils.writeBlockState`), l'air en premier.
- `BlockStates` : tableau de `long`, indices sur `bits = max(2, nombre de bits de (taille de la palette − 1))`,
  serrés à la suite **en traversant les frontières de `long`**, dans l'ordre `x + z × sizeX + y × sizeX × sizeZ`.
- `TileEntities` : les données de chaque entité de bloc, avec `x`, `y`, `z` relatifs.

**Rotation** par quarts de tour : `state.rotate(Rotation)` pour chaque état,
`StructureTemplate.transform(pos, Mirror.NONE, rotation, pivot)` pour chaque position (blocs, entités de bloc, et
emplacements de la salle). Les tailles x et z s'échangent pour un quart de tour.

**À confirmer en jeu.** Qu'Axiom colle bien les deux fichiers sans décalage (faire l'essai avec l'admin sur une
seule salle avant la première soirée).

## S12. Capturer une salle avec ses entités de bloc

- L'éditeur de salle s'utilise **en solo**. Là, le monde complet est celui du serveur intégré :
  `Minecraft.getInstance().getSingleplayerServer().getLevel(dimension)`, à lire sur le fil du serveur
  (`server.execute(...)`), puis à renvoyer au fil du client.
- Blocs : `level.getBlockState(pos)`. Entités de bloc (panneaux, bannières, têtes) :
  `blockEntity.saveWithFullMetadata(level.registryAccess())`.
- Sur un serveur multijoueur, le client n'a pas le contenu complet des entités de bloc : ne pas y capturer de salle.

## S13. Garder les fichiers de salle sur le backend

- Events garde déjà les terrains des hubs miroirs dans le dossier des schématiques de hub
  (`@Value("${phantasmon.hub.schematics-dir:hub_schematics}")`, voir `MirrorService` : écriture dans un fichier
  temporaire puis déplacement).
- Même dossier, sous-dossier `trahison_rooms/<id>/` : `room.bin` (les blocs, format libre ; reprendre celui de
  `mirror.bin` si pratique) et la définition en base (`trahison_rooms.data`).
- Routes sous `/events/trahison/...`, protégées par le jeton du joueur comme les autres routes d'Events ; écriture
  réservée aux admins (`AdminService.requireAdmin`).

## S14. Afficher du texte et des tableaux dans le monde

- Rendu libre dans le monde : `WorldRenderEvents` de Fabric (par exemple `AFTER_TRANSLUCENT`), avec la matrice de
  la caméra. Modèle : `Phantasmon-Client/.../hub/HubDebugBorders.java` (boîtes) et `HubController.renderWorld`.
- Texte : `Font.drawInBatch` sur un plan orienté (face à un mur pour un panneau d'indice ; face au joueur pour un
  nom d'action).
- Le tableau des résultats du hall est un grand panneau fixe du plan (un élément du plan : position, orientation,
  taille), dessiné de cette façon.
- Règle de Showtime à respecter aussi ici : ne jamais garder un tampon de sommets pendant un appel qui en demande
  un autre (dessiner un bloc ou un objet), sinon plantage sans Iris.
