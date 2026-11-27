package com.fisudur.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The three layers: yesterday, linking (20 pages before it), review (everything older, in cycles). */
class PlanEngineTest {
    private val engine = PlanEngine()
    private val start = LocalDate.of(2026, 1, 3)

    /** [days] memorizing days in a row at [amount] pages, with no off days. */
    private fun memo(days: Int, amount: Int = 1): MutableList<DayEntry> {
        val h = mutableListOf<DayEntry>()
        var d = start
        repeat(days) {
            h += DayEntry(d, DayKind.MEMO, engine.today(h, amount, 40, DayKind.MEMO, d).newPages)
            d = d.plusDays(1)
        }
        return h
    }

    @Test fun exampleFromTheUser() {
        // Memorizing page 50 today, one page a day.
        val plan = engine.today(memo(49), 1, 40, DayKind.MEMO)
        assertEquals(PageRange(50, 50), plan.newPages)
        assertEquals(PageRange(49, 49), plan.yesterday)
        assertEquals(PageRange(29, 48), plan.linking)
        val r = plan.review!!
        assertTrue(r.from >= 1 && r.to <= 28)
    }

    @Test fun noReviewUntilSomethingIsOlderThanLinking() {
        val d22 = engine.today(memo(21), 1, 40, DayKind.MEMO)
        assertEquals(PageRange(21, 21), d22.yesterday)
        assertEquals(PageRange(1, 20), d22.linking)
        assertNull(d22.review)
        val d23 = engine.today(memo(22), 1, 40, DayKind.MEMO)
        assertEquals(PageRange(2, 21), d23.linking)
        assertEquals(PageRange(1, 1), d23.review) // even one page is reviewed
        val d24 = engine.today(memo(23), 1, 40, DayKind.MEMO)
        assertEquals(PageRange(2, 2), d24.review) // goes on from where it stopped
        assertEquals(PageRange(1, 1), d24.reviewWrap) // then back to the first page
    }

    @Test fun reviewRunsOnWithoutStopping() {
        val h = memo(400, 2)
        val hist = mutableListOf<DayEntry>()
        var expectedNext: Int? = null
        var wrapsSeen = 0
        for (e in h) {
            val p = engine.today(hist, 2, 40, DayKind.MEMO, e.date)
            val r = p.review
            if (r != null) {
                val oldTo = p.linking!!.from - 1
                val total = r.count + (p.reviewWrap?.count ?: 0)
                assertEquals(minOf(20, oldTo), total) // a juz a day, or all old pages if fewer
                // It starts where yesterday stopped, or at page 1 if yesterday reached the end.
                expectedNext?.let { n -> assertEquals(if (n > oldTo) 1 else n, r.from) }
                val w = p.reviewWrap
                if (w != null) {
                    assertEquals(oldTo, r.to) // reached the newest old page...
                    assertEquals(1, w.from) // ...and carried on from the first
                    wrapsSeen++
                }
                expectedNext = (w ?: r).to + 1
            }
            hist += e
        }
        assertTrue(wrapsSeen > 5)
    }

    @Test fun linkingNeverPassesTwentyPages() {
        val p = engine.today(memo(150, 3), 3, 30, DayKind.MEMO)
        assertEquals(20, p.linking!!.count)
        assertEquals(p.yesterday!!.from - 1, p.linking!!.to)
    }

    @Test fun offDays() {
        val h = memo(40, 2)
        val date = start.plusDays(40)
        val off = engine.today(h, 2, 40, DayKind.OFF, date)
        assertNull(off.newPages)
        assertEquals(PageRange(79, 80), off.yesterday) // yesterday was memorized: still repeated
        assertEquals(PageRange(59, 78), off.linking)
        // A second off day in a row has no yesterday; linking is the last 20 pages.
        val withOff = h + DayEntry(date, DayKind.OFF, null)
        val off2 = engine.today(withOff, 2, 40, DayKind.OFF, date.plusDays(1))
        assertNull(off2.yesterday)
        assertEquals(PageRange(61, 80), off2.linking)
        val next = engine.today(withOff, 2, 40, DayKind.MEMO, date.plusDays(1))
        assertEquals(off.planDay, next.planDay)
        assertTrue(next.review != off.review) // the off day used up one review part
    }

    @Test fun changingAmountWorksByPages() {
        val h = mutableListOf<DayEntry>()
        var d = start
        for (day in 1..38) {
            val amount = if (day <= 10) 1 else if (day <= 20) 3 else 2
            h += DayEntry(d, DayKind.MEMO, engine.today(h, amount, 40, DayKind.MEMO, d).newPages)
            d = d.plusDays(1)
        }
        val p = engine.today(h, 2, 40, DayKind.MEMO)
        assertEquals(PageRange(77, 78), p.newPages)
        assertEquals(PageRange(75, 76), p.yesterday)
        assertEquals(PageRange(55, 74), p.linking)
    }

    @Test fun startingMidMushaf() {
        val e = PlanEngine(startPage = 300)
        val h = mutableListOf<DayEntry>()
        var d = start
        repeat(30) {
            h += DayEntry(d, DayKind.MEMO, e.today(h, 1, 40, DayKind.MEMO, d).newPages)
            d = d.plusDays(1)
        }
        val p = e.today(h, 1, 40, DayKind.MEMO)
        assertEquals(PageRange(330, 330), p.newPages)
        assertEquals(PageRange(309, 328), p.linking)
        assertTrue(p.review!!.from >= 300)
    }

