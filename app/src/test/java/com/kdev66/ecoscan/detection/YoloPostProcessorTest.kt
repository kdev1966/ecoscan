package com.kdev66.ecoscan.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloPostProcessorTest {

    private val numClasses = 7
    private val input = 640

    /** Construit une sortie brute `[4 + 7][ancres]` à partir de (cx, cy, w, h, classe, score) en pixels d'entrée. */
    private fun output(vararg anchors: FloatArray): FloatArray {
        val n = anchors.size
        val out = FloatArray((4 + numClasses) * n)
        anchors.forEachIndexed { a, v ->
            for (k in 0 until 4) out[k * n + a] = v[k]
            out[(4 + v[4].toInt()) * n + a] = v[5]
        }
        return out
    }

    private fun anchor(cx: Float, cy: Float, w: Float, h: Float, cls: Int, score: Float) =
        floatArrayOf(cx, cy, w, h, cls.toFloat(), score)

    @Test
    fun letterbox_paysage_ajoute_des_bandes_en_haut_et_en_bas() {
        val lb = Letterbox.compute(640, 480, input)
        assertEquals(1f, lb.scale, 1e-6f)
        assertEquals(0f, lb.padX, 1e-6f)
        assertEquals(80f, lb.padY, 1e-6f)
    }

    @Test
    fun letterbox_portrait_reduit_et_centre_horizontalement() {
        val lb = Letterbox.compute(1200, 1600, input)
        assertEquals(0.4f, lb.scale, 1e-6f)
        assertEquals(80f, lb.padX, 1e-4f)
        assertEquals(0f, lb.padY, 1e-4f)
    }

    @Test
    fun boite_ramenee_dans_le_repere_de_l_image_source() {
        val p = YoloPostProcessor(numClasses, 1)
        // Image 640 × 480 : bande de 80 px en haut. Boîte centrée (320, 320) de 64 × 48 → (320, 240) dans la source.
        val d = p.process(output(anchor(320f, 320f, 64f, 48f, cls = 5, score = 0.9f)), Letterbox.compute(640, 480, input))
        assertEquals(1, d.size)
        assertEquals(WasteClass.PLASTIC, d[0].wasteClass)
        assertEquals((320f - 32f) / 640f, d[0].box.left, 1e-5f)
        assertEquals((240f - 24f) / 480f, d[0].box.top, 1e-5f)
        assertEquals((320f + 32f) / 640f, d[0].box.right, 1e-5f)
        assertEquals((240f + 24f) / 480f, d[0].box.bottom, 1e-5f)
    }

    @Test
    fun seuil_de_confiance_strict() {
        val p = YoloPostProcessor(numClasses, 2, confidenceThreshold = 0.25f)
        val d = p.process(
            output(anchor(100f, 100f, 20f, 20f, 0, 0.25f), anchor(300f, 300f, 20f, 20f, 1, 0.26f)),
            Letterbox.compute(640, 640, input),
        )
        assertEquals(listOf(1), d.map { it.classIndex })
    }

    @Test
    fun nms_garde_la_meilleure_boite_par_classe_et_conserve_les_autres_classes() {
        val p = YoloPostProcessor(numClasses, 3)
        val d = p.process(
            output(
                anchor(200f, 200f, 100f, 100f, 5, 0.80f),
                anchor(205f, 202f, 100f, 100f, 5, 0.95f),   // doublon, meilleur score → gardé
                anchor(205f, 202f, 100f, 100f, 2, 0.60f),   // même place, autre classe → gardé
            ),
            Letterbox.compute(640, 640, input),
        )
        assertEquals(2, d.size)
        assertEquals(0.95f, d[0].confidence, 1e-6f)
        assertEquals(WasteClass.GLASS, d[1].wasteClass)
    }

    @Test
    fun boites_hors_image_bornees() {
        val p = YoloPostProcessor(numClasses, 1)
        val d = p.process(output(anchor(5f, 85f, 40f, 40f, 3, 0.7f)), Letterbox.compute(640, 480, input))
        val b = d.single().box
        assertTrue(b.left >= 0f && b.top >= 0f && b.right <= 1f && b.bottom <= 1f)
        assertEquals(0f, b.left, 1e-6f)
        assertEquals(0f, b.top, 1e-6f)
    }

    @Test
    fun comptage_par_classe() {
        val box = BoundingBox(0f, 0f, 0.1f, 0.1f)
        val r = DetectionResult(
            listOf(Detection(5, 0.9f, box), Detection(5, 0.8f, box), Detection(2, 0.7f, box)),
            640, 480, DetectionTimings(1, 8, 1),
        )
        assertEquals(mapOf(WasteClass.GLASS to 1, WasteClass.PLASTIC to 2), r.countsByClass())
        assertEquals(listOf(WasteClass.GLASS, WasteClass.PLASTIC), r.countsByClass().keys.toList())
    }
}
