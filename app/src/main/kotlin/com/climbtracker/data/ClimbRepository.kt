package com.climbtracker.data

import com.climbtracker.core.detection.DetectedHold
import com.climbtracker.core.detection.DetectionResult
import com.climbtracker.core.detection.HoldMatch
import com.climbtracker.core.editor.EditorHold
import com.climbtracker.core.editor.SelectedHold
import com.climbtracker.core.image.ContourCodec
import com.climbtracker.core.tracker.AttemptResult
import com.climbtracker.core.tracker.BoulderStatus
import com.climbtracker.core.tracker.Progress
import com.climbtracker.core.tracker.GeoPoint
import com.climbtracker.core.tracker.GymLocator
import com.climbtracker.core.tracker.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** A boulder ready to be listed. [holds] contains only the holds of its circuit. */
data class BoulderSummary(
    val boulder: BoulderEntity,
    val photoPath: String?,
    val holds: List<EditorHold>,
    val selection: Map<Long, SelectedHold>,
    val status: BoulderStatus,
    /** Best height reached over all attempts, 0..1; 1 once sent. */
    val progress: Float,
    /** Colour of the circuit's main colour group. */
    val color: Int,
    /** Chronological order, oldest first. */
    val attempts: List<AttemptEntity>,
    val gymId: Long?,
    val gymName: String?,
)

