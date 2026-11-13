package com.kdev66.ecoscan.detection

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Vérifie, sur le téléphone, que le détecteur de l'application retrouve les détections d'Ultralytics.
 *
 * Image : ref_640x480.jpg, découpée dans une image du jeu de test TUN-MED-MAR-UAV-LITTER (non carrée, pour
 * éprouver le letterbox). Référence : `YOLO("best.pt").predict(imgsz=640, conf=0.25, iou=0.7)` le 6 novembre 2026.
 */
@RunWith(AndroidJUnit4::class)
class WasteDetectorDeviceTest {

    private data class Expected(val cls: Int, val conf: Float, val box: BoundingBox)

    private val reference = listOf(
        Expected(1, 0.921f, BoundingBox(0.0710f, 0.7191f, 0.1952f, 1.0000f)),
        Expected(3, 0.912f, BoundingBox(0.2132f, 0.3384f, 0.3886f, 0.4781f)),
        Expected(5, 0.903f, BoundingBox(0.4155f, 0.7824f, 0.5929f, 1.0000f)),
        Expected(5, 0.857f, BoundingBox(0.0000f, 0.4352f, 0.0764f, 0.5942f)),
        Expected(6, 0.843f, BoundingBox(0.6476f, 0.0000f, 0.9181f, 0.1441f)),
        Expected(4, 0.652f, BoundingBox(0.8535f, 0.7858f, 0.8996f, 0.8386f)),
        Expected(4, 0.591f, BoundingBox(0.8980f, 0.7633f, 0.9627f, 0.8412f)),
        Expected(6, 0.589f, BoundingBox(0.0000f, 0.8810f, 0.0791f, 0.9999f)),
        Expected(5, 0.508f, BoundingBox(0.1002f, 0.4485f, 0.2574f, 0.6098f)),
    )

    @Test
    fun retrouve_les_detections_de_reference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("ref_640x480.jpg").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inMutable = false })
        }!!
        val detector = WasteDetector.createBest(instrumentation.targetContext)
        detector.detect(bitmap)                      // préchauffage
        val result = detector.detect(bitmap)
        detector.close()

        Log.i("EcoScanTest", "${detector.acceleration} : durées ${result.timings} ms, ${result.detections.size} détections")
        result.detections.forEach { Log.i("EcoScanTest", "  $it") }

        // Toute détection de référence nette (≥ 0,55) doit être retrouvée : même classe, IoU ≥ 0,5.
        // Les cas limites (0,508) peuvent basculer selon le décodage JPEG et le padding (640 × 640 au lieu de 640 × 480).
        val missed = reference.filter { it.conf >= 0.55f }.filter { ref ->
            result.detections.none { it.classIndex == ref.cls && it.box.iou(ref.box) >= 0.5f }
        }
        assertTrue("détections manquées : $missed", missed.isEmpty())

        // Pas de détection forte inventée (≥ 0,5) absente de la référence.
        val extra = result.detections.filter { it.confidence >= 0.5f }.filter { d ->
            reference.none { it.cls == d.classIndex && it.box.iou(d.box) >= 0.5f }
        }
        assertTrue("détections en trop : $extra", extra.isEmpty())
    }

    /** Mesure seulement (pas d'assertion) : durée d'inférence par moteur, médiane de 5 passages. */
    @Test
    fun mesure_par_moteur() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("ref_640x480.jpg").use { BitmapFactory.decodeStream(it) }!!
        val configs = listOf(
            WasteDetector.Acceleration.XNNPACK to 4,
            WasteDetector.Acceleration.CPU to 4,
            WasteDetector.Acceleration.NNAPI to 4,
            WasteDetector.Acceleration.GPU to 4,
            WasteDetector.Acceleration.GPU to 4,   // 2e création : le cache GPU doit raccourcir l'initialisation
        )
        for ((acceleration, threads) in configs) {
            try {
                val t0 = android.os.SystemClock.elapsedRealtime()
                WasteDetector(instrumentation.targetContext, acceleration, numThreads = threads).use { detector ->
                    val first = detector.detect(bitmap)
                    val initMs = android.os.SystemClock.elapsedRealtime() - t0
                    val runs = List(5) { detector.detect(bitmap).timings.inferenceMs }.sorted()
                    Log.i(
                        "EcoScanTest",
                        "$acceleration threads=$threads inférence médiane ${runs[2]} ms $runs, " +
                            "${first.detections.size} détections, init + 1re analyse $initMs ms",
                    )
                }
            } catch (e: Exception) {
                Log.i("EcoScanTest", "$acceleration threads=$threads indisponible : $e")
            }
        }
    }
}
