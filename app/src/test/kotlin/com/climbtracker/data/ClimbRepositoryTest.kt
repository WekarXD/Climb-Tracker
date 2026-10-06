package com.climbtracker.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.climbtracker.core.detection.Bounds
import com.climbtracker.core.detection.DetectedHold
import com.climbtracker.core.detection.DetectionResult
import com.climbtracker.core.detection.PointF
import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.editor.SelectedHold
import com.climbtracker.core.tracker.AttemptResult
import com.climbtracker.core.tracker.BoulderStatus
import com.climbtracker.core.tracker.GeoPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClimbRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ClimbRepository
    private var clock = 1000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ClimbRepository(db.dao()) { clock++ }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun hold(x: Float, group: Int) = DetectedHold(
        contour = listOf(PointF(x, 0.1f), PointF(x + 0.1f, 0.1f), PointF(x, 0.2f)),
        bounds = Bounds(x, 0.1f, x + 0.1f, 0.2f),
        center = PointF(x, 0.15f),
        argb = 0xFFD02020.toInt(),
        colorGroup = group,
        area = 100,
    )

    private fun detection(n: Int) = DetectionResult((0 until n).map { hold(it * 0.1f, it % 2) }, emptyList())

    private val normal = SelectedHold(HoldRole.NORMAL, 0)

    @Test
    fun createWallStoresHolds() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(3))
        val holds = repo.holds(wallId)
        assertEquals(3, holds.size)
        assertEquals(listOf(0, 1, 0), holds.map { it.colorGroup })
        assertEquals(3, holds[0].contour.size)
        assertEquals(PointF(0.1f, 0.1f), holds[1].contour[0])
        assertEquals(640, repo.wall(wallId)!!.width)
    }

    @Test
    fun newBoulderGetsDefaultNameWhenBlank() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(2))
        val holds = repo.holds(wallId)
        val first = repo.saveBoulder(null, wallId, "  ", "6A", mapOf(holds[0].id to normal))
        val second = repo.saveBoulder(null, wallId, "", "6B", mapOf(holds[1].id to normal))
        assertEquals("Bloque 1", repo.boulderOnce(first)!!.name)
        assertEquals("Bloque 2", repo.boulderOnce(second)!!.name)
    }

    @Test
    fun boulderKeepsSelectionWithRoles() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(3))
        val holds = repo.holds(wallId)
        val selection = mapOf(
            holds[0].id to SelectedHold(HoldRole.START, 0),
            holds[2].id to SelectedHold(HoldRole.TOP, 5),
        )
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", selection)
        assertEquals(selection, repo.selection(id))
        assertEquals("Azul", repo.boulderOnce(id)!!.name)
        assertEquals("6A", repo.boulderOnce(id)!!.grade)
    }

    @Test
    fun updatingBoulderReplacesHoldsAndKeepsNameWhenBlank() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(3))
        val holds = repo.holds(wallId)
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(holds[0].id to normal))
        val same = repo.saveBoulder(id, wallId, "", "6C", mapOf(holds[1].id to normal, holds[2].id to normal))
        assertEquals(id, same)
        assertEquals(setOf(holds[1].id, holds[2].id), repo.selection(id).keys)
        assertEquals("Azul", repo.boulderOnce(id)!!.name)
        assertEquals("6C", repo.boulderOnce(id)!!.grade)
    }

    @Test
    fun redetectReplacesHoldsOfWallWithoutBoulders() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(3))
        repo.redetect(wallId, detection(5))
        assertEquals(5, repo.holds(wallId).size)
    }

    @Test
    fun deletingWallCascadesAndReturnsPhotoPath() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(2))
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.addAttempt(id, AttemptResult.FAIL)
        assertEquals(1, repo.boulderCount(wallId))

        assertEquals("a.jpg", repo.deleteWall(wallId))

        assertNull(repo.wall(wallId))
        assertNull(repo.boulderOnce(id))
        assertTrue(repo.holds(wallId).isEmpty())
        assertTrue(repo.selection(id).isEmpty())
        assertTrue(repo.attempts(id).first().isEmpty())
        assertTrue(repo.summaries().first().isEmpty())
    }

    @Test
    fun deletingMissingWallReturnsNull() = runTest {
        assertNull(repo.deleteWall(99))
    }

    @Test
    fun deletingBoulderKeepsWallAndHolds() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(2))
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.deleteBoulder(id)
        assertNull(repo.boulderOnce(id))
        assertEquals(2, repo.holds(wallId).size)
        assertEquals(0, repo.boulderCount(wallId))
    }

    @Test
    fun attemptsAreChronologicalAndDeletable() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.addAttempt(id, AttemptResult.FAIL)
        repo.addAttempt(id, AttemptResult.SEND)
        val attempts = repo.attempts(id).first()
        assertEquals(listOf(AttemptResult.FAIL, AttemptResult.SEND), attempts.map { it.result })
        assertTrue(attempts[0].date < attempts[1].date)

        repo.deleteAttempt(attempts[1].id)
        assertEquals(listOf(AttemptResult.FAIL), repo.attempts(id).first().map { it.result })
    }

    @Test
    fun summariesCarryStatusPhotoAndCircuitHolds() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(3))
        val holds = repo.holds(wallId)
        val project = repo.saveBoulder(null, wallId, "Proyecto", "7A", mapOf(holds[0].id to normal))
        val sent = repo.saveBoulder(null, wallId, "Hecho", "6A", mapOf(holds[1].id to normal, holds[2].id to normal))
        val flash = repo.saveBoulder(null, wallId, "Flash", "5", mapOf(holds[0].id to normal))
        repo.addAttempt(project, AttemptResult.FAIL)
        repo.addAttempt(sent, AttemptResult.FAIL)
        repo.addAttempt(sent, AttemptResult.SEND)
        repo.addAttempt(flash, AttemptResult.SEND)

        val summaries = repo.summaries().first()
        assertEquals(listOf("Flash", "Hecho", "Proyecto"), summaries.map { it.boulder.name })
        assertEquals(
            listOf(BoulderStatus.FLASH, BoulderStatus.SENT, BoulderStatus.PROJECT),
            summaries.map { it.status },
        )
        assertEquals("a.jpg", summaries[1].photoPath)
        assertEquals(setOf(holds[1].id, holds[2].id), summaries[1].holds.map { it.id }.toSet())
        assertEquals(setOf(holds[1].id, holds[2].id), summaries[1].selection.keys)
    }

    @Test
    fun updateBoulderInfoChangesNameGradeAndNotes() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.updateBoulderInfo(id, "Azul largo", "6B", "Talón en la tercera")
        val boulder = repo.boulder(id).first()!!
        assertEquals("Azul largo", boulder.name)
        assertEquals("6B", boulder.grade)
        assertEquals("Talón en la tercera", boulder.notes)
    }

    private fun holdAt(y: Float) = DetectedHold(
        contour = listOf(PointF(0.4f, y - 0.02f), PointF(0.6f, y - 0.02f), PointF(0.6f, y + 0.02f), PointF(0.4f, y + 0.02f)),
        bounds = Bounds(0.4f, y - 0.02f, 0.6f, y + 0.02f),
        center = PointF(0.5f, y),
        argb = 0xFFE8C020.toInt(),
        colorGroup = 0,
        area = 100,
    )

    @Test
    fun splittingAHoldGivesItsCircuitsBothPartsAndMovesTheirAttempts() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, DetectionResult(listOf(holdAt(0.9f), holdAt(0.5f), holdAt(0.1f)), emptyList()))
        val holds = repo.holds(wallId)
        val id = repo.saveBoulder(
            null, wallId, "Amarillo", "6A",
            mapOf(
                holds[0].id to SelectedHold(HoldRole.START, 0),
                holds[1].id to SelectedHold(HoldRole.NORMAL, 1),
                holds[2].id to SelectedHold(HoldRole.TOP, 2),
            ),
        )
        repo.addAttempt(id, AttemptResult.FAIL, holds[1].id)
        repo.addAttempt(id, AttemptResult.SEND, holds[2].id)
        val above = listOf(PointF(0.4f, 0.1f), PointF(0.6f, 0.1f), PointF(0.5f, 0.2f))
        val below = listOf(PointF(0.4f, 0.3f), PointF(0.6f, 0.3f), PointF(0.5f, 0.4f))

        val (topUpper, topLower) = repo.splitHold(holds[2].id, above, below)!!
        val (midUpper, midLower) = repo.splitHold(holds[1].id, above, below)!!

        assertEquals(setOf(holds[0].id, topUpper.id, topLower.id, midUpper.id, midLower.id), repo.holds(wallId).map { it.id }.toSet())
        val selection = repo.selection(id)
        assertEquals(HoldRole.TOP, selection.getValue(topUpper.id).role)
        assertEquals(HoldRole.NORMAL, selection.getValue(topLower.id).role)
        assertEquals(HoldRole.NORMAL, selection.getValue(midUpper.id).role)
        assertEquals(HoldRole.START, selection.getValue(holds[0].id).role)
        assertEquals(5, selection.size)
        // The send stays on the top; the attempt that ended on the other hold is not counted higher than it got.
        assertEquals(listOf(midLower.id, topUpper.id), repo.attempts(id).first().map { it.lastHoldId })
        assertEquals(above, repo.holds(wallId).first { it.id == topUpper.id }.contour)
        assertNull(repo.splitHold(holds[2].id, above, below))
    }

    @Test
    fun attemptRemembersLastHoldAndSummaryShowsBestProgress() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, DetectionResult(listOf(holdAt(0.9f), holdAt(0.5f), holdAt(0.1f)), emptyList()))
        val holds = repo.holds(wallId)
        val id = repo.saveBoulder(
            null, wallId, "Amarillo", "6A",
            mapOf(
                holds[0].id to SelectedHold(HoldRole.START, 0),
                holds[1].id to SelectedHold(HoldRole.NORMAL, 1),
                holds[2].id to SelectedHold(HoldRole.TOP, 2),
            ),
        )
        assertEquals(0f, repo.summaries().first()[0].progress, 0.001f)

        val attemptId = repo.addAttempt(id, AttemptResult.FAIL, holds[1].id)
        assertTrue(attemptId > 0)
        assertEquals(holds[1].id, repo.attempts(id).first()[0].lastHoldId)
        val summary = repo.summaries().first()[0]
        assertEquals(0.5f, summary.progress, 0.001f)
        assertEquals(0xFFE8C020.toInt(), summary.color)

        repo.addAttempt(id, AttemptResult.SEND, holds[2].id)
        assertEquals(1f, repo.summaries().first()[0].progress, 0.001f)
    }

    @Test
    fun attemptWithoutLastHoldCountsAsZeroProgress() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(2))
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.addAttempt(id, AttemptResult.FAIL)
        assertNull(repo.attempts(id).first()[0].lastHoldId)
        assertEquals(0f, repo.summaries().first()[0].progress, 0.001f)
    }

    @Test
    fun archivedFlagPersistsAndReachesSummaries() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        assertEquals(false, repo.summaries().first()[0].boulder.archived)

        repo.setArchived(id, true)
        assertEquals(true, repo.boulderOnce(id)!!.archived)
        assertEquals(true, repo.summaries().first()[0].boulder.archived)

        repo.setArchived(id, false)
        assertEquals(false, repo.boulderOnce(id)!!.archived)
    }

    @Test
    fun summariesExposeAttemptsInChronologicalOrder() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.addAttempt(id, AttemptResult.FAIL)
        repo.addAttempt(id, AttemptResult.SEND)
        assertEquals(
            listOf(AttemptResult.FAIL, AttemptResult.SEND),
            repo.summaries().first()[0].attempts.map { it.result },
        )
    }

    /** A manual hold as the editor keeps it before the boulder is saved: with a negative id. */
    private fun pending(id: Long) = EditorHold(
        id, listOf(PointF(0.5f, 0.5f), PointF(0.6f, 0.5f), PointF(0.5f, 0.6f)), 0, 0xFFD02020.toInt(),
    )

    @Test
    fun pendingManualHoldIsStoredOnlyWhenTheBoulderUsesIt() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        val detected = repo.holds(wallId)[0].id
        val id = repo.saveBoulder(
            null, wallId, "Azul", "6A",
            mapOf(detected to normal, -1L to SelectedHold(HoldRole.TOP, 1)),
            pending = listOf(pending(-1), pending(-2)),
        )
        val holds = repo.holds(wallId)
        assertEquals(2, holds.size)
        val selection = repo.selection(id)
        assertEquals(2, selection.size)
        assertTrue(selection.keys.all { it > 0 })
        val manualId = selection.keys.first { it != detected }
        assertEquals(HoldRole.TOP, selection.getValue(manualId).role)
        assertEquals(3, holds.first { it.id == manualId }.contour.size)
    }

    @Test
    fun manualHoldNoBoulderUsesIsRemovedOnSave() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        val detected = repo.holds(wallId)[0].id
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(detected to normal, -1L to normal), listOf(pending(-1)))
        assertEquals(2, repo.holds(wallId).size)

        repo.saveBoulder(id, wallId, "Azul", "6A", mapOf(detected to normal))
        assertEquals(listOf(detected), repo.holds(wallId).map { it.id })
    }

    @Test
    fun manualHoldUsedByAnotherBoulderSurvives() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        val detected = repo.holds(wallId)[0].id
        val first = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(-1L to normal), listOf(pending(-1)))
        val manualId = repo.selection(first).keys.single()
        repo.saveBoulder(null, wallId, "Rojo", "6B", mapOf(manualId to normal))

        repo.saveBoulder(first, wallId, "Azul", "6A", mapOf(detected to normal))
        assertTrue(repo.holds(wallId).any { it.id == manualId })
    }

    @Test
    fun detectedHoldsAreNeverRemoved() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(3))
        repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.removeUnusedManualHolds(wallId)
        assertEquals(3, repo.holds(wallId).size)
    }

    @Test
    fun leftoverManualHoldsCanBeCleanedUp() = runTest {
        // Holds stored by earlier versions as soon as they were drawn, and never used.
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(-1L to normal), listOf(pending(-1)))
        repo.deleteBoulder(id)
        assertEquals(2, repo.holds(wallId).size)
        repo.removeUnusedManualHolds(wallId)
        assertEquals(1, repo.holds(wallId).size)
    }

    /** A detection whose holds sit where those of detection(n) do, moved a little. */
    private fun shifted(vararg indices: Int) =
        DetectionResult(indices.map { hold(it * 0.1f + 0.005f, 0) }, emptyList())

    @Test
    fun redetectingAWallWithBouldersKeepsTheirCircuits() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(3))
        val before = repo.holds(wallId)
        val id = repo.saveBoulder(
            null, wallId, "Azul", "6A",
            mapOf(before[0].id to SelectedHold(HoldRole.START, 0), before[2].id to SelectedHold(HoldRole.TOP, 1)),
        )

        repo.redetect(wallId, shifted(0, 1, 2))

        val after = repo.holds(wallId)
        assertEquals(3, after.size)
        assertTrue(after.none { new -> before.any { it.id == new.id } })
        val selection = repo.selection(id)
        assertEquals(2, selection.size)
        val start = selection.entries.single { it.value.role == HoldRole.START }.key
        val top = selection.entries.single { it.value.role == HoldRole.TOP }.key
        assertEquals(0.005f, after.first { it.id == start }.contour[0].x, 0.0001f)
        assertEquals(0.205f, after.first { it.id == top }.contour[0].x, 0.0001f)
    }

    @Test
    fun aCircuitHoldTheNewDetectionMissesIsKept() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(3))
        val before = repo.holds(wallId)
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(before[0].id to normal, before[2].id to normal))

        repo.redetect(wallId, shifted(0, 1))

        val after = repo.holds(wallId)
        assertEquals(3, after.size)
        assertTrue(after.any { it.id == before[2].id })
        assertEquals(2, repo.selection(id).size)
        assertTrue(before[2].id in repo.selection(id))
    }

    @Test
    fun oldHoldsNoBoulderUsesAreDropped() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(3))
        val before = repo.holds(wallId)
        repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(before[0].id to normal))

        repo.redetect(wallId, shifted(0))

        assertEquals(1, repo.holds(wallId).size)
    }

    @Test
    fun lastHoldOfAnAttemptFollowsTheNewDetection() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(2))
        val before = repo.holds(wallId)
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(before[0].id to normal, before[1].id to normal))
        repo.addAttempt(id, AttemptResult.FAIL, before[1].id)

        repo.redetect(wallId, shifted(0, 1))

        val reached = repo.attempts(id).first()[0].lastHoldId
        assertTrue(reached != before[1].id)
        assertTrue(reached in repo.selection(id))
    }

    @Test
    fun twoCircuitHoldsMergedByTheNewDetectionBecomeOne() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(2))
        val before = repo.holds(wallId)
        val id = repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(before[0].id to normal, before[1].id to normal))
        val whole = DetectedHold(
            contour = listOf(PointF(0f, 0.1f), PointF(0.2f, 0.1f), PointF(0.2f, 0.2f), PointF(0f, 0.2f)),
            bounds = Bounds(0f, 0.1f, 0.2f, 0.2f), center = PointF(0.1f, 0.15f),
            argb = 0xFFD02020.toInt(), colorGroup = 0, area = 400,
        )

        repo.redetect(wallId, DetectionResult(listOf(whole), emptyList()))

        assertEquals(1, repo.holds(wallId).size)
        assertEquals(1, repo.selection(id).size)
    }

    @Test
    fun assigningAGymCreatesItOnceAndTagsTheBoulders() = runTest {
        val first = repo.createWall("a.jpg", 640, 480, detection(1))
        val second = repo.createWall("b.jpg", 640, 480, detection(1))
        repo.saveBoulder(null, first, "Azul", "6A", mapOf(repo.holds(first)[0].id to normal))
        repo.saveBoulder(null, second, "Rojo", "6B", mapOf(repo.holds(second)[0].id to normal))

        repo.assignGym(first, " Mi roco ")
        repo.assignGym(second, "mi roco")

        val gyms = repo.gyms().first()
        assertEquals(listOf("Mi roco"), gyms.map { it.name })
        assertTrue(repo.summaries().first().all { it.gymId == gyms[0].id && it.gymName == "Mi roco" })
        assertEquals("Mi roco", repo.gymNameOfWall(first))
    }

    @Test
    fun blankGymNameClearsTheGym() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.assignGym(wallId, "Mi roco")
        repo.assignGym(wallId, "  ")
        assertNull(repo.summaries().first()[0].gymName)
        assertNull(repo.gymNameOfWall(wallId))
    }

    @Test
    fun deletingAGymKeepsItsWallsAndBoulders() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.assignGym(wallId, "Mi roco")
        repo.deleteGym(repo.gyms().first()[0].id)
        assertTrue(repo.gyms().first().isEmpty())
        val summary = repo.summaries().first().single()
        assertEquals("Azul", summary.boulder.name)
        assertNull(summary.gymId)
    }

    @Test
    fun renamingAGymShowsInItsBoulders() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        repo.saveBoulder(null, wallId, "Azul", "6A", mapOf(repo.holds(wallId)[0].id to normal))
        repo.assignGym(wallId, "Mi roco")
        repo.renameGym(repo.gyms().first()[0].id, "Otro nombre")
        assertEquals("Otro nombre", repo.summaries().first()[0].gymName)
    }

    private val madrid = GeoPoint(40.4168, -3.7038)
    private val oneKmNorth = GeoPoint(40.4258, -3.7038)

    @Test
    fun theFirstKnownPositionBecomesWhereTheGymIs() = runTest {
        val first = repo.createWall("a.jpg", 640, 480, detection(1))
        val second = repo.createWall("b.jpg", 640, 480, detection(1))

        repo.assignGym(first, "Mi roco", madrid)
        repo.assignGym(second, "Mi roco", oneKmNorth)

        val gym = repo.gyms().first().single()
        assertEquals(madrid.latitude, gym.latitude!!, 1e-9)
        assertEquals(madrid.longitude, gym.longitude!!, 1e-9)
    }

    @Test
    fun aGymAssignedWithoutPositionGetsItLater() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        repo.assignGym(wallId, "Mi roco")
        assertNull(repo.gyms().first().single().latitude)
        repo.assignGym(wallId, "Mi roco", madrid)
        assertEquals(madrid.latitude, repo.gyms().first().single().latitude!!, 1e-9)
    }

    @Test
    fun findsTheGymThePhoneIsAt() = runTest {
        val first = repo.createWall("a.jpg", 640, 480, detection(1))
        val second = repo.createWall("b.jpg", 640, 480, detection(1))
        val third = repo.createWall("c.jpg", 640, 480, detection(1))
        repo.assignGym(first, "Centro", madrid)
        repo.assignGym(second, "Norte", oneKmNorth)
        repo.assignGym(third, "Sin sitio")

        assertEquals("Norte", repo.gymNear(GeoPoint(40.4256, -3.7039))?.name)
        assertEquals("Centro", repo.gymNear(GeoPoint(40.4170, -3.7036))?.name)
        assertNull(repo.gymNear(GeoPoint(41.0, -3.0)))
    }

    @Test
    fun aPoorFixReachesFurther() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        repo.assignGym(wallId, "Centro", madrid)
        assertNull(repo.gymNear(oneKmNorth))
        assertEquals("Centro", repo.gymNear(oneKmNorth, accuracy = 1500.0)?.name)
    }

    @Test
    fun theGymPositionCanBeCorrected() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        repo.assignGym(wallId, "Mi roco", madrid)
        repo.setGymLocation(repo.gyms().first().single().id, oneKmNorth)
        assertEquals(oneKmNorth.latitude, repo.gyms().first().single().latitude!!, 1e-9)
    }

    @Test
    fun aGymFoundOnTheMapIsAddedWhereItIs() = runTest {
        repo.addGym(" Roco Norte ", oneKmNorth)
        val gym = repo.gyms().first().single()
        assertEquals("Roco Norte", gym.name)
        assertEquals(oneKmNorth.latitude, gym.latitude!!, 1e-9)
        assertEquals("Roco Norte", repo.gymNear(oneKmNorth)?.name)
    }

    @Test
    fun addingAGymThatExistsMovesItInsteadOfDuplicatingIt() = runTest {
        val wallId = repo.createWall("a.jpg", 640, 480, detection(1))
        repo.assignGym(wallId, "Mi roco", madrid)
        repo.addGym("mi roco", oneKmNorth)
        val gym = repo.gyms().first().single()
        assertEquals(oneKmNorth.latitude, gym.latitude!!, 1e-9)
        assertEquals("Mi roco", repo.gymNameOfWall(wallId))
    }
}
