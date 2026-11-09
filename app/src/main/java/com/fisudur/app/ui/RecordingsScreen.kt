package com.fisudur.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fisudur.app.Actions
import com.fisudur.app.UiState
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun recordingsDir(context: Context): File = File(context.filesDir, "recordings").apply { mkdirs() }

@Composable
fun RecordingsScreen(s: UiState, a: Actions) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(listFiles(context)) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }

    fun stopPlayer() {
        player?.release(); player = null; playing = null
    }

    fun start() {
        val pages = s.plan.newPages?.label?.replace(" ← ", "-") ?: "مراجعة"
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val f = File(recordingsDir(context), "وجه ${pages} ${stamp}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        recorder = r; recordingFile = f
    }

    fun stop() {
        runCatching { recorder?.stop() }
        recorder?.release(); recorder = null; recordingFile = null
        files = listFiles(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) start()
    }

    DisposableEffect(Unit) {
        onDispose { stop(); stopPlayer() }
    }

    val pal = LocalPalette.current
    val recording = recorder != null
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("التسجيلات", "سجّل غيبًا ثلاث مرات متتالية بلا خطأ") }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.hero)) {
                KhatamPattern(Modifier.matchParentSize(), color = pal.gold.copy(alpha = .09f))
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        s.plan.newPages?.let { "الوجه ${it.label.ar()}" } ?: "تسجيل للمراجعة",
                        style = MaterialTheme.typography.headlineMedium,
                        color = pal.onHero,
                    )
                    s.plan.newPages?.let {
                        Text(it.surahs, style = MaterialTheme.typography.bodyMedium, color = pal.onHeroMuted)
                    }
                    Spacer(Modifier.height(18.dp))
                    Box(
                        Modifier.size(96.dp).clip(CircleShape)
                            .background(if (recording) pal.warn else pal.gold)
                            .border(6.dp, Color.White.copy(alpha = .15f), CircleShape)
                            .clickable {
                                if (recording) stop()
                                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                                else permission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                            if (recording) "إيقاف" else "تسجيل",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (recording) "جارٍ التسجيل… اضغط للإيقاف" else "اضغط لبدء التسجيل",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (recording) pal.onHero else pal.gold,
                    )
                }
            }
        }
        if (s.plan.newPages != null) {
            item {
                val done = "record" in s.progress.checked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(if (done) pal.done.copy(alpha = .12f) else pal.card)
                        .border(1.dp, if (done) pal.done.copy(alpha = .4f) else pal.line, RoundedCornerShape(18.dp))
                        .clickable { a.toggle("record") }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(if (done) pal.done else Color.Transparent)
                            .border(1.5.dp, if (done) pal.done else pal.line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "أتممت التسجيل لهذا اليوم",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (done) pal.done else pal.ink,
                    )
                }
            }
        }
        item { SectionTitle("تسجيلاتك") { Text(files.size.ar(), style = MaterialTheme.typography.labelLarge, color = pal.gold) } }
        if (files.isEmpty()) {
            item {
                Text(
                    "لا توجد تسجيلات بعد. استمع لتسجيلك وتابع في المصحف، فإن وجدت خطأً فأعد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.muted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(files, key = { it.name }) { f ->
            val isPlaying = playing == f.name
            Panel(Modifier.fillMaxWidth(), padding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (isPlaying) pal.gold else MaterialTheme.colorScheme.primary)
                            .clickable {
                                if (isPlaying) stopPlayer() else {
                                    stopPlayer()
                                    player = MediaPlayer().apply {
                                        setDataSource(f.absolutePath)
                                        setOnCompletionListener { stopPlayer() }
                                        prepare(); start()
                                    }
                                    playing = f.name
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, "تشغيل", tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(titleOf(f).ar(), style = MaterialTheme.typography.titleSmall, color = pal.ink)
                        Text(whenOf(f).ar(), style = MaterialTheme.typography.bodySmall, color = pal.muted)
                    }
                    IconButton(onClick = {
                        if (isPlaying) stopPlayer()
                        f.delete(); files = listFiles(context)
                    }) { Icon(Icons.Outlined.DeleteOutline, "حذف", tint = pal.muted) }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** "وجه 5-6 2026-11-09_04-12-00" → "وجه 5-6". */
private fun titleOf(f: File): String = f.nameWithoutExtension.substringBeforeLast(' ')

private fun whenOf(f: File): String =
    Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).toLocalDateTime().let {
        "${Fmt.shortDate(it.toLocalDate())} · ${Fmt.time(it.toLocalTime())}"
    }

private fun listFiles(context: Context): List<File> =
    recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
