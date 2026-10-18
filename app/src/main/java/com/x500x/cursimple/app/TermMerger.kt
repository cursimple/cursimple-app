package com.x500x.cursimple.app

import com.x500x.cursimple.core.data.term.TermProfileRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.MergedTermSource
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermMergeAlignment
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.mergeTerms
import kotlinx.coroutines.flow.first

/** Per-term reads and writes the merge needs, so the orchestration stays testable off-device. */
interface TermMergeStore {
    suspend fun scheduleOf(termId: String): TermSchedule?
    suspend fun coursesOf(termId: String): List<CourseItem>
    suspend fun eventsOf(termId: String): List<ScheduleEvent>
    suspend fun saveSchedule(termId: String, schedule: TermSchedule)
    suspend fun saveCourses(termId: String, courses: List<CourseItem>)
    suspend fun saveEvents(termId: String, events: List<ScheduleEvent>)
}

/**
 * Fold the selected terms' timetable, placeholder courses and events into one new term, aligned
 * by real calendar date. The sources stay untouched so the merge can be undone by deleting it.
 */
class TermMerger(
    private val terms: TermProfileRepository,
    private val store: TermMergeStore,
) {

    suspend fun merge(
        sourceIds: List<String>,
        name: String,
        alignment: TermMergeAlignment = TermMergeAlignment.ByRealDate,
        anchorTermId: String? = null,
    ): String? {
        val profiles = terms.termsFlow.first().associateBy { it.id }
        val selected = sourceIds.distinct().mapNotNull { profiles[it] }
        if (selected.size < 2) return null

        val sources = selected.map { profile ->
            MergedTermSource(
                termId = profile.id,
                name = profile.name,
                termStartDate = profile.termStartDate,
                schedule = store.scheduleOf(profile.id),
                manualCourses = store.coursesOf(profile.id),
                events = store.eventsOf(profile.id),
            )
        }

        val effectiveAnchorId = anchorTermId
            ?: terms.activeTermId().takeIf { it in sourceIds }
            ?: selected.maxByOrNull { it.termStartDate ?: "" }?.id

        val created = terms.createTerm(name = name, termStartDateIso = null)
        val merged = mergeTerms(
            mergedTermId = created.id,
            sources = sources,
            alignment = alignment,
            anchorTermId = effectiveAnchorId,
        )
        terms.setTermStartDate(created.id, merged.termStartDate)

        merged.schedule?.let { store.saveSchedule(created.id, it) }
        store.saveCourses(created.id, merged.manualCourses)
        store.saveEvents(created.id, merged.events)

        terms.setActiveTerm(created.id)
        return created.id
    }
}

/** Wire the merge store onto the real app stores; events carry no term of their own here. */
class RepositoryTermMergeStore(
    private val scheduleRepository: com.x500x.cursimple.core.data.DataStoreScheduleRepository,
    private val manualCourseRepository: com.x500x.cursimple.core.data.DataStoreManualCourseRepository,
    private val eventRepository: com.x500x.cursimple.core.data.event.DataStoreScheduleEventRepository,
) : TermMergeStore {

    override suspend fun scheduleOf(termId: String): TermSchedule? = scheduleRepository.scheduleFor(termId)

    override suspend fun coursesOf(termId: String): List<CourseItem> = manualCourseRepository.coursesFor(termId)

    override suspend fun eventsOf(termId: String): List<ScheduleEvent> =
        eventRepository.eventsFor(termId)

    override suspend fun saveSchedule(termId: String, schedule: TermSchedule) =
        scheduleRepository.saveScheduleFor(termId, schedule)

    override suspend fun saveCourses(termId: String, courses: List<CourseItem>) =
        manualCourseRepository.replaceAllFor(termId, courses)

    override suspend fun saveEvents(termId: String, events: List<ScheduleEvent>) =
        eventRepository.replaceAllFor(termId, events)
}
