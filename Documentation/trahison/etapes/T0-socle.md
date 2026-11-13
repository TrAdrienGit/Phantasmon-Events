# T0 : le socle dans Events

## Objectif

Faire une place au jeu dans Phantasmon Events, côté client et côté backend, et vérifier que les deux se parlent.
Aucune règle du jeu ici. Rien de nouveau à installer : c'est le même mod et le même module qu'aujourd'hui.

**Fini quand :** en jeu, la commande `/phantasmon event trahison` envoie un message au backend et affiche sa
réponse, et la table des parties existe dans la base.

## À lire avant

- [`architecture.md`](../architecture.md), sections 2 et 9.
- `src/main/java/com/mystaria/events/PhantasmonEvents.java` et `EventsCommands.java` : comment une partie d'Events
  s'enregistre et ajoute ses commandes.
- `src/main/java/com/mystaria/events/tournament/` : un exemple complet d'une fonction d'Events côté client.
- `backend/src/main/java/com/mystaria/phantasmon_backend/events/EventsWsHandler.java`, `EventsDatabase.java`, et
  le paquet `tournament/` : le même exemple côté backend.
- `backend/src/main/resources/db/events/` : les migrations existantes.
- `Documentation/backend-reference.md` et `Documentation/plan-separation.md`, section 3.

## Sous-tâche

### T0.1 Le socle (Sonnet)

Backend (`backend/`) :

- Paquet `com.mystaria.phantasmon_backend.events.trahison`.
- `TrahisonWsHandler` : un second `WsMessageHandler` (le cœur en accepte plusieurs), qui prend les types
  commençant par `EventsTrahison`. Pour l'instant il répond à `EventsTrahisonPing` par `EventsTrahisonPong`.
- Une migration de plus dans `db/events`, au numéro qui suit le dernier fichier présent : la table
  `trahison_games` (`id uuid`, `hub text`, `state jsonb`, `created_at`, `updated_at`, `ended_at`).
- Un test d'intégration sur le modèle de `MirrorHubIntegrationTest` (il lui faut Docker : le dire dans le compte
  rendu s'il ne tourne pas).

Client (`src/`) :

- Paquet `com.mystaria.events.trahison`, avec une classe `Trahison` dont la méthode `register()` est appelée par
  `PhantasmonEvents`.
- Commande `/phantasmon event trahison` dans `EventsCommands` : envoie un `EventsTrahisonPing`
  (`PhantasmonApi.send`) et affiche la réponse (`PhantasmonApi.onEventsMessage`).
- Les textes dans `assets/phantasmon_events/lang/en_us.json` et `fr_fr.json`, clés
  `phantasmon_events.trahison.*`.

Documentation :

- `Documentation/trahison/protocole.md` : créé, avec les deux messages de cette étape.
- `Documentation/backend-reference.md` : la nouvelle table et les nouveaux messages.

**Fini quand :** le « Fini quand » de l'étape, après déploiement du mod (`--events`) et du module backend.

## Hors périmètre

Toute règle du jeu, tout écran, toute donnée de partie. Toute modification de Phantasmon.

## Pièges

- Ne pas créer de second mod, de second module ni de second historique Flyway : tout va dans ceux d'Events.
- Le numéro de migration se lit dans le dossier au moment de coder (un autre travail sur Events a pu en ajouter).
- Ne pas « réparer » la version de Java de Gradle : Loom exige JDK 25 pour tourner, même si le code vise Java 21.
