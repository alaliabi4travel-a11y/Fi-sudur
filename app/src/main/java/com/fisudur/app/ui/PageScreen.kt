package com.fisudur.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fisudur.app.Ayah
import com.fisudur.app.AyahPlayer
import com.fisudur.app.QuranApi
import com.fisudur.app.engine.PageRange
import com.fisudur.app.engine.QuranData

/**
 * The new pages inside the app: their ayat to read and listen to, and Tafsir al-Muyassar.
 * A full listening to the end counts one reading of the listening step.
 */
@Composable
fun PageScreen(
    pages: PageRange,
    reciter: Int,
    showTafsir: Boolean,
    listened: Int,
    listenTarget: Int,
    onListened: () -> Unit,
    onClose: () -> Unit,
) {
    val pal = LocalPalette.current
    val context = LocalContext.current
    val api = remember { QuranApi(context) }
    val player = remember { AyahPlayer() }
    var tafsir by remember { mutableStateOf(showTafsir) }
    var ayat by remember { mutableStateOf<List<Ayah>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var playing by remember { mutableIntStateOf(-1) }
    var audioError by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    LaunchedEffect(pages, reciter, attempt) {
        failed = false
        ayat = runCatching { (pages.from..pages.to).flatMap { api.page(it, reciter) } }
            .onFailure { failed = true }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    LaunchedEffect(playing) { if (playing >= 0) runCatching { list.animateScrollToItem(playing) } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        if (pages.from == pages.to) "الوجه ${pages.from.ar()}" else "الوجه ${pages.from.ar()} – ${pages.to.ar()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = pal.ink,
                    )
                    Text("سورة ${pages.surahs}", style = MaterialTheme.typography.bodySmall, color = pal.muted)
                }
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "إغلاق", tint = pal.ink) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("الآيات", !tafsir) { tafsir = false }
                Pill("التفسير الميسّر", tafsir) { tafsir = true }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val a = ayat
                when {
                    failed -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("تعذّر تحميل الوجه. تأكد من الاتصال بالإنترنت؛ ما يُحمَّل مرة يبقى على الهاتف.", color = pal.muted, textAlign = TextAlign.Center)
                        TextButton(onClick = { attempt++ }) { Text("حاول مرة أخرى") }
                    }
                    a == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = pal.gold)
                    else -> LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        itemsIndexed(a) { i, ayah ->
                            AyahRow(ayah, tafsir, playing == i, firstOfSurah = ayah.number == 1)
                        }
                    }
                }
            }
            // Listening: the whole wird, ayah after ayah.
            val a = ayat
            Column(Modifier.fillMaxWidth().background(pal.card).padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (audioError) Text("تعذّر تشغيل الصوت. تأكد من الاتصال بالإنترنت.", style = MaterialTheme.typography.bodySmall, color = pal.warn)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val urls = a?.mapNotNull { it.audio }.orEmpty()
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(50)).background(if (urls.isEmpty()) pal.line else pal.gold)
                            .clickable(enabled = urls.isNotEmpty()) {
                                if (playing >= 0) {
                                    player.stop(); playing = -1
                                } else {
                                    audioError = false
                                    player.play(
                                        urls,
                                        onIndex = { playing = it },
                                        onFinished = { playing = -1; onListened() },
                                        onError = { playing = -1; audioError = true },
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (playing >= 0) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, if (playing >= 0) "إيقاف" else "استمع", tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            QuranApi.reciters.firstOrNull { it.first == reciter }?.second ?: "القارئ",
                            style = MaterialTheme.typography.titleSmall,
                            color = pal.ink,
                        )
                        if (listenTarget > 0) {
                            Text(
                                "الاستماع ${minOf(listened, listenTarget).ar()} من ${listenTarget.ar()} مع النظر في الآيات",
                                style = MaterialTheme.typography.bodySmall,
                                color = pal.muted,
                            )
                        }
                    }
                    if (listenTarget > 0 && listened < listenTarget) {
                        TextButton(onClick = onListened) { Text("استمعت من غيره") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AyahRow(ayah: Ayah, tafsir: Boolean, playing: Boolean, firstOfSurah: Boolean) {
    val pal = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(if (playing) pal.goldSoft else Color.Transparent).padding(8.dp),
    ) {
        if (firstOfSurah) {
            Text(
                "سورة ${QuranData.surahName(ayah.surah)}",
                style = SurahStyle,
                color = pal.gold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
        Text(
            "${ayah.text} ﴿${ayah.number.ar()}﴾",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Amiri, fontSize = if (tafsir) 20.sp else 25.sp, lineHeight = if (tafsir) 36.sp else 46.sp),
            color = pal.ink,
        )
        if (tafsir) {
            Spacer(Modifier.height(4.dp))
            Text(
                ayah.tafsir ?: "التفسير غير متاح الآن.",
                style = MaterialTheme.typography.bodyMedium,
                color = pal.muted,
            )
        }
    }
}
