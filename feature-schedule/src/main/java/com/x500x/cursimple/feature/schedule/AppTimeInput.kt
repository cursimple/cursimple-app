package com.x500x.cursimple.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedTextField

@Stable
internal class AppTimeInputState(initialHour: String, initialMinute: String) {
    var hourText by mutableStateOf(initialHour)
        private set
    var minuteText by mutableStateOf(initialMinute)
        private set

    private val hour get() = hourText.toIntOrNull()?.takeIf { it in 0..23 }
    private val minute get() = minuteText.toIntOrNull()?.takeIf { it in 0..59 }
    val hourOutOfRange get() = hourText.isNotEmpty() && hour == null
    val minuteOutOfRange get() = minuteText.isNotEmpty() && minute == null
    val minutesOfDay: Int?
        get() {
            val hour = hour ?: return null
            val minute = minute ?: return null
            return hour * 60 + minute
        }

    fun editHour(value: String) {
        if (value.isTimeSegment()) hourText = value
    }

    fun editMinute(value: String) {
        if (value.isTimeSegment()) minuteText = value
    }
}

private fun String.isTimeSegment() = length <= 2 && all(Char::isDigit)

private val TimeInputSaver = listSaver<AppTimeInputState, String>(
    save = { listOf(it.hourText, it.minuteText) },
    restore = { AppTimeInputState(it[0], it[1]) },
)

@Composable
internal fun rememberAppTimeInputState(initialMinutesOfDay: Int): AppTimeInputState =
    rememberSaveable(initialMinutesOfDay, saver = TimeInputSaver) {
        val initial = initialMinutesOfDay.coerceIn(0, 24 * 60 - 1)
        AppTimeInputState((initial / 60).toString().padStart(2, '0'), (initial % 60).toString().padStart(2, '0'))
    }

@Composable
internal fun AppTimeInput(state: AppTimeInputState, modifier: Modifier = Modifier) {
    val minuteFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val numberStyle = MaterialTheme.typography.headlineSmall.copy(
        textAlign = TextAlign.Center,
        fontFeatureSettings = "tnum",
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppOutlinedTextField(
                value = state.hourText,
                onValueChange = state::editHour,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("time-input-hour"),
                label = { Text(stringResource(R.string.schedule_time_input_hour)) },
                textStyle = numberStyle,
                singleLine = true,
                isError = state.hourOutOfRange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { minuteFocus.requestFocus() }),
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppOutlinedTextField(
                value = state.minuteText,
                onValueChange = state::editMinute,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).focusRequester(minuteFocus).testTag("time-input-minute"),
                label = { Text(stringResource(R.string.schedule_time_input_minute)) },
                textStyle = numberStyle,
                singleLine = true,
                isError = state.minuteOutOfRange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        }
        if (state.hourOutOfRange || state.minuteOutOfRange) {
            Text(
                text = stringResource(R.string.schedule_time_input_range),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
