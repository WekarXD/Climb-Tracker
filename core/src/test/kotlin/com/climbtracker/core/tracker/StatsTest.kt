package com.climbtracker.core.tracker

import com.climbtracker.core.tracker.AttemptResult.FAIL
import com.climbtracker.core.tracker.AttemptResult.SEND
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsTest {

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    /** A Wednesday; its week runs from Monday 5 to Sunday 11 October. */
    private val today = day(10, 7)

    private fun boulder(
        id: Long,
        grade: String,
        colour: String,
        created: LocalDate,
        vararg attempts: Pair<LocalDate, AttemptResult>,
    ) = BoulderRecord(id, "B$id", grade, colour, id.toInt(), created, attempts.map { AttemptRecord(it.first, it.second) })

    private val sentSecondGo = boulder(1, "6A", "Amarillo", day(10, 5), day(10, 5) to FAIL, day(10, 6) to SEND)
    private val flashed = boulder(2, "6B", "Azul", day(10, 6), day(10, 6) to SEND)
    private val oldProject = boulder(3, "7A", "Rojo", day(9, 29), day(9, 29) to FAIL, day(9, 30) to FAIL)
    private val untried = boulder(4, "5", "Amarillo", day(10, 7))
    private val all = listOf(sentSecondGo, flashed, oldProject, untried)

    private val week = Stats.span(StatsPeriod.WEEK, today)
    private val lastWeek = Stats.previousSpan(StatsPeriod.WEEK, today)

    @Test
    fun spansCoverWholeCalendarPeriods() {
        assertEquals(DateSpan(day(10, 5), day(10, 11)), week)
        assertEquals(DateSpan(day(9, 28), day(10, 4)), lastWeek)
        assertEquals(DateSpan(day(10, 1), day(10, 31)), Stats.span(StatsPeriod.MONTH, today))
        assertEquals(DateSpan(day(9, 1), day(9, 30)), Stats.previousSpan(StatsPeriod.MONTH, today))
        assertEquals(DateSpan(day(1, 1), day(12, 31)), Stats.span(StatsPeriod.YEAR, today))
        assertNull(Stats.span(StatsPeriod.ALL, today))
        assertNull(Stats.previousSpan(StatsPeriod.ALL, today))
    }

    @Test
    fun weekStartsOnMondayEvenOnSundayAndMonday() {
        assertEquals(day(10, 5), Stats.span(StatsPeriod.WEEK, day(10, 5))!!.start)
        assertEquals(day(10, 5), Stats.span(StatsPeriod.WEEK, day(10, 11))!!.start)
    }

    @Test
    fun summaryCountsTopsFlashesAndSessionsInSpan() {
        assertEquals(PeriodSummary(tops = 2, flashes = 1, sessions = 2), Stats.summary(all, week))
        assertEquals(PeriodSummary(tops = 0, flashes = 0, sessions = 2), Stats.summary(all, lastWeek))
        assertEquals(PeriodSummary(tops = 2, flashes = 1, sessions = 4), Stats.summary(all, null))
    }

    @Test
    fun aBoulderIsToppedOnlyOnceHoweverOftenItIsRepeated() {
        val repeated = boulder(9, "6A", "Verde", day(10, 5), day(10, 5) to SEND, day(10, 6) to SEND)
        assertEquals(PeriodSummary(tops = 1, flashes = 1, sessions = 2), Stats.summary(listOf(repeated), week))
    }

    @Test
    fun summaryOfNothingIsZero() {
        assertEquals(PeriodSummary(0, 0, 0), Stats.summary(emptyList(), week))
    }

    @Test
    fun volumeCountsBouldersPerWeekday() {
        val volume = Stats.volumeByWeekday(all, week)
        assertEquals(7, volume.size)
        assertEquals(DayVolume(tried = 1, topped = 0), volume[0])
        assertEquals(DayVolume(tried = 2, topped = 2), volume[1])
        assertEquals(DayVolume(0, 0), volume[2])
        assertEquals(DayVolume(0, 0), volume[6])
    }

    @Test
    fun byColourCountsBouldersActiveInSpan() {
        val buckets = Stats.byColor(all, week)
        assertEquals(listOf("Amarillo", "Azul"), buckets.map { it.label })
        assertEquals(listOf(2, 1), buckets.map { it.total })
        assertEquals(listOf(1, 1), buckets.map { it.sent })
        assertEquals(1, buckets[0].color)
    }

    @Test
    fun byColourOverAllTimeIncludesEverything() {
        val buckets = Stats.byColor(all, null).associateBy { it.label }
        assertEquals(3, buckets.size)
        assertEquals(0, buckets.getValue("Rojo").sent)
        assertEquals(1, buckets.getValue("Rojo").total)
    }

    @Test
    fun byGradeIsOrderedFromEasiestToHardest() {
        val buckets = Stats.byGrade(all, null)
        assertEquals(listOf("5", "6A", "6B", "7A"), buckets.map { it.label })
        assertEquals(listOf(0, 1, 1, 0), buckets.map { it.sent })
    }

    @Test
    fun unknownGradesGoLast() {
        val odd = boulder(8, "raro", "Verde", day(10, 5))
        assertEquals("raro", Stats.byGrade(all + odd, null).last().label)
    }

    @Test
    fun consistencyMarksSessionDaysOldestWeekFirst() {
        val grid = Stats.consistency(all, today, weeks = 2)
        assertEquals(listOf(false, true, true, false, false, false, false), grid[0])
        assertEquals(listOf(true, true, false, false, false, false, false), grid[1])
    }

    @Test
    fun weekStreakCountsConsecutiveWeeksWithASession() {
        assertEquals(2, Stats.weekStreak(all, today))
        // A week that has only just started does not break the streak...
        assertEquals(2, Stats.weekStreak(all, day(10, 14)))
        // ...but a whole week without climbing does.
        assertEquals(0, Stats.weekStreak(all, day(10, 21)))
        assertEquals(0, Stats.weekStreak(emptyList(), today))
    }

    @Test
    fun recordsPickHardestSendMostAttemptsAndBestSession() {
        val records = Stats.records(all)
        assertEquals("6B", records.hardestSend)
        assertEquals("B1" to 2, records.mostAttempts)
        assertEquals(day(10, 6) to 2, records.bestSession)
    }

    @Test
    fun recordsAreEmptyWithoutData() {
        val records = Stats.records(listOf(untried))
        assertNull(records.hardestSend)
        assertNull(records.mostAttempts)
        assertNull(records.bestSession)
    }

    @Test
    fun hardestSendWorksOnTheVScale() {
        val v = listOf(
            boulder(1, "V2", "Azul", day(10, 5), day(10, 5) to SEND),
            boulder(2, "V10", "Azul", day(10, 5), day(10, 5) to SEND),
            boulder(3, "V12", "Azul", day(10, 5), day(10, 5) to FAIL),
        )
        assertEquals("V10", Stats.records(v).hardestSend)
    }
}
