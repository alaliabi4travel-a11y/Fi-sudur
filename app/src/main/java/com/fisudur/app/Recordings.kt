package com.fisudur.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Recitations from memory, recorded inside the app and kept on the phone for [KEEP_DAYS] days:
 * record, then listen while following in the mushaf.
 */
class Recordings(private val context: Context) {
    private val dir = File(context.filesDir, "recordings").apply { mkdirs() }
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var current: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Recordings of [date] (a plan date), oldest first. */
    fun of(date: LocalDate): List<File> =
        dir.listFiles { f -> f.name.startsWith("$date") && f.name.endsWith(".m4a") }?.sortedBy { it.name } ?: emptyList()

    fun start(date: LocalDate): Boolean = runCatching {
        stopPlaying()
        val stamp = LocalDateTime.now().toLocalTime().toString().replace(':', '-').take(8)
        val file = File(dir, "${date}_$stamp.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        current = file
        true
    }.getOrElse { recorder?.release(); recorder = null; false }

    /** Stops the recording; returns its file, or null if it failed or was too short to keep. */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val f = current
        current = null
        if (!ok) f?.delete()
        return f?.takeIf { ok && it.exists() }
    }

    fun play(file: File, onDone: () -> Unit) {
        stopPlaying()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPlaying(); onDone() }
                prepare()
                start()
            }
        }.getOrNull()
        if (player == null) onDone()
    }

    fun stopPlaying() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    fun delete(file: File) {
        file.delete()
    }

    /** Removes recordings older than [KEEP_DAYS] days. */
    fun prune(today: LocalDate) {
        val keepFrom = today.minusDays(KEEP_DAYS).toString()
        dir.listFiles()?.filter { it.name.take(10) < keepFrom }?.forEach { it.delete() }
    }

    fun release() {
        stop()
        stopPlaying()
    }

    companion object {
        const val KEEP_DAYS = 7L
    }
}
