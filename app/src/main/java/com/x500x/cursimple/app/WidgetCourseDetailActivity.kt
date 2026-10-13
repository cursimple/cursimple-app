package com.x500x.cursimple.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.x500x.cursimple.R
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.note.resolveCourseNotes
import com.x500x.cursimple.core.kernel.model.*
import com.x500x.cursimple.feature.schedule.CourseDetailDialog
import com.x500x.cursimple.feature.widget.WidgetDeepLinks
import com.x500x.cursimple.feature.widget.WidgetCourseDetailSelection
import com.x500x.cursimple.feature.widget.widgetCourseDetailsForSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Transparent launcher task hosts the same course dialog with all mutations disabled. */
class WidgetCourseDetailActivity : ComponentActivity() {
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
        val date = intent.getStringExtra(WidgetDeepLinks.EXTRA_OPEN_SCHEDULE_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val start = intent.getIntExtra(WidgetDeepLinks.EXTRA_COURSE_START_NODE, 0)
        val end = intent.getIntExtra(WidgetDeepLinks.EXTRA_COURSE_END_NODE, 0)
        val requestedTerm = intent.getStringExtra(WidgetDeepLinks.EXTRA_COURSE_TERM_ID).orEmpty()
        if (date == null || start !in 1..32 || end !in start..32) { finish(); return }
        val container = (application as ClassScheduleApplication).appContainer
        setContent {
            val theme by container.userPreferencesRepository.preferencesFlow.collectAsState(initial = UserPreferences())
            var details by remember { mutableStateOf<DetailSnapshot?>(null) }
            var loaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                details = withContext(Dispatchers.IO) { runCatching {
                    val termId = container.termProfileRepository.activeTermIdFlow.first { it.isNotBlank() }
                    if (requestedTerm.isNotBlank() && requestedTerm != termId) return@runCatching null
                    val term = container.termProfileRepository.termsFlow.first().firstOrNull { it.id == termId }
                    val prefs = container.userPreferencesRepository.preferencesFlow.first()
                    val timing = container.widgetPreferencesRepository.timingProfileFlow.first()
                    val termStart = term?.termStartDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?: timing?.termStartLocalDate() ?: prefs.termStartDate
                    val schedule = container.scheduleRepository.scheduleFlow.first()
                    val manual = container.manualCourseRepository.manualCoursesFlow.first()
                    val all = schedule.allCoursesWith(manual)
                    val selected = widgetCourseDetailsForSlot(date, start, end, all, termStart, prefs.temporaryScheduleOverrides, prefs.holidayCalendar)
                    val notes = resolveCourseNotes(all, container.courseNoteRepository.courseNotesFlow.first()).byCourseId.mapValues { it.value.text }
                    if (container.termProfileRepository.activeTermId() != termId) return@runCatching null
                    DetailSnapshot(selected, all, timing, termStart, prefs, manual.mapTo(mutableSetOf()) { it.id },
                        schedule?.dailySchedules.orEmpty().flatMap { it.courses }.mapTo(mutableSetOf()) { it.id },
                        DEFAULT_TERM_WEEK_COUNT + (term?.extraWeekCount ?: 0),
                        notes)
                }.getOrNull() }
                loaded = true
            }
            ClassScheduleTheme(theme.themeMode, theme.themeAccent, theme.themeCustomColorArgb) {
                val info = details
                if (info != null && info.selection.courses.isNotEmpty()) {
                    CourseDetailDialog(courses = info.selection.courses, timingProfile = info.timing,
                        visibleWeekNumber = info.selection.week, targetDate = date, dayIsHoliday = info.selection.onHoliday,
                        sourceWeekOf = { info.selection.sourceWeeks[it] }, isManual = { it.id in info.manualIds },
                        isPluginOverride = { it.id in info.pluginIds }, maxWeekCount = info.weekCount, sessionWeekCount = info.weekCount,
                        noteTextOf = { info.notes[it.id].orEmpty() }, existingCourses = info.allCourses,
                        termStartDate = info.termStart, temporaryScheduleOverrides = info.prefs.temporaryScheduleOverrides,
                        holidayCalendar = info.prefs.holidayCalendar, countHolidays = info.prefs.scheduleDisplay.courseSessionCountIncludeHolidays,
                        onDismiss = ::finish, onSetReminder = {}, onDelete = {}, readOnly = true)
                } else {
                    AlertDialog(onDismissRequest = ::finish,
                        title = { Text(stringResource(R.string.widget_course_detail_title)) },
                        text = { Text(stringResource(if (loaded) R.string.widget_course_detail_changed else R.string.widget_course_detail_loading)) },
                        confirmButton = { TextButton(onClick = ::finish) { Text(stringResource(android.R.string.ok)) } })
                }
            }
        }
    }
}

private data class DetailSnapshot(val selection: WidgetCourseDetailSelection, val allCourses: List<CourseItem>,
    val timing: TermTimingProfile?, val termStart: LocalDate?, val prefs: UserPreferences, val manualIds: Set<String>,
    val pluginIds: Set<String>, val weekCount: Int, val notes: Map<String, String>)
