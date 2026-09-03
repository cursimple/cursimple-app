package com.x500x.cursimple.app

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.x500x.cursimple.R
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.app.reminder.AutoSilenceController
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.MAX_SLOT_NODE
import com.x500x.cursimple.core.data.widget.MIN_SLOT_NODE
import com.x500x.cursimple.core.data.widget.SlotDraftInput
import com.x500x.cursimple.core.data.widget.TimingDraftError
import com.x500x.cursimple.core.data.widget.buildTimingSlots
import com.x500x.cursimple.core.data.widget.slotTimes
import com.x500x.cursimple.core.data.widget.timingDraftErrorText
import com.x500x.cursimple.core.data.widget.timingTemplates
import com.x500x.cursimple.core.data.widget.toDraftInput
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.TimingProfileEntry
import com.x500x.cursimple.core.kernel.model.TimingProfileLibrary
import com.x500x.cursimple.core.kernel.model.active
import com.x500x.cursimple.core.kernel.model.termStartLocalDate
import com.x500x.cursimple.feature.widget.ScheduleWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.x500x.cursimple.core.kernel.time.toDatePickerMillis

/** 节次上课时间的编辑区，含作息套数管理与模板套用。 */

@Composable
internal fun TimingProfileEntryRow(onClick: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { DataStoreWidgetPreferencesRepository(context.applicationContext) }
    val profile by repository.timingProfileFlow.collectAsState(initial = null)
    val slotCount = profile?.slotTimes?.size ?: 0
    val subtitle = if (slotCount > 0) {
        stringResource(R.string.settings_timing_entry_subtitle_set, slotCount)
    } else {
        stringResource(R.string.settings_timing_entry_subtitle_unset)
    }
    SettingsActionRow(
        icon = Icons.Rounded.Schedule,
        title = stringResource(R.string.settings_dest_timing_profile),
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
internal fun TimingProfileSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { DataStoreWidgetPreferencesRepository(context.applicationContext) }
    val userPreferencesRepository = remember(context) { DataStoreUserPreferencesRepository(context.applicationContext) }
    val termRepository = remember(context) { DataStoreTermProfileRepository(context.applicationContext) }
    val manuallyEdited by repository.timingProfileManuallyEditedFlow.collectAsState(initial = false)
    val library by repository.timingProfileLibraryFlow.collectAsState(initial = TimingProfileLibrary())
    val activeProfileId = library.active?.id

    val drafts = remember { mutableStateListOf<SlotDraftInput>() }
    var errors by remember { mutableStateOf<List<TimingDraftError>>(emptyList()) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var creatingProfile by remember { mutableStateOf(false) }
    val newProfileName = stringResource(R.string.settings_timing_profile_default_name)
    // 带占位符的先取原文，结果出来后再填数
    val profileCreatedFormat = stringResource(R.string.settings_toast_timing_profile_created)
    val timingSavedFormat = stringResource(R.string.settings_toast_timing_saved)
    val timingHandbackText = stringResource(R.string.settings_toast_timing_handback)

    // 切换作息时把编辑区换成那一套的内容，否则改动会落到另一套上
    androidx.compose.runtime.LaunchedEffect(activeProfileId) {
        val slots = repository.timingProfileLibraryFlow.first().active?.slotTimes.orEmpty()
        drafts.clear()
        drafts.addAll(slots.mapIndexed { index, slot -> slot.toDraftInput(context, index + 1) })
        errors = emptyList()
    }

    // 选中一套作息同时把当前学期绑到它上面，之后学期之间来回切会自动带上各自的作息
    fun switchProfile(entry: TimingProfileEntry) {
        scope.launch {
            repository.activateTimingProfile(entry.id)
            val activeTermId = termRepository.activeTermId()
            if (activeTermId.isNotBlank()) {
                termRepository.setTermTimingProfile(activeTermId, entry.id)
            }
            withContext(Dispatchers.IO) {
                ScheduleWidgetUpdater.refreshAll(context.applicationContext)
                AutoSilenceController.evaluate(context.applicationContext, reason = "timing_profile_switched")
            }
        }
    }

    if (creatingProfile) {
        TimingProfileNameDialog(
            initial = newProfileName,
            onDismiss = { creatingProfile = false },
            onConfirm = { name ->
                creatingProfile = false
                scope.launch {
                    repository.createTimingProfile(name, emptyList())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            profileCreatedFormat.format(name.trim()),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }

    TimingProfileLibrarySection(
        library = library,
        onActivate = ::switchProfile,
        onCreate = { creatingProfile = true },
        onRename = { entry, name -> scope.launch { repository.renameTimingProfile(entry.id, name) } },
        onDuplicate = { entry, name -> scope.launch { repository.duplicateTimingProfile(entry.id, name) } },
        onDelete = { entry ->
            scope.launch {
                repository.deleteTimingProfile(entry.id)
                withContext(Dispatchers.IO) {
                    ScheduleWidgetUpdater.refreshAll(context.applicationContext)
                    AutoSilenceController.evaluate(context.applicationContext, reason = "timing_profile_deleted")
                }
            }
        },
    )

    SettingsSectionHeader(stringResource(R.string.settings_dest_timing_profile))

    fun updateRow(index: Int, transform: (SlotDraftInput) -> SlotDraftInput) {
        drafts[index] = transform(drafts[index])
    }

    if (drafts.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_timing_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_timing_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }

    Text(
        text = stringResource(R.string.settings_timing_hint, MIN_SLOT_NODE, MAX_SLOT_NODE),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (manuallyEdited) {
        Text(
            text = stringResource(R.string.settings_timing_manual_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    drafts.forEachIndexed { index, draft ->
        TimingSlotEditorRow(
            index = index,
            draft = draft,
            onChange = { updated -> updateRow(index) { updated } },
            onDelete = { drafts.removeAt(index) },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                drafts.add(SlotDraftInput("", "", "", "", ""))
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_timing_add_row))
        }
        OutlinedButton(
            onClick = { showTemplatePicker = true },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.settings_timing_apply_template))
        }
    }

    if (errors.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.settings_timing_errors_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                errors.forEach { error ->
                    Text(
                        text = "· ${context.timingDraftErrorText(error)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }

    Button(
        onClick = {
            val result = buildTimingSlots(drafts.toList())
            if (!result.isValid) {
                errors = result.errors
                return@Button
            }
            errors = emptyList()
            scope.launch {
                val existing = repository.timingProfileFlow.first()
                // 用户可能还没设开学日期，这里留空而不是发明一个，
                // 否则小组件、提醒与自动静音会据此算出周次，与界面显示的“未设置”矛盾
                val termStart = existing?.termStartLocalDate()?.toString()
                    ?: userPreferencesRepository.preferencesFlow.first().termStartDate?.toString()
                    ?: ""
                val profile = TermTimingProfile(
                    termStartDate = termStart,
                    slotTimes = result.slots,
                    timezone = existing?.timezone ?: "",
                )
                repository.saveManualTimingProfile(profile)
                withContext(Dispatchers.IO) {
                    ScheduleWidgetUpdater.refreshAll(context.applicationContext)
                    AutoSilenceController.evaluate(context.applicationContext, reason = "timing_profile_saved")
                }
                withContext(Dispatchers.Main) {
                    drafts.clear()
                    drafts.addAll(result.slots.mapIndexed { index, slot -> slot.toDraftInput(context, index + 1) })
                    Toast.makeText(
                        context,
                        timingSavedFormat.format(result.slots.size),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_save))
    }

    if (manuallyEdited) {
        TextButton(
            onClick = {
                scope.launch {
                    repository.clearManualTimingProfileFlag()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            timingHandbackText,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_timing_handback))
        }
    }

    if (showTemplatePicker) {
        TimingTemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onSelect = { template ->
                drafts.clear()
                drafts.addAll(
                    template.slotTimes(context).mapIndexed { index, slot -> slot.toDraftInput(context, index + 1) },
                )
                errors = emptyList()
                showTemplatePicker = false
            },
        )
    }
}

@Composable
internal fun TimingSlotEditorRow(
    index: Int,
    draft: SlotDraftInput,
    onChange: (SlotDraftInput) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_timing_row_index, index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.settings_timing_row_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.startNode,
                    onValueChange = { onChange(draft.copy(startNode = it.filter(Char::isDigit))) },
                    label = { Text(stringResource(R.string.settings_timing_start_node)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.endNode,
                    onValueChange = { onChange(draft.copy(endNode = it.filter(Char::isDigit))) },
                    label = { Text(stringResource(R.string.settings_timing_end_node)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.startTime,
                    onValueChange = { onChange(draft.copy(startTime = it)) },
                    label = { Text(stringResource(R.string.settings_timing_start_time)) },
                    placeholder = { Text("08:00") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.endTime,
                    onValueChange = { onChange(draft.copy(endTime = it)) },
                    label = { Text(stringResource(R.string.settings_timing_end_time)) },
                    placeholder = { Text("08:45") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draft.label,
                onValueChange = { onChange(draft.copy(label = it)) },
                label = { Text(stringResource(R.string.settings_timing_label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun TimingTemplatePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (com.x500x.cursimple.core.data.widget.TimingTemplate) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_timing_template_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.settings_timing_template_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                timingTemplates().forEach { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(template) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(template.nameRes),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(template.summaryRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}
