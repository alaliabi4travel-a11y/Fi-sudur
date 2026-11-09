package com.fisudur.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fisudur.app.data.Store
import com.fisudur.app.engine.DayKind
import com.fisudur.app.ui.Fmt
import com.fisudur.app.ui.ar
import java.time.LocalDateTime

/** Home-screen widget: today's pages, steps done, and when the day ends. */
class WirdWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WirdWidget::class.java))
                if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
            }
        }

        private fun views(context: Context): RemoteViews {
            val store = Store(context)
            val s = buildUiState(LocalDateTime.now(), store.loadSettings(), store.loadHistory(), store.loadProgress())
            val v = RemoteViews(context.packageName, R.layout.widget_wird)
            val pages = s.plan.newPages
            when {
                s.needsSetup -> {
                    v.setTextViewText(R.id.w_pages, "في صدور")
                    v.setTextViewText(R.id.w_surah, "اضغط لبدء خطتك")
                    v.setTextViewText(R.id.w_steps, "")
                }
                else -> {
                    v.setTextViewText(
                        R.id.w_pages,
                        when {
                            s.plan.kind == DayKind.OFF -> "يوم إجازة"
                            pages == null -> "خُتم الحفظ"
                            pages.from == pages.to -> pages.from.ar()
                            else -> "${pages.from.ar()} – ${pages.to.ar()}"
                        },
                    )
                    v.setTextViewText(R.id.w_surah, if (pages != null) "سورة ${pages.surahs}" else "ربط ومراجعة")
                    v.setTextViewText(
                        R.id.w_steps,
                        if (s.doneToday != null) "أنجزت يومك ✓"
                        else "${s.checkedCount.ar()} من ${s.steps.size.ar()} خطوات، ينتهي ${Fmt.time(s.clock.dayEnd(s.now).toLocalTime()).ar()}",
                    )
                }
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.w_root, open)
            return v
        }
    }
}
