package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.feature.plugin.ui.AppCircleIconButton
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.x500x.cursimple.feature.plugin.ui.AppAtomicText
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.data.DataStoreUserPreferencesRepository
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.reminderDayPolicy
import com.x500x.cursimple.core.reminder.alarmDaySuppression
import com.x500x.cursimple.core.reminder.AlarmDaySuppression
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.EditableAppAlarmSettings
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import com.x500x.cursimple.core.reminder.model.ReminderLabelAction
import com.x500x.cursimple.core.reminder.model.ReminderLabelActionType
import com.x500x.cursimple.core.reminder.model.reminderNotificationMessageText
import com.x500x.cursimple.core.reminder.model.ReminderLabelCondition
import com.x500x.cursimple.core.reminder.model.ReminderLabelPresence
import com.x500x.cursimple.core.reminder.model.ReminderRule
import com.x500x.cursimple.core.reminder.model.ReminderScopeType
import com.x500x.cursimple.core.reminder.dispatch.AppAlarmClockRegistrationVerifier
import com.x500x.cursimple.core.reminder.model.SystemAlarmRecord
import com.x500x.cursimple.core.reminder.model.isLegacy
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import com.x500x.cursimple.feature.schedule.time.LocalAppZone
import com.x500x.cursimple.core.reminder.permission.AlarmPermission
import com.x500x.cursimple.core.reminder.permission.readAlarmPermissionState
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ScheduleSettingsRoute(
    viewModel: ScheduleViewModel,
    alarmRingtoneUri: String?,
    alarmAlertMode: AlarmAlertMode,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onAlarmRingtoneUriChange: (String?) -> Unit,
    onAlarmAlertModeChange: (AlarmAlertMode) -> Unit,
    onAlarmRingDurationSecondsChange: (Int) -> Unit,
    onAlarmRepeatIntervalSecondsChange: (Int) -> Unit,
    onAlarmRepeatCountChange: (Int) -> Unit,
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScheduleSettingsScreen(
        state = state,
        alarmRingtoneUri = alarmRingtoneUri,
        alarmAlertMode = alarmAlertMode,
        alarmRingDurationSeconds = alarmRingDurationSeconds,
        alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
        alarmRepeatCount = alarmRepeatCount,
        onAlarmRingtoneUriChange = onAlarmRingtoneUriChange,
        onAlarmAlertModeChange = onAlarmAlertModeChange,
        onAlarmRingDurationSecondsChange = onAlarmRingDurationSecondsChange,
        onAlarmRepeatIntervalSecondsChange = onAlarmRepeatIntervalSecondsChange,
        onAlarmRepeatCountChange = onAlarmRepeatCountChange,
        onPickSystemRingtone = onPickSystemRingtone,
        onPickLocalAudio = onPickLocalAudio,
        onSaveRule = viewModel::saveLabelReminderRule,
        onSetRuleEnabled = viewModel::setReminderRuleEnabled,
        onRemoveRule = viewModel::removeReminderRule,
        onSavePlaceholder = viewModel::savePlaceholderCourse,
        onDeletePlaceholder = viewModel::deletePlaceholderCourse,
        onSaveExamReminder = viewModel::saveExamReminder,
        onRefreshAlarms = viewModel::refreshReminderAlarmsNow,
        onDeleteAlarm = viewModel::removeAlarmRecord,
        onSetAppAlarmEnabled = viewModel::setAppAlarmEnabled,
        onUpdateAppAlarm = viewModel::updateAppAlarmSettings,
        onCreateManualAlarm = viewModel::createManualAppAlarm,
        modifier = modifier,
    )
}

/**
 * Alarm page: next alarm and primary action first, then upcoming alarms, automatic sources,
 * default ringing, and rarely used tools folded under "More".
 */
