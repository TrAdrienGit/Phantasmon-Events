# Premier essai en jeu de Trahison

> Rien de Trahison n'a jamais tourné dans Minecraft. Cette liste se coche dans l'ordre : si un point échoue, on s'arrête et on
> note le symptôme (et le journal du jeu, `logs/latest.log`) avant de continuer. Le détail de chaque test est dans
> [`tests-humains.md`](tests-humains.md) (les sections sont citées par leur titre) ; ici, seulement l'ordre et les cases.
> Aucune des étapes de déploiement ci-dessous n'a été lancée par l'agent qui a écrit cette page.

## 0. Déploiement (à faire par Gwen, rien n'est lancé automatiquement)

- [x] **Module backend** : `Phantasmon-Events/backend` : `./gradlew jar`, après `./gradlew publishToMavenLocal` dans
  `Phantasmon-Backend`. Le jar part dans `/opt/phantasmon/addons/` avec `Phantasmon-Backend/scripts/deploy-ubuntu.sh`
  (voir [`../backend-reference.md`](../backend-reference.md), section 1). **Demande le consentement du propriétaire avant**
  (le serveur Ubuntu, sa base et Flyway `db/events` V1 à V10 sont touchés). Résultat : déployé le 2026-11-18 avec l'accord de Gwen, sauvegarde de la base prise avant, migrations events V6 à V10 appliquées.
- [x] **Version du backend** : `GET /version` annonce `"addons": {"phantasmon_events": ...}`. Résultat : `{"phantasmon_events":"0.1.0"}`.
- [x] **Mod client** : `Phantasmon-Client/scripts/deploy-to-prod-server.sh --events` (ou son `.bat`) : construit Showtime,
  Phantasmon puis Events, et copie les jars dans les instances `Cobblemon Academy 2.0 - *` (`C:/Users/adrie/curseforge/minecraft/Instances`).
  Sans `--events`, le jar Events est retiré. Relancer le jeu après. Résultat : jars copiés dans les deux instances le 2026-11-18 ; relancer le jeu.
- [ ] **Simple Voice Chat** (2.5.x pour 1.21.1) installé chez les deux comptes pour la partie voix ; **absent** chez un des
  deux pour le point 1 ci-dessous. Résultat :

## 1. Le mod se charge

- [ ] Le jeu démarre avec Events **sans** Simple Voice Chat : pas de plantage, `Phantasmon Events loaded` dans `latest.log`
  (l'entrée `voicechat` n'est lue que par Simple Voice Chat). Résultat :
- [ ] Idem **avec** Simple Voice Chat : aucune erreur de plugin `phantasmon_events_trahison` dans le journal. Résultat :
- [ ] Options > Commandes, catégorie « Phantasmon Events » : les touches Trahison sont là (V cabine, M carte, K éditeur, plus
  les touches libres : menu, MJ, annonce, inventaire, objet suivant) et leurs noms sont traduits. Résultat :
- [ ] Aucun `Mixin apply failed` ni `InvalidInjectionException` dans `latest.log`. Résultat :
- [ ] **T0** : `/phantasmon event trahison ping` répond « Pong » (liaison avec le module backend). Résultat :
- [ ] Menu principal, puis un monde solo : aucune exception au chargement ni en se déconnectant (les gestionnaires
  ne tournent pas avant le monde). Résultat :

## 2. Le lobby (un compte admin, un hub miroir)

- [ ] Se placer dans le hub miroir de la soirée ; l'écran du MJ s'ouvre : `/phantasmon event trahison gm` (ou le bouton du menu
  Phantasmon). Résultat :
- [ ] Charte UI : l'écran à 320 × 180 et 854 × 480, échelles 2 à 4 (section « Charte UI » de [`tests-humains.md`](tests-humains.md)).
  Résultat :
- [ ] « Créer la partie » : la partie passe en `LOBBY` ; pour l'essai, `test_room` = `essai_cobblemon` si la banque a moins
  de neuf salles (sinon `ERROR_TRAHISON_BANK_TOO_SMALL`). Résultat :
- [ ] `/phantasmon event trahison menu` : l'écran joueur s'ouvre, sans partie et en lobby. Résultat :

## 3. Deux comptes et des factices

Sections : « T1.4 client » et « Tests humains T3.3 » de [`tests-humains.md`](tests-humains.md).

