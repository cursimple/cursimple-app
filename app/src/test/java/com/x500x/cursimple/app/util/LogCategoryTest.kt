package com.x500x.cursimple.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Test prefix classification without Context-dependent write filtering. */
class LogCategoryTest {
    @Test
    fun `class notice events are notice`() {
        assertEquals(LogCategory.Notice, LogCategory.of("class_notice.deliver.failure"))
    }

    @Test
    fun `alarm pre notice events are notice not reminder`() {
        assertEquals(LogCategory.Notice, LogCategory.of("alarm_pre_notice.schedule"))
    }

    @Test
    fun `reminder events are reminder`() {
        assertEquals(LogCategory.Reminder, LogCategory.of("reminder.sync"))
    }

    @Test
    fun `widget events are widget`() {
        assertEquals(LogCategory.Widget, LogCategory.of("widget.update"))
    }

    @Test
    fun `unknown events fall back to general`() {
        assertEquals(LogCategory.General, LogCategory.of("app.lifecycle"))
    }

    @Test
    fun `prefix must include the dot`() {
        assertEquals(LogCategory.General, LogCategory.of("widgets_refresh"))
    }
}
