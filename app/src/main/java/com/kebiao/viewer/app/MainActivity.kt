package com.kebiao.viewer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    val viewModel: ScheduleViewModel = viewModel(
                        factory = ScheduleViewModelFactory(
                            scheduleRepository = container.scheduleRepository,
                            scheduleSyncService = container.scheduleSyncService,
                            onSyncCompleted = { container.refreshWidgets() },
                        ),
                    )
                    ScheduleRoute(viewModel = viewModel)
                }
            }
        }
    }
}

