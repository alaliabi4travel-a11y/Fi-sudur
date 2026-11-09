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
)

object StatsCalculator {
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

        val engine = PlanEngine(startPage)
        val firstFree = if (byDate.containsKey(today)) today.plusDays(1) else today
        val finish = engine.projectedFinish(memorized, amount, firstFree, offWeekdays)

        // Madinah mushaf: juz n (n >= 2) starts on page 20(n-1) + 2.
        val page = maxOf(1, minOf(total, memorized + 1))
        val juz = if (page < 22) 1 else minOf(30, (page - 2) / 20 + 1)

        return Stats(
            pagesMemorized = maxOf(0, memorized - (startPage - 1)),
            pagesRemaining = maxOf(0, total - memorized),
            percent = (memorized * 100) / total,
            currentJuz = juz.coerceIn(1, 30),
            memoDays = history.count { it.kind == DayKind.MEMO },
            offDays = history.count { it.kind == DayKind.OFF },
            missedDays = missed,
            missedThisMonth = missedMonth,
            currentStreak = current,
            longestStreak = longest,
            projectedFinish = finish,
            daysToFinish = finish?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) },
        )
    }
}
