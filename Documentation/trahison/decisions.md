# Décisions à trancher

> Un point par section. Coche une case (`[x]`) ou écris ta réponse dans le champ « Ta réponse ». Le choix
> appliqué en attendant est marqué *(par défaut)*. Je relis ce fichier à chaque reprise ; une fois un point
> appliqué, je le déplace dans « Tranché ».

## Tranché

Voir [`decisions-tranchees.md`](decisions-tranchees.md).

## D140. Nom de l'action des objets secrets
Choix appliqué : l'action des objets cachés s'appelle `use_object` (la fiche T7 disait `use_item`, mais `use_item` existe déjà : c'est l'action d'inventaire des salles, qui pose un objet de salle sur un emplacement).
- [x] `use_object` pour les objets secrets, `use_item` reste celle des salles *(par défaut)*
- [ ] Renommer l'action des salles et garder `use_item` pour les objets secrets

Ta réponse :

## D141. Où l'on cache les objets
Choix appliqué : les objets sont répartis dans les cachettes du plan seulement (une par cachette, au tirage de la partie). Les emplacements `secret` des salles ne sont pas encore garnis : un objet sans cachette libre est mis de côté (`unplaced_items`, vu du maître du jeu) et n'apparaît pas.
- [x] Cachettes du plan seulement ; les objets en trop sont mis de côté *(par défaut)*
- [ ] Garnir aussi les emplacements `secret` des salles au début de chaque manche
- [ ] Plusieurs objets possibles par cachette

Ta réponse :

## D142. Nombre de rôles tirés
Choix appliqué : 3 rôles par partie par défaut (le plan dit « 2 ou 3 »), tous différents, jamais pour un factice ni pour le maître du jeu qui joue (son rôle secret est déjà « maître du jeu », D12). Réglages : `roles`, `role_count` (0 à 6).
- [x] 3 par défaut, réglable *(par défaut)*
- [ ] 2 ou 3 au sort selon la graine
- [ ] 2 par défaut

Ta réponse :

## D143. L'Infiltré « trahit » : qui compte
Choix appliqué : le point de l'Infiltré est donné s'il a lui-même rendu le vote « Trahir » à la manche 1 (dans une paire, seul celui qui a voté compte ; si son partenaire trahit à sa place, il n'a rien).
- [x] Il doit avoir voté lui-même *(par défaut)*
- [ ] Le vote de sa paire suffit, même rendu par son partenaire

Ta réponse :

## D144. Le Voyant : ce qu'il voit
Choix appliqué : le Voyant désigne un camp (groupe 0 à 2, paire ou solo) qui n'est pas le sien et qui a déjà voté pendant `VOTE` ; il reçoit ce vote. Refusé sans être dépensé si le camp n'a pas encore voté.
- [x] Un camp qui a déjà voté, pas le sien *(par défaut)*
- [ ] Une cabine précise (il faut alors qu'elle soit fermée)
- [ ] Il peut aussi voir son propre camp

Ta réponse :

## D145. Passe et déverrouillage
Choix appliqué : la Passe rouvre une porte ou une cabine verrouillée de n'importe où, même de l'intérieur (c'est ce qui libère un enfermé) et se consomme ; le déverrouillage sans objet (`unlock`) demande d'être dehors, à 4 blocs de la porte. La Passe n'ouvre pas une cabine que son groupe tient fermée pendant un vote (D4, D5).
- [x] Comme décrit *(par défaut)*
- [ ] La Passe ouvre aussi une cabine tenue par son groupe

Ta réponse :

## D146. Détacher : cinq secondes
Choix appliqué : le backend ne voit pas le temps passé à côté de l'attaché, donc `untie` se fait en deux appels : le premier commence, le second, au moins 5 secondes plus tard et toujours à moins de 3 blocs, libère (oublié après une minute). L'écran envoie le second appel tout seul.
- [x] Deux appels, 5 secondes *(par défaut)*
- [ ] Un seul appel, immédiat

Ta réponse :

## D147. La Lettre
Choix appliqué : le texte (120 signes au plus) se tape avec `/phantasmon event trahison letter <place> <texte>` ; il arrive en chuchotement de l'animatrice, sans nom d'expéditeur, à la place visée (même morte). Il est écrit dans le journal du maître du jeu.
- [x] Commande, texte libre, anonyme *(par défaut)*
- [ ] Phrases toutes faites à choisir dans l'écran

Ta réponse :

## D150. Touche de la carte : Z et non M
Choix appliqué : la touche par défaut de la carte est Z (réglable). M est déjà « résumé » de Cobblemon dans le modpack (et « couper le micro »), N est le menu de Showtime, O, P, J, H sont pris, K est celle de l'éditeur du plan.
- [x] Z *(par défaut)*
- [ ] M quand même (en conflit avec Cobblemon)
- [ ] Une autre touche : ...

Ta réponse :

## D151. Noms des salles sur la carte : quand ?
Choix appliqué : un joueur ne voit le nom de la salle d'une place que quand la manche de cette place a commencé (la manche 1 dès la fin du lobby). Le fichier de l'étape disait « dès que le tirage est fait » : cela dévoilerait à tous, avant la manche, quelles salles viennent et dans quel ordre. Le maître du jeu, le spectateur et le fantôme voient tous les noms tout de suite.
- [x] Seulement les manches commencées *(par défaut)*
- [ ] Tous les noms dès que le tirage est fait (comme écrit dans T11)
- [ ] Seulement la salle où l'on est entré

Ta réponse :

## D152. Exploration : quelles zones, quel dessin
Choix appliqué : le backend retient toutes les zones du plan où un joueur est entré (pas seulement les salles) ; la carte ne hachure que les zones de type « place de salle » (`room_slot`) qu'il n'a jamais visitées. Un joueur vivant ne reçoit jamais les curseurs des autres ; le maître du jeu, le spectateur et le fantôme reçoivent ceux des joueurs vivants, **sans direction** (le serveur ne connaît que la position, pas le regard) : un disque à la couleur du bracelet.
- [x] Comme décrit *(par défaut)*
- [ ] Hachurer aussi les alcôves et les cabines
- [ ] Envoyer aussi la direction du regard des joueurs aux observateurs

Ta réponse :

## D153. Fond de carte fait à la main : calage
Choix appliqué : l'image `textures/trahison/map/<id d'étage>.png` (l'étage implicite s'appelle `main`) est étirée sur la **boîte englobante** de tout ce que le plan met sur cet étage (zones et portes), sans marge. Un exemple modifiable, `example.png`, est fourni (non utilisé tant qu'on ne le renomme pas).
- [x] Boîte englobante de l'étage, sans marge *(par défaut)*
- [ ] Boîte englobante plus une marge de 4 blocs
- [ ] Un champ « bornes de l'image » par étage, dans le plan

Ta réponse :

## D154. Notes : écriture à la fin de la partie
Choix appliqué : une fois la partie finie (`ENDED`), on ne peut plus écrire ni dessiner (`ERROR_TRAHISON_GAME_ENDED`) ; on peut seulement parcourir celles de tout le monde (D15). Les annotations restent en base sans limite de durée.
- [x] Lecture seule à la fin *(par défaut)*
- [ ] On peut encore écrire pendant le bilan

Ta réponse :
