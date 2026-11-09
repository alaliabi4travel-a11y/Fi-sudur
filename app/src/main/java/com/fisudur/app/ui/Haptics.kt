package com.fisudur.app.ui

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.fisudur.app.Actions
import com.fisudur.app.StepAction
import com.fisudur.app.UiState
import com.fisudur.app.data.Settings
import com.fisudur.app.engine.DayKind
import java.time.LocalDate

/**
 * Four feels, each with one meaning:
 * tick = a small step (a count, a tab, a setting), confirm = something recorded,
 * success = something completed, undo = something taken back.
 */
class Haptics(context: Context, private val enabled: () -> Boolean) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    private val main = Handler(Looper.getMainLooper())

    fun tick() = play(VibrationEffect.EFFECT_TICK, 12)
    fun confirm() = play(VibrationEffect.EFFECT_CLICK, 25)
    fun undo() = play(VibrationEffect.EFFECT_HEAVY_CLICK, 60)
    fun success() {
        play(VibrationEffect.EFFECT_CLICK, 25)
        main.postDelayed({ play(VibrationEffect.EFFECT_HEAVY_CLICK, 40) }, 140)
    }

    private fun play(effect: Int, fallbackMs: Long) {
        val v = vibrator ?: return
        if (!enabled() || !v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(effect))
            else v.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

@Composable
fun rememberHaptics(enabled: () -> Boolean): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context, enabled) }
}

/** Wraps the screens' actions so each one is felt the way the plan says, in one place. */
class HapticActions(private val a: Actions, private val s: UiState, private val h: Haptics) : Actions by a {
    override fun toggle(stepId: String) = stepToggle(s, stepId) { a.toggle(stepId) }
    override fun toggleNext(stepId: String) { s.tomorrow?.let { t -> stepToggle(t, stepId) { a.toggleNext(stepId) } } }
    override fun tally(stepId: String) = stepTally(s, stepId) { a.tally(stepId) }
    override fun tallyNext(stepId: String) { s.tomorrow?.let { t -> stepTally(t, stepId) { a.tallyNext(stepId) } } }

    override fun setCounter(value: Int) {
        val target = s.plan.repetitions
        when {
            value < s.progress.counter -> h.undo()
            target > 0 && value == target -> h.success()
            else -> h.tick()
        }
        a.setCounter(value)
    }

    override fun setTodayKind(kind: DayKind?, reason: String?) { if (kind == null) h.undo() else h.confirm(); a.setTodayKind(kind, reason) }
    override fun finishDay() { h.success(); a.finishDay() }
    override fun undoFinish() { h.undo(); a.undoFinish() }
    override fun saveSettings(new: Settings) {
        h.tick()
        a.saveSettings(new)
    }
    override fun startPlan(amount: Int, startPage: Int, dayEndMinutes: Int) { h.success(); a.startPlan(amount, startPage, dayEndMinutes) }
    override fun resetAll() { h.undo(); a.resetAll() }
    override fun backfill(date: LocalDate, kind: DayKind, reason: String?) { h.confirm(); a.backfill(date, kind, reason) }
    override fun restoreAutoBackup() { h.confirm(); a.restoreAutoBackup() }

    private fun willCompleteAll(st: UiState, stepId: String) =
        st.steps.all { it.id == stepId || it.id in st.progress.checked }

    private inline fun stepToggle(st: UiState, stepId: String, go: () -> Unit) {
        val step = st.steps.firstOrNull { it.id == stepId } ?: return go()
        if (step.action == StepAction.COUNTER) return go()
        when {
            stepId in st.progress.checked -> h.undo()
            willCompleteAll(st, stepId) -> h.success()
            else -> h.confirm()
        }
        go()
    }

    private inline fun stepTally(st: UiState, stepId: String, go: () -> Unit) {
        val step = st.steps.firstOrNull { it.id == stepId } ?: return go()
        val n = (st.progress.tallies[stepId] ?: 0) + 1
        when {
            n > step.target -> h.undo()
            n == step.target -> h.success()
            else -> h.tick()
        }
        go()
    }
}