@Composable
fun ScheduleSettingsScreen(
    state: ScheduleUiState,
    alarmRingtoneUri: String?,
    alarmAlertMode: AlarmAlertMode,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onAlarmRingtoneUriChange: (String?) -> Unit,
    onAlarmAlertModeChange: (AlarmAlertMode) -> Unit,
    onAlarmRingDurationSecondsChange: (Int) -> Unit,
    onAlarmRepeatIntervalSecondsChange: (Int) -> Unit,
    onAlarmRepeatCountChange: (Int) -> Unit,
    onPickSystemRingtone: ((String?) -> Unit) -> Unit,
    onPickLocalAudio: ((String?) -> Unit) -> Unit,
    onSaveRule: (String?, String, Boolean, Int, String?, List<ReminderLabelCondition>, List<ReminderLabelAction>) -> Unit,
    onSetRuleEnabled: (String, Boolean) -> Unit,
    onRemoveRule: (String) -> Unit,
    onSavePlaceholder: (String?, String, String, String, List<Int>, List<Int>, String?) -> Unit,
    onDeletePlaceholder: (String) -> Unit,
    onSaveExamReminder: (Boolean, Int, String?) -> Unit,
    onRefreshAlarms: () -> Unit,
    onDeleteAlarm: (String, ReminderAlarmBackend) -> Unit,
    onSetAppAlarmEnabled: (String, Boolean) -> Unit,
    onUpdateAppAlarm: (String, EditableAppAlarmSettings) -> Unit,
    onCreateManualAlarm: (Long, String, String, EditableAppAlarmSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingRule by remember { mutableStateOf<ReminderRule?>(null) }
    var showRuleEditor by rememberSaveable { mutableStateOf(false) }
    // Gate new alarms and reminder rules on required access.
    val alarmPermissionGate = rememberAlarmPermissionGateState()
    val context = LocalContext.current
    var showPlaceholderDialog by rememberSaveable { mutableStateOf(false) }
    var editingPlaceholder by remember { mutableStateOf<PlaceholderCourseGroup?>(null) }
    var editingAlarm by remember { mutableStateOf<SystemAlarmRecord?>(null) }
    var showManualAlarmDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemClockExport by rememberSaveable { mutableStateOf(false) }
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    val slotLabels = remember(state.timingProfile, state.manualCourses) {
        (state.timingProfile?.slotTimes.orEmpty().map { it.label } +
            state.manualCourses.mapNotNull { it.slotLabelOverride })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val placeholderCourses = remember(state.manualCourses) {
        state.manualCourses
            .filter { it.reminderOnly }
            .groupBy { it.id.placeholderGroupId() }
            .values
            .map { PlaceholderCourseGroup(it.sortedBy { course -> course.time.dayOfWeek }) }
            .sortedWith(
                compareBy<PlaceholderCourseGroup>(
                    { it.representative.slotLabelOverride ?: it.representative.title },
                    { it.representative.reminderStartTime.orEmpty() },
                ),
            )
    }

    val zone = LocalAppZone.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { DataStoreUserPreferencesRepository(context.applicationContext) }
    val preferences by repository.preferencesFlow.collectAsState(initial = UserPreferences())
    val nowMillis = System.currentTimeMillis()
    val appRecords = state.systemAlarmRecords
        .filter { it.backend == ReminderAlarmBackend.AppAlarmClock && it.triggerAtMillis >= nowMillis }
        .sortedBy { it.triggerAtMillis }
    val unregisteredKeys = remember(appRecords) {
        val verifier = AppAlarmClockRegistrationVerifier(context)
        appRecords.filterNot { runCatching { verifier.isRegistered(it) }.getOrDefault(true) }
            .mapTo(mutableSetOf()) { it.alarmKey }
    }
    val suppressions = appRecords.associate { record ->
        record.alarmKey to alarmDaySuppression(
            record.triggerAtMillis, record.allowOnHoliday, zone, preferences.reminderDayPolicy(),
            preferences.holidayCalendar, preferences.temporaryScheduleOverrides,
        )
    }
    // Next alarm that will actually ring; skipped and disabled ones stay in the list only.
    val ringing = appRecords.filter { it.enabled && suppressions[it.alarmKey] == null }
    val labelRules = state.reminderRules.filter { it.scopeType == ReminderScopeType.LabelRule }
    val courseRules = state.reminderRules.filter { it.isCourseReminderRule() }
    val legacyRules = legacyReminderRules(state.reminderRules)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Keep missing required access visible until granted.
                AlarmAccessBanner(onFix = { alarmPermissionGate.require(context) {} })
                NextAlarmCard(
                    next = ringing.firstOrNull(),
                    remaining = (ringing.size - 1).coerceAtLeast(0),
                    onCreate = { alarmPermissionGate.require(context) { showManualAlarmDialog = true } },
                    onRefresh = onRefreshAlarms,
                )
            }

            if (appRecords.isNotEmpty()) {
                ReminderSection(
                    title = stringResource(R.string.schedule_section_upcoming),
                    description = stringResource(R.string.schedule_section_upcoming_desc),
                ) {
                    val today = LocalDate.now(zone)
                    appRecords
                        .groupBy { Instant.ofEpochMilli(it.triggerAtMillis).atZone(zone).toLocalDate() }
                        .forEach { (date, records) ->
                            Text(
                                text = alarmDayHeader(date, today),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            )
                            records.forEach { record ->
                                AlarmRecordRow(
                                    record = record,
                                    suppression = suppressions[record.alarmKey],
                                    registered = record.alarmKey !in unregisteredKeys,
                                    onEdit = { editingAlarm = record },
                                    onSetEnabled = { onSetAppAlarmEnabled(record.alarmKey, it) },
                                    onUnmute = {
                                        scope.launch { repository.setReminderMuted(date.toString(), muted = false) }
                                    },
                                    onReregister = onRefreshAlarms,
                                )
                            }
                        }
                }
            }

            ReminderSection(
                title = stringResource(R.string.schedule_section_auto),
                description = stringResource(R.string.schedule_section_auto_desc),
            ) {
                ExamReminderRow(
                    rules = state.reminderRules,
                    alarmRingtoneUri = alarmRingtoneUri,
                    alarmAlertMode = alarmAlertMode,
                    alarmRingDurationSeconds = alarmRingDurationSeconds,
                    alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
                    alarmRepeatCount = alarmRepeatCount,
                    onSave = onSaveExamReminder,
                )
                SubsectionLabel(
                    title = stringResource(R.string.schedule_rules_title),
                    description = stringResource(R.string.schedule_rules_desc),
                )
                if (labelRules.isEmpty()) {
                    EmptyHint(stringResource(R.string.schedule_rule_none))
                } else {
                    labelRules.forEach { rule ->
                        RuleRow(
                            rule = rule,
                            onEdit = {
                                editingRule = rule
                                showRuleEditor = true
                            },
                            onSetEnabled = { onSetRuleEnabled(rule.ruleId, it) },
                        )
                    }
                }
                AddButton(stringResource(R.string.schedule_new_rule)) {
                    alarmPermissionGate.require(context) {
                        editingRule = null
                        showRuleEditor = true
                    }
                }
                if (courseRules.isNotEmpty()) {
                    SubsectionLabel(
                        title = stringResource(R.string.schedule_course_reminder_card_title),
                        description = stringResource(R.string.schedule_course_reminder_card_subtitle, courseRules.size),
                    )
                    courseRules.forEach { rule ->
                        val name = rule.displayName?.removePrefix(COURSE_RULE_PREFIX)
                            ?: stringResource(R.string.schedule_course_unnamed)
                        CourseRuleRow(
                            name = name,
                            rule = rule,
                            onSetEnabled = { onSetRuleEnabled(rule.ruleId, it) },
                            onDelete = { pendingDelete = PendingDelete(name) { onRemoveRule(rule.ruleId) } },
                        )
                    }
                }
                if (legacyRules.isNotEmpty()) {
                    LegacyReminderRuleGroup(
                        rules = legacyRules,
                        onDelete = { name, ruleId -> pendingDelete = PendingDelete(name) { onRemoveRule(ruleId) } },
                    )
                }
            }

            ReminderSection(
                title = stringResource(R.string.schedule_defaults_title),
                description = stringResource(R.string.schedule_defaults_subtitle),
            ) {
                RingDefaults(
                    alarmRingtoneUri = alarmRingtoneUri,
                    alarmAlertMode = alarmAlertMode,
                    alarmRingDurationSeconds = alarmRingDurationSeconds,
                    alarmRepeatIntervalSeconds = alarmRepeatIntervalSeconds,
                    alarmRepeatCount = alarmRepeatCount,
                    onUseDefaultRingtone = { onAlarmRingtoneUriChange(null) },
                    onPickSystemRingtone = { onPickSystemRingtone { uri -> onAlarmRingtoneUriChange(uri) } },
                    onPickLocalAudio = { onPickLocalAudio { uri -> onAlarmRingtoneUriChange(uri) } },
                    onAlertModeChange = onAlarmAlertModeChange,
                    onRingDurationChange = onAlarmRingDurationSecondsChange,
                    onRepeatIntervalChange = onAlarmRepeatIntervalSecondsChange,
                    onRepeatCountChange = onAlarmRepeatCountChange,
                )
            }

            ReminderExpandableSection(
                title = stringResource(R.string.schedule_more_title),
                summary = stringResource(R.string.schedule_more_summary),
                expanded = moreExpanded,
                onExpandedChange = { moreExpanded = it },
            ) {
                SubsectionLabel(
                    title = stringResource(R.string.schedule_placeholder_card_title),
                    description = stringResource(R.string.schedule_placeholder_desc),
                )
                placeholderCourses.forEach { group ->
                    val course = group.representative
                    val name = course.slotLabelOverride ?: course.title
                    ReminderRowCard(
                        onClick = {
                            editingPlaceholder = group
                            showPlaceholderDialog = true
                        },
                        trailing = {
                            DeleteCircleButton(stringResource(R.string.schedule_action_delete)) {
                                pendingDelete = PendingDelete(name) { onDeletePlaceholder(course.id) }
                            }
                        },
                    ) {
                        RowTitle(name)
                        RowSupporting(
                            stringResource(
                                R.string.schedule_placeholder_summary,
                                group.daysOfWeek.joinToString(","),
                                course.reminderStartTime ?: "--:--",
                                course.reminderEndTime ?: "--:--",
                                course.weeks.ifEmpty { listOf(0) }.joinToString(",")
                                    .replace("0", stringResource(R.string.schedule_week_parity_all)),
                            ),
                        )
                    }
                }
                AddButton(stringResource(R.string.schedule_placeholder_add_title)) {
                    editingPlaceholder = null
                    showPlaceholderDialog = true
                }
                SubsectionLabel(
                    title = stringResource(R.string.schedule_system_clock_title),
                    description = stringResource(R.string.schedule_system_clock_desc),
                )
                AppOutlinedButton(
                    onClick = { showSystemClockExport = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.schedule_system_clock_action)) }
            }
        }
    }

    AlarmPermissionGateHost(alarmPermissionGate)

    pendingDelete?.let { request ->
        ReminderDeleteConfirmDialog(
            name = request.name,
            onConfirm = {
                request.onConfirm()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showRuleEditor) {
        val rule = editingRule
        ReminderRuleEditorDialog(
            rule = rule,
            slotLabels = slotLabels,
            onPickSystemRingtone = onPickSystemRingtone,
            onPickLocalAudio = onPickLocalAudio,
            onDismiss = { showRuleEditor = false },
            onSave = { ruleId, name, enabled, advance, ringtone, conditions, actions ->
                onSaveRule(ruleId, name, enabled, advance, ringtone, conditions, actions)
                showRuleEditor = false
            },
            onDelete = rule?.let {
                {
                    onRemoveRule(it.ruleId)
                    showRuleEditor = false
                }
            },
        )
    }

    if (showPlaceholderDialog) {
        PlaceholderCourseDialog(
            course = editingPlaceholder?.representative,
            daysOfWeek = editingPlaceholder?.daysOfWeek.orEmpty(),
            slotLabels = slotLabels,
            onDismiss = { showPlaceholderDialog = false },
            onSave = { id, label, start, end, weeks, days, title ->
                onSavePlaceholder(id, label, start, end, weeks, days, title)
                showPlaceholderDialog = false
            },
        )
    }

    editingAlarm?.let { alarm ->
        AppAlarmEditorDialog(
            record = alarm,
            onPickSystemRingtone = onPickSystemRingtone,
            onPickLocalAudio = onPickLocalAudio,
            onDismiss = { editingAlarm = null },
            onSave = { settings ->
                onUpdateAppAlarm(alarm.alarmKey, settings)
                editingAlarm = null
            },
            onDelete = {
                onDeleteAlarm(alarm.alarmKey, alarm.backend)
                editingAlarm = null
            },
        )
    }

    if (showSystemClockExport) {
        SystemClockExportDialog(
            records = state.systemAlarmRecords,
            onDismiss = { showSystemClockExport = false },
        )
    }

    if (showManualAlarmDialog) {
        ManualAppAlarmDialog(
            onPickSystemRingtone = onPickSystemRingtone,
            onPickLocalAudio = onPickLocalAudio,
            onDismiss = { showManualAlarmDialog = false },
            onCreate = { triggerAtMillis, title, message, settings ->
                onCreateManualAlarm(triggerAtMillis, title, message, settings)
                showManualAlarmDialog = false
            },
        )
    }
}

private class PendingDelete(val name: String, val onConfirm: () -> Unit)

/** Required access summary; recommended access stays in the permission dialog. */
@Composable
private fun AlarmAccessBanner(onFix: () -> Unit) {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(readAlarmPermissionState(context).missingBlocking) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missing = readAlarmPermissionState(context).missingBlocking
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (missing.isEmpty()) return

    val names = missing.map { stringResource(it.shortTitleRes()) }
        .joinToString(stringResource(R.string.schedule_summary_separator))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.schedule_alarm_access_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.schedule_alarm_access_body, names),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Button(
                onClick = onFix,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.schedule_exact_alarm_banner_action), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun AlarmPermission.shortTitleRes(): Int = when (this) {
    AlarmPermission.Notifications -> R.string.schedule_alarm_permission_notifications
    AlarmPermission.ExactAlarm -> R.string.schedule_alarm_permission_exact_alarm
    AlarmPermission.FullScreenIntent -> R.string.schedule_alarm_permission_full_screen
    AlarmPermission.BatteryUnrestricted -> R.string.schedule_alarm_permission_battery
    AlarmPermission.VendorAutoStart -> R.string.schedule_alarm_permission_autostart
}

/** Answer "when does it ring next" and offer the page's primary action. */
@Composable
private fun NextAlarmCard(
    next: SystemAlarmRecord?,
    remaining: Int,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
) {
    val zone = LocalAppZone.current
    val context = LocalContext.current
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.schedule_next_alarm_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer,
                    )
                    if (next != null) {
                        val date = Instant.ofEpochMilli(next.triggerAtMillis).atZone(zone).toLocalDate()
                        AppAtomicText(
                            text = formatAlarmTime(next.triggerAtMillis, zone),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = onContainer,
                        )
                        Text(
                            text = alarmDayHeader(date, LocalDate.now(zone)) + " · " +
                                context.systemAlarmRecordTitle(next),
                            style = MaterialTheme.typography.bodyLarge,
                            color = onContainer,
                            maxLines = 2,
                        )
                        if (remaining > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.schedule_next_alarm_more, remaining, remaining),
                                style = MaterialTheme.typography.bodySmall,
                                color = onContainer,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.schedule_next_alarm_none),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onContainer,
                        )
                        Text(
                            text = stringResource(R.string.schedule_next_alarm_none_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainer,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                AppCircleIconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.schedule_cd_refresh_alarms),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.schedule_new_alarm), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AlarmRecordRow(
    record: SystemAlarmRecord,
    suppression: AlarmDaySuppression?,
    registered: Boolean,
    onEdit: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onUnmute: () -> Unit,
    onReregister: () -> Unit,
) {
    val zone = LocalAppZone.current
    val context = LocalContext.current
    // Localize typed content, otherwise retain legacy display text.
    val title = context.systemAlarmRecordTitle(record)
    val message = (
        record.messageContent?.let { context.reminderNotificationMessageText(it) }
            ?: record.displayMessage ?: record.message
        ).takeIf { it.isNotBlank() && it != title }
    ReminderRowCard(
        onClick = onEdit,
        trailing = { Switch(checked = record.enabled, onCheckedChange = onSetEnabled) },
    ) {
        Column(
            modifier = Modifier.alpha(if (record.enabled) 1f else 0.6f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppAtomicText(
                text = formatAlarmTime(record.triggerAtMillis, zone),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            RowTitle(title)
            message?.let { RowSupporting(it, maxLines = 2) }
        }
        when {
            record.enabled && suppression == AlarmDaySuppression.MutedDate -> {
                RowStatus(stringResource(R.string.schedule_alarm_date_muted))
                AppOutlinedButton(onClick = onUnmute) { Text(stringResource(R.string.schedule_alarm_unmute_date)) }
            }
            record.enabled && suppression != null -> RowStatus(stringResource(R.string.schedule_alarm_holiday_skipped))
            record.allowOnHoliday -> RowSupporting(stringResource(R.string.schedule_alarm_allow_holiday))
        }
        if (!registered && record.enabled) {
            RowStatus(stringResource(R.string.schedule_alarm_not_registered), error = true)
            AppOutlinedButton(onClick = onReregister) { Text(stringResource(R.string.schedule_alarm_reregister)) }
        }
    }
}

/** Exam alarms are one switch; parameters follow the default ringing settings. */
@Composable
private fun ExamReminderRow(
    rules: List<ReminderRule>,
    alarmRingtoneUri: String?,
    alarmAlertMode: AlarmAlertMode,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onSave: (Boolean, Int, String?) -> Unit,
) {
    val enabled = examReminderEnabled(rules)
    val advance = rules.firstOrNull { it.isExamReminderRule() }?.advanceMinutes ?: EXAM_ADVANCE_MINUTES
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    ReminderRowCard(
        onClick = null,
        trailing = {
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked) showConfirm = true else onSave(false, EXAM_ADVANCE_MINUTES, null)
                },
            )
        },
    ) {
        RowTitle(stringResource(R.string.schedule_exam_card_title))
        RowSupporting(
            if (enabled) {
                stringResource(R.string.schedule_exam_switch_on, advance)
            } else {
                stringResource(R.string.schedule_exam_switch_off)
            },
        )
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.schedule_exam_confirm_title)) },
            text = {
                Text(
                    listOf(
                        stringResource(R.string.schedule_exam_confirm_intro),
                        stringResource(R.string.schedule_exam_confirm_ringtone, stringResource(alarmRingtoneLabelRes(alarmRingtoneUri))),
                        stringResource(R.string.schedule_exam_confirm_mode, stringResource(alarmAlertModeLabelRes(alarmAlertMode))),
                        stringResource(R.string.schedule_exam_confirm_duration, alarmRingDurationSeconds),
                        stringResource(R.string.schedule_exam_confirm_interval, alarmRepeatIntervalSeconds),
                        stringResource(R.string.schedule_exam_confirm_count, alarmRepeatCount),
                    ).joinToString("\n"),
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSave(true, EXAM_ADVANCE_MINUTES, null)
                    showConfirm = false
                }) { Text(stringResource(R.string.schedule_exam_confirm_ok)) }
            },
            dismissButton = { AppOutlinedButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.schedule_action_cancel)) } },
        )
    }
}

