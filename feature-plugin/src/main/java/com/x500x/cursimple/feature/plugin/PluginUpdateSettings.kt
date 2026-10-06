package com.x500x.cursimple.feature.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PluginUpdateAutoCheck(viewModel: PluginMarketViewModel) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                viewModel.refreshInstalledPluginVersions(automatic = true)
                delay(60_000L)
            }
        }
    }
}

@Composable
internal fun PluginUpdateDot() {
    val description = stringResource(R.string.plugin_update_available_badge)
    Box(Modifier.testTag("plugin-update-dot").size(6.dp).background(MaterialTheme.colorScheme.error, CircleShape)
        .semantics { contentDescription = description })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PluginUpdateSettingsSheet(
    state: PluginMarketUiState,
    onOptions: (Boolean, Boolean, Int) -> Unit,
    onCheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.plugin_update_settings_title), style = MaterialTheme.typography.titleLarge)
            SettingsSwitch(stringResource(R.string.plugin_auto_check_title), stringResource(R.string.plugin_auto_check_description),
                state.autoCheckUpdates, "plugin-auto-check-switch") { onOptions(it, state.showUpdateBadge, state.updateIntervalHours) }
            SettingsSwitch(stringResource(R.string.plugin_update_badge_title), stringResource(R.string.plugin_update_badge_description),
                state.showUpdateBadge, "plugin-update-badge-switch") { onOptions(state.autoCheckUpdates, it, state.updateIntervalHours) }
            Text(stringResource(R.string.plugin_update_interval_title), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 6, 12, 24).forEach { hours ->
                    FilterChip(selected = state.updateIntervalHours == hours,
                        onClick = { onOptions(state.autoCheckUpdates, state.showUpdateBadge, hours) },
                        label = { Text(stringResource(R.string.plugin_update_interval_hours, hours)) })
                }
            }
            HorizontalDivider()
            val status = when {
                state.checkingInstalledUpdates -> stringResource(R.string.plugin_update_check_progress, state.updateCheckCompleted, state.updateCheckTotal)
                state.updateCheckUnconfirmed > 0 -> stringResource(R.string.plugin_update_check_unconfirmed, state.availableUpdateKeys().size, state.updateCheckUnconfirmed)
                state.lastUpdateCheckAtMillis > 0 -> stringResource(R.string.plugin_update_check_result, state.availableUpdateKeys().size)
                else -> stringResource(R.string.plugin_update_check_not_yet)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (state.lastUpdateCheckAtMillis > 0) Text(
                stringResource(R.string.plugin_update_check_last, Instant.ofEpochMilli(state.lastUpdateCheckAtMillis)
                    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCheck, enabled = !state.checkingInstalledUpdates,
                modifier = Modifier.fillMaxWidth().testTag("plugin-check-updates")) {
                Text(stringResource(if (state.checkingInstalledUpdates) R.string.plugin_update_check_busy else R.string.plugin_update_check_now))
            }
            Text(stringResource(R.string.plugin_update_check_local_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, description: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, modifier = Modifier.testTag(tag))
    }
}
