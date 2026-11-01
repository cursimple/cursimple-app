package com.x500x.cursimple.app

import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.data.term.TermProfile
import com.x500x.cursimple.core.data.term.TermProfileRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 合并学期把两个学期的课表合成一个新学期，源学期原样保留。 */
class TermMergeOrchestrationTest {

    private class FakeTerms : TermProfileRepository {
        val list = MutableStateFlow(
            listOf(
                TermProfile("a", "甲", "2026-02-23", createdAt = 1L),
                TermProfile("b", "乙", "2026-03-09", createdAt = 2L),
            ),
        )
        private val active = MutableStateFlow("a")
        override val termsFlow: Flow<List<TermProfile>> = list
        override val activeTermIdFlow: Flow<String> = active
        override suspend fun activeTermId() = active.value
        override suspend fun createTerm(name: String, termStartDateIso: String?): TermProfile {
            val term = TermProfile("merged", name, termStartDateIso, createdAt = 3L)
            list.value = list.value + term
            return term
        }
        override suspend fun renameTerm(id: String, name: String) = Unit
        override suspend fun setTermStartDate(id: String, dateIso: String?) {
            list.value = list.value.map { if (it.id == id) it.copy(termStartDate = dateIso) else it }
        }
        override suspend fun setTermTimingProfile(id: String, timingProfileId: String?) = Unit
        override suspend fun setTermArchived(id: String, archived: Boolean) = Unit

        override suspend fun setTermExtraWeekCount(id: String, extraWeekCount: Int) = Unit
        override suspend fun adjustActiveTermExtraWeekCount(delta: Int) = 0
        override suspend fun deleteTerm(id: String) = Unit
        override suspend fun setActiveTerm(id: String) { active.value = id }
        override suspend fun ensureBootstrapped(defaultName: String, legacyTermStartDateIso: String?) = active.value
    }

    private class FakeStore : TermMergeStore {
        val schedules = mutableMapOf<String, TermSchedule>()
        val courses = mutableMapOf<String, List<CourseItem>>()
        val events = mutableMapOf<String, List<ScheduleEvent>>()
        var writtenTo: String? = null

        override suspend fun scheduleOf(termId: String) = schedules[termId]
        override suspend fun coursesOf(termId: String) = courses[termId].orEmpty()
        override suspend fun eventsOf(termId: String) = events[termId].orEmpty()
        override suspend fun saveSchedule(termId: String, schedule: TermSchedule) {
            writtenTo = termId
            schedules[termId] = schedule
        }
        override suspend fun saveCourses(termId: String, courses: List<CourseItem>) {
            writtenTo = termId
            this.courses[termId] = courses
        }
        override suspend fun saveEvents(termId: String, events: List<ScheduleEvent>) {
            writtenTo = termId
            this.events[termId] = events
        }
    }

    private fun course(id: String, weeks: List<Int>) = CourseItem(
        id = id, title = id, weeks = weeks, time = CourseTimeSlot(3, 1, 2),
    )

    private fun schedule(termId: String, courses: List<CourseItem>) =
        TermSchedule(termId, "2026-03-01", listOf(DailySchedule(3, courses)))

    @Test
    fun `merging two terms writes one new term and leaves the sources alone`() = runBlocking {
        val terms = FakeTerms()
        val store = FakeStore().apply {
            schedules["a"] = schedule("a", listOf(course("a1", listOf(1))))
            schedules["b"] = schedule("b", listOf(course("b1", listOf(1))))
            events["a"] = listOf(ScheduleEvent("ea", "甲的会", "2026-03-02", "12:00", "13:00"))
        }
        val merger = TermMerger(terms, store)

        val mergedId = merger.merge(listOf("a", "b"), "甲乙合并")

        assertEquals("merged", mergedId)
        assertEquals("merged", store.writtenTo)
        val merged = store.schedules.getValue("merged")
        assertEquals(listOf(1), merged.coursesOfDay(3).first { it.id == "a1" }.weeks)
        assertEquals(listOf(3), merged.coursesOfDay(3).first { it.id == "b1" }.weeks)
        assertEquals("2026-02-23", terms.list.value.first { it.id == "merged" }.termStartDate)
        // 源学期保留原样，合并只是多出一份。
        assertEquals(listOf(1), store.schedules.getValue("b").coursesOfDay(3).single().weeks)
        assertEquals(listOf("ea"), store.events.getValue("merged").map { it.id })
    }

    @Test
    fun `one selected term is not a merge`() = runBlocking {
        val store = FakeStore()
        val merger = TermMerger(FakeTerms(), store)

        assertEquals(null, merger.merge(listOf("a"), "只有一份"))
        assertEquals(null, store.writtenTo)
    }

    @Test
    fun `manual courses and per-week overrides travel with the merge`() = runBlocking {
        val store = FakeStore().apply {
            schedules["a"] = schedule("a", listOf(course("a1", listOf(1))))
            courses["b"] = listOf(course("m1", listOf(1)))
        }
        val merger = TermMerger(FakeTerms(), store)

        merger.merge(listOf("a", "b"), "甲乙合并")

        assertEquals(listOf(3), store.courses.getValue("merged").single().weeks)
        assertTrue(store.schedules.containsKey("merged"))
    }

    @Test
    fun `merging with ByWeekNumber alignment delegates correctly`() = runBlocking {
        val terms = FakeTerms()
        val store = FakeStore().apply {
            schedules["a"] = schedule("a", listOf(course("a1", listOf(1))))
            schedules["b"] = schedule("b", listOf(course("b1", listOf(1))))
        }
        val merger = TermMerger(terms, store)

        merger.merge(
            sourceIds = listOf("a", "b"),
            name = "按周次合并",
            alignment = com.x500x.cursimple.core.kernel.model.TermMergeAlignment.ByWeekNumber,
        )

        val merged = store.schedules.getValue("merged")
        // In ByWeekNumber mode, week shifts are 0, so b1 stays at week 1
        assertEquals(listOf(1), merged.coursesOfDay(3).first { it.id == "a1" }.weeks)
        assertEquals(listOf(1), merged.coursesOfDay(3).first { it.id == "b1" }.weeks)
    }

    @Test
    fun `merging with explicit anchor term sets start date and active term`() = runBlocking {
        val terms = FakeTerms()
        val store = FakeStore().apply {
            schedules["a"] = schedule("a", listOf(course("a1", listOf(1))))
            schedules["b"] = schedule("b", listOf(course("b1", listOf(1))))
        }
        val merger = TermMerger(terms, store)

        merger.merge(
            sourceIds = listOf("a", "b"),
            name = "以乙为基准",
            alignment = com.x500x.cursimple.core.kernel.model.TermMergeAlignment.ByRealDate,
            anchorTermId = "b",
        )

        assertEquals("2026-03-09", terms.list.value.first { it.id == "merged" }.termStartDate)
    }
}
