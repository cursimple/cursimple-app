package com.x500x.cursimple.feature.plugin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.x500x.cursimple.feature.plugin.ui.AppModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.plugin_update_settings_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                fontWeight = FontWeight.Bold,
            )
            Column(Modifier.fillMaxWidth().padding(top = 0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsSwitch(stringResource(R.string.plugin_auto_check_title), stringResource(R.string.plugin_auto_check_description),
                    state.autoCheckUpdates, "plugin-auto-check-switch") { onOptions(it, state.showUpdateBadge, state.updateIntervalHours) }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                SettingsSwitch(stringResource(R.string.plugin_update_badge_title), stringResource(R.string.plugin_update_badge_description),
                    state.showUpdateBadge, "plugin-update-badge-switch") { onOptions(state.autoCheckUpdates, it, state.updateIntervalHours) }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                Text(
                    stringResource(R.string.plugin_update_interval_title),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 6, 12, 24).forEach { hours ->
                        IntervalPill(selected = state.updateIntervalHours == hours,
                            onClick = { onOptions(state.autoCheckUpdates, state.showUpdateBadge, hours) },
                            label = stringResource(R.string.plugin_update_interval_hours, hours))
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
            val status = when {
                state.checkingInstalledUpdates -> stringResource(R.string.plugin_update_check_progress, state.updateCheckCompleted, state.updateCheckTotal)
                state.updateCheckUnconfirmed > 0 -> stringResource(R.string.plugin_update_check_unconfirmed, state.availableUpdateKeys().size, state.updateCheckUnconfirmed)
                state.lastUpdateCheckAtMillis > 0 -> stringResource(R.string.plugin_update_check_result, state.availableUpdateKeys().size)
                else -> stringResource(R.string.plugin_update_check_not_yet)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp))
            if (state.lastUpdateCheckAtMillis > 0) Text(
                stringResource(R.string.plugin_update_check_last, Instant.ofEpochMilli(state.lastUpdateCheckAtMillis)
                    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onCheck,
                enabled = !state.checkingInstalledUpdates,
                modifier = Modifier.fillMaxWidth().height(42.dp).testTag("plugin-check-updates"),
                shape = RoundedCornerShape(21.dp),
            ) {
                Text(
                    stringResource(if (state.checkingInstalledUpdates) R.string.plugin_update_check_busy else R.string.plugin_update_check_now),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(stringResource(R.string.plugin_update_check_local_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The 40×4 grabber the design draws above every sheet. */
@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 40.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun SettingsSwitch(title: String, description: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
                fontWeight = FontWeight.Bold)
            Text(description, modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 17.25.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, modifier = Modifier.testTag(tag))
    }
}

/** A selectable pill, the choice control the design uses instead of M3 chips. */
@Composable
private fun IntervalPill(selected: Boolean, onClick: () -> Unit, label: String) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = primary,
        border = if (selected) null else BorderStroke(1.5.dp, primary.copy(alpha = 0.35f)),
    ) {
        Box(Modifier.height(32.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                fontWeight = FontWeight.Bold)
        }
    }
}
