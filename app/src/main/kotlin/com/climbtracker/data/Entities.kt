package com.climbtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.tracker.AttemptResult

/** [width] and [height] are the size of the image the detection ran on, not of the photo file. */
@Entity(tableName = "gyms")
data class GymEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** Where the gym is, in degrees; null until the phone has been located there. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Entity(
    tableName = "walls",
    foreignKeys = [
        ForeignKey(
            entity = GymEntity::class,
            parentColumns = ["id"],
            childColumns = ["gymId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("gymId")],
)
data class WallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoPath: String,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    /** Gym the wall belongs to; null until the user says. */
    val gymId: Long? = null,
)

@Entity(
    tableName = "holds",
    foreignKeys = [
        ForeignKey(
            entity = WallEntity::class,
            parentColumns = ["id"],
            childColumns = ["wallId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("wallId")],
)
data class HoldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wallId: Long,
    val contour: String,
    val argb: Int,
    val colorGroup: Int,
    val manual: Boolean,
)

@Entity(
    tableName = "boulders",
    foreignKeys = [
        ForeignKey(
            entity = WallEntity::class,
            parentColumns = ["id"],
            childColumns = ["wallId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("wallId")],
)
data class BoulderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wallId: Long,
    val name: String,
    val grade: String,
    val notes: String,
    val createdAt: Long,
    /** Taken down at the gym: hidden from the project list but kept for history and statistics. */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
)

@Entity(
    tableName = "boulder_holds",
    primaryKeys = ["boulderId", "holdId"],
    foreignKeys = [
        ForeignKey(
            entity = BoulderEntity::class,
            parentColumns = ["id"],
            childColumns = ["boulderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = HoldEntity::class,
            parentColumns = ["id"],
            childColumns = ["holdId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("holdId")],
)
data class BoulderHoldEntity(
    val boulderId: Long,
    val holdId: Long,
    val role: HoldRole,
    val markOrder: Int,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = BoulderEntity::class,
            parentColumns = ["id"],
            childColumns = ["boulderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("boulderId")],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boulderId: Long,
    val date: Long,
    val result: AttemptResult,
    /** Last hold reached in the attempt; null for attempts logged before this was recorded. */
    val lastHoldId: Long? = null,
)