    @Test fun dayClockWithFourAmEnd() {
        val clock = DayClock(LocalTime.of(4, 0))
        assertEquals(LocalDate.of(2026, 11, 8), clock.planDate(LocalDateTime.of(2026, 11, 9, 2, 30)))
        assertEquals(LocalDate.of(2026, 11, 9), clock.planDate(LocalDateTime.of(2026, 11, 9, 4, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 10, 4, 0), clock.dayEnd(LocalDateTime.of(2026, 11, 9, 13, 0)))
    }

    @Test fun dayClockWithMidnightEnd() {
        val clock = DayClock(LocalTime.MIDNIGHT)
        assertEquals(LocalDate.of(2026, 11, 9), clock.planDate(LocalDateTime.of(2026, 11, 9, 23, 59)))
        assertEquals(LocalDateTime.of(2026, 11, 10, 0, 0), clock.dayEnd(LocalDateTime.of(2026, 11, 9, 13, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 9, 0, 0), clock.dayStart(LocalDateTime.of(2026, 11, 9, 13, 0)))
    }

    @Test fun lateDayEndIsNamedAfterTheDateItEndsOn() {
        // Day ends at 22:00: "Friday" runs from Thursday 22:00 to Friday 22:00.
        val clock = DayClock(LocalTime.of(22, 0))
        val friday = LocalDate.of(2026, 11, 27)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        assertEquals(friday, clock.planDate(LocalDateTime.of(2026, 11, 26, 22, 0)))
        assertEquals(friday, clock.planDate(LocalDateTime.of(2026, 11, 27, 21, 59)))
        assertEquals(friday.plusDays(1), clock.planDate(LocalDateTime.of(2026, 11, 27, 22, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 26, 22, 0), clock.startOf(friday))
        assertEquals(LocalDateTime.of(2026, 11, 27, 22, 0), clock.dayEnd(LocalDateTime.of(2026, 11, 27, 9, 0)))
        assertEquals(clock.planDate(clock.startOf(friday)), friday)
    }

    @Test fun startOfIsTheMomentTheNamedDayBegins() {
        for (end in listOf(LocalTime.MIDNIGHT, LocalTime.of(4, 0), LocalTime.of(12, 0), LocalTime.of(13, 30), LocalTime.of(23, 0))) {
            val clock = DayClock(end)
            val d = LocalDate.of(2026, 3, 1)
            val start = clock.startOf(d)
            assertEquals("$end", d, clock.planDate(start))
            assertEquals("$end", d.minusDays(1), clock.planDate(start.minusMinutes(1)))
            assertEquals("$end", start.plusDays(1), clock.dayEnd(start))
        }
    }

    @Test fun projectedFinishSkipsFridays() {
        val finish = engine.projectedFinish(100, 2, LocalDate.of(2026, 11, 9), setOf(DayOfWeek.FRIDAY))!!
        // 504 pages = 252 memorizing days = 42 weeks of 6 days.
        assertEquals(LocalDate.of(2026, 11, 9).plusDays(293), finish)
    }

    @Test fun pagesMemorizedBeforeTheStartJoinLinkingAndReview() {
        // Starting at page 300 with pages 1..299 already held.
        val e = PlanEngine(startPage = 300, firstKnown = 1)
        val day1 = e.today(emptyList(), 1, 40, DayKind.MEMO, start)
        assertEquals(PageRange(300, 300), day1.newPages)
        assertEquals(PageRange(280, 299), day1.linking)
        assertEquals(PageRange(1, 20), day1.review)
        val h = listOf(DayEntry(start, DayKind.MEMO, PageRange(300, 300)))
        val day2 = e.today(h, 1, 40, DayKind.MEMO, start.plusDays(1))
        assertEquals(PageRange(300, 300), day2.yesterday)
        assertEquals(PageRange(21, 40), day2.review)
    }

    @Test fun withoutEarlierPagesTheStartIsTheFirstPage() {
        val e = PlanEngine(startPage = 300)
        val day1 = e.today(emptyList(), 1, 40, DayKind.MEMO, start)
        assertNull(day1.linking)
        assertNull(day1.review)
    }

    @Test fun statsCountOnlyPagesHeld() {
        val h = listOf(DayEntry(start, DayKind.MEMO, PageRange(300, 301)))
        val fresh = StatsCalculator.compute(h, start, 300, start.plusDays(1), 2, emptySet())
        assertEquals(2, fresh.pagesMemorized)
        assertEquals(0, fresh.percent)
        val known = StatsCalculator.compute(h, start, 300, start.plusDays(1), 2, emptySet(), firstKnown = 1)
        assertEquals(301, known.pagesMemorized)
        assertEquals(49, known.percent)
    }

    @Test fun paceFollowsTheLastThirtyDays() {
        // One page every other day for 30 days: 15 pages, 3.5 a week.
        val h = mutableListOf<DayEntry>()
        var p = 1
        for (i in 0 until 30) {
            val d = start.plusDays(i.toLong())
            h += if (i % 2 == 0) DayEntry(d, DayKind.MEMO, PageRange(p, p).also { p++ }) else DayEntry(d, DayKind.OFF, null, "عمل")
        }
        val today = start.plusDays(30)
        val st = StatsCalculator.compute(h, start, 1, today, 1, emptySet())
        assertEquals(3.5, st.pagesPerWeek!!, 0.01)
        // 589 pages left at half a page a day.
        assertEquals(today.plusDays(1178), st.paceFinish)
    }
}
