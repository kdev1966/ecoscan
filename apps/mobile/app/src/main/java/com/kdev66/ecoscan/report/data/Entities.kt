package com.kdev66.ecoscan.report.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Table des signalements. `syncState` vaut `LOCAL` en V1 ; il servira à la synchronisation de la V2. */
@Entity(tableName = "report", indices = [Index("status")])
data class ReportEntity(
    @PrimaryKey val id: String,
    val createdAtMs: Long,
    val utcOffsetMinutes: Int,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Float?,
    val context: String,
    val comment: String?,
    val photoFile: String,
    val modelVersion: String,
    val status: String,
    val syncState: String = SYNC_LOCAL,
) {
    companion object {
        const val SYNC_LOCAL = "LOCAL"
    }
}

/** Une boîte. Coordonnées normalisées de 0 à 1 : coin haut gauche, largeur, hauteur (comme le contrat). */
@Entity(
    tableName = "detection",
    foreignKeys = [ForeignKey(ReportEntity::class, ["id"], ["reportId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reportId")],
)
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: String,
    val source: String,
    val classIndex: Int,
    val modelClassIndex: Int?,
    val confidence: Float?,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val deleted: Boolean,
)

/** Quantité validée d'une classe pour un signalement. */
@Entity(
    tableName = "category_count",
    primaryKeys = ["reportId", "classIndex"],
    foreignKeys = [ForeignKey(ReportEntity::class, ["id"], ["reportId"], onDelete = ForeignKey.CASCADE)],
)
data class CategoryCountEntity(
    val reportId: String,
    val classIndex: Int,
    val count: Int,
)
