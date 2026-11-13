package com.kdev66.ecoscan.report.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Insert
    suspend fun insertReport(report: ReportEntity)

    @Insert
    suspend fun insertDetections(detections: List<DetectionEntity>)

    @Insert
    suspend fun insertCounts(counts: List<CategoryCountEntity>)

    @Update
    suspend fun updateReport(report: ReportEntity)

    @Update
    suspend fun updateDetections(detections: List<DetectionEntity>)

    @Query("DELETE FROM category_count WHERE reportId = :reportId")
    suspend fun deleteCounts(reportId: String)

    @Query("SELECT * FROM report WHERE id = :id")
    suspend fun report(id: String): ReportEntity?

    @Query("SELECT * FROM detection WHERE reportId = :reportId ORDER BY id")
    suspend fun detections(reportId: String): List<DetectionEntity>

    @Query("SELECT * FROM category_count WHERE reportId = :reportId ORDER BY classIndex")
    suspend fun counts(reportId: String): List<CategoryCountEntity>

    @Query("SELECT * FROM report WHERE id = :id")
    fun observeReport(id: String): Flow<ReportEntity?>

    @Query("SELECT * FROM detection WHERE reportId = :reportId ORDER BY id")
    fun observeDetections(reportId: String): Flow<List<DetectionEntity>>

    @Query("SELECT * FROM category_count WHERE reportId = :reportId ORDER BY classIndex")
    fun observeCounts(reportId: String): Flow<List<CategoryCountEntity>>

    /** Signalements enregistrés, du plus récent au plus ancien (l'historique ne montre pas les brouillons). */
    @Query("SELECT * FROM report WHERE status = 'SAVED' ORDER BY createdAtMs DESC")
    fun observeSavedReports(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM report WHERE status = 'SAVED' ORDER BY createdAtMs DESC")
    suspend fun savedReports(): List<ReportEntity>

    @Query("SELECT * FROM category_count")
    fun observeAllCounts(): Flow<List<CategoryCountEntity>>

    @Query("SELECT id FROM report WHERE status = 'DRAFT' ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun latestDraftId(): String?

    /** Complète la position d'un signalement qui n'en a pas encore ; ne remplace jamais une position connue. */
    @Query(
        "UPDATE report SET latitude = :latitude, longitude = :longitude, accuracyM = :accuracyM " +
            "WHERE id = :id AND latitude IS NULL",
    )
    suspend fun setPositionIfMissing(id: String, latitude: Double, longitude: Double, accuracyM: Float?): Int

    @Query("DELETE FROM report WHERE id = :id")
    suspend fun deleteReport(id: String)

    @Query("SELECT photoFile FROM report")
    suspend fun photoFiles(): List<String>
}
