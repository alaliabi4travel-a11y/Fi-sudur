package com.fisudur.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fisudur.app.AppViewModel
import com.fisudur.app.BuildConfig
import com.fisudur.app.UiState
import com.fisudur.app.UpdateChecker
import com.fisudur.app.engine.PlanRules
import kotlinx.coroutines.launch
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(s: UiState, vm: AppViewModel) {
    val st = s.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("الإعدادات", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Section("مقدار الحفظ اليومي", "يمكنك تغييره في أي وقت، ويُعاد حساب الربط والمراجعة تلقائيًا.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { n ->
                    FilterChip(
                        selected = st.amount == n,
                        onClick = { vm.saveSettings(st.copy(amount = n)) },
                        label = { Text(if (n == 1) "وجه" else if (n == 2) "وجهان" else "$n أوجه") },
                    )
                }
            }
        }

        Section("عدد التكرار", "الافتراضي ${PlanRules.defaultRepetitions(st.amount)} مرة لهذا المقدار.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تلقائي", modifier = Modifier.weight(1f))
                Switch(
                    checked = st.repetitions == null,
                    onCheckedChange = { auto ->
                        vm.saveSettings(st.copy(repetitions = if (auto) null else PlanRules.defaultRepetitions(st.amount)))
                    },
                )
            }
            if (st.repetitions != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 40, 50).forEach { n ->
                        FilterChip(selected = st.repetitions == n, onClick = { vm.saveSettings(st.copy(repetitions = n)) }, label = { Text("$n") })
                    }
                }
            }
        }

        Section("نهاية اليوم", "يبدأ اليوم الجديد في الوقت نفسه. ما تنجزه قبل هذا الوقت يُحسب لليوم السابق.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 2 * 60, 3 * 60, 4 * 60, 5 * 60).forEach { m ->
                    FilterChip(
                        selected = st.dayEndMinutes == m,
                        onClick = { vm.saveSettings(st.copy(dayEndMinutes = m)) },
                        label = { Text(Fmt.time(LocalTime.of(m / 60, m % 60))) },
                    )
                }
                FilterChip(
                    selected = st.dayEndMinutes !in listOf(0, 120, 180, 240, 300),
                    onClick = {
                        TimePickerDialog(context, { _, h, min ->
                            vm.saveSettings(st.copy(dayEndMinutes = h * 60 + min))
                        }, st.dayEndMinutes / 60, st.dayEndMinutes % 60, false).show()
                    },
                    label = { Text("وقت آخر…") },
                )
            }
            Text("الحالي: ${Fmt.time(st.dayEnd)}", color = MaterialTheme.colorScheme.secondary)
        }

        Section("أيام الإجازة الأسبوعية", "ربط ومراجعة فقط، بلا حفظ جديد.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fmt.weekOrder.forEach { d ->
                    FilterChip(
                        selected = d in st.offWeekdays,
                        onClick = {
                            vm.saveSettings(st.copy(offWeekdays = if (d in st.offWeekdays) st.offWeekdays - d else st.offWeekdays + d))
                        },
                        label = { Text(Fmt.day(d)) },
                    )
                }
            }
        }

        Section("بداية الخطة", "تاريخ البداية: ${st.startDate?.let { Fmt.shortDate(it) } ?: "-"} · من الوجه ${st.startPage}") {
            var page by remember(st.startPage) { mutableStateOf(st.startPage.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = page,
                    onValueChange = { page = it.filter(Char::isDigit).take(3) },
                    label = { Text("أبدأ الحفظ من الوجه") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.padding(4.dp))
                TextButton(onClick = {
                    page.toIntOrNull()?.takeIf { it in 1..604 }?.let { vm.saveSettings(st.copy(startPage = it)) }
                }) { Text("حفظ") }
            }
            Text(
                "غيّره فقط إن كنت تحفظ من موضع آخر غير أول المصحف.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("التحديثات", "الإصدار ${BuildConfig.VERSION_NAME}") {
            OutlinedButton(onClick = {
                updateMsg = "جارٍ التحقق…"; updateUrl = null
                scope.launch {
                    UpdateChecker.latest().onSuccess { info ->
                        when {
                            info == null -> updateMsg = "لا توجد إصدارات منشورة بعد."
                            info.versionCode > BuildConfig.VERSION_CODE -> {
                                updateMsg = "يوجد إصدار جديد: ${info.versionName}"
                                updateUrl = info.apkUrl.ifEmpty { info.pageUrl }
                            }
                            else -> updateMsg = "لديك أحدث إصدار."
                        }
                    }.onFailure { updateMsg = "تعذر التحقق. تأكد من الاتصال بالإنترنت." }
                }
            }) { Text("التحقق من وجود تحديث") }
            updateMsg?.let { Text(it) }
            updateUrl?.let { url ->
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Text("تنزيل التحديث")
                }
            }
        }

        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
            Text("بدء خطة جديدة (حذف السجل)", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("حذف السجل؟") },
            text = { Text("سيُحذف كل ما سجلته من أيام وتبدأ خطة جديدة. التسجيلات الصوتية لا تُحذف.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetAll() }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun Section(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            subtitle?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}
