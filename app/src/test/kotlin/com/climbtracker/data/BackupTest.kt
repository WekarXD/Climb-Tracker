package com.climbtracker.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.climbtracker.core.detection.Bounds
import com.climbtracker.core.detection.DetectedHold
import com.climbtracker.core.detection.DetectionResult
import com.climbtracker.core.detection.PointF
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.editor.SelectedHold
import com.climbtracker.core.tracker.AttemptResult
import com.climbtracker.core.tracker.GeoPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase
    private lateinit var sourcePhotos: File
    private lateinit var targetPhotos: File

    @Before
    fun setUp() {
        sourceDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        targetDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        sourcePhotos = File(context.cacheDir, "source/walls").apply { deleteRecursively(); mkdirs() }
        targetPhotos = File(context.cacheDir, "target/walls").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    private fun hold(y: Float) = DetectedHold(
        contour = listOf(PointF(0.4f, y), PointF(0.6f, y), PointF(0.5f, y + 0.05f)),
        bounds = Bounds(0.4f, y, 0.6f, y + 0.05f), center = PointF(0.5f, y),
        argb = 0xFFE8C020.toInt(), colorGroup = 0, area = 100,
    )

    /** One wall with a photo, one boulder with roles, two attempts, taken down. Returns the zip. */
    private suspend fun exported(): ByteArray {
        val photo = File(sourcePhotos, "pared.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        var clock = 1000L
        val repo = ClimbRepository(sourceDb.dao()) { clock++ }
        val wallId = repo.createWall(photo.path, 960, 1280, DetectionResult(listOf(hold(0.8f), hold(0.2f)), emptyList()))
        val holds = repo.holds(wallId)
        val id = repo.saveBoulder(
            null, wallId, "Amarillo", "6A",
            mapOf(holds[0].id to SelectedHold(HoldRole.START, 0), holds[1].id to SelectedHold(HoldRole.TOP, 1)),
        )
        repo.updateBoulderInfo(id, "Amarillo", "6A", "Talón en la segunda")
        repo.addAttempt(id, AttemptResult.FAIL, holds[0].id)
        repo.addAttempt(id, AttemptResult.SEND, holds[1].id)
        repo.setArchived(id, true)
        val out = ByteArrayOutputStream()
        Backup(sourceDb.dao(), sourcePhotos).export(out)
        return out.toByteArray()
    }

    @Test
    fun exportedDataComesBackIdentical() = runTest {
        val zip = exported()
        Backup(targetDb.dao(), targetPhotos).import(ByteArrayInputStream(zip))

        val repo = ClimbRepository(targetDb.dao())
        val summary = repo.summaries().first().single()
        assertEquals("Amarillo", summary.boulder.name)
        assertEquals("6A", summary.boulder.grade)
        assertEquals("Talón en la segunda", summary.boulder.notes)
        assertTrue(summary.boulder.archived)
        assertEquals(listOf(HoldRole.START, HoldRole.TOP), summary.selection.values.map { it.role }.sorted())
        assertEquals(listOf(AttemptResult.FAIL, AttemptResult.SEND), summary.attempts.map { it.result })
        assertTrue(summary.attempts.all { it.lastHoldId in summary.selection })
        val wall = repo.wall(summary.boulder.wallId)!!
        assertEquals(960, wall.width)
        assertEquals(2, repo.holds(wall.id).size)
        assertEquals(3, repo.holds(wall.id)[0].contour.size)
    }

    @Test
    fun photosAreRestoredIntoTheTargetFolder() = runTest {
        val zip = exported()
        Backup(targetDb.dao(), targetPhotos).import(ByteArrayInputStream(zip))
        val wall = targetDb.dao().allWallsOnce().single()
        val photo = File(wall.photoPath)
        assertEquals(targetPhotos.canonicalPath, photo.parentFile!!.canonicalPath)
        assertTrue(photo.exists())
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), photo.readBytes().toList())
    }

    @Test
    fun importReplacesWhatWasThere() = runTest {
        val zip = exported()
        val repo = ClimbRepository(targetDb.dao())
        val oldPhoto = File(targetPhotos, "vieja.jpg").apply { writeBytes(byteArrayOf(9)) }
        val oldWall = repo.createWall(oldPhoto.path, 640, 480, DetectionResult(listOf(hold(0.5f)), emptyList()))
        repo.saveBoulder(null, oldWall, "Viejo", "5", mapOf(repo.holds(oldWall)[0].id to SelectedHold(HoldRole.NORMAL, 0)))

        Backup(targetDb.dao(), targetPhotos).import(ByteArrayInputStream(zip))

        assertEquals(listOf("Amarillo"), repo.summaries().first().map { it.boulder.name })
        assertFalse(oldPhoto.exists())
    }

    @Test
    fun aFileThatIsNotABackupIsRejectedAndNothingChanges() = runTest {
        val repo = ClimbRepository(targetDb.dao())
        val wallId = repo.createWall("x.jpg", 640, 480, DetectionResult(listOf(hold(0.5f)), emptyList()))
        repo.saveBoulder(null, wallId, "Intacto", "5", mapOf(repo.holds(wallId)[0].id to SelectedHold(HoldRole.NORMAL, 0)))
        val other = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("otra-cosa.txt")); it.write(1); it.closeEntry() }
        }
        for (bytes in listOf(other.toByteArray(), byteArrayOf(1, 2, 3))) {
            try {
                Backup(targetDb.dao(), targetPhotos).import(ByteArrayInputStream(bytes))
                fail("import should have thrown")
            } catch (expected: IllegalArgumentException) {
                assertEquals(listOf("Intacto"), repo.summaries().first().map { it.boulder.name })
            }
        }
    }

    @Test
    fun photoNamesWithPathsCannotEscapeThePhotoFolder() = runTest {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("climbtracker.json"))
            zip.write("""{"format":1,"walls":[],"holds":[],"boulders":[],"boulderHolds":[],"attempts":[]}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("walls/../../fuera.jpg"))
            zip.write(7)
            zip.closeEntry()
        }
        Backup(targetDb.dao(), targetPhotos).import(ByteArrayInputStream(out.toByteArray()))
        assertFalse(File(targetPhotos.parentFile!!.parentFile, "fuera.jpg").exists())
        assertFalse(File(targetPhotos.parentFile, "fuera.jpg").exists())
    }

    @Test
    fun gymsSurviveABackup() = runTest {
        val photo = File(sourcePhotos, "pared.jpg").apply { writeBytes(byteArrayOf(1)) }
        val repo = ClimbRepository(sourceDb.dao())
        val wallId = repo.createWall(photo.path, 960, 1280, DetectionResult(listOf(hold(0.5f)), emptyList()))
        repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to SelectedHold(HoldRole.NORMAL, 0)))
        repo.assignGym(wallId, "Mi roco")
        val out = ByteArrayOutputStream()
        Backup(sourceDb.dao(), sourcePhotos).export(out)

        Backup(targetDb.dao(), targetPhotos).import(ByteArrayInputStream(out.toByteArray()))

        assertEquals("Mi roco", ClimbRepository(targetDb.dao()).summaries().first().single().gymName)
    }

    @Test
    fun gymPositionsSurviveABackup() = runTest {
        val photo = File(sourcePhotos, "pared.jpg").apply { writeBytes(byteArrayOf(1)) }
        val repo = ClimbRepository(sourceDb.dao())
        val wallId = repo.createWall(photo.path, 960, 1280, DetectionResult(listOf(hold(0.5f)), emptyList()))
        val second = File(sourcePhotos, "otra.jpg").apply { writeBytes(byteArrayOf(2)) }
        val other = repo.createWall(second.path, 960, 1280, DetectionResult(listOf(hold(0.5f)), emptyList()))
        repo.assignGym(wallId, "Con sitio", GeoPoint(40.4168, -3.7038))
        repo.assignGym(other, "Sin sitio")
        val out = ByteArrayOutputStream()
        Backup(sourceDb.dao(), sourcePhotos).export(out)

        Backup(targetDb.dao(), targetPhotos).import(ByteArrayInputStream(out.toByteArray()))

        val restored = ClimbRepository(targetDb.dao())
        assertEquals("Con sitio", restored.gymNear(GeoPoint(40.4168, -3.7038))?.name)
        assertEquals(listOf(40.4168, null), restored.gyms().first().map { it.latitude })
    }
}
