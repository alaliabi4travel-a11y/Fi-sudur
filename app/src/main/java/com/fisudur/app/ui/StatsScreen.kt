package com.fisudur.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fisudur.app.Actions
import com.fisudur.app.NoActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fisudur.app.UiState
import com.fisudur.app.engine.DayKind
import java.time.LocalDate

@Composable
fun StatsScreen(s: UiState, a: Actions = NoActions) {
    val st = s.stats
    val pal = LocalPalette.current
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("إحصائياتي", "مسيرتك مع كتاب الله")

        // Hero: pages memorized with a big ring.
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.hero)) {
            KhatamPattern(Modifier.matchParentSize(), color = pal.gold.copy(alpha = .09f))
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("المحفوظ", style = MaterialTheme.typography.labelLarge, color = pal.gold)
                    Text(st.pagesMemorized.ar(), style = MaterialTheme.typography.displayLarge, color = pal.onHero)
                    Text("من ٦٠٤ في المصحف", style = MaterialTheme.typography.bodyLarge, color = pal.onHeroMuted)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "الجزء ${st.currentJuz.ar()} · بقي ${pagesText(st.pagesRemaining)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.onHeroMuted,
                    )
                }
                Ring(
                    progress = st.percent / 100f,
                    modifier = Modifier.size(120.dp),
                    color = pal.gold,
                    track = Color.White.copy(alpha = .14f),
                    width = 8.dp,
                ) {
                    Text("${st.percent.ar()}٪", style = MaterialTheme.typography.headlineMedium, color = pal.onHero)
                }
            }
        }

        // Projected finish.
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.EventAvailable, pal.goldSoft, pal.gold)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("موعد الختم المتوقع", style = MaterialTheme.typography.labelMedium, color = pal.muted)
                    val f = st.projectedFinish
                    Text(
                        if (f != null) Fmt.date(f).ar() else "أتممت الحفظ، الحمد لله",
                        style = MaterialTheme.typography.headlineMedium,
                        color = pal.ink,
                    )
                    if (f != null) {
                        Text(
                            "بعد ${daysText(st.daysToFinish ?: 0L)}، بمقدار ${amountLabel(s.settings.amount)} يوميًا",
                            style = MaterialTheme.typography.bodySmall,
                            color = pal.muted,
                        )
                    }
                }
            }
        }

        // The record of days, in the four kinds used across the app.
        val leave = s.history.count { it.kind == DayKind.OFF && it.reason == null }
        val excused = s.history.filter { it.kind == DayKind.OFF && it.reason != null }
        val byReason = excused.groupingBy { it.reason!! }.eachCount()
        SectionTitle("سجل الأيام")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DayTile("حفظ", st.memoDays, pal.done, null, Modifier.weight(1f))
            DayTile("إجازة", leave, pal.gold, null, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DayTile(
                "عذر", excused.size, pal.excuse,
                listOf("عمل", "سفر", "مرض").mapNotNull { r -> byReason[r]?.let { "$r ${it.ar()}" } }.joinToString(" · ").ifEmpty { null },
                Modifier.weight(1f),
            )
            DayTile(
                "تفريط", st.missedDays, pal.warn,
                if (st.missedThisMonth > 0) "هذا الشهر ${st.missedThisMonth.ar()}" else null,
                Modifier.weight(1f),
            )
        }

        // Streak.
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.LocalFireDepartment, pal.goldSoft, pal.gold)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("الاستمرار الحالي", style = MaterialTheme.typography.labelMedium, color = pal.muted)
                    Text(daysText(st.currentStreak.toLong()), style = MaterialTheme.typography.headlineMedium, color = pal.ink)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("أطول استمرار", style = MaterialTheme.typography.labelMedium, color = pal.muted)
                    Text(st.longestStreak.ar(), style = MaterialTheme.typography.titleLarge, color = pal.gold)
                }
            }
        }

        SectionTitle("آخر ٢٨ يومًا")
        Panel(Modifier.fillMaxWidth()) {
            LastDays(s) { picked = it }
            Spacer(Modifier.height(14.dp))
            Legend()
            Spacer(Modifier.height(8.dp))
            Text("اضغط يوم التفريط لتسجيله إن كنت حفظت فيه أو كان لك عذر.", style = MaterialTheme.typography.bodySmall, color = pal.muted)
        }
        Spacer(Modifier.height(12.dp))
    }

    picked?.let { d ->
        val memoOk = a.canBackfillMemo(s, d)
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text("${Fmt.date(d).ar()}") },
            text = {
                Text(
                    if (memoOk) "هذا اليوم انتهى دون تسجيل. هل حفظت فيه، أم كان لك عذر؟"
                    else "هذا اليوم انتهى دون تسجيل. بعده أيام مسجّلة، فيُحتسب عذرًا فقط حتى لا تتغير أوجهها. ما العذر؟",
                )
            },
            confirmButton = {
                if (memoOk) TextButton(onClick = { a.backfill(d, DayKind.MEMO); picked = null }) { Text("حفظت فيه") }
            },
            dismissButton = {
                Row {
                    listOf("عمل", "سفر", "مرض").forEach { r ->
                        TextButton(onClick = { a.backfill(d, DayKind.OFF, r); picked = null }) { Text(r) }
                    }
                }
            },
        )
    }
}

private fun amountLabel(n: Int) = when (n) {
    1 -> "وجه"
    2 -> "وجهين"
    else -> "${n.ar()} أوجه"
}

@Composable
private fun LastDays(s: UiState, onMissed: (LocalDate) -> Unit) {
    val byDate = s.history.associateBy { it.date }
    val start = s.settings.startDate ?: s.planDate
    val days = (27 downTo 0).map { s.planDate.minusDays(it.toLong()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { d ->
                    val (bg, fg) = dayColors(d, byDate[d], start, s.planDate)
                    val missed = byDate[d] == null && !d.isBefore(start) && d.isBefore(s.planDate)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                            .clickable(enabled = missed) { onMissed(d) },
                        contentAlignment = Alignment.Center,
                    ) { Text(d.dayOfMonth.ar(), style = MaterialTheme.typography.labelMedium, color = fg) }
                }
            }
        }
    }
}

@Composable
private fun dayColors(d: LocalDate, e: com.fisudur.app.engine.DayEntry?, start: LocalDate, today: LocalDate): Pair<Color, Color> {
    val pal = LocalPalette.current
    return when {
        e?.kind == DayKind.MEMO -> pal.done to Color.White
        e?.kind == DayKind.OFF && e.reason != null -> pal.excuse to Color.White
        e?.kind == DayKind.OFF -> pal.gold to Color.White
        d == today -> pal.goldSoft to pal.ink
        d.isBefore(start) || d.isAfter(today) -> MaterialTheme.colorScheme.surfaceVariant to pal.muted
        else -> pal.warn to Color.White
    }
}

@Composable
private fun Legend() {
    val pal = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(pal.done, "حفظ")
        LegendItem(pal.gold, "إجازة")
        LegendItem(pal.excuse, "عذر")
        LegendItem(pal.warn, "تفريط")
    }
}

@Composable
private fun DayTile(label: String, count: Int, color: Color, note: String?, modifier: Modifier) {
    val pal = LocalPalette.current
    Panel(modifier, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = pal.muted)
        }
        Spacer(Modifier.height(2.dp))
        Text(count.ar(), style = MaterialTheme.typography.headlineMedium, color = pal.ink)
        Text(note ?: " ", style = MaterialTheme.typography.bodySmall, color = pal.muted, maxLines = 1)
    }
}

@Composable
private fun LegendItem(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(c))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.muted)
    }
}
