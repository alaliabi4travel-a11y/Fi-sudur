package com.fisudur.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fisudur.app.Recordings
import com.fisudur.app.engine.PlanRules
import java.io.File
import java.time.LocalDate

/**
 * Record the wird from memory, listen back while following in the mushaf, then say whether it
 * was free of mistakes. Three in a row without a mistake complete the step; a mistake starts again.
 */
@Composable
fun RecordDialog(date: LocalDate, inARow: Int, onResult: (Boolean) -> Unit, onClose: () -> Unit) {
    val pal = LocalPalette.current
    val context = LocalContext.current
    val rec = remember { Recordings(context).also { it.prune(date) } }
    var recording by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(rec.of(date)) }
    var playing by remember { mutableStateOf<File?>(null) }
    var judged by remember { mutableStateOf(setOf<String>()) }
    var failed by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { rec.release() } }
    androidx.compose.runtime.LaunchedEffect(recording) {
        seconds = 0
        while (recording) { kotlinx.coroutines.delay(1000); seconds++ }
    }

    fun begin() {
        failed = !rec.start(date)
        recording = !failed
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) begin() else failed = true }

    AlertDialog(
        onDismissRequest = { if (!recording) onClose() },
        title = { Text("سجّل غيبًا") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "سمّع الورد غيبًا وأنت تسجّل، ثم استمع إلى التسجيل وأنت تنظر في المصحف. " +
                        "${PlanRules.RECORD_REPS.ar()} تسجيلات متتالية بلا خطأ تُتم الخطوة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.muted,
                )
                Text("المتتالية بلا خطأ: ${inARow.ar()} من ${PlanRules.RECORD_REPS.ar()}", style = MaterialTheme.typography.titleSmall, color = pal.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).background(if (recording) pal.warn else pal.gold, CircleShape)
                            .clickable {
                                if (recording) {
                                    rec.stop()
                                    recording = false
                                    files = rec.of(date)
                                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    begin()
                                } else {
                                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic, if (recording) "إيقاف التسجيل" else "ابدأ التسجيل", tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (recording) "جارٍ التسجيل… ${(seconds / 60).ar()}:${"%02d".format(seconds % 60).ar()}" else "اضغط لتبدأ التسجيل",
                        style = MaterialTheme.typography.bodyLarge,
                        color = pal.ink,
                    )
                }
                if (failed) Text("تعذّر التسجيل. اسمح للتطبيق باستعمال الميكروفون من إعدادات الهاتف.", style = MaterialTheme.typography.bodySmall, color = pal.warn)
                files.asReversed().forEachIndexed { i, f ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (playing == f) { rec.stopPlaying(); playing = null } else { playing = f; rec.play(f) { playing = null } }
                            }) { Icon(if (playing == f) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, "استمع", tint = pal.gold) }
                            Text("التسجيل ${(files.size - i).ar()}، الساعة ${f.name.substring(11, 16).replace('-', ':').ar()}", style = MaterialTheme.typography.bodyMedium, color = pal.ink, modifier = Modifier.weight(1f))
                        }
                        if (f.name !in judged) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 48.dp)) {
                                Pill("بلا خطأ", false) { judged = judged + f.name; onResult(true) }
                                Pill("فيه خطأ", false) { judged = judged + f.name; onResult(false) }
                            }
                        }
                    }
                }
                Text("تُحذف التسجيلات بعد ${Recordings.KEEP_DAYS.toInt().ar()} أيام.", style = MaterialTheme.typography.bodySmall, color = pal.muted)
            }
        },
        confirmButton = { TextButton(onClick = { if (recording) { rec.stop(); recording = false }; onClose() }) { Text("تم") } },
        dismissButton = {
            // For recording with another app: record the result here directly.
            if (!recording) Row {
                TextButton(onClick = { onResult(true) }) { Text("سجّلت بغيره: بلا خطأ") }
            }
        },
    )
}
