package com.fisudur.app.engine

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The plan in three layers, counted in pages so it works for any daily amount
 * (one, two or three pages, changeable at any time):
 *  - Yesterday's repetition: the pages memorized on the last memorizing day.
 *  - Linking (ربط): the [LINK_PAGES] pages right before yesterday's pages, no more.
 *  - Review (مراجعة): everything older than linking, even a single page. It runs on
 *    without stopping: each day takes up to [REVIEW_PAGES] pages from where it stopped,
 *    and on reaching the newest old page it carries on from the first one.
 */
object PlanRules {
    const val LINK_PAGES = 20
    const val REVIEW_PAGES = 20
    const val YESTERDAY_REPS = 5
    const val RECORD_REPS = 3
    const val LISTEN_REPS = 3
    const val TAFSIR_REPS = 1

    fun defaultRepetitions(amount: Int): Int = if (amount >= 3) 30 else 40
}

data class PageRange(val from: Int, val to: Int) {
    val count: Int get() = to - from + 1
    val label: String get() = if (from == to) "$from" else "$from ← $to"
    val surahs: String get() = QuranData.surahLabel(from, to)
}

enum class DayKind { MEMO, OFF }

/** One completed day in the history. */
data class DayEntry(
    val date: LocalDate,
    val kind: DayKind,
    /** Pages memorized that day (null on an off day or after the whole Quran is memorized). */
    val pages: PageRange?,
    /** Why an off day was taken outside the weekly off days: work, travel, sickness. */
    val reason: String? = null,
)

data class TodayPlan(
    val planDay: Int,
    val kind: DayKind,
    val newPages: PageRange?,
    val repetitions: Int,
    val yesterday: PageRange?,
    val linking: PageRange?,
    val review: PageRange?,
    /** When today's review reaches the newest old page, it carries on from the first: this part. */
    val reviewWrap: PageRange? = null,
    /** How many times the review has gone back to the first page. */
    val round: Int,
    /** True when today's review starts from the first page. */
    val roundStartsToday: Boolean,
    val memorizationDone: Boolean,
)

/**
 * Computes today's plan from the history of completed days.
 *
 * Off days (Friday, sickness, travel) do not memorize new pages and do not move the
 * plan day forward, but they still get linking and the next review part.
 */
