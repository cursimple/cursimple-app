package com.x500x.cursimple.core.data.attendance

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceTest {
    private val math = "高等数学"

    @Test
    fun markingAnotherStatusReplacesTheOldOneForThatDay() {
        val data = AttendanceData()
            .withMark(math, "2026-09-14", AttendanceStatus.Present, 1)
            .withMark(math, "2026-09-14", AttendanceStatus.Absent, 2)
        assertEquals(1, data.records.size)
        assertEquals(AttendanceStatus.Absent, data.statusOf(math, "2026-09-14"))
    }

    @Test
    fun clearingAMarkRemovesIt() {
        val data = AttendanceData().withMark(math, "2026-09-14", AttendanceStatus.Absent, 1).withMark(math, "2026-09-14", null, 2)
        assertTrue(data.records.isEmpty())
        assertNull(data.statusOf(math, "2026-09-14"))
    }

    @Test
    fun summaryCountsEachStatusOfOneCourseOnly() {
        val data = AttendanceData()
            .withMark(math, "2026-09-14", AttendanceStatus.Present, 1)
            .withMark(math, "2026-09-21", AttendanceStatus.Absent, 1)
            .withMark(math, "2026-09-28", AttendanceStatus.Leave, 1)
            .withMark("大学英语", "2026-09-14", AttendanceStatus.Absent, 1)
        val summary = data.summaryOf(math)
        assertEquals(1, summary.present)
        assertEquals(1, summary.absent)
        assertEquals(1, summary.leave)
        assertEquals(3, summary.total)
    }

    @Test
    fun leaveDoesNotUseUpTheAbsenceLimit() {
        val data = AttendanceData()
            .withAllowance(math, 2)
            .withMark(math, "2026-09-14", AttendanceStatus.Absent, 1)
            .withMark(math, "2026-09-21", AttendanceStatus.Leave, 1)
        val summary = data.summaryOf(math)
        assertEquals(1, summary.remaining)
        assertFalse(summary.atLimit)
    }

    @Test
    fun reachingAndPassingTheLimitIsReported() {
        var data = AttendanceData().withAllowance(math, 1).withMark(math, "2026-09-14", AttendanceStatus.Absent, 1)
        assertTrue(data.summaryOf(math).atLimit)
        assertEquals(0, data.summaryOf(math).remaining)
        data = data.withMark(math, "2026-09-21", AttendanceStatus.Absent, 2)
        assertEquals(-1, data.summaryOf(math).remaining)
    }

    @Test
    fun withoutALimitNothingIsRemaining() {
        val summary = AttendanceData().withMark(math, "2026-09-14", AttendanceStatus.Absent, 1).summaryOf(math)
        assertNull(summary.remaining)
        assertFalse(summary.atLimit)
    }

    @Test
    fun anAllowanceCanBeClearedAgain() {
        val data = AttendanceData().withAllowance(math, 3).withAllowance(math, null)
        assertTrue(data.allowances.isEmpty())
    }

    @Test
    fun savedDataRoundTripsAndOldEmptyDataLoads() {
        val data = AttendanceData().withAllowance(math, 2).withMark(math, "2026-09-14", AttendanceStatus.Leave, 5)
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(data, json.decodeFromString(AttendanceData.serializer(), json.encodeToString(AttendanceData.serializer(), data)))
        assertEquals(AttendanceData(), json.decodeFromString(AttendanceData.serializer(), "{}"))
    }
}
