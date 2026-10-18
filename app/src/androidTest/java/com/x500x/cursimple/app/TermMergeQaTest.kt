package com.x500x.cursimple.app

import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.coursesOfDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 合并学期走真实存储：新学期拿到按日期对齐的课表、占位课和事务，源学期不动。 */
class TermMergeQaTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ClassScheduleApplication).appContainer
    private val terms get() = container.termProfileRepository
    private val created = mutableListOf<String>()
    private var previousActive: String? = null

    private fun course(id: String, weeks: List<Int>) =
        CourseItem(id = id, title = id, weeks = weeks, time = CourseTimeSlot(3, 1, 2))

    private suspend fun seedTerm(name: String, start: String, courseId: String, manualId: String, eventId: String): String {
        val id = terms.createTerm(name, start).id.also { created += it }
        terms.setActiveTerm(id)
        container.scheduleRepository.saveSchedule(
            TermSchedule(id, "2026-03-01", listOf(DailySchedule(3, listOf(course(courseId, listOf(1, 2)))))),
        )
        container.manualCourseRepository.replaceAll(listOf(course(manualId, listOf(1))))
        container.scheduleEventRepository.upsert(ScheduleEvent(eventId, eventId, "2026-03-11", "12:00", "13:00"))
        return id
    }

    @After
    fun restore() = runBlocking {
        previousActive?.let { terms.setActiveTerm(it) }
        created.forEach { terms.deleteTerm(it) }
    }

    @Test
    fun mergedTermAlignsByDateAndLeavesSourcesAlone() = runBlocking {
        previousActive = terms.activeTermId()
        val a = seedTerm("QA 合并甲", "2026-02-23", "qa-merge-a", "qa-merge-ma", "qa-merge-ea")
        val b = seedTerm("QA 合并乙", "2026-03-09", "qa-merge-b", "qa-merge-mb", "qa-merge-eb")

        val mergedId = container.termMerger.merge(listOf(a, b), "QA 合并结果")
        assertNotNull(mergedId)
        created += mergedId!!

        assertEquals(mergedId, terms.activeTermId())
        val merged = terms.termsFlow.first().first { it.id == mergedId }
        assertEquals("2026-02-23", merged.termStartDate)

        val schedule = container.scheduleRepository.scheduleFlow.first()!!
        assertEquals(listOf(1, 2), schedule.coursesOfDay(3).first { it.id == "qa-merge-a" }.weeks)
        assertEquals(listOf(3, 4), schedule.coursesOfDay(3).first { it.id == "qa-merge-b" }.weeks)
        val manual = container.manualCourseRepository.manualCoursesFlow.first().associate { it.id to it.weeks }
        assertEquals(mapOf("qa-merge-ma" to listOf(1), "qa-merge-mb" to listOf(3)), manual)
        assertEquals(
            setOf("qa-merge-ea", "qa-merge-eb"),
            container.scheduleEventRepository.eventsFlow.first().map { it.id }.toSet(),
        )

        terms.setActiveTerm(b)
        val source = container.scheduleRepository.scheduleFlow.first()!!
        assertEquals("源学期周次不变", listOf(1, 2), source.coursesOfDay(3).single().weeks)
    }
}
