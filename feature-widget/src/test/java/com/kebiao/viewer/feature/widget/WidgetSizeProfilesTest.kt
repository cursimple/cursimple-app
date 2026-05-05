package com.kebiao.viewer.feature.widget

import com.kebiao.viewer.core.kernel.model.ClassSlotTime
import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.CourseTimeSlot
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WidgetSizeProfilesTest {
    @Test
    fun `day offset labels support unbounded daily switching`() {
        assertEquals("昨天", WidgetDayLabels.tag(-1))
        assertEquals("今天", WidgetDayLabels.tag(0))
        assertEquals("明天", WidgetDayLabels.tag(1))
        assertEquals("+7天", WidgetDayLabels.tag(7))
        assertEquals("-8天", WidgetDayLabels.tag(-8))
    }

    @Test
    fun `empty labels stay date aware`() {
        assertEquals("昨日没有课程", WidgetDayLabels.empty(-1))
        assertEquals("今日没有课程，享受一天", WidgetDayLabels.empty(0))
        assertEquals("明日没有课程", WidgetDayLabels.empty(1))
        assertEquals("当日没有课程", WidgetDayLabels.empty(5))
    }

    @Test
    fun `size profiles distinguish compact regular and expanded widgets`() {
        assertEquals(WidgetSizeClass.Compact, WidgetSizeClass.fromDp(widthDp = 160, heightDp = 110))
        assertEquals(WidgetSizeClass.Compact, WidgetSizeClass.fromDp(widthDp = 160, heightDp = 120))
        assertEquals(WidgetSizeClass.Regular, WidgetSizeClass.fromDp(widthDp = 220, heightDp = 180))
        assertEquals(WidgetSizeClass.Expanded, WidgetSizeClass.fromDp(widthDp = 300, heightDp = 260))
    }

    @Test
    fun `widgets advance to next day at night only after current day is done`() {
        val profile = TermTimingProfile(
            termStartDate = "2026-02-23",
            slotTimes = listOf(ClassSlotTime(1, 2, "08:00", "09:35")),
        )
        val course = CourseItem(
            id = "math",
            title = "高等数学",
            time = CourseTimeSlot(dayOfWeek = 1, startNode = 1, endNode = 2),
        )

        assertFalse(shouldShowNextDayAtNight(LocalTime.of(21, 59), listOf(course), profile))
        assertTrue(shouldShowNextDayAtNight(LocalTime.of(22, 0), listOf(course), profile))
        assertTrue(shouldShowNextDayAtNight(LocalTime.of(22, 0), emptyList(), profile))
    }
}
