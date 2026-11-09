package com.fisudur.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Replays the three printed plans (one, two and three pages a day) and checks that
 * the engine gives the same linking and review ranges as the tables.
 * Columns: day, newFrom, newTo, linkFrom, linkTo, reviewFrom, reviewTo, round, reps.
 */
class PlanEngineTest {
    // Days where the printed table itself has a typo, or groups the last short surahs.
    private val knownDifferences = mapOf(
        "one" to setOf(33, 34, 35, 36, 37, 38, 130, 385, 460, 532) + (599..620),
        "two" to setOf(244, 298, 299, 300),
        "three" to setOf(98, 172, 201),
    )

    private fun replay(name: String, amount: Int) {
        val rows = javaClass.getResource("/plans/$name.csv")!!.readText().trim().lines()
            .map { l -> l.split(",").map { it.toInt() } }
        val engine = PlanEngine()
        val history = mutableListOf<DayEntry>()
        var date = LocalDate.of(2026, 1, 3)
        var checked = 0
        for (r in rows) {
            val day = r[0]
            val plan = engine.today(history, amount, PlanRules.defaultRepetitions(amount), DayKind.MEMO)
            assertEquals(day, plan.planDay)
            if (day !in knownDifferences.getValue(name)) {
                assertEquals("$name day $day linking from", r[3], plan.linking?.from ?: 0)
                assertEquals("$name day $day linking to", r[4], plan.linking?.to ?: 0)
                assertEquals("$name day $day review from", r[5], plan.review?.from ?: 0)
                assertEquals("$name day $day review to", r[6], plan.review?.to ?: 0)
                if (r[2] > 0) assertEquals("$name day $day new pages", PageRange(r[1], r[2]), plan.newPages)
                checked++
            }
            val printed = if (r[2] > 0) PageRange(r[1], r[2]) else plan.newPages
            history += DayEntry(date, DayKind.MEMO, printed)
            date = date.plusDays(1)
        }
        assertTrue(checked > rows.size - 40)
    }

    @Test fun onePagePlan() = replay("one", 1)
    @Test fun twoPagePlan() = replay("two", 2)
    @Test fun threePagePlan() = replay("three", 3)

    @Test fun changingAmountUsesWhatWasMemorized() {
        val engine = PlanEngine()
        val history = mutableListOf<DayEntry>()
        var date = LocalDate.of(2026, 1, 3)
        for (day in 1..38) {
            val amount = when {
                day <= 10 -> 1
                day <= 20 -> 3
                else -> 2
            }
            val plan = engine.today(history, amount, 40, DayKind.MEMO)
            history += DayEntry(date, DayKind.MEMO, plan.newPages)
            date = date.plusDays(1)
        }
        val day39 = engine.today(history, 2, 40, DayKind.MEMO)
        assertEquals(PageRange(77, 78), day39.newPages)
        assertEquals(PageRange(17, 74), day39.linking)
        assertEquals(PageRange(1, 3), day39.review) // 16 old pages split 3,3,3,3,2,2
    }

    @Test fun offDayHasOnlyLinkingAndReview() {
        val engine = PlanEngine()
        val history = mutableListOf<DayEntry>()
        var date = LocalDate.of(2026, 1, 3)
        repeat(40) {
            val plan = engine.today(history, 2, 40, DayKind.MEMO)
            history += DayEntry(date, DayKind.MEMO, plan.newPages)
            date = date.plusDays(1)
        }
        val off = engine.today(history, 2, 40, DayKind.OFF, date)
        assertEquals(null, off.newPages)
        assertEquals(PageRange(79, 80), off.yesterday) // yesterday was memorized: still repeated
        assertEquals(78, off.linking?.to)
        // A second off day in a row has no yesterday, so those pages join linking.
        val withOff = history + DayEntry(date, DayKind.OFF, null)
        val off2 = engine.today(withOff, 2, 40, DayKind.OFF, date.plusDays(1))
        assertEquals(null, off2.yesterday)
        assertEquals(80, off2.linking?.to)
        val next = engine.today(withOff, 2, 40, DayKind.MEMO, date.plusDays(1))
        assertEquals(off.planDay, next.planDay)
        assertTrue(next.review != off.review) // the off day used up one review portion
    }

    @Test fun dayClockWithFourAmEnd() {
        val clock = DayClock(LocalTime.of(4, 0))
        assertEquals(LocalDate.of(2026, 11, 8), clock.planDate(LocalDateTime.of(2026, 11, 9, 2, 30)))
        assertEquals(LocalDate.of(2026, 11, 9), clock.planDate(LocalDateTime.of(2026, 11, 9, 4, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 10, 4, 0), clock.dayEnd(LocalDateTime.of(2026, 11, 9, 13, 0)))
    }

    @Test fun projectedFinishSkipsFridays() {
        val engine = PlanEngine()
        val finish = engine.projectedFinish(100, 2, LocalDate.of(2026, 11, 9), setOf(DayOfWeek.FRIDAY))!!
        // 504 pages = 252 memorizing days = 42 weeks of 6 days.
        assertEquals(LocalDate.of(2026, 11, 9).plusDays(293), finish)
    }
}
