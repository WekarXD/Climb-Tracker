package com.climbtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        GymEntity::class,
        WallEntity::class,
        HoldEntity::class,
        BoulderEntity::class,
        BoulderHoldEntity::class,
        AttemptEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ClimbDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "climb.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /** Adds the last hold reached to each attempt. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN lastHoldId INTEGER")
            }
        }

        /** Adds the "taken down" flag to boulders. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boulders ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds gyms, and the gym of each wall. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE gyms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("ALTER TABLE walls ADD COLUMN gymId INTEGER REFERENCES gyms(id) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX index_walls_gymId ON walls (gymId)")
            }
        }

        /** Adds the position of each gym. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gyms ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE gyms ADD COLUMN longitude REAL")
            }
        }
    }
}
