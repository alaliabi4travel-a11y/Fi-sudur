package com.fisudur.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fisudur.app.AppViewModel
import com.fisudur.app.UiState

@Composable
fun CounterScreen(s: UiState, vm: AppViewModel) {
    val haptic = LocalHapticFeedback.current
    val target = s.plan.repetitions
    val count = s.progress.counter
    val pages = s.plan.newPages
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("عدّاد التكرار", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (pages != null) {
            Text("الوجه ${pages.label} · ${pages.surahs}", color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(28.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (target > 0) (count.toFloat() / target).coerceAtMost(1f) else 0f },
                modifier = Modifier.size(270.dp),
                strokeWidth = 10.dp,
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Box(
                Modifier.size(232.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .2f), CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (count + 1 == target) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.setCounter(count + 1)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("من $target", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (count >= target) "أتممت التكرار المطلوب" else "اضغط الدائرة بعد كل قراءة",
            color = if (count >= target) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { vm.setCounter(count - 1) }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.Remove, "نقص")
            }
            Spacer(Modifier.width(40.dp))
            FilledTonalIconButton(onClick = { vm.setCounter(count + 1) }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.Add, "زيادة")
            }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = { vm.setCounter(0) }) {
            Icon(Icons.Filled.Refresh, null)
            Spacer(Modifier.width(6.dp))
            Text("إعادة التعيين")
        }
    }
}

