package com.climbtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.climbtracker.core.editor.SelectedHold
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ClimbDao {

    @Query("SELECT * FROM gyms ORDER BY name COLLATE NOCASE")
    abstract fun gyms(): Flow<List<GymEntity>>

    @Query("SELECT * FROM gyms")
    abstract suspend fun allGymsOnce(): List<GymEntity>

    @Query("SELECT * FROM gyms WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun gymByName(name: String): GymEntity?

    @Query("SELECT g.name FROM gyms g JOIN walls w ON w.gymId = g.id WHERE w.id = :wallId")
    abstract suspend fun gymNameOfWall(wallId: Long): String?

    @Insert
    abstract suspend fun insertGym(gym: GymEntity): Long

    @Insert
    abstract suspend fun insertGyms(gyms: List<GymEntity>)

    @Query("UPDATE gyms SET name = :name WHERE id = :id")
    abstract suspend fun renameGym(id: Long, name: String)

    @Query("UPDATE gyms SET latitude = :latitude, longitude = :longitude WHERE id = :id")
    abstract suspend fun setGymLocation(id: Long, latitude: Double, longitude: Double)

    @Query("DELETE FROM gyms WHERE id = :id")
    abstract suspend fun deleteGym(id: Long)

    @Query("DELETE FROM gyms")
    abstract suspend fun deleteAllGyms()

    @Query("UPDATE walls SET gymId = :gymId WHERE id = :wallId")
    abstract suspend fun setWallGym(wallId: Long, gymId: Long?)

    @Insert
    abstract suspend fun insertWall(wall: WallEntity): Long

    @Query("SELECT * FROM walls ORDER BY createdAt DESC, id DESC")
    abstract fun walls(): Flow<List<WallEntity>>

    @Query("SELECT * FROM walls WHERE id = :id")
    abstract suspend fun wall(id: Long): WallEntity?

    @Query("DELETE FROM walls WHERE id = :id")
    abstract suspend fun deleteWall(id: Long)

    @Insert
    abstract suspend fun insertHolds(holds: List<HoldEntity>): List<Long>

    @Insert
    abstract suspend fun insertHold(hold: HoldEntity): Long

    @Query("SELECT * FROM holds WHERE wallId = :wallId ORDER BY id")
    abstract suspend fun holds(wallId: Long): List<HoldEntity>

    @Query("SELECT * FROM holds")
    abstract fun allHolds(): Flow<List<HoldEntity>>

    @Query("DELETE FROM holds WHERE wallId = :wallId")
    abstract suspend fun deleteHolds(wallId: Long)

    @Query("DELETE FROM holds WHERE wallId = :wallId AND manual = 1 AND id NOT IN (SELECT holdId FROM boulder_holds)")
    abstract suspend fun deleteUnusedManualHolds(wallId: Long)

    @Insert
    abstract suspend fun insertBoulder(boulder: BoulderEntity): Long

    @Update
    abstract suspend fun updateBoulder(boulder: BoulderEntity)

    @Query("SELECT * FROM boulders ORDER BY createdAt DESC, id DESC")
    abstract fun boulders(): Flow<List<BoulderEntity>>

    @Query("SELECT * FROM boulders WHERE id = :id")
    abstract fun boulder(id: Long): Flow<BoulderEntity?>

    @Query("SELECT * FROM boulders WHERE id = :id")
    abstract suspend fun boulderOnce(id: Long): BoulderEntity?

    @Query("SELECT COUNT(*) FROM boulders WHERE wallId = :wallId")
    abstract suspend fun boulderCount(wallId: Long): Int

    @Query("SELECT COUNT(*) FROM boulders")
    abstract suspend fun totalBoulders(): Int

    @Query("DELETE FROM boulders WHERE id = :id")
    abstract suspend fun deleteBoulder(id: Long)

    @Query("UPDATE boulders SET archived = :archived WHERE id = :id")
    abstract suspend fun setArchived(id: Long, archived: Boolean)

    @Insert
    abstract suspend fun insertBoulderHolds(links: List<BoulderHoldEntity>)

    @Query("SELECT * FROM boulder_holds WHERE boulderId = :boulderId")
    abstract suspend fun boulderHolds(boulderId: Long): List<BoulderHoldEntity>

    @Query("SELECT * FROM boulder_holds")
    abstract fun allBoulderHolds(): Flow<List<BoulderHoldEntity>>

    @Query("DELETE FROM boulder_holds WHERE boulderId = :boulderId")
    abstract suspend fun clearBoulderHolds(boulderId: Long)

    @Insert
    abstract suspend fun insertAttempt(attempt: AttemptEntity): Long

    @Query("SELECT * FROM attempts WHERE boulderId = :boulderId ORDER BY date, id")
    abstract fun attempts(boulderId: Long): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts ORDER BY date, id")
    abstract fun allAttempts(): Flow<List<AttemptEntity>>

    @Query("DELETE FROM attempts WHERE id = :id")
    abstract suspend fun deleteAttempt(id: Long)

    @Query("SELECT * FROM walls")
    abstract suspend fun allWallsOnce(): List<WallEntity>

    @Query("SELECT * FROM holds")
    abstract suspend fun allHoldsOnce(): List<HoldEntity>

    @Query("SELECT * FROM boulders")
    abstract suspend fun allBouldersOnce(): List<BoulderEntity>

    @Query("SELECT * FROM boulder_holds")
    abstract suspend fun allBoulderHoldsOnce(): List<BoulderHoldEntity>

    @Query("SELECT * FROM attempts")
    abstract suspend fun allAttemptsOnce(): List<AttemptEntity>

    @Query("DELETE FROM walls")
    abstract suspend fun deleteAllWalls()

    @Insert
    abstract suspend fun insertWalls(walls: List<WallEntity>)

    @Insert
    abstract suspend fun insertBoulders(boulders: List<BoulderEntity>)

    @Insert
    abstract suspend fun insertAttempts(attempts: List<AttemptEntity>)

    /** Replaces every row with the given ones, keeping their ids. Used to restore a backup. */
    @Transaction
    open suspend fun restore(
        gyms: List<GymEntity>,
        walls: List<WallEntity>,
        holds: List<HoldEntity>,
        boulders: List<BoulderEntity>,
        links: List<BoulderHoldEntity>,
        attempts: List<AttemptEntity>,
    ) {
        // Deleting the walls removes everything else through the foreign keys.
        deleteAllWalls()
        deleteAllGyms()
        insertGyms(gyms)
        insertWalls(walls)
        insertHolds(holds)
        insertBoulders(boulders)
        insertBoulderHolds(links)
        insertAttempts(attempts)
    }

    /** Creates the boulder when [boulderId] is null, otherwise replaces its grade, name and holds. */
    @Transaction
    open suspend fun saveBoulder(
        boulderId: Long?,
        wallId: Long,
        name: String,
        grade: String,
        selection: Map<Long, SelectedHold>,
        now: Long,
    ): Long {
        val id = if (boulderId == null) {
            val finalName = name.trim().ifEmpty { "Bloque ${totalBoulders() + 1}" }
            insertBoulder(BoulderEntity(wallId = wallId, name = finalName, grade = grade, notes = "", createdAt = now))
        } else {
            val existing = checkNotNull(boulderOnce(boulderId)) { "Boulder $boulderId does not exist" }
            updateBoulder(existing.copy(name = name.trim().ifEmpty { existing.name }, grade = grade))
            clearBoulderHolds(boulderId)
            boulderId
        }
        insertBoulderHolds(selection.map { (holdId, selected) -> BoulderHoldEntity(id, holdId, selected.role, selected.order) })
        return id
    }

    @Query("DELETE FROM holds WHERE id = :id")
    abstract suspend fun deleteHold(id: Long)

    @Query("UPDATE holds SET manual = 1 WHERE id = :id")
    abstract suspend fun markManual(id: Long)

    /** Holds of a wall that a circuit or an attempt refers to. */
    @Query(
        "SELECT bh.holdId FROM boulder_holds bh JOIN boulders b ON b.id = bh.boulderId WHERE b.wallId = :wallId " +
            "UNION SELECT a.lastHoldId FROM attempts a JOIN boulders b ON b.id = a.boulderId " +
            "WHERE b.wallId = :wallId AND a.lastHoldId IS NOT NULL",
    )
    abstract suspend fun referencedHoldIds(wallId: Long): List<Long>

    /** Points a circuit entry at another hold, unless that circuit already contains it. */
    @Query("UPDATE OR IGNORE boulder_holds SET holdId = :newId WHERE holdId = :oldId")
    abstract suspend fun moveBoulderHolds(oldId: Long, newId: Long)

    @Query("UPDATE attempts SET lastHoldId = :newId WHERE lastHoldId = :oldId")
    abstract suspend fun moveAttempts(oldId: Long, newId: Long)

    /**
     * Replaces the holds of a wall that has boulders. [matchOfOld] gives, for the id of each
     * current hold, the index in [fresh] of the new hold at the same place. Circuits and
     * attempts follow their holds to the new ones; a hold in use that the new detection did not
     * find is kept, as a manual hold, so no circuit loses a hold.
     */
    @Transaction
    open suspend fun replaceHoldsKeepingBoulders(wallId: Long, fresh: List<HoldEntity>, matchOfOld: Map<Long, Int>) {
        val old = holds(wallId)
        val referenced = referencedHoldIds(wallId).toSet()
        val newIds = insertHolds(fresh)
        for (hold in old) {
            val target = matchOfOld[hold.id]?.takeIf { it in newIds.indices }?.let { newIds[it] }
            when {
                target != null -> {
                    moveBoulderHolds(hold.id, target)
                    moveAttempts(hold.id, target)
                    // Deleting also removes entries left behind because their circuit already
                    // had the new hold: two old pieces joined into one.
                    deleteHold(hold.id)
                }
                hold.id in referenced -> markManual(hold.id)
                else -> deleteHold(hold.id)
            }
        }
    }

    /** Replaces every hold of a wall. The caller must make sure the wall has no boulders. */
    @Transaction
    open suspend fun replaceHolds(wallId: Long, holds: List<HoldEntity>) {
        deleteHolds(wallId)
        insertHolds(holds)
    }
}
