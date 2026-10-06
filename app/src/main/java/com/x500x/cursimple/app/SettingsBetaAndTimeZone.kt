package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalConfiguration
import java.text.Collator
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import java.time.ZoneId

@Composable
internal fun BetaUpdatesConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_beta_updates_confirm_title)) },
        text = { Text(stringResource(R.string.settings_beta_updates_confirm_body)) },
        confirmButton = {
            AppOutlinedButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_beta_updates_confirm_ok))
            }
        },
        dismissButton = {
            AppOutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
internal fun BetaUpdatesRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    var showConfirm by rememberSaveable { mutableStateOf(false) }

    if (showConfirm) {
        BetaUpdatesConfirmDialog(
            onConfirm = {
                showConfirm = false
                onEnabledChange(true)
            },
            onDismiss = { showConfirm = false },
        )
    }

    SettingsSwitchRow(
        icon = Icons.Rounded.Science,
        title = stringResource(R.string.settings_beta_updates_title),
        subtitle = if (enabled) {
            stringResource(R.string.settings_beta_updates_on)
        } else {
            stringResource(R.string.settings_beta_updates_off)
        },
        checked = enabled,
        onCheckedChange = { next ->
            if (next) showConfirm = true else onEnabledChange(false)
        },
    )
}

@Composable
internal fun TimeZoneRow(
    zoneId: String?,
    onZoneChange: (String?) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val deviceZone = remember { ZoneId.systemDefault().id }

    if (showPicker) {
        TimeZonePickerDialog(
            selected = zoneId,
            onDismiss = { showPicker = false },
            onSelect = {
                onZoneChange(it)
                showPicker = false
            },
        )
    }

    SettingsActionRow(
        icon = Icons.Rounded.Public,
        title = stringResource(R.string.settings_time_zone_title),
        subtitle = zoneId ?: stringResource(R.string.settings_time_zone_subtitle_system, deviceZone),
        onClick = { showPicker = true },
    )
}

/** Localized [cityName] is searchable; [displayName] is the fallback zone label. */
internal data class ZoneChoice(
    val id: String,
    val cityName: String,
    val displayName: String,
    val offsetLabel: String,
    val offsetSeconds: Int,
) {
    val label: String get() = cityName.ifBlank { displayName }
}

internal fun matchesZoneQuery(choice: ZoneChoice, query: String): Boolean {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return true
    return listOf(choice.cityName, choice.displayName, choice.id, choice.offsetLabel)
        .any { it.lowercase(Locale.ROOT).contains(needle) }
}

/** Sort by UTC offset, then localized name using [nameComparator]. */
internal fun sortZoneChoices(
    choices: List<ZoneChoice>,
    nameComparator: Comparator<String> = naturalOrder(),
): List<ZoneChoice> = choices.sortedWith(
    compareBy<ZoneChoice> { it.offsetSeconds }
        .thenComparing({ it.label }, nameComparator)
        .thenBy { it.id },
)

private fun buildZoneChoices(locale: Locale, now: Instant): List<ZoneChoice> {
    val timeZoneNames = runCatching { android.icu.text.TimeZoneNames.getInstance(locale) }.getOrNull()
    return ZoneId.getAvailableZoneIds().mapNotNull { id ->
        val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return@mapNotNull null
        val offset = zone.rules.getOffset(now)
        ZoneChoice(
            id = id,
            cityName = runCatching { timeZoneNames?.getExemplarLocationName(id) }.getOrNull().orEmpty(),
            displayName = TimeZone.getTimeZone(id)
                .getDisplayName(false, TimeZone.LONG, locale)
                .takeIf { it.isNotBlank() } ?: id,
            offsetLabel = "GMT" + offset.id.replace("Z", "+00:00"),
            offsetSeconds = offset.totalSeconds,
        )
    }
}

@Composable
private fun TimeZonePickerDialog(
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val choices = remember(locale) {
        val collator = Collator.getInstance(locale)
        sortZoneChoices(buildZoneChoices(locale, Instant.now())) { a, b -> collator.compare(a, b) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val matched = remember(choices, query) { choices.filter { matchesZoneQuery(it, query) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_time_zone_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_time_zone_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_time_zone_search)) },
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item {
                        ZoneOption(
                            label = stringResource(R.string.settings_time_zone_system),
                            detail = null,
                            selected = selected == null,
                            onSelect = { onSelect(null) },
                        )
                    }
                    items(matched, key = { it.id }) { choice ->
                        ZoneOption(
                            label = choice.label,
                            detail = "${choice.offsetLabel} · ${choice.displayName}",
                            selected = selected == choice.id,
                            onSelect = { onSelect(choice.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun ZoneOption(
    label: String,
    detail: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
