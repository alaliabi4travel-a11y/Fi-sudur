package com.fisudur.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.fisudur.app.Actions
import com.fisudur.app.UiState

@Composable
fun CounterScreen(s: UiState, a: Actions) {
    val pal = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val target = s.plan.repetitions
    val count = s.progress.counter
    val pages = s.plan.newPages
    val done = target > 0 && count >= target
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val count1 = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (count + 1 == target) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        a.setCounter(count + 1)
    }

    // The screen stays on while counting, and either volume key counts one reading.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        Modifier.fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                val volume = e.key == Key.VolumeUp || e.key == Key.VolumeDown
                if (volume && e.type == KeyEventType.KeyDown) count1()
                volume
            }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("عدّاد التكرار", style = MaterialTheme.typography.headlineLarge, color = pal.ink, modifier = Modifier.padding(top = 6.dp))
        Text(
            if (pages != null) "الوجه ${pages.label.ar()} · سورة ${pages.surahs}" else "عدّاد حر",
            style = MaterialTheme.typography.bodyMedium,
            color = pal.muted,
        )
        Spacer(Modifier.weight(1f))
        Ring(
            progress = if (target > 0) count.toFloat() / target else 0f,
            modifier = Modifier.size(300.dp).scale(scale),
            color = pal.gold,
            track = pal.line,
            width = 12.dp,
        ) {
            Box(
                Modifier.size(244.dp).clip(CircleShape).background(pal.hero)
                    .clickable(interactionSource = press, indication = null) { count1() },
                contentAlignment = Alignment.Center,
            ) {
                KhatamPattern(Modifier.fillMaxSize(), color = pal.gold.copy(alpha = .08f), cell = 38.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(count.ar(), style = MaterialTheme.typography.displayLarge, color = pal.onHero)
                    Text(
                        if (target > 0) "من ${target.ar()}" else "اضغط للعد",
                        style = MaterialTheme.typography.titleMedium,
                        color = pal.gold,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (done) "أتممت التكرار المطلوب، بارك الله فيك" else "اضغط الدائرة أو زر الصوت بعد كل قراءة غيبًا",
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) pal.done else pal.muted,
        )
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Filled.Remove, "نقص") { a.setCounter(count - 1) }
            Row(
                Modifier.clip(RoundedCornerShape(50)).border(1.dp, pal.line, RoundedCornerShape(50))
                    .clickable { a.setCounter(0) }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.RestartAlt, null, tint = pal.muted)
                Text("  من جديد", style = MaterialTheme.typography.labelLarge, color = pal.muted)
            }
            RoundButton(Icons.Filled.Add, "زيادة") { a.setCounter(count + 1) }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(pal.card).border(1.dp, pal.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = MaterialTheme.colorScheme.primary) }
}
