package com.x500x.cursimple.app

import com.x500x.cursimple.feature.plugin.ui.AppFilterChip
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.util.SystemCalendarAccount
import com.x500x.cursimple.app.util.SystemCalendarExportStore
import com.x500x.cursimple.app.util.SystemCalendarExporter
import com.x500x.cursimple.app.util.SystemCalendarUndoResult
import com.x500x.cursimple.app.util.SystemCalendarWriteResult
import com.x500x.cursimple.app.util.planScheduleOccurrences
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.time.BeijingTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Calendar export: write to the system calendar or share an .ics file. Exported event IDs are
 * recorded locally so undo removes only app-created events.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SystemCalendarExportDialog(
    canExport: Boolean,
    termStartDate: LocalDate?,
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
    icsBusy: Boolean,
    onExportIcs: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { BeijingTime.zone }

    var granted by remember { mutableStateOf(SystemCalendarExporter.hasWritePermission(context)) }
    var accounts by remember { mutableStateOf<List<SystemCalendarAccount>>(emptyList()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastRecordEventCount by remember { mutableIntStateOf(0) }
    var lastRecordAt by remember { mutableLongStateOf(0L) }
    var pendingOverwrite by remember { mutableStateOf(false) }

    val resources = LocalContext.current.resources
    val permissionDeniedText = stringResource(R.string.calendar_toast_permission_denied)
    val nothingToUndoText = stringResource(R.string.calendar_toast_nothing_to_undo)
    val failedUnknownText = stringResource(R.string.calendar_toast_failed_unknown)
    val exportedFormat = stringResource(R.string.calendar_toast_exported)
    val failedFormat = stringResource(R.string.calendar_toast_failed)

    fun reloadRecord() {
        val record = SystemCalendarExportStore.read(context)
        lastRecordEventCount = record?.eventIds?.size ?: 0
        lastRecordAt = record?.exportedAt ?: 0L
    }

    fun reloadAccounts() {
        accounts = SystemCalendarExporter.writableAccounts(context)
        if (accounts.none { it.id == selectedId }) {
            selectedId = accounts.firstOrNull()?.id
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = SystemCalendarExporter.hasWritePermission(context)
        if (granted) reloadAccounts()
    }

    LaunchedEffect(Unit) {
        granted = SystemCalendarExporter.hasWritePermission(context)
        if (granted) reloadAccounts()
        reloadRecord()
    }

    fun runWrite(calendarId: Long) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (SystemCalendarExportStore.read(context) != null) {
                    SystemCalendarExporter.undo(context)
                }
                val plan = planScheduleOccurrences(
                    termStartDate = termStartDate,
                    schedule = schedule,
                    manualCourses = manualCourses,
                    timingProfile = timingProfile,
                    overrides = overrides,
                    holidayCalendar = holidayCalendar,
                )
                SystemCalendarExporter.write(context, calendarId, plan, zone)
            }
            busy = false
            reloadRecord()
            val message = when (result) {
                is SystemCalendarWriteResult.Success -> {
                    val skippedNote = if (result.skipped.isNotEmpty()) {
                        "\n" + resources.getQuantityString(
                            R.plurals.calendar_export_skipped,
                            result.skipped.size,
                            result.skipped.size,
                        )
                    } else {
                        ""
                    }
                    exportedFormat.format(result.eventCount, result.occurrenceCount) + skippedNote
                }
                SystemCalendarWriteResult.PermissionDenied -> permissionDeniedText
                is SystemCalendarWriteResult.MissingConfig -> resources.getString(result.reason)
                is SystemCalendarWriteResult.Failed -> result.message
                    ?.let { failedFormat.format(it) }
                    ?: failedUnknownText
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun runUndo() {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { SystemCalendarExporter.undo(context) }
            busy = false
            reloadRecord()
            val message = when (result) {
                is SystemCalendarUndoResult.Success -> resources.getQuantityString(
                    R.plurals.calendar_toast_undone,
                    result.removedCount,
                    result.removedCount,
                )
                SystemCalendarUndoResult.NothingToUndo -> nothingToUndoText
                SystemCalendarUndoResult.PermissionDenied -> permissionDeniedText
                is SystemCalendarUndoResult.Failed -> result.message
                    ?.let { failedFormat.format(it) }
                    ?: failedUnknownText
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    if (pendingOverwrite) {
        AlertDialog(
            onDismissRequest = { pendingOverwrite = false },
            title = { Text(stringResource(R.string.calendar_export_overwrite_title)) },
            text = {
                Text(pluralStringResource(R.plurals.calendar_export_overwrite_body, lastRecordEventCount, lastRecordEventCount))
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingOverwrite = false
                        selectedId?.let(::runWrite)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.calendar_export_overwrite_confirm)) }
            },
            dismissButton = {
                AppOutlinedButton(onClick = { pendingOverwrite = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.ie_calendar_row_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!canExport) {
                    Text(
                        text = stringResource(R.string.ie_no_schedule_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                CalendarSectionTitle(
                    icon = Icons.Rounded.EventAvailable,
                    title = stringResource(R.string.calendar_export_title),
                    body = stringResource(R.string.calendar_export_subtitle),
                )
                when {
                    !granted -> {
                        Text(
                            text = stringResource(R.string.calendar_export_permission_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.calendar_export_permission_grant)) }
                    }
                    accounts.isEmpty() -> Text(
                        text = stringResource(R.string.calendar_export_no_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        Text(
                            text = stringResource(R.string.calendar_export_pick_account),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            accounts.forEach { account ->
                                AppFilterChip(
                                    selected = account.id == selectedId,
                                    onClick = { selectedId = account.id },
                                    enabled = !busy,
                                    label = {
                                        Text(
                                            text = account.displayName.ifBlank { account.accountName },
                                            maxLines = 2,
                                        )
                                    },
                                )
                            }
                        }
                        if (lastRecordEventCount > 0) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.calendar_export_last,
                                    lastRecordEventCount,
                                    formatExportTime(lastRecordAt, zone),
                                    lastRecordEventCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    val id = selectedId ?: return@Button
                                    if (lastRecordEventCount > 0) pendingOverwrite = true else runWrite(id)
                                },
                                enabled = canExport && !busy && selectedId != null,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.calendar_export_action), maxLines = 1) }
                            AppOutlinedButton(
                                onClick = { runUndo() },
                                enabled = !busy && lastRecordEventCount > 0,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.calendar_export_undo), maxLines = 1) }
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                CalendarSectionTitle(
                    icon = Icons.Rounded.CalendarMonth,
                    title = stringResource(R.string.ie_ics_title),
                    body = stringResource(R.string.ie_ics_body),
                )
                AppOutlinedButton(
                    onClick = onExportIcs,
                    enabled = canExport && !icsBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Text(
                        if (icsBusy) {
                            stringResource(R.string.ie_generating)
                        } else {
                            stringResource(R.string.ie_ics_generate_action)
                        },
                    )
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ie_close)) }
        },
    )
}

@Composable
private fun CalendarSectionTitle(icon: ImageVector, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatExportTime(millis: Long, zone: ZoneId): String {
    if (millis <= 0L) return ""
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    return formatter.format(Instant.ofEpochMilli(millis).atZone(zone))
}
