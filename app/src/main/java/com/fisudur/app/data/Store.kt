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
    /** Daily reminder, minutes after midnight; null = off. */
    val reminderMinutes: Int? = 20 * 60,
    /** A second reminder an hour before the day ends, only if the day is not finished. */
    val lateReminder: Boolean = true,
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
    /** Taps counted on steps read several times (yesterday ×5, listening ×3). */
    val tallies: Map<String, Int> = emptyMap(),
    /** Work, travel or sickness, when today was made an off day. */
    val reason: String? = null,
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
            reminderMinutes = if (!o.has("remind")) 20 * 60 else if (o.isNull("remind")) null else o.getInt("remind"),
            lateReminder = o.optBoolean("late", true),
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
            .put("remind", s.reminderMinutes ?: JSONObject.NULL)
            .put("late", s.lateReminder)
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
                reason = o.optString("r", "").takeIf { it.isNotEmpty() },
            )
        }.sortedBy { it.date }
    }

    fun saveHistory(h: List<DayEntry>) {
        val a = JSONArray()
        h.sortedBy { it.date }.forEach { e ->
            val o = JSONObject().put("d", e.date.toString()).put("k", e.kind.name)
            e.pages?.let { o.put("f", it.from).put("t", it.to) }
            e.reason?.let { o.put("r", it) }
            a.put(o)
        }
        prefs.edit().putString("history", a.toString()).apply()
    }

    fun loadProgress(): DayProgress? = readProgress("progress")

    fun saveProgress(p: DayProgress) = writeProgress("progress", p)

    /** Steps of tomorrow's wird started early, after today was finished. */
    fun loadNextProgress(): DayProgress? = readProgress("next")

    fun saveNextProgress(p: DayProgress?) {
        if (p == null) prefs.edit().remove("next").commit() else writeProgress("next", p)
    }

    private fun readProgress(key: String): DayProgress? {
        val s = prefs.getString(key, null) ?: return null
        val o = JSONObject(s)
        val c = o.optJSONArray("c") ?: JSONArray()
        return DayProgress(
            date = LocalDate.parse(o.getString("d")),
            checked = (0 until c.length()).map { c.getString(it) }.toSet(),
            counter = o.optInt("n", 0),
            kindOverride = o.optString("k", "").takeIf { it.isNotEmpty() }?.let { DayKind.valueOf(it) },
            reason = o.optString("r", "").takeIf { it.isNotEmpty() },
            tallies = o.optJSONObject("t")?.let { t -> t.keys().asSequence().associateWith { t.getInt(it) } } ?: emptyMap(),
        )
    }

    private fun writeProgress(key: String, p: DayProgress) {
        val o = JSONObject()
            .put("d", p.date.toString())
            .put("c", JSONArray(p.checked.toList()))
            .put("n", p.counter)
            .put("k", p.kindOverride?.name ?: "")
            .put("t", JSONObject(p.tallies))
            .put("r", p.reason ?: "")
        prefs.edit().putString(key, o.toString()).commit()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // ---- Backup ------------------------------------------------------------

    /** Everything the app knows, as one JSON document that can be saved anywhere and restored. */
    fun exportJson(): String = JSONObject()
        .put("app", "fi-sudur")
        .put("schema", SCHEMA)
        .put("exportedAt", java.time.LocalDateTime.now().toString())
        .put("settings", prefs.getString("settings", null)?.let(::JSONObject) ?: JSONObject())
        .put("history", prefs.getString("history", null)?.let(::JSONArray) ?: JSONArray())
        .put("progress", prefs.getString("progress", null)?.let(::JSONObject) ?: JSONObject.NULL)
        .toString(2)

    /** Restores an exported document. Throws if it is not one of ours; nothing changes in that case. */
    fun importJson(text: String) {
        val o = JSONObject(text)
        require(o.optString("app") == "fi-sudur") { "not a backup" }
        val settings = o.getJSONObject("settings")
        val history = o.getJSONArray("history")
        // Parse before writing anything, so a broken file cannot leave half a restore behind.
        for (i in 0 until history.length()) {
            val e = history.getJSONObject(i)
            LocalDate.parse(e.getString("d")); DayKind.valueOf(e.getString("k"))
        }
        prefs.edit()
            .putString("settings", settings.toString())
            .putString("history", history.toString())
            .apply {
                val p = o.opt("progress")
                if (p is JSONObject) putString("progress", p.toString()) else remove("progress")
            }
            .putInt("schema", SCHEMA)
            .apply()
    }

    private val backupDir = java.io.File(context.filesDir, "backups").apply { mkdirs() }

    /** Copies kept inside the phone, newest first. */
    fun autoBackups(): List<java.io.File> =
        backupDir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.name } ?: emptyList()

    /**
     * Keeps one copy a day (the last [KEEP] days), plus one before anything destructive.
     * Skipped while there is no plan yet.
     */
    fun autoBackup(force: Boolean = false) {
        if (!prefs.contains("settings")) return
        val today = LocalDate.now().toString()
        if (!force && autoBackups().any { it.name.startsWith(today) }) return
        val stamp = java.time.LocalDateTime.now().toString().replace(':', '-').take(19)
        java.io.File(backupDir, "$stamp.json").writeText(exportJson())
        autoBackups().drop(KEEP).forEach { it.delete() }
    }

    companion object {
        const val SCHEMA = 1
        const val KEEP = 10
    }
}
