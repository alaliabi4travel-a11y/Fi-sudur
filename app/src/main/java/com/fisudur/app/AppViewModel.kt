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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    init {
        checkUpdate(quiet = true)
        viewModelScope.launch {
            while (true) {
                delay(1000)
                refresh()
            }
        }
    }

    private fun build(): UiState {
        val s = buildUiState(LocalDateTime.now(), settings, history, progress).copy(update = update)
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
        refresh()
    }

    override fun toggle(stepId: String) = update { p ->
        p.copy(checked = if (stepId in p.checked) p.checked - stepId else p.checked + stepId)
    }

    override fun setCounter(value: Int) {
        val target = _state.value.plan.repetitions
        update { p ->
            val v = value.coerceAtLeast(0)
            val checked = if (target > 0 && v >= target) p.checked + "repeat" else p.checked
            p.copy(counter = v, checked = checked)
        }
    }

    override fun setTodayKind(kind: DayKind?) = update { it.copy(kindOverride = kind, checked = emptySet()) }

    override fun finishDay() {
        val s = _state.value
        if (s.doneToday != null) return
        history = (history + DayEntry(s.planDate, s.plan.kind, s.plan.newPages)).sortedBy { it.date }
        store.saveHistory(history)
        refresh()
    }

    override fun undoFinish() {
        val date = _state.value.planDate
        history = history.filterNot { it.date == date }
        store.saveHistory(history)
        refresh()
    }

    override fun saveSettings(new: Settings) {
        settings = new
        store.saveSettings(new)
        refresh()
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
        store.clearAll()
        settings = Settings()
        history = emptyList()
        progress = null
        refresh()
    }
}
