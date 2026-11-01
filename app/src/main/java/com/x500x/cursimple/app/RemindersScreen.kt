package com.x500x.cursimple.app

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.NotificationsActive
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin

/** Separate alarm rules from class-notice settings in the reminders page. */
@Composable
internal fun RemindersScreen(
    classNotice: ClassNoticePreferences,
    onClassNoticeEnabledChange: (Boolean) -> Unit,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

private enum class RemindersTab(val labelRes: Int, val icon: ImageVector) {
    Alarms(R.string.reminders_tab_alarms, Icons.Rounded.Alarm),
    Notice(R.string.reminders_tab_notice, Icons.Rounded.NotificationsActive),
}

/** Segmented tab: 48dp pill whose container is the selectable target. */
@Composable
private fun RemindersTabChip(
    tab: RemindersTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(50)
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = shape,
        color = containerColor,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppAtomicText(
                text = stringResource(tab.labelRes),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
