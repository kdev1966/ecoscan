package com.kdev66.ecoscan.detection

import kotlin.math.max
import kotlin.math.min

/** Boîte normalisée entre 0 et 1 dans le repère de l'image analysée (image remise à l'endroit). */
data class BoundingBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = max(0f, width) * max(0f, height)

    /** Intersection sur union. Invariante à l'échelle de chaque axe : on peut la calculer en coordonnées normalisées. */
    fun iou(other: BoundingBox): Float {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        if (w <= 0f || h <= 0f) return 0f
        val inter = w * h
        val union = area + other.area - inter
        return if (union <= 0f) 0f else inter / union
    }
}

/** Un objet détecté : indice de classe du modèle (voir [WasteClass]), score de confiance et boîte. */
data class Detection(val classIndex: Int, val confidence: Float, val box: BoundingBox) {
    val wasteClass: WasteClass get() = WasteClass.fromIndex(classIndex)
}

/** Durées d'une analyse, en millisecondes : préparation de l'image, inférence du modèle, post-traitement. */
data class DetectionTimings(val preprocessMs: Long, val inferenceMs: Long, val postprocessMs: Long) {
    val totalMs: Long get() = preprocessMs + inferenceMs + postprocessMs
}

/** Résultat d'une analyse : les détections, la taille de l'image analysée et les durées. */
data class DetectionResult(
    val detections: List<Detection>,
    val imageWidth: Int,
    val imageHeight: Int,
    val timings: DetectionTimings,
) {
    val durationMs: Long get() = timings.totalMs

    /** Nombre d'objets par classe, dans l'ordre des classes, sans les classes absentes. */
    fun countsByClass(): Map<WasteClass, Int> =
        detections.groupingBy { it.wasteClass }.eachCount().toSortedMap(compareBy { it.ordinal })
}
