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
 *  - After the khatm (consolidation): no new pages and no linking; the review covers the
 *    whole memorization once every [KHATM_DAYS] days.
 */
object PlanRules {
    const val LINK_PAGES = 20
    const val REVIEW_PAGES = 20
    const val YESTERDAY_REPS = 5
    const val RECORD_REPS = 3
    const val LISTEN_REPS = 3
    const val TAFSIR_REPS = 1
    /** Readings a day for each weak page, and for how many recorded days. */
    const val WEAK_REPS = 3
    const val WEAK_DAYS = 3
    /** After the khatm, the whole memorization is reviewed once every this many days. */
    const val KHATM_DAYS = 6

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
    /**
     * Half a page a day: 1 when this day memorized the first half of the next page (its pages
     * stay null, as the page is not complete), 2 when it completed the page with its second half.
     */
    val half: Int = 0,
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
    /** Half a page a day: 1 = today is the first half of [newPages], 2 = its second half; 0 = whole pages. */
    val half: Int = 0,
    /** When yesterday was a first half, yesterday's repetition is that half: 1. */
    val yesterdayHalf: Int = 0,
) {
    /** The pages today's entry records: none for a first half, the page is complete only with its second. */
    val recordedPages: PageRange? get() = if (half == 1) null else newPages
}

/**
 * Computes today's plan from the history of completed days.
 *
 * Off days (Friday, sickness, travel) do not memorize new pages and do not move the
 * plan day forward, but they still get linking and the next review part.
 */
/** The daily amount that stands for half a page. */
const val HALF = 0

class PlanEngine(
    private val startPage: Int = 1,
    private val totalPages: Int = QuranData.TOTAL_PAGES,
    /**
     * The first page already held before the plan: [startPage] normally, or 1 when the pages
     * before the start were memorized earlier, so they join linking and review from day one.
     */
    private val firstKnown: Int = startPage,
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
        if (memorized >= totalPages) {
            // After the khatm there is no linking: every old page is in the review, a khatm every few days.
            return Layers(yesterday, null, if (yesterday != null) yesterday.from - 1 else memorized)
        }
        val linkEnd = if (yesterday != null) yesterday.from - 1 else memorized
        val linkFrom = maxOf(firstKnown, linkEnd - PlanRules.LINK_PAGES + 1)
        val linking = if (linkEnd >= linkFrom) PageRange(linkFrom, linkEnd) else null
        return Layers(yesterday, linking, if (linking != null) linking.from - 1 else firstKnown - 1)
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

        // Half a page a day (amount 0): the first half of the next page, then its second half.
        val lastMemoEntry = sorted.lastOrNull { it.kind == DayKind.MEMO }
        val afterFirstHalf = lastMemoEntry?.half == 1
        val half = if (amount == HALF && todayKind == DayKind.MEMO && !memoDone) (if (afterFirstHalf) 2 else 1) else 0
        val newPages = if (todayKind == DayKind.MEMO && !memoDone) {
            PageRange(memorized + 1, minOf(totalPages, memorized + maxOf(1, amount)))
        } else null

        // Walk every recorded day, then today, keeping the review's place.
        var cursor = firstKnown
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
            if (l.oldTo >= firstKnown) {
                val old = l.oldTo - firstKnown + 1
                val amount = if (soFar >= totalPages) {
                    // Consolidation: the whole memorization reviewed every KHATM_DAYS days.
                    (old + PlanRules.KHATM_DAYS - 1) / PlanRules.KHATM_DAYS
                } else {
                    minOf(PlanRules.REVIEW_PAGES, old)
                }
                if (cursor > l.oldTo || cursor < firstKnown) cursor = firstKnown
                fromStart = cursor == firstKnown
                first = PageRange(cursor, minOf(l.oldTo, cursor + amount - 1))
                val rest = amount - first.count
                if (rest > 0) {
                    second = PageRange(firstKnown, firstKnown + rest - 1)
                    cursor = firstKnown + rest
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
        // After a first half, yesterday's repetition is that half of the page being memorized
        // (on an off day only when the first half was memorized the day before).
        val yesterdayHalfPage = if (afterFirstHalf && !memoDone && (todayKind == DayKind.MEMO || last === lastMemoEntry)) {
            PageRange(memorized + 1, memorized + 1)
        } else null

        return TodayPlan(
            planDay = memoDays + 1,
            kind = todayKind,
            newPages = newPages,
            repetitions = repetitions,
            yesterday = yesterdayHalfPage ?: l.yesterday,
            linking = l.linking,
            review = review,
            reviewWrap = reviewWrap,
            round = wraps,
            roundStartsToday = startsToday,
            memorizationDone = memoDone,
            half = half,
            yesterdayHalf = if (yesterdayHalfPage != null) 1 else 0,
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
        if (amount < 0) return null
        // Counted in half pages, so half a page a day (amount 0) works like the rest.
        val step = if (amount == HALF) 1 else amount * 2
        var remaining = (totalPages - memorized) * 2
        if (remaining <= 0) return null
        var date = from
        var guard = 0
        while (true) {
            if (date.dayOfWeek !in offWeekdays) {
                remaining -= step
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
 *
 * A day is named after the calendar date most of it falls on: with an early end
 * (midnight to noon, e.g. 04:00) that is the date it starts on, so 02:00 still counts
 * as the evening before; with a late end (after noon, e.g. 22:00) it is the date it
 * ends on, so Friday runs from Thursday 22:00 to Friday 22:00.
 */
class DayClock(private val endTime: LocalTime) {
    private val shift: Duration = Duration.ofSeconds(endTime.toSecondOfDay().toLong())
    private val late: Boolean = endTime.isAfter(LocalTime.NOON)

    /** The plan date that [now] belongs to. */
    fun planDate(now: LocalDateTime): LocalDate {
        val base = now.minus(shift).toLocalDate()
        return if (late) base.plusDays(1) else base
    }

    /** When the plan day named [date] begins. */
    fun startOf(date: LocalDate): LocalDateTime =
        (if (late) date.minusDays(1) else date).atStartOfDay().plus(shift)

    /** When the plan day containing [now] ends. */
    fun dayEnd(now: LocalDateTime): LocalDateTime = startOf(planDate(now).plusDays(1))

    fun dayStart(now: LocalDateTime): LocalDateTime = startOf(planDate(now))

    fun remaining(now: LocalDateTime): Duration = Duration.between(now, dayEnd(now))
}
