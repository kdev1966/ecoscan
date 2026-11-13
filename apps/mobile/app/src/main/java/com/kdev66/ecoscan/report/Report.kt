package com.kdev66.ecoscan.report

import com.kdev66.ecoscan.detection.BoundingBox
import com.kdev66.ecoscan.detection.WasteClass

/**
 * Un signalement, aligné sur `packages/contracts/v1/report.schema.json`.
 *
 * Il naît en [ReportStatus.DRAFT] dès la capture (photo écrite, détections du modèle enregistrées) et devient
 * [ReportStatus.SAVED] quand l'utilisateur l'enregistre. Un brouillon survit à l'arrêt de l'application.
 */
data class Report(
    val id: String,
    val createdAtMs: Long,
    /** Décalage horaire au moment de la capture, pour exporter une date avec son fuseau. */
    val utcOffsetMinutes: Int,
    val position: Position?,
    val context: ReportContext,
    val comment: String?,
    /** Nom du fichier de la photo dans le dossier des photos de l'application. */
    val photoFile: String,
    val modelVersion: String,
    val status: ReportStatus,
    val detections: List<ReviewedDetection>,
    /** Quantités validées par classe, sans les classes à zéro. Toujours ≥ au nombre de boîtes de la classe. */
    val counts: Map<WasteClass, Int>,
)

enum class ReportStatus { DRAFT, SAVED }

/** Position WGS 84 et précision horizontale en mètres. */
data class Position(val latitude: Double, val longitude: Double, val accuracyM: Float?)

/** Contexte du lieu. Les identifiants sont ceux du contrat ; forêt et ville sont hors des données d'entraînement. */
enum class ReportContext(val id: String, val experimental: Boolean) {
    BEACH("beach", false),
    ROCKY_COAST("rocky_coast", false),
    FOREST("forest", true),
    CITY("city", true);

    companion object {
        fun fromId(id: String): ReportContext = entries.first { it.id == id }
    }
}

enum class DetectionSource { MODEL, USER }

/**
 * Une boîte du signalement. Une boîte [DetectionSource.MODEL] garde la classe proposée par le modèle dans
 * [modelClass] et n'est jamais effacée, seulement marquée [deleted] : sortie du modèle et corrections restent
 * séparables pour le réentraînement.
 */
data class ReviewedDetection(
    val id: Long,
    val source: DetectionSource,
    val wasteClass: WasteClass,
    val modelClass: WasteClass?,
    val confidence: Float?,
    val box: BoundingBox,
    val deleted: Boolean,
) {
    val corrected: Boolean get() = deleted || (modelClass != null && modelClass != wasteClass)
}
