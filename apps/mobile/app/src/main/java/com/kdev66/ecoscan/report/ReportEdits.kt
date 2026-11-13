package com.kdev66.ecoscan.report

import com.kdev66.ecoscan.detection.WasteClass

/**
 * Les corrections de l'écran de revue. Règle des quantités : pour chaque classe, la quantité vaut le nombre de
 * boîtes gardées plus les objets ajoutés à la main (jamais négatifs). Supprimer une boîte ou changer sa classe
 * déplace donc la quantité correspondante sans toucher aux objets ajoutés.
 */
object ReportEdits {

    /** Nombre de boîtes gardées (non supprimées) par classe. */
    fun boxCounts(detections: List<ReviewedDetection>): Map<WasteClass, Int> =
        detections.filterNot { it.deleted }.groupingBy { it.wasteClass }.eachCount()

    /** Objets ajoutés à la main par classe : quantité validée moins boîtes gardées. */
    fun extras(report: Report): Map<WasteClass, Int> {
        val boxes = boxCounts(report.detections)
        return WasteClass.entries
            .associateWith { (report.counts[it] ?: 0) - (boxes[it] ?: 0) }
            .filterValues { it > 0 }
    }

    /** Quantités initiales d'une analyse : une par boîte. */
    fun initialCounts(detections: List<ReviewedDetection>): Map<WasteClass, Int> = boxCounts(detections)

    fun delete(report: Report, detectionId: Long): Report =
        withDetections(report) { d -> if (d.id == detectionId) d.copy(deleted = true) else d }

    fun restore(report: Report, detectionId: Long): Report =
        withDetections(report) { d -> if (d.id == detectionId) d.copy(deleted = false) else d }

    fun changeClass(report: Report, detectionId: Long, newClass: WasteClass): Report =
        withDetections(report) { d -> if (d.id == detectionId) d.copy(wasteClass = newClass, deleted = false) else d }

    /** Fixe la quantité d'une classe, sans descendre sous son nombre de boîtes gardées. */
    fun setCount(report: Report, wasteClass: WasteClass, count: Int): Report {
        val floor = boxCounts(report.detections)[wasteClass] ?: 0
        val value = count.coerceAtLeast(floor)
        return report.copy(counts = clean(report.counts + (wasteClass to value)))
    }

    fun total(report: Report): Int = report.counts.values.sum()

    private fun withDetections(report: Report, transform: (ReviewedDetection) -> ReviewedDetection): Report {
        val extras = extras(report)
        val detections = report.detections.map(transform)
        val boxes = boxCounts(detections)
        val counts = WasteClass.entries.associateWith { (boxes[it] ?: 0) + (extras[it] ?: 0) }
        return report.copy(detections = detections, counts = clean(counts))
    }

    private fun clean(counts: Map<WasteClass, Int>): Map<WasteClass, Int> =
        counts.filterValues { it > 0 }.toSortedMap(compareBy { it.ordinal })
}
