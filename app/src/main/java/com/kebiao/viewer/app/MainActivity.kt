package com.kebiao.viewer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kebiao.viewer.feature.plugin.PluginMarketRoute
import com.kebiao.viewer.feature.plugin.PluginMarketViewModel
import com.kebiao.viewer.feature.plugin.PluginMarketViewModelFactory
import com.kebiao.viewer.feature.schedule.ScheduleRoute
import com.kebiao.viewer.feature.schedule.ScheduleViewModel
import com.kebiao.viewer.feature.schedule.ScheduleViewModelFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ClassScheduleApplication).appContainer
        setContent {
            MaterialTheme(
                colorScheme = viewerDarkColorScheme(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Schedule) }
                    val scheduleViewModel: ScheduleViewModel = viewModel(
                        factory = ScheduleViewModelFactory(
                            scheduleRepository = container.scheduleRepository,
                            pluginManager = container.pluginManager,
                            reminderCoordinator = container.reminderCoordinator,
                            onSyncCompleted = { container.refreshWidgets() },
                        ),
                    )
                    val pluginMarketViewModel: PluginMarketViewModel = viewModel(
                        factory = PluginMarketViewModelFactory(container.pluginManager),
                    )
                    val scheduleState by scheduleViewModel.uiState.collectAsStateWithLifecycle()

                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            AppBottomBar(
                                currentScreen = currentScreen,
                                onSelect = { currentScreen = it },
                            )
                        },
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            when (currentScreen) {
                                AppScreen.Schedule -> ScheduleRoute(
                                    viewModel = scheduleViewModel,
                                    onOpenPluginMarket = { currentScreen = AppScreen.Plugins },
                                    modifier = Modifier.fillMaxSize(),
                                )

                                AppScreen.Plugins -> PluginMarketRoute(
                                    viewModel = pluginMarketViewModel,
                                    activePluginId = scheduleState.pluginId,
                                    onSelectInstalledPlugin = scheduleViewModel::onPluginIdChange,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                AppScreen.Settings -> SettingsRoute(
                                    viewModel = scheduleViewModel,
                                    onOpenPluginMarket = { currentScreen = AppScreen.Plugins },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private enum class AppScreen(
        val label: String,
        val icon: ImageVector,
    ) {
        Schedule("课表", Icons.Rounded.MenuBook),
        Plugins("插件", Icons.Rounded.Extension),
        Settings("设置", Icons.Rounded.Settings),
    }

    @Composable
    private fun AppBottomBar(
        currentScreen: AppScreen,
        onSelect: (AppScreen) -> Unit,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AppScreen.entries.forEach { screen ->
                        AppBottomBarItem(
                            screen = screen,
                            selected = currentScreen == screen,
                            onClick = { onSelect(screen) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AppBottomBarItem(
        screen: AppScreen,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        val indicatorColor by animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            label = "bottom-indicator",
        )
        val contentColor by animateColorAsState(
            if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "bottom-content",
        )

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(indicatorColor)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = screen.icon,
                    contentDescription = screen.label,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = screen.label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

private fun viewerDarkColorScheme() = darkColorScheme(
    primary = Color(0xFFB9C5FF),
    onPrimary = Color(0xFF0A1336),
    primaryContainer = Color(0xFF4A4A74),
    onPrimaryContainer = Color(0xFFF2F4FF),
    secondary = Color(0xFF94A5FF),
    onSecondary = Color(0xFF09112D),
    background = Color(0xFF090D18),
    onBackground = Color(0xFFF4F6FF),
    surface = Color(0xFF171B2A),
    onSurface = Color(0xFFF3F5FF),
    surfaceVariant = Color(0xFF1E2336),
    onSurfaceVariant = Color(0xFFB4BCD7),
    tertiary = Color(0xFF7BA1FF),
    onTertiary = Color(0xFF05142F),
    tertiaryContainer = Color(0xFF19294D),
    error = Color(0xFFFF8E8E),
    onError = Color(0xFF3A0303),
)
