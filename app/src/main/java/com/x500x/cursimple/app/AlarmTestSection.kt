package com.x500x.cursimple.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.reminder.model.AlarmDispatchResult
import com.x500x.cursimple.core.reminder.model.EditableAppAlarmSettings
import com.x500x.cursimple.core.reminder.model.reminderMessageText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Developer check: schedule one alarm about two seconds ahead through the same manual-alarm path
 * the alarm editor uses, so scheduling, the ringing screen and the notification can be checked.
 */
@Composable
internal fun AlarmTestSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AlarmDispatchResult?>(null) }
    val title = stringResource(R.string.dev_alarm_test_alarm_title)
    val message = stringResource(R.string.dev_alarm_test_alarm_message)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("dev-alarm-test"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.dev_alarm_test_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                busy = true
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        val coordinator = (context.applicationContext as ClassScheduleApplication).appContainer.reminderCoordinator
                        coordinator.createManualAppAlarm(
                            pluginId = "dev-alarm-test",
                            triggerAtMillis = System.currentTimeMillis() + TEST_ALARM_DELAY_MILLIS,
                            title = title,
                            message = message,
                            settings = EditableAppAlarmSettings(allowOnHoliday = true),
                        )
                    }
                    result = outcome
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dev-alarm-trigger"),
        ) {
            Text(stringResource(R.string.dev_alarm_test_trigger))
        }
        result?.let { outcome ->
            val text = if (outcome.succeeded) {
                stringResource(R.string.dev_alarm_test_scheduled)
            } else {
                stringResource(
                    R.string.dev_alarm_test_failed,
                    outcome.localizedMessage?.let { context.reminderMessageText(it) } ?: outcome.message,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (outcome.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("dev-alarm-result"),
            )
        }
    }
}

private const val TEST_ALARM_DELAY_MILLIS = 2_000L
