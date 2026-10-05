package com.climbtracker.core.tracker

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** An attempt reduced to what statistics need. */
data class AttemptRecord(val day: LocalDate, val result: AttemptResult)

/** A boulder with its attempts in chronological order. */
data class BoulderRecord(
    val id: Long,
    val name: String,
    val grade: String,
    val colorName: String,
    val color: Int,
    val createdDay: LocalDate,
    val attempts: List<AttemptRecord>,
)

enum class StatsPeriod { WEEK, MONTH, YEAR, ALL }

/** Inclusive range of days. */
data class DateSpan(val start: LocalDate, val end: LocalDate) {
    operator fun contains(day: LocalDate): Boolean = !day.isBefore(start) && !day.isAfter(end)
}

data class PeriodSummary(val tops: Int, val flashes: Int, val sessions: Int)

/** Boulders tried and topped on one weekday. */
data class DayVolume(val tried: Int, val topped: Int)

data class Bucket(val label: String, val color: Int, val sent: Int, val total: Int)

data class Records(
    val hardestSend: String?,
    /** Boulder name and its number of attempts. */
    val mostAttempts: Pair<String, Int>?,
    /** Day and number of boulders topped for the first time on it. */
    val bestSession: Pair<LocalDate, Int>?,
)

/** Statistics over the climbing log. A null span means "all time". */
object Stats {

    /** The calendar week (Monday to Sunday), month or year containing [today]; null for ALL. */
    fun span(period: StatsPeriod, today: LocalDate): DateSpan? = when (period) {
        StatsPeriod.WEEK -> monday(today).let { DateSpan(it, it.plusDays(6)) }
        StatsPeriod.MONTH -> DateSpan(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()))
        StatsPeriod.YEAR -> DateSpan(today.withDayOfYear(1), today.with(TemporalAdjusters.lastDayOfYear()))
        StatsPeriod.ALL -> null
    }

    /** The period just before the one containing [today]; null for ALL. */
    fun previousSpan(period: StatsPeriod, today: LocalDate): DateSpan? = when (period) {
        StatsPeriod.WEEK -> span(period, today.minusWeeks(1))
        StatsPeriod.MONTH -> span(period, today.minusMonths(1))
        StatsPeriod.YEAR -> span(period, today.minusYears(1))
        StatsPeriod.ALL -> null
    }

    /**
     * Tops are boulders sent for the first time within the span (repeats do not count again),
     * flashes are the tops achieved on the boulder's very first attempt, and sessions are the
     * distinct days with any attempt.
     */
    fun summary(boulders: List<BoulderRecord>, span: DateSpan?): PeriodSummary {
        var tops = 0
        var flashes = 0
        val days = HashSet<LocalDate>()
        for (boulder in boulders) {
            val firstSend = boulder.attempts.firstOrNull { it.result == AttemptResult.SEND }
            if (firstSend != null && within(firstSend.day, span)) {
                tops++
                if (boulder.attempts.first().result == AttemptResult.SEND) flashes++
            }
            for (attempt in boulder.attempts) if (within(attempt.day, span)) days += attempt.day
        }
        return PeriodSummary(tops, flashes, days.size)
    }

    /** Seven entries, Monday first: boulders tried and boulders topped on each weekday of the span. */
    fun volumeByWeekday(boulders: List<BoulderRecord>, span: DateSpan?): List<DayVolume> {
        val tried = IntArray(7)
        val topped = IntArray(7)
        for (boulder in boulders) {
            val byDay = boulder.attempts.filter { within(it.day, span) }.groupBy { it.day }
            for ((day, attempts) in byDay) {
                val index = day.dayOfWeek.value - 1
                tried[index]++
                if (attempts.any { it.result == AttemptResult.SEND }) topped[index]++
            }
        }
        return List(7) { DayVolume(tried[it], topped[it]) }
    }

    /** Boulders active in the span grouped by colour, most numerous first. */
    fun byColor(boulders: List<BoulderRecord>, span: DateSpan?): List<Bucket> =
        boulders.filter { active(it, span) }
            .groupBy { it.colorName }
            .map { (name, group) -> Bucket(name, group.first().color, group.count { sentBy(it, span) }, group.size) }
            .sortedByDescending { it.total }

    /** Boulders active in the span grouped by grade, easiest first; unknown grades last. */
    fun byGrade(boulders: List<BoulderRecord>, span: DateSpan?): List<Bucket> =
        boulders.filter { active(it, span) }
            .groupBy { it.grade }
            .map { (grade, group) -> Bucket(grade, 0, group.count { sentBy(it, span) }, group.size) }
            .sortedBy { gradeRank(it.label).let { rank -> if (rank < 0f) Float.MAX_VALUE else rank } }

    /** [weeks] rows of seven days (Monday first), oldest week first; true on days with a session. */
    fun consistency(boulders: List<BoulderRecord>, today: LocalDate, weeks: Int = 12): List<List<Boolean>> {
        val days = sessionDays(boulders)
        val thisMonday = monday(today)
        return (weeks - 1 downTo 0).map { back ->
            val start = thisMonday.minusWeeks(back.toLong())
            List(7) { start.plusDays(it.toLong()) in days }
        }
    }

    /**
     * Consecutive weeks with at least one session, counting back from the current week. A current
     * week without a session yet does not break the streak; a whole empty week does.
     */
    fun weekStreak(boulders: List<BoulderRecord>, today: LocalDate): Int {
        val days = sessionDays(boulders)
        fun climbed(start: LocalDate) = (0L..6L).any { start.plusDays(it) in days }
        var week = monday(today)
        if (!climbed(week)) week = week.minusWeeks(1)
        var streak = 0
        while (climbed(week)) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    fun records(boulders: List<BoulderRecord>): Records {
        val hardest = boulders
            .filter { sentBy(it, null) && gradeRank(it.grade) >= 0f }
            .maxByOrNull { gradeRank(it.grade) }
        val most = boulders.filter { it.attempts.isNotEmpty() }.maxByOrNull { it.attempts.size }
        val best = boulders
            .mapNotNull { boulder -> boulder.attempts.firstOrNull { it.result == AttemptResult.SEND }?.day }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
        return Records(
            hardestSend = hardest?.grade,
            mostAttempts = most?.let { it.name to it.attempts.size },
            bestSession = best?.let { it.key to it.value },
        )
    }

    /** Position of a grade within its own scale, 0..1; negative when the grade is in neither scale. */
    private fun gradeRank(grade: String): Float {
        val font = Grades.FONT.indexOf(grade)
        if (font >= 0) return font.toFloat() / Grades.FONT.size
        val v = Grades.V.indexOf(grade)
        if (v >= 0) return v.toFloat() / Grades.V.size
        return -1f
    }

    private fun monday(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun within(day: LocalDate, span: DateSpan?): Boolean = span == null || day in span

    private fun sessionDays(boulders: List<BoulderRecord>): Set<LocalDate> =
        boulders.flatMapTo(HashSet()) { boulder -> boulder.attempts.map { it.day } }

    /** Created or attempted within the span. */
    private fun active(boulder: BoulderRecord, span: DateSpan?): Boolean =
        span == null || boulder.createdDay in span || boulder.attempts.any { it.day in span }

    /** Sent at any time up to the end of the span. */
    private fun sentBy(boulder: BoulderRecord, span: DateSpan?): Boolean =
        boulder.attempts.any { it.result == AttemptResult.SEND && (span == null || !it.day.isAfter(span.end)) }
}
