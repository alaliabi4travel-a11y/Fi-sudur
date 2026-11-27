package com.fisudur.app

import android.app.Application
import android.net.Uri
import android.widget.Toast
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
import com.fisudur.app.ui.ar
import com.fisudur.app.ui.daysText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The reason recorded for a day closed without its new pages. */
const val POSTPONED = "تأجيل"

class AppViewModel(app: Application) : AndroidViewModel(app), Actions {
    private val store = Store(app)
    private var settings = store.loadSettings()
    private var history: List<DayEntry> = store.loadHistory()
    private var progress: DayProgress? = store.loadProgress()
    private var nextProgress: DayProgress? = store.loadNextProgress()
    private var weak: Map<Int, Int> = store.loadWeak()

    private var update: UpdateState = UpdateState.Idle

    /** Read from disk only when backups change, not on every tick. */
    private var lastBackup: String? = null
    private fun readLastBackup() { lastBackup = store.autoBackups().firstOrNull()?.name?.take(10) }

    private val _state = MutableStateFlow(build())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var ticker: Job? = null

    /** When updates were last looked for; the app checks again on return after a few hours. */
    private var lastUpdateCheck = 0L

    init {
        checkUpdate(quiet = true)
        runCatching { store.autoBackup() }
        copyToFolder()
        readLastBackup()
        refresh()
        Reminders.schedule(app)
        WirdWidget.refresh(app)
    }

    /** The countdown only needs to run while the app is on screen. */
    override fun setForeground(on: Boolean) {
        ticker?.cancel()
        ticker = null
        if (on) {
            // The day-end alarm may have recorded a day while the app was in the background.
            history = store.loadHistory()
            refresh()
            if (System.currentTimeMillis() - lastUpdateCheck > 3 * 60 * 60 * 1000L) checkUpdate(quiet = true)
            ticker = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    tick()
                }
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
    }

    /** After anything that changes what today looks like: home-screen widget and reminders follow. */
    private fun changed() {
        refresh()
        val app = getApplication<Application>()
        WirdWidget.refresh(app)
        Reminders.schedule(app)
    }

    private fun build(): UiState {
        val now = LocalDateTime.now()
        // A day that ended fully done (or as a chosen off day) is recorded even if "finish" was not pressed.
        autoFinishEntry(settings, history, progress, DayClock(settings.dayEnd).planDate(now), weak)?.let { e ->
            history = (history + e).sortedBy { it.date }
            store.saveHistory(history)
            store.settleWeak(progress)
            weak = store.loadWeak()
        }
        val today = DayClock(settings.dayEnd).planDate(now)
        // Tomorrow has come: what was started early becomes today's progress.
        if (progress?.date != today && nextProgress != null) {
            if (nextProgress?.date == today) {
                progress = nextProgress
                store.saveProgress(progress!!)
            }
            nextProgress = null
            store.saveNextProgress(null)
        }
        val base = buildUiState(now, settings, history, progress, weak)
        val tomorrow = if (base.doneToday != null) {
            val start = DayClock(settings.dayEnd).dayEnd(now).plusMinutes(1)
            buildUiState(start, settings, history, nextProgress, weak)
        } else null
        val s = base.copy(update = update, lastAutoBackup = lastBackup, tomorrow = tomorrow, lastFolderCopy = store.lastFolderCopy())
        if (progress != s.progress) {
            progress = s.progress
            store.saveProgress(s.progress)
        }
        return s
    }

    private fun refresh() {
        _state.value = build()
    }

    /**
     * Each second only the clock moves: the countdown is redrawn without rebuilding the plan.
     * When the plan day changes, everything is rebuilt (and a finished day recorded).
     */
    private fun tick() {
        val now = LocalDateTime.now()
        val s = _state.value
        if (DayClock(settings.dayEnd).planDate(now) == s.planDate) _state.value = s.copy(now = now) else refresh()
    }

    private fun update(block: (DayProgress) -> DayProgress) {
        val p = block(_state.value.progress)
        progress = p
        store.saveProgress(p)
        changed()
    }

