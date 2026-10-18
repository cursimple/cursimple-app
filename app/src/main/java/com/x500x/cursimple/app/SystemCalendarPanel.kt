package com.x500x.cursimple.app

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
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
            shape = RoundedCornerShape(24.dp),
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
                    modifier = Modifier.heightIn(min = 48.dp),
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
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.ie_calendar_row_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.ie_calendar_row_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!canExport) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.ie_no_schedule_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CalendarSectionHeader(
                            icon = Icons.Rounded.EventAvailable,
                            title = stringResource(R.string.calendar_export_title),
                            subtitle = stringResource(R.string.calendar_export_subtitle),
                        )

                        when {
                            !granted -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
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
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Lock,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.calendar_export_permission_grant))
                                        }
                                    }
                                }
                            }
                            accounts.isEmpty() -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.calendar_export_no_account),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = stringResource(R.string.calendar_export_pick_account),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 190.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    accounts.forEach { account ->
                                        val selected = account.id == selectedId
                                        Surface(
                                            onClick = { if (!busy) selectedId = account.id },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                                    else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                width = if (selected) 1.5.dp else 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                val dotColor = account.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .background(dotColor, CircleShape),
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = account.displayName.ifBlank { account.accountName },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    if (account.displayName.isNotBlank() && account.accountName.isNotBlank() && account.displayName != account.accountName) {
                                                        Text(
                                                            text = account.accountName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (lastRecordEventCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = pluralStringResource(
                                                    R.plurals.calendar_export_last,
                                                    lastRecordEventCount,
                                                    formatExportTime(lastRecordAt, zone),
                                                    lastRecordEventCount,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            val id = selectedId ?: return@Button
                                            if (lastRecordEventCount > 0) pendingOverwrite = true else runWrite(id)
                                        },
                                        enabled = canExport && !busy && selectedId != null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                    ) {
                                        if (busy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Sync,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.calendar_export_action), maxLines = 1)
                                        }
                                    }
                                    if (lastRecordEventCount > 0) {
                                        AppOutlinedButton(
                                            onClick = { runUndo() },
                                            enabled = !busy,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.calendar_export_undo), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CalendarSectionHeader(
                            icon = Icons.Rounded.CalendarMonth,
                            title = stringResource(R.string.ie_ics_title),
                            subtitle = stringResource(R.string.ie_ics_body),
                        )
                        AppOutlinedButton(
                            onClick = onExportIcs,
                            enabled = canExport && !icsBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            if (icsBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ie_generating))
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ie_ics_generate_action))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppOutlinedButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.ie_close)) }
        },
    )
}

@Composable
private fun CalendarSectionHeader(icon: ImageVector, title: String, subtitle: String) {
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
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = subtitle,
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
