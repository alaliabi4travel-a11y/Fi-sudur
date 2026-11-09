package com.fisudur.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fisudur.app.AppViewModel
import java.time.LocalTime

private data class PlanChoice(val amount: Int, val title: String, val desc: String)

private val choices = listOf(
    PlanChoice(1, "حفظ وجه", "وجه كل يوم، تكرار 40 مرة. نحو 620 يومًا."),
    PlanChoice(2, "حفظ وجهين", "وجهان كل يوم، تكرار 40 مرة. نحو 320 يومًا."),
    PlanChoice(3, "إتقان ثلاثة أوجه", "ثلاثة أوجه كل يوم، تكرار 30 مرة. نحو 218 يومًا."),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: AppViewModel) {
    var amount by remember { mutableIntStateOf(2) }
    var page by remember { mutableStateOf("1") }
    var dayEnd by remember { mutableIntStateOf(4 * 60) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("في صدور", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("«بَلْ هُوَ آيَاتٌ بَيِّنَاتٌ فِي صُدُورِ الَّذِينَ أُوتُوا الْعِلْمَ»", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(6.dp))
        Text("اختر خطتك", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        choices.forEach { c ->
            val selected = c.amount == amount
            Card(
                modifier = Modifier.fillMaxWidth().clickable { amount = c.amount },
                shape = RoundedCornerShape(18.dp),
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(c.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(c.desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        }
        Text("يمكنك تغيير المقدار لاحقًا في أي يوم.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("متى ينتهي يومك؟", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 2 * 60, 3 * 60, 4 * 60, 5 * 60).forEach { m ->
                FilterChip(
                    selected = dayEnd == m,
                    onClick = { dayEnd = m },
                    label = { Text(Fmt.time(LocalTime.of(m / 60, m % 60))) },
                )
            }
        }

        OutlinedTextField(
            value = page,
            onValueChange = { page = it.filter(Char::isDigit).take(3) },
            label = { Text("أبدأ من الوجه رقم") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { vm.startPlan(amount, page.toIntOrNull()?.coerceIn(1, 604) ?: 1, dayEnd) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("ابدأ على بركة الله", fontSize = 17.sp) }
    }
}
