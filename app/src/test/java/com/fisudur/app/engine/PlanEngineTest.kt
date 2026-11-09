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
        assertEquals(0, d22.round)
        val d23 = engine.today(memo(22), 1, 40, DayKind.MEMO)
        assertEquals(PageRange(2, 21), d23.linking)
        assertEquals(PageRange(1, 1), d23.review) // even one page is reviewed
        assertTrue(d23.roundStartsToday)
    }

    @Test fun reviewPartsAreEven() {
        // 28 old pages make two days of 14, not 20 then 8.
        assertEquals(PageRange(1, 14), engine.part(1, 28, 2, 0))
        assertEquals(PageRange(15, 28), engine.part(1, 28, 2, 1))
        assertNull(engine.part(1, 28, 2, 2))
    }

    @Test fun eachCycleGoesOverAllOldPages() {
        val h = memo(400, 2)
        val covered = mutableSetOf<Int>()
        var cycleTo = 0
        val hist = mutableListOf<DayEntry>()
        var cycles = 0
        for (e in h) {
            val p = engine.today(hist, 2, 40, DayKind.MEMO, e.date)
            if (p.roundStartsToday) {
                if (cycleTo > 0) {
                    assertEquals((1..cycleTo).toSet(), covered)
                    cycles++
                }
                covered.clear()
                cycleTo = p.linking!!.from - 1
            }
            p.review?.let { r ->
                assertTrue(r.count <= 20)
                covered += r.from..r.to
            }
            hist += e
        }
        assertTrue(cycles > 10)
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

    @Test fun projectedFinishSkipsFridays() {
        val finish = engine.projectedFinish(100, 2, LocalDate.of(2026, 11, 9), setOf(DayOfWeek.FRIDAY))!!
        // 504 pages = 252 memorizing days = 42 weeks of 6 days.
        assertEquals(LocalDate.of(2026, 11, 9).plusDays(293), finish)
    }
}
