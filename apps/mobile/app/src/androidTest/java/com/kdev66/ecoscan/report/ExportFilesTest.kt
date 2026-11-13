package com.kdev66.ecoscan.report

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kdev66.ecoscan.detection.BoundingBox
import com.kdev66.ecoscan.detection.Detection
import com.kdev66.ecoscan.detection.DetectionResult
import com.kdev66.ecoscan.detection.DetectionTimings
import com.kdev66.ecoscan.detection.WasteClass
import com.kdev66.ecoscan.report.data.EcoScanDatabase
import com.kdev66.ecoscan.report.data.PhotoStore
import com.kdev66.ecoscan.report.data.ReportRepository
import com.kdev66.ecoscan.report.export.ExportFiles
import com.kdev66.ecoscan.report.export.ReportExport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ExportFilesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: EcoScanDatabase
    private lateinit var dir: File
    private lateinit var repo: ReportRepository
    private lateinit var exports: ExportFiles

    private val result = DetectionResult(
        detections = listOf(Detection(WasteClass.PLASTIC.ordinal, 0.9f, BoundingBox(0.1f, 0.2f, 0.3f, 0.5f))),
        imageWidth = 160, imageHeight = 120, timings = DetectionTimings(1, 1, 1),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, EcoScanDatabase::class.java).build()
        dir = File(context.cacheDir, "test-photos-${System.nanoTime()}")
        repo = ReportRepository(db, PhotoStore(dir))
        exports = ExportFiles(context, repo)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun nothingToExportWithoutSavedReports() = runBlocking {
        repo.createDraft(Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888), result, "v1")
        assertNull("drafts are not exported", exports.exportAll())
    }

    @Test
    fun exportsSavedReportsAndSharesThemThroughTheFileProvider() = runBlocking {
        val photo = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
        val saved = repo.createDraft(photo, result, "v1").also { repo.finalize(it.id) }
        repo.setPositionIfMissing(saved.id, Position(35.5, 11.06, 5f))
        repo.createDraft(photo, result, "v1") // brouillon : ne doit pas sortir

        val export = requireNotNull(exports.exportAll())
        assertEquals(1, export.reports)
        assertEquals(0, export.missingPhotos)

        ZipFile(export.file).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals(listOf(ReportExport.GEOJSON_NAME, ReportExport.CSV_NAME, saved.photoFile), names)
            val geo = JSONObject(zip.getInputStream(zip.getEntry(ReportExport.GEOJSON_NAME)).reader().readText())
            val feature = geo.getJSONArray("features").getJSONObject(0)
            assertEquals(saved.id, feature.getString("id"))
            assertEquals(35.5, feature.getJSONObject("geometry").getJSONArray("coordinates").getDouble(1), 1e-9)
        }

        val chooser = exports.shareIntent(export, "share")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.exports", uri.authority)
        context.contentResolver.openInputStream(uri).use { assertTrue(it!!.read() >= 0) }
        assertEquals(1, repo.observeHistory().first().size)
    }
}
