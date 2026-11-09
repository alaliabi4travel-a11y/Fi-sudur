package com.fisudur.app

import com.fisudur.app.data.DayProgress
import com.fisudur.app.data.Settings
import com.fisudur.app.engine.DayClock
import com.fisudur.app.engine.DayEntry
import com.fisudur.app.engine.DayKind
import com.fisudur.app.engine.PlanEngine
import com.fisudur.app.engine.PlanRules
import com.fisudur.app.engine.Stats
import com.fisudur.app.engine.StatsCalculator
import com.fisudur.app.engine.TodayPlan
import java.time.LocalDate
import java.time.LocalDateTime

enum class StepAction { CHECK, COUNTER, RECORD }

enum class StepKind { YESTERDAY, LISTEN, TAFSIR, REPEAT, RECORD, LINK, REVIEW }

data class Step(
    val id: String,
    val kind: StepKind,
    val title: String,
    val pages: String?,
    val surahs: String?,
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
    val update: UpdateState = UpdateState.Idle,
) {
    val needsSetup: Boolean get() = settings.startDate == null
    val clock: DayClock get() = DayClock(settings.dayEnd)
    val allChecked: Boolean get() = steps.all { it.id in progress.checked }
    val checkedCount: Int get() = steps.count { it.id in progress.checked }
}

/** What the screens can ask for. The view model implements it; previews use a no-op. */
interface Actions {
    fun toggle(stepId: String) {}
    fun setCounter(value: Int) {}
    fun setTodayKind(kind: DayKind?) {}
    fun finishDay() {}
    fun undoFinish() {}
    fun saveSettings(new: Settings) {}
    fun startPlan(amount: Int, startPage: Int, dayEndMinutes: Int) {}
    fun resetAll() {}
    fun checkUpdate() {}
    fun getUpdate() {}
}

object NoActions : Actions

/** Pure function from stored data to everything the screens show. */
fun buildUiState(now: LocalDateTime, settings: Settings, history: List<DayEntry>, stored: DayProgress?): UiState {
    val clock = DayClock(settings.dayEnd)
    val date = clock.planDate(now)
    val p = if (stored == null || stored.date != date) DayProgress(date) else stored
    val done = history.firstOrNull { it.date == date }
    val kind = p.kindOverride ?: if (date.dayOfWeek in settings.offWeekdays) DayKind.OFF else DayKind.MEMO
    val reps = settings.repetitions ?: PlanRules.defaultRepetitions(settings.amount)
    val engine = PlanEngine(settings.startPage)
    // Once today is finished, keep showing the plan as it was for today.
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
        list += Step("yesterday", StepKind.YESTERDAY, "تكرار الأمس", it.label, it.surahs, "${PlanRules.YESTERDAY_REPS} مرات غيبًا")
    }
    plan.newPages?.let {
        list += Step("listen", StepKind.LISTEN, "استمع إلى القارئ", it.label, it.surahs, "${PlanRules.LISTEN_REPS} مرات مع النظر في المصحف")
        list += Step("tafsir", StepKind.TAFSIR, "اقرأ التفسير", it.label, it.surahs, "مرة واحدة · الميسّر أو المختصر")
        list += Step("repeat", StepKind.REPEAT, "كرّر حتى تحفظ", it.label, it.surahs, "${plan.repetitions} مرة", StepAction.COUNTER, plan.repetitions)
        list += Step("record", StepKind.RECORD, "سجّل غيبًا", it.label, it.surahs, "${PlanRules.RECORD_REPS} مرات متتالية بلا خطأ", StepAction.RECORD)
    }
    plan.linking?.let {
        list += Step("link", StepKind.LINK, "الربط", it.label, it.surahs, "مرة واحدة غيبًا · ${it.count} وجهًا")
    }
    plan.review?.let {
        list += Step("review", StepKind.REVIEW, "المراجعة", it.label, it.surahs, "مرة واحدة غيبًا · ${it.count} وجهًا")
    }
    return list
}
