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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AppViewModel(app: Application) : AndroidViewModel(app), Actions {
    private val store = Store(app)
    private var settings = store.loadSettings()
    private var history: List<DayEntry> = store.loadHistory()
    private var progress: DayProgress? = store.loadProgress()

    private var update: UpdateState = UpdateState.Idle

    private val _state = MutableStateFlow(build())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var ticker: Job? = null

    init {
        checkUpdate(quiet = true)
        runCatching { store.autoBackup() }
        Reminders.schedule(app)
        WirdWidget.refresh(app)
    }

    /** The countdown only needs to run while the app is on screen. */
    override fun setForeground(on: Boolean) {
        ticker?.cancel()
        ticker = null
        if (on) {
            refresh()
            ticker = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    refresh()
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
        val s = buildUiState(LocalDateTime.now(), settings, history, progress)
            .copy(update = update, lastAutoBackup = lastBackup)
        if (progress != s.progress) {
            progress = s.progress
            store.saveProgress(s.progress)
        }
        return s
    }

    private fun refresh() {
        _state.value = build()
    }

    private fun update(block: (DayProgress) -> DayProgress) {
        val p = block(_state.value.progress)
        progress = p
        store.saveProgress(p)
        changed()
    }

    override fun toggle(stepId: String) {
        // The repetition step follows the counter only; it cannot be ticked by hand.
        if (_state.value.steps.firstOrNull { it.id == stepId }?.action == StepAction.COUNTER) return
        toggleStep(stepId)
    }

    private fun toggleStep(stepId: String) = update { p ->
        p.copy(checked = if (stepId in p.checked) p.checked - stepId else p.checked + stepId)
    }

    override fun setCounter(value: Int) {
        val target = _state.value.plan.repetitions
        update { p ->
            val v = value.coerceAtLeast(0)
            val checked = if (target > 0 && v >= target) p.checked + "repeat" else p.checked - "repeat"
            p.copy(counter = v, checked = checked)
        }
    }

    override fun tally(stepId: String) {
        val target = _state.value.steps.firstOrNull { it.id == stepId }?.target ?: return
        update { p ->
            val n = (p.tallies[stepId] ?: 0) + 1
            if (n > target) {
                // One tap past the target starts the count again and unchecks the step.
                p.copy(tallies = p.tallies - stepId, checked = p.checked - stepId)
            } else {
                p.copy(tallies = p.tallies + (stepId to n), checked = if (n == target) p.checked + stepId else p.checked)
            }
        }
    }

    override fun setTodayKind(kind: DayKind?, reason: String?) =
        update { it.copy(kindOverride = kind, reason = if (kind == DayKind.OFF) reason else null, checked = emptySet(), tallies = emptyMap()) }

    override fun finishDay() {
        val s = _state.value
        if (s.doneToday != null) return
        history = (history + DayEntry(s.planDate, s.plan.kind, s.plan.newPages, s.progress.reason.takeIf { s.plan.kind == DayKind.OFF })).sortedBy { it.date }
        store.saveHistory(history)
        changed()
    }

    override fun undoFinish() {
        val date = _state.value.planDate
        history = history.filterNot { it.date == date }
        store.saveHistory(history)
        changed()
    }

    override fun backfill(date: LocalDate, kind: DayKind, reason: String?) {
        val s = _state.value
        if (!date.isBefore(s.planDate) || history.any { it.date == date }) return
        val entry = if (kind == DayKind.MEMO && canBackfillMemo(s, date)) {
            val reps = settings.repetitions ?: PlanRules.defaultRepetitions(settings.amount)
            val pages = PlanEngine(settings.startPage).today(history, settings.amount, reps, DayKind.MEMO, date).newPages
            DayEntry(date, DayKind.MEMO, pages)
        } else {
            DayEntry(date, DayKind.OFF, null, reason)
        }
        history = (history + entry).sortedBy { it.date }
        store.saveHistory(history)
        changed()
        toast(if (entry.kind == DayKind.MEMO) "سُجّل يوم حفظ" else "سُجّل يوم عذر: ${reason ?: ""}")
    }

    override fun exportTo(uri: Uri) {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(store.exportJson().toByteArray()) }
        }.onSuccess { toast("حُفظت النسخة الاحتياطية") }
            .onFailure { toast("تعذّر حفظ الملف") }
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

    override fun restoreAutoBackup() {
        val file = store.autoBackups().firstOrNull() ?: return toast("لا توجد نسخة تلقائية بعد")
        runCatching { store.importJson(file.readText()) }
            .onSuccess {
                reload()
                toast("استُعيدت نسخة ${file.name.take(10)}")
            }
            .onFailure { toast("تعذّرت الاستعادة") }
    }

    private val lastBackup: String? get() = store.autoBackups().firstOrNull()?.name?.take(10)

    private fun reload() {
        settings = store.loadSettings()
        history = store.loadHistory()
        progress = store.loadProgress()
        changed()
    }

    override fun saveSettings(new: Settings) {
        settings = new
        store.saveSettings(new)
        changed()
    }

    override fun startPlan(amount: Int, startPage: Int, dayEndMinutes: Int) {
        val clock = DayClock(LocalTime.of(dayEndMinutes / 60, dayEndMinutes % 60))
        saveSettings(
            settings.copy(
                amount = amount,
                startPage = startPage,
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
        if (update is UpdateState.Downloading) return
        if (!quiet) setUpdate(UpdateState.Checking)
        viewModelScope.launch {
            val result = runCatching { Updater.latest() }
            val info = result.getOrNull()
            setUpdate(
                when {
                    info != null && info.versionCode > BuildConfig.VERSION_CODE && info.apkUrl.isNotEmpty() ->
                        UpdateState.Available(info)
                    quiet -> UpdateState.Idle
                    result.isFailure -> UpdateState.Failed("تعذر التحقق. تأكد من الاتصال بالإنترنت.")
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
        settings = Settings()
        history = emptyList()
        progress = null
        changed()
    }
}
