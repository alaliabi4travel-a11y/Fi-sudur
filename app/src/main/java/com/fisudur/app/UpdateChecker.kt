package com.fisudur.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val pageUrl: String)

/**
 * Looks at the latest GitHub release of the app's repository. Release tags are
 * "v<versionName>" and the release body carries "versionCode=<n>".
 */
object UpdateChecker {
    const val REPO = "alaliabi4travel-a11y/Fi-sudur"

    suspend fun latest(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode == 404) return@runCatching null
            val body = conn.inputStream.bufferedReader().readText()
            val o = JSONObject(body)
            val code = Regex("versionCode=(\\d+)").find(o.optString("body"))?.groupValues?.get(1)?.toInt() ?: 0
            val assets = o.optJSONArray("assets")
            var apk = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
                }
            }
            UpdateInfo(code, o.optString("tag_name").removePrefix("v"), apk, o.optString("html_url"))
        }
    }
}
