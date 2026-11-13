package com.kdev66.ecoscan.report.export

import com.kdev66.ecoscan.detection.BoundingBox
import com.kdev66.ecoscan.detection.WasteClass
import com.kdev66.ecoscan.report.DetectionSource
import com.kdev66.ecoscan.report.Position
import com.kdev66.ecoscan.report.Report
import com.kdev66.ecoscan.report.ReportContext
import com.kdev66.ecoscan.report.ReportEdits
import com.kdev66.ecoscan.report.ReportStatus
import com.kdev66.ecoscan.report.ReviewedDetection
import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/** L'export doit respecter le contrat partagé `packages/contracts/v1` : ce test le lit dans le dépôt. */
class ReportExportTest {

    private val contracts = File("../../../packages/contracts/v1").canonicalFile

    private val schema: JsonSchema by lazy {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012) { builder ->
            builder.schemaMappers { it.mapPrefix(CONTRACT_URL, contracts.toURI().toString()) }
        }
        val config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build()
        factory.getSchema(SchemaLocation.of(CONTRACT_URL + "report-collection.schema.json"), config)
    }

    private fun detection(id: Long, c: WasteClass, model: WasteClass = c, deleted: Boolean = false) = ReviewedDetection(
        id = id, source = DetectionSource.MODEL, wasteClass = c, modelClass = model, confidence = 0.734567f,
        box = BoundingBox(0.1f, 0.2f, 0.35f, 0.5f), deleted = deleted,
    )

    private val located: Report = run {
        val detections = listOf(
            detection(1, WasteClass.PLASTIC),
            detection(2, WasteClass.METAL, model = WasteClass.GLASS),
            detection(3, WasteClass.FABRICS, deleted = true),
        )
        val r = Report(
            id = "3f2b8c1e-7a4d-4e9b-9c21-5d6f0a1b2c3d", createdAtMs = 1_763_025_660_000, utcOffsetMinutes = 60,
            position = Position(35.5047, 11.0622, 6.5f), context = ReportContext.BEACH,
            comment = "Près du port, \"sacs\", 3 tas — قرب الميناء", photoFile = "3f2b8c1e-7a4d-4e9b-9c21-5d6f0a1b2c3d.jpg",
            modelVersion = "yolov8n-tunmarinelitter-v1-640-fp32", status = ReportStatus.SAVED,
            detections = detections, counts = ReportEdits.initialCounts(detections),
        )
        ReportEdits.setCount(r, WasteClass.PLASTIC, 4)
    }

    private val unlocated = Report(
        id = "9a0e4b7c-1d2f-4a3b-8c5d-6e7f8091a2b3", createdAtMs = 1_763_026_512_000, utcOffsetMinutes = 60,
        position = null, context = ReportContext.CITY, comment = null, photoFile = "9a0e4b7c-1d2f-4a3b-8c5d-6e7f8091a2b3.jpg",
        modelVersion = "yolov8n-tunmarinelitter-v1-640-fp32", status = ReportStatus.SAVED,
        detections = emptyList(), counts = emptyMap(),
    )

    @Test
    fun `geojson export follows the v1 contract`() {
        val errors = schema.validate(ReportExport.geoJson(listOf(located, unlocated)), InputFormat.JSON)
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }

    @Test
    fun `the contract check really rejects a broken export`() {
        val broken = JSONObject(ReportExport.geoJson(listOf(located)))
        broken.getJSONArray("features").getJSONObject(0).getJSONObject("properties")
            .getJSONArray("detections").getJSONObject(0).remove("modelClassId")
        assertTrue(schema.validate(broken.toString(), InputFormat.JSON).isNotEmpty())
    }

    @Test
    fun `geojson keeps model output and corrections apart`() {
        val feature = JSONObject(ReportExport.geoJson(listOf(located))).getJSONArray("features").getJSONObject(0)
        assertEquals(11.0622, feature.getJSONObject("geometry").getJSONArray("coordinates").getDouble(0), 1e-9)
        val props = feature.getJSONObject("properties")
        assertEquals("2025-11-13T10:21:00+01:00", props.getString("createdAt"))
        val d = props.getJSONArray("detections")
        assertEquals(3, d.length())
        assertEquals(WasteClass.METAL.ordinal, d.getJSONObject(1).getInt("classId"))
        assertEquals(WasteClass.GLASS.ordinal, d.getJSONObject(1).getInt("modelClassId"))
        assertTrue(d.getJSONObject(2).getBoolean("deleted"))
        assertEquals(0.7346, d.getJSONObject(0).getDouble("confidence"), 1e-9)
    }

    @Test
    fun `csv has one escaped row per report`() {
        val csv = ReportExport.csv(listOf(located, unlocated))
        assertTrue("BOM for spreadsheets", csv.startsWith("\uFEFF"))
        val lines = csv.removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("id,created_at,latitude,longitude,accuracy_m,context,comment,cardboard,"))
        assertTrue(lines[1].contains(",\"Près du port, \"\"sacs\"\", 3 tas — قرب الميناء\","))
        // cardboard..wood = 0,0,0,1,0,4,0 (textile supprimé) ; total 5 ; 3 boîtes du modèle, 1 supprimée, 1 reclassée
        assertTrue(lines[1], lines[1].contains(",0,0,0,1,0,4,0,5,3,1,1,"))
        assertTrue(lines[2].contains(",,,,city,,"))
    }

    @Test
    fun `zip holds geojson, csv and the photos that exist`() {
        val dir = createTempDir()
        File(dir, located.photoFile).writeBytes(byteArrayOf(1, 2, 3))
        val out = ByteArrayOutputStream()
        val summary = ReportExport.writeZip(listOf(located, unlocated), { File(dir, it.photoFile) }, out)
        assertEquals(1, summary.missingPhotos)
        val names = ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            generateSequence { zip.nextEntry?.name }.toList()
        }
        assertEquals(listOf(ReportExport.GEOJSON_NAME, ReportExport.CSV_NAME, located.photoFile), names)
        dir.deleteRecursively()
    }

    private companion object {
        const val CONTRACT_URL = "https://ecoscan.app/contracts/v1/"
    }
}
