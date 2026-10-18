package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScheduleItemTypeSelectionTest {

    @Test
    fun `ScheduleItemType provides labels for Course, Exam, and Event`() {
        assertEquals(R.string.schedule_category_course, ScheduleItemType.Course.labelRes)
        assertEquals(R.string.schedule_category_exam, ScheduleItemType.Exam.labelRes)
        assertEquals(R.string.schedule_category_event, ScheduleItemType.Event.labelRes)
    }

    @Test
    fun `CourseActionRequest and QuickAddPositionRequest retain tapped date`() {
        val slot = CourseTimeSlot(dayOfWeek = 3, startNode = 1, endNode = 2)
        val course = CourseItem(id = "c1", title = "Algorithms", time = slot)
        val date = LocalDate.of(2026, 10, 14)

        val actionRequest = CourseActionRequest(course = course, position = slot, date = date)
        assertEquals(course, actionRequest.course)
        assertEquals(slot, actionRequest.position)
        assertEquals(date, actionRequest.date)

        val quickAddRequest = QuickAddPositionRequest(position = actionRequest.position, date = actionRequest.date)
        assertEquals(slot, quickAddRequest.position)
        assertEquals(date, quickAddRequest.date)
    }

    @Test
    fun `event created from slot defaults to correct date and repeat`() {
        val slotDate = LocalDate.of(2026, 10, 14)
        val termStart = LocalDate.of(2026, 9, 2)
        val anchor = eventWeekAnchor(termStart, slotDate)

        val event = ScheduleEvent(
            id = "test-event",
            title = "实验组会",
            date = slotDate.toString(),
            startTime = "08:00",
            endTime = "09:35",
            location = "理科楼 201",
        ).withRepeat(EventRepeat.Once, null, emptyList(), anchor)

        assertEquals("实验组会", event.title)
        assertEquals("2026-10-14", event.date)
        assertEquals("08:00", event.startTime)
        assertEquals("09:35", event.endTime)
        assertEquals("理科楼 201", event.location)
        assertEquals(EventRepeat.Once, event.repeatMode)
    }
}
