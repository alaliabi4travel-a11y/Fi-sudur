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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
fun HomeScreen(s: UiState, a: Actions, openCounter: () -> Unit) {
    var confirmFinish by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        FillScreen(
            minHeight = maxHeight - 12.dp,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Header(s)
            if (s.update is UpdateState.Available || s.update is UpdateState.Downloading || s.update is UpdateState.Ready) {
                UpdateBanner(s.update, a)
            }
            Box(Modifier.layoutId(FILL_CARD)) { TodayCard(s) }
            if (s.doneToday != null) {
                DoneCard(s, a)
                s.tomorrow?.let { t -> TomorrowCard(t, a) }
            } else {
                Timeline(s, a, openCounter)
                Box(Modifier.layoutId(FILL_FOOT)) { FinishButton(s) { if (s.allChecked) a.finishDay() else confirmFinish = true } }
                OffDayToggle(s, a)
            }
        }
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

/** One slim line: name, plan day, and the share of the Quran memorized. */
@Composable
private fun Header(s: UiState) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("في صدور", style = MaterialTheme.typography.headlineMedium, color = pal.ink)
        Spacer(Modifier.width(10.dp))
        Text(
            "اليوم ${s.plan.planDay.ar()}",
            style = MaterialTheme.typography.bodyMedium,
            color = pal.muted,
            modifier = Modifier.weight(1f),
        )
        Chip("${s.stats.percent.ar()}٪ من المصحف", pal.goldSoft, pal.ink)
    }
}

/**
 * Today in one card: the pages and surah on the right, the time left on the left,
 * and the day's progress along the bottom.
 */
