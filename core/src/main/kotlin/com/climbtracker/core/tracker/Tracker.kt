package com.climbtracker.core.tracker

enum class AttemptResult { FAIL, SEND }

enum class BoulderStatus { PROJECT, SENT, FLASH }

object Tracker {
    /** [results] must be in chronological order. */
    fun statusOf(results: List<AttemptResult>): BoulderStatus = when {
        results.none { it == AttemptResult.SEND } -> BoulderStatus.PROJECT
        results.first() == AttemptResult.SEND -> BoulderStatus.FLASH
        else -> BoulderStatus.SENT
    }
}
