# Textes de l'animatrice (T9.4)

Tout ce que dit l'animatrice (Mystaria, ou le nom qui sera choisi) vit dans **deux fichiers**, faciles à changer d'une soirée
à l'autre, sans toucher au code :

- `src/main/resources/assets/phantasmon_events/trahison/animateur/fr_fr.json`
- `src/main/resources/assets/phantasmon_events/trahison/animateur/en_us.json`

Après une modification : reconstruire le mod (`./gradlew build -x test`) et le déployer sur les clients. Rien à changer côté
serveur.

## Les moments et les variantes

Le bloc `lines` associe chaque **moment** à une **liste de variantes**. Une seule est dite, tirée par le backend (graine de
la partie, moment et manche) : tous les joueurs entendent la même, et une partie rejouée dit les mêmes choses.

```json
"phase.vote": [
  "Mystaria : Aux cabines. S'allier ou trahir...",
  "Mystaria : Les cabines vous attendent. (variante 2)"
]
```

Pour **ajouter une variante** : ajouter une ligne à la liste, dans les **deux** langues. Pour en retirer une : la supprimer
(il en faut au moins une par moment, le test client le vérifie). Le texte avant le premier deux-points est le nom de
l'animatrice, affiché dans la couleur d'accent. `%1$s`, `%2$s` sont les arguments du moment (voir la table). Un `%` seul
dans un texte s'écrit `%%`.

| Moment | Quand | Arguments |
|---|---|---|
| `phase.lobby`, `phase.prologue`, `phase.doors`, `phase.rooms`, `phase.free_time`, `phase.vote`, `phase.results`, `phase.final`, `phase.ended` | début de chaque étape | aucun |
| `doors_dead` | entrée en salles, des joueurs sont morts faute de porte | 1 : les noms |
| `death` | résultats, des joueurs sont morts aux points | 1 : les noms |
| `betrayal` / `alliance` | résultats : un vote « Trahir » a été rendu / aucun | aucun |
| `final_door_open` | la porte finale s'ouvre | 1 : qui l'a ouverte, 2 : secondes |
| `final_countdown` | décompte de la porte finale (dit par le client) | 1 : secondes |
| `game_master_killed` | le maître du jeu qui jouait est tué | 1 : son nom |
| `ending.solo`, `ending.some`, `ending.all`, `ending.nobody` | fin de partie, selon le nombre de sortis | 1 : les noms, 2 : combien |
| `role.<rôle>` | rôle secret, au prologue (privé) | aucun |
| `letter`, `offer`, `offer_made`, `leak.*`, `race_first`, `gm`, `gm_whisper` | lettres, offres, rumeurs, annonces | voir la clé de langue |

Les clés de langue `phantasmon_events.trahison.say.*` existent toujours : elles servent de **secours** quand un moment manque
dans le fichier. Les textes d'origine y sont la variante 1. Le test `AnnouncerTextsTest` exige que chaque clé `say.*` des
fichiers de langue ait au moins une variante dans les deux fichiers d'animatrice : **un nouveau moment s'ajoute dans les trois
endroits** (clé de langue fr et en, puis les deux fichiers d'animatrice), et dans le code du backend qui l'envoie.

## Les quatre fins

Le backend décide (`rules/Outcome.ending`) : `nobody` (personne), `solo` (un seul), `some` (plusieurs, pas tous), `all` (tous
les sièges). La fin est dans la vue (`ending`), avec le récapitulatif des votes (`recap`). À l'écran de fin : le bandeau de la
fin (couleur propre), qui est sorti, les rôles révélés, puis les votes manche par manche. À l'oreille : la réplique
`ending.<fin>`.

## Les fragments de lore

Le bloc `fragments` associe un **numéro** à un texte (plusieurs lignes avec `\n`). Dans un document de salle ou de cachette
(texte d'une page, `document` d'un `pickup_set`), une ligne `@fragment:3` est remplacée par le fragment 3. Un numéro absent
du fichier s'affiche « Fragment n — à écrire. ». Les douze premiers sont des textes provisoires à remplacer. Le numéro est
libre (1 à 9999) : l'histoire se complète de partie en partie en gardant les mêmes numéros pour le même fragment.
