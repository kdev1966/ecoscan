# Le Brain d'EcoScan

> Rôle validé le 13 novembre 2026, ouvert à d'autres fonctions. Service Python de la V2, derrière la passerelle Go.
> Rien n'est encore construit.

## Principe

Le Brain est **la seule autorité** sur l'interprétation des signalements. La passerelle transmet, le web et l'admin
affichent, un LLM rédige ; aucun d'eux ne décide. Le Brain ne dit que ce que les données établissent, et dit
quand il n'est pas sûr.

## Facultés

| Faculté | Ce que fait le Brain |
|---|---|
| **Comprendre** | Vérifier un signalement reçu : contrat respecté, position plausible (sur le littoral, précision suffisante), cohérence entre les boîtes, les quantités et la photo |
| **Mémoire** | L'histoire d'un lieu : signalé, nettoyé, revenu ; par qui et quand |
| **Savoir** | Les zones touchées, les doublons entre utilisateurs (même lieu, même moment), les tendances par plage et par saison |
| **Conscience** | La confiance d'un signalement et ses raisons : contexte expérimental (forêt, ville), scores bas, nombreuses corrections, position imprécise, version du modèle |
| **Expérience** | Apprendre des corrections : rassembler les boîtes `MODEL` corrigées ou supprimées et les boîtes `USER`, mesurer où le modèle se trompe, préparer le jeu de réentraînement pour `ml/` |
| **Savoir-être** | Aucun chiffre inventé, aucune raison qui ne soit un fait vérifié ; respect des personnes visibles sur les photos (signalement à l'admin) |

## Parcours d'un signalement (cible)

```text
passerelle ─► Comprendre (contrat, position, cohérence)
           ─► Mémoire (ce lieu est-il connu ?)
           ─► Savoir (doublon ? zone existante ?)
           ─► Conscience (confiance, raisons, faut-il une revue humaine ?)
           ─► décision structurée { accepté | à revoir | rejeté, zone, doublon de, confiance, raisons }
           ─► Mémoire + journal (une seule transaction)
```

## La boucle d'expérience

1. Le téléphone garde séparément la sortie du modèle et les corrections (`modelClassId`, `deleted`, boîtes `USER` ;
   voir `packages/contracts`).
2. Le Brain rassemble les corrections des signalements validés et mesure les erreurs par classe et par contexte.
3. `ml/` réentraîne à partir du jeu d'origine et des photos corrigées, puis évalue sur le même test **et** sur le
   test terrain.
4. Un modèle meilleur sur les deux reçoit une nouvelle version dans `model.json` et part dans l'application.

Les photos des utilisateurs ne servent à l'entraînement qu'avec leur accord ; la façon de le recueillir reste à décider.

## Questions ouvertes

- Seuils de la Conscience (précision GPS, score, nombre de corrections) : à fixer avec les premières données réelles.
- Rayon et fenêtre de temps d'un doublon.
- Définition d'une « zone » (grille, regroupement par distance, découpage administratif).
