# Architecture d'EcoScan

> Décidée le 13 novembre 2026, sur le modèle de khaddouj-ai. Ce document décrit **la cible** ;
> la colonne « Statut » dit ce qui existe.

## Chaîne

```text
V1 :  Application mobile (hors ligne) ──export GeoJSON + photos──► partage manuel

V2 :  Application mobile ──┐
      Web public ──────────┼──► Passerelle Go ──► Brain Python ──► PostgreSQL + PostGIS
      Admin ───────────────┘          (publique)       (interne)          (interne)
```

## Briques

| Dossier | Rôle | Technologie | Statut |
|---|---|---|---|
| `apps/mobile` | Scanner, revue, signalement, historique, export ; détection sur le téléphone, sans réseau | Kotlin, Compose, CameraX, LiteRT | V1, étape 1 sur 4 faite |
| `apps/web` | Carte publique des zones touchées, puis PWA avec le modèle ONNX dans le navigateur | TypeScript | V2 / V3, pas créé |
| `apps/admin` | Validation des signalements, modération des photos (personnes visibles) | TypeScript | V2, pas créé |
| `apps/api` | Passerelle publique : réception des signalements et des photos, authentification, limites de débit | Go | V2, pas créé |
| `apps/brain` | L'intelligence du système (voir [brain.md](brain.md)) | Python | V2, pas créé |
| `ml` | Entraînement, export, évaluation, test terrain du modèle de détection | Python, Ultralytics | Procédure documentée |
| `packages/contracts` | Classes, modèle, schéma du signalement : la référence commune | JSON Schema | v1 |
| `database/migrations` | Schéma PostgreSQL + PostGIS | SQL | V2, pas créé |
| `infrastructure/compose` | Déploiement Docker sur VPS | Docker Compose, Caddy | V2, pas créé |

Les dossiers de la V2 ne sont créés qu'avec la V2.

## Frontières

- **L'application mobile fonctionne seule.** Toutes les fonctions de la V1 marchent sans réseau ;
  le serveur, quand il existera, ne sera jamais nécessaire pour signaler.
- **La passerelle Go ne décide rien.** Elle reçoit, authentifie, limite et transmet ; aucune règle métier.
- **Le Brain est la seule autorité** sur l'interprétation des signalements (confiance, doublons, zones).
- **Un LLM n'est qu'un outil de rédaction** (V1.1 sur le téléphone, plus tard côté serveur). Il ne décide pas
  et n'invente aucun chiffre.
- **Les contrats sont la seule définition partagée.** Une brique ne lit jamais le code d'une autre.
- Seule la passerelle est exposée sur Internet ; le Brain et PostgreSQL restent internes.

## Le modèle de détection : de l'entraînement au téléphone

```text
tunmed/base (jeu de données) ──► ml/ : entraînement, export, évaluation
                                       │
                                       ├─► packages/contracts/v1/model.json (version, empreinte, mesures)
                                       └─► apps/mobile/app/src/main/assets/ecoscan_detector_v1.tflite

V2 : corrections des utilisateurs ──► Brain ──► ml/ (réentraînement) ──► nouvelle version du modèle
```

## Données hors du dépôt

| Emplacement | Contenu | Pourquoi hors du dépôt |
|---|---|---|
| `tunmed/base/` | Jeu de données TUN-MED-MAR-UAV-LITTER (1,2 Go), CC BY 4.0 | Taille ; redistribution à faire depuis sa source |
| `tunmed/models/` | `best.pt`, `last.pt`, `best.onnx`, exports TFLite (55 Mo) | Taille ; seul le `.tflite` utilisé par l'application est versionné |

## Dépôt

Monorepo git, branche `main` stable, une branche par travail. Chaque application garde son propre outil de
compilation (Gradle, Go, npm, pip) ; la CI aura un job par application, lancé seulement quand son dossier change.
