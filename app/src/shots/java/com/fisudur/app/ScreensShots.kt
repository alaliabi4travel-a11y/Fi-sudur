package com.fisudur.app

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.fisudur.app.data.DayProgress
import com.fisudur.app.data.Settings
import com.fisudur.app.engine.DayEntry
import com.fisudur.app.engine.DayKind
import com.fisudur.app.engine.PlanEngine
import com.fisudur.app.engine.PlanRules
import com.fisudur.app.ui.AppTheme
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Renders each screen with sample data so the design can be checked without a phone. */
class ScreensShots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 100.0)

    private val start = LocalDate.of(2026, 9, 1)
    private val today = LocalDate.of(2026, 11, 9)
    private val settings = Settings(amount = 2, dayEndMinutes = 4 * 60, startDate = start)

    /** Two months of real plan history, with a couple of missed days. */
    private val history: List<DayEntry> = buildList {
        val engine = PlanEngine(1)
        var d = start
        while (d.isBefore(today)) {
            if (d.dayOfMonth != 14 && d.dayOfMonth != 27) {
                val kind = if (d.dayOfWeek == DayOfWeek.FRIDAY) DayKind.OFF else DayKind.MEMO
                val plan = engine.today(this, 2, PlanRules.defaultRepetitions(2), kind)
                add(DayEntry(d, kind, plan.newPages))
            }
            d = d.plusDays(1)
        }
    }

    private fun state(
        s: Settings = settings,
        checked: Set<String> = setOf("yesterday", "listen", "tafsir"),
        counter: Int = 27,
    ) = buildUiState(
        LocalDateTime.of(today, LocalTime.of(21, 35)),
        s,
        history,
        DayProgress(today, checked, counter),
    )

    private val noRegistry = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {}
        }
    }

    private fun shot(tab: Int, s: UiState = state(), dark: Boolean = false) {
        paparazzi.snapshot {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides noRegistry) {
                AppTheme(dark = dark) { AppRoot(s, NoActions, tab) {} }
            }
        }
    }

    @Test fun home() = shot(0)
    @Test fun homeDark() = shot(0, dark = true)
    @Test fun counter() = shot(1)
    @Test fun recordings() = shot(2)
    @Test fun stats() = shot(3)
    @Test fun settings() = shot(4)
    @Test fun setup() = shot(0, state(s = Settings()))
}
