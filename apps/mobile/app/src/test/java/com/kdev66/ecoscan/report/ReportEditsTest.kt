package com.kdev66.ecoscan.report

import com.kdev66.ecoscan.detection.BoundingBox
import com.kdev66.ecoscan.detection.WasteClass
import com.kdev66.ecoscan.detection.WasteClass.GLASS
import com.kdev66.ecoscan.detection.WasteClass.METAL
import com.kdev66.ecoscan.detection.WasteClass.PLASTIC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportEditsTest {

    private fun box(id: Long, c: WasteClass) = ReviewedDetection(
        id = id, source = DetectionSource.MODEL, wasteClass = c, modelClass = c, confidence = 0.8f,
        box = BoundingBox(0.1f, 0.1f, 0.2f, 0.2f), deleted = false,
    )

    /** Deux plastiques et un verre détectés, plus deux plastiques ajoutés à la main. */
    private fun report(): Report {
        val detections = listOf(box(1, PLASTIC), box(2, PLASTIC), box(3, GLASS))
        val r = Report(
            id = "r", createdAtMs = 0, utcOffsetMinutes = 60, position = null, context = ReportContext.BEACH,
            comment = null, photoFile = "r.jpg", modelVersion = "v1", status = ReportStatus.DRAFT,
            detections = detections, counts = ReportEdits.initialCounts(detections),
        )
        return ReportEdits.setCount(r, PLASTIC, 4)
    }

    @Test
    fun `initial counts are one per box`() {
        val detections = listOf(box(1, PLASTIC), box(2, PLASTIC), box(3, GLASS))
        assertEquals(mapOf(PLASTIC to 2, GLASS to 1), ReportEdits.initialCounts(detections))
    }

    @Test
    fun `deleting a box lowers its class but keeps hand-added items`() {
        val r = ReportEdits.delete(report(), 1)
        assertEquals(mapOf(PLASTIC to 3, GLASS to 1), r.counts)
        assertTrue(r.detections.first { it.id == 1L }.deleted)
        assertEquals("the box is kept for retraining", 3, r.detections.size)
    }

    @Test
    fun `restoring a box brings its count back`() {
        val r = ReportEdits.restore(ReportEdits.delete(report(), 1), 1)
        assertEquals(report().counts, r.counts)
        assertFalse(r.detections.first { it.id == 1L }.deleted)
    }

    @Test
    fun `changing the class moves one item and keeps the model's class`() {
        val r = ReportEdits.changeClass(report(), 3, METAL)
        assertEquals(mapOf(PLASTIC to 4, METAL to 1), r.counts)
        val d = r.detections.first { it.id == 3L }
        assertEquals(METAL, d.wasteClass)
        assertEquals(GLASS, d.modelClass)
        assertTrue(d.corrected)
    }

    @Test
    fun `a count never goes below the boxes kept`() {
        val r = ReportEdits.setCount(report(), PLASTIC, 0)
        assertEquals(2, r.counts[PLASTIC])
        assertEquals(0, ReportEdits.extras(r)[PLASTIC] ?: 0)
    }

    @Test
    fun `a class without boxes can be added and removed`() {
        val added = ReportEdits.setCount(report(), METAL, 2)
        assertEquals(2, added.counts[METAL])
        val removed = ReportEdits.setCount(added, METAL, 0)
        assertFalse("zero counts are not stored", METAL in removed.counts)
    }

    @Test
    fun `total adds boxes and hand-added items`() {
        assertEquals(5, ReportEdits.total(report()))
        assertEquals(4, ReportEdits.total(ReportEdits.delete(report(), 3)))
    }
}
