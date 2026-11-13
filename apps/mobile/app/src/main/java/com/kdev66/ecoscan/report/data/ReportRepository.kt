package com.kdev66.ecoscan.report.data

import android.graphics.Bitmap
import androidx.room.withTransaction
import com.kdev66.ecoscan.detection.DetectionResult
import com.kdev66.ecoscan.report.DetectionSource
import com.kdev66.ecoscan.report.Position
import com.kdev66.ecoscan.report.Report
import com.kdev66.ecoscan.report.ReportContext
import com.kdev66.ecoscan.report.ReportEdits
import com.kdev66.ecoscan.report.ReportStatus
import com.kdev66.ecoscan.report.ReviewedDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TimeZone
import java.util.UUID

/**
 * Les signalements : la base Room et les photos, toujours dans cet ordre — **la photo est écrite avant la ligne
 * en base**. Si l'enregistrement en base échoue, la photo est retirée ; si l'application est tuée entre les deux,
 * [deleteOrphanPhotos] la retire au démarrage suivant. Aucun signalement ne pointe donc vers une photo absente.
 */
class ReportRepository(
    private val db: EcoScanDatabase,
    private val photos: PhotoStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.reportDao()

    /** Crée le brouillon d'une capture : photo sur le disque, puis signalement, détections et quantités en base. */
    suspend fun createDraft(photo: Bitmap, result: DetectionResult, modelVersion: String): Report =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val now = clock()
            val photoFile = photos.write(id, photo)
            val detections = result.detections.map {
                ReviewedDetection(
                    id = 0,
                    source = DetectionSource.MODEL,
                    wasteClass = it.wasteClass,
                    modelClass = it.wasteClass,
                    confidence = it.confidence,
                    box = it.box,
                    deleted = false,
                )
            }
            val draft = Report(
                id = id,
                createdAtMs = now,
                utcOffsetMinutes = TimeZone.getDefault().getOffset(now) / 60_000,
                position = null,
                context = ReportContext.BEACH,
                comment = null,
                photoFile = photoFile,
                modelVersion = modelVersion,
                status = ReportStatus.DRAFT,
                detections = detections,
                counts = ReportEdits.initialCounts(detections),
            )
            try {
                db.withTransaction {
                    dao.insertReport(draft.toEntity())
                    dao.insertDetections(detections.map { it.toEntity(id) })
                    dao.insertCounts(draft.countEntities())
                }
            } catch (e: Exception) {
                photos.delete(photoFile)
                throw e
            }
            requireNotNull(load(id))
        }

    suspend fun load(id: String): Report? = withContext(Dispatchers.IO) {
        val entity = dao.report(id) ?: return@withContext null
        toReport(entity, dao.detections(id), dao.counts(id))
    }

    fun observe(id: String): Flow<Report?> =
        combine(dao.observeReport(id), dao.observeDetections(id), dao.observeCounts(id)) { r, d, c ->
            r?.let { toReport(it, d, c) }
        }

    suspend fun latestDraftId(): String? = withContext(Dispatchers.IO) { dao.latestDraftId() }

    /**
     * Applique une correction dans une transaction, à partir de l'état en base : deux corrections rapprochées
     * ne s'écrasent pas. Les boîtes ne sont jamais ajoutées ni retirées ici, seulement modifiées.
     */
    suspend fun edit(id: String, transform: (Report) -> Report) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current = dao.report(id)?.let { toReport(it, dao.detections(id), dao.counts(id)) }
                ?: return@withTransaction
            val updated = transform(current)
            require(updated.detections.map { it.id } == current.detections.map { it.id }) { "boîtes ajoutées ou retirées" }
            dao.updateReport(updated.toEntity().copy(status = current.status.name))
            dao.updateDetections(updated.detections.map { it.toEntity(id) })
            dao.deleteCounts(id)
            dao.insertCounts(updated.countEntities())
        }
    }

    suspend fun setPositionIfMissing(id: String, position: Position): Boolean = withContext(Dispatchers.IO) {
        dao.setPositionIfMissing(id, position.latitude, position.longitude, position.accuracyM) > 0
    }

    /** Enregistre le brouillon : il quitte l'état DRAFT et ne sera plus rouvert au démarrage. */
    suspend fun finalize(id: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val entity = dao.report(id) ?: return@withTransaction
            dao.updateReport(entity.copy(status = ReportStatus.SAVED.name))
        }
    }

    /** Abandonne un signalement : la ligne d'abord (les détections suivent), puis la photo. */
    suspend fun discard(id: String) = withContext(Dispatchers.IO) {
        val entity = dao.report(id) ?: return@withContext
        dao.deleteReport(id)
        photos.delete(entity.photoFile)
    }

    fun photoFile(report: Report): File = photos.file(report.photoFile)

    suspend fun deleteOrphanPhotos(): Int = withContext(Dispatchers.IO) {
        photos.deleteOrphans(dao.photoFiles().toSet(), clock(), ORPHAN_MIN_AGE_MS)
    }

    private companion object {
        /** Une photo sans signalement n'est retirée qu'après ce délai, pour ne jamais gêner une capture en cours. */
        const val ORPHAN_MIN_AGE_MS = 10 * 60_000L
    }
}
