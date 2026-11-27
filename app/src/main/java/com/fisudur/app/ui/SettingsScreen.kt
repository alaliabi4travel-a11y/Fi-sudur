package com.fisudur.app.ui

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
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
    var pendingImport by remember { mutableStateOf<Pair<android.net.Uri, String>?>(null) }
    var choosingBackup by remember { mutableStateOf(false) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val about = a.preview(uri)
            if (about != null) pendingImport = uri to about else a.importFrom(uri) // not ours: the import says so
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(a::chooseBackupFolder)
    }
    var crash by remember { mutableStateOf(com.fisudur.app.CrashLog.read(context)) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("الإعدادات", "أي تغيير هنا يُعيد حساب الخطة فورًا")
        PlanGuide()

        Section(Icons.Outlined.MenuBook, "مقدار الحفظ اليومي", "غيّره متى شئت، ويُعاد حساب الربط والمراجعة تلقائيًا.") {
            Segmented(
                // Index 0 is half a page (amount 0), then one, two and three pages.
                options = listOf("نصف وجه", "وجه", "وجهان", "ثلاثة"),
                selected = st.amount.coerceIn(0, 3),
                onSelect = { a.saveSettings(st.copy(amount = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section(Icons.Outlined.Repeat, "عدد مرات التكرار", "الافتراضي ${PlanRules.defaultRepetitions(st.amount).ar()} مرة لهذا المقدار.") {
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
            "بدأت ${st.startDate?.let { Fmt.shortDate(it).ar() } ?: "-"}، من الوجه ${st.startPage.ar()}",
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
            if (st.startPage > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("أحفظ ما قبله", style = MaterialTheme.typography.bodyLarge, color = pal.ink)
                        Text(
                            "الأوجه من ١ إلى ${(st.startPage - 1).ar()} محفوظة عندي، فتدخل الربط والمراجعة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = pal.muted,
                        )
                    }
                    Switch(checked = st.memorizedBefore, onCheckedChange = { a.saveSettings(st.copy(memorizedBefore = it)) })
                }
            }
        }

        Section(Icons.Outlined.Headphones, "القارئ", "تسمعه في خطوة الاستماع، والصوت من quran.com ويحتاج الإنترنت.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.fisudur.app.QuranApi.reciters.forEach { (id, name) ->
                    Pill(name, st.reciter == id) { a.saveSettings(st.copy(reciter = id)) }
                }
            }
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

        Section(Icons.Outlined.CalendarMonth, "أيام الإجازة الأسبوعية", "لا حفظ جديد فيها، ويستمر تكرار الأمس والربط والمراجعة.") {
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
            "يحفظ التطبيق نسخة كل يوم داخل الهاتف${s.lastAutoBackup?.let { "، آخرها ${it.ar()}" } ?: ""}. واحفظ ملفًا خارجه (Google Drive أو رسالة) لتنقل سجلك إلى هاتف آخر.",
        ) {
            if (st.backupFolder != null) {
                Text(
                    "نسخة يومية في المجلد الذي اخترته${s.lastFolderCopy?.let { "، آخرها ${it.ar()}" } ?: ""}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.ink,
                )
                OutlineAction("إيقاف النسخ إلى المجلد") { a.chooseBackupFolder(null) }
            } else {
                OutlineAction("نسخة يومية في مجلد تختاره") { pickFolder.launch(null) }
            }
            OutlineAction("حفظ نسخة في ملف") { exportFile.launch("fi-sudur-${java.time.LocalDate.now()}.json") }
            OutlineAction("استعادة من ملف") { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            if (s.lastAutoBackup != null) {
                OutlineAction("استعادة نسخة تلقائية") { choosingBackup = true }
            }
            Text(
                "اختر مجلدًا في Google Drive أو غيره ليبقى سجلك إن ضاع الهاتف.",
                style = MaterialTheme.typography.bodySmall,
                color = pal.muted,
            )
        }

        if (!BuildConfig.SELF_UPDATE) {
            Section(Icons.Outlined.SystemUpdate, "الإصدار", "${BuildConfig.VERSION_NAME.ar()}، التحديث من متجر Google Play") {}
        } else Section(Icons.Outlined.SystemUpdate, "التحديثات", "الإصدار ${BuildConfig.VERSION_NAME.ar()}") {
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

        crash?.let { report ->
            Section(Icons.Outlined.BugReport, "حدث عطل في التطبيق", "أرسل التفاصيل ليُصلَح، ولا تحوي شيئًا من سجلك.") {
                OutlineAction("إرسال تقرير العطل") {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, "عطل في في صدور ${BuildConfig.VERSION_NAME}")
                        .putExtra(android.content.Intent.EXTRA_TEXT, report)
                    context.startActivity(android.content.Intent.createChooser(send, "إرسال التقرير"))
                }
                OutlineAction("تجاهل") { com.fisudur.app.CrashLog.clear(context); crash = null }
            }
        }

        OutlineAction("بدء خطة جديدة (حذف السجل)", danger = true) { confirmReset = true }
        Spacer(Modifier.height(4.dp))
    }

    pendingImport?.let { (uri, about) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("استعادة هذه النسخة؟") },
            text = { Text("$about\n\nتحل محل سجلك الحالي، وتُحفظ نسخة منه أولًا.") },
            confirmButton = { TextButton(onClick = { a.importFrom(uri); pendingImport = null }) { Text("استعادة") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("إلغاء") } },
        )
    }

    if (choosingBackup) {
        val list = remember { a.backups() }
        AlertDialog(
            onDismissRequest = { choosingBackup = false },
            title = { Text("اختر نسخة") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text("تحل النسخة محل سجلك الحالي، وتُحفظ نسخة منه أولًا.", style = MaterialTheme.typography.bodySmall, color = pal.muted)
                    list.forEach { (name, about) ->
                        Text(
                            about,
                            style = MaterialTheme.typography.bodyLarge,
                            color = pal.ink,
                            modifier = Modifier.fillMaxWidth().clickable { a.restoreAutoBackup(name); choosingBackup = false }.padding(vertical = 12.dp),
                        )
                        HorizontalDivider(color = pal.line)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingBackup = false }) { Text("إلغاء") } },
        )
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
            IconBadge(icon, pal.goldSoft, pal.gold, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = pal.ink)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = pal.muted) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun OutlineAction(text: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val color = if (danger) pal.warn else MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .border(1.dp, if (danger) pal.warn.copy(alpha = .5f) else pal.line, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) color else pal.muted) }
}
