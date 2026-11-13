package com.kdev66.ecoscan

import android.app.Application
import android.content.Context
import com.kdev66.ecoscan.detection.WasteDetector

class EcoScanApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Dépendances partagées de l'application (injection manuelle, comme AuraNote). */
class AppContainer(private val context: Context) {
    /**
     * Chargé au premier usage, sur l'exécuteur d'analyse (jamais sur le thread principal) : jusqu'à 5 s sur GPU
     * au tout premier lancement, beaucoup moins ensuite grâce au cache.
     */
    val detector by lazy { WasteDetector.createBest(context) }
}

val Context.appContainer: AppContainer get() = (applicationContext as EcoScanApp).container