class ClimbRepository(
    private val dao: ClimbDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun walls(): Flow<List<WallEntity>> = dao.walls()

    fun gyms(): Flow<List<GymEntity>> = dao.gyms()

    suspend fun gymNameOfWall(wallId: Long): String? = dao.gymNameOfWall(wallId)

    /**
     * Puts a wall in the gym called [name], creating the gym if new; a blank name removes it from any.
     * [here] is where the phone is, if known: a gym without a position takes it.
     */
    suspend fun assignGym(wallId: Long, name: String, here: GeoPoint? = null) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            dao.setWallGym(wallId, null)
            return
        }
        val existing = dao.gymByName(clean)
        val gymId = existing?.id ?: dao.insertGym(GymEntity(name = clean, createdAt = now()))
        if (here != null && existing?.latitude == null) dao.setGymLocation(gymId, here.latitude, here.longitude)
        dao.setWallGym(wallId, gymId)
    }

    /** Adds a gym found on the map, or moves the one already called [name] to [point]. Returns its id. */
    suspend fun addGym(name: String, point: GeoPoint): Long {
        val clean = name.trim()
        val existing = dao.gymByName(clean)
        if (existing != null) {
            dao.setGymLocation(existing.id, point.latitude, point.longitude)
            return existing.id
        }
        return dao.insertGym(GymEntity(name = clean, createdAt = now(), latitude = point.latitude, longitude = point.longitude))
    }

    suspend fun setGymLocation(id: Long, here: GeoPoint) = dao.setGymLocation(id, here.latitude, here.longitude)

    /** The gym the phone is at, given its position and how many metres off that may be. */
    suspend fun gymNear(here: GeoPoint, accuracy: Double = 0.0): GymEntity? {
        val located = dao.allGymsOnce().mapNotNull { gym ->
            val latitude = gym.latitude ?: return@mapNotNull null
            val longitude = gym.longitude ?: return@mapNotNull null
            gym to GeoPoint(latitude, longitude)
        }
        return GymLocator.nearest(located, here, GymLocator.RADIUS_METERS + accuracy.coerceIn(0.0, MAX_ACCURACY_METERS))
    }

    suspend fun renameGym(id: Long, name: String) {
        if (name.isNotBlank()) dao.renameGym(id, name.trim())
    }

    /** Removes the gym; its walls and boulders stay, without a gym. */
    suspend fun deleteGym(id: Long) = dao.deleteGym(id)

    suspend fun wall(id: Long): WallEntity? = dao.wall(id)

    suspend fun holds(wallId: Long): List<EditorHold> = dao.holds(wallId).map { it.toEditorHold() }

    suspend fun boulderCount(wallId: Long): Int = dao.boulderCount(wallId)

    suspend fun createWall(photoPath: String, width: Int, height: Int, detection: DetectionResult): Long {
        val wallId = dao.insertWall(WallEntity(photoPath = photoPath, width = width, height = height, createdAt = now()))
        dao.insertHolds(detection.holds.map { it.toEntity(wallId, it.colorGroup, manual = false) })
        return wallId
    }

    /**
     * Replaces the holds of a wall with a new detection. If the wall has boulders, their circuits
     * and attempts are moved to the new hold at the same place, and holds in use that the new
     * detection misses are kept.
     */
    suspend fun redetect(wallId: Long, detection: DetectionResult) {
        val fresh = detection.holds.map { it.toEntity(wallId, it.colorGroup, manual = false) }
        if (dao.boulderCount(wallId) == 0) {
            dao.replaceHolds(wallId, fresh)
            return
        }
        val old = dao.holds(wallId)
        val match = HoldMatch.match(old.map { ContourCodec.decode(it.contour) }, detection.holds.map { it.contour })
        dao.replaceHoldsKeepingBoulders(wallId, fresh, old.indices.filter { match[it] >= 0 }.associate { old[it].id to match[it] })
    }

    /** Removes the manual holds of a wall that no boulder uses. Detected holds are never removed. */
    suspend fun removeUnusedManualHolds(wallId: Long) = dao.deleteUnusedManualHolds(wallId)

    /** Returns the photo path of the deleted wall so the caller can remove the file. */
    suspend fun deleteWall(id: Long): String? {
        val wall = dao.wall(id) ?: return null
        dao.deleteWall(id)
        return wall.photoPath
    }

    /**
     * Saves a boulder. [pending] are manual holds drawn in the editor and not stored yet, which
     * carry negative ids; those in [selection] are stored now and take real ids. A manual hold
     * therefore only exists on a wall while some boulder uses it.
     */
    suspend fun saveBoulder(
        boulderId: Long?,
        wallId: Long,
        name: String,
        grade: String,
        selection: Map<Long, SelectedHold>,
        pending: List<EditorHold> = emptyList(),
    ): Long {
        val stored = HashMap<Long, Long>()
        for (hold in pending) {
            if (hold.id !in selection) continue
            stored[hold.id] = dao.insertHold(
                HoldEntity(
                    wallId = wallId,
                    contour = ContourCodec.encode(hold.contour),
                    argb = hold.argb,
                    colorGroup = hold.colorGroup,
                    manual = true,
                ),
            )
        }
        val resolved = selection.mapKeys { (id, _) -> stored[id] ?: id }
        val id = dao.saveBoulder(boulderId, wallId, name, grade, resolved, now())
        dao.deleteUnusedManualHolds(wallId)
        return id
    }

    suspend fun selection(boulderId: Long): Map<Long, SelectedHold> =
        dao.boulderHolds(boulderId).associate { it.holdId to SelectedHold(it.role, it.markOrder) }

    fun boulder(id: Long): Flow<BoulderEntity?> = dao.boulder(id)

    suspend fun boulderOnce(id: Long): BoulderEntity? = dao.boulderOnce(id)

    suspend fun updateBoulderInfo(id: Long, name: String, grade: String, notes: String) {
        val existing = dao.boulderOnce(id) ?: return
        dao.updateBoulder(existing.copy(name = name.trim().ifEmpty { existing.name }, grade = grade, notes = notes))
    }

    suspend fun deleteBoulder(id: Long) = dao.deleteBoulder(id)

    /** Marks a boulder as taken down at the gym, or brings it back. */
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    fun attempts(boulderId: Long): Flow<List<AttemptEntity>> = dao.attempts(boulderId)

    /** Returns the id of the new attempt. */
    suspend fun addAttempt(boulderId: Long, result: AttemptResult, lastHoldId: Long? = null): Long =
        dao.insertAttempt(AttemptEntity(boulderId = boulderId, date = now(), result = result, lastHoldId = lastHoldId))

    suspend fun deleteAttempt(id: Long) = dao.deleteAttempt(id)

    fun summaries(): Flow<List<BoulderSummary>> = combine(
        dao.boulders(),
        dao.walls(),
        dao.allHolds(),
        dao.allBoulderHolds(),
        dao.allAttempts(),
    ) { boulders, walls, holds, links, attempts ->
        val wallById = walls.associateBy { it.id }
        val holdById = holds.associateBy { it.id }
        val linksByBoulder = links.groupBy { it.boulderId }
        val attemptsByBoulder = attempts.groupBy { it.boulderId }
        boulders.map { boulder ->
            val own = linksByBoulder[boulder.id].orEmpty()
            val circuit = own.mapNotNull { holdById[it.holdId]?.toEditorHold() }
            val selection = own.associate { it.holdId to SelectedHold(it.role, it.markOrder) }
            val tries = attemptsByBoulder[boulder.id].orEmpty()
            BoulderSummary(
                boulder = boulder,
                photoPath = wallById[boulder.wallId]?.photoPath,
                holds = circuit,
                selection = selection,
                status = Tracker.statusOf(tries.map { it.result }),
                progress = Progress.best(circuit, selection, tries.map { it.result to it.lastHoldId }),
                color = Progress.circuitColor(circuit, selection),
                attempts = tries,
                gymId = wallById[boulder.wallId]?.gymId,
                gymName = null,
            )
        }
    }.combine(dao.gyms()) { list, gyms ->
        val names = gyms.associate { it.id to it.name }
        list.map { s -> s.copy(gymName = s.gymId?.let { names[it] }) }
    }.flowOn(Dispatchers.Default)

    private fun HoldEntity.toEditorHold() = EditorHold(id, ContourCodec.decode(contour), colorGroup, argb)

    private fun DetectedHold.toEntity(wallId: Long, group: Int, manual: Boolean) = HoldEntity(
        wallId = wallId,
        contour = ContourCodec.encode(contour),
        argb = argb,
        colorGroup = group,
        manual = manual,
    )

    private companion object {
        /** An approximate fix can be a couple of kilometres off; beyond that it says nothing. */
        const val MAX_ACCURACY_METERS = 3000.0
    }
}
