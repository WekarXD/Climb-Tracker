package com.climbtracker.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.tracker.AttemptResult
import com.climbtracker.core.tracker.GeoPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opens databases laid out as older versions of the app left them and checks that the
 * migrations bring them to the current schema without losing data. Room compares the migrated
 * schema with the entities when it opens the file, so a wrong migration fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** The tables exactly as version 1 of the app created them. */
    private val version1 = listOf(
        "CREATE TABLE walls (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, photoPath TEXT NOT NULL, " +
            "width INTEGER NOT NULL, height INTEGER NOT NULL, createdAt INTEGER NOT NULL)",
        "CREATE TABLE holds (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, wallId INTEGER NOT NULL, " +
            "contour TEXT NOT NULL, argb INTEGER NOT NULL, colorGroup INTEGER NOT NULL, manual INTEGER NOT NULL, " +
            "FOREIGN KEY(wallId) REFERENCES walls(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        "CREATE INDEX index_holds_wallId ON holds (wallId)",
        "CREATE TABLE boulders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, wallId INTEGER NOT NULL, " +
            "name TEXT NOT NULL, grade TEXT NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
            "FOREIGN KEY(wallId) REFERENCES walls(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        "CREATE INDEX index_boulders_wallId ON boulders (wallId)",
        "CREATE TABLE boulder_holds (boulderId INTEGER NOT NULL, holdId INTEGER NOT NULL, role TEXT NOT NULL, " +
            "markOrder INTEGER NOT NULL, PRIMARY KEY(boulderId, holdId), " +
            "FOREIGN KEY(boulderId) REFERENCES boulders(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
            "FOREIGN KEY(holdId) REFERENCES holds(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        "CREATE INDEX index_boulder_holds_holdId ON boulder_holds (holdId)",
        "CREATE TABLE attempts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, boulderId INTEGER NOT NULL, " +
            "date INTEGER NOT NULL, result TEXT NOT NULL, " +
            "FOREIGN KEY(boulderId) REFERENCES boulders(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        "CREATE INDEX index_attempts_boulderId ON attempts (boulderId)",
    )

    private val data = listOf(
        "INSERT INTO walls VALUES (1, 'a.jpg', 640, 480, 1000)",
        "INSERT INTO holds VALUES (1, 1, '0.1,0.8;0.2,0.8;0.1,0.9', -1520608, 0, 0)",
        "INSERT INTO holds VALUES (2, 1, '0.1,0.1;0.2,0.1;0.1,0.2', -1520608, 0, 1)",
        "INSERT INTO boulders VALUES (1, 1, 'Amarillo', '6A', 'Talón', 2000)",
        "INSERT INTO boulder_holds VALUES (1, 1, 'START', 0)",
        "INSERT INTO boulder_holds VALUES (1, 2, 'TOP', 1)",
        "INSERT INTO attempts VALUES (1, 1, 3000, 'FAIL')",
        "INSERT INTO attempts VALUES (2, 1, 4000, 'SEND')",
    )

    private fun createOldDatabase(version: Int, extra: List<String> = emptyList()) {
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        (version1 + data + extra).forEach(db::execSQL)
        db.version = version
        db.close()
    }

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
        .allowMainThreadQueries()
        .build()

    @Test
    fun version1DatabaseKeepsItsData() = runTest {
        createOldDatabase(1)
        val db = open()
        val dao = db.dao()

        val boulder = dao.boulderOnce(1)!!
        assertEquals("Amarillo", boulder.name)
        assertEquals("6A", boulder.grade)
        assertEquals("Talón", boulder.notes)
        assertEquals(2, dao.holds(1).size)
        assertTrue(dao.holds(1)[1].manual)
        assertEquals(
            mapOf(1L to HoldRole.START, 2L to HoldRole.TOP),
            dao.boulderHolds(1).associate { it.holdId to it.role },
        )
        assertEquals(listOf(AttemptResult.FAIL, AttemptResult.SEND), dao.attempts(1).first().map { it.result })
        db.close()
    }

    @Test
    fun version1DatabaseGetsDefaultsForNewColumns() = runTest {
        createOldDatabase(1)
        val db = open()
        assertFalse(db.dao().boulderOnce(1)!!.archived)
        assertTrue(db.dao().attempts(1).first().all { it.lastHoldId == null })
        db.close()
    }

    @Test
    fun version2DatabaseKeepsLastHoldOfAttempts() = runTest {
        createOldDatabase(
            2,
            listOf(
                "ALTER TABLE attempts ADD COLUMN lastHoldId INTEGER",
                "UPDATE attempts SET lastHoldId = 2 WHERE id = 2",
            ),
        )
        val db = open()
        val attempts = db.dao().attempts(1).first()
        assertNull(attempts[0].lastHoldId)
        assertEquals(2L, attempts[1].lastHoldId)
        assertFalse(db.dao().boulderOnce(1)!!.archived)
        db.close()
    }

    @Test
    fun migratedDatabaseAcceptsTheNewFields() = runTest {
        createOldDatabase(1)
        val db = open()
        val repo = ClimbRepository(db.dao()) { 5000 }
        repo.setArchived(1, true)
        repo.addAttempt(1, AttemptResult.FAIL, lastHoldId = 1)
        assertTrue(repo.boulderOnce(1)!!.archived)
        assertEquals(1L, repo.attempts(1).first().last().lastHoldId)
        db.close()
    }

    @Test
    fun deletingAWallStillCascadesAfterMigration() = runTest {
        createOldDatabase(1)
        val db = open()
        db.dao().deleteWall(1)
        assertNull(db.dao().boulderOnce(1))
        assertTrue(db.dao().attempts(1).first().isEmpty())
        db.close()
    }

    @Test
    fun migratedWallsHaveNoGymAndCanBeGivenOne() = runTest {
        createOldDatabase(1)
        val db = open()
        val repo = ClimbRepository(db.dao()) { 5000 }
        assertNull(db.dao().wall(1)!!.gymId)
        repo.assignGym(1, "Mi roco")
        assertEquals("Mi roco", repo.gymNameOfWall(1))
        db.close()
    }

    @Test
    fun gymsFromVersion4KeepTheirWallsAndCanBeLocated() = runTest {
        createOldDatabase(
            4,
            listOf(
                "ALTER TABLE attempts ADD COLUMN lastHoldId INTEGER",
                "ALTER TABLE boulders ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
                "CREATE TABLE gyms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)",
                "ALTER TABLE walls ADD COLUMN gymId INTEGER REFERENCES gyms(id) ON DELETE SET NULL",
                "CREATE INDEX index_walls_gymId ON walls (gymId)",
                "INSERT INTO gyms VALUES (1, 'Mi roco', 500)",
                "UPDATE walls SET gymId = 1",
            ),
        )
        val db = open()
        val repo = ClimbRepository(db.dao()) { 5000 }
        assertEquals("Mi roco", repo.gymNameOfWall(1))
        assertNull(db.dao().allGymsOnce().single().latitude)
        repo.setGymLocation(1, GeoPoint(40.0, -3.0))
        assertEquals("Mi roco", repo.gymNear(GeoPoint(40.0, -3.0))?.name)
        db.close()
    }
}
