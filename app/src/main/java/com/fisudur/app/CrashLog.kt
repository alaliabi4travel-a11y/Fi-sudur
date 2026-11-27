package com.fisudur.app

import android.content.Context
import java.io.File

/**
 * The last crash, kept in a file inside the app so it can be sent from Settings.
 * Nothing leaves the phone unless the person sends it.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val text = buildString {
                    appendLine("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), ${android.os.Build.MODEL}")
                    appendLine(java.time.LocalDateTime.now().toString())
                    appendLine()
                    append(e.stackTraceToString())
                }
                file(app).writeText(text)
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }
}
