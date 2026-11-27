package com.fisudur.app

import com.fisudur.app.data.DayProgress
import com.fisudur.app.data.Settings
import com.fisudur.app.engine.DayClock
import com.fisudur.app.engine.DayEntry
import com.fisudur.app.engine.DayKind
import com.fisudur.app.engine.PageRange
import com.fisudur.app.engine.PlanEngine
import com.fisudur.app.engine.QuranData
import com.fisudur.app.engine.PlanRules
import com.fisudur.app.engine.Stats
import com.fisudur.app.engine.StatsCalculator
import com.fisudur.app.engine.TodayPlan
import com.fisudur.app.ui.ar
import java.time.LocalDate
import java.time.LocalDateTime

enum class StepAction { CHECK, COUNTER, TALLY }

enum class StepKind { YESTERDAY, LISTEN, TAFSIR, MEMORIZE, REPEAT, RECORD, LINK, REVIEW, WEAK }

data class Step(
    val id: String,
    val kind: StepKind,
    val title: String,
    val pages: String?,
    val surahs: String?,
    val times: String,
    val action: StepAction = StepAction.CHECK,
    val target: Int = 0,
    /** The pages this step reads, for marking one as weak (linking, yesterday, review). */
    val pageList: List<Int> = emptyList(),
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
    /** When the newest automatic copy inside the phone was taken, if any. */
    val lastAutoBackup: String? = null,
    /** Tomorrow's wird, shown once today is finished so it can be started early. */
    val tomorrow: UiState? = null,
    /** Weak pages and the days of extra reading each still has. */
    val weak: Map<Int, Int> = emptyMap(),
    /** The day a copy last went to the chosen backup folder. */
    val lastFolderCopy: String? = null,
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
    fun setTodayKind(kind: DayKind?, reason: String? = null) {}
    fun finishDay() {}
    fun undoFinish() {}
    fun saveSettings(new: Settings) {}
    fun startPlan(amount: Int, startPage: Int, dayEndMinutes: Int, memorizedBefore: Boolean = false) {}
    fun resetAll() {}
    fun checkUpdate() {}
    fun getUpdate() {}
    /** One more reading of a step read several times; checks it at its target. */
    fun tally(stepId: String) {}
    fun exportTo(uri: android.net.Uri) {}
    fun importFrom(uri: android.net.Uri) {}
    /** Restores the automatic copy named [name], or the newest when null. */
    fun restoreAutoBackup(name: String? = null) {}
    /** Automatic copies inside the phone, newest first: file name and a one-line description. */
    fun backups(): List<Pair<String, String>> = emptyList()
    /** A one-line description of a backup file, or null when it is not one. */
    fun preview(uri: android.net.Uri): String? = null
    /** A folder for a daily copy outside the app (null stops it). */
    fun chooseBackupFolder(uri: android.net.Uri?) {}
    /** Marks a page weak, or settles it. */
    fun toggleWeak(page: Int) {}
    /** Closes today without the new pages; they move to the next memorizing day. */
    fun postponeNew() {}
    /** Whether a recorded past day can be changed (see the view model for the rule). */
    fun canEditDay(s: UiState, date: LocalDate): Boolean = false
    /** Changes a recorded past day: to an off day (with a reason or none), or null to remove the record. */
    fun editDay(date: LocalDate, kind: DayKind?, reason: String? = null) {}
    /**
     * The result of one recitation from memory, checked against the mushaf: one more in a row
     * when it had no mistake; a mistake starts the count of the record step again.
     */
    fun recordResult(ok: Boolean) {}
    /** Records a past day that was never finished. */
    fun backfill(date: LocalDate, kind: DayKind, reason: String? = null) {}
    /** Whether a past day can still be recorded as a memorizing day (only the newest gap can). */
    fun canBackfillMemo(s: UiState, date: LocalDate): Boolean =
        date.isBefore(s.planDate) && s.history.none { !it.date.isBefore(date) }
    fun setForeground(on: Boolean) {}
    /** Checks or unchecks a step of tomorrow's wird, started early. */
    fun toggleNext(stepId: String) {}
    /** One more reading of a step of tomorrow's wird. */
    fun tallyNext(stepId: String) {}
}

object NoActions : Actions

/** Pure function from stored data to everything the screens show. */
fun buildUiState(
    now: LocalDateTime,
    settings: Settings,
    history: List<DayEntry>,
    stored: DayProgress?,
    weak: Map<Int, Int> = emptyMap(),
): UiState {
    val clock = DayClock(settings.dayEnd)
    val date = clock.planDate(now)
    val p = if (stored == null || stored.date != date) DayProgress(date) else stored
    val done = history.firstOrNull { it.date == date }
    val kind = p.kindOverride ?: if (date.dayOfWeek in settings.offWeekdays) DayKind.OFF else DayKind.MEMO
    val reps = settings.repetitions ?: PlanRules.defaultRepetitions(settings.amount)
    val engine = PlanEngine(settings.startPage, firstKnown = settings.firstKnown)
    // Once today is finished, keep showing the plan as it was for today.
    val past = history.filter { it.date.isBefore(date) }
    val plan = engine.today(past, settings.amount, reps, done?.kind ?: kind, date)
    val stats = StatsCalculator.compute(
        history, settings.startDate ?: date, settings.startPage, date, settings.amount, settings.offWeekdays,
        firstKnown = settings.firstKnown,
    )
    return UiState(now, settings, history, date, p, plan, stepsFor(plan, weak), done, stats, weak = weak)
}

