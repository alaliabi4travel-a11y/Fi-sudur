package com.fisudur.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One ayah of a page: its key ("2:6"), its Uthmani text, and its tafsir when asked for. */
data class Ayah(val key: String, val text: String, val tafsir: String? = null, val audio: String? = null) {
    val surah: Int get() = key.substringBefore(':').toInt()
    val number: Int get() = key.substringAfter(':').toInt()
}

/**
 * Page text, Tafsir al-Muyassar and recitation audio from the public quran.com API.
 * Each page is fetched once and kept on the phone, so a page opened before works offline.
 */
class QuranApi(context: Context) {
    private val cache = File(context.filesDir, "quran").apply { mkdirs() }

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    /** Every item of [key] over all result pages of a paginated endpoint. */
    private fun all(base: String, key: String): JSONArray {
        val out = JSONArray()
        var page = 1
        while (true) {
            val sep = if ('?' in base) '&' else '?'
            val o = get("$base${sep}per_page=50&page=$page")
            val a = o.getJSONArray(key)
            for (i in 0 until a.length()) out.put(a.get(i))
            val next = o.optJSONObject("pagination")?.opt("next_page")
            if (next == null || next == JSONObject.NULL) break
            page++
            if (page > 20) break
        }
        return out
    }

    private fun cached(name: String, fetch: () -> JSONArray): JSONArray {
        val f = File(cache, name)
        if (f.exists()) runCatching { return JSONArray(f.readText()) }
        val a = fetch()
        runCatching { f.writeText(a.toString()) }
        return a
    }

    /** The ayat of a mushaf page with Tafsir al-Muyassar and the audio of [reciter]. */
    suspend fun page(page: Int, reciter: Int): List<Ayah> = withContext(Dispatchers.IO) {
        val verses = cached("text_$page.json") {
            get("$API/quran/verses/uthmani?page_number=$page").getJSONArray("verses")
        }
        val tafsir = runCatching {
            cached("muyassar_$page.json") { all("$API/tafsirs/$MUYASSAR/by_page/$page", "tafsirs") }
        }.getOrNull()
        val audio = runCatching {
            cached("audio_${reciter}_$page.json") { all("$API/recitations/$reciter/by_page/$page", "audio_files") }
        }.getOrNull()
        val tafsirByKey = tafsir?.let { a -> (0 until a.length()).associate { a.getJSONObject(it).let { o -> o.getString("verse_key") to clean(o.optString("text")) } } }
        val audioByKey = audio?.let { a -> (0 until a.length()).associate { a.getJSONObject(it).let { o -> o.getString("verse_key") to o.optString("url") } } }
        (0 until verses.length()).map { i ->
            val v = verses.getJSONObject(i)
            val key = v.getString("verse_key")
            Ayah(key, v.getString("text_uthmani"), tafsirByKey?.get(key), audioByKey?.get(key)?.let(::audioUrl))
        }
    }

    companion object {
        private const val API = "https://api.quran.com/api/v4"
        const val MUYASSAR = 16
        const val DEFAULT_RECITER = 7

        /** Reciters offered in Settings: quran.com recitation id and name. */
        val reciters = listOf(
            7 to "مشاري راشد العفاسي",
            6 to "محمود خليل الحصري",
            12 to "محمود خليل الحصري (المعلّم)",
            9 to "محمد صديق المنشاوي (مرتّل)",
            2 to "عبد الباسط عبد الصمد (مرتّل)",
            3 to "عبد الرحمن السديس",
            10 to "سعود الشريم",
            4 to "أبو بكر الشاطري",
            5 to "هاني الرفاعي",
        )

        /** The API gives a path such as "Alafasy/mp3/002006.mp3"; full links are used as they are. */
        fun audioUrl(path: String): String =
            if (path.startsWith("http")) path else if (path.startsWith("//")) "https:$path" else "https://verses.quran.com/$path"

        /** Tafsir text comes with a little HTML around the Quranic words. */
        fun clean(html: String): String =
            html.replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&amp;", "&").trim()
    }
}
