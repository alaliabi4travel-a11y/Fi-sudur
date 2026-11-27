package com.fisudur.app

import com.fisudur.app.data.DayProgress
import com.fisudur.app.data.Settings
import com.fisudur.app.engine.DayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AutoFinishTest {
    private val start = LocalDate.of(2026, 11, 2) // a Monday
    private val settings = Settings(amount = 1, startDate = start, dayEndMinutes = 4 * 60)

    @Test
    fun fullyCheckedDayIsRecorded() {
        val all = setOf("tafsir", "listen", "memorize", "record", "repeat")
        val e = autoFinishEntry(settings, emptyList(), DayProgress(start, checked = all), start.plusDays(1))
        assertEquals(DayKind.MEMO, e!!.kind)
        assertEquals(1, e.pages!!.from)
    }

    @Test
    fun partlyCheckedDayStaysOpen() {
        assertNull(autoFinishEntry(settings, emptyList(), DayProgress(start, checked = setOf("tafsir")), start.plusDays(1)))
    }

    @Test
    fun todayIsNeverAutoFinished() {
        val all = setOf("tafsir", "listen", "memorize", "record", "repeat")
        assertNull(autoFinishEntry(settings, emptyList(), DayProgress(start, checked = all), start))
    }

    @Test
    fun chosenOffDayWithReasonIsRecorded() {
        val p = DayProgress(start, kindOverride = DayKind.OFF, reason = "سفر")
        val e = autoFinishEntry(settings, emptyList(), p, start.plusDays(2))
        assertEquals(DayKind.OFF, e!!.kind)
        assertEquals("سفر", e.reason)
    }

    @Test
    fun lateDayEndRecordsTheRightDay() {
        val late = settings.copy(dayEndMinutes = 22 * 60)
        val all = setOf("tafsir", "listen", "memorize", "record", "repeat")
        val e = autoFinishEntry(late, emptyList(), DayProgress(start, checked = all), start.plusDays(1))
        assertEquals(start, e!!.date)
        assertEquals(1, e.pages!!.from)
    }

    @Test
    fun changingDayEndAfterMidnightKeepsTodaysProgress() {
        // At 01:00 on the 10th, midnight → 04:00 turns "today" (the 10th) into the 9th.
        val now = java.time.LocalDateTime.of(2026, 11, 10, 1, 0)
        val p = DayProgress(LocalDate.of(2026, 11, 10), checked = setOf("tafsir"), counter = 12)
        val moved = carryProgress(java.time.LocalTime.MIDNIGHT, java.time.LocalTime.of(4, 0), now, p, emptyList())!!
        assertEquals(LocalDate.of(2026, 11, 9), moved.date)
        assertEquals(setOf("tafsir"), moved.checked)
        assertEquals(12, moved.counter)
    }

    @Test
    fun changingDayEndKeepsProgressWhenTheDateStays() {
        val now = java.time.LocalDateTime.of(2026, 11, 10, 15, 0)
        val p = DayProgress(LocalDate.of(2026, 11, 10), checked = setOf("tafsir"))
        assertEquals(p, carryProgress(java.time.LocalTime.MIDNIGHT, java.time.LocalTime.of(4, 0), now, p, emptyList()))
    }

    @Test
    fun progressDoesNotMoveOntoARecordedDay() {
        val now = java.time.LocalDateTime.of(2026, 11, 10, 1, 0)
        val p = DayProgress(LocalDate.of(2026, 11, 10), checked = setOf("tafsir"))
        val done = listOf(com.fisudur.app.engine.DayEntry(LocalDate.of(2026, 11, 9), DayKind.MEMO, null))
        assertEquals(p, carryProgress(java.time.LocalTime.MIDNIGHT, java.time.LocalTime.of(4, 0), now, p, done))
    }

    @Test
    fun reviewStepShowsItsRound() {
        // 23 one-page days: review has started, first round.
        var h = emptyList<com.fisudur.app.engine.DayEntry>()
        var d = start
        val one = settings.copy(offWeekdays = emptySet())
        repeat(23) {
            val s = buildUiState(d.atTime(12, 0), one, h, null)
            h = h + com.fisudur.app.engine.DayEntry(d, DayKind.MEMO, s.plan.newPages)
            d = d.plusDays(1)
        }
        val s = buildUiState(d.atTime(12, 0), one, h, null)
        val review = s.steps.first { it.id == "review" }
        assertEquals(true, review.title.startsWith("المراجعة، الجولة"))
    }

    @Test
    fun hijriDateIsUmmAlQura() {
        // 1 Ramadan 1447 = 18 February 2026 (Umm al-Qura).
        assertEquals("1 رمضان 1447", com.fisudur.app.ui.Fmt.hijri(LocalDate.of(2026, 2, 18)))
    }

    @Test
    fun weakPagesAddAStepAndMustBeDoneToAutoFinish() {
        val weak = mapOf(5 to 3, 9 to 1)
        val s = buildUiState(start.atTime(12, 0), settings, emptyList(), null, weak)
        val step = s.steps.first { it.id == "weak" }
        assertEquals(listOf(5, 9), step.pageList)
        val all = setOf("tafsir", "listen", "memorize", "record", "repeat")
        assertNull(autoFinishEntry(settings, emptyList(), DayProgress(start, checked = all), start.plusDays(1), weak))
        val e = autoFinishEntry(settings, emptyList(), DayProgress(start, checked = all + "weak"), start.plusDays(1), weak)
        assertEquals(DayKind.MEMO, e!!.kind)
    }

    @Test
    fun linkingAndReviewCarryTheirPages() {
        var h = emptyList<com.fisudur.app.engine.DayEntry>()
        var d = start
        val one = settings.copy(offWeekdays = emptySet())
        repeat(25) {
            val s = buildUiState(d.atTime(12, 0), one, h, null)
            h = h + com.fisudur.app.engine.DayEntry(d, DayKind.MEMO, s.plan.newPages)
            d = d.plusDays(1)
        }
        val s = buildUiState(d.atTime(12, 0), one, h, null)
        assertEquals(20, s.steps.first { it.id == "link" }.pageList.size)
        assertEquals(listOf(25), s.steps.first { it.id == "yesterday" }.pageList)
        assertEquals(true, s.steps.first { it.id == "review" }.pageList.isNotEmpty())
    }
}