class PlanEngine(
    private val startPage: Int = 1,
    private val totalPages: Int = QuranData.TOTAL_PAGES,
) {
    private class Layers(val yesterday: PageRange?, val linking: PageRange?, val oldTo: Int)

    /**
     * Yesterday, linking and where old pages end, for a day of [kind] on [date], given what
     * came before it: pages memorized so far, the last memorizing day and the last recorded day.
     */
    private fun layers(memorized: Int, lastMemo: DayEntry?, last: DayEntry?, kind: DayKind, date: LocalDate?): Layers {
        val yesterday = if (kind == DayKind.MEMO) {
            lastMemo?.pages
        } else {
            // Off day: repeat yesterday only when yesterday itself was a memorizing day.
            last?.takeIf { it.kind == DayKind.MEMO && (date == null || it.date == date.minusDays(1)) }?.pages
        }
        val linkEnd = if (yesterday != null) yesterday.from - 1 else memorized
        val linkFrom = maxOf(startPage, linkEnd - PlanRules.LINK_PAGES + 1)
        val linking = if (linkEnd >= linkFrom) PageRange(linkFrom, linkEnd) else null
        return Layers(yesterday, linking, if (linking != null) linking.from - 1 else startPage - 1)
    }

    /**
     * [today] is the plan date. On an off day it decides whether yesterday was a memorizing day,
     * in which case yesterday's pages are still repeated five times.
     */
    fun today(
        history: List<DayEntry>,
        amount: Int,
        repetitions: Int,
        todayKind: DayKind,
        today: LocalDate? = null,
    ): TodayPlan {
        val sorted = history.sortedBy { it.date }
        val memoDays = sorted.count { it.kind == DayKind.MEMO }
        val memorized = maxOf(startPage - 1, sorted.filter { it.kind == DayKind.MEMO }.maxOfOrNull { it.pages?.to ?: 0 } ?: 0)
        val memoDone = memorized >= totalPages

        val newPages = if (todayKind == DayKind.MEMO && !memoDone) {
            PageRange(memorized + 1, minOf(totalPages, memorized + amount))
        } else null

        // Walk every recorded day, then today, keeping the review's place.
        var cursor = startPage
        var wraps = 0
        var review: PageRange? = null
        var reviewWrap: PageRange? = null
        var startsToday = false
        var todayLayers: Layers? = null
        var soFar = startPage - 1
        var lastMemo: DayEntry? = null
        var last: DayEntry? = null
        for (i in 0..sorted.size) {
            val isToday = i == sorted.size
            val e = if (isToday) null else sorted[i]
            val l = layers(soFar, lastMemo, last, e?.kind ?: todayKind, e?.date ?: today)
            var first: PageRange? = null
            var second: PageRange? = null
            var fromStart = false
            if (l.oldTo >= startPage) {
                val amount = minOf(PlanRules.REVIEW_PAGES, l.oldTo - startPage + 1)
                if (cursor > l.oldTo || cursor < startPage) cursor = startPage
                fromStart = cursor == startPage
                first = PageRange(cursor, minOf(l.oldTo, cursor + amount - 1))
                val rest = amount - first.count
                if (rest > 0) {
                    second = PageRange(startPage, startPage + rest - 1)
                    cursor = startPage + rest
                    wraps++
                } else {
                    cursor = first.to + 1
                }
            }
            if (isToday) {
                todayLayers = l
                review = first
                reviewWrap = second
                startsToday = fromStart
            } else {
                if (e!!.kind == DayKind.MEMO) {
                    lastMemo = e
                    soFar = maxOf(soFar, e.pages?.to ?: soFar)
                }
                last = e
            }
        }
        val l = todayLayers!!

        return TodayPlan(
            planDay = memoDays + 1,
            kind = todayKind,
            newPages = newPages,
            repetitions = repetitions,
            yesterday = l.yesterday,
            linking = l.linking,
            review = review,
            reviewWrap = reviewWrap,
            round = wraps,
            roundStartsToday = startsToday,
            memorizationDone = memoDone,
        )
    }

    /**
     * Calendar date the memorization is expected to finish on, if the current
     * amount is kept and every non-off day is done.
     */
    fun projectedFinish(
        memorized: Int,
        amount: Int,
        from: LocalDate,
        offWeekdays: Set<DayOfWeek>,
    ): LocalDate? {
        if (amount <= 0) return null
        var remaining = totalPages - memorized
        if (remaining <= 0) return null
        var date = from
        var guard = 0
        while (true) {
            if (date.dayOfWeek !in offWeekdays) {
                remaining -= amount
                if (remaining <= 0) return date
            }
            date = date.plusDays(1)
            if (++guard > 20000) return null
        }
    }
}

/**
 * The person's own day: it ends at [endTime] (midnight by default, or e.g. 04:00)
 * and the next day starts at that same moment.
 */
class DayClock(private val endTime: LocalTime) {
    private val shift: Duration = Duration.ofSeconds(endTime.toSecondOfDay().toLong())

    /** The plan date that [now] belongs to. Before 04:00 still counts as the previous date. */
    fun planDate(now: LocalDateTime): LocalDate = now.minus(shift).toLocalDate()

    /** When the plan day containing [now] ends. */
    fun dayEnd(now: LocalDateTime): LocalDateTime = planDate(now).plusDays(1).atStartOfDay().plus(shift)

    fun dayStart(now: LocalDateTime): LocalDateTime = planDate(now).atStartOfDay().plus(shift)

    fun remaining(now: LocalDateTime): Duration = Duration.between(now, dayEnd(now))
}
