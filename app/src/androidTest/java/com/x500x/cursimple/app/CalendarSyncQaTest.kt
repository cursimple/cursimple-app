package com.x500x.cursimple.app

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.util.*
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Uses disposable local calendars on the QA emulator, never the user's calendars. */
class CalendarSyncQaTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get() = context.contentResolver
    private val calendarIds = mutableListOf<Long>()
    private var previous: SystemCalendarExportRecord? = null
    private val date = LocalDate.of(2026, 9, 7)
    private val zone = ZoneId.of("Asia/Shanghai")

    @Before fun prepare() {
        assertTrue("Calendar permissions must be granted on the QA emulator", SystemCalendarExporter.hasWritePermission(context))
        previous = SystemCalendarExportStore.read(context)
        SystemCalendarExportStore.clear(context)
    }

    @After fun cleanUp() {
        calendarIds.forEach { id -> resolver.delete(syncUri(CalendarContract.Calendars.CONTENT_URI).buildUpon().appendPath(id.toString()).build(), null, null) }
        SystemCalendarExportStore.clear(context)
        previous?.let { SystemCalendarExportStore.record(context, it.calendarId, it.eventIds) }
    }

    private fun syncUri(uri: android.net.Uri) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "cursimple-pre-qa")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()

    private fun calendar(): Long {
        val uri = resolver.insert(syncUri(CalendarContract.Calendars.CONTENT_URI), ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "cursimple-pre-qa")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "pre-regression-${System.nanoTime()}")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Pre regression fixture")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xff336699.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "cursimple-pre-qa")
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, zone.id)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        })!!
        return ContentUris.parseId(uri).also(calendarIds::add)
    }

    private fun plan(title: String = "高数", recurring: Boolean = true): SchedulePlan {
        val course = CourseItem("pre-sync-fixture", title, location = "一教101", time = CourseTimeSlot(1, 1, 2))
        val dates = if (recurring) listOf(date, date.plusWeeks(1)) else listOf(date)
        return SchedulePlan(listOf(PlannedCourse(course, null, dates.map {
            CourseOccurrence(it, it.atTime(8, 0), it.atTime(9, 40), false)
        })), emptyList(), null)
    }

    private fun sync(id: Long, plan: SchedulePlan = plan()): SystemCalendarWriteResult.Success {
        val result = SystemCalendarExporter.sync(context, id, plan, zone)
        assertTrue("sync failed: $result", result is SystemCalendarWriteResult.Success)
        return result as SystemCalendarWriteResult.Success
    }

    @Test fun repeatUpdateRecurrenceAndRemovalKeepStableIds() {
        val id = calendar()
        assertEquals(1, sync(id).inserted)
        val first = SystemCalendarExporter.queryCurSimpleEvents(context, id).single().id
        val repeated = sync(id)
        assertEquals(0, repeated.inserted + repeated.updated + repeated.deleted)
        assertEquals(1, sync(id, plan("新课程名", recurring = false)).updated)
        val updated = SystemCalendarExporter.queryCurSimpleEvents(context, id).single()
        assertEquals(first, updated.id)
        assertNull(updated.rrule)
        assertNotNull(updated.dtend)
        assertEquals(1, sync(id, SchedulePlan(emptyList(), emptyList(), null)).deleted)
        assertTrue(SystemCalendarExporter.queryCurSimpleEvents(context, id).isEmpty())
    }

    @Test fun duplicateUidIsReconciledToOneEvent() {
        val id = calendar()
        sync(id)
        val original = SystemCalendarExporter.queryCurSimpleEvents(context, id).single()
        resolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, id)
            put(CalendarContract.Events.TITLE, original.title)
            put(CalendarContract.Events.DESCRIPTION, original.description)
            put(CalendarContract.Events.DTSTART, original.dtstart)
            put(CalendarContract.Events.DTEND, original.dtstart + 600000)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        })!!
        assertEquals(2, SystemCalendarExporter.queryCurSimpleEvents(context, id).size)
        sync(id)
        assertEquals("Repeated UIDs must not leave duplicate notifications", 1, SystemCalendarExporter.queryCurSimpleEvents(context, id).size)
    }

    @Test fun invalidDuplicatePlanPreservesExistingEventsAndUndoRecord() {
        val id = calendar()
        sync(id)
        val before = SystemCalendarExporter.queryCurSimpleEvents(context, id)
        val record = SystemCalendarExportStore.read(context)
        val valid = plan()
        val invalid = SchedulePlan(valid.courses + valid.courses, emptyList(), null)
        assertTrue(SystemCalendarExporter.sync(context, id, invalid, zone) is SystemCalendarWriteResult.Failed)
        assertEquals(before, SystemCalendarExporter.queryCurSimpleEvents(context, id))
        assertEquals(record, SystemCalendarExportStore.read(context))
    }

    @Test fun clearingSelectedCalendarPreservesOtherCalendarAndItsUndoRecord() {
        val first = calendar()
        val second = calendar()
        sync(first)
        sync(second)
        SystemCalendarExporter.clearAll(context, first)
        assertTrue(SystemCalendarExporter.queryCurSimpleEvents(context, first).isEmpty())
        assertEquals("Clearing A must preserve the previously synchronized B", 1, SystemCalendarExporter.queryCurSimpleEvents(context, second).size)
        assertEquals(second, SystemCalendarExportStore.read(context)?.calendarId)
    }
}
