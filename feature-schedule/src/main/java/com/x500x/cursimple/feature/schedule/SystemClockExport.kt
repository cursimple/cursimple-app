package com.x500x.cursimple.feature.schedule

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.reminderDayPolicy
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.AlarmDaySuppression
import com.x500x.cursimple.core.reminder.alarmDaySuppression
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.core.reminder.model.reminderNotificationTitleText
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * System Clock chooses the next wall-clock occurrence; defer tomorrow's export until today's
 * same minute has passed.
 */
internal data class SystemClockExportPlan(
    val date: LocalDate,
    val writable: List<SystemClockAlarm>,
    val notYet: List<SystemClockAlarm>,
    val alreadyMuted: Boolean,
) {
    val allWritableAfter: LocalTime? get() = notYet.maxOfOrNull { it.time }
}

internal data class SystemClockAlarm(
    val time: LocalTime,
    val records: List<SystemAlarmRecord>,
)

internal fun systemClockExportPlan(
    records: List<SystemAlarmRecord>,
    nowMillis: Long,
    appZone: ZoneId,
    deviceZone: ZoneId,
    suppression: (SystemAlarmRecord) -> AlarmDaySuppression?,
): SystemClockExportPlan {
    val tomorrow = Instant.ofEpochMilli(nowMillis).atZone(appZone).toLocalDate().plusDays(1)
    val candidates = records.filter {
        it.backend == ReminderAlarmBackend.AppAlarmClock &&
            it.enabled &&
            it.triggerAtMillis > nowMillis &&
            Instant.ofEpochMilli(it.triggerAtMillis).atZone(appZone).toLocalDate() == tomorrow
    }
    val alreadyMuted = candidates.any { suppression(it) == AlarmDaySuppression.MutedDate }
    val now = Instant.ofEpochMilli(nowMillis).atZone(deviceZone).toLocalDateTime()
    val alarms = candidates
        .filter { suppression(it) == null }
        .groupBy { triggerMinute(it.triggerAtMillis, deviceZone) }
        .toSortedMap()
        .map { (minute, group) -> minute to SystemClockAlarm(minute.toLocalTime(), group.sortedBy { it.triggerAtMillis }) }
    val (writable, notYet) = alarms.partition { (minute, _) -> nextOccurrence(now, minute.toLocalTime()) == minute }
    return SystemClockExportPlan(
        date = tomorrow,
        writable = writable.map { it.second },
        notYet = notYet.map { it.second },
        alreadyMuted = alreadyMuted,
    )
}

private fun triggerMinute(millis: Long, zone: ZoneId): LocalDateTime =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)

/** Resolve the system Clock's next occurrence: today if still future, otherwise tomorrow. */
private fun nextOccurrence(now: LocalDateTime, time: LocalTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

internal fun Context.systemAlarmRecordTitle(record: SystemAlarmRecord): String =
    record.titleContent?.let { reminderNotificationTitleText(it) }
        ?: record.displayTitle ?: record.alarmLabel ?: record.message

/**
 * Space creation requests so Clock can handle each; return submitted count or null without a
 * handler.
 */
private suspend fun Context.sendToSystemClock(alarms: List<SystemClockAlarm>): Int? {
    var sent = 0
    alarms.forEachIndexed { index, alarm ->
        if (index > 0) delay(SYSTEM_CLOCK_SEND_GAP_MILLIS)
        val titles = alarm.records.map { systemAlarmRecordTitle(it) }.distinct().joinToString("、")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_HOUR, alarm.time.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.time.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, getString(R.string.schedule_system_clock_label, titles))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        try {
            startActivity(intent)
            sent += 1
        } catch (_: ActivityNotFoundException) {
            return null
        } catch (_: SecurityException) {
            return sent
        }
    }
    return sent
}

private const val SYSTEM_CLOCK_SEND_GAP_MILLIS = 600L

@Composable
internal fun SystemClockExportDialog(
    records: List<SystemAlarmRecord>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { DataStoreUserPreferencesRepository(appContext) }
    val preferences by repository.preferencesFlow.collectAsState(initial = UserPreferences())
    val nowMillis = remember { System.currentTimeMillis() }
    val plan = remember(records, preferences, nowMillis) {
        val policy = preferences.reminderDayPolicy()
        systemClockExportPlan(
            records = records,
            nowMillis = nowMillis,
            appZone = BeijingTime.zone,
            deviceZone = ZoneId.systemDefault(),
            suppression = {
                alarmDaySuppression(
                    it.triggerAtMillis, it.allowOnHoliday, BeijingTime.zone, policy,
                    preferences.holidayCalendar, preferences.temporaryScheduleOverrides,
                )
            },
        )
    }
    val canMute = plan.notYet.isEmpty()
    var mute by rememberSaveable { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.schedule_system_clock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    plan.alreadyMuted -> Text(stringResource(R.string.schedule_system_clock_muted))
                    plan.writable.isEmpty() && plan.notYet.isEmpty() ->
                        Text(stringResource(R.string.schedule_system_clock_empty))
                    else -> {
                        if (plan.writable.isNotEmpty()) {
                            Text(
                                pluralStringResource(
                                    R.plurals.schedule_system_clock_intro,
                                    plan.writable.size,
                                    plan.date.monthValue,
                                    plan.date.dayOfMonth,
                                    plan.writable.size,
                                ),
                            )
                            plan.writable.forEach { alarm ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(timeFormat.format(alarm.time), fontWeight = FontWeight.SemiBold)
                                    Text(
                                        alarm.records.map { context.systemAlarmRecordTitle(it) }.distinct().joinToString("、"),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        plan.allWritableAfter?.let { after ->
                            Text(
                                pluralStringResource(
                                    R.plurals.schedule_system_clock_not_yet,
                                    plan.notYet.size,
                                    plan.notYet.size,
                                    timeFormat.format(plan.notYet.first().time),
                                    timeFormat.format(after),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (plan.writable.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = canMute) { mute = !mute },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = canMute && mute, onCheckedChange = null, enabled = canMute)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.schedule_system_clock_mute))
                                    Text(
                                        stringResource(
                                            if (canMute) R.string.schedule_system_clock_mute_desc
                                            else R.string.schedule_system_clock_mute_unavailable,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.schedule_system_clock_caveat),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (plan.writable.isNotEmpty() && !plan.alreadyMuted) {
                Button(
                    enabled = !sending,
                    onClick = {
                        sending = true
                        scope.launch {
                            val sent = context.sendToSystemClock(plan.writable)
                            val allSent = sent == plan.writable.size
                            val muted = allSent && canMute && mute
                            if (muted) repository.setReminderMuted(plan.date.toString(), muted = true)
                            val message = when {
                                sent == null -> resources.getString(R.string.schedule_system_clock_no_app)
                                allSent && muted -> resources.getQuantityString(R.plurals.schedule_system_clock_done_muted, sent, sent)
                                allSent -> resources.getQuantityString(R.plurals.schedule_system_clock_done, sent, sent)
                                else -> resources.getString(R.string.schedule_system_clock_partial, sent, plan.writable.size)
                            }
                            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                            sending = false
                            onDismiss()
                        }
                    },
                ) { Text(stringResource(R.string.schedule_system_clock_confirm)) }
            }
        },
        dismissButton = {
            AppOutlinedButton(enabled = !sending, onClick = onDismiss) {
                Text(stringResource(R.string.schedule_action_cancel))
            }
        },
    )
}
