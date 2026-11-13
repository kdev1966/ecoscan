package com.kdev66.ecoscan.ui.review

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdev66.ecoscan.detection.WasteClass
import com.kdev66.ecoscan.location.LocationTracker
import com.kdev66.ecoscan.report.Report
import com.kdev66.ecoscan.report.ReportContext
import com.kdev66.ecoscan.report.ReportEdits
import com.kdev66.ecoscan.report.data.ReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ReviewOutcome { SAVED, DISCARDED, MISSING }

enum class PositionStatus { KNOWN, NO_PERMISSION, SEARCHING, UNAVAILABLE }

data class ReviewUiState(
    val report: Report? = null,
    val photo: Bitmap? = null,
    val comment: String = "",
    val selectedId: Long? = null,
    val positionStatus: PositionStatus = PositionStatus.SEARCHING,
    /** La position peut encore être cherchée (délai depuis la capture non dépassé). */
    val canRetryPosition: Boolean = false,
    val busy: Boolean = false,
    val outcome: ReviewOutcome? = null,
)

/**
 * Revue d'un signalement. La base est la source de vérité : l'écran affiche ce qu'elle contient, et chaque
 * correction y est écrite aussitôt, dans l'ordre ([editLock]). Un brouillon interrompu est donc repris tel quel.
 * Seul le commentaire est tenu localement pendant la saisie et écrit après une courte pause.
 */
class ReviewViewModel(
    private val reportId: String,
    private val repository: ReportRepository,
    private val location: LocationTracker,
    /** Portée de l'application : termine l'écriture d'un commentaire après la fermeture de l'écran. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())
    private val permission = MutableStateFlow(location.hasPermission())
    private val editLock = Mutex()
    private var commentJob: Job? = null
    /** Saisie pas encore écrite en base. */
    @Volatile private var commentPending = false

    val state: StateFlow<ReviewUiState> = combine(
        repository.observe(reportId),
        local,
        location.locating,
        permission,
    ) { report, l, locating, granted ->
        val status = when {
            report?.position != null -> PositionStatus.KNOWN
            !granted -> PositionStatus.NO_PERMISSION
            reportId in locating -> PositionStatus.SEARCHING
            else -> PositionStatus.UNAVAILABLE
        }
        ReviewUiState(
            report = report,
            photo = l.photo,
            comment = l.comment,
            selectedId = l.selectedId?.takeIf { id -> report?.detections?.any { it.id == id && !it.deleted } == true },
            positionStatus = status,
            canRetryPosition = report != null && location.withinWindow(report.createdAtMs),
            busy = l.busy,
            outcome = l.outcome ?: if (l.loaded && report == null && !l.busy) ReviewOutcome.MISSING else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    init {
        viewModelScope.launch {
            val report = repository.load(reportId)
            if (report == null) {
                local.update { it.copy(loaded = true) }
                return@launch
            }
            local.update { it.copy(loaded = true, comment = report.comment.orEmpty()) }
            location.locate(report.id, report.createdAtMs)
            val photo = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(repository.photoFile(report).path)
            }
            if (photo == null) Log.w("EcoScan", "photo illisible : ${report.photoFile}")
            local.update { it.copy(photo = photo) }
        }
    }

    fun select(detectionId: Long?) = local.update { it.copy(selectedId = detectionId) }

    fun delete(detectionId: Long) {
        local.update { it.copy(selectedId = null) }
        edit { ReportEdits.delete(it, detectionId) }
    }

    fun restore(detectionId: Long) = edit { ReportEdits.restore(it, detectionId) }

    fun changeClass(detectionId: Long, wasteClass: WasteClass) {
        local.update { it.copy(selectedId = null) }
        edit { ReportEdits.changeClass(it, detectionId, wasteClass) }
    }

    fun setCount(wasteClass: WasteClass, count: Int) = edit { ReportEdits.setCount(it, wasteClass, count) }

    fun setContext(context: ReportContext) = edit { it.copy(context = context) }

    fun setComment(text: String) {
        local.update { it.copy(comment = text.take(COMMENT_MAX)) }
        commentPending = true
        commentJob?.cancel()
        commentJob = viewModelScope.launch {
            delay(COMMENT_DEBOUNCE_MS)
            saveComment()
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        permission.value = granted || location.hasPermission()
        retryPosition()
    }

    fun retryPosition() {
        val report = state.value.report ?: return
        location.locate(report.id, report.createdAtMs)
    }

    fun save() = finish(ReviewOutcome.SAVED) {
        commentJob?.cancel()
        saveComment()
        editLock.withLock { repository.finalize(reportId) }
    }

    fun discard() = finish(ReviewOutcome.DISCARDED) {
        commentJob?.cancel()
        editLock.withLock { repository.discard(reportId) }
    }

    private fun finish(outcome: ReviewOutcome, action: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                action()
                local.update { it.copy(outcome = outcome) }
            } catch (e: Exception) {
                Log.e("EcoScan", "fin de la revue impossible ($outcome)", e)
                local.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun saveComment() {
        commentPending = false
        val text = local.value.comment.trim().ifEmpty { null }
        editLock.withLock { repository.edit(reportId) { it.copy(comment = text) } }
    }

    private fun edit(transform: (Report) -> Report) {
        viewModelScope.launch {
            try {
                editLock.withLock { repository.edit(reportId, transform) }
            } catch (e: Exception) {
                Log.e("EcoScan", "correction impossible", e)
            }
        }
    }

    override fun onCleared() {
        // Une saisie en attente ne doit pas être perdue si l'écran se ferme pendant la pause
        // (viewModelScope est déjà annulé ici : on passe par la portée de l'application).
        if (commentPending && local.value.outcome == null) {
            appScope.launch { saveComment() }
        }
    }

    private data class LocalState(
        val loaded: Boolean = false,
        val photo: Bitmap? = null,
        val comment: String = "",
        val selectedId: Long? = null,
        val busy: Boolean = false,
        val outcome: ReviewOutcome? = null,
    )

    private companion object {
        const val COMMENT_DEBOUNCE_MS = 500L
        const val COMMENT_MAX = 2000
    }
}
