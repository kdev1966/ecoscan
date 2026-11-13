package com.kdev66.ecoscan

import android.app.Application
import android.content.Context
import android.util.Log
import com.kdev66.ecoscan.detection.WasteDetector
import com.kdev66.ecoscan.location.LocationTracker
import com.kdev66.ecoscan.report.data.EcoScanDatabase
import com.kdev66.ecoscan.report.data.PhotoStore
import com.kdev66.ecoscan.report.data.ReportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class EcoScanApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Photos laissées par une capture interrompue avant l'enregistrement en base.
        container.applicationScope.launch {
            runCatching { container.reports.deleteOrphanPhotos() }
                .onSuccess { if (it > 0) Log.i("EcoScan", "$it photo(s) orpheline(s) supprimée(s)") }
                .onFailure { Log.w("EcoScan", "nettoyage des photos impossible", it) }
        }
    }
}

/** Dépendances partagées de l'application (injection manuelle, comme AuraNote). */
class AppContainer(private val context: Context) {
    /** Travaux qui doivent survivre aux écrans : recherche de position, nettoyage. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Chargé au premier usage, sur l'exécuteur d'analyse (jamais sur le thread principal) : jusqu'à 5 s sur GPU
     * au tout premier lancement, beaucoup moins ensuite grâce au cache.
     */
    val detector by lazy { WasteDetector.createBest(context) }

    private val database by lazy { EcoScanDatabase.create(context) }

    val reports by lazy { ReportRepository(database, PhotoStore(File(context.filesDir, "photos"))) }

    val location by lazy { LocationTracker(context, reports, applicationScope) }
}

val Context.appContainer: AppContainer get() = (applicationContext as EcoScanApp).container
