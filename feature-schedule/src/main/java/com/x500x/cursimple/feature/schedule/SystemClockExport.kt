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
 * 把明天的闹钟抄一份到系统时钟。
 *
 * 系统时钟只收「几点几分」，落在下一次到达的那个钟点：明天 8:00 的闹钟要等今天 8:00 过了才能写，
 * 否则它会在今天响。这类先不写，告诉用户几点以后再来。
 */
internal data class SystemClockExportPlan(
    /** 明天，按应用时区算，和响铃时判断静音用的是同一个日期。 */
    val date: LocalDate,
    /** 现在写进去正好落在明天的，同一分钟的合成一条。 */
    val writable: List<SystemClockAlarm>,
    /** 现在写会落到今天的。 */
    val notYet: List<SystemClockAlarm>,
    /** 明天已经静音，课简不响，也就没东西可写。 */
    val alreadyMuted: Boolean,
) {
    /** 过了这个钟点（设备时间）再写就能全部写进去。 */
    val allWritableAfter: LocalTime? get() = notYet.maxOfOrNull { it.time }
}

internal data class SystemClockAlarm(
    /** 设备时区下的钟点，系统时钟按设备时区解释。 */
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

/** 系统时钟收到一个钟点后实际会响的时刻：今天还没到就是今天，否则是明天。 */
private fun nextOccurrence(now: LocalDateTime, time: LocalTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

internal fun Context.systemAlarmRecordTitle(record: SystemAlarmRecord): String =
    record.titleContent?.let { reminderNotificationTitleText(it) }
        ?: record.displayTitle ?: record.alarmLabel ?: record.message

/**
 * 逐条交给系统时钟。连发时有的时钟会吞掉后面的请求，所以每条之间停一下；
 * 全部停顿加起来远小于系统给刚离开前台的应用留的 10 秒启动窗口。
 * 返回送出去的条数；系统里没有能接的时钟应用时返回 null。
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
    // 有写不进去的就不静音：静音按整天算，会把这些也一起关掉，两边都不响
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
