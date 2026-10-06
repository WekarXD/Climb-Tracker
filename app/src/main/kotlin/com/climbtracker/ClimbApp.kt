package com.climbtracker

import android.app.Application
import com.climbtracker.core.detection.ColorHoldDetector
import com.climbtracker.core.detection.HoldDetector
import com.climbtracker.data.AppDatabase
import com.climbtracker.data.Backup
import com.climbtracker.data.BoulderSummary
import com.climbtracker.data.ClimbRepository
import com.climbtracker.data.GymSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import java.io.File

/** Holds the objects shared by every screen. */
class ClimbApp : Application() {
    private val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: ClimbRepository by lazy { ClimbRepository(database.dao()) }

    /** Every boulder ready to be listed, worked out once for all the screens that show them. */
    val summaries: Flow<List<BoulderSummary>> by lazy {
        repository.summaries().shareIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.WhileSubscribed(5000), replay = 1)
    }
    val backup: Backup by lazy { Backup(database.dao(), File(filesDir, "walls")) }
    val photos: PhotoStore by lazy { PhotoStore(this) }
    val prefs: Prefs by lazy { Prefs(this) }
    val locator: Locator by lazy { Locator(this) }
    val gymSearch: GymSearch by lazy {
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        GymSearch("ClimbTracker/$version (Android; $packageName)")
    }
    val detector: HoldDetector by lazy { RefiningDetector(ColorHoldDetector(), SilhouetteModel(this)) }
}
