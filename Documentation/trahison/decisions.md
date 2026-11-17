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

## D160. Offre en cabine : ce qu'elle vaut
Choix appliqué : l'offre promet « +1 point de plus » et le tient : si l'élu trahit lui-même (pour une paire : si c'est son vote qui compte), il gagne 1 point de plus aux résultats, une seule fois. Elle va à un humain vivant tiré à la graine, reçue quand sa cabine est fermée ; tout le monde entend qu'une offre a été faite, sans nom.
- [x] +1 point s'il trahit, annoncée sans nom *(par défaut)*
- [ ] Offre sans effet réel (un simple appât, l'élu n'a rien de plus)
- [ ] +1 point qu'il trahisse ou non, s'il accepte en appuyant sur un bouton

Ta réponse :

## D161. Offre en cabine : le Gardien et les cases sans cabine
Choix appliqué : le Gardien peut être tiré et reçoit l'offre comme les autres (son vote « Trahir » reste refusé, donc l'offre ne lui rapporte rien : un piège de plus). Sans cabine à fermer (réglage `cabins_required` faux, plan sans cabine), l'offre arrive dès le début du vote.
- [x] Le Gardien peut la recevoir *(par défaut)*
- [ ] Le Gardien est écarté du tirage de l'offre

Ta réponse :

## D162. Fuites : ce qu'on peut apprendre
Choix appliqué : quatre genres de faits vrais, jamais sur le joueur qui reçoit : temps mis à voter, temps mis à fermer sa cabine, tentative d'entrer dans une cabine, objet trouvé (sans dire lequel). Un seul destinataire par manche, après les résultats. La liste est courte exprès ; d'autres genres (par exemple « X est allé dans la salle de Y ») viendront avec T8 si tu en veux.
- [x] Ces quatre genres, un destinataire par manche *(par défaut)*
- [ ] Aussi dire quel objet a été trouvé
- [ ] Deux fuites par manche

Ta réponse :

## D163. La course : qui est annoncé
Choix appliqué : le premier groupe dont les joueurs résolvent leur salle pendant `ROOMS` est annoncé à tous avec les noms de ses membres. Une salle résolue par le délai ou par le maître du jeu ne compte pas.
- [x] Noms des membres, une seule annonce par manche *(par défaut)*
- [ ] Seulement la couleur de la porte, sans noms
- [ ] Classement complet des trois groupes

Ta réponse :

## D164. Le journal est réservé aux maîtres du jeu
Choix appliqué : l'ancienne règle « le journal ne quitte jamais le backend » devient « seuls les maîtres du jeu le reçoivent » (les 300 dernières lignes dans le bloc `gm`) pour que le panneau ait un journal filtrable. Aucun joueur, spectateur ou fantôme n'en reçoit. Un maître du jeu qui joue voit donc les actions des autres : c'est déjà le cas de son bloc `gm`.
- [x] Journal dans la vue du maître du jeu, 300 lignes *(par défaut)*
- [ ] Journal gardé au backend, consulté par une route HTTP admin
- [ ] 1 000 lignes

Ta réponse :

## D165. Panneau du maître du jeu : fenêtre, chat, réglages
Choix appliqué : le panneau reste une fenêtre (`GmScreen`, 480 x 280) ouverte par la commande, par une touche non liée par défaut, et par une page « Maître du jeu Trahison » du menu de Phantasmon (admins) qui n'a qu'un bouton d'ouverture (une page de menu n'a pas la place d'un tel écran). Les phrases libres (chuchoter, parler à tous) se tapent dans le chat, comme la lettre, le panneau préremplissant la commande. Les réglages se changent dans le lobby seulement, y compris les durées par `set_settings` (l'écran n'édite pas encore les durées).
- [x] Fenêtre + page de lancement + chat pour les phrases *(par défaut)*
- [ ] Un vrai champ de texte dans le panneau (écran de saisie dédié)
- [ ] Réglages modifiables aussi en cours de partie

Ta réponse :

## D170. Espèces du catalogue Pokémon
Choix appliqué : le catalogue (`pokemon_catalog.json`) couvre les générations 1 à 3 (386 espèces), les énigmes ne tirent que dans la liste « connues de tous » (58 espèces). Le script `tools/pokemon_data.py` se relance pour changer cela.
- [x] Générations 1 à 3, énigmes sur les espèces célèbres *(par défaut)*
- [ ] Jusqu'à la génération 4 ou plus
- [ ] Énigmes sur toutes les espèces du catalogue

Ta réponse :

## D171. Duel tactique : attaque de l'adversaire affichée
Choix appliqué : la vue du duel montre toujours quelle attaque l'adversaire va jouer (`enemy_next_move`), en plus de la règle écrite, pour que le puzzle se déduise sans connaître les types. Seule la suite gagnante est secrète (maître du jeu). Les attaques n'ont pas de nom : elles s'affichent « type + puissance » (aucun nom tiré des jeux).
- [x] Attaque de l'adversaire montrée *(par défaut)*
- [ ] Cachée, à déduire de la règle et de la table des types
- [ ] Attaques nommées par nous

Ta réponse :
