# Contrats EcoScan

La référence commune à toutes les briques : application mobile (Kotlin), passerelle (Go), Brain (Python),
web et admin (TypeScript). Format neutre : JSON et JSON Schema (draft 2020-12), lisibles par les quatre langages.

| Fichier | Contenu |
|---|---|
| `v1/classes.json` | Les 7 classes : indice de sortie du modèle, identifiant, nom du jeu de données, couleur, libellés en, fr, ar |
| `v1/model.json` | Le modèle embarqué : version, fichier et empreinte SHA-256, entrée, sortie, post-traitement, évaluation |
| `v1/report.schema.json` | Un signalement : une `Feature` GeoJSON (RFC 7946) |
| `v1/report-collection.schema.json` | Un export : une `FeatureCollection` de signalements (export V1, import de l'API en V2) |
| `v1/examples/export.geojson` | Un export d'exemple, valide (données fictives) |

## Règles

1. **L'ordre des classes ne change jamais.** Un signalement enregistre l'indice (`classId`), pas le nom.
   Une nouvelle classe s'ajoute à la fin, avec un nouveau modèle.
2. **Un changement incompatible crée `v2/`** ; `v1/` reste en place tant qu'un client l'utilise.
   Ajouter un champ facultatif reste compatible.
3. **Les détections du modèle et les corrections restent séparables** : une boîte `MODEL` garde la classe
   d'origine dans `modelClassId` et n'est jamais effacée, seulement marquée `deleted`. C'est la matière du
   réentraînement (voir `docs/architecture/brain.md`, faculté Expérience).
4. Coordonnées en WGS 84, ordre GeoJSON `[longitude, latitude]` ; boîtes normalisées de 0 à 1
   (coin haut gauche, largeur, hauteur) dans la photo remise à l'endroit.

## État de la synchronisation avec le code

Aujourd'hui, `apps/mobile` recopie les classes à la main (`WasteClass.kt` et `strings.xml`) et la version du modèle
(`WasteDetector.MODEL_VERSION`). La génération de ce code à partir de `classes.json` par une tâche Gradle est prévue.
Jusque-là, toute modification se fait aux deux endroits.
