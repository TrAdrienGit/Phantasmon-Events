# Procédure d'une soirée de Trahison

> Écrite le 2026-11-16 (étape T4.4) pour quelqu'un qui n'a pas codé le jeu. Elle suit l'ordre des choses : avant, pendant, après.
> Ce qui n'est pas encore fait est dit en clair dans les encadrés « Pas encore ». Le vocal par zones (étape T5) et la carte du
> lieu (T11) n'existent pas encore : ne les cherche pas.

## Les rôles

- **Admin du jeu** : crée la partie, nomme les maîtres du jeu. Il faut un compte admin de Phantasmon.
- **Maître du jeu** : fait avancer la partie depuis l'écran du maître du jeu (mode « maître du jeu »), ou personne (mode automatique).
- **Admin du serveur** : colle les salles dans le monde avec Axiom.
- **Neuf joueurs** : les autres comptes qui viennent sont spectateurs.

## Avant (la veille, ou au moins une heure avant)

1. **Vérifier la banque.** Il faut au moins neuf salles enregistrées *avec leurs blocs* (commande `/phantasmon event trahison room list` :
   la colonne des blocs doit dire oui). Sans cela, le tirage n'a rien à tirer et la partie jouera une salle d'essai.
2. **Vérifier le plan.** Le plan fixe du complexe doit être enregistré pour le hub de la soirée avec ses neuf places de salle
   (trois par manche). Son éditeur : `/phantasmon event trahison edit`.
3. **Se placer dans le hub miroir** de la soirée (celui où le jeu se joue).
4. **Créer la partie.** Ouvre l'écran du maître du jeu : `/phantasmon event trahison gm`. Choisis le mode (automatique ou maître du
   jeu) puis « Créer la partie ». Le jeu tire tout de suite les neuf salles.
5. **Regarder le tirage** (panneau « Salles tirées », visible par les maîtres du jeu nommés : nomme-toi d'abord dans la liste du
   lobby si besoin). Chaque manche a trois salles de difficulté proche, avec des mécanismes différents. Si une ligne rouge dit que la
   banque n'a pas pu respecter une contrainte, c'est normal avec une petite banque : relis-la.
   - Une salle ne convient pas ? Clique-la, puis clique la salle voulue dans la liste.
   - Tout est à refaire ? « Tirer à nouveau ». **Fais-le avant d'exporter les fichiers.**
6. **Fabriquer les fichiers.** Bouton « Exporter les fichiers » (ou `/phantasmon event trahison export`). Le message dans le chat dit
   où ils sont, avec un lien qui ouvre le dossier : `.minecraft/phantasmon_events/trahison/export/<partie>/`.
   - un fichier par place, `place_<nom>.schem` et `.litematic`, **déjà tourné** pour sa place ;
   - `all_places.schem` et `.litematic` : les neuf places en un seul fichier (raccourci, s'il n'est pas trop grand) ;
   - `paste_sheet.txt` : la fiche de collage.
   - Les fichiers sont aussi envoyés au backend : un admin les télécharge avec `/phantasmon event trahison exports` (menu :
     choisir la partie, puis un fichier ou « Tout télécharger »). Pas besoin de transmettre le dossier à la main.
   - La fiche est en français avec accents (UTF-8) et, si tu étais dans le hub à l'export, donne aussi les coordonnées du monde.
7. **Envoyer le dossier à l'admin du serveur** avec cette consigne : « Colle chaque fichier avec Axiom, **sans rotation ni miroir**,
   le coin minimum du fichier (x, y et z les plus petits) sur le coin écrit dans `paste_sheet.txt`. » La fiche dit, pour chaque place,
   la salle, le coin où coller, le coin opposé et l'orientation. En cas de doute sur un décalage, essayer d'abord une seule place.
8. **Vérifier le collage.** Quand l'admin dit avoir fini, va près des places (pour que leurs chunks soient chargés) et clique
   « Vérifier le collage » (ou `/phantasmon event trahison check`). Le chat liste chaque place : bien collée, pas collée, mal collée
   (mauvaise salle, mauvaise rotation ou décalage, ou texte de panneau différent) ou chunks non chargés. Corrige avec l'admin, puis revérifie jusqu'à
   « Toutes les places sont bien collées ». La vérification regarde un échantillon de blocs : elle ne remplace pas un tour en jeu des
   places qui ont un doute.
9. **Faire un tour de contrôle** : entre dans une place et regarde que les emplacements (terminaux, indices) s'affichent là où la
   salle les attend.

> **Pas encore** : l'essai du vocal (étape T5). La vérification du collage n'est jamais bloquante : le bouton « Démarrer » ne
> la demande pas, c'est à toi de la faire.

## Pendant

1. **Ouvrir le lobby.** La partie créée est déjà en lobby. Les neuf joueurs rejoignent (action « rejoindre » du jeu) ; les autres
   restent spectateurs. Dans l'écran du maître du jeu, tu peux ajouter des joueurs factices pour compléter (« Remplir ») : utile pour
   un essai, pas pour la vraie soirée.
2. **Nommer les maîtres du jeu** (admins seulement) dans la liste du lobby : aucun, un ou plusieurs. Un maître du jeu peut aussi jouer.
3. **Démarrer.** Il faut exactement neuf inscrits. « Démarrer » lance le prologue. En mode « maître du jeu », il faut au moins un
   maître du jeu nommé.
4. **Faire avancer.** Chaque étape a un compte à rebours : prologue, portes, salles, temps libre, vote, résultats, puis la porte finale.
   En mode automatique tout s'enchaîne ; en mode maître du jeu, « Étape suivante » passe à la suivante.
5. **Pause, reprise, rallonge.** « Pause » et « Reprendre » gèlent le compte à rebours. Les boutons « +60 s » et « -60 s » le
   corrigent. Un joueur déconnecté met la partie en pause automatique (au plus cinq minutes) en mode automatique.
6. **Incidents.**
   - Un joueur coincé dans une salle : le maître du jeu peut ouvrir une porte du plan à la main (commande admin
     `/phantasmon event trahison door <porte> open`) ; les salles non finies s'ouvrent d'elles-mêmes à la fin de l'étape des salles,
     chacun du groupe perdant un point.
   - Un score faux : les boutons « + » et « - » à côté du bracelet corrigent les points.
   - Une salle cassée ou un mauvais collage découvert en route : n'arrête pas la partie, note-le ; remplacer une salle n'est plus
     possible une fois que les salles de sa manche ont démarré.
   - Un joueur perdu : l'écran du maître du jeu montre où est chaque joueur (zone).
7. **Terminer en urgence** : « Terminer » (avec confirmation) arrête la partie.

## Après

1. **La fin de partie** arrive à la porte finale : ceux qui y passent à neuf points gagnent. Le tableau des résultats est dans le hall.
2. **Les temps mesurés** des salles sont enregistrés tout seuls pendant la partie (par étape) ; rien à sauvegarder à la main. Au bout
   de quelques parties, le temps affiché d'une salle devient celui qui a été mesuré.
3. **Nettoyer** : l'admin du serveur peut recoller de la matière neutre sur les places, ou simplement laisser les salles ; le tirage de
   la partie suivante évite les salles des dernières parties.
4. **Noter** ce qui a coincé (salle trop dure, indice manquant) pour la banque, dans `decisions.md` si c'est une question de règle.

## En cas de doute

- Le message d'erreur du jeu s'affiche dans le chat, avec son code (`ERROR_TRAHISON_...`) : le protocole les liste.
- Pour un essai complet sans neuf personnes : joueurs factices + le réglage « rapide » à la création (toutes les étapes durent
  dix secondes).
