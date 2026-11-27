package com.fisudur.app.engine

import java.time.DayOfWeek
import java.time.LocalDate

data class Stats(
    val pagesMemorized: Int,
    val pagesRemaining: Int,
    val percent: Int,
    val currentJuz: Int,
    val memoDays: Int,
    val offDays: Int,
    val missedDays: Int,
    val missedThisMonth: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val projectedFinish: LocalDate?,
    val daysToFinish: Long?,
    /** When the memorization would end at the pace of the last 30 days, missed days included. */
    val paceFinish: LocalDate? = null,
    /** Pages memorized per week over those days. */
    val pagesPerWeek: Double? = null,
)

object StatsCalculator {
    private const val PACE_DAYS = 30L

    /**
     * @param today the current plan date (already shifted by the custom day end).
     * A day counts as missed when it ended (it is before [today]) with nothing recorded.
     */
    fun compute(
        history: List<DayEntry>,
        startDate: LocalDate,
        startPage: Int,
        today: LocalDate,
        amount: Int,
        offWeekdays: Set<DayOfWeek>,
        firstKnown: Int = startPage,
    ): Stats {
        val byDate = history.associateBy { it.date }
        val memorized = history.filter { it.kind == DayKind.MEMO }
            .mapNotNull { it.pages?.to }.maxOrNull() ?: (startPage - 1)
        val total = QuranData.TOTAL_PAGES

        var missed = 0
        var missedMonth = 0
        var longest = 0
        var run = 0
        var d = startDate
        while (d.isBefore(today)) {
            if (byDate.containsKey(d)) {
                run++
                longest = maxOf(longest, run)
            } else {
                missed++
                if (d.year == today.year && d.month == today.month) missedMonth++
                run = 0
            }
            d = d.plusDays(1)
        }
        // Today only adds to the streak once it is done; it never breaks it.
        val current = if (byDate.containsKey(today)) run + 1 else run
        longest = maxOf(longest, current)

        val engine = PlanEngine(startPage, firstKnown = firstKnown)
        val firstFree = if (byDate.containsKey(today)) today.plusDays(1) else today
        val finish = engine.projectedFinish(memorized, amount, firstFree, offWeekdays)

        // The real pace: pages memorized over the last 30 ended days (or since the start, at least a week).
        val windowStart = maxOf(startDate, today.minusDays(PACE_DAYS))
        val windowDays = java.time.temporal.ChronoUnit.DAYS.between(windowStart, today)
        val windowPages = history.filter { it.kind == DayKind.MEMO && !it.date.isBefore(windowStart) && it.date.isBefore(today) }
            .sumOf { it.pages?.count ?: 0 }
        val remainingPages = total - memorized
        val pace = if (windowDays >= 7 && windowPages > 0 && remainingPages > 0) {
            val perDay = windowPages.toDouble() / windowDays
            today.plusDays(kotlin.math.ceil(remainingPages / perDay).toLong()) to perDay * 7
        } else null

        // Madinah mushaf: juz n (n >= 2) starts on page 20(n-1) + 2.
        val page = maxOf(1, minOf(total, memorized + 1))
        val juz = if (page < 22) 1 else minOf(30, (page - 2) / 20 + 1)

        return Stats(
            pagesMemorized = maxOf(0, memorized - (firstKnown - 1)),
            pagesRemaining = maxOf(0, total - memorized),
            // The share of the mushaf actually held: pages before the start only when memorized earlier.
            percent = (maxOf(0, memorized - (firstKnown - 1)) * 100) / total,
            currentJuz = juz.coerceIn(1, 30),
            memoDays = history.count { it.kind == DayKind.MEMO },
            offDays = history.count { it.kind == DayKind.OFF },
            missedDays = missed,
            missedThisMonth = missedMonth,
            currentStreak = current,
            longestStreak = longest,
            projectedFinish = finish,
            daysToFinish = finish?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) },
            paceFinish = pace?.first,
            pagesPerWeek = pace?.second,
        )
    }
}
