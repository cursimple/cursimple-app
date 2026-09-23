package com.x500x.cursimple.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticePreferences
import com.x500x.cursimple.core.data.ClassNoticeSkin

/**
 * 侧边栏「提醒」页，分两栏：
 *
 * - 闹钟：按课程响铃的闹钟规则、铃声与重复，原来整页就是它；
 * - 提醒：上课前弹一条悬浮横幅或通知，这才是真正意义上的「提醒」。
 *   它的设置原本藏在「设置 → 提醒与权限 → 上课通知」里，这里放一份，不用绕路就能开关、改提前量。
 */
@Composable
internal fun RemindersScreen(
    classNotice: ClassNoticePreferences,
    onClassNoticeEnabledChange: (Boolean) -> Unit,
    onClassNoticeAdvanceMinutesChange: (Int) -> Unit,
    onClassNoticeHeadsUpChange: (Boolean) -> Unit,
    onClassNoticeLockScreenChange: (Boolean) -> Unit,
    onClassNoticeFocusChange: (Boolean) -> Unit,
    onClassNoticeSkinChange: (ClassNoticeSkin) -> Unit,
    onClassNoticeAnimationChange: (ClassNoticeAnimation) -> Unit,
    onClassNoticeBlurChange: (Boolean) -> Unit,
    onClassNoticeBlurStrengthChange: (Int) -> Unit,
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
                .padding(horizontal = 18.dp, vertical = 12.dp),
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
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reminders_notice_tab_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ClassNoticeSettingsSection(
                        preferences = classNotice,
                        onEnabledChange = onClassNoticeEnabledChange,
                        onAdvanceMinutesChange = onClassNoticeAdvanceMinutesChange,
                        onHeadsUpChange = onClassNoticeHeadsUpChange,
                        onLockScreenChange = onClassNoticeLockScreenChange,
                        onFocusChange = onClassNoticeFocusChange,
                        onSkinChange = onClassNoticeSkinChange,
                        onAnimationChange = onClassNoticeAnimationChange,
                        onBlurChange = onClassNoticeBlurChange,
                        onBlurStrengthChange = onClassNoticeBlurStrengthChange,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

private enum class RemindersTab(val labelRes: Int, val icon: ImageVector) {
    Alarms(R.string.reminders_tab_alarms, Icons.Rounded.Alarm),
    Notice(R.string.reminders_tab_notice, Icons.Rounded.NotificationsActive),
}

/** 与插件页「插件 / 组件」同一种胶囊分栏，两处切换手感一致。 */
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
    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
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
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(tab.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
