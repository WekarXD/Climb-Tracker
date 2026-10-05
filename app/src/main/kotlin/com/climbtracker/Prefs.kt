package com.climbtracker

import android.content.Context
import com.climbtracker.core.tracker.GradeScale

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var gradeScale: GradeScale
        get() = runCatching { GradeScale.valueOf(prefs.getString(KEY_SCALE, null) ?: GradeScale.FONT.name) }
            .getOrDefault(GradeScale.FONT)
        set(value) = prefs.edit().putString(KEY_SCALE, value.name).apply()

    /** Detection sensitivity last applied from the editor, 0..1. */
    var lastSensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, 0.5f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value).apply()

    /** Gym last assigned to a wall; new walls are put in it. */
    var lastGym: String?
        get() = prefs.getString(KEY_GYM, null)
        set(value) = prefs.edit().putString(KEY_GYM, value).apply()

    /** True once the location permission has been requested, so it is not asked again and again. */
    var locationAsked: Boolean
        get() = prefs.getBoolean(KEY_LOCATION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCATION_ASKED, value).apply()

    private companion object {
        const val KEY_LOCATION_ASKED = "locationAsked"
        const val KEY_GYM = "lastGym"
        const val KEY_SENSITIVITY = "lastSensitivity"
        const val KEY_SCALE = "gradeScale"
    }
}
