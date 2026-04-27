package com.kebiao.viewer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            MaterialTheme {
                Surface {
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextButton(onClick = { currentScreen = AppScreen.Schedule }) {
                                Text("课表")
                            }
                            TextButton(onClick = { currentScreen = AppScreen.Plugins }) {
                                Text("插件")
                            }
                        }
                        when (currentScreen) {
                            AppScreen.Schedule -> ScheduleRoute(
                                viewModel = scheduleViewModel,
                                onOpenPluginMarket = { currentScreen = AppScreen.Plugins },
                                modifier = Modifier.fillMaxSize(),
                            )

                            AppScreen.Plugins -> PluginMarketRoute(
                                viewModel = pluginMarketViewModel,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    private enum class AppScreen {
        Schedule,
        Plugins,
    }
}