private const val EXAM_ADVANCE_MINUTES = 40

@Composable
private fun RuleRow(
    rule: ReminderRule,
    onEdit: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    ReminderRowCard(
        onClick = onEdit,
        trailing = { Switch(checked = rule.enabled, onCheckedChange = onSetEnabled) },
    ) {
        RowTitle(rule.displayName ?: stringResource(R.string.schedule_rule_unnamed))
        RowSupporting("${rule.conditionSummary()} → ${rule.actionSummary()}")
        RowSupporting(
            stringResource(
                R.string.schedule_course_rule_summary,
                rule.advanceMinutes,
                stringResource(alarmRingtoneLabelRes(rule.ringtoneUri)),
            ),
        )
    }
}

@Composable
private fun CourseRuleRow(
    name: String,
    rule: ReminderRule,
    onSetEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    ReminderRowCard(
        onClick = null,
        trailing = {
            Switch(checked = rule.enabled, onCheckedChange = onSetEnabled)
            DeleteCircleButton(stringResource(R.string.schedule_cd_delete_reminder), onDelete)
        },
    ) {
        RowTitle(name)
        RowSupporting(
            stringResource(
                R.string.schedule_course_rule_summary,
                rule.advanceMinutes,
                stringResource(alarmRingtoneLabelRes(rule.ringtoneUri)),
            ),
        )
    }
}

