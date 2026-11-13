package com.kdev66.ecoscan.ui.scanner

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import com.kdev66.ecoscan.R
import com.kdev66.ecoscan.camera.toUprightBitmap
import com.kdev66.ecoscan.detection.DetectionResult
import com.kdev66.ecoscan.detection.WasteDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Photo capturée et son analyse (la photo est réduite à 1 600 px au plus, cf. PRD § 6). */
data class CapturedAnalysis(val photo: Bitmap, val result: DetectionResult)

data class ScannerUiState(
    /** Dernière analyse de l'aperçu : sert à viser, pas au comptage. */
    val live: DetectionResult? = null,
    val capturing: Boolean = false,
    val captured: CapturedAnalysis? = null,
    @StringRes val error: Int? = null,
)

/**
 * État du scanner. Partagé entre l'écran Scanner et l'écran Résultat.
 * Toute l'inférence passe par un seul thread ([analysisExecutor]) : le détecteur n'est pas réentrant.
 */
class ScannerViewModel(private val detectorProvider: () -> WasteDetector) : ViewModel() {

    val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private val detector: WasteDetector? by lazy {
        try {
            detectorProvider()
        } catch (e: Exception) {
            Log.e(TAG, "chargement du modèle impossible", e)
            _state.update { it.copy(error = R.string.error_model) }
            null
        }
    }

    /** Appelé par CameraX sur [analysisExecutor], une image à la fois. */
    fun analyzeFrame(image: ImageProxy) {
        image.use {
            val current = _state.value
            if (current.capturing || current.captured != null) return
            val d = detector ?: return
            val result = d.detect(it.toUprightBitmap())
            Log.d(TAG, "aperçu ${result.imageWidth}x${result.imageHeight} : ${result.timings}")
            _state.update { s -> if (s.capturing || s.captured != null) s else s.copy(live = result) }
        }
    }

    fun capture(imageCapture: ImageCapture) {
        // Ne pas toucher à `detector` ici : son premier accès charge le modèle, ce qui doit rester hors du thread principal.
        if (_state.value.capturing || _state.value.error == R.string.error_model) return
        _state.update { it.copy(capturing = true) }
        imageCapture.takePicture(analysisExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val photo = image.use { it.toUprightBitmap(maxSide = PHOTO_MAX_SIDE) }
                    val d = detector ?: run { _state.update { it.copy(capturing = false) }; return }
                    val result = d.detect(photo)
                    _state.update { it.copy(capturing = false, captured = CapturedAnalysis(photo, result)) }
                } catch (e: Exception) {
                    Log.e(TAG, "analyse de la photo impossible", e)
                    _state.update { it.copy(capturing = false, error = R.string.error_camera) }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "capture impossible", exception)
                _state.update { it.copy(capturing = false, error = R.string.error_camera) }
            }
        })
    }

    fun onCameraError(e: Throwable) {
        Log.e(TAG, "caméra indisponible", e)
        _state.update { it.copy(error = R.string.error_camera) }
    }

    /** Retour au scanner après un résultat. */
    fun newScan() = _state.update { it.copy(captured = null, live = null) }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        analysisExecutor.shutdown()
    }

    private companion object {
        const val TAG = "EcoScan"
        const val PHOTO_MAX_SIDE = 1600
    }
}
