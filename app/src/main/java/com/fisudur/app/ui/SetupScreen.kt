package com.fisudur.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fisudur.app.Actions
import com.fisudur.app.engine.PlanEngine
import java.time.LocalTime

private data class PlanChoice(val amount: Int, val title: String, val reps: Int)

private val choices = listOf(
    PlanChoice(0, "حفظ نصف وجه", 40),
    PlanChoice(1, "حفظ وجه", 40),
    PlanChoice(2, "حفظ وجهين", 40),
    PlanChoice(3, "حفظ ثلاثة أوجه", 30),
)

/** Calendar days to the end of the mushaf from [startPage], with Friday off as the default plan has it. */
private fun daysToFinish(amount: Int, startPage: Int): Int {
    val today = java.time.LocalDate.now()
    val finish = PlanEngine(startPage).projectedFinish(startPage - 1, amount, today, setOf(java.time.DayOfWeek.FRIDAY))
        ?: return 0
    return java.time.temporal.ChronoUnit.DAYS.between(today, finish).toInt() + 1
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(a: Actions) {
    val pal = LocalPalette.current
    var amount by remember { mutableIntStateOf(2) }
    var page by remember { mutableStateOf("1") }
    var dayEnd by remember { mutableIntStateOf(4 * 60) }
    var known by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        // Welcome hero.
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)).background(pal.hero),
        ) {
            KhatamPattern(Modifier.matchParentSize(), color = pal.gold.copy(alpha = .10f))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("في صدور", style = MaterialTheme.typography.displayLarge, color = pal.onHero)
                Ornament(Modifier.padding(horizontal = 60.dp, vertical = 6.dp))
                Text(
                    "﴿بَلْ هُوَ آيَاتٌ بَيِّنَاتٌ فِي صُدُورِ الَّذِينَ أُوتُوا الْعِلْمَ﴾",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = Amiri),
                    color = pal.gold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("اختر خطتك")
            val from = page.toIntOrNull()?.coerceIn(1, 604) ?: 1
            choices.forEach { c -> PlanCard(c, daysToFinish(c.amount, from), c.amount == amount) { amount = c.amount } }
            Text("يمكنك تغيير المقدار لاحقًا في أي يوم.", style = MaterialTheme.typography.bodySmall, color = pal.muted)

            SectionTitle("متى ينتهي يومك؟")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 2 * 60, 3 * 60, 4 * 60, 5 * 60).forEach { m ->
                    Pill(Fmt.time(LocalTime.of(m / 60, m % 60)).ar(), dayEnd == m) { dayEnd = m }
                }
            }

            SectionTitle("من أين تبدأ؟")
            OutlinedTextField(
                value = page,
                onValueChange = { page = it.filter(Char::isDigit).take(3) },
                label = { Text("رقم الوجه") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            val startAt = page.toIntOrNull()?.coerceIn(1, 604) ?: 1
            if (startAt > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("أحفظ ما قبله", style = MaterialTheme.typography.bodyLarge, color = pal.ink)
                        Text(
                            "الأوجه من ١ إلى ${(startAt - 1).ar()} محفوظة عندي، فتدخل الربط والمراجعة من أول يوم.",
                            style = MaterialTheme.typography.bodySmall,
                            color = pal.muted,
                        )
                    }
                    androidx.compose.material3.Switch(checked = known, onCheckedChange = { known = it })
                }
            }

            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(pal.gold, Color(0xFFB0832F))))
                    .clickable { a.startPlan(amount, page.toIntOrNull()?.coerceIn(1, 604) ?: 1, dayEnd, known) },
                contentAlignment = Alignment.Center,
            ) {
                Text("ابدأ على بركة الله", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PlanCard(c: PlanChoice, days: Int, selected: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val border by animateColorAsState(if (selected) pal.gold else pal.line, label = "plan")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(pal.card)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) MaterialTheme.colorScheme.primary else pal.goldSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (c.amount == 0) "½" else c.amount.ar(), style = MaterialTheme.typography.headlineMedium, color = if (selected) pal.gold else pal.ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium, color = pal.ink)
            Text(
                "تكرار ${c.reps.ar()} مرة، نحو ${daysText(days.toLong())} مع إجازة الجمعة",
                style = MaterialTheme.typography.bodySmall,
                color = pal.muted,
            )
        }
        if (selected) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(pal.gold), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
