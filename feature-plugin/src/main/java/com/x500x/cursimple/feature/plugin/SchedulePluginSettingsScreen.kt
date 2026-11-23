package com.x500x.cursimple.feature.plugin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.x500x.cursimple.core.data.*
import com.x500x.cursimple.core.data.term.DataStoreTermProfileRepository
import com.x500x.cursimple.core.data.widget.DataStoreWidgetPreferencesRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.allCoursesWith
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import com.x500x.cursimple.core.plugin.ui.PluginTimetableRowSetting
import com.x500x.cursimple.core.plugin.ui.PluginUiSchema
import com.x500x.cursimple.feature.plugin.ui.AppOutlinedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal fun localizedTimetableRowTitle(row: PluginTimetableRowSetting, language: String): String {
    val key = when {
        language.startsWith("zh", true) && (language.contains("TW", true) || language.contains("HK", true) || language.contains("Hant", true)) -> "zh-TW"
        language.startsWith("zh", true) -> "zh-CN"
        else -> "en"
    }
    return row.titles[key]?.takeIf(String::isNotBlank) ?: row.title
}

private data class TimetableSettingsCourses(val termId: String, val termName: String, val schedule: TermSchedule?, val courses: List<CourseItem>, val start: LocalDate?)

/** Declarative settings for timetable importers; no school-specific rules live in the host. */
@Composable
internal fun SchedulePluginSettingsScreen(record: InstalledPluginRecord, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { DataStoreUserPreferencesRepository(context) }
    val terms = remember(context) { DataStoreTermProfileRepository(context) }
    val schedules = remember(context, terms) { DataStoreScheduleRepository(context, terms) }
    val prefs by repository.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())
    val catalogFlow = remember(context) {
        val activeTerm = combine(terms.termsFlow, terms.activeTermIdFlow) { profiles, id ->
            id to profiles.firstOrNull { it.id == id }
        }
        combine(schedules.scheduleFlow,
            DataStoreManualCourseRepository(context, terms).manualCoursesFlow, activeTerm) { schedule, manual, active ->
                TimetableSettingsCourses(active.first, active.second?.name.orEmpty(), schedule, schedule.allCoursesWith(manual),
                    active.second?.termStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            }
            .flowOn(Dispatchers.IO)
    }
    val courses by catalogFlow.collectAsStateWithLifecycle(initialValue = null)
    val timingRepository = remember(context) { DataStoreWidgetPreferencesRepository(context) }
    val timing by timingRepository.timingProfileFlow.collectAsStateWithLifecycle(initialValue = null)
    var schema by remember(record.packageRevision) { mutableStateOf<PluginUiSchema?>(null) }
    var loadFailed by remember(record.packageRevision) { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var bindingBusy by remember { mutableStateOf(false) }
    val scope = LocalLifecycleOwner.current.lifecycleScope
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(record.packageRevision) {
        val result = runCatching { withContext(Dispatchers.IO) { PluginFileStore(context).loadUiSchema(record) } }
        schema = result.getOrNull()
        loadFailed = result.isFailure
    }
    val applicable = remember(record.pluginId, schema, courses?.schedule, timing) {
        schema?.let { pluginTimetableRowsApply(record.pluginId, it, courses?.schedule, timing) } == true
    }
    val legacyBound = courses?.schedule?.let { it.sourcePluginId == null && it.legacyDisplayPluginId == record.pluginId } == true
    val canBindLegacy = remember(record.pluginId, schema, courses?.schedule, timing) {
        schema?.let { canBindLegacyTimetableDisplayPlugin(record.pluginId, it, courses?.schedule, timing) } == true
    }
    Column(modifier.fillMaxSize().testTag("schedule-plugin-settings")) {
        MarketDetailTopBar(stringResource(R.string.plugin_timetable_settings_title), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = record.name,
                modifier = Modifier.padding(horizontal = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.4.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (loadFailed) Text(stringResource(R.string.plugin_timetable_load_failed), color = MaterialTheme.colorScheme.error)
            if (saveFailed) Text(stringResource(R.string.plugin_timetable_save_failed), color = MaterialTheme.colorScheme.error)
            if (legacyBound || canBindLegacy) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.plugin_timetable_legacy_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 19.5.sp),
                            fontWeight = FontWeight.Bold,
                        )
                        val termName = courses?.termName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.plugin_timetable_current_term)
                        Text(
                            stringResource(if (legacyBound) R.string.plugin_timetable_legacy_enabled else R.string.plugin_timetable_legacy_confirm, termName),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 20.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val toggleBinding = {
                            val snapshot = courses
                            val definition = schema
                            val expected = snapshot?.schedule
                            val profile = timing
                            val next = !legacyBound
                            if (snapshot != null && definition != null && expected != null) {
                                bindingBusy = true
                                scope.launch {
                                    try {
                                        saveFailed = runCatching { withContext(Dispatchers.IO) {
                                            schedules.setLegacyTimetableDisplayPlugin(snapshot.termId, expected, record.pluginId, definition, profile, next)
                                        } }.getOrDefault(false).not()
                                    } finally { bindingBusy = false }
                                }
                            }
                        }
                        val buttonEnabled = prefs.loaded && !bindingBusy
                        val buttonLabel = stringResource(when {
                            bindingBusy -> R.string.plugin_timetable_legacy_saving
                            legacyBound -> R.string.plugin_timetable_legacy_disable
                            else -> R.string.plugin_timetable_legacy_enable
                        })
                        val buttonModifier = Modifier.fillMaxWidth().height(38.dp)
                        if (legacyBound) {
                            AppOutlinedButton(onClick = toggleBinding, enabled = buttonEnabled, modifier = buttonModifier,
                                shape = RoundedCornerShape(19.dp), border = BorderStroke(1.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (buttonEnabled) 0.35f else 0.12f)),
                                contentPadding = PaddingValues(horizontal = 16.dp)) {
                                SettingsButtonLabel(buttonLabel)
                            }
                        } else {
                            Button(onClick = toggleBinding, enabled = buttonEnabled, modifier = buttonModifier,
                                shape = RoundedCornerShape(19.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                                SettingsButtonLabel(buttonLabel)
                            }
                        }
                    }
                }
            }
            val rows = validPluginTimetableRows(schema?.timetableRows.orEmpty())
            if (rows.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.plugin_timetable_display_settings),
                        modifier = Modifier.padding(horizontal = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.4.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            rows.forEachIndexed { index, row ->
                                val occupied = remember(row, timing, courses, prefs.temporaryScheduleOverrides, prefs.termStartDate) {
                                    pluginRowHasCourses(row, courses?.courses.orEmpty(), prefs.temporaryScheduleOverrides, courses?.start ?: prefs.termStartDate)
                                }
                                val visible = !applicable || occupied || (prefs.pluginTimetableRowVisibility[record.pluginId]?.get(row.id) ?: row.defaultVisible)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Rounded.TableRows, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            localizedTimetableRowTitle(row, language),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.2.sp),
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            stringResource(when {
                                                !applicable -> R.string.plugin_timetable_row_not_applicable
                                                occupied -> R.string.plugin_timetable_row_occupied
                                                else -> R.string.plugin_timetable_row_empty
                                            }),
                                            modifier = Modifier.padding(top = 2.dp),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.7.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(checked = visible, enabled = prefs.loaded && applicable && !occupied,
                                        onCheckedChange = { next ->
                                            scope.launch { saveFailed = runCatching { repository.setPluginTimetableRowVisible(record.pluginId, row.id, next) }.isFailure }
                                        }, modifier = Modifier.testTag("plugin-row-${row.id}"))
                                }
                                if (index != rows.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 64.dp, end = 14.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 13/700 label inside the 38dp page buttons. */
@Composable
private fun SettingsButtonLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 17.sp),
        fontWeight = FontWeight.Bold)
}
