package com.fisudur.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fisudur.app.Actions
import com.fisudur.app.Step
import com.fisudur.app.StepAction
import com.fisudur.app.UiState
import com.fisudur.app.UpdateState
import com.fisudur.app.engine.DayKind
import java.time.Duration

@Composable
fun HomeScreen(s: UiState, a: Actions, openCounter: () -> Unit, openRecordings: () -> Unit) {
    var confirmFinish by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(s) }
        if (s.update is UpdateState.Available || s.update is UpdateState.Downloading || s.update is UpdateState.Ready) {
            item { UpdateBanner(s.update, a) }
        }
        item { DayHero(s) }
        item { WirdCard(s, a) }
        if (s.doneToday != null) {
            item { DoneCard(s, a) }
        } else {
            item {
                SectionTitle("خطوات اليوم") {
                    Text(
                        "${s.checkedCount.ar()} من ${s.steps.size.ar()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalPalette.current.gold,
                    )
                }
            }
            item { Timeline(s, a, openCounter, openRecordings) }
            item { FinishButton(s) { if (s.allChecked) a.finishDay() else confirmFinish = true } }
            item { OffDayToggle(s, a) }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("لم تكمل كل الخطوات") },
            text = { Text("هل تريد إنهاء اليوم على كل حال؟ سيُحسب اليوم منجزًا.") },
            confirmButton = { TextButton(onClick = { confirmFinish = false; a.finishDay() }) { Text("إنهاء اليوم") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("رجوع") } },
        )
    }
}

@Composable
private fun Header(s: UiState) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("في صدور", style = MaterialTheme.typography.headlineLarge, color = pal.ink)
            Text(
                if (s.plan.round > 0) "اليوم ${s.plan.planDay.ar()} · الجولة ${s.plan.round.ar()}" else "اليوم ${s.plan.planDay.ar()} من الخطة",
                style = MaterialTheme.typography.bodyMedium,
                color = pal.muted,
            )
        }
        Ring(
            progress = s.stats.percent / 100f,
            modifier = Modifier.size(54.dp),
            color = pal.gold,
            track = pal.line,
            width = 4.dp,
        ) {
            Text("${s.stats.percent.ar()}٪", style = MaterialTheme.typography.labelMedium, color = pal.ink)
        }
    }
}

@Composable
private fun DayHero(s: UiState) {
    val pal = LocalPalette.current
    val clock = s.clock
    val start = clock.dayStart(s.now)
    val end = clock.dayEnd(s.now)
    val remaining = clock.remaining(s.now)
    val total = Duration.between(start, end).seconds.toFloat()
    val elapsed = (total - remaining.seconds).coerceIn(0f, total)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.hero)) {
        KhatamPattern(Modifier.matchParentSize(), color = pal.gold.copy(alpha = .09f))
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("يومك", style = MaterialTheme.typography.labelLarge, color = pal.gold)
                Text(Fmt.day(s.planDate.dayOfWeek), style = MaterialTheme.typography.displaySmall, color = pal.onHero)
                Text(Fmt.shortDate(s.planDate).ar(), style = MaterialTheme.typography.bodyLarge, color = pal.onHeroMuted)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WbTwilight, null, tint = pal.gold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "من ${Fmt.time(start.toLocalTime()).ar()} إلى ${Fmt.time(end.toLocalTime()).ar()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.onHeroMuted,
                    )
                }
            }
            Ring(
                progress = if (total > 0) elapsed / total else 0f,
                modifier = Modifier.size(128.dp),
                color = pal.gold,
                track = Color.White.copy(alpha = .14f),
                width = 7.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.countdown(remaining).ar(), style = MaterialTheme.typography.titleLarge, color = pal.onHero)
                    Text("متبقٍ", style = MaterialTheme.typography.labelMedium, color = pal.onHeroMuted)
                }
            }
        }
    }
}