    override fun toggle(stepId: String) {
        val step = _state.value.steps.firstOrNull { it.id == stepId } ?: return
        // The repetition step follows the counter only; it cannot be ticked by hand.
        if (step.action == StepAction.COUNTER) return
        update { p -> toggled(p, step) }
    }

    /**
     * Ticks or unticks a step. A step read several times keeps its count in step with
     * its tick: unticking starts the count again, ticking fills it.
     */
    private fun toggled(p: DayProgress, step: Step): DayProgress {
        val on = step.id !in p.checked
        val checked = if (on) p.checked + step.id else p.checked - step.id
        val tallies = when {
            step.action != StepAction.TALLY -> p.tallies
            on -> p.tallies + (step.id to step.target)
            else -> p.tallies - step.id
        }
        return p.copy(checked = checked, tallies = tallies)
    }

    /** One more reading; it stops at the target, so an extra tap never loses the count. */
    private fun tallied(p: DayProgress, step: Step): DayProgress {
        val n = (p.tallies[step.id] ?: 0) + 1
        if (n > step.target) return p
        return p.copy(tallies = p.tallies + (step.id to n), checked = if (n == step.target) p.checked + step.id else p.checked)
    }

    override fun setCounter(value: Int) {
        // No new pages (an off day, or after the whole Quran): a free counter with no target.
        val target = if (_state.value.plan.newPages != null) _state.value.plan.repetitions else 0
        update { p ->
            val v = value.coerceAtLeast(0)
            val checked = if (target > 0 && v >= target) p.checked + "repeat" else p.checked - "repeat"
            p.copy(counter = v, checked = checked)
        }
    }

    override fun tally(stepId: String) {
        val step = _state.value.steps.firstOrNull { it.id == stepId } ?: return
        if ((_state.value.progress.tallies[stepId] ?: 0) >= step.target) return
        update { p -> tallied(p, step) }
    }

    private fun updateNext(block: (DayProgress) -> DayProgress) {
        val t = _state.value.tomorrow ?: return
        val p = block(t.progress)
        nextProgress = p
        store.saveNextProgress(p)
        refresh()
    }

    override fun toggleNext(stepId: String) {
        val step = _state.value.tomorrow?.steps?.firstOrNull { it.id == stepId } ?: return
        if (step.action == StepAction.COUNTER) return
        updateNext { p -> toggled(p, step) }
    }

    override fun tallyNext(stepId: String) {
        val step = _state.value.tomorrow?.steps?.firstOrNull { it.id == stepId } ?: return
        updateNext { p -> tallied(p, step) }
    }

    override fun setTodayKind(kind: DayKind?, reason: String?) =
        update { it.copy(kindOverride = kind, reason = if (kind == DayKind.OFF) reason else null, checked = emptySet(), tallies = emptyMap()) }

    override fun finishDay() {
        val s = _state.value
        if (s.doneToday != null) return
        history = (history + DayEntry(s.planDate, s.plan.kind, s.plan.newPages, s.progress.reason.takeIf { s.plan.kind == DayKind.OFF })).sortedBy { it.date }
        store.saveHistory(history)
        store.settleWeak(s.progress)
        weak = store.loadWeak()
        changed()
    }

    override fun postponeNew() {
        // The day is closed without the new pages: they stay for the next memorizing day.
        val s = _state.value
        if (s.doneToday != null) return
        history = (history + DayEntry(s.planDate, DayKind.OFF, null, POSTPONED)).sortedBy { it.date }
        store.saveHistory(history)
        store.settleWeak(s.progress)
        weak = store.loadWeak()
        changed()
    }

    override fun undoFinish() {
        val date = _state.value.planDate
        history = history.filterNot { it.date == date }
        store.saveHistory(history)
        store.unsettleWeak(date)
        weak = store.loadWeak()
        changed()
    }

    override fun toggleWeak(page: Int) {
        weak = if (page in weak) weak - page else weak + (page to PlanRules.WEAK_DAYS)
        store.saveWeak(weak)
        changed()
    }

