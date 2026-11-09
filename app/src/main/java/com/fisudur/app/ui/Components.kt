package com.fisudur.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fisudur.app.StepKind
import kotlin.math.cos
import kotlin.math.sin

/** Arabic-Indic digits for display: 604 → ٦٠٤. */
fun String.ar(): String = buildString(length) {
    for (c in this@ar) append(if (c in '0'..'9') '٠' + (c - '0') else c)
}

fun Int.ar(): String = toString().ar()

fun stepIcon(kind: StepKind): ImageVector = when (kind) {
    StepKind.YESTERDAY -> Icons.Outlined.History
    StepKind.LISTEN -> Icons.Outlined.Headphones
    StepKind.TAFSIR -> Icons.Outlined.MenuBook
    StepKind.REPEAT -> Icons.Outlined.Repeat
    StepKind.RECORD -> Icons.Outlined.Mic
    StepKind.LINK -> Icons.Outlined.Link
    StepKind.REVIEW -> Icons.Outlined.AutoStories
}

/** An eight-pointed star (two overlapping squares), the classic khatam motif. */
private fun DrawScope.khatam(center: Offset, r: Float, color: Color, stroke: Float) {
    for (angle in listOf(0f, 45f)) {
        rotate(angle, center) {
            val p = Path().apply {
                moveTo(center.x - r, center.y - r); lineTo(center.x + r, center.y - r)
                lineTo(center.x + r, center.y + r); lineTo(center.x - r, center.y + r); close()
            }
            drawPath(p, color, style = Stroke(stroke))
        }
    }
}

/** A soft lattice of khatam stars, drawn behind hero cards. */
@Composable
fun KhatamPattern(modifier: Modifier = Modifier, color: Color, cell: Dp = 46.dp) {
    Canvas(modifier) {
        val step = cell.toPx()
        val r = step * 0.27f
        var y = 0f
        var row = 0
        while (y < size.height + step) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < size.width + step) {
                khatam(Offset(x, y), r, color, 1.dp.toPx())
                x += step
            }
            y += step * 0.85f
            row++
        }
    }
}

/** A gold rule with a small star in the middle. */
@Composable
fun Ornament(modifier: Modifier = Modifier, color: Color = LocalPalette.current.gold) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val cy = size.height / 2
        val cx = size.width / 2
        val gap = 14.dp.toPx()
        drawLine(color.copy(alpha = .55f), Offset(0f, cy), Offset(cx - gap, cy), 1.dp.toPx())
        drawLine(color.copy(alpha = .55f), Offset(cx + gap, cy), Offset(size.width, cy), 1.dp.toPx())
        khatam(Offset(cx, cy), 4.dp.toPx(), color, 1.2.dp.toPx())
    }
}

/** A circular progress ring with rounded ends. */
@Composable
fun Ring(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color,
    track: Color,
    width: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = width.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (p > 0.001f) {
                val a = Math.toRadians((-90 + 360 * p).toDouble())
                val rr = (size.width - w) / 2
                drawCircle(color, w * 0.9f, Offset(size.width / 2 + rr * cos(a).toFloat(), size.height / 2 + rr * sin(a).toFloat()))
            }
        }
        content()
    }
}

/** The app's card: warm surface, hairline border, generous radius. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(pal.card)
            .border(1.dp, pal.line, RoundedCornerShape(24.dp))
            .padding(padding),
        content = content,
    )
}

/** A pill-shaped segmented control. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else pal.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(LocalPalette.current.gold))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, color = LocalPalette.current.ink, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Chip(text: String, color: Color, onColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 12.dp, vertical = 4.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = onColor) }
}

/** Small round icon badge. */
@Composable
fun IconBadge(icon: ImageVector, bg: Color, fg: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = LocalPalette.current.ink) {
    Panel(modifier, padding = 16.dp) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.muted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor)
    }
}

/** A selectable pill used for option groups in settings and setup. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else pal.card, label = "pill")
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else pal.line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else pal.ink,
        )
    }
}

/** Big screen title with an optional muted subtitle. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = pal.ink)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = pal.muted) }
    }
}
