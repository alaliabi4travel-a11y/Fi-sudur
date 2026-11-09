package com.fisudur.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fisudur.app.Actions
import com.fisudur.app.UpdateState

/** One tap downloads the new version inside the app and opens the installer. */
@Composable
fun UpdateBanner(u: UpdateState, a: Actions) {
    val pal = LocalPalette.current
    val (title, action) = when (u) {
        is UpdateState.Available -> "إصدار جديد ${u.info.versionName.ar()}" to "تحديث الآن"
        is UpdateState.Downloading -> "جارٍ تنزيل ${u.info.versionName.ar()}" to "${(u.progress * 100).toInt().ar()}٪"
        is UpdateState.Ready -> "التحديث جاهز ${u.info.versionName.ar()}" to "تثبيت"
        else -> return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.goldSoft)
            .clickable(enabled = u !is UpdateState.Downloading) { a.getUpdate() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.SystemUpdate, bg = pal.gold, fg = Color.White, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = pal.ink)
                if (u is UpdateState.Available && u.info.notes.isNotBlank()) {
                    Text(u.info.notes, style = MaterialTheme.typography.bodySmall, color = pal.muted, maxLines = 2)
                }
            }
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (u is UpdateState.Downloading) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { u.progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = pal.gold,
                trackColor = pal.line,
            )
        }
    }
}
