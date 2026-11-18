# Essai du vocal en jeu (T5.1)

> À remplir à deux comptes, sur le serveur, avec Simple Voice Chat (2.5.x pour 1.21.1) et Phantasmon Events installés chez
> celui qui écoute. Le code de T5.2 à T5.4 est écrit pour que ces quatre points soient vrais ; **s'ils ne le sont pas, c'est
> toute la suite de T5 qui change** (voir [`solutions-techniques.md`](solutions-techniques.md), S4 et S5).

## Mise en place

1. Deux comptes, A (celui qui écoute, avec Events) et B (celui qui parle), sur le serveur, à quelques blocs l'un de l'autre,
   chacun avec Simple Voice Chat réglé et le micro qui marche.
2. Chez A, un admin Phantasmon : `/phantasmon event trahison voice B 0` (B = son nom en jeu). Le message du chat doit confirmer.
3. `/phantasmon event trahison voice reset` rend la main (à faire entre deux points).

## Les quatre points

| # | Point | Comment | Résultat | Date et version de Simple Voice Chat | Remarques |
|---|---|---|---|---|---|
| 1 | Le gain 0 coupe la voix de proximité de B, sans grésillement | `voice B 0`, B parle à 2 blocs | À remplir : OK / ÉCHEC | | |
| 2 | Un gain intermédiaire la baisse | `voice B 0.5`, puis `voice B 0.2` : le volume baisse nettement et sans saut | À remplir : OK / ÉCHEC | | |
| 3 | Même chose pour une voix venue d'une copie du hub (clé = identifiant de l'avatar, S5) | A est dans une copie du hub (miroir) où l'avatar de B est affiché ; `voice B 0` puis `voice B 0.5` | À remplir : OK / ÉCHEC | | |
| 4 | L'icône « parle » d'un joueur coupé | `voice B 0` : l'icône au-dessus de B (ou dans la liste) disparaît-elle quand il parle ? | À remplir : OK / ÉCHEC / autre : l'icône reste | | |

## À noter aussi (T5.1, charge)

- Le chuchotement (touche de Simple Voice Chat) change-t-il la portée une fois T5.2 en jeu ? Notes :
- La charge à 9 joueurs, mesurée en partie : le vocal du hub ne porte que les voix entre le serveur et les copies. Notes :
- Les voix de la proximité du serveur arrivent-elles à plus de 8 blocs (portée du serveur de la communauté) ? Si la portée est
  plus courte que la distance entre deux cabines, l'Écoute (T5.4) n'entendra pas la cabine visée. Portée du serveur :

## Verdict

À remplir : les quatre points sont confirmés (oui / non). Si l'un échoue, le dire à l'orchestrateur avant de continuer T5.