- [ ] Le second compte rejoint (bouton ou `join`) ; les deux apparaissent dans le lobby de l'écran MJ. Résultat :
- [ ] L'admin ajoute des factices jusqu'à neuf joueurs (`add_bots` de l'écran MJ). Résultat :
- [ ] Les maîtres du jeu sont nommés dans le lobby (D7). Résultat :
- [ ] « Démarrer » : le HUD (bracelet, chrono) s'affiche, l'animatrice parle (textes en `trahison/animateur`), pas d'exception.
  Résultat :
- [ ] Prologue : chaque joueur reçoit son rôle en privé (section « T7.1 et T7.2 ») ; l'autre compte ne voit pas celui du premier.
  Résultat :

## 4. Les portes

Sections : « T2.3 portes de couleur », « T2.2 faux blocs et zones » (plan complet enregistré avant).

- [ ] Les portes de couleur fermées arrêtent le joueur ; `door <id> open` / `close` agit chez les deux comptes. Résultat :
- [ ] Le panneau au-dessus d'une porte se lit à 10 blocs et n'est pas en miroir. Résultat :
- [ ] Une paire et un solo qui vont ensemble ouvrent la porte ; le passage est enregistré. Résultat :
- [ ] Fin de `DOORS` : un joueur sans porte meurt (D2). Résultat :

## 5. Une salle

Sections : « Tests humains T3.3 », « T3.4 mini-jeux », « T9.3 ».

- [ ] Les blocs de la salle d'essai sont collés à leur place ; l'étape `ROOMS` s'ouvre, les emplacements se dessinent
  (aucun carré rose-noir, aucun texte en miroir). Résultat :
- [ ] Viser un emplacement : contour et nom de l'action ; clic : l'écran du mécanisme s'ouvre une fois, Échap le ferme sans rien perdre.
  Résultat :
- [ ] Un mini-jeu se résout (`/phantasmon event trahison minigame three_dials` hors ligne d'abord) ; le cadenas, la sortie
  de la salle. Résultat :
- [ ] Inventaire (touche réglée) : les objets sans image ont une pastille colorée, rien ne plante. Résultat :

## 6. Le vote

Sections : « T1.4 client » (vote d'une paire), « T2.4 » et « Décisions D143, D144, D150, D151 ».

- [ ] Fin de salle, `FREE_TIME`, puis la cabine : V la ferme, le pupitre de vote s'ouvre. Résultat :
- [ ] Vote « Allier » / « Trahir » des deux côtés ; les résultats et les points changent au bracelet. Résultat :
- [ ] Pause par le MJ, puis automatique (déconnexion d'un joueur). Résultat :

## 7. La carte

Section : « T11 : la carte du lieu ».

- [ ] M ouvre la carte (si Cobblemon répond aussi à M, rebinder l'une des deux) ; les salles explorées, les noms des places
  des manches suivantes. Résultat :
- [ ] Un trait et une note sur la carte, puis fermeture et réouverture : ils sont gardés. Résultat :

## 8. La voix

Voir [`vocal-essai.md`](vocal-essai.md) (les quatre points) **avant** la section « T5 : le vocal par zones » de
[`tests-humains.md`](tests-humains.md).

- [ ] Les quatre points de `vocal-essai.md` confirmés. Résultat :
- [ ] Deux salles voisines, même salle, cabine fermée, fantômes, annonce du MJ : section « T5 ». Résultat :

## 9. La salle d'essai Cobblemon

Fichier : `Documentation/trahison/exemples/salle-essai-cobblemon.json`. Section : « T8 Cobblemon : écrans des mécanismes
Pokémon » et « T8.3 Le partenaire à pouvoir » ; la mise en place est au paragraphe « Salle d'essai » de la section T8 de
[`tests-humains.md`](tests-humains.md).

- [ ] La salle est enregistrée (`PUT /events/trahison/rooms/essai_cobblemon`, admin), ses blocs construits (`room new essai_cobblemon`),
  les boîtes de pouvoir vides d'air. Résultat :
- [ ] Une partie `test_room` = `essai_cobblemon` : les écrans Pokémon s'ouvrent, les cris sonnent, aucune exception Cobblemon.
  Résultat :
- [ ] Le partenaire à pouvoir apparaît, suit son joueur, et Flash / Force / Coupe agissent. Résultat :

## 10. Fin de l'essai

- [ ] Fin de partie : le tableau des résultats, la révélation des rôles, le récapitulatif (section « T9.4 »). Résultat :
- [ ] Déconnexion et reconnexion en cours de partie : la vue revient, aucun écran bloqué. Résultat :
- [ ] Journal du jeu et journal du backend relus : noter toute ligne `Trahison timer task failed` ou `Trahison message ... failed`.
  Résultat :
