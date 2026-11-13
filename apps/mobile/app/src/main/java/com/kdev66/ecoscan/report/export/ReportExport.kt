package com.kdev66.ecoscan.report.export

import com.kdev66.ecoscan.detection.WasteClass
import com.kdev66.ecoscan.report.DetectionSource
import com.kdev66.ecoscan.report.Report
import com.kdev66.ecoscan.report.ReportEdits
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Export des signalements (PRD § F5) : une archive ZIP avec
 * - `ecoscan.geojson` : `FeatureCollection` conforme à `packages/contracts/v1/report-collection.schema.json` ;
 * - `ecoscan.csv` : une ligne par signalement, pour un tableur ;
 * - les photos, à la racine de l'archive, sous le nom indiqué par le champ `photo` de chaque signalement.
 */
object ReportExport {
    const val CONTRACT_VERSION = 1
    const val GEOJSON_NAME = "ecoscan.geojson"
    const val CSV_NAME = "ecoscan.csv"

    fun geoJson(reports: List<Report>): String = JSONObject()
        .put("type", "FeatureCollection")
        .put("contractVersion", CONTRACT_VERSION)
        .put("features", JSONArray(reports.map(::feature)))
        .toString(2)

    fun feature(report: Report): JSONObject {
        val geometry = report.position?.let {
            JSONObject().put("type", "Point").put("coordinates", JSONArray(listOf(it.longitude, it.latitude)))
        } ?: JSONObject.NULL
        val properties = JSONObject()
            .put("createdAt", isoDate(report))
            .put("accuracyM", report.position?.accuracyM?.toDouble() ?: JSONObject.NULL)
            .put("context", report.context.id)
            .put("comment", report.comment ?: JSONObject.NULL)
            .put("photo", report.photoFile)
            .put("modelVersion", report.modelVersion)
            .put("detections", JSONArray(report.detections.map { d ->
                JSONObject()
                    .put("source", d.source.name)
                    .put("classId", d.wasteClass.ordinal)
                    .apply {
                        if (d.source == DetectionSource.MODEL) {
                            put("modelClassId", requireNotNull(d.modelClass).ordinal)
                            put("confidence", requireNotNull(d.confidence).toDouble().round(4))
                        }
                    }
                    .put("box", JSONObject()
                        .put("x", d.box.left.toDouble().round(5))
                        .put("y", d.box.top.toDouble().round(5))
                        .put("width", d.box.width.toDouble().round(5))
                        .put("height", d.box.height.toDouble().round(5)))
                    .put("deleted", d.deleted)
            }))
            .put("counts", JSONArray(report.counts.map { (c, n) -> JSONObject().put("classId", c.ordinal).put("count", n) }))
        return JSONObject()
            .put("type", "Feature")
            .put("id", report.id)
            .put("geometry", geometry)
            .put("properties", properties)
    }

    /** CSV (RFC 4180, UTF-8 avec BOM pour que les tableurs lisent l'arabe et les accents). */
    fun csv(reports: List<Report>): String {
        val header = listOf("id", "created_at", "latitude", "longitude", "accuracy_m", "context", "comment") +
            WasteClass.entries.map { it.name.lowercase(Locale.ROOT) } +
            listOf("total", "model_boxes", "boxes_deleted", "boxes_reclassified", "model_version", "photo")
        val rows = reports.map { r ->
            val model = r.detections.filter { it.source == DetectionSource.MODEL }
            listOf(
                r.id,
                isoDate(r),
                r.position?.latitude?.toString().orEmpty(),
                r.position?.longitude?.toString().orEmpty(),
                r.position?.accuracyM?.let { "%.1f".format(Locale.ROOT, it) }.orEmpty(),
                r.context.id,
                r.comment.orEmpty(),
            ) + WasteClass.entries.map { (r.counts[it] ?: 0).toString() } + listOf(
                ReportEdits.total(r).toString(),
                model.size.toString(),
                model.count { it.deleted }.toString(),
                model.count { !it.deleted && it.modelClass != it.wasteClass }.toString(),
                r.modelVersion,
                r.photoFile,
            )
        }
        return "\uFEFF" + (listOf(header) + rows).joinToString("\r\n", postfix = "\r\n") { row ->
            row.joinToString(",", transform = ::csvField)
        }
    }

    /** Écrit l'archive : GeoJSON, CSV, puis une photo par signalement (une photo manquante est signalée). */
    fun writeZip(reports: List<Report>, photoFile: (Report) -> File, out: OutputStream): ZipSummary {
        var missing = 0
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(GEOJSON_NAME)); zip.write(geoJson(reports).toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry(CSV_NAME)); zip.write(csv(reports).toByteArray()); zip.closeEntry()
            for (r in reports) {
                val file = photoFile(r)
                if (!file.isFile) { missing++; continue }
                zip.putNextEntry(ZipEntry(r.photoFile))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return ZipSummary(reports.size, missing)
    }

    data class ZipSummary(val reports: Int, val missingPhotos: Int)

    fun isoDate(report: Report): String =
        OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(report.createdAtMs),
            ZoneOffset.ofTotalSeconds(report.utcOffsetMinutes * 60),
        ).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun Double.round(decimals: Int): Double {
        var f = 1.0
        repeat(decimals) { f *= 10 }
        return Math.round(this * f) / f
    }
}