private data class PlaceholderCourseGroup(
    val courses: List<CourseItem>,
) {
    val representative: CourseItem = courses.first()
    val daysOfWeek: List<Int> = courses.map { it.time.dayOfWeek }.distinct().sorted()
}

@Composable
private fun RingDefaults(
    alarmRingtoneUri: String?,
    alarmAlertMode: AlarmAlertMode,
    alarmRingDurationSeconds: Int,
    alarmRepeatIntervalSeconds: Int,
    alarmRepeatCount: Int,
    onUseDefaultRingtone: () -> Unit,
    onPickSystemRingtone: () -> Unit,
    onPickLocalAudio: () -> Unit,
    onAlertModeChange: (AlarmAlertMode) -> Unit,
    onRingDurationChange: (Int) -> Unit,
    onRepeatIntervalChange: (Int) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
) {
    var timingExpanded by rememberSaveable { mutableStateOf(false) }
    AlarmRingtoneSelector(
        ringtoneUri = alarmRingtoneUri,
        onUseDefault = onUseDefaultRingtone,
        onPickSystem = onPickSystemRingtone,
        onPickLocal = onPickLocalAudio,
    )
    AlarmAlertModeSelector(
        selected = alarmAlertMode,
        includeDefault = false,
        onSelect = { mode -> mode?.let(onAlertModeChange) },
    )
    // Timing parameters are rarely changed; the summary keeps current values visible.
    ReminderExpandableSection(
        title = stringResource(R.string.schedule_ring_timing_title),
        summary = ringTimingSummary(alarmRingDurationSeconds, alarmRepeatIntervalSeconds, alarmRepeatCount),
        expanded = timingExpanded,
        onExpandedChange = { timingExpanded = it },
    ) {
        AlarmStepperRow(
            label = stringResource(R.string.schedule_ring_duration),
            value = stringResource(R.string.schedule_alarm_unit_seconds, alarmRingDurationSeconds),
            canDecrease = alarmRingDurationSeconds > 5,
            canIncrease = alarmRingDurationSeconds < 600,
            onDecrease = { onRingDurationChange((alarmRingDurationSeconds - 5).coerceAtLeast(5)) },
            onIncrease = { onRingDurationChange((alarmRingDurationSeconds + 5).coerceAtMost(600)) },
        )
        AlarmStepperRow(
            label = stringResource(R.string.schedule_ring_interval),
            value = stringResource(R.string.schedule_alarm_unit_seconds, alarmRepeatIntervalSeconds),
            canDecrease = alarmRepeatIntervalSeconds > 5,
            canIncrease = alarmRepeatIntervalSeconds < 3600,
            onDecrease = { onRepeatIntervalChange((alarmRepeatIntervalSeconds - 5).coerceAtLeast(5)) },
            onIncrease = { onRepeatIntervalChange((alarmRepeatIntervalSeconds + 5).coerceAtMost(3600)) },
        )
        AlarmStepperRow(
            label = stringResource(R.string.schedule_ring_count),
            value = pluralStringResource(R.plurals.schedule_alarm_unit_times, alarmRepeatCount, alarmRepeatCount),
            canDecrease = alarmRepeatCount > 1,
            canIncrease = alarmRepeatCount < 10,
            onDecrease = { onRepeatCountChange((alarmRepeatCount - 1).coerceAtLeast(1)) },
            onIncrease = { onRepeatCountChange((alarmRepeatCount + 1).coerceAtMost(10)) },
        )
    }
}

