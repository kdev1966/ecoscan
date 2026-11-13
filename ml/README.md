# ml — le modèle de détection

Entraînement, export, évaluation et test terrain du modèle embarqué dans `apps/mobile`.
Le jeu de données et les poids restent **hors du dépôt**, dans `tunmed/` à côté d'`ecoscan/`.

## Où sont les données

| Chemin | Contenu |
|---|---|
| `tunmed/base/base non augmentée/base1/` | TUN-MED-MAR-UAV-LITTER : 2 946 images d'entraînement, 551 de validation, 179 de test ; `data.yaml` |
| `tunmed/base/base augmentée/base2/` | Même jeu, entraînement augmenté à 9 667 images (validation et test identiques) |
| `tunmed/base/README.pdf` | Description du jeu de données |
| `tunmed/models/best.pt`, `last.pt` | Poids YOLOv8n après 30 époques (entraînés sur Kaggle) |
| `tunmed/models/best.onnx` | Export ONNX (PWA de la V3, outils) |
| `tunmed/models/best_saved_model/` | Export TensorFlow : `best_float32.tflite` (celui de l'application), `best_float16.tflite` |

Jeu de données : *TUN-MED-MAR-UAV-LITTER*, Moataz Harrazi et al., ENSIT / RIFTSI, licence CC BY 4.0
(voir `NOTICE.md` à la racine). Le `data.yaml` écrit la classe 0 « Cardboar » : c'est une faute de frappe de la
source, la classe est bien le carton (`packages/contracts/v1/classes.json`).

## Environnement

Environnement conda `yolo-export` : Python 3.11, ultralytics 8.4.203, torch 2.2.2, onnx 1.16.2, onnx2tf 1.26.3,
tensorflow 2.16.2, onnxruntime 1.19.2.

```bash
conda activate yolo-export
cd ~/Desktop/tunmed
```

## Entraînement

Fait sur Kaggle ; la commande exacte n'a pas été conservée. Équivalent avec les réglages connus (YOLOv8n, 30 époques,
640 px, base non augmentée) — **à confirmer au prochain entraînement** :

```bash
yolo detect train model=yolov8n.pt data="base/base non augmentée/base1/data.yaml" epochs=30 imgsz=640
```

## Export

Ultralytics passe par ONNX puis onnx2tf, et écrit `best_saved_model/` avec les variantes float32 et float16 :

```bash
yolo export model=models/best.pt format=onnx imgsz=640
yolo export model=models/best.pt format=tflite imgsz=640
```

Puis, pour l'application :

```bash
cp models/best_saved_model/best_float32.tflite \
   ecoscan/apps/mobile/app/src/main/assets/ecoscan_detector_v1.tflite
shasum -a 256 ecoscan/apps/mobile/app/src/main/assets/ecoscan_detector_v1.tflite
```

Un nouveau modèle exige de mettre à jour `packages/contracts/v1/model.json` (version, taille, empreinte, mesures) et
`WasteDetector.MODEL_VERSION` dans l'application, puis de relancer `WasteDetectorDeviceTest` sur le téléphone.

## Évaluation

```bash
yolo detect val model=models/best.pt data="base/base non augmentée/base1/data.yaml" split=test imgsz=640
```

Résultat du 6 novembre 2026 (179 images, 360 objets) : précision 0,859, rappel 0,917, mAP50 0,933, mAP50-95 0,835.
Détail par classe dans `docs/PRD.md` § 4.4. `best.onnx` donne mAP50 0,928 ; `best_float32.tflite` donne les mêmes
sorties qu'ONNX.

**Ces scores valent pour des vues de drone.**

## À faire

- **Test terrain** : 50 à 100 photos prises au téléphone sur une plage, annotées à la main, pour mesurer l'écart avec
  les scores drone. C'est le critère qui décide si la V1 est publiable (`docs/PRD.md` § 4.4).
- Export 320 px pour l'aperçu en direct (cible ≥ 5 images/s).
- Comparer float16 et int8 (taille, latence, précision).
- Garder ici les scripts d'entraînement et d'évaluation dès qu'ils existent, pour que chaque modèle soit reproductible.
