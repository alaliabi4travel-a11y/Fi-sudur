package com.fisudur.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Finds the latest GitHub release, downloads its APK inside the app and hands it
 * to Android's installer, so updates never go through a browser.
 * Release tags are "v<versionName>"; the release body carries "versionCode=<n>".
 */
object Updater {
    const val REPO = "alaliabi4travel-a11y/Fi-sudur"

    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode == 404) return@withContext null
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val body = o.optString("body")
            val code = Regex("versionCode=(\\d+)").find(body)?.groupValues?.get(1)?.toInt() ?: 0
            val assets = o.optJSONArray("assets")
            var apk = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
                }
            }
            val notes = body.lines().firstOrNull { it.isNotBlank() && !it.startsWith("versionCode") }.orEmpty()
            UpdateInfo(code, o.optString("tag_name").removePrefix("v"), apk, notes)
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the APK into the app's cache, following GitHub's redirect to its file host. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "fi-sudur-${info.versionName}.apk")
            var url = URL(info.apkUrl)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                val code = conn.responseCode
                if (code in 300..399 && hops < 5) {
                    url = URL(url, conn.getHeaderField("Location"))
                    conn.disconnect()
                    hops++
                    continue
                }
                if (code != 200) error("HTTP $code")
                break
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            out
        }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the screen where the person allows this app to install updates. */
    fun requestInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
