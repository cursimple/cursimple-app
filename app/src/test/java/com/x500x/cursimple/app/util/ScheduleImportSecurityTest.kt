package com.x500x.cursimple.app.util

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ScheduleImportSecurityTest {
    @Test fun rejectsOversizedInputAndDeepJsonBeforeParsing() {
        assertTrue(runCatching { ScheduleWakeUpImporter.parse(ByteArrayInputStream(ByteArray(ScheduleImportLimits.MAX_BYTES + 1))) }.isFailure)
        assertTrue(runCatching { ScheduleIcsImporter.parse("x".repeat(ScheduleImportLimits.MAX_BYTES + 1)) }.isFailure)
        assertTrue(runCatching { ScheduleWakeUpImporter.parseJson("[".repeat(65) + "]".repeat(65)) }.isFailure)
    }

    @Test fun zipBombInSkippedDirectoryIsBounded() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use {
            it.putNextEntry(ZipEntry("padding/"))
            it.write(ByteArray(ScheduleImportLimits.MAX_BYTES + 1))
            it.closeEntry()
            it.putNextEntry(ZipEntry("schedule.json")); it.write("[]".toByteArray()); it.closeEntry()
        }
        assertTrue(runCatching { ScheduleWakeUpImporter.parse(output.toByteArray().inputStream()) }.isFailure)
    }

    @Test fun hugeRecurrenceCountAndIntervalCannotExpandUnboundedly() {
        val content = """BEGIN:VCALENDAR
            |BEGIN:VEVENT
            |DTSTART:20261019T080000
            |DTEND:20261019T090000
            |SUMMARY:Math
            |RRULE:FREQ=WEEKLY;COUNT=2147483647;INTERVAL=2147483647
            |END:VEVENT
            |END:VCALENDAR""".trimMargin()
        val courses = ScheduleIcsImporter.parse(content).schedule!!.dailySchedules.flatMap { it.courses }
        assertEquals(1, courses.size)
        assertEquals(listOf(1), courses.single().weeks)
    }

    @Test fun wakeupNodeArithmeticCannotOverflow() {
        val courses = ScheduleWakeUpImporter.parseJson("""[{"name":"Math","startNode":2147483647,"step":2147483647}]""")
            .schedule!!.dailySchedules.flatMap { it.courses }
        assertTrue(courses.single().time.startNode in 1..48)
        assertTrue(courses.single().time.endNode in courses.single().time.startNode..48)
    }
}
