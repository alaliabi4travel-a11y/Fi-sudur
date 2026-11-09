package com.fisudur.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fisudur.app.data.Store
import com.fisudur.app.ui.ar
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Two reminders, both inexact (no special permission needed):
 * a daily one at the chosen time, and a late one an hour before the day ends.
 * Neither shows once today is finished.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    const val DAILY = "com.fisudur.app.REMIND_DAILY"
    const val LATE = "com.fisudur.app.REMIND_LATE"
    const val ROLLOVER = "com.fisudur.app.DAY_END"

    fun schedule(context: Context) {
        val store = Store(context)
        val settings = store.loadSettings()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()

        val daily = pending(context, DAILY)
        val late = pending(context, LATE)
        val rollover = pending(context, ROLLOVER)
        am.cancel(daily)
        am.cancel(late)
        am.cancel(rollover)
        if (settings.startDate == null) return

        // Just after the day ends: record a finished day and move the widget to the new day.
        set(am, com.fisudur.app.engine.DayClock(settings.dayEnd).dayEnd(now).plusMinutes(1), rollover)

        settings.reminderMinutes?.let { m ->
            var at = now.toLocalDate().atTime(LocalTime.of(m / 60, m % 60))
            if (!at.isAfter(now)) at = at.plusDays(1)
            set(am, at, daily)
        }
        if (settings.lateReminder) {
            val clock = com.fisudur.app.engine.DayClock(settings.dayEnd)
            var at = clock.dayEnd(now).minusHours(1)
            if (!at.isAfter(now)) at = at.plusDays(1)
            set(am, at, late)
        }
    }

    private fun set(am: AlarmManager, at: LocalDateTime, pi: PendingIntent) {
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60 * 1000L, pi)
    }

    private fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Records the day that just ended if it was fully done (see [autoFinishEntry]). */
    fun autoFinish(context: Context) {
        val store = Store(context)
        val settings = store.loadSettings()
        val history = store.loadHistory()
        val today = com.fisudur.app.engine.DayClock(settings.dayEnd).planDate(LocalDateTime.now())
        autoFinishEntry(settings, history, store.loadProgress(), today)?.let { store.saveHistory(history + it) }
    }

    fun show(context: Context, late: Boolean) {
        val store = Store(context)
        val s = buildUiState(LocalDateTime.now(), store.loadSettings(), store.loadHistory(), store.loadProgress())
        if (s.needsSetup || s.doneToday != null) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "تذكير الورد", NotificationManager.IMPORTANCE_DEFAULT))
        val pages = s.plan.newPages
        val wird = if (pages != null) "الوجه ${pages.label.ar()}، سورة ${pages.surahs}" else "ربط ومراجعة"
        val title = if (late) "بقيت ساعة على نهاية يومك" else "حان وقت وردك"
        val text = if (late) "لم تُنهِ وردك بعد: $wird" else wird
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(if (late) 2 else 1, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.DAILY -> Reminders.show(context, late = false)
            Reminders.LATE -> Reminders.show(context, late = true)
            Reminders.ROLLOVER -> Reminders.autoFinish(context)
        }
        // Boot, time change, or an alarm that just fired: set the next ones.
        Reminders.schedule(context)
        WirdWidget.refresh(context)
    }
}
