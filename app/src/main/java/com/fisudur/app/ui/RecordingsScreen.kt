package com.fisudur.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fisudur.app.AppViewModel
import com.fisudur.app.UiState
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun recordingsDir(context: Context): File = File(context.filesDir, "recordings").apply { mkdirs() }

@Composable
fun RecordingsScreen(s: UiState, vm: AppViewModel) {
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("التسجيلات", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "سجّل الوجه غيبًا ثلاث مرات متتالية، ثم استمع وتابع في المصحف. إن وجدت خطأً فأعد التسجيل.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
            )
            if (s.plan.newPages != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = "record" in s.progress.checked, onCheckedChange = { vm.toggle("record") })
                    Text("أتممت التسجيل لهذا اليوم")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (files.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد تسجيلات بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 80.dp)) {
                items(files, key = { it.name }) { f ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (playing == f.name) stopPlayer() else {
                                    stopPlayer()
                                    player = MediaPlayer().apply {
                                        setDataSource(f.absolutePath)
                                        setOnCompletionListener { stopPlayer() }
                                        prepare(); start()
                                    }
                                    playing = f.name
                                }
                            }) {
                                Icon(if (playing == f.name) Icons.Filled.Stop else Icons.Filled.PlayArrow, "تشغيل")
                            }
                            Text(f.nameWithoutExtension, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            IconButton(onClick = {
                                if (playing == f.name) stopPlayer()
                                f.delete(); files = listFiles(context)
                            }) { Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                if (recorder != null) stop()
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            containerColor = if (recorder != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        ) {
            Icon(if (recorder != null) Icons.Filled.Stop else Icons.Filled.Mic, null, modifier = Modifier.size(22.dp))
            Text(if (recorder != null) "  إيقاف التسجيل" else "  تسجيل")
        }
    }
}

private fun listFiles(context: Context): List<File> =
    recordingsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
