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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.feature.plugin.PluginWebSessionScreen

@Composable
fun ScheduleSettingsRoute(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleSettingsScreen(
        state = state,
        onSelectTimeSlot = viewModel::selectTimeSlot,
        onClearSelection = viewModel::clearSelection,
        onCreateReminder = viewModel::createReminderForSelection,
        onRemoveReminderRule = viewModel::removeReminderRule,
        onCompleteWebSession = viewModel::completeWebSession,
        onCancelWebSession = viewModel::cancelWebSession,
        modifier = modifier,
    )
}

@Composable
fun ScheduleSettingsScreen(
    state: ScheduleUiState,
    onSelectTimeSlot: (Int, Int) -> Unit,
    onClearSelection: () -> Unit,
    onCreateReminder: (Int, String?) -> Unit,
    onRemoveReminderRule: (String) -> Unit,
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
            val statusLines = buildList {
                state.statusMessage?.takeIf(String::isNotBlank)?.let(::add)
                addAll(state.messages)
            }
            if (statusLines.isNotEmpty()) {
                MessageCard(
                    title = "插件状态",
                    lines = statusLines,
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
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
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

