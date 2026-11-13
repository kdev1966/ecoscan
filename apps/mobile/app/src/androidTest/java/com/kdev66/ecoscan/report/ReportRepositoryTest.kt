package com.kdev66.ecoscan.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReportRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: EcoScanDatabase
    private lateinit var dir: File
    private var now = 1_700_000_000_000L
    private lateinit var repo: ReportRepository

    private val photo: Bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
    private val result = DetectionResult(
        detections = listOf(
            Detection(WasteClass.PLASTIC.ordinal, 0.9f, BoundingBox(0.1f, 0.2f, 0.3f, 0.5f)),
            Detection(WasteClass.GLASS.ordinal, 0.4f, BoundingBox(0.5f, 0.5f, 0.6f, 0.7f)),
        ),
        imageWidth = 160, imageHeight = 120, timings = DetectionTimings(1, 1, 1),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, EcoScanDatabase::class.java).build()
        dir = File(context.cacheDir, "test-photos-${System.nanoTime()}")
        repo = ReportRepository(db, PhotoStore(dir), clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun draftWritesPhotoThenRows() = runBlocking {
        val r = repo.createDraft(photo, result, "v1")
        val file = repo.photoFile(r)
        assertTrue(file.exists())
        assertNotNull(BitmapFactory.decodeFile(file.path))
        assertFalse("the JPEG carries no camera metadata", ExifInterface(file.path).hasAttribute(ExifInterface.TAG_MAKE))
        assertEquals(ReportStatus.DRAFT, r.status)
        assertEquals(ReportContext.BEACH, r.context)
        assertEquals(2, r.detections.size)
        assertTrue(r.detections.all { it.source == DetectionSource.MODEL && it.modelClass == it.wasteClass })
        assertEquals(mapOf(WasteClass.GLASS to 1, WasteClass.PLASTIC to 1), r.counts)
        assertEquals(0.1f, r.detections[0].box.left, 1e-6f)
        assertEquals(0.5f, r.detections[0].box.bottom, 1e-6f)
        assertEquals(r.id, repo.latestDraftId())
    }

    @Test
    fun editsAreStoredAndObserved() = runBlocking {
        val r = repo.createDraft(photo, result, "v1")
        val glass = r.detections.first { it.wasteClass == WasteClass.GLASS }.id
        repo.edit(r.id) { ReportEdits.changeClass(it, glass, WasteClass.METAL) }
        repo.edit(r.id) { ReportEdits.setCount(it, WasteClass.PLASTIC, 3) }
        repo.edit(r.id) { it.copy(context = ReportContext.ROCKY_COAST, comment = "près du port") }

        val loaded = repo.observe(r.id).first()!!
        assertEquals(mapOf(WasteClass.METAL to 1, WasteClass.PLASTIC to 3), loaded.counts)
        val d = loaded.detections.first { it.id == glass }
        assertEquals(WasteClass.METAL, d.wasteClass)
        assertEquals(WasteClass.GLASS, d.modelClass)
        assertEquals(ReportContext.ROCKY_COAST, loaded.context)
        assertEquals("près du port", loaded.comment)
        assertEquals("edits keep the draft a draft", ReportStatus.DRAFT, loaded.status)
    }

    @Test
    fun positionIsOnlyFilledOnce() = runBlocking {
        val r = repo.createDraft(photo, result, "v1")
        assertTrue(repo.setPositionIfMissing(r.id, Position(35.5, 11.06, 6f)))
        assertFalse(repo.setPositionIfMissing(r.id, Position(1.0, 2.0, 3f)))
        assertEquals(Position(35.5, 11.06, 6f), repo.load(r.id)!!.position)
    }

    @Test
    fun finalizeLeavesTheDraftState() = runBlocking {
        val r = repo.createDraft(photo, result, "v1")
        repo.finalize(r.id)
        assertEquals(ReportStatus.SAVED, repo.load(r.id)!!.status)
        assertNull(repo.latestDraftId())
    }

    @Test
    fun discardRemovesRowsAndPhoto() = runBlocking {
        val r = repo.createDraft(photo, result, "v1")
        repo.discard(r.id)
        assertNull(repo.load(r.id))
        assertFalse(repo.photoFile(r).exists())
    }

    @Test
    fun orphanPhotosAreRemovedOnlyWhenOld() = runBlocking {
        val kept = repo.createDraft(photo, result, "v1")
        val orphan = File(dir, "orphan.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val tmp = File(dir, "x.jpg.tmp").apply { writeBytes(byteArrayOf(1)) }

        now = System.currentTimeMillis()
        assertEquals("a capture in progress is never touched", 0, repo.deleteOrphanPhotos())

        now = System.currentTimeMillis() + 11 * 60_000L
        assertEquals(2, repo.deleteOrphanPhotos())
        assertFalse(orphan.exists())
        assertFalse(tmp.exists())
        assertTrue(repo.photoFile(kept).exists())
    }
}
