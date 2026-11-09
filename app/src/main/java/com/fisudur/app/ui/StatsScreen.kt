package com.fisudur.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fisudur.app.UiState
import com.fisudur.app.engine.DayKind
import java.time.LocalDate

@Composable
fun StatsScreen(s: UiState) {
    val st = s.stats
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("إحصائياتي", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("المحفوظ", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${st.pagesMemorized}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text(" وجهًا من 604 · ${st.percent}%", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(bottom = 8.dp))
                }
                LinearProgressIndicator(
                    progress = { st.percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .2f),
                )
                Spacer(Modifier.height(8.dp))
                Text("الجزء ${st.currentJuz} · المتبقي ${st.pagesRemaining} وجهًا", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .85f))
            }
        }

        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("موعد الختم المتوقع", color = MaterialTheme.colorScheme.onSurfaceVariant)
                val f = st.projectedFinish
                Text(
                    if (f != null) Fmt.date(f) else "أتممت الحفظ",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                )
                if (f != null) {
                    Text(
                        "بعد ${st.daysToFinish} يومًا، بمقدار ${amountLabel(s.settings.amount)} يوميًا مع أيام الإجازة الأسبوعية",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("الاستمرار الحالي", "${st.currentStreak} يوم", Modifier.weight(1f))
            Tile("أطول استمرار", "${st.longestStreak} يوم", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("أيام الحفظ", "${st.memoDays}", Modifier.weight(1f))
            Tile("أيام الإجازة والعذر", "${st.offDays}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("أيام الغياب", "${st.missedDays}", Modifier.weight(1f), warn = st.missedDays > 0)
            Tile("الغياب هذا الشهر", "${st.missedThisMonth}", Modifier.weight(1f), warn = st.missedThisMonth > 0)
        }

        Text("آخر ٢٨ يومًا", fontWeight = FontWeight.SemiBold)
        LastDays(s)
        Legend()
        Spacer(Modifier.height(16.dp))
    }
}

private fun amountLabel(n: Int) = when (n) {
    1 -> "وجه"
    2 -> "وجهين"
    else -> "$n أوجه"
}

@Composable
private fun Tile(title: String, value: String, modifier: Modifier, warn: Boolean = false) {
    Card(shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LastDays(s: UiState) {
    val byDate = s.history.associateBy { it.date }
    val start = s.settings.startDate ?: s.planDate
    val days = (27 downTo 0).map { s.planDate.minusDays(it.toLong()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { d -> DayDot(d, dayColor(d, byDate[d]?.kind, start, s.planDate)) }
            }
        }
    }
}

@Composable
private fun dayColor(d: LocalDate, kind: DayKind?, start: LocalDate, today: LocalDate): Color = when {
    kind == DayKind.MEMO -> MaterialTheme.colorScheme.primary
    kind == DayKind.OFF -> MaterialTheme.colorScheme.secondary
    d.isBefore(start) || !d.isBefore(today) -> MaterialTheme.colorScheme.surfaceVariant
    else -> MaterialTheme.colorScheme.error.copy(alpha = .75f)
}

@Composable
private fun DayDot(d: LocalDate, color: Color) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) { Text("${d.dayOfMonth}", fontSize = 12.sp, color = Color.White) }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MaterialTheme.colorScheme.primary, "حفظ")
        LegendItem(MaterialTheme.colorScheme.secondary, "إجازة")
        LegendItem(MaterialTheme.colorScheme.error.copy(alpha = .75f), "غياب")
    }
}

@Composable
private fun LegendItem(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c))
        Text("  $label", fontSize = 13.sp)
    }
}
