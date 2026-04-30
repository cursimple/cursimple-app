package com.kebiao.viewer.feature.schedule

import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.feature.plugin.PluginWebSessionScreen

@Composable
fun ScheduleSettingsRoute(
    viewModel: ScheduleViewModel,
    onOpenPluginMarket: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleSettingsScreen(
        state = state,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTermIdChange = viewModel::onTermIdChange,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onSyncClick = viewModel::syncSchedule,
        onSelectTimeSlot = viewModel::selectTimeSlot,
        onClearSelection = viewModel::clearSelection,
        onCreateReminder = viewModel::createReminderForSelection,
        onRemoveReminderRule = viewModel::removeReminderRule,
        onOpenPluginMarket = onOpenPluginMarket,
        onCompleteWebSession = viewModel::completeWebSession,
        onCancelWebSession = viewModel::cancelWebSession,
        modifier = modifier,
    )
}

@Composable
fun ScheduleSettingsScreen(
    state: ScheduleUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTermIdChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    onSelectTimeSlot: (Int, Int) -> Unit,
    onClearSelection: () -> Unit,
    onCreateReminder: (Int, String?) -> Unit,
    onRemoveReminderRule: (String) -> Unit,
    onOpenPluginMarket: () -> Unit,
    onCompleteWebSession: (WebSessionPacket) -> Unit,
    onCancelWebSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var advanceMinutesText by rememberSaveable { mutableStateOf("20") }
    var ringtoneUri by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCourse = remember(state.selectionState, state.schedule) {
        selectedCourseFromState(state.selectionState, state.schedule)
    }
    val scrollState = rememberScrollState()
    val selectedPlugin = remember(state.installedPlugins, state.pluginId) {
        state.installedPlugins.firstOrNull { it.pluginId == state.pluginId }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        ringtoneUri = uri?.toString()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsHeaderCard(
                pluginName = selectedPlugin?.name,
                pluginVersion = selectedPlugin?.version,
            )

            SyncSettingsCard(
                baseUrl = state.baseUrl,
                termId = state.termId,
                username = state.username,
                password = state.password,
                isSyncing = state.isSyncing,
                onBaseUrlChange = onBaseUrlChange,
                onTermIdChange = onTermIdChange,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onSyncClick = onSyncClick,
                onOpenPluginMarket = onOpenPluginMarket,
            )

            PluginBannerSection(state.uiSchema)

            if (state.messages.isNotEmpty()) {
                MessageCard(
                    title = "插件消息",
                    lines = state.messages,
                )
            }

            if (state.alarmRecommendations.isNotEmpty()) {
                MessageCard(
                    title = "提醒建议",
                    lines = state.alarmRecommendations.map { "建议提前 ${it.advanceMinutes} 分钟：${it.note}" },
                )
            }

            if (state.selectionState != null) {
                ReminderComposerCard(
                    selectedCourse = selectedCourse,
                    selectionState = state.selectionState,
                    advanceMinutesText = advanceMinutesText,
                    ringtoneUri = ringtoneUri,
                    onAdvanceMinutesChange = { advanceMinutesText = it.filter(Char::isDigit) },
                    onPickRingtone = {
                        ringtoneLauncher.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_ALARM,
                                )
                            },
                        )
                    },
                    onCreateReminder = {
                        onCreateReminder(advanceMinutesText.toIntOrNull() ?: 20, ringtoneUri)
                    },
                    onSelectSameSlot = {
                        selectedCourse?.let { course ->
                            onSelectTimeSlot(course.time.startNode, course.time.endNode)
                        }
                    },
                    onClearSelection = onClearSelection,
                )
            }

            if (state.reminderRules.isNotEmpty()) {
                ReminderRulesSection(
                    reminderRules = state.reminderRules,
                    onRemoveReminderRule = onRemoveReminderRule,
                )
            }
        }

        state.pendingWebSession?.let { request ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xD9000000))
                    .padding(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    PluginWebSessionScreen(
                        request = request,
                        onFinish = onCompleteWebSession,
                        onCancel = onCancelWebSession,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeaderCard(
    pluginName: String?,
    pluginVersion: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    pluginName.isNullOrBlank() -> "先去插件页安装并选择学校插件，再在这里填写同步信息。"
                    pluginVersion.isNullOrBlank() -> "当前插件：$pluginName"
                    else -> "当前插件：$pluginName $pluginVersion"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
