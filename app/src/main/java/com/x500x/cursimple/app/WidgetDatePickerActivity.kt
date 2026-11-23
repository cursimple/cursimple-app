package com.x500x.cursimple.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.kernel.time.WeekStartDay
import com.x500x.cursimple.feature.plugin.ui.AppAlertDialog
import com.x500x.cursimple.feature.plugin.ui.AppDialogPrimaryButton
import com.x500x.cursimple.feature.plugin.ui.AppDialogSecondaryButton
import com.x500x.cursimple.feature.schedule.CalendarMonthPicker
import com.x500x.cursimple.feature.widget.ScheduleWidgetActionReceiver
import com.x500x.cursimple.feature.widget.WidgetDeepLinks
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Transparent host that shows the app's own month picker for the day widget's title and pages the widget to the chosen date. */
class WidgetDatePickerActivity : ComponentActivity() {
    private var vanished = false

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun finish() {
        if (!vanished) { vanished = true; vanishThen(::finish); return }
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        if (isTaskRoot) super.finishAndRemoveTask() else super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setWindowAnimations(0)
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val shown = intent.getStringExtra(WidgetDeepLinks.EXTRA_SHOWN_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val container = (application as ClassScheduleApplication).appContainer
        setContent {
            val prefs by container.userPreferencesRepository.preferencesFlow.collectAsState(initial = UserPreferences())
            var selected by remember { mutableStateOf(shown) }
            ClassScheduleTheme(prefs.themeMode, prefs.themeAccent, prefs.themeCustomColorArgb) {
                AppAlertDialog(
                    onDismissRequest = ::finish,
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    title = { Text(stringResource(R.string.widget_date_picker_title)) },
                    text = {
                        CalendarMonthPicker(
                            selected = selected,
                            onSelect = { selected = it },
                            weekStartDayOfWeek = if (prefs.scheduleDisplay.weekStartDay == WeekStartDay.Sunday) 7 else 1,
                            headline = {
                                val fmt = DateTimeFormatter.ofPattern(stringResource(R.string.main_date_pattern))
                                Text(
                                    text = fmt.format(selected),
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            },
                        )
                    },
                    confirmButton = {
                        AppDialogPrimaryButton(onClick = {
                            sendBroadcast(
                                Intent(this@WidgetDatePickerActivity, ScheduleWidgetActionReceiver::class.java).apply {
                                    setPackage(packageName)
                                    putExtra(ScheduleWidgetActionReceiver.EXTRA_ACTION, ScheduleWidgetActionReceiver.ACTION_GOTO)
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    putExtra(ScheduleWidgetActionReceiver.EXTRA_TARGET_DATE, selected.toString())
                                },
                            )
                            finish()
                        }) { Text(stringResource(R.string.widget_date_picker_go)) }
                    },
                    dismissButton = {
                        AppDialogSecondaryButton(onClick = ::finish) { Text(stringResource(R.string.main_cancel)) }
                    },
                )
            }
        }
    }
}
