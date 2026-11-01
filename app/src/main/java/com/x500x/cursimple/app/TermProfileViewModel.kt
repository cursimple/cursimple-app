package com.x500x.cursimple.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.data.term.TermProfile
import com.x500x.cursimple.core.data.attendance.AttendanceData
import com.x500x.cursimple.core.data.term.TermProfileRepository
import com.x500x.cursimple.core.data.term.planTermCopy
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.DEFAULT_TERM_WEEK_COUNT
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.feature.schedule.TermStats
import com.x500x.cursimple.feature.schedule.computeTermStats
import com.x500x.cursimple.feature.schedule.termLastWeek
import com.x500x.cursimple.core.data.term.termStartDateIsoOf
import com.x500x.cursimple.core.kernel.model.TermMergeAlignment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TermProfileUiState(
    val terms: List<TermProfile> = emptyList(),
    val activeTermId: String = "",
)

/** What one term holds, read for copying and statistics. Courses are the timetable's merged list. */
data class TermSnapshot(
    val termStart: LocalDate?,
    val courses: List<CourseItem>,
    val schedule: TermSchedule?,
    val timingProfileId: String?,
    val extraWeeks: Int,
)

class TermProfileViewModel(
    private val termRepo: TermProfileRepository,
    private val userPrefs: UserPreferencesRepository,
    private val onActiveTermChanged: suspend () -> Unit = {},
    private val merger: (suspend (ids: List<String>, name: String, alignment: TermMergeAlignment, anchorTermId: String?) -> String?)? = null,
    private val readTerm: suspend (termId: String) -> TermSnapshot? = { null },
    /** Writes a term's manual courses and synced timetable structure; the new term is never the active one until chosen. */
    private val writeTerm: suspend (termId: String, manualCourses: List<CourseItem>, schedule: TermSchedule?) -> Unit = { _, _, _ -> },
    private val attendance: suspend () -> AttendanceData = { AttendanceData() },
) : ViewModel() {

    /** Copy [sourceId] into a new term. Returns the new term's id, or null if the source is missing. */
    fun copyTerm(
        sourceId: String,
        name: String,
        startDate: LocalDate?,
        keepReminders: Boolean,
        onCopied: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val source = readTerm(sourceId) ?: return@launch
            val created = termRepo.createTerm(name = name, termStartDateIso = startDate?.toString())
            source.timingProfileId?.let { termRepo.setTermTimingProfile(created.id, it) }
            termRepo.setTermExtraWeekCount(created.id, source.extraWeeks)
            val plan = planTermCopy(source.courses, source.schedule, keepReminders, created.id)
            writeTerm(created.id, plan.manualCourses, plan.schedule)
            onCopied(name)
        }
    }

    /** Statistics for any term, not only the active one. [onResult] gets null when the term has no start date. */
    fun loadStats(termId: String, onResult: (TermStats?) -> Unit) {
        viewModelScope.launch {
            val snapshot = readTerm(termId)
            val stats = snapshot?.let {
                val lastWeek = termLastWeek(it.courses, it.extraWeeks, DEFAULT_TERM_WEEK_COUNT)
                computeTermStats(
                    courses = it.courses,
                    termStart = it.termStart,
                    today = com.x500x.cursimple.core.kernel.time.BeijingTime.nowDateTime().toLocalDate(),
                    lastWeek = lastWeek,
                    overrides = emptyList(),
                    holidayCalendar = HolidayCalendarSettings.NONE,
                    attendance = attendance(),
                )
            }
            onResult(stats)
        }
    }

    fun setArchived(id: String, archived: Boolean) {
        viewModelScope.launch { termRepo.setTermArchived(id, archived) }
    }

    val state: StateFlow<TermProfileUiState> = combine(
        termRepo.termsFlow,
        termRepo.activeTermIdFlow,
    ) { terms, activeId ->
        TermProfileUiState(terms = terms.sortedBy { it.createdAt }, activeTermId = activeId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TermProfileUiState())

    fun createTerm(name: String, startDate: LocalDate?) {
        viewModelScope.launch {
            val term = termRepo.createTerm(name = name, termStartDateIso = startDate?.toString())
            termRepo.setActiveTerm(term.id)
            userPrefs.setTermStartDate(startDate)
            onActiveTermChanged()
        }
    }

    fun renameTerm(id: String, name: String) {
        viewModelScope.launch { termRepo.renameTerm(id, name) }
    }

    fun setStartDate(id: String, date: LocalDate?) {
        viewModelScope.launch {
            termRepo.setTermStartDate(id, date?.toString())
            if (id == state.value.activeTermId) {
                // Treat date clearing as an explicit user choice so plugin sync cannot restore it.
                userPrefs.setTermStartUserDecided(true)
                userPrefs.setTermStartDate(date)
            }
        }
    }

    fun activate(id: String) {
        viewModelScope.launch {
            termRepo.setActiveTerm(id)
            val newActive = termRepo.termsFlow.let { flow ->
                flow.let {
                    state.value.terms.firstOrNull { t -> t.id == id }
                }
            }
            val iso = newActive?.termStartDate
            userPrefs.setTermStartDate(iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            onActiveTermChanged()
        }
    }

    /** 合并后切到新学期，和切换学期一样同步开学日期与节次方案。 */
    fun merge(
        ids: List<String>,
        name: String,
        alignment: TermMergeAlignment = TermMergeAlignment.ByRealDate,
        anchorTermId: String? = null,
        onMerged: (String) -> Unit = {},
    ) {
        val run = merger ?: return
        viewModelScope.launch {
            val mergedId = run(ids, name, alignment, anchorTermId) ?: return@launch
            val iso = termRepo.termsFlow.first().termStartDateIsoOf(mergedId)
            userPrefs.setTermStartDate(iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            onActiveTermChanged()
            onMerged(name)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            termRepo.deleteTerm(id)
            val activeId = termRepo.activeTermIdFlow.first()
            val iso = termRepo.termsFlow.first().termStartDateIsoOf(activeId)
            userPrefs.setTermStartDate(iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            onActiveTermChanged()
        }
    }
}

class TermProfileViewModelFactory(
    private val termRepo: TermProfileRepository,
    private val userPrefs: UserPreferencesRepository,
    private val onActiveTermChanged: suspend () -> Unit = {},
    private val merger: (suspend (ids: List<String>, name: String, alignment: TermMergeAlignment, anchorTermId: String?) -> String?)? = null,
    private val readTerm: suspend (termId: String) -> TermSnapshot? = { null },
    private val writeTerm: suspend (termId: String, manualCourses: List<CourseItem>, schedule: TermSchedule?) -> Unit = { _, _, _ -> },
    private val attendance: suspend () -> AttendanceData = { AttendanceData() },
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TermProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TermProfileViewModel(termRepo, userPrefs, onActiveTermChanged, merger, readTerm, writeTerm, attendance) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
