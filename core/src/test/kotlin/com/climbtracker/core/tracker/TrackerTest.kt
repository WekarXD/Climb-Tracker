package com.climbtracker.core.tracker

import com.climbtracker.core.tracker.AttemptResult.FAIL
import com.climbtracker.core.tracker.AttemptResult.SEND
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackerTest {

    @Test
    fun noAttemptsIsProject() {
        assertEquals(BoulderStatus.PROJECT, Tracker.statusOf(emptyList()))
    }

    @Test
    fun onlyFailsIsProject() {
        assertEquals(BoulderStatus.PROJECT, Tracker.statusOf(listOf(FAIL, FAIL, FAIL)))
    }

    @Test
    fun sendOnFirstAttemptIsFlash() {
        assertEquals(BoulderStatus.FLASH, Tracker.statusOf(listOf(SEND)))
        assertEquals(BoulderStatus.FLASH, Tracker.statusOf(listOf(SEND, FAIL, SEND)))
    }

    @Test
    fun sendAfterFailIsSent() {
        assertEquals(BoulderStatus.SENT, Tracker.statusOf(listOf(FAIL, SEND)))
        assertEquals(BoulderStatus.SENT, Tracker.statusOf(listOf(FAIL, FAIL, SEND, FAIL)))
    }

    @Test
    fun deletingTheOnlySendRevertsToProject() {
        val attempts = mutableListOf(FAIL, SEND)
        attempts.removeAt(1)
        assertEquals(BoulderStatus.PROJECT, Tracker.statusOf(attempts))
    }

    @Test
    fun gradeScales() {
        assertEquals("4", Grades.FONT.first())
        assertEquals("8C+", Grades.FONT.last())
        assertEquals(22, Grades.FONT.size)
        assertTrue("6A+" in Grades.FONT)
        assertEquals("V0", Grades.V.first())
        assertEquals("V16", Grades.V.last())
        assertEquals(17, Grades.V.size)
        assertEquals(Grades.FONT, Grades.of(GradeScale.FONT))
        assertEquals(Grades.V, Grades.of(GradeScale.V))
    }
}
