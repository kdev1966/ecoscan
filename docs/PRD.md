# EcoScan — Product Requirements Document

> Version 2.0 — révision du PRD initial (généré par Gemini), après analyse du modèle et du jeu de données.
> Statut : **brouillon à valider avant développement.**

---

## 1. Vue d'ensemble

**EcoScan** est une application mobile Android open source qui détecte, classe et géolocalise les déchets
sur le littoral à l'aide d'un modèle d'IA exécuté **sur le téléphone, sans connexion Internet**.

**Objectif** : permettre à un citoyen, un bénévole ou une association de documenter un point de pollution
en quelques secondes, même sans réseau, et de constituer des données exploitables pour organiser les
campagnes de nettoyage.

**Zone cible de la V1** : plages et littoral méditerranéen tunisien.
Les zones forestières et urbaines ne sont pas couvertes par les données d'entraînement (voir § 4.3) ;
elles sont reportées à une version ultérieure, après collecte de nouvelles images.

## 2. Périmètre et phases

| Phase | Contenu | Plateforme |
|---|---|---|
| **V1 — Mobile hors ligne** (ce document) | Scanner, revue des détections, signalement géolocalisé, historique local, export des données | Android (Kotlin) |
| V1.1 — IA générative embarquée | LLM local (llama.cpp, même architecture qu'AuraNote) : usage à définir (rédaction du signalement, questions sur l'historique). Modèle livré à part (Play Asset Delivery), repli sans LLM sur les téléphones à moins de 3 Go de RAM | Android |
| V2 — Serveur | API, base PostgreSQL/PostGIS, synchronisation des signalements, carte web des points chauds | Backend + web |
| V3 — PWA | Version web installable, inférence dans le navigateur avec le modèle ONNX | Web |

Tout ce qui concerne la V2 et la V3 est indicatif ; ces phases auront leur propre PRD.
La V1 est cependant conçue pour faciliter la V2 (identifiants UUID, état de synchronisation stocké dès la V1).

## 3. Utilisateurs cibles

| Persona | Besoin principal en V1 |
|---|---|
| **Citoyen / bénévole** | Signaler rapidement un amas de déchets lors d'une sortie à la plage |
| **Responsable associatif (clean-up)** | Documenter une zone avant/après ramassage et exporter les relevés pour son équipe |
| **Collectivité / chercheur** | Récupérer des relevés géolocalisés et horodatés (via export en V1, via la carte en V2) |

## 4. Modèle d'IA

### 4.1 Caractéristiques (mesurées sur `models/best.pt`)

| Élément | Valeur |
|---|---|
| Architecture | YOLOv8n (Ultralytics), tâche de **détection** (boîtes englobantes) |
| Taille d'entrée | 640 × 640 px |
| Entraînement | 30 époques, entraîné par l'auteur du projet sur Kaggle |
| Classes (7) | `Cardboard`, `Fabrics`, `Glass`, `Metal`, `Other`, `Plastic`, `Wood` |
| Formats livrés | `best.tflite` (application Android), `best.onnx` (PWA en V3, outils) |

Les noms affichés dans l'application seront traduits (Carton, Textile, Verre, Métal, Autre, Plastique, Bois) ;
l'ordre des indices du modèle ci-dessus fait référence.

### 4.2 Jeu de données

**TUN-MED-MAR-UAV-LITTER** (*Tunisian Mediterranean Coastal Marine Litter Dataset: Drone-Based Real-World
Images for Detection, Classification and Segmentation*), publié sur Kaggle par Moataz Harrazi
(laboratoire RIFTSI, ENSIT — projet dirigé par le Pr Hassene Seddik, en partenariat avec l'INSTM et l'UVT).
Licence **CC BY 4.0**.

- Images aériennes prises **par drone, en vue verticale**, sur les plages du gouvernorat de **Mahdia**.
- Annotations en polygones (format YOLO segmentation), utilisables pour la détection et la segmentation.
- Base non augmentée : 2 946 images d'entraînement, 551 de validation, 179 de test.
  Base augmentée : 9 667 images d'entraînement (validation et test identiques).