@Composable
private fun WirdCard(s: UiState, a: Actions) {
    val pal = LocalPalette.current
    val p = s.plan
    Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
        Column(
            Modifier.fillMaxWidth().padding(6.dp)
                .border(1.dp, pal.gold.copy(alpha = .45f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("وِرد اليوم", style = MaterialTheme.typography.labelLarge, color = pal.gold)
            val newPages = p.newPages
            when {
                p.kind == DayKind.OFF -> {
                    Text("يوم إجازة", style = MaterialTheme.typography.displaySmall, color = pal.ink)
                    Text("ربط ومراجعة فقط، بلا حفظ جديد", style = MaterialTheme.typography.bodyMedium, color = pal.muted)
                }
                newPages != null -> {
                    Text(
                        if (newPages.from == newPages.to) newPages.from.ar() else "${newPages.from.ar()} – ${newPages.to.ar()}",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("سورة ${newPages.surahs}", style = MaterialTheme.typography.headlineSmall, color = pal.ink)
                    Text(
                        "${if (newPages.count == 1) "وجه واحد" else "${newPages.count.ar()} أوجه"} · تكرار ${p.repetitions.ar()} مرة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = pal.muted,
                    )
                }
                else -> {
                    Text("خُتم الحفظ", style = MaterialTheme.typography.displaySmall, color = pal.ink)
                    Text("بارك الله لك. يستمر الربط والمراجعة.", style = MaterialTheme.typography.bodyMedium, color = pal.muted)
                }
            }
            Ornament(Modifier.padding(vertical = 12.dp))
            if (s.doneToday == null && p.kind == DayKind.MEMO && !p.memorizationDone) {
                Text("مقدار الحفظ", style = MaterialTheme.typography.labelMedium, color = pal.muted)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf("وجه", "وجهان", "٣ أوجه"),
                    selected = (s.settings.amount - 1).coerceIn(0, 2),
                    onSelect = { a.saveSettings(s.settings.copy(amount = it + 1)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val done = s.checkedCount
                LinearProgressIndicator(
                    progress = { if (s.steps.isEmpty()) 1f else done / s.steps.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = pal.gold,
                    trackColor = pal.line,
                )
            }
        }
    }
}

@Composable
private fun Timeline(s: UiState, a: Actions, openCounter: () -> Unit, openRecordings: () -> Unit) {
    Column {
        s.steps.forEachIndexed { i, step ->
            TimelineRow(
                step = step,
                checked = step.id in s.progress.checked,
                first = i == 0,
                last = i == s.steps.lastIndex,
                counter = s.progress.counter,
                onOpen = {
                    when (step.action) {
                        StepAction.COUNTER -> openCounter()
                        StepAction.RECORD -> openRecordings()
                        StepAction.CHECK -> a.toggle(step.id)
                    }
                },
                onCheck = { a.toggle(step.id) },
            )
        }
    }
}

@Composable
private fun TimelineRow(
    step: Step,
    checked: Boolean,
    first: Boolean,
    last: Boolean,
    counter: Int,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
) {
    val pal = LocalPalette.current
    val node by animateColorAsState(if (checked) pal.gold else pal.card, label = "node")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // the rail
        Box(Modifier.width(36.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(2.dp).height(22.dp).background(if (first) Color.Transparent else pal.line))
                Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else pal.line))
            }
            Box(
                Modifier.padding(top = 12.dp).size(22.dp).clip(CircleShape).background(node)
                    .border(2.dp, if (checked) pal.gold else pal.line, CircleShape)
                    .clickable(onClick = onCheck),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        // the card
        Row(
            Modifier.weight(1f).padding(bottom = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (checked) pal.goldSoft.copy(alpha = .55f) else pal.card)
                .border(1.dp, if (checked) pal.gold.copy(alpha = .35f) else pal.line, RoundedCornerShape(20.dp))
                .clickable(onClick = onOpen)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                stepIcon(step.kind),
                bg = if (checked) pal.gold else MaterialTheme.colorScheme.primaryContainer,
                fg = if (checked) Color.White else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.title, style = MaterialTheme.typography.titleMedium, color = pal.ink)
                Text(step.times.ar(), style = MaterialTheme.typography.bodySmall, color = pal.gold)
                if (step.pages != null) {
                    Text(
                        "${step.pages.ar()} · ${step.surahs}",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.muted,
                    )
                }
                if (step.action == StepAction.COUNTER) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (step.target > 0) (counter.toFloat() / step.target).coerceAtMost(1f) else 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = pal.gold,
                        trackColor = pal.line,
                    )
                    Text("${counter.ar()} / ${step.target.ar()}", style = MaterialTheme.typography.labelSmall, color = pal.muted)
                }
            }
            if (step.action != StepAction.CHECK) {
                Icon(Icons.Outlined.ChevronLeft, null, tint = pal.muted)
            }
        }
    }
}

@Composable
private fun FinishButton(s: UiState, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val ready = s.allChecked
    Box(
        Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(50))
            .background(
                if (ready) Brush.horizontalGradient(listOf(Color(0xFFB8873A), pal.gold, Color(0xFFE2BE72)))
                else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (ready) "أتممت خطوات اليوم · إنهاء اليوم" else "إنهاء اليوم",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun DoneCard(s: UiState, a: Actions) {
    val pal = LocalPalette.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.hero)) {
        KhatamPattern(Modifier.matchParentSize(), color = pal.gold.copy(alpha = .09f))
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(pal.gold), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("أنجزت يومك", style = MaterialTheme.typography.headlineLarge, color = pal.onHero)
            val pages = s.doneToday?.pages
            Text(
                if (pages != null) "حفظت ${pages.label.ar()} من سورة ${pages.surahs}" else "يوم ربط ومراجعة",
                style = MaterialTheme.typography.bodyLarge,
                color = pal.onHeroMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                "يبدأ يومك التالي ${Fmt.time(s.clock.dayEnd(s.now).toLocalTime()).ar()}",
                style = MaterialTheme.typography.bodySmall,
                color = pal.onHeroMuted,
            )
            TextButton(onClick = a::undoFinish) { Text("تراجع عن الإنهاء", color = pal.gold) }
        }
    }
}

@Composable
private fun OffDayToggle(s: UiState, a: Actions) {
    val pal = LocalPalette.current
    val weeklyOff = s.planDate.dayOfWeek in s.settings.offWeekdays
    val isOff = s.plan.kind == DayKind.OFF
    TextButton(
        onClick = {
            a.setTodayKind(
                when {
                    isOff && weeklyOff -> DayKind.MEMO
                    isOff -> null
                    weeklyOff -> null
                    else -> DayKind.OFF
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            when {
                isOff && weeklyOff -> "أريد الحفظ اليوم رغم الإجازة"
                isOff -> "إلغاء الإجازة والعودة للحفظ"
                weeklyOff -> "العودة إلى يوم الإجازة"
                else -> "اجعل اليوم إجازة لعذر (مرض أو سفر)"
            },
            style = MaterialTheme.typography.labelLarge,
            color = pal.muted,
        )
    }
}