@Composable
internal fun ringTimingSummary(durationSeconds: Int, intervalSeconds: Int, count: Int): String =
    pluralStringResource(R.plurals.schedule_ring_timing_summary, count, durationSeconds, intervalSeconds, count)

internal fun legacyReminderRules(rules: List<ReminderRule>): List<ReminderRule> =
    rules.filter { it.scopeType.isLegacy() }

internal sealed interface LegacyReminderRuleLabel {
    data class DisplayName(val name: String) : LegacyReminderRuleLabel

    data class ScopeName(val nameRes: Int) : LegacyReminderRuleLabel
}

internal fun legacyReminderRuleScopeNameRes(scopeType: ReminderScopeType): Int = when (scopeType) {
    ReminderScopeType.SingleCourse -> R.string.schedule_legacy_rule_scope_single_course
    ReminderScopeType.TimeSlot -> R.string.schedule_legacy_rule_scope_time_slot
    ReminderScopeType.Exam -> R.string.schedule_legacy_rule_scope_exam
    ReminderScopeType.FirstCourseOfPeriod, ReminderScopeType.LabelRule ->
        R.string.schedule_legacy_rule_scope_generic
}

internal fun legacyReminderRuleLabel(rule: ReminderRule): LegacyReminderRuleLabel =
    rule.displayName?.takeIf { it.isNotBlank() }?.let(LegacyReminderRuleLabel::DisplayName)
        ?: LegacyReminderRuleLabel.ScopeName(legacyReminderRuleScopeNameRes(rule.scopeType))

