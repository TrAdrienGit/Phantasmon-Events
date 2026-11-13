# T7 : les rôles, les objets, le maître du jeu

## Objectif

Tout ce qui casse la confiance : les rôles secrets, les tentations automatiques, les objets cachés (dont le
couteau et les entraves), et le vrai panneau du maître du jeu.

**Fini quand :** une partie avec rôles et objets se déroule seule de bout en bout (mode automatique), et la même
se déroule avec un maître du jeu qui chuchote, donne un indice, met en pause et corrige des points.

## À lire avant

- [`plan.md`](../plan.md), section 3 (« Le couteau et les entraves ») et section 4 en entier.
- [`architecture.md`](../architecture.md), section 4 (« Qui voit quoi »).
- Le code de T1 (`Views`, `Settings`), T3.3 (`Inventory`), T6 (`kill`, corps).

## Questions à poser avant de commencer

- Les durées maximales des liens et du verrou (le plan propose 10 et 5 minutes).
- La liste des rôles actifs par défaut, et combien par partie (le plan propose 2 ou 3).

## Sous-tâches

### T7.1 Les rôles secrets (Opus)

Backend, paquet `game`.

- `Role` : `INFILTRATOR`, `FERRYMAN`, `GUARDIAN`, `SEER`, `GAMBLER`, `LONER` (plan, section 4). Chaque rôle a une
  contrainte, un pouvoir ou une condition de victoire.
- Tirage au début de la partie, d'après `settings` (rôles actifs, nombre). Chaque joueur reçoit le sien en privé
  pendant le prologue, avec son texte.
- Effets :
  - Infiltré : s'il trahit à la manche 1, +1 point aux résultats de cette manche.
  - Gardien : un vote « Trahir » de sa part est refusé.
  - Voyant : action `foresee` (une fois), entre la fermeture d'une cabine et les résultats : il reçoit le vote
    de cette cabine.
  - Parieur : avant chaque vote, action `bet` sur le vote de son adversaire ; un pari juste rapporte +1, une
    seule fois dans la partie.
  - Passeur et Solitaire : conditions de victoire, évaluées à la fin.
- `Outcome` (T1.1) s'étend : en plus de « qui est sorti », la liste de ceux qui ont rempli leur objectif.
- La fin de partie révèle tous les rôles.
- Tests : chaque rôle ; aucun rôle n'apparaît dans la vue d'un autre joueur avant `ENDED`.
- **Fini quand :** les tests passent, et le prologue annonce son rôle à chacun en jeu.

### T7.2 Les objets cachés (Opus)

- `Item` : `KNIFE`, `BONDS`, `LOCK`, `EAR`, `SHIELD`, `PASS`, `LETTER`. Quantités dans `settings` (0 à 2 chacun).
- Au début de la partie, le backend répartit les objets dans les **cachettes** du plan (T2.1) et les emplacements
  `secret` des salles. Une cachette garnie se fouille comme un emplacement (T3.3) ; l'objet va dans l'inventaire
  personnel, que les autres ne voient pas.
- Effets (action `use_item`, cible et lieu vérifiés par le backend) :

| Objet | Condition | Effet |
|---|---|---|
| Couteau | Cible vivante à moins de 3 blocs, même zone | `kill` (T6). L'objet est consommé |
| Liens | Cible à moins de 3 blocs, même zone | La cible est attachée sur place, jusqu'à ce qu'un autre joueur la détache (action `untie`, 5 secondes à côté d'elle) ou jusqu'à la durée maximale |
| Verrou | Sur une porte ordinaire ou une cabine, de l'extérieur | Fermée ; se rouvre de l'extérieur (action `unlock`) ou après la durée maximale |
| Écoute | Pendant un vote, sur une cabine | Voir T5.4 |
| Bouclier | Automatique | Annule le prochain −2 subi par son porteur, puis disparaît |
| Passe | Sur une cabine ou une porte verrouillée | La rouvre |
| Lettre | N'importe quand | Un message court, anonyme, livré à un joueur par l'animateur |

- Client : un joueur attaché ne peut plus se déplacer (ses touches de mouvement sont ignorées par son mod), il
  peut regarder et parler. Affichage chez les autres : des liens autour de lui.
- Un joueur attaché ou enfermé ne vote pas : « Allier » par défaut (déjà la règle).
- Tests : chaque objet ; le couteau ne marche pas à travers une porte fermée (zones différentes).
- **Fini quand :** en jeu, on trouve un objet, on attache un joueur, un autre le détache, et le couteau tue.

### T7.3 Les tentations automatiques (Sonnet)

- **L'offre en cabine :** à chaque vote, un joueur tiré au sort reçoit, une fois sa cabine fermée, une offre
  privée (« Trahis : +1 point de plus »). L'animateur annonce à tous qu'une offre a été faite. Réglable et
  désactivable.
- **Les fuites :** après les résultats, un joueur tiré au sort reçoit une information vraie tirée du journal de la
  partie (temps mis à voter, tentative d'entrer dans une cabine, objet ramassé près de lui). Les textes sont des
  modèles dans les fichiers de langue.
- **La course :** le premier groupe sorti de sa salle est annoncé.
- Tout est tiré avec la graine de la partie et écrit dans le journal.
- **Fini quand :** les trois tentations se voient dans une partie d'essai.

### T7.4 Le panneau du maître du jeu (Fable)

Remplace l'écran provisoire de T1. C'est l'outil de travail d'une soirée : il doit être clair sous la pression.
Il s'ouvre comme une page du menu de Phantasmon réservée aux admins (`PhantasmonApi.registerMenuPage`, comme la
page Tournois), et par une touche.

- Une vue d'ensemble : l'étape et le chrono, les 9 joueurs (points, couleur, zone, vivant, attaché, connecté,
  rôle, objets), les 3 salles (étape en cours, temps, aides données), les votes à mesure qu'ils tombent.
- Les gestes : pause et reprise, prolonger ou écourter, passer à la suite, corriger des points, donner une aide à
  une salle, résoudre une étape bloquée, chuchoter à un joueur, parler à tous comme l'animateur, déclencher une
  offre, ouvrir ou fermer une porte, ressusciter ou tuer (pour réparer une erreur), terminer.
- Les réglages de la partie avant le démarrage : durées, rôles, objets, mort annoncée ou non, mode automatique.
- Le journal de la partie, filtrable.
- Utilisable depuis une copie du hub (le maître du jeu n'est pas forcément sur le serveur).
- **Fini quand :** le « Fini quand » de l'étape.

## Hors périmètre

Le défi à 1 point entre joueurs (écarté de la première version).

## Pièges

- Le Gardien ne doit pas être démasqué par un message d'erreur visible des autres : le refus de son vote est privé.
- Une offre faite à une paire va à celui qui est dans la cabine fermée.
- Les objets donnent du pouvoir à ceux qui explorent seuls : garder les quantités basses par défaut (1 de chaque).
- Toute action du maître du jeu est écrite dans le journal.
