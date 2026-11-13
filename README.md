# EcoScan

Application Android open source qui détecte, classe et géolocalise les déchets sur le littoral, avec un modèle d'IA
exécuté **sur le téléphone, sans connexion**. Pour qu'un citoyen, un bénévole ou une association documente un point
de pollution en quelques secondes et constitue des relevés utiles aux campagnes de nettoyage.

Zone de la V1 : plages et littoral méditerranéen tunisien.

> **État** : V1 en développement, étape 1 sur 4 faite (scanner, analyse de la photo, résultat ; interface en
> français, arabe et anglais). Le modèle est évalué sur des vues de drone ; sa qualité sur des photos prises au
> téléphone n'est pas encore mesurée.

## Monorepo

| Dossier | Contenu |
|---|---|
| [`apps/mobile`](apps/mobile) | Application Android (Kotlin, Compose, CameraX, LiteRT) |
| [`packages/contracts`](packages/contracts) | Classes, modèle, schéma du signalement : la référence commune |
| [`ml`](ml) | Entraînement, export et évaluation du modèle |
| [`docs`](docs) | [PRD](docs/PRD.md), [architecture](docs/architecture/overview.md), [Brain](docs/architecture/brain.md) |

À venir avec la V2 : `apps/web` (carte publique), `apps/admin` (modération), `apps/api` (passerelle Go),
`apps/brain` (Python), `database`, `infrastructure`.

## Application mobile

Android 8.0 (API 26) minimum, processeurs arm64. Compilation avec un JDK 17 :

```bash
cd apps/mobile
./gradlew testDebugUnitTest      # tests unitaires
./gradlew assembleDebug          # APK de debug
./gradlew connectedDebugAndroidTest   # tests sur le téléphone (WasteDetectorDeviceTest)
```

Ouvrir `apps/mobile` (et non la racine) dans Android Studio.

## Licence et crédits

Code sous licence [AGPL-3.0](LICENSE). Modèle entraîné sur le jeu de données TUN-MED-MAR-UAV-LITTER
(Moataz Harrazi et al., ENSIT / RIFTSI), sous licence CC BY 4.0 : voir [NOTICE.md](NOTICE.md).
