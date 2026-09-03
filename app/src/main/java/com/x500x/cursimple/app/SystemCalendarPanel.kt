package com.x500x.cursimple.app

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 把课表写进系统日历账户，并保留一键撤销。
 * 写入的事件 id 记在本机，撤销按 id 删除，不影响用户自己建的日程。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SystemCalendarPanel(
    canExport: Boolean,
    termStartDate: LocalDate?,
    schedule: TermSchedule?,
    manualCourses: List<CourseItem>,
    timingProfile: TermTimingProfile?,
    overrides: List<TemporaryScheduleOverride>,
    holidayCalendar: HolidayCalendarSettings,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    var granted by remember { mutableStateOf(SystemCalendarExporter.hasWritePermission(context)) }
    var accounts by remember { mutableStateOf<List<SystemCalendarAccount>>(emptyList()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastRecordEventCount by remember { mutableStateOf(0) }
    var lastRecordAt by remember { mutableStateOf(0L) }
    var pendingOverwrite by remember { mutableStateOf(false) }

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
                        "\n" + context.getString(R.string.calendar_export_skipped, result.skipped.size)
                    } else {
                        ""
                    }
                    context.getString(
                        R.string.calendar_toast_exported,
                        result.eventCount,
                        result.occurrenceCount,
                    ) + skippedNote
                }
                SystemCalendarWriteResult.PermissionDenied ->
                    context.getString(R.string.calendar_toast_permission_denied)
                is SystemCalendarWriteResult.MissingConfig -> context.getString(result.reason)
                is SystemCalendarWriteResult.Failed -> result.message
                    ?.let { context.getString(R.string.calendar_toast_failed, it) }
                    ?: context.getString(R.string.calendar_toast_failed_unknown)
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
                is SystemCalendarUndoResult.Success ->
                    context.getString(R.string.calendar_toast_undone, result.removedCount)
                SystemCalendarUndoResult.NothingToUndo ->
                    context.getString(R.string.calendar_toast_nothing_to_undo)
                SystemCalendarUndoResult.PermissionDenied ->
                    context.getString(R.string.calendar_toast_permission_denied)
                is SystemCalendarUndoResult.Failed -> result.message
                    ?.let { context.getString(R.string.calendar_toast_failed, it) }
                    ?: context.getString(R.string.calendar_toast_failed_unknown)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    if (pendingOverwrite) {
        AlertDialog(
            onDismissRequest = { pendingOverwrite = false },
            title = { Text(stringResource(R.string.calendar_export_overwrite_title)) },
            text = {
                Text(stringResource(R.string.calendar_export_overwrite_body, lastRecordEventCount))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingOverwrite = false
                    selectedId?.let(::runWrite)
                }) { Text(stringResource(R.string.calendar_export_overwrite_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwrite = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.calendar_export_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.calendar_export_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!granted) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.calendar_export_permission_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.calendar_export_permission_grant)) }
                return@Card
            }

            if (accounts.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.calendar_export_no_account),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Card
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.calendar_export_pick_account),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accounts.forEach { account ->
                    FilterChip(
                        selected = account.id == selectedId,
                        onClick = { selectedId = account.id },
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
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.calendar_export_last,
                        formatExportTime(lastRecordAt, zone),
                        lastRecordEventCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val id = selectedId ?: return@Button
                        if (lastRecordEventCount > 0) pendingOverwrite = true else runWrite(id)
                    },
                    enabled = canExport && !busy && selectedId != null,
                ) { Text(stringResource(R.string.calendar_export_action), maxLines = 1) }
                OutlinedButton(
                    onClick = { runUndo() },
                    enabled = !busy && lastRecordEventCount > 0,
                ) { Text(stringResource(R.string.calendar_export_undo), maxLines = 1) }
            }
        }
    }
}

private fun formatExportTime(millis: Long, zone: ZoneId): String {
    if (millis <= 0L) return ""
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    return formatter.format(Instant.ofEpochMilli(millis).atZone(zone))
}
