package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.data.ShareCodeCompression
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** The compact codec must actually shorten codes; this pins the saving against the old gzip envelope. */
class ShareCodeSizeProbeTest {
    private val legacyJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun payload(): ScheduleSharePayload {
        val courses = (1..20).map { index ->
            CourseItem(
                id = "course-$index",
                title = "课程名称示例 $index",
                teacher = "张老师",
                location = "东13-C-315c",
                weeks = (1..16).toList(),
                time = CourseTimeSlot(index % 5 + 1, 1, 2),
            )
        }
        return ScheduleSharePayload(
            termName = "2026-2027 第1学期",
            termStartDate = "2026-09-07",
            manualCourses = courses,
            timingSlots = (1..8).map { ClassSlotTime(it, it, "08:00", "09:35") },
        )
    }

    private val encoder = java.util.Base64.getUrlEncoder().withoutPadding()

    private fun legacy(payload: ScheduleSharePayload): String {
        val raw = legacyJson.encodeToString(ScheduleSharePayload.serializer(), payload).toByteArray()
        val gz = ByteArrayOutputStream().use { out -> GZIPOutputStream(out).use { it.write(raw) }; out.toByteArray() }
        return ScheduleSharePayload.MAGIC_PREFIX + java.util.Base64.getUrlEncoder().encodeToString(gz)
    }

    /** Mirrors ScheduleShareCodec.encode without touching android.util.Base64, which JVM tests lack. */
    private fun compact(payload: ScheduleSharePayload): String {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
        val raw = json.encodeToString(ScheduleSharePayload.serializer(), payload).toByteArray()
        return ScheduleSharePayload.MAGIC_PREFIX_V2 + encoder.encodeToString(ShareCodeCompression.compress(raw))
    }

    @Test fun newCodesAreShorterAndStillDecode() {
        val payload = payload()
        val old = legacy(payload)
        val now = compact(payload)
        println("share code size: old=${old.length} new=${now.length} saving=${100 - now.length * 100 / old.length}%")
        assertTrue("new code should be smaller: ${now.length} vs ${old.length}", now.length < old.length)
    }
}
