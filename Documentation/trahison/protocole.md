# Protocole du jeu de trahison

> Référence unique de tous les messages et actions du jeu. Chaque étape y **ajoute** ce qu'elle crée (une section par
> message, une section par action) ; elle ne la contourne jamais. Vue d'ensemble : [`architecture.md`](architecture.md)
> section 5. État : étape T0 (Ping et Pong seulement).

## 1. Conventions

- Canal : le WebSocket de Phantasmon. Enveloppe `{ "type": "...", "data": { ... } }`.
- Tous les types commencent par `EventsTrahison` (le client de Phantasmon n'accepte que le préfixe `Events`).
- Sens : **C2S** client vers backend, **S2C** backend vers client.
- Le backend décide ; un message C2S est une demande, jamais un fait. Un secret n'est jamais envoyé à qui n'a pas le
  droit de le voir (une vue par destinataire).
- Une demande refusée est répondue par un `EventsTrahisonSay` privé portant un code `ERROR_TRAHISON_…`, traduit dans
  `phantasmon_events.error.*` (`en_us.json` et `fr_fr.json`). Une demande d'un message inconnu du module est ignorée.
- Les noms de champs sont en `snake_case`.
- Les routes HTTP (`/events/trahison/…`) servent aux gros contenus ; elles ont leur section 4.

## 2. Messages

Modèle de section à recopier pour chaque nouveau message :

> ### `EventsTrahisonXxx`
> - Sens : C2S | S2C
> - Qui l'envoie / qui le reçoit : …
> - Quand : …
> - Champs : tableau (nom, type, sens)
> - Exemple JSON
> - Erreurs : codes possibles

### `EventsTrahisonPing`

- Sens : C2S.
- Qui l'envoie : tout joueur connecté.
- Quand : commande `/phantasmon event trahison` (étape T0) ; sert à vérifier la liaison avec le module backend.
- Champs :

| Champ | Type | Notes |
|---|---|---|
| `nonce` | texte | Facultatif. Renvoyé tel quel dans le Pong |

```json
{ "type": "EventsTrahisonPing", "data": { "nonce": "123456789" } }
```

- Erreurs : aucune.

### `EventsTrahisonPong`

- Sens : S2C.
- Qui le reçoit : le joueur qui a envoyé le Ping, et lui seul.
- Quand : en réponse immédiate à un Ping.
- Champs :

| Champ | Type | Notes |
|---|---|---|
| `nonce` | texte | Celui du Ping ; absent s'il n'y en avait pas |

```json
{ "type": "EventsTrahisonPong", "data": { "nonce": "123456789" } }
```

- Erreurs : aucune.

### Messages prévus (à détailler par l'étape qui les crée)

`EventsTrahisonView`, `EventsTrahisonPatch`, `EventsTrahisonRoom`, `EventsTrahisonPuzzle`, `EventsTrahisonSay`
(S2C) ; `EventsTrahisonAction`, `EventsTrahisonZone`, `EventsTrahisonGm` (C2S). Contenu esquissé dans
`architecture.md` section 5.

## 3. Actions

Les actions voyagent dans `EventsTrahisonAction` : `{ "action": "<nom>", ... }`. Une section par action, sur le
modèle : qui peut l'envoyer (rôle, étape de la partie, zone), champs, effet, réponse, codes d'erreur.

Aucune action à ce jour. Prévues : `vote`, `pass_door`, `enter_cabin`, `close_cabin`, `use_slot`, `puzzle_input`,
`pick_up`, `use_item`, `open_final_door`.

## 4. Routes HTTP

Aucune à ce jour. Préfixe prévu : `/events/trahison/…` (définitions de salles, schématiques, plan). Chaque route
ajoutée est aussi reportée dans [`../backend-reference.md`](../backend-reference.md).

## 5. Codes d'erreur

Préfixe `ERROR_TRAHISON_`. Tableau à compléter : code, canal, cas, clé i18n.

| Code | Canal | Cas | Clé i18n |
|---|---|---|---|
| (aucun à ce jour) | | | |
