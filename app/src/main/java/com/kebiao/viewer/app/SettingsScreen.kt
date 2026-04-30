package com.kebiao.viewer.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kebiao.viewer.feature.schedule.ScheduleSettingsRoute
import com.kebiao.viewer.feature.schedule.ScheduleViewModel

@Composable
fun SettingsRoute(
    viewModel: ScheduleViewModel,
    onOpenPluginMarket: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScheduleSettingsRoute(
        viewModel = viewModel,
        onOpenPluginMarket = onOpenPluginMarket,
        modifier = modifier,
    )
}
