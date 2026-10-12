package com.x500x.cursimple.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.reminder.ReminderGuardForegroundService
import com.x500x.cursimple.app.vendor.*
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.UserPreferences
import kotlinx.coroutines.launch

@Composable
internal fun VendorCourseSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DataStoreUserPreferencesRepository(context.applicationContext) }
    val preferences by repository.preferencesFlow.collectAsState(initial = UserPreferences())
    val revision by VendorCourseStore.changes.collectAsState()
    val config = remember(revision) { VendorCourseStore.config(context) }
    val guardState by ReminderGuardForegroundService.state.collectAsState()
    val guardRevision by ReminderGuardForegroundService.changes.collectAsState()
    val guarded = remember(guardRevision, preferences.alarmKeepAliveEnabled) { ReminderGuardForegroundService.isEnabled(context) && preferences.alarmKeepAliveEnabled }
    var opened by remember { mutableStateOf(false) }
    var configure by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    SettingsSwitchRow(icon = Icons.Rounded.NotificationsActive,
        title = stringResource(R.string.reminder_guard_title), subtitle = stringResource(if (guarded && guardState != "running") R.string.reminder_guard_waiting else R.string.reminder_guard_description),
        checked = guarded, onCheckedChange = { enabled -> scope.launch {
            if (enabled) repository.setAlarmKeepAliveEnabled(true)
            ReminderGuardForegroundService.setEnabled(context, enabled)
            if (enabled) ReminderGuardForegroundService.startIfEnabled(context)
        } })
    SettingsActionRow(icon = Icons.Rounded.Star, title = stringResource(R.string.vendor_course_title),
        subtitle = stringResource(R.string.vendor_course_description), onClick = { opened = true })
    if (opened) AlertDialog(onDismissRequest = { opened = false },
        title = { Text(stringResource(R.string.vendor_course_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.vendor_course_explanation), style = MaterialTheme.typography.bodySmall)
            SettingsSwitchRow(icon = Icons.Rounded.Star, title = stringResource(R.string.vendor_course_enable),
                subtitle = stringResource(R.string.vendor_course_enable_description), checked = config.enabled,
                onCheckedChange = { VendorCourseStore.save(context, config.copy(enabled = it)) })
            SettingsSwitchRow(icon = Icons.Rounded.Lock, title = stringResource(R.string.vendor_course_aod),
                subtitle = stringResource(R.string.vendor_course_aod_description), checked = config.aodEnabled,
                onCheckedChange = { VendorCourseStore.save(context, config.copy(aodEnabled = it)) })
            HorizontalDivider()
            VendorCourseStore.statuses(context).forEach { (vendor, status) ->
                Text("${when(vendor) { "android" -> "Android"; "oppo" -> "OPPO / OnePlus"; "honor" -> "HONOR"; else -> "vivo / iQOO" }} · ${stringResource(vendorStatusLabel(status))}", style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (preferences.advancedToolsEnabled) {
                SettingsActionRow(icon = Icons.Rounded.Settings, title = stringResource(R.string.vendor_course_configuration),
                    subtitle = stringResource(R.string.vendor_course_configuration_description), onClick = { configure = true })
            }
            TextButton(onClick = {
                val report = com.x500x.cursimple.app.notice.ClassNoticeDiagnostics.report(context, preferences.classNotice).joinToString("\n") { "${it.first}: ${it.second}" }
                clipboard.setText(AnnotatedString("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}\n$report"))
            }) { Text(stringResource(R.string.vendor_course_copy)) }
        } },
        confirmButton = { TextButton(onClick = { opened = false }) { Text(stringResource(R.string.notice_guide_close)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
            try { VendorCourseSync.refresh(context) } catch (_: Exception) { VendorCourseStore.status(context, "android", "failed") }
            finally { busy = false }
        } }) { Text(stringResource(R.string.vendor_course_refresh)) } },
    )
    if (configure) {
        var vivoId by remember { mutableStateOf(config.vivoIntentId) }
        var vivoName by remember { mutableStateOf(config.vivoIntentName) }
        var vivoEntity by remember { mutableStateOf(config.vivoEntityName) }
        var capsule by remember { mutableStateOf(config.honorCapsuleType) }
        var card by remember { mutableStateOf(config.honorCardType) }
        var icon by remember { mutableStateOf(config.honorAodIconUrl) }
        var invalid by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { configure = false }, title = { Text(stringResource(R.string.vendor_course_configuration)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.vendor_course_configuration_description))
                OutlinedTextField(vivoId, { vivoId = it }, label = { Text(stringResource(R.string.vendor_course_vivo_id)) }, singleLine = true)
                OutlinedTextField(vivoName, { vivoName = it }, label = { Text(stringResource(R.string.vendor_course_vivo_name)) }, singleLine = true)
                OutlinedTextField(vivoEntity, { vivoEntity = it }, label = { Text(stringResource(R.string.vendor_course_vivo_entity)) }, singleLine = true)
                OutlinedTextField(capsule, { capsule = it }, label = { Text(stringResource(R.string.vendor_course_capsule_id)) }, singleLine = true)
                OutlinedTextField(card, { card = it }, label = { Text(stringResource(R.string.vendor_course_card_id)) }, singleLine = true)
                OutlinedTextField(icon, { icon = it }, label = { Text(stringResource(R.string.vendor_course_icon_url)) }, singleLine = true)
                if (invalid) Text(stringResource(R.string.vendor_course_invalid), color = MaterialTheme.colorScheme.error)
            } }, confirmButton = { TextButton(onClick = {
                invalid = runCatching { VendorCourseStore.save(context, config.copy(vivoIntentId = vivoId.trim(), vivoIntentName = vivoName.trim(), vivoEntityName = vivoEntity.trim(), honorCapsuleType = capsule.trim(), honorCardType = card.trim(), honorAodIconUrl = icon.trim())) }.isFailure
                if (!invalid) configure = false
            }) { Text(stringResource(R.string.vendor_course_save)) } },
            dismissButton = { TextButton(onClick = { configure = false }) { Text(stringResource(R.string.notice_guide_close)) } })
    }
}

internal fun vendorStatusLabel(state: String): Int = when (state) {
    "disabled" -> R.string.vendor_course_status_disabled
    "unavailable" -> R.string.vendor_course_status_unavailable
    "ready" -> R.string.vendor_course_status_ready
    "queried" -> R.string.vendor_course_status_queried
    "accepted" -> R.string.vendor_course_status_accepted
    "confirmed" -> R.string.vendor_course_status_confirmed
    "throttled" -> R.string.vendor_course_status_throttled
    "configuration" -> R.string.vendor_course_status_configuration
    "unconfirmed" -> R.string.vendor_course_status_unconfirmed
    "cleanup_pending" -> R.string.vendor_course_status_cleanup
    "platform" -> R.string.vendor_course_status_platform
    "standard" -> R.string.vendor_course_status_standard
    "permission", "rejected" -> R.string.vendor_course_status_rejected
    "failed" -> R.string.vendor_course_status_failed
    else -> R.string.vendor_course_status_unchecked
}
