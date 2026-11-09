package com.fisudur.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fisudur.app.data.DayProgress
import com.fisudur.app.data.Settings
import com.fisudur.app.data.Store
import com.fisudur.app.engine.DayClock
import com.fisudur.app.engine.DayEntry
import com.fisudur.app.engine.DayKind
import com.fisudur.app.engine.PlanEngine
import com.fisudur.app.engine.PlanRules
import com.fisudur.app.engine.Stats
import com.fisudur.app.engine.StatsCalculator
import com.fisudur.app.engine.TodayPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

enum class StepAction { CHECK, COUNTER, RECORD }

data class Step(
    val id: String,
    val title: String,
    val detail: String?,
    val times: String,
    val action: StepAction = StepAction.CHECK,
    val target: Int = 0,
)

data class UiState(
    val now: LocalDateTime,
    val settings: Settings,
    val history: List<DayEntry>,
    val planDate: LocalDate,
    val progress: DayProgress,
    val plan: TodayPlan,
    val steps: List<Step>,
    val doneToday: DayEntry?,
    val stats: Stats,
) {
    val needsSetup: Boolean get() = settings.startDate == null
    val clock: DayClock get() = DayClock(settings.dayEnd)
    val allChecked: Boolean get() = steps.all { it.id in progress.checked }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private var settings = store.loadSettings()
    private var history = store.loadHistory()
    private var progress: DayProgress? = store.loadProgress()

    private val _state = MutableStateFlow(build(LocalDateTime.now()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                refresh()
            }
        }
    }

    private fun refresh() {
        _state.value = build(LocalDateTime.now())
    }

    private fun build(now: LocalDateTime): UiState {
        val clock = DayClock(settings.dayEnd)
        val date = clock.planDate(now)
        var p = progress
        if (p == null || p.date != date) {
            p = DayProgress(date)
            progress = p
            store.saveProgress(p)
        }
        val done = history.firstOrNull { it.date == date }
        val kind = p.kindOverride ?: if (date.dayOfWeek in settings.offWeekdays) DayKind.OFF else DayKind.MEMO
        val reps = settings.repetitions ?: PlanRules.defaultRepetitions(settings.amount)
        val engine = PlanEngine(settings.startPage)
        // When today is already finished, show the plan as it was for today.
        val past = history.filter { it.date.isBefore(date) }
        val plan = engine.today(past, settings.amount, reps, done?.kind ?: kind)
        val stats = StatsCalculator.compute(
            history, settings.startDate ?: date, settings.startPage, date, settings.amount, settings.offWeekdays,
        )
        return UiState(now, settings, history, date, p, plan, stepsFor(plan), done, stats)
    }

    private fun stepsFor(plan: TodayPlan): List<Step> {
        val list = mutableListOf<Step>()
        plan.yesterday?.let {
            list += Step("yesterday", "تكرار الأمس", "${it.label} · ${it.surahs}", "${PlanRules.YESTERDAY_REPS} مرات غيبًا")
        }
        plan.newPages?.let {
            val d = "${it.label} · ${it.surahs}"
            list += Step("listen", "استمع إلى القارئ", d, "${PlanRules.LISTEN_REPS} مرات مع النظر في المصحف")
            list += Step("tafsir", "اقرأ التفسير", d, "مرة واحدة")
            list += Step("repeat", "كرّر حتى تحفظ", d, "${plan.repetitions} مرة", StepAction.COUNTER, plan.repetitions)
            list += Step("record", "سجّل الوجه غيبًا", d, "${PlanRules.RECORD_REPS} مرات متتالية بلا خطأ", StepAction.RECORD)
        }
        plan.linking?.let {
            list += Step("link", "الربط", "${it.label} · ${it.surahs}", "مرة واحدة غيبًا (${it.count} وجه)")
        }
        plan.review?.let {
            list += Step("review", "المراجعة", "${it.label} · ${it.surahs}", "مرة واحدة غيبًا (${it.count} وجه)")
        }
        return list
    }

    private fun update(block: (DayProgress) -> DayProgress) {
        val p = block(progress ?: DayProgress(_state.value.planDate))
        progress = p
        store.saveProgress(p)
        refresh()
    }

    fun toggle(stepId: String) = update { p ->
        p.copy(checked = if (stepId in p.checked) p.checked - stepId else p.checked + stepId)
    }

    fun setCounter(value: Int) {
        val target = _state.value.plan.repetitions
        update { p ->
            val v = value.coerceAtLeast(0)
            val checked = if (target > 0 && v >= target) p.checked + "repeat" else p.checked
            p.copy(counter = v, checked = checked)
        }
    }

    /** Switches today between an off day (sickness, travel, weekly off) and a memorizing day. */
    fun setTodayKind(kind: DayKind?) = update { it.copy(kindOverride = kind, checked = emptySet()) }

    fun finishDay() {
        val s = _state.value
        if (s.doneToday != null) return
        val entry = DayEntry(s.planDate, s.plan.kind, s.plan.newPages)
        history = (history + entry).sortedBy { it.date }
        store.saveHistory(history)
        refresh()
    }

    fun undoFinish() {
        val date = _state.value.planDate
        history = history.filterNot { it.date == date }
        store.saveHistory(history)
        refresh()
    }

    fun saveSettings(new: Settings) {
        settings = new
        store.saveSettings(new)
        refresh()
    }

    fun startPlan(amount: Int, startPage: Int, dayEndMinutes: Int) {
        val clock = DayClock(java.time.LocalTime.of(dayEndMinutes / 60, dayEndMinutes % 60))
        saveSettings(
            settings.copy(
                amount = amount,
                startPage = startPage,
                dayEndMinutes = dayEndMinutes,
                startDate = clock.planDate(LocalDateTime.now()),
            ),
        )
    }

    fun resetAll() {
        store.clearAll()
        settings = Settings()
        history = emptyList()
        progress = null
        refresh()
    }
}
