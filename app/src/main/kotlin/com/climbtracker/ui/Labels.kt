package com.climbtracker.ui

import com.climbtracker.core.tracker.AttemptResult
import com.climbtracker.core.tracker.BoulderStatus

fun BoulderStatus.label(): String = when (this) {
    BoulderStatus.PROJECT -> "En progreso"
    BoulderStatus.SENT -> "Encadenado"
    BoulderStatus.FLASH -> "Flash"
}

fun AttemptResult.label(): String = when (this) {
    AttemptResult.FAIL -> "Intento"
    AttemptResult.SEND -> "Encadenado"
}
