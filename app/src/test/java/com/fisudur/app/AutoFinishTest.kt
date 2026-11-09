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
}
