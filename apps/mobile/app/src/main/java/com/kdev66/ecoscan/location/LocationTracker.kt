package com.kdev66.ecoscan.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.kdev66.ecoscan.report.Position
import com.kdev66.ecoscan.report.data.ReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cherche la position d'un signalement et la complète en base dès qu'elle arrive (PRD § F3).
 *
 * Tourne dans la portée de l'application : la recherche continue pendant la revue et après l'enregistrement.
 * Une position n'est acceptée que dans les [MAX_DELAY_MS] qui suivent la capture : au-delà, l'utilisateur a pu
 * changer de lieu. Le GPS fonctionne sans réseau ; sans Google Play services, la position reste inconnue.
 */
class LocationTracker(
    private val context: Context,
    private val repository: ReportRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    private val _locating = MutableStateFlow<Set<String>>(emptySet())
    /** Signalements dont la position est en cours de recherche. */
    val locating: StateFlow<Set<String>> = _locating.asStateFlow()

    fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Peut-on encore chercher la position d'un signalement créé à [createdAtMs] ? */
    fun withinWindow(createdAtMs: Long): Boolean = clock() - createdAtMs < MAX_DELAY_MS

    /** Lance la recherche, sauf si elle est déjà en cours, sans permission ou hors délai. */
    @SuppressLint("MissingPermission") // vérifiée par hasPermission()
    fun locate(reportId: String, createdAtMs: Long) {
        if (!hasPermission() || !withinWindow(createdAtMs)) return
        while (true) {
            val current = _locating.value
            if (reportId in current) return
            if (_locating.compareAndSet(current, current + reportId)) break
        }

        scope.launch {
            try {
                val remaining = MAX_DELAY_MS - (clock() - createdAtMs)
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(MAX_FIX_AGE_MS)
                    .setDurationMillis(remaining.coerceIn(1_000L, SEARCH_DURATION_MS))
                    .build()
                val token = CancellationTokenSource()
                val location = withTimeoutOrNull(SEARCH_DURATION_MS + 5_000L) {
                    try {
                        client.getCurrentLocation(request, token.token).await()
                    } finally {
                        token.cancel()
                    }
                }
                if (location != null) {
                    val accuracy = if (location.hasAccuracy()) location.accuracy else null
                    repository.setPositionIfMissing(reportId, Position(location.latitude, location.longitude, accuracy))
                }
            } catch (e: Exception) {
                Log.w(TAG, "position indisponible pour $reportId", e)
            } finally {
                _locating.update { it - reportId }
            }
        }
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        /** Délai après la capture au-delà duquel une position n'est plus attribuée au signalement. */
        const val MAX_DELAY_MS = 10 * 60_000L
        private const val SEARCH_DURATION_MS = 2 * 60_000L
        /** Une position mise en cache par le système est acceptée si elle a moins de 30 s. */
        private const val MAX_FIX_AGE_MS = 30_000L
        private const val TAG = "EcoScan"
    }
}