/**
 * When a plan day has ended without "finish day" being pressed, this is the entry it
 * should get anyway: a full day if every step was checked, or the off day the user
 * chose with a reason. Null when the day should stay unrecorded.
 */
fun autoFinishEntry(
    settings: Settings,
    history: List<DayEntry>,
    stored: DayProgress?,
    today: LocalDate,
    weak: Map<Int, Int> = emptyMap(),
): DayEntry? {
    if (stored == null || settings.startDate == null || !stored.date.isBefore(today)) return null
    if (stored.date.isBefore(settings.startDate) || history.any { it.date == stored.date }) return null
    // A moment just after that day began, so the plan is rebuilt exactly as it was.
    val then = DayClock(settings.dayEnd).startOf(stored.date).plusMinutes(1)
    val old = buildUiState(then, settings, history, stored, weak)
    if (old.planDate != stored.date) return null
    val chosenOff = stored.kindOverride == DayKind.OFF && stored.reason != null
    val complete = old.steps.isNotEmpty() && old.allChecked
    if (!chosenOff && !complete) return null
    return DayEntry(old.planDate, old.plan.kind, old.plan.newPages, stored.reason.takeIf { old.plan.kind == DayKind.OFF })
}

/**
 * When the day end changes, the plan date of this moment can move (changing midnight to
 * 04:00 at 01:00 turns "today" into yesterday's date). Today's progress moves with it,
 * so nothing ticked or counted is lost. Unchanged when the date stays, or the new date
 * is already recorded.
 */
fun carryProgress(
    oldEnd: java.time.LocalTime,
    newEnd: java.time.LocalTime,
    now: LocalDateTime,
    progress: DayProgress?,
    history: List<DayEntry>,
): DayProgress? {
    if (progress == null) return null
    val was = DayClock(oldEnd).planDate(now)
    val becomes = DayClock(newEnd).planDate(now)
    if (was == becomes || progress.date != was || history.any { it.date == becomes }) return progress
    return progress.copy(date = becomes)
}

private fun PageRange.pages(): List<Int> = (from..to).toList()

private fun stepsFor(plan: TodayPlan, weak: Map<Int, Int>): List<Step> {
    // Order as practised: linking, yesterday, then the new pages (tafsir, listening,
    // memorizing, recording to confirm, repetition to consolidate), and review last when there is one.
    val list = mutableListOf<Step>()
    plan.linking?.let {
        list += Step("link", StepKind.LINK, "الربط", it.label, it.surahs, "مرة واحدة غيبًا، ${com.fisudur.app.ui.pagesText(it.count)}", pageList = it.pages())
    }
    plan.yesterday?.let {
        list += Step("yesterday", StepKind.YESTERDAY, "تكرار الأمس", it.label, it.surahs, "${PlanRules.YESTERDAY_REPS} مرات غيبًا", StepAction.TALLY, PlanRules.YESTERDAY_REPS, it.pages())
    }
    if (weak.isNotEmpty()) {
        // Pages that slipped in linking or review get extra readings for a few days, before the new pages.
        val pages = weak.keys.sorted()
        val surahs = pages.map { QuranData.surahName(QuranData.pageFirstSurah[it - 1]) }.distinct()
        list += Step(
            "weak", StepKind.WEAK, "الأوجه الضعيفة",
            pages.joinToString("، "),
            surahs.take(3).joinToString("، ") + if (surahs.size > 3) "…" else "",
            "${PlanRules.WEAK_REPS} مرات غيبًا لكل وجه",
            StepAction.TALLY, PlanRules.WEAK_REPS, pages,
        )
    }
    plan.newPages?.let {
        list += Step("tafsir", StepKind.TAFSIR, "اقرأ التفسير", it.label, it.surahs, "مرة واحدة، الميسّر أو المختصر")
        list += Step("listen", StepKind.LISTEN, "استمع إلى القارئ", it.label, it.surahs, "${PlanRules.LISTEN_REPS} مرات مع النظر في المصحف", StepAction.TALLY, PlanRules.LISTEN_REPS)
        list += Step("memorize", StepKind.MEMORIZE, "احفظ الورد", it.label, it.surahs, "حتى تقرأه غيبًا")
        list += Step("record", StepKind.RECORD, "سجّل غيبًا لتأكيد الحفظ", it.label, it.surahs, "${PlanRules.RECORD_REPS} مرات متتالية بلا خطأ", StepAction.TALLY, PlanRules.RECORD_REPS)
        list += Step("repeat", StepKind.REPEAT, "كرّر لتثبيت الحفظ", it.label, it.surahs, "${plan.repetitions} مرة", StepAction.COUNTER, plan.repetitions)
    }
    plan.review?.let {
        // Reaching the newest old page, the review carries on from the first one.
        val w = plan.reviewWrap
        val pages = if (w != null) "${it.label} ثم ${w.label}" else it.label
        val surahs = if (w != null && w.surahs != it.surahs) "${it.surahs} ثم ${w.surahs}" else it.surahs
        val count = it.count + (w?.count ?: 0)
        // Which pass over the old pages this is; a day that reaches the end closes it.
        val round = if (w != null) plan.round else plan.round + 1
        val title = "المراجعة، الجولة ${round.ar()}"
        list += Step("review", StepKind.REVIEW, title, pages, surahs, "مرة واحدة غيبًا، ${com.fisudur.app.ui.pagesText(count)}", pageList = it.pages() + (w?.pages() ?: emptyList()))
    }
    return list
}
