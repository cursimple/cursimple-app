package com.x500x.cursimple.app

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
import androidx.compose.material3.TextButton
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

/**
 * 测试版更新开关。
 *
 * 默认关闭，开启前弹窗二次确认；关闭时不确认。
 */
@Composable
internal fun BetaUpdatesRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    var showConfirm by rememberSaveable { mutableStateOf(false) }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.settings_beta_updates_confirm_title)) },
            text = { Text(stringResource(R.string.settings_beta_updates_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onEnabledChange(true)
                }) { Text(stringResource(R.string.settings_beta_updates_confirm_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
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

/** 时区选择。为空表示跟随设备。 */
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

/**
 * 一个可选时区。
 *
 * [cityName] 是按当前语言的城市名，中文下是「上海」这类写法，比时区标准名更好认也更好搜；
 * [displayName] 是时区的标准名，取不到城市名时用它顶上。
 */
internal data class ZoneChoice(
    val id: String,
    val cityName: String,
    val displayName: String,
    val offsetLabel: String,
    val offsetSeconds: Int,
) {
    val label: String get() = cityName.ifBlank { displayName }
}

/** 城市名、时区名、id 或偏移任一命中即算命中；查询为空时全部命中。 */
internal fun matchesZoneQuery(choice: ZoneChoice, query: String): Boolean {
    val needle = query.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return true
    return listOf(choice.cityName, choice.displayName, choice.id, choice.offsetLabel)
        .any { it.lowercase(Locale.ROOT).contains(needle) }
}

/**
 * 按与 UTC 的偏移排序，同偏移内按名称，便于按地理位置找。
 * 名称比较交给 [nameComparator]，中文按字符码排出来的顺序读起来是乱的。
 */
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
            // 取不到本地化名称时退回 id，不留空行
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
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