Répartition des objets annotés (entraînement, base non augmentée) :

| Plastic | Fabrics | Other | Wood | Cardboard | Glass | Metal |
|---|---|---|---|---|---|---|
| 1 548 | 720 | 593 | 207 | 188 | 175 | **73** |

### 4.3 Limites connues — à refléter dans le produit

1. **Écart de domaine** : le modèle a appris sur des vues de drone au-dessus du sable. Il est attendu
   raisonnable pour une photo prise **téléphone pointé vers le sol sur une plage**, dégradé pour une vue
   horizontale ou lointaine, et peu fiable en forêt ou en ville.
   → L'application guide la prise de vue (« tenez le téléphone au-dessus des déchets »).
   → Si le contexte choisi est Forêt ou Ville, la détection est signalée comme **expérimentale**.
2. **Déséquilibre des classes** : `Metal` (73 exemples) et, dans une moindre mesure, `Glass` et `Cardboard`
   seront moins bien reconnus.
3. **Comptage** : sur un amas, les objets se chevauchent. Les quantités affichées sont des **estimations
   corrigeables** par l'utilisateur, pas des mesures.

### 4.4 Évaluation

**Mesuré le 6 novembre 2026** sur le jeu de test de la base non augmentée (179 images, 360 objets, vues drone) :

| Classe | Objets | Précision | Rappel | mAP50 | mAP50-95 |
|---|---|---|---|---|---|
| **Toutes** | 360 | 0,859 | 0,917 | 0,933 | 0,835 |
| Plastic | 186 | 0,932 | 0,989 | 0,980 | 0,863 |
| Other | 37 | 0,654 | 0,730 | 0,766 | 0,620 |
| Fabrics | 35 | 0,885 | 0,883 | 0,911 | 0,775 |
| Metal | 28 | 0,901 | 0,976 | 0,992 | 0,903 |
| Glass | 27 | 0,971 | 1,000 | 0,995 | 0,938 |
| Wood | 19 | 0,742 | 0,895 | 0,909 | 0,822 |
| Cardboard | **0** | — | — | — | — |

Remarques :
- **Cardboard n'a aucun exemple dans le jeu de test** : sa qualité n'est pas mesurée.
- Les effectifs par classe sont petits (19 à 37 objets hors Plastic) : ces chiffres sont indicatifs.
- `Other` est la classe la plus faible ; c'est attendu pour une catégorie hétérogène.
- Ces scores valent pour des **vues de drone**. Ils ne disent rien des photos prises au téléphone.

Conversion (vérifiée) : `best.onnx` donne mAP50 0,928 sur le même test. `best_float32.tflite` produit les mêmes
sorties qu'ONNX ; `best_float16.tflite` s'en écarte au plus de 0,014 sur les scores, avec 100 % d'accord sur les
détections au seuil 0,25 (30 images testées).

Reste à faire avant la V1 :
- **Test terrain** : 50 à 100 photos prises au téléphone sur une plage, annotées manuellement, pour mesurer
  l'écart avec les métriques drone. C'est le critère qui décide si la V1 est publiable.
- Comparaison des variantes TFLite (float32 / float16 / int8) : taille, latence, perte de précision.

## 5. Fonctionnalités de la V1

### F1 — Scanner

- Aperçu caméra plein écran (CameraX) avec **boîtes englobantes en direct**, à cadence réduite, pour aider
  à cadrer. Ce flux ne sert pas au comptage (il compterait plusieurs fois les mêmes objets).
- Bouton de capture : la photo capturée est analysée une fois, en pleine résolution du modèle (640 px).
- Compteur par catégorie affiché sur l'aperçu.
- Consigne de prise de vue visible à l'ouverture (désactivable).

### F2 — Revue des détections

- Affichage de la photo avec les boîtes, la classe et le score de confiance.
- L'utilisateur peut **supprimer** une détection erronée, **changer sa classe**, ou **ajuster les quantités**
  par catégorie (pour les objets non détectés).
- Les détections brutes du modèle et les corrections sont conservées séparément (utile pour réentraîner).

