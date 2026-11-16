# Charte graphique et UI du jeu de trahison

> À lire avant de coder le moindre écran, HUD ou panneau du jeu. Les constantes vivent dans
> `src/main/java/com/mystaria/events/trahison/ui/` (`TrahisonPalette`, `TrahisonUi`) ; ce document et ces classes
> changent ensemble.

## 1. Direction artistique

Un jeu de société hanté, vu depuis l'intérieur d'un miroir. Les surfaces sont d'un bleu encre très sombre, presque
noires, taillées en relief comme le menu de Showtime (contours noirs d'un pixel, arêtes éclairées en haut à gauche,
ombrées en bas à droite, grille de points discrète). Un seul accent froid, le turquoise spectral de l'animatrice,
signale ce qui est interactif ou important. Les trois couleurs de jeu et les deux votes sont les seules touches
vives : elles doivent claquer sur ce fond calme. Rien ne clignote sans raison ; la tension vient du rythme (chrono qui
se resserre, texte qui s'écrit lettre à lettre), pas du bruit visuel.

Qualité visée : le menu de Cobblemon Battle Showtime (`ShowtimeMenuScreen`) et `MenuUi` de Phantasmon. Les écrans du
jeu ont leur propre palette (fixe, indépendante du thème choisi dans Phantasmon) mais **les mêmes formes**.

## 2. Ce qu'on réutilise

| Besoin | Classe | Remarque |
|---|---|---|
| Relief, contours, dégradé horizontal, points | `MenuUi.raised`, `sunken`, `outline`, `gradientRight`, `dots` | Publics et statiques, sans palette (sauf `dots`, remplacé par `TrahisonUi.dots`) |
| Texte (police `uniform` de Cobblemon, ombre) | `MenuUi.text`, `centered`, `paragraph`, `paragraphHeight`, `width` | Le texte trop long rétrécit ; pour couper avec « ... », `TrahisonUi.textEllipsis` |
| Hit-test, son de clic | `MenuUi.inside`, `MenuUi.click` | Clic : le son du PC de Cobblemon |
| Zones cliquables, champs de texte, défilement | `com.mystaria.events.tournament.ui.Ui` | Mode immédiat : on dessine et on déclare la zone dans la même passe ; recopier son principe pour les écrans du jeu, ou l'utiliser tel quel pour les boutons |
| Fond d'écran plein, toile 1600 x 900 à échelle fixe | `PhantasmonCanvasScreen` | Classe publique abstraite, membres `protected` : un add-on peut en hériter. À réserver aux écrans de mini-jeu qui ont besoin de modèles 3D, d'images ou d'une grille très précise |
| Fenêtre à taille fixe en pixels GUI | `ShowtimeMenuScreen` (modèle à imiter, pas une classe à appeler) | Son découpage (en-tête 24, marges, voile autour) est la référence |

Ce que `MenuUi` ne sait pas faire pour nous : ses formes lisent `MenuUi.p()` (gris Cobblemon ou cyan sur marine selon
le thème). `TrahisonUi` redessine donc les formes avec `TrahisonPalette`, en gardant les mêmes proportions. Ne jamais
mélanger `MenuUi.button` et `TrahisonUi.button` dans le même écran.

### Deux modes d'écran

- **Fenêtre** (par défaut) : tout en pixels GUI entiers, à la manière de Showtime. Un rectangle centré par
  `TrahisonUi.stage(...)`, jamais plus grand que l'écran moins `SAFE_MARGIN`. Panneau MJ, éditeurs, vote, tableau de
  résultats, modales.
- **Toile** : `PhantasmonCanvasScreen` (1600 x 900, échelle `min(1, w/800, h/450) / 2 * 0,8`), pour un mini-jeu plein
  écran. Dans la toile, le texte reste à l'échelle 1 ou 2 (entiers seulement, sinon la police devient floue) et
  les bordures utilisent `line()` de la classe parente. Les couleurs viennent quand même de `TrahisonPalette`.

## 3. Palette

Toutes les valeurs sont en ARGB (`0xAARRGGBB`). Nom du jeton = nom de la constante dans `TrahisonPalette`.

### Fonds, panneaux, bordures

| Jeton | Valeur | Usage |
|---|---|---|
| `BG_DEEP` | `0xFF0E1018` | Fond le plus profond, infobulles |
| `BG` | `0xFF161A26` | Fond d'écran plein |
| `PANEL` | `0xFF1F2433` | Surface d'un panneau |
| `PANEL_RAISED` | `0xFF2A3045` | Bas des boutons, ligne survolée, barre de titre |
| `PANEL_SUNKEN` | `0xFF121520` | Puits : listes, pistes de barres, champs |
| `DOTS` | `0xFF222840` | Grille de points du fond |
| `VEIL` | `0xB0080A10` | Voile derrière une modale |
| `BORDER` | `0xFF0A0C12` | Contour noir d'un pixel de toute forme |
| `EDGE` | `0xFF4A5372` | Bord de bouton, filets, fond de barre de titre |
| `EDGE_LIGHT` | `0xFF7A85AD` | Bouton survolé, arête vive |
| `SHINE` / `SHADE` | `0x30FFFFFF` / `0x50000000` | Relief (mêmes valeurs que `MenuUi`) |

### Texte

| Jeton | Valeur | Usage |
|---|---|---|
| `TEXT` | `0xFFF2F4FA` | Texte principal, titres |
| `TEXT_SECONDARY` | `0xFFC3C9DE` | Texte courant sur panneau |
| `TEXT_MUTED` | `0xFF8089A8` | Légendes, étiquettes de champs, indications |
| `TEXT_DISABLED` | `0xFF5A6280` | Contrôle inactif |

### Accent

| Jeton | Valeur | Usage |
|---|---|---|
| `ACCENT` | `0xFF5FE0D0` | Action principale, onglet actif, soulignement de titre |
| `ACCENT_DIM` | `0xFF2A8A80` | Bas des dégradés d'accent, ligne sélectionnée |
| `ACCENT_HOT` | `0xFFA5F5EA` | Survol d'un bouton principal |

### Les trois couleurs de jeu (noms neutres, habillées plus tard en Feu / Plante / Eau)

| Couleur | Base | Claire | Sombre | Symbole |
|---|---|---|---|---|
| `RED` | `0xFFE0483E` | `0xFFFF8A80` | `0xFF8E2620` | triangle (`Glyph.TRIANGLE`) |
| `GREEN` | `0xFF4CBF5A` | `0xFF8FE89A` | `0xFF21702C` | disque (`Glyph.CIRCLE`) |
| `BLUE` | `0xFF4A8CF0` | `0xFF8FBBFF` | `0xFF204E94` | carré (`Glyph.SQUARE`) |

Base : remplissages et glyphes. Claire : texte sur fond sombre, lueurs. Sombre : dégradés, fond de bracelet.
`TrahisonUi.GameColor` regroupe les trois tons et le symbole d'une couleur. Le futur habillage Feu / Plante / Eau ne
change que ces valeurs et ces symboles (flamme, feuille, goutte), jamais le code des écrans.

### Votes, états, teintes

| Jeton | Valeur | Usage |
|---|---|---|
| `ALLY` / `ALLY_DARK` | `0xFFFFD36B` / `0xFF8A6A1E` | Vote « s'allier » (or chaud), symbole `Glyph.ALLY` |
| `BETRAY` / `BETRAY_DARK` | `0xFFC04BD8` / `0xFF65237A` | Vote « trahir » (violet), symbole `Glyph.BETRAY` |
| `DANGER` / `DANGER_DARK` | `0xFFFF5C5C` / `0xFF8A2A2A` | Erreur, action destructrice, chrono critique |
| `SUCCESS` / `SUCCESS_DARK` | `0xFF5FD38D` / `0xFF24704A` | Réussite, porte ouverte |
| `GHOST` | `0xFF9FB0E8` | Fantômes : teinte bleu pâle, dessinée à ~50 % d'opacité (`withAlpha`) |
| `GAME_MASTER` / `GAME_MASTER_DARK` | `0xFFFFB347` / `0xFF8A5A14` | Tout ce que seul le maître du jeu voit |

Choix voulus : le violet du vote « trahir » n'est ni le rouge du jeu ni le rouge de danger ; l'or de « s'allier »
n'est pas le vert du jeu. Les deux votes ne se confondent donc avec aucune des trois couleurs.

## 4. Typographie

- Police : `uniform` de Cobblemon, via `MenuUi.text` (ombre portée, c'est le réglage de Showtime). Jamais la police
  vanilla dans un écran du jeu.
- Une seule taille de base : échelle 1, ligne de 10 pixels GUI (`TrahisonUi.LINE`), glyphes de 8 de haut. Une police
  plus petite devient floue et illisible : on distingue le texte secondaire par la couleur, pas par la taille.
- Échelle 2 (`TrahisonUi.bigText`) pour les chiffres seulement : points, chrono, code d'un digicode. Jamais d'échelle
  fractionnaire.
- Titres : gras (`withBold`), couleur `TEXT`, soulignement `ACCENT`.
- Texte trop long : `TrahisonUi.textEllipsis` (coupe avec « ... ») dans les listes et cartes ; `MenuUi.paragraph`
  (retour à la ligne) dans les zones de lecture. Ne jamais laisser `MenuUi.text` rétrécir un nom de joueur.
- Tous les textes passent par des clés de langue `phantasmon_events.trahison.*`, en `en_us.json` et `fr_fr.json`.

## 5. Grille d'espacement

Unité : 4 pixels GUI (`TrahisonUi.GRID`). Tout rembourrage, écart et taille est un multiple de 4, sauf les filets de
1 pixel et la ligne de texte (10).

| Élément | Valeur |
|---|---|
| Rembourrage intérieur d'un panneau | 8 |
| Écart entre deux panneaux ou deux blocs | 8 |
| Écart entre deux contrôles liés | 4 |
| Hauteur d'un bouton / d'un onglet | 18 (`BUTTON_H`) |
| Hauteur d'une ligne de liste | 18, pas de 20 |
| Barre de titre d'un panneau | 20 + 2 de filet ; contenu à `y + 24` |
| Pastille | 12 de haut |
| Marge de sécurité de l'écran | 6 (`SAFE_MARGIN`) |
| Taille minimale à prévoir pour un écran « fenêtre » | 320 x 180 pixels GUI (écran 1280 x 720 en échelle GUI 4) |

## 6. Composants

Tous les dessins sont dans `TrahisonUi`, en code, sans texture.

| Composant | Méthode | Description |
|---|---|---|
| Panneau | `panel`, `titledPanel`, `well` | Contour noir, fond `PANEL`, relief. `titledPanel` ajoute la barre de titre (dégradé `EDGE` vers `PANEL_RAISED`, losange et filet d'accent). `well` est creusé (`PANEL_SUNKEN`) |
| Bouton | `button(..., ButtonKind, ButtonState)` | Trois genres : `DEFAULT` (gris), `PRIMARY` (accent, texte sombre), `DANGER` (rouge). Quatre états : `NORMAL`, `HOVER` (dégradé éclairci), `PRESSED` (creusé, texte décalé d'un pixel), `DISABLED` (plat, texte `TEXT_DISABLED`, pas de survol). `stateOf` calcule l'état |
| Onglet | `tab` | Sélectionné : accent plein, texte sombre. Sinon gris, éclairci au survol |
| Ligne de liste | `row` | Creusée ; survol : `PANEL_RAISED` ; sélection : barre d'accent à gauche et dégradé |
| Pastille | `pill`, `glyphPill` | 12 de haut, coins coupés. `glyphPill` met un symbole avant le texte : c'est la forme à employer pour une couleur de jeu ou un vote |
| Barre de progression / chrono | `progressBar` | Piste creusée, remplissage en dégradé de la couleur donnée |
| Infobulle | `tooltip` | Fond `BG_DEEP`, filet `EDGE`, reste dans l'écran, z + 400 |
| Bracelet | `bracelet` | 64 x 22 : bande de la couleur à gauche, symbole, points en échelle 2, marque « paire » (deux points reliés) ou « solo » (un point). Fantôme : teinte `GHOST` |
| Carte de joueur | à composer | `titledPanel` ou `panel` (teinte de couleur de jeu en 4e paramètre), tête du joueur 16 x 16 en pixels entiers, nom (`textEllipsis`), `bracelet` ou pastilles de statut en dessous. Fantôme : tout sous `withAlpha(..., 0x80)` |
| Modale de confirmation | `modal` | Voile `VEIL`, panneau titré centré ; retourne le rectangle de contenu. Boutons en bas : action à gauche en `DEFAULT`, confirmation à droite (`PRIMARY`, ou `DANGER` si destructrice). Échap = annuler |
| Pied de formulaire, champ de texte | `MenuUi.field`, `Ui.field` | Les champs de Phantasmon lisent le thème : utiliser ceux de `tournament.ui.Ui` seulement dans les écrans de MJ et d'édition (hors ambiance du jeu), sinon écrire un champ avec `well` |

## 7. Animation

| Règle | Valeur |
|---|---|
| Apparition d'un panneau ou d'une modale | 160 ms, `easeOut`, glissement de 6 pixels + fondu |
| Survol, pression | Immédiat, pas de transition |
| Phrase de l'animatrice | Machine à écrire, 40 caractères par seconde (`TYPEWRITER_CPS`, `typewriter`) ; un clic ou la touche Espace affiche la phrase entière ; pause de 300 ms sur `.`, `!`, `?` ; la phrase reste 3 s après sa dernière lettre, puis 250 ms de fondu |
| Chrono | La barre se vide en continu. Orange (`GAME_MASTER`) sous 60 s, `DANGER` sous 15 s avec `pulse(800)` sur le contour. Aucune autre animation permanente |
| Points qui changent | Le chiffre du bracelet flashe en `ACCENT_HOT` 400 ms ; le delta (+1, -2) monte de 8 pixels en 600 ms |
| Vote validé | Le choix reste allumé, l'autre s'assombrit en 150 ms. Jamais d'indice sur le choix des autres |
| Fin de phase | Fondu de 250 ms, pas de balayage |

Aucun mouvement ne doit dépendre de la fréquence d'images : calculer à partir de l'horloge
(`System.currentTimeMillis`, `progress`, `pulse`), jamais d'un compteur de rendus.

## 8. Placement du HUD

- Le **bracelet + chrono + nom de phase** est en bas de l'écran, centré, **au-dessus de la barre d'objets**
  (au moins 32 pixels du bas, pour laisser barre d'objets, santé et faim intacts). Rien n'est dessiné au centre de
  l'écran : le **viseur** reste libre.
- Les **phrases de l'animatrice** sont en haut, centrées, largeur maximale 60 % de l'écran, sous les messages de
  barre de boss (24 pixels du haut, plus 12 par barre de boss visible). Fond `BG_DEEP` à 70 % d'opacité, texte
  `TEXT`, nom de l'animatrice en `ACCENT`.
- Marge de sécurité : 6 pixels GUI contre tout bord de l'écran, plus si le HUD vanilla l'exige.
- Rien de cliquable dans le HUD ; il ne prend jamais le curseur. Il se cache avec F1 et sous l'écran de débogage
  (`TrahisonUi.hudVisible`).
- Les écrans pleins cachent le HUD du jeu (sinon le bracelet est dessiné deux fois) et le remettent en fermant.
- Un écran de mini-jeu ne couvre jamais le chat : le voile est à 70 %, le texte du chat reste lisible.
- Le tableau des résultats géant du monde n'est pas de l'UI : c'est un objet du monde, avec cette palette en
  référence.

## 9. Accessibilité

- **Jamais la couleur seule.** Chaque couleur de jeu a un symbole : rouge = triangle, vert = disque, bleu = carré.
  Chaque vote a un symbole : s'allier = deux points reliés, trahir = croix. Partout où une couleur apparaît (bracelet,
  carte, pastille, voile d'un vote, porte), le symbole l'accompagne (`glyphPill`, `colorGlyph`) et le nom est en
  texte dans la langue du joueur.
- Statut paire / solo : forme du repère (deux points reliés / un point) **et** texte au survol.
- Fantôme : teinte + opacité réduite + mention « fantôme » dans la carte, jamais l'un sans l'autre.
- Contraste : le texte `TEXT` sur `PANEL` dépasse largement 7:1 ; `TEXT_MUTED` sur `PANEL` fait environ 4,5:1,
  suffisant pour des légendes mais pas pour une information essentielle. `TEXT_DISABLED` est réservé à ce qui est
  inactif.
- Pas de clignotement plus rapide qu'une période de 800 ms. Pas de flash plein écran.
- Chaque information donnée par un son (fin de phase, vote) a aussi un équivalent visuel.
- Tout est faisable à la souris et au clavier : Échap ferme, Entrée valide l'action principale, Tab passe d'un
  champ au suivant.

## 10. Textures de remplacement

Aucune. Tout est dessiné en code, y compris bracelet et symboles (rectangles), donc rien à fournir pour l'instant.
Si un artiste ajoute des textures plus tard, les mettre sous
`assets/phantasmon_events/textures/gui/trahison/` et les lister ici avec taille et usage :

| Fichier | Taille | Usage |
|---|---|---|
| (aucun) | | |

## 11. Construire un nouvel écran : liste de contrôle

1. Choisir le mode : **fenêtre** (pixels GUI, `TrahisonUi.stage`) ou **toile** (`PhantasmonCanvasScreen`, seulement
   si nécessaire).
2. Ne mettre dans l'écran aucune règle de jeu : il affiche la vue reçue du backend (`GameView`) et envoie des gestes.
   Un geste s'affiche tout de suite chez soi, puis se recale sur la réponse.
3. Couleurs : uniquement `TrahisonPalette`. Aucune valeur ARGB écrite en dur dans l'écran.
4. Dessins : `TrahisonUi` d'abord ; si une forme manque et sert à deux écrans, l'ajouter à `TrahisonUi` (et ici),
   pas dans l'écran.
5. Écarts et tailles en multiples de 4 ; marge de sécurité de 6 ; testé à 320 x 180 pixels GUI et à 854 x 480.
6. Texte : clés de langue dans `en_us.json` et `fr_fr.json`, police `uniform`, échelle 1 (2 pour les chiffres),
   `textEllipsis` pour les noms.
7. Chaque couleur ou vote affiché a son symbole (`glyphPill`).
8. États de chaque contrôle : normal, survol, pressé, inactif. Une zone cliquable est déclarée dans la même passe que
   son dessin (voir `tournament.ui.Ui`). Son de clic : `MenuUi.click()`.
9. Échap ferme ; fermer ne perd rien (l'état est au backend). Un écran plein ne met pas le jeu en pause
   (`isPauseScreen` faux).
10. Animations : durées de la section 7, calculées sur l'horloge.
11. Fantômes et maître du jeu : prévoir les deux rendus (`GHOST`, `GAME_MASTER`) ; ne jamais montrer à un joueur ce
    que seul le MJ voit.
12. Mettre à jour cette charte si un jeton, un composant ou une règle change.
