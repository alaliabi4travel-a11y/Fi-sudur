package com.fisudur.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fisudur.app.AppViewModel
import com.fisudur.app.Step
import com.fisudur.app.StepAction
import com.fisudur.app.UiState
import com.fisudur.app.engine.DayKind
import java.time.Duration

@Composable
fun HomeScreen(s: UiState, vm: AppViewModel, openCounter: () -> Unit, openRecordings: () -> Unit) {
    var confirmFinish by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item { DayCard(s) }
        item { AmountRow(s, vm) }
        item { PlanHeader(s) }
        if (s.doneToday != null) {
            item { DoneCard(s, vm) }
        } else {
            itemsIndexed(s.steps) { i, step ->
                StepRow(i + 1, step, step.id in s.progress.checked, s) {
                    when (step.action) {
                        StepAction.COUNTER -> openCounter()
                        StepAction.RECORD -> openRecordings()
                        StepAction.CHECK -> vm.toggle(step.id)
                    }
                }
            }
            item {
                Button(
                    onClick = { if (s.allChecked) vm.finishDay() else confirmFinish = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("إنهاء اليوم", fontSize = 17.sp) }
            }
            item { OffDayToggle(s, vm) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("لم تكمل كل الخطوات") },
            text = { Text("هل تريد إنهاء اليوم على كل حال؟ سيُحسب اليوم منجزًا.") },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.finishDay() }) { Text("إنهاء") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("رجوع") } },
        )
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    list: List<T>,
    content: @Composable (Int, T) -> Unit,
) = items(list.size) { i -> content(i, list[i]) }

@Composable
private fun DayCard(s: UiState) {
    val clock = s.clock
    val end = clock.dayEnd(s.now)
    val start = clock.dayStart(s.now)
    val remaining = clock.remaining(s.now)
    val total = Duration.between(start, end).seconds.toFloat()
    val elapsed = (total - remaining.seconds).coerceIn(0f, total)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("يومك", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f), fontSize = 14.sp)
            Text(
                Fmt.date(s.planDate),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Fmt.countdown(remaining),
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "متبقٍ على نهاية اليوم",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) elapsed / total else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .2f),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "بدأ ${Fmt.time(start.toLocalTime())}",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .7f), fontSize = 13.sp,
                )
                Text(
                    "ينتهي ${Fmt.time(end.toLocalTime())}",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .7f), fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun AmountRow(s: UiState, vm: AppViewModel) {
    if (s.doneToday != null || s.plan.kind == DayKind.OFF || s.plan.memorizationDone) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("مقدار الحفظ:", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        listOf(1 to "وجه", 2 to "وجهان", 3 to "٣ أوجه").forEach { (n, label) ->
            FilterChip(
                selected = s.settings.amount == n,
                onClick = { vm.saveSettings(s.settings.copy(amount = n)) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun PlanHeader(s: UiState) {
    val p = s.plan
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("خطة اليوم", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Badge("اليوم ${p.planDay}")
                if (p.round > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge("الجولة ${p.round}")
                }
            }
            Spacer(Modifier.height(10.dp))
            val newPages = p.newPages
            when {
                p.kind == DayKind.OFF -> Text(
                    "يوم إجازة: ربط ومراجعة فقط، بلا حفظ جديد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                newPages != null -> {
                    Text("رقم الوجه", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(newPages.label, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("سورة ${newPages.surahs}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }
                else -> Text("أتممت حفظ المصحف، بارك الله لك. يستمر الربط والمراجعة.")
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer) }
}

@Composable
private fun StepRow(n: Int, step: Step, checked: Boolean, s: UiState, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(
                    if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                else Text("$n", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(step.times, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
                step.detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                if (step.action == StepAction.COUNTER) {
                    Text("العداد: ${s.progress.counter} / ${step.target}", fontSize = 13.sp)
                }
            }
            when (step.action) {
                StepAction.CHECK -> Checkbox(checked = checked, onCheckedChange = { onClick() })
                else -> Icon(Icons.Outlined.ChevronLeft, null)
            }
        }
    }
}

@Composable
private fun DoneCard(s: UiState, vm: AppViewModel) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Check, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
            Text("أنجزت يومك", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            val pages = s.doneToday?.pages
            Text(
                if (pages != null) "حفظت ${pages.label} (${pages.surahs})" else "يوم ربط ومراجعة",
                textAlign = TextAlign.Center,
            )
            Text(
                "يبدأ يومك التالي ${Fmt.time(s.clock.dayEnd(s.now).toLocalTime())}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            TextButton(onClick = { vm.undoFinish() }) { Text("تراجع عن الإنهاء") }
        }
    }
}

@Composable
private fun OffDayToggle(s: UiState, vm: AppViewModel) {
    val weeklyOff = s.planDate.dayOfWeek in s.settings.offWeekdays
    val isOff = s.plan.kind == DayKind.OFF
    OutlinedButton(
        onClick = {
            vm.setTodayKind(
                when {
                    isOff && weeklyOff -> DayKind.MEMO // memorize on a weekly off day
                    isOff -> null // back to normal
                    weeklyOff -> null
                    else -> DayKind.OFF // sickness or travel
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.BeachAccess, null)
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                isOff && weeklyOff -> "أريد الحفظ اليوم رغم الإجازة"
                isOff -> "إلغاء الإجازة والعودة للحفظ"
                weeklyOff -> "العودة إلى يوم الإجازة"
                else -> "اجعل اليوم إجازة (مرض أو سفر)"
            },
        )
    }
}
