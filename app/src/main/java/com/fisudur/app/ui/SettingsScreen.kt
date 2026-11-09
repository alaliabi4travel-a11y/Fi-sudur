package com.fisudur.app.ui

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fisudur.app.Actions
import com.fisudur.app.BuildConfig
import com.fisudur.app.UiState
import com.fisudur.app.UpdateState
import com.fisudur.app.engine.PlanRules
import java.time.LocalTime

private val presetEnds = listOf(0, 2 * 60, 3 * 60, 4 * 60, 5 * 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(s: UiState, a: Actions) {
    val st = s.settings
    val pal = LocalPalette.current
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<Int?>(null) }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(a::exportTo)
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(a::importFrom)
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("الإعدادات", "كل تغيير يُعاد به حساب الخطة فورًا")

        Section(Icons.Outlined.MenuBook, "مقدار الحفظ اليومي", "غيّره متى شئت، ويُعاد حساب الربط والمراجعة تلقائيًا.") {
            Segmented(
                options = listOf("وجه", "وجهان", "ثلاثة أوجه"),
                selected = (st.amount - 1).coerceIn(0, 2),
                onSelect = { a.saveSettings(st.copy(amount = it + 1)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section(Icons.Outlined.Repeat, "عدد التكرار", "الافتراضي ${PlanRules.defaultRepetitions(st.amount).ar()} مرة لهذا المقدار.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تلقائي", style = MaterialTheme.typography.bodyLarge, color = pal.ink, modifier = Modifier.weight(1f))
                Switch(
                    checked = st.repetitions == null,
                    onCheckedChange = { auto ->
                        a.saveSettings(st.copy(repetitions = if (auto) null else PlanRules.defaultRepetitions(st.amount)))
                    },
                )
            }
            if (st.repetitions != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 40, 50).forEach { n ->
                        Pill(n.ar(), st.repetitions == n) { a.saveSettings(st.copy(repetitions = n)) }
                    }
                }
            }
        }

        Section(
            Icons.Outlined.Flag,
            "بداية الخطة",
            "بدأت ${st.startDate?.let { Fmt.shortDate(it).ar() } ?: "-"} · من الوجه ${st.startPage.ar()}",
        ) {
            var page by remember(st.startPage) { mutableStateOf(st.startPage.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = page,
                    onValueChange = { page = it.filter(Char::isDigit).take(3) },
                    label = { Text("أبدأ الحفظ من الوجه") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    page.toIntOrNull()?.takeIf { it in 1..604 && it != st.startPage }?.let { pendingStart = it }
                }) { Text("حفظ") }
            }
            Text("غيّره فقط إن كنت تحفظ من موضع غير أول المصحف.", style = MaterialTheme.typography.bodySmall, color = pal.muted)
        }

        Section(Icons.Outlined.Bedtime, "نهاية اليوم", "يبدأ يومك الجديد عند هذا الوقت. ما تنجزه قبله يُحسب لليوم السابق.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presetEnds.forEach { m ->
                    Pill(Fmt.time(LocalTime.of(m / 60, m % 60)).ar(), st.dayEndMinutes == m) {
                        a.saveSettings(st.copy(dayEndMinutes = m))
                    }
                }
                val custom = st.dayEndMinutes !in presetEnds
                Pill(if (custom) Fmt.time(st.dayEnd).ar() else "وقت آخر…", custom) {
                    TimePickerDialog(context, { _, h, min ->
                        a.saveSettings(st.copy(dayEndMinutes = h * 60 + min))
                    }, st.dayEndMinutes / 60, st.dayEndMinutes % 60, false).show()
                }
            }
        }

        Section(Icons.Outlined.CalendarMonth, "أيام الإجازة الأسبوعية", "ربط ومراجعة فقط، بلا حفظ جديد.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Fmt.weekOrder.forEach { d ->
                    Pill(Fmt.day(d), d in st.offWeekdays) {
                        a.saveSettings(st.copy(offWeekdays = if (d in st.offWeekdays) st.offWeekdays - d else st.offWeekdays + d))
                    }
                }
            }
        }

        Section(Icons.Outlined.NotificationsActive, "التذكير", "لا يظهر إذا أنهيت يومك.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تذكير يومي", style = MaterialTheme.typography.bodyLarge, color = pal.ink, modifier = Modifier.weight(1f))
                Switch(
                    checked = st.reminderMinutes != null,
                    onCheckedChange = { on -> a.saveSettings(st.copy(reminderMinutes = if (on) 20 * 60 else null)) },
                )
            }
            st.reminderMinutes?.let { m ->
                Pill("الساعة ${Fmt.time(LocalTime.of(m / 60, m % 60)).ar()}", true) {
                    TimePickerDialog(context, { _, h, min ->
                        a.saveSettings(st.copy(reminderMinutes = h * 60 + min))
                    }, m / 60, m % 60, false).show()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("قبل نهاية اليوم بساعة", style = MaterialTheme.typography.bodyLarge, color = pal.ink)
                    Text("إن لم تُنهِ وردك بعد", style = MaterialTheme.typography.bodySmall, color = pal.muted)
                }
                Switch(checked = st.lateReminder, onCheckedChange = { a.saveSettings(st.copy(lateReminder = it)) })
            }
        }

        Section(
            Icons.Outlined.Backup,
            "النسخ الاحتياطي",
            "يحفظ التطبيق نسخة كل يوم داخل الهاتف${s.lastAutoBackup?.let { "، آخرها ${it.ar()}" } ?: ""}. واحفظ ملفًا خارجه (Drive أو رسالة) لتنقل سجلك إلى هاتف آخر.",
        ) {
            OutlineAction("حفظ نسخة في ملف") { exportFile.launch("fi-sudur-${java.time.LocalDate.now()}.json") }
            OutlineAction("استعادة من ملف") { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            if (s.lastAutoBackup != null) {
                OutlineAction("استعادة آخر نسخة تلقائية") { a.restoreAutoBackup() }
            }
        }

        Section(Icons.Outlined.SystemUpdate, "التحديثات", "الإصدار ${BuildConfig.VERSION_NAME.ar()}") {
            when (val u = s.update) {
                is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready -> UpdateBanner(u, a)
                else -> {
                    val msg = when (u) {
                        UpdateState.Checking -> "جارٍ التحقق…"
                        UpdateState.UpToDate -> "لديك أحدث إصدار."
                        is UpdateState.Failed -> u.message
                        else -> null
                    }
                    msg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = pal.muted) }
                    OutlineAction("التحقق من وجود تحديث", enabled = u != UpdateState.Checking) { a.checkUpdate() }
                }
            }
        }

        OutlineAction("بدء خطة جديدة (حذف السجل)", danger = true) { confirmReset = true }
        Spacer(Modifier.height(12.dp))
    }

    pendingStart?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text("تغيير وجه البداية؟") },
            text = { Text("ستتغير أرقام الأوجه في كل الأيام القادمة، ويُعاد حساب الربط والمراجعة من الوجه ${p.ar()}. سجلّك الماضي يبقى كما هو.") },
            confirmButton = { TextButton(onClick = { a.saveSettings(st.copy(startPage = p)); pendingStart = null }) { Text("غيّر") } },
            dismissButton = { TextButton(onClick = { pendingStart = null }) { Text("إلغاء") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("حذف السجل؟") },
            text = { Text("سيُحذف كل ما سجلته من أيام وتبدأ خطة جديدة. تُحفظ نسخة تلقائية قبل الحذف، فيمكنك استعادتها من «النسخ الاحتياطي».") },
            confirmButton = { TextButton(onClick = { confirmReset = false; a.resetAll() }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, subtitle: String?, content: @Composable () -> Unit) {
    val pal = LocalPalette.current
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, pal.goldSoft, pal.gold, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = pal.ink)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = pal.muted) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun OutlineAction(text: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val color = if (danger) pal.warn else MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .border(1.dp, if (danger) pal.warn.copy(alpha = .5f) else pal.line, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) color else pal.muted) }
}
