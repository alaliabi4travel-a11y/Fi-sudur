package com.fisudur.app.engine

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The scheduling rules shared by the three printed plans (one page, two pages,
 * three pages a day). They differ only in the daily amount, so the same rules
 * work when the amount changes in the middle of the plan.
 *
 * Plan day d (1-based) is the d-th day that counts toward the plan:
 *  - New pages: the next [amount] pages after everything memorized so far.
 *  - Yesterday's repetition: the pages memorized on day d-1.
 *  - Linking (ربط): from the linking start up to the pages memorized on day d-2.
 *  - Rounds (جولات): round k starts on day 33 + 6(k-1) and lasts 6 days. When it
 *    starts, the pages memorized in the first 6k days leave linking and become
 *    "old memorization", which is reviewed once over the 6 days of the round.
 *
 * These rules reproduce the printed tables day by day.
 */
object PlanRules {
    const val FIRST_ROUND_DAY = 33
    const val ROUND_LENGTH = 6
    const val YESTERDAY_REPS = 5
    const val LISTEN_REPS = 3
    const val TAFSIR_REPS = 1
    const val RECORD_REPS = 3

    fun defaultRepetitions(amount: Int): Int = if (amount >= 3) 30 else 40

    /** Number of the round in progress on plan day [day], or 0 before the first round. */
    fun roundOf(day: Int): Int =
        if (day < FIRST_ROUND_DAY) 0 else (day - FIRST_ROUND_DAY) / ROUND_LENGTH + 1

    /** First plan day of round [k]. */
    fun roundStartDay(k: Int): Int = FIRST_ROUND_DAY + (k - 1) * ROUND_LENGTH
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
)

data class TodayPlan(
    val planDay: Int,
    val kind: DayKind,
    val newPages: PageRange?,
    val repetitions: Int,
    val yesterday: PageRange?,
    val linking: PageRange?,
    val review: PageRange?,
    val round: Int,
    /** True on the first day of a round (the printed tables show the round number there). */
    val roundStartsToday: Boolean,
    val memorizationDone: Boolean,
)

/**
 * Computes today's plan from the history of completed days.
 *
 * Off days (Friday, sickness, travel) do not memorize new pages and do not move the
 * plan day forward, but they still get linking and the next review portion.
 */
class PlanEngine(
    private val startPage: Int = 1,
    private val totalPages: Int = QuranData.TOTAL_PAGES,
) {
    fun today(history: List<DayEntry>, amount: Int, repetitions: Int, todayKind: DayKind): TodayPlan {
        val memoDays = history.filter { it.kind == DayKind.MEMO }
        // cumEnd[i] = last page memorized after i plan days (cumEnd[0] = page before start).
        val cumEnd = IntArray(memoDays.size + 1)
        cumEnd[0] = startPage - 1
        for ((i, e) in memoDays.withIndex()) {
            cumEnd[i + 1] = maxOf(cumEnd[i], e.pages?.to ?: cumEnd[i])
        }
        val done = memoDays.size
        val day = done + 1
        fun endAfter(days: Int): Int = cumEnd[days.coerceIn(0, done)]

        val memorized = endAfter(done)
        val memoDone = memorized >= totalPages

        val newPages = if (todayKind == DayKind.MEMO && !memoDone) {
            PageRange(memorized + 1, minOf(totalPages, memorized + amount))
        } else null

        val yesterday = memoDays.lastOrNull()?.pages?.takeIf { todayKind == DayKind.MEMO }

        val round = PlanRules.roundOf(day)
        val linkingStart = if (round == 0) startPage else endAfter(PlanRules.ROUND_LENGTH * round) + 1
        // On an off day there is no "yesterday's repetition", so those pages join linking.
        val linkingEnd = if (todayKind == DayKind.OFF) endAfter(day - 1) else endAfter(day - 2)
        val linking = if (linkingEnd >= linkingStart) PageRange(linkingStart, linkingEnd) else null

        var review: PageRange? = null
        var startsToday = false
        if (round > 0) {
            val oldFrom = startPage
            val oldTo = linkingStart - 1
            // Days of this round already lived (memo + off days), so off days take the next portion.
            val roundStart = PlanRules.roundStartDay(round)
            val memoDaysInRound = day - roundStart // memo days completed since the round began
            val offDaysInRound = countOffDaysSince(history, memoDays, roundStart)
            val index = (memoDaysInRound + offDaysInRound) % PlanRules.ROUND_LENGTH
            startsToday = memoDaysInRound == 0 && offDaysInRound == 0
            review = portion(oldFrom, oldTo, index)
        }

        return TodayPlan(
            planDay = day,
            kind = todayKind,
            newPages = newPages,
            repetitions = repetitions,
            yesterday = yesterday,
            linking = linking,
            review = review,
            round = round,
            roundStartsToday = startsToday,
            memorizationDone = memoDone,
        )
    }

    /** Off days recorded after the memo day that opened round starting at [roundStartDay]. */
    private fun countOffDaysSince(history: List<DayEntry>, memoDays: List<DayEntry>, roundStartDay: Int): Int {
        // Plan day roundStartDay is reached once (roundStartDay - 1) memo days are complete.
        val before = roundStartDay - 1
        val anchor: LocalDate? = if (before == 0) null else memoDays.getOrNull(before - 1)?.date
        return history.count { it.kind == DayKind.OFF && (anchor == null || it.date.isAfter(anchor)) }
    }

    /** Splits [from..to] into 6 near-equal portions and returns portion [index]. */
    fun portion(from: Int, to: Int, index: Int): PageRange? {
        val total = to - from + 1
        if (total <= 0) return null
        val n = PlanRules.ROUND_LENGTH
        val base = total / n
        val extra = total % n
        // The first `extra` portions get one more page.
        val start = from + index * base + minOf(index, extra)
        val size = base + if (index < extra) 1 else 0
        if (size <= 0) return null
        return PageRange(start, start + size - 1)
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
