package com.fisudur.app.data

import android.content.Context
import com.fisudur.app.engine.DayEntry
import com.fisudur.app.engine.DayKind
import com.fisudur.app.engine.PageRange
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class Settings(
    val amount: Int = 2,
    /** null = default for the amount (40, or 30 for three pages). */
    val repetitions: Int? = null,
    /** Minutes after midnight when the day ends; 0 = midnight. */
    val dayEndMinutes: Int = 0,
    val offWeekdays: Set<DayOfWeek> = setOf(DayOfWeek.FRIDAY),
    val startDate: LocalDate? = null,
    val startPage: Int = 1,
) {
    val dayEnd: LocalTime get() = LocalTime.of(dayEndMinutes / 60, dayEndMinutes % 60)
}

/** Progress inside the current plan day, before it is finished. */
data class DayProgress(
    val date: LocalDate,
    val checked: Set<String> = emptySet(),
    val counter: Int = 0,
    /** True when today was switched to an off day (sickness, travel) or a weekly off day was switched to memorizing. */
    val kindOverride: DayKind? = null,
)

/** Keeps everything in one JSON blob in SharedPreferences: small, simple and easy to back up. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("fisudur", Context.MODE_PRIVATE)

    fun loadSettings(): Settings {
        val s = prefs.getString("settings", null) ?: return Settings()
        val o = JSONObject(s)
        val off = o.optJSONArray("off")?.let { a -> (0 until a.length()).map { DayOfWeek.of(a.getInt(it)) }.toSet() }
        return Settings(
            amount = o.optInt("amount", 2),
            repetitions = if (o.has("reps") && !o.isNull("reps")) o.getInt("reps") else null,
            dayEndMinutes = o.optInt("dayEnd", 0),
            offWeekdays = off ?: setOf(DayOfWeek.FRIDAY),
            startDate = o.optString("start", "").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            startPage = o.optInt("startPage", 1),
        )
    }

    fun saveSettings(s: Settings) {
        val o = JSONObject()
            .put("amount", s.amount)
            .put("reps", s.repetitions ?: JSONObject.NULL)
            .put("dayEnd", s.dayEndMinutes)
            .put("off", JSONArray(s.offWeekdays.map { it.value }))
            .put("start", s.startDate?.toString() ?: "")
            .put("startPage", s.startPage)
        prefs.edit().putString("settings", o.toString()).apply()
    }

    fun loadHistory(): List<DayEntry> {
        val s = prefs.getString("history", null) ?: return emptyList()
        val a = JSONArray(s)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            DayEntry(
                date = LocalDate.parse(o.getString("d")),
                kind = DayKind.valueOf(o.getString("k")),
                pages = if (o.has("f")) PageRange(o.getInt("f"), o.getInt("t")) else null,
            )
        }.sortedBy { it.date }
    }

    fun saveHistory(h: List<DayEntry>) {
        val a = JSONArray()
        h.sortedBy { it.date }.forEach { e ->
            val o = JSONObject().put("d", e.date.toString()).put("k", e.kind.name)
            e.pages?.let { o.put("f", it.from).put("t", it.to) }
            a.put(o)
        }
        prefs.edit().putString("history", a.toString()).apply()
    }

    fun loadProgress(): DayProgress? {
        val s = prefs.getString("progress", null) ?: return null
        val o = JSONObject(s)
        val c = o.optJSONArray("c") ?: JSONArray()
        return DayProgress(
            date = LocalDate.parse(o.getString("d")),
            checked = (0 until c.length()).map { c.getString(it) }.toSet(),
            counter = o.optInt("n", 0),
            kindOverride = o.optString("k", "").takeIf { it.isNotEmpty() }?.let { DayKind.valueOf(it) },
        )
    }

    fun saveProgress(p: DayProgress) {
        val o = JSONObject()
            .put("d", p.date.toString())
            .put("c", JSONArray(p.checked.toList()))
            .put("n", p.counter)
            .put("k", p.kindOverride?.name ?: "")
        prefs.edit().putString("progress", o.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