    override fun canEditDay(s: UiState, date: LocalDate): Boolean {
        val e = s.history.firstOrNull { it.date == date } ?: return false
        if (!date.isBefore(s.planDate)) return false
        // A memorizing day can change only when no later day memorized, so no later pages move.
        return e.kind == DayKind.OFF || s.history.none { it.kind == DayKind.MEMO && it.date.isAfter(date) }
    }

    override fun editDay(date: LocalDate, kind: DayKind?, reason: String?) {
        val s = _state.value
        if (!canEditDay(s, date)) return
        history = history.filterNot { it.date == date }
        if (kind == DayKind.OFF) history = (history + DayEntry(date, DayKind.OFF, null, reason)).sortedBy { it.date }
        store.saveHistory(history)
        changed()
        toast(if (kind == null) "حُذف تسجيل اليوم" else if (reason != null) "صار يوم عذر: $reason" else "صار يوم إجازة")
    }

    override fun backfill(date: LocalDate, kind: DayKind, reason: String?) {
        val s = _state.value
        if (!date.isBefore(s.planDate) || history.any { it.date == date }) return
        val entry = if (kind == DayKind.MEMO && canBackfillMemo(s, date)) {
            val reps = settings.repetitions ?: PlanRules.defaultRepetitions(settings.amount)
            val pages = PlanEngine(settings.startPage, firstKnown = settings.firstKnown).today(history, settings.amount, reps, DayKind.MEMO, date).newPages
            DayEntry(date, DayKind.MEMO, pages)
        } else {
            DayEntry(date, DayKind.OFF, null, reason)
        }
        history = (history + entry).sortedBy { it.date }
        store.saveHistory(history)
        changed()
        toast(if (entry.kind == DayKind.MEMO) "سُجّل يوم حفظ" else if (reason != null) "سُجّل يوم عذر: $reason" else "سُجّل يوم عذر")
    }

