# Le jeu de trahison

> Une partie de Phantasmon Events. Conception terminée le 2026-11-13, code pas encore commencé.
> Cette page est le contexte court à charger avant de travailler sur le jeu.

## Ce que c'est

Un jeu de trahison pour exactement 9 joueurs d'une communauté Cobblemon, joué le temps d'une soirée dans un hub
miroir : des salles d'escape game résolues par groupes de trois, puis un vote secret « s'allier » ou « trahir »
contre ceux avec qui on vient de coopérer. Les points sont sur des bracelets ; 9 points ouvrent la porte finale.
L'histoire, les noms et les salles sont les nôtres, tirés du lore de la communauté.

Il **fait partie de Phantasmon Events** : même mod client (`phantasmon_events`), même module backend, même
installation. Aucun autre mod, aucun autre dépôt. Phantasmon et Showtime ne sont pas modifiés.

## Où lire

| Besoin | Fichier |
|---|---|
| Les règles du jeu, validées | [`plan.md`](plan.md) |
| Comment on le construit : paquets, modèle de partie, protocole, monde, salles | [`architecture.md`](architecture.md) |
| Le travail, étape par étape, en sous-tâches faites pour un agent | [`etapes/README.md`](etapes/README.md) |
| Tous les messages et actions (à partir de l'étape T0) | `protocole.md` |

Pour une sous-tâche (par exemple « T3.5 `wiring` ») : lire `architecture.md`, puis la fiche de l'étape, puis sa
liste « À lire avant », et seulement ensuite écrire du code.

## Règles

1. **Le backend décide, le client affiche.** Aucune règle du jeu côté client.
2. **Un secret ne quitte le backend que pour celui qui a le droit de le voir.** Votes, rôles, solutions et morts
   sortent par les vues faites pour chaque destinataire, jamais par un envoi à tous « pour l'affichage ».
3. **Aucun mod serveur, aucune commande serveur, aucun vrai coffre.** Ce que le jeu ajoute au monde est affiché
   par le mod de chaque joueur : faux blocs, objets cliquables, corps, écrans.
4. **Ouvertures réelles, fermetures fausses.** Un faux bloc arrête un joueur ; un vrai bloc ne se traverse pas.
5. **Phantasmon et Showtime ne se modifient pas.** Tout passe par leurs points d'entrée pour add-ons. S'il en
   manque un, s'arrêter et le signaler.
6. Dans Events, le jeu vit dans ses paquets `trahison`. Ne toucher au reste d'Events (hubs, tournois, PNJ, IA)
   que si la fiche le dit.
7. Code, commentaires et messages de commit en **anglais**. Documentation en **français**. Textes du jeu dans
   `en_us.json` et `fr_fr.json`, toujours complets tous les deux.
8. **Tout tirage part d'une graine.** Même graine, même résultat. Les règles pures (paquet `rules`) ont des tests.
9. Types de messages préfixés `EventsTrahison` ; codes d'erreur préfixés `ERROR_TRAHISON_`.
10. Pas de nom de personne dans le code ni la documentation. Rien de repris du jeu qui nous inspire (noms, textes,
    images, salles), ni des jeux Pokémon.
11. Les écrans visent la qualité du menu de Cobblemon Battle Showtime.
12. Chaque étape met à jour la documentation qu'elle touche.

## Construire et déployer

- Mod client : Gradle tourne avec **JDK 25** (Loom) ; le code vise Java 21. Ne pas « réparer » cela. Phantasmon
  et Showtime doivent être dans le Maven local (`./gradlew publishToMavenLocal` chez eux).
- Module backend (`backend/`) : Java 21, compilé contre le jar du backend de Phantasmon dans le Maven local. Les
  tests d'intégration demandent Docker ; les tests des règles pures, non.
- Après un changement : `Phantasmon-Client/scripts/deploy-to-prod-server.sh --events` pour le mod,
  `Phantasmon-Backend/scripts/deploy-ubuntu.sh` pour le module backend. Puis validation en jeu, puis commit.

## Essayer sans neuf joueurs

Des joueurs factices (réservés aux admins) complètent une partie dès l'étape T1. Chaque étape doit rester jouable
avec deux vrais comptes et sept factices.