internal fun Context.legacyReminderRuleLabelText(label: LegacyReminderRuleLabel): String =
    when (label) {
        is LegacyReminderRuleLabel.DisplayName -> label.name
        is LegacyReminderRuleLabel.ScopeName -> getString(label.nameRes)
    }

@Composable
private fun LegacyReminderRuleGroup(
    rules: List<ReminderRule>,
    onDelete: (name: String, ruleId: String) -> Unit,
) {
    val context = LocalContext.current
    SubsectionLabel(
        title = stringResource(R.string.schedule_legacy_card_title),
        description = pluralStringResource(R.plurals.schedule_legacy_card_subtitle, rules.size, rules.size),
    )
    rules.forEach { rule ->
        val name = context.legacyReminderRuleLabelText(legacyReminderRuleLabel(rule))
        ReminderRowCard(
            onClick = null,
            trailing = {
                DeleteCircleButton(stringResource(R.string.schedule_cd_delete_legacy)) { onDelete(name, rule.ruleId) }
            },
        ) {
            RowTitle(name)
            RowStatus(stringResource(R.string.schedule_legacy_rule_summary, rule.advanceMinutes), error = true)
        }
    }
}

/** Top-level page section: heading, one-line purpose, then content. */
@Composable
private fun ReminderSection(
    title: String,
    description: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .semantics { heading() },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun SubsectionLabel(title: String, description: String?) {
    Column(
        modifier = Modifier
            .padding(start = 4.dp, end = 4.dp, top = 8.dp)
            .semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shared list row: the card itself is the edit target; trailing holds switch/delete. */
@Composable
private fun ReminderRowCard(
    onClick: (() -> Unit)?,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val color = MaterialTheme.colorScheme.surfaceVariant
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
            trailing()
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, color = color) { body() }
    } else {
        Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = color) { body() }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun RowSupporting(text: String, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
    )
}

@Composable
private fun RowStatus(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EmptyHint(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    AppOutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun DeleteCircleButton(description: String, onClick: () -> Unit) {
    AppCircleIconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Collapsible group for rarely used options; the summary says what is inside. */
@Composable
fun ReminderExpandableSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "expand")
    val state = stringResource(if (expanded) R.string.schedule_state_expanded else R.string.schedule_state_collapsed)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = state },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).rotate(rotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun ReminderRule.conditionSummary(): String {
    val exists = stringResource(R.string.schedule_reminder_presence_exists)
    val absent = stringResource(R.string.schedule_reminder_presence_absent)
    val empty = stringResource(R.string.schedule_no_condition)
    val separator = stringResource(R.string.schedule_summary_separator)
    return labelConditions.joinToString(separator) { condition ->
        val presence = when (condition.presence) {
            ReminderLabelPresence.Exists -> exists
            ReminderLabelPresence.Absent -> absent
        }
        "${condition.slotLabel} $presence"
    }.ifBlank { empty }
}

@Composable
private fun ReminderRule.actionSummary(): String {
    val remind = stringResource(R.string.schedule_reminder_action_remind)
    val skip = stringResource(R.string.schedule_reminder_action_skip)
    val empty = stringResource(R.string.schedule_no_action)
    val separator = stringResource(R.string.schedule_summary_separator)
    return labelActions.joinToString(separator) { action ->
        val type = when (action.action) {
            ReminderLabelActionType.Remind -> remind
            ReminderLabelActionType.Skip -> skip
        }
        "${action.slotLabel} $type"
    }.ifBlank { empty }
}

private fun formatAlarmTime(millis: Long, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone))

@Composable
private fun alarmDayHeader(date: LocalDate, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val day = when (date) {
        today -> stringResource(R.string.schedule_day_today)
        today.plusDays(1) -> stringResource(R.string.schedule_day_tomorrow)
        else -> "${date.monthValue}/${date.dayOfMonth}"
    }
    return "$day · $weekday"
}
