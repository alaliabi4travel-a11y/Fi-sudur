package com.fisudur.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fisudur.app.ui.LocalPalette
import com.fisudur.app.ui.AppTheme
import com.fisudur.app.ui.CounterScreen
import com.fisudur.app.ui.HomeScreen
import com.fisudur.app.ui.SettingsScreen
import com.fisudur.app.ui.SetupScreen
import com.fisudur.app.ui.StatsScreen

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("الرئيسية", Icons.Outlined.Home),
    Tab("العداد", Icons.Outlined.TouchApp),
    Tab("إحصائياتي", Icons.Outlined.BarChart),
    Tab("الإعدادات", Icons.Outlined.Settings),
)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val s by vm.state.collectAsState()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                AppRoot(s, vm, tab) { tab = it }
            }
        }
    }
}

/** The whole app for a given state: screens plus the bottom bar. */
@Composable
fun AppRoot(s: UiState, a: Actions, tab: Int, onTab: (Int) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!s.needsSetup) {
                val pal = LocalPalette.current
                Column {
                    HorizontalDivider(color = pal.line)
                    NavigationBar(containerColor = pal.card, tonalElevation = 0.dp) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { onTab(i) },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = pal.gold,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = pal.goldSoft,
                                    unselectedIconColor = pal.muted,
                                    unselectedTextColor = pal.muted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.needsSetup) {
                SetupScreen(a)
            } else {
                when (tab) {
                    0 -> HomeScreen(s, a, openCounter = { onTab(1) })
                    1 -> CounterScreen(s, a)
                    2 -> StatsScreen(s)
                    else -> SettingsScreen(s, a)
                }
            }
        }
    }
}