@Composable
private fun TodayCard(s: UiState) {
    val pal = LocalPalette.current
    val p = s.plan
    val clock = s.clock
    val start = clock.dayStart(s.now)
    val end = clock.dayEnd(s.now)
    val remaining = clock.remaining(s.now)
    val total = Duration.between(start, end).seconds.toFloat()
    val elapsed = (total - remaining.seconds).coerceIn(0f, total)
    val newPages = p.newPages
    val reason = s.progress.reason ?: s.doneToday?.reason
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(pal.hero), contentAlignment = Alignment.Center) {
        KhatamPattern(Modifier.matchParentSize(), color = pal.gold.copy(alpha = .08f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${Fmt.day(s.planDate.dayOfWeek)}، ${Fmt.shortDate(s.planDate).ar()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = pal.gold,
                    )
                    Spacer(Modifier.height(2.dp))
                    when {
                        p.kind == DayKind.OFF -> {
                            Text(reason?.let { "يوم عذر، $it" } ?: "يوم إجازة", style = MaterialTheme.typography.headlineMedium, color = pal.onHero)
                            Text("لا حفظ جديد، ويستمر ما قبله", style = MaterialTheme.typography.bodySmall, color = pal.onHeroMuted)
                        }
                        newPages != null -> {
                            Text(
                                if (newPages.from == newPages.to) "الوجه ${newPages.from.ar()}" else "الوجه ${newPages.from.ar()} – ${newPages.to.ar()}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = pal.onHero,
                            )
                            Text(
                                "سورة ${newPages.surahs}",
                                style = SurahStyle,
                                color = pal.onHero,
                                maxLines = 1,
                            )
                            Text(
                                "${pagesText(newPages.count)}، تكرار ${p.repetitions.ar()} مرة",
                                style = MaterialTheme.typography.bodySmall,
                                color = pal.onHeroMuted,
                            )
                        }
                        else -> {
                            Text("خُتم الحفظ", style = MaterialTheme.typography.headlineMedium, color = pal.onHero)
                            Text("يستمر الربط والمراجعة", style = MaterialTheme.typography.bodySmall, color = pal.onHeroMuted)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Ring(
                    progress = if (total > 0) elapsed / total else 0f,
                    modifier = Modifier.size(88.dp),
                    color = pal.gold,
                    track = Color.White.copy(alpha = .14f),
                    width = 5.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.countdown(remaining).ar(), style = MaterialTheme.typography.labelLarge.copy(fontFamily = Readex), color = pal.onHero, maxLines = 1, softWrap = false)
                        Text("حتى ${Fmt.time(end.toLocalTime()).ar()}", style = MaterialTheme.typography.labelSmall, color = pal.onHeroMuted)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { if (s.steps.isEmpty()) 1f else s.checkedCount / s.steps.size.toFloat() },
                    modifier = Modifier.weight(1f).height(5.dp).clip(CircleShape),
                    color = pal.gold,
                    trackColor = Color.White.copy(alpha = .14f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${s.checkedCount.ar()} من ${s.steps.size.ar()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = pal.onHeroMuted,
                )
            }
        }
    }
}

@Composable
private fun Timeline(s: UiState, a: Actions, openCounter: () -> Unit) {
    run {
        s.steps.forEachIndexed { i, step ->
            TimelineRow(
                step = step,
                checked = step.id in s.progress.checked,
                first = i == 0,
                last = i == s.steps.lastIndex,
                counter = s.progress.counter,
                tally = s.progress.tallies[step.id] ?: 0,
                onOpen = {
                    when (step.action) {
                        StepAction.COUNTER -> openCounter()
                        StepAction.TALLY -> a.tally(step.id)
                        StepAction.CHECK -> a.toggle(step.id)
                    }
                },
                // Repetition is checked only by the counter reaching its target.
                onCheck = { if (step.action == StepAction.COUNTER) openCounter() else a.toggle(step.id) },
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
    tally: Int,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
) {
    val pal = LocalPalette.current
    val node by animateColorAsState(if (checked) pal.gold else pal.card, label = "node")
    Row(Modifier.layoutId(FILL_ROW).fillMaxWidth().height(IntrinsicSize.Min)) {
        // the rail: the node sits on the middle of its card, the line runs through the gap below
        Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).weight(1f).background(if (first) Color.Transparent else pal.line))
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(node)
                    .border(2.dp, if (checked) pal.gold else pal.line, CircleShape)
                    .clickable(onClick = onCheck),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Box(Modifier.width(2.dp).weight(1f).background(if (last) Color.Transparent else pal.line))
            Box(Modifier.width(2.dp).height(RAIL_GAP).background(if (last) Color.Transparent else pal.line))
        }
        Spacer(Modifier.width(6.dp))
        // the card
        Row(
            Modifier.weight(1f).fillMaxHeight().padding(bottom = RAIL_GAP)
                .clip(RoundedCornerShape(16.dp))
                .background(if (checked) pal.goldSoft.copy(alpha = .55f) else pal.card)
                .border(1.dp, if (checked) pal.gold.copy(alpha = .35f) else pal.line, RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                stepIcon(step.kind),
                bg = if (checked) pal.gold else MaterialTheme.colorScheme.primaryContainer,
                fg = if (checked) Color.White else MaterialTheme.colorScheme.primary,
                size = 32.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(step.title, style = MaterialTheme.typography.titleSmall, color = pal.ink, maxLines = 1)
                // One line: how many times, then which pages.
                val detail = buildString {
                    append(if (step.action == StepAction.COUNTER) "${counter.ar()} / ${step.target.ar()} مرة" else step.times.ar())
                    if (step.pages != null) append("، ${step.pages.ar()} ${step.surahs}")
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = pal.muted,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (step.action == StepAction.COUNTER) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (step.target > 0) (counter.toFloat() / step.target).coerceAtMost(1f) else 0f },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                        color = pal.gold,
                        trackColor = pal.line,
                    )
                }
            }
            if (step.action == StepAction.TALLY) {
                // One dot per reading; tap the card after each one.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(step.target) { i ->
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(if (i < tally || checked) pal.gold else pal.line),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${(if (checked) step.target else tally).ar()} / ${step.target.ar()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = pal.muted,
                    )
                }
            } else if (step.action != StepAction.CHECK) {
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
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50))
            .background(
                if (ready) Brush.horizontalGradient(listOf(Color(0xFFB8873A), pal.gold, Color(0xFFE2BE72)))
                else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (ready) "أتممت خطوات اليوم، إنهاء اليوم" else "إنهاء اليوم",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun DoneCard(s: UiState, a: Actions) {
    val pal = LocalPalette.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.hero)) {
        KhatamPattern(Modifier.matchParentSize(), color = pal.gold.copy(alpha = .09f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(pal.gold), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("أنجزت يومك", style = MaterialTheme.typography.headlineMedium, color = pal.onHero)
            val pages = s.doneToday?.pages
            Text(
                if (pages != null) "حفظت ${if (pages.from == pages.to) "الوجه" else "الأوجه"} ${pages.label.ar()} من سورة ${pages.surahs}" else "يوم إجازة: تكرار وربط ومراجعة",
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

/** After today is finished: tomorrow's wird, folded, so it can be read or started early. */
@Composable
private fun TomorrowCard(t: UiState, a: Actions) {
    val pal = LocalPalette.current
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(pal.card)
                .border(1.dp, pal.gold.copy(alpha = .45f), RoundedCornerShape(18.dp))
                .clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("ورد الغد", style = MaterialTheme.typography.titleLarge, color = pal.ink)
                val pages = t.plan.newPages
                Text(
                    if (pages != null) "${Fmt.day(t.planDate.dayOfWeek)}، الوجه ${pages.label.ar()}، ${pages.surahs}"
                    else "${Fmt.day(t.planDate.dayOfWeek)}، يوم إجازة",
                    style = MaterialTheme.typography.bodySmall,
                    color = pal.muted,
                )
            }
            if (t.checkedCount > 0) {
                Chip("${t.checkedCount.ar()} من ${t.steps.size.ar()}", pal.goldSoft, pal.ink)
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = pal.gold,
            )
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Column {
                t.steps.forEachIndexed { i, step ->
                    TimelineRow(
                        step = step,
                        checked = step.id in t.progress.checked,
                        first = i == 0,
                        last = i == t.steps.lastIndex,
                        counter = t.progress.counter,
                        tally = t.progress.tallies[step.id] ?: 0,
                        onOpen = {
                            when (step.action) {
                                StepAction.COUNTER -> {}
                                StepAction.TALLY -> a.tallyNext(step.id)
                                StepAction.CHECK -> a.toggleNext(step.id)
                            }
                        },
                        onCheck = { if (step.action != StepAction.COUNTER) a.toggleNext(step.id) },
                    )
                }
            }
            Text(
                "ما تنجزه الآن يُحسب لك غدًا. أما التكرار بالعداد فيبدأ مع يوم الغد.",
                style = MaterialTheme.typography.bodySmall,
                color = pal.muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private val offReasons = listOf("عمل", "سفر", "مرض")

@Composable
private fun OffDayToggle(s: UiState, a: Actions) {
    val pal = LocalPalette.current
    val weeklyOff = s.planDate.dayOfWeek in s.settings.offWeekdays
    val isOff = s.plan.kind == DayKind.OFF
    var choosing by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(pal.goldSoft.copy(alpha = .6f))
            .border(1.dp, pal.gold.copy(alpha = .35f), RoundedCornerShape(18.dp))
            .clickable {
                when {
                    isOff && weeklyOff -> a.setTodayKind(DayKind.MEMO)
                    isOff -> a.setTodayKind(null)
                    weeklyOff -> a.setTodayKind(null)
                    else -> choosing = true
                }
            }
            .padding(vertical = 10.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                isOff && weeklyOff -> "أريد الحفظ اليوم رغم الإجازة"
                isOff -> "إلغاء العذر والعودة للحفظ"
                weeklyOff -> "العودة إلى يوم الإجازة"
                else -> "اجعل اليوم إجازة لعذر (عمل، سفر، مرض)"
            },
            style = MaterialTheme.typography.labelLarge,
            color = pal.ink,
        )
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("ما العذر؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("لا حفظ جديد اليوم، ويستمر تكرار الأمس والربط والمراجعة.", style = MaterialTheme.typography.bodyMedium, color = pal.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        offReasons.forEach { r ->
                            Pill(r, false) {
                                choosing = false
                                a.setTodayKind(DayKind.OFF, r)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("إلغاء") } },
        )
    }
}

private const val FILL_CARD = "card"
private const val FILL_ROW = "row"
private const val FILL_FOOT = "foot"
private val RAIL_GAP = 6.dp

/**
 * Lays the home out top to bottom and, when it is shorter than the screen, spends the
 * spare height so the page reaches the bottom instead of leaving it empty:
 * first the step cards grow a little, then the today card, then the gaps between
 * sections; anything left goes above the finish button so it sits near the thumb.
 * When the content is taller than the screen it simply scrolls.
 */
@Composable
private fun FillScreen(minHeight: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { ms, c ->
        val w = c.maxWidth
        val gap = 10.dp.roundToPx()
        val ids = ms.map { it.layoutId }
        // no gap between two step rows: each row carries its own small gap
        val gapBefore = BooleanArray(ms.size) { i -> i > 0 && !(ids[i] == FILL_ROW && ids[i - 1] == FILL_ROW) }
        val gaps = gapBefore.count { it }
        val fixed = arrayOfNulls<androidx.compose.ui.layout.Placeable>(ms.size)
        val natural = IntArray(ms.size)
        ms.forEachIndexed { i, m ->
            if (ids[i] == FILL_ROW || ids[i] == FILL_CARD) {
                natural[i] = m.minIntrinsicHeight(w)
            } else {
                fixed[i] = m.measure(Constraints(minWidth = w, maxWidth = w))
                natural[i] = fixed[i]!!.height
            }
        }
        var spare = (minHeight.roundToPx() - natural.sum() - gaps * gap).coerceAtLeast(0)
        val rows = ids.count { it == FILL_ROW }
        val rowGrow = if (rows > 0) minOf(22.dp.roundToPx(), spare / rows) else 0
        spare -= rowGrow * rows
        val cardGrow = if (FILL_CARD in ids) minOf(32.dp.roundToPx(), spare / 2) else 0
        spare -= cardGrow
        val gapGrow = if (gaps > 0) minOf(14.dp.roundToPx(), spare / gaps) else 0
        spare -= gapGrow * gaps
        val foot = ids.indexOf(FILL_FOOT)
        val placeables = ms.mapIndexed { i, m ->
            fixed[i] ?: run {
                val h = natural[i] + if (ids[i] == FILL_ROW) rowGrow else cardGrow
                m.measure(Constraints(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h))
            }
        }
        val total = placeables.sumOf { it.height } + gaps * (gap + gapGrow) + if (foot >= 0) spare else 0
        layout(w, total) {
            var y = 0
            placeables.forEachIndexed { i, p ->
                if (gapBefore[i]) y += gap + gapGrow
                if (i == foot) y += spare
                p.place(0, y)
                y += p.height
            }
        }
    }
}