    override fun exportTo(uri: Uri) {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.exportJson().toByteArray()) }
        }.onSuccess { toast("حُفظت النسخة الاحتياطية") }
            .onFailure { toast("تعذّر حفظ الملف") }
    }

    override fun preview(uri: Uri): String? = runCatching {
        val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        describe(store.summarize(text))
    }.getOrNull()

    override fun backups(): List<Pair<String, String>> = store.autoBackups().mapNotNull { f ->
        runCatching { f.name to describe(store.summarize(f.readText())) }.getOrNull()
    }

    private fun describe(s: com.fisudur.app.data.Store.Summary): String {
        val at = s.exportedAt.take(16).replace('T', ' ')
        val last = s.lastPage?.let { "، آخر وجه ${it.ar()}" } ?: ""
        return "${at.ar()}: ${daysText(s.days.toLong())} مسجّلة$last"
    }

    override fun importFrom(uri: Uri) {
        val app = getApplication<Application>()
        runCatching {
            val text = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            runCatching { store.autoBackup(force = true) }
            store.importJson(text)
        }.onSuccess {
            reload()
            toast("استُعيدت بياناتك")
        }.onFailure { toast("هذا الملف ليس نسخة احتياطية من «في صدور»") }
    }

    override fun restoreAutoBackup(name: String?) {
        val file = store.autoBackups().firstOrNull { name == null || it.name == name } ?: return toast("لا توجد نسخة تلقائية بعد")
        val text = file.readText()
        // A copy of now first, so a restore by mistake can itself be undone.
        runCatching { store.autoBackup(force = true) }
        runCatching { store.importJson(text) }
            .onSuccess {
                reload()
                toast("استُعيدت نسخة ${file.name.take(10)}")
            }
            .onFailure { toast("تعذّرت الاستعادة") }
    }


    private fun reload() {
        readLastBackup()
        settings = store.loadSettings()
        history = store.loadHistory()
        progress = store.loadProgress()
        nextProgress = store.loadNextProgress()
        weak = store.loadWeak()
        changed()
    }

    override fun chooseBackupFolder(uri: Uri?) {
        val app = getApplication<Application>()
        if (uri != null) {
            runCatching {
                app.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        saveSettings(settings.copy(backupFolder = uri?.toString()))
        if (uri != null) copyToFolder(force = true, tell = true)
    }

    /** Writes the backup into the folder the person chose, once a day (or now, when asked). */
    private fun copyToFolder(force: Boolean = false, tell: Boolean = false) {
        val folder = settings.backupFolder ?: return
        val today = LocalDate.now().toString()
        if (!force && store.lastFolderCopy() == today) return
        val ok = Backups.writeToFolder(getApplication(), folder, store.exportJson())
        if (ok) store.setLastFolderCopy(today)
        if (tell) toast(if (ok) "حُفظت نسخة في المجلد" else "تعذّرت الكتابة في المجلد، اختره من جديد")
        refresh()
    }

    override fun saveSettings(new: Settings) {
        if (new.dayEndMinutes != settings.dayEndMinutes) {
            // The plan date of this moment may move with the new day end; today's progress moves too.
            val now = LocalDateTime.now()
            val moved = carryProgress(settings.dayEnd, new.dayEnd, now, progress, history)
            if (moved != progress && moved != null) {
                val shift = java.time.temporal.ChronoUnit.DAYS.between(progress!!.date, moved.date)
                progress = moved
                store.saveProgress(moved)
                nextProgress = nextProgress?.let { it.copy(date = it.date.plusDays(shift)) }
                store.saveNextProgress(nextProgress)
            }
        }
        settings = new
        store.saveSettings(new)
        changed()
    }

    override fun startPlan(amount: Int, startPage: Int, dayEndMinutes: Int, memorizedBefore: Boolean) {
        val clock = DayClock(LocalTime.of(dayEndMinutes / 60, dayEndMinutes % 60))
        saveSettings(
            settings.copy(
                amount = amount,
                startPage = startPage,
                memorizedBefore = memorizedBefore && startPage > 1,
                dayEndMinutes = dayEndMinutes,
                startDate = clock.planDate(LocalDateTime.now()),
            ),
        )
    }

    private fun setUpdate(u: UpdateState) {
        update = u
        refresh()
    }

    override fun checkUpdate() = checkUpdate(quiet = false)

    private fun checkUpdate(quiet: Boolean) {
        if (update is UpdateState.Downloading || update is UpdateState.Ready) return
        lastUpdateCheck = System.currentTimeMillis()
        if (!quiet) setUpdate(UpdateState.Checking)
        viewModelScope.launch {
            val result = runCatching { Updater.latest() }
            val info = result.getOrNull()
            setUpdate(
                when {
                    info != null && info.versionCode > BuildConfig.VERSION_CODE && info.apkUrl.isNotEmpty() ->
                        UpdateState.Available(info)
                    quiet -> UpdateState.Idle
                    result.isFailure -> UpdateState.Failed("تعذّر التحقق. تأكد من الاتصال بالإنترنت.")
                    else -> UpdateState.UpToDate
                },
            )
        }
    }

    /** Downloads the new version inside the app, then opens Android's installer. */
    override fun getUpdate() {
        val app = getApplication<Application>()
        val current = update
        if (current is UpdateState.Ready) {
            if (Updater.canInstall(app)) Updater.install(app, current.file) else Updater.requestInstallPermission(app)
            return
        }
        val info = (current as? UpdateState.Available)?.info ?: return
        setUpdate(UpdateState.Downloading(info, 0f))
        viewModelScope.launch {
            runCatching {
                Updater.download(app, info) { p -> update = UpdateState.Downloading(info, p) }
            }.onSuccess { file ->
                setUpdate(UpdateState.Ready(info, file))
                if (Updater.canInstall(app)) Updater.install(app, file) else Updater.requestInstallPermission(app)
            }.onFailure {
                setUpdate(UpdateState.Failed("انقطع التنزيل. حاول مرة أخرى."))
            }
        }
    }

    override fun resetAll() {
        // A copy first, so "start a new plan" by mistake can be undone from Settings.
        runCatching { store.autoBackup(force = true) }
        store.clearAll()
        readLastBackup()
        settings = Settings()
        history = emptyList()
        weak = emptyMap()
        progress = null
        nextProgress = null
        changed()
    }
}
