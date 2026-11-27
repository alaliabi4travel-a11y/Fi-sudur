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
    /** The pages before [startPage] were memorized earlier: they join linking and review from day one. */
    val memorizedBefore: Boolean = false,
    /** A folder (a document tree the person picked) where a copy is written every day; null = none. */
    val backupFolder: String? = null,
    /** The reciter heard in the listening step (a quran.com recitation id). */
    val reciter: Int = 7,
) {
    /** The first page held: 1 when the pages before the start were memorized earlier. */
    val firstKnown: Int get() = if (memorizedBefore) 1 else startPage

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

    init { migrateLateDayEnd() }

    /**
     * A day ending after noon used to be named after the date it starts on; it is now named
     * after the date it ends on (see DayClock). Once, the day in progress moves to its new name.
     * Here, so it runs before anything reads the store: the app, the widget or the day-end alarm.
     */
    private fun migrateLateDayEnd() {
        if (flag("lateDayNamed")) return
        if (loadSettings().dayEndMinutes > 12 * 60) {
            loadProgress()?.let { saveProgress(it.copy(date = it.date.plusDays(1))) }
            loadNextProgress()?.let { saveNextProgress(it.copy(date = it.date.plusDays(1))) }
        }
        setFlag("lateDayNamed")
    }

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
            memorizedBefore = o.optBoolean("known", false),
            backupFolder = o.optString("folder", "").takeIf { it.isNotEmpty() },
            reciter = o.optInt("reciter", 7),
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
            .put("known", s.memorizedBefore)
            .put("folder", s.backupFolder ?: "")
            .put("reciter", s.reciter)
        prefs.edit().putString("settings", o.toString()).commit()
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
                half = o.optInt("h", 0),
            )
        }.sortedBy { it.date }
    }

    fun saveHistory(h: List<DayEntry>) {
        val a = JSONArray()
        h.sortedBy { it.date }.forEach { e ->
            val o = JSONObject().put("d", e.date.toString()).put("k", e.kind.name)
            e.pages?.let { o.put("f", it.from).put("t", it.to) }
            e.reason?.let { o.put("r", it) }
            if (e.half != 0) o.put("h", e.half)
            a.put(o)
        }
        // Written at once: the day-end alarm saves from the background, where the process may stop right after.
        prefs.edit().putString("history", a.toString()).commit()
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

    /** One-time markers, such as a migration that has run. Kept apart from the plan, so a restore never resets them. */
    fun flag(name: String): Boolean = prefs.getBoolean("flag_$name", false)

    fun setFlag(name: String) {
        prefs.edit().putBoolean("flag_$name", true).commit()
    }

    fun clearAll() {
        val e = prefs.edit()
        prefs.all.keys.filterNot { it.startsWith("flag_") }.forEach { e.remove(it) }
        e.commit()
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
        .put("weak", prefs.getString("weak", null)?.let(::JSONObject) ?: JSONObject())
        .toString(2)

    /** What a backup holds, read without restoring it: shown before a restore. */
    data class Summary(val exportedAt: String, val days: Int, val lastPage: Int?, val startDate: String?)

    fun summarize(text: String): Summary {
        val o = JSONObject(text)
        require(o.optString("app") == "fi-sudur") { "not a backup" }
        val h = o.getJSONArray("history")
        var last: Int? = null
        for (i in 0 until h.length()) {
            val e = h.getJSONObject(i)
            if (e.has("t")) last = maxOf(last ?: 0, e.getInt("t"))
        }
        return Summary(
            exportedAt = o.optString("exportedAt", ""),
            days = h.length(),
            lastPage = last,
            startDate = o.optJSONObject("settings")?.optString("start", "")?.takeIf { it.isNotEmpty() },
        )
    }

    /** The day a copy last went to the chosen backup folder. */
    fun lastFolderCopy(): String? = prefs.getString("folderCopy", null)

    fun setLastFolderCopy(day: String) {
        prefs.edit().putString("folderCopy", day).commit()
    }

    // ---- Weak pages ----------------------------------------------------------

    /** Pages marked as shaky, each with the days of extra reading left before it counts as settled. */
    fun loadWeak(): Map<Int, Int> {
        val o = prefs.getString("weak", null)?.let(::JSONObject) ?: return emptyMap()
        return o.keys().asSequence().associate { it.toInt() to o.getInt(it) }
    }

    fun saveWeak(w: Map<Int, Int>) {
        val o = JSONObject()
        w.toSortedMap().forEach { (p, n) -> o.put(p.toString(), n) }
        prefs.edit().putString("weak", o.toString()).commit()
    }

    /**
     * A day was recorded: if its weak-pages step was done, each weak page has one day less to go,
     * and settles at zero. The pages as they were are kept, so "undo finish" can put them back.
     */
    fun settleWeak(day: DayProgress?) {
        if (day == null || "weak" !in day.checked) return
        // Once per day, though the alarm and the open app may both record it.
        if (prefs.getString("weakBefore", null)?.let(::JSONObject)?.optString("d") == day.date.toString()) return
        val before = loadWeak()
        prefs.edit().putString("weakBefore", JSONObject().put("d", day.date.toString())
            .put("w", JSONObject(before.mapKeys { it.key.toString() })).toString()).commit()
        saveWeak(before.mapValues { it.value - 1 }.filterValues { it > 0 })
    }

    /** Undoes [settleWeak] for [date], if that day settled them. */
    fun unsettleWeak(date: LocalDate) {
        val o = prefs.getString("weakBefore", null)?.let(::JSONObject) ?: return
        if (o.optString("d") != date.toString()) return
        val w = o.getJSONObject("w")
        saveWeak(w.keys().asSequence().associate { it.toInt() to w.getInt(it) })
        prefs.edit().remove("weakBefore").commit()
    }

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
            .remove("next")
            .remove("weakBefore")
            .apply { val w = o.optJSONObject("weak"); if (w != null) putString("weak", w.toString()) else remove("weak") }
            .putInt("schema", SCHEMA)
            .commit()
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
