package com.x500x.cursimple.app

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin

/** Separate alarm rules from class-notice settings in the reminders page. */
@Composable
internal fun RemindersScreen(
    classNotice: ClassNoticePreferences,
    scheduleEventReminder: com.x500x.cursimple.core.data.EventReminderPreferences = com.x500x.cursimple.core.data.EventReminderPreferences(),
    onClassNoticeEnabledChange: (Boolean) -> Unit,
    onScheduleEventReminderEnabledChange: (Boolean) -> Unit = {},
    onScheduleEventReminderAdvanceMinutesChange: (Int) -> Unit = {},
    onClassNoticeAdvanceMinutesChange: (Int) -> Unit,
    onClassNoticeHeadsUpChange: (Boolean) -> Unit,
    onClassNoticeVibrationChange: (Boolean) -> Unit = {},
    onClassNoticeLockScreenChange: (Boolean) -> Unit,
    onClassNoticeFocusChange: (Boolean) -> Unit,
    onClassNoticeSkinChange: (ClassNoticeSkin) -> Unit,
    onClassNoticeAnimationChange: (ClassNoticeAnimation) -> Unit,
    onClassNoticeBlurChange: (Boolean) -> Unit,
    onClassNoticeBlurStrengthChange: (Int) -> Unit,
    onClassNoticeBannerDurationChange: (Int) -> Unit,
    onClassNoticeEveningBriefingChange: (Boolean) -> Unit = {},
    onClassNoticeTransitReminderChange: (Boolean) -> Unit = {},
    onClassNoticeExamAlertChange: (Boolean) -> Unit = {},
    alarmsContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(RemindersTab.Alarms) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Surface(
            shape = RoundedCornerShape(25.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RemindersTab.entries.forEach { tab ->
                    RemindersTabChip(
                        tab = tab,
                        selected = tab == selected,
                        onClick = { selected = tab },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (selected) {
                RemindersTab.Alarms -> alarmsContent(Modifier.fillMaxSize())
                RemindersTab.Notice -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EventReminderSettingsInline(
                        preferences = scheduleEventReminder,
                        onEnabledChange = onScheduleEventReminderEnabledChange,
                        onAdvanceMinutesChange = onScheduleEventReminderAdvanceMinutesChange,
                    )
                    ClassNoticeSettingsSection(
                        preferences = classNotice,
                        onEnabledChange = onClassNoticeEnabledChange,
                        onAdvanceMinutesChange = onClassNoticeAdvanceMinutesChange,
                        onHeadsUpChange = onClassNoticeHeadsUpChange,
                        onVibrationChange = onClassNoticeVibrationChange,
                        onLockScreenChange = onClassNoticeLockScreenChange,
                        onFocusChange = onClassNoticeFocusChange,
                        onSkinChange = onClassNoticeSkinChange,
                        onAnimationChange = onClassNoticeAnimationChange,
                        onBlurChange = onClassNoticeBlurChange,
                        onBlurStrengthChange = onClassNoticeBlurStrengthChange,
                        onBannerDurationChange = onClassNoticeBannerDurationChange,
                        onEveningBriefingChange = onClassNoticeEveningBriefingChange,
                        onTransitReminderChange = onClassNoticeTransitReminderChange,
                        onExamAlertChange = onClassNoticeExamAlertChange,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun EventReminderSettingsInline(
    preferences: com.x500x.cursimple.core.data.EventReminderPreferences,
    onEnabledChange: (Boolean) -> Unit,
    onAdvanceMinutesChange: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.EventNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_dest_event_reminder), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_event_reminder_enabled_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = preferences.enabled, onCheckedChange = onEnabledChange)
            }
            if (preferences.enabled) {
                Text(stringResource(R.string.settings_event_reminder_advance_title), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.x500x.cursimple.core.data.EventReminderPreferences.SUPPORTED_ADVANCE_MINUTES.forEach { minutes ->
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onAdvanceMinutesChange(minutes) },
                            color = if (preferences.advanceMinutes == minutes) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(if (minutes == 0) stringResource(R.string.schedule_event_reminder_on_time) else stringResource(R.string.schedule_event_reminder_summary_minutes, minutes), modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private enum class RemindersTab(val labelRes: Int, val icon: ImageVector) {
    Alarms(R.string.reminders_tab_alarms, Icons.Rounded.Alarm),
    Notice(R.string.reminders_tab_notice, Icons.Rounded.NotificationsActive),
}

/** Segmented tab: 42dp pill whose container is the selectable target. */
@Composable
private fun RemindersTabChip(
    tab: RemindersTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(21.dp)
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            AppAtomicText(
                text = stringResource(tab.labelRes),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.5.sp,
                    letterSpacing = 0.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = contentColor,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