### F3 — Signalement

Enregistré automatiquement :
- photo (compressée, métadonnées EXIF de l'appareil retirées) ;
- position GPS et précision (en mètres) ; si la position n'est pas encore disponible, le signalement est
  enregistré et la position complétée dès qu'elle arrive ;
- date et heure ;
- résultats de l'IA (détections brutes) et quantités validées par l'utilisateur ;
- version du modèle utilisée.

Saisi par l'utilisateur :
- contexte du lieu : **Plage** (par défaut), Littoral rocheux, Forêt, Ville ;
- commentaire libre (facultatif).

**Parcours cible : 3 actions** depuis l'ouverture — capturer, (corriger si besoin), enregistrer.

### F4 — Historique local

- Liste des signalements (miniature, date, total de déchets, contexte), du plus récent au plus ancien.
- Fiche détaillée : photo annotée, quantités, coordonnées, lien « ouvrir dans une application de cartes ».
- Suppression d'un signalement.
- Pas de carte intégrée en V1 (les fonds de carte demandent une connexion ou un stockage hors ligne lourd).

### F5 — Export

- Export de tout ou partie de l'historique en **GeoJSON** et **CSV**, avec les photos dans une archive ZIP.
- Partage via le menu Android (e-mail, Drive, messagerie…).
- Le format GeoJSON sert aussi de format d'import pour la V2.

### F6 — Réglages et à propos

- Langue : français, arabe (interface de droite à gauche), anglais.
- Seuil de confiance des détections (valeur par défaut fixée après l'évaluation § 4.4).
- Page **Crédits et licences** (voir § 8).
- Politique de confidentialité.

## 6. Exigences non fonctionnelles

| Domaine | Exigence |
|---|---|
| Hors ligne | 100 % des fonctions V1 utilisables sans réseau. Aucune donnée envoyée hors du téléphone, sauf export volontaire. |
| Fiabilité des données | Aucune perte : la photo est écrite sur le disque avant l'enregistrement en base, dans une même opération réversible ; un signalement interrompu (application tuée) est récupéré au redémarrage. |
| Appareil de référence | **Xiaomi Redmi Note 8** : Snapdragon 665, GPU Adreno 610, 3,6 Go de RAM, Android 11 (API 30), écran 1080 × 2340 |
| Performance | Latence d'inférence sur la photo capturée **≤ 500 ms** sur l'appareil de référence ; aperçu en direct ≥ 5 images/s. Valeurs à confirmer par mesure. |
| Taille | APK ≤ 30 Mo (modèle inclus). |
| Stockage | Photos compressées (JPEG, côté long ≤ 1 600 px). Espace utilisé affiché dans les réglages. |
| Confidentialité | Permissions caméra et localisation demandées au moment de l'usage, avec explication. Les photos peuvent contenir des personnes : avertissement à la première capture. Aucune analyse ni publicité. |
| Accessibilité | Contrastes suffisants en plein soleil, cibles tactiles ≥ 48 dp, libellés pour TalkBack. |
| Compatibilité | Android 8.0 (API 26) minimum ; processeurs arm64-v8a. |

## 7. Architecture technique (V1)

| Couche | Choix |
|---|---|
| Langage / UI | Kotlin, Jetpack Compose |
| Caméra | CameraX (Preview + ImageAnalysis + ImageCapture) |
| Inférence | LiteRT 1.4.2 (ex-TensorFlow Lite) : **GPU** par défaut (cache des programmes compilés, mode « vitesse soutenue »), repli sur le CPU générique 4 threads. Mesures dans l'application : § 11 |
| Modèle embarqué | `best_float32.tflite` (12,7 Mo), copié dans les assets sous `ecoscan_detector_v1.tflite`. Entrée `[1, 640, 640, 3]` float32, RVB normalisé 0–1. Sortie `[1, 11, 8400]` : 4 coordonnées (cx, cy, l, h) + 7 scores de classe pour 8 400 ancres |
| Post-traitement | Décodage de la sortie, seuil de confiance et NMS dans l'application |
| Base locale | Room (SQLite) |
| Localisation | Fused Location Provider (Google Play services) |
| Photos | Stockage interne de l'application |
| Tâches de fond | WorkManager (préparé pour la synchronisation V2) |

Modèle de données (simplifié) :

- `Report` : `id` (UUID), `createdAt`, `latitude`, `longitude`, `accuracyM`, `context`, `comment`,
  `photoPath`, `modelVersion`, `syncState` (`LOCAL` en V1).
- `Detection` : `reportId`, `classId`, `confidence`, `box` (x, y, l, h normalisés), `source`
  (`MODEL` / `USER`), `deleted`.
- `CategoryCount` : `reportId`, `classId`, `count` (quantité validée par l'utilisateur).

## 8. Licences et crédits

- **Code de l'application** : AGPL-3.0. Ce choix est cohérent avec Ultralytics YOLOv8 (AGPL-3.0), utilisé
  pour entraîner et exporter le modèle.
- **Jeu de données** : TUN-MED-MAR-UAV-LITTER, Moataz Harrazi et al., ENSIT/RIFTSI — CC BY 4.0.
  Attribution obligatoire dans l'application, le README et la fiche du Play Store, avec lien vers la source
  et mention de la licence.
- **Modèle** : entraîné par l'auteur d'EcoScan à partir de ce jeu de données ; EcoScan n'est pas affilié
  à l'équipe RIFTSI.

## 9. Critères de succès de la V1

| Critère | Cible |
|---|---|
| Qualité IA (test terrain § 4.4) | Seuil à fixer après la première mesure |
| Latence | ≤ 500 ms sur l'appareil de référence |
| Parcours | ≤ 3 actions de l'ouverture à l'enregistrement |
| Fiabilité | 0 signalement perdu sur une série de tests (coupure, application tuée, batterie faible) |

## 10. Hors périmètre de la V1

LLM ou autre IA générative embarquée (V1.1), comptes utilisateurs, serveur et synchronisation, carte intégrée, tableau de bord, statut de nettoyage
des signalements, détection des doublons entre utilisateurs, iOS, PWA, détection en forêt et en ville.

## 11. Questions ouvertes

1. ~~Appareil de référence~~ → Redmi Note 8 (voir § 6). Latence mesurée le 6 novembre 2026 avec `benchmark_model`
   (TFLite officiel), entrée 640 px, moyenne de 30 passages :

   | Variante | CPU 4 threads (XNNPACK) | GPU (Adreno 610) | CPU 1 thread |
   |---|---|---|---|
   | float32 | **268 ms** | 330 ms (initialisation 4,3 s) | 943 ms |
   | float16 | 339 ms | 334 ms (initialisation 3,9 s) | — |

   **Mesuré dans l'application** (LiteRT 1.4.2 en Java, float32, médiane de 5 passages), le résultat diffère :

   | Moteur | Inférence | Initialisation + 1re analyse |
   |---|---|---|
   | **GPU** (cache + vitesse soutenue) | **238 ms** | 5 s au tout premier lancement, puis 0,5–0,6 s |
   | NNAPI | 556 ms | 1,7 s |
   | CPU générique, 4 threads | 645–697 ms | 0,7 s |
   | CPU XNNPACK | 925–985 ms (les 4 threads sont ignorés par l'API Java) | 1 s |

   → Choix retenu : **GPU, repli sur le CPU générique**. La cible ≤ 500 ms pour la photo est tenue (≈ 280 ms
   préparation comprise). L'aperçu tourne à environ 3,5 images/s : la cible de 5 images/s demanderait un export
   en 320 px pour l'aperçu seulement (à tester). LiteRT 2.x est écarté pour l'instant : il exige Kotlin 2.4.
2. ~~Version minimale d'Android~~ → Android 8.0 (API 26), comme AuraNote.
3. ~~Variante TFLite~~ → float32 sur CPU (mesuré, voir point 1). Taille d'entrée de l'aperçu en direct : à tester.
4. Seuil de confiance par défaut.
5. ~~Langues de la V1~~ → français, arabe (de droite à gauche) et anglais dès la V1.
