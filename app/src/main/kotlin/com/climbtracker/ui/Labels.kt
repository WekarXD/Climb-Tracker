package com.climbtracker.ui

import androidx.annotation.StringRes
import com.climbtracker.R
import com.climbtracker.core.tracker.BoulderStatus

@StringRes
fun BoulderStatus.label(): Int = when (this) {
    BoulderStatus.PROJECT -> R.string.status_project
    BoulderStatus.SENT -> R.string.status_sent
    BoulderStatus.FLASH -> R.string.status_flash
}
