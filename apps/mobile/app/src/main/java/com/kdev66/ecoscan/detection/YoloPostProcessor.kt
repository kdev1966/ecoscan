package com.kdev66.ecoscan.detection

import kotlin.math.min

/**
 * Placement d'une image dans le carré d'entrée du modèle (« letterbox ») : mise à l'échelle sans déformation,
 * puis centrage avec des bandes grises. C'est le prétraitement utilisé à l'entraînement par Ultralytics.
 */
data class Letterbox(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    companion object {
        fun compute(sourceWidth: Int, sourceHeight: Int, inputSize: Int): Letterbox {
            require(sourceWidth > 0 && sourceHeight > 0) { "image vide" }
            val scale = min(inputSize.toFloat() / sourceWidth, inputSize.toFloat() / sourceHeight)
            return Letterbox(
                scale = scale,
                padX = (inputSize - sourceWidth * scale) / 2f,
                padY = (inputSize - sourceHeight * scale) / 2f,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
            )
        }
    }
}

/**
 * Décodage de la sortie YOLOv8 (détection) puis suppression des doublons (NMS par classe).
 *
 * Sortie attendue, aplatie « canal d'abord » : `[4 + numClasses][numAnchors]`. Les 4 premières lignes sont
 * cx, cy, largeur, hauteur **en pixels du carré d'entrée** ; les suivantes, un score par classe.
 * Valeurs par défaut identiques à `model.predict()` d'Ultralytics : conf 0,25, IoU 0,7, 300 détections au plus.
 */
class YoloPostProcessor(
    private val numClasses: Int,
    private val numAnchors: Int,
    val confidenceThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.7f,
    private val maxDetections: Int = 300,
) {
    fun process(output: FloatArray, letterbox: Letterbox): List<Detection> {
        require(output.size == (4 + numClasses) * numAnchors) {
            "sortie de taille ${output.size}, attendu ${(4 + numClasses) * numAnchors}"
        }
        val candidates = ArrayList<Detection>()
        for (a in 0 until numAnchors) {
            var bestClass = -1
            var bestScore = confidenceThreshold
            for (c in 0 until numClasses) {
                val score = output[(4 + c) * numAnchors + a]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }
            if (bestClass < 0) continue

            val cx = output[a]
            val cy = output[numAnchors + a]
            val halfW = output[2 * numAnchors + a] / 2f
            val halfH = output[3 * numAnchors + a] / 2f
            val box = toSource(cx - halfW, cy - halfH, cx + halfW, cy + halfH, letterbox)
            if (box.area > 0f) candidates += Detection(bestClass, bestScore, box)
        }
        return nonMaxSuppression(candidates)
    }

    /** Ramène une boîte du carré d'entrée vers l'image d'origine, normalisée et bornée à [0, 1]. */
    private fun toSource(x1: Float, y1: Float, x2: Float, y2: Float, lb: Letterbox): BoundingBox {
        fun nx(x: Float) = ((x - lb.padX) / lb.scale / lb.sourceWidth).coerceIn(0f, 1f)
        fun ny(y: Float) = ((y - lb.padY) / lb.scale / lb.sourceHeight).coerceIn(0f, 1f)
        return BoundingBox(nx(x1), ny(y1), nx(x2), ny(y2))
    }

    internal fun nonMaxSuppression(candidates: List<Detection>): List<Detection> {
        val kept = ArrayList<Detection>()
        for (d in candidates.sortedByDescending { it.confidence }) {
            if (kept.size >= maxDetections) break
            val overlaps = kept.any { it.classIndex == d.classIndex && it.box.iou(d.box) > iouThreshold }
            if (!overlaps) kept += d
        }
        return kept
    }
}
