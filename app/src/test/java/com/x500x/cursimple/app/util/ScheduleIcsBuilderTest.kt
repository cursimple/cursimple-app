package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.R
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ScheduleIcsBuilderTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    // 2026-09-07 是周一，第 1 教学周周一
    private val termStart = LocalDate.of(2026, 9, 7)
    private val generatedAt = Instant.parse("2026-09-01T04:00:00Z")

    private fun profile(): TermTimingProfile = TermTimingProfile(
        termStartDate = termStart.toString(),
        slotTimes = listOf(
            ClassSlotTime(1, 2, "08:00", "09:35", "第一大节"),
            ClassSlotTime(3, 4, "10:05", "11:40", "第二大节"),
        ),
    )

    private fun mondayCourse(
        id: String = "c1",
        title: String = "高等数学",
        weeks: List<Int> = emptyList(),
        startNode: Int = 1,
        endNode: Int = 2,
        teacher: String = "张三",
        location: String = "教一 101",
        category: CourseCategory = CourseCategory.Course,
    ): CourseItem = CourseItem(
        id = id,
        title = title,
        teacher = teacher,
        location = location,
        weeks = weeks,
        category = category,
        time = CourseTimeSlot(dayOfWeek = 1, startNode = startNode, endNode = endNode),
    )

    private fun scheduleOf(vararg courses: CourseItem): TermSchedule {
        val byDay = courses.groupBy { it.time.dayOfWeek }
            .map { (day, list) -> DailySchedule(dayOfWeek = day, courses = list) }
        return TermSchedule(termId = "t1", updatedAt = "2026-09-01T00:00:00+08:00", dailySchedules = byDay)
    }

    private fun build(
        schedule: TermSchedule? = null,
        manual: List<CourseItem> = emptyList(),
        profile: TermTimingProfile? = profile(),
        overrides: List<TemporaryScheduleOverride> = emptyList(),
        start: LocalDate? = termStart,
        zone: ZoneId = shanghai,
    ) = ScheduleIcsBuilder.build(
        termName = "2026 秋",
        termStartDate = start,
        schedule = schedule,
        manualCourses = manual,
        timingProfile = profile,
        overrides = overrides,
        zone = zone,
        generatedAt = generatedAt,
    )

    /** 把折叠后的 ICS 展开成逻辑行。 */
    private fun unfold(content: String): List<String> =
        content.replace("\r\n ", "").split("\r\n").filter { it.isNotEmpty() }

    private fun vevents(content: String): List<List<String>> {
        val lines = unfold(content)
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> current = mutableListOf()
                line == "END:VEVENT" -> {
                    current?.let { blocks.add(it) }
                    current = null
                }
                else -> current?.add(line)
            }
        }
        return blocks
    }

    @Test
    fun `连续周次生成单个带 RRULE COUNT 的事件`() {
        val result = build(schedule = scheduleOf(mondayCourse(weeks = listOf(1, 2, 3, 4))))
        val events = vevents(result.content)
        assertEquals(1, events.size)
        assertEquals(4, result.occurrenceCount)
        val ev = events.first()
        assertTrue(ev.contains("DTSTART;TZID=Asia/Shanghai:20260907T080000"))
        assertTrue(ev.contains("DTEND;TZID=Asia/Shanghai:20260907T093500"))
        assertTrue(ev.contains("RRULE:FREQ=WEEKLY;COUNT=4"))
        assertTrue(ev.none { it.startsWith("EXDATE") })
        assertTrue(ev.contains("SUMMARY:高等数学"))
        assertTrue(ev.contains("LOCATION:教一 101"))
    }

    @Test
    fun `不连续周次用 RRULE UNTIL 加 EXDATE 表达`() {
        val result = build(schedule = scheduleOf(mondayCourse(weeks = listOf(1, 3, 5))))
        val ev = vevents(result.content).single()
        assertEquals(3, result.occurrenceCount)
        // 末次 = 第 5 周周一 2026-10-05 08:00 Asia/Shanghai = 2026-10-05 00:00Z
        assertTrue(ev.contains("RRULE:FREQ=WEEKLY;UNTIL=20261005T000000Z"))
        val exdate = ev.first { it.startsWith("EXDATE") }
        assertEquals("EXDATE;TZID=Asia/Shanghai:20260914T080000,20260928T080000", exdate)
    }

    @Test
    fun `单次课程不产生 RRULE`() {
        val result = build(schedule = scheduleOf(mondayCourse(weeks = listOf(2))))
        val ev = vevents(result.content).single()
        assertTrue(ev.contains("DTSTART;TZID=Asia/Shanghai:20260914T080000"))
        assertTrue(ev.none { it.startsWith("RRULE") })
        assertEquals(1, result.occurrenceCount)
    }

    @Test
    fun `临时调课按来源日的课在目标日单独生成事件`() {
        // 2026-09-19 周六，按 2026-09-07 周一（第 1 周）上课
        val makeUp = TemporaryScheduleOverride(
            id = "mk1",
            type = TemporaryScheduleOverrideType.MakeUp,
            targetDate = "2026-09-19",
            sourceDate = "2026-09-07",
        )
        val result = build(
            schedule = scheduleOf(mondayCourse(weeks = listOf(1, 2))),
            overrides = listOf(makeUp),
        )
        val events = vevents(result.content)
        assertEquals(2, events.size)
        assertEquals(3, result.occurrenceCount)
        // 常规系列：第 1、2 周周一
        val recurring = events.first { ev -> ev.any { it.startsWith("RRULE") } }
        assertTrue(recurring.contains("RRULE:FREQ=WEEKLY;COUNT=2"))
        // 调课事件：目标日 09-19，且不属于 RRULE
        val makeUpEvent = events.first { ev -> ev.any { it.contains("20260919T080000") } }
        assertTrue(makeUpEvent.none { it.startsWith("RRULE") })
        assertTrue(makeUpEvent.any { it.startsWith("UID:") && it.contains("-mk-20260919") })
    }

    @Test
    fun `取消调课让该次课从 EXDATE 移除`() {
        val cancel = TemporaryScheduleOverride(
            id = "cx1",
            type = TemporaryScheduleOverrideType.CancelCourse,
            targetDate = "2026-09-14",
            cancelStartNode = 1,
            cancelEndNode = 2,
        )
        val result = build(
            schedule = scheduleOf(mondayCourse(weeks = listOf(1, 2, 3))),
            overrides = listOf(cancel),
        )
        val ev = vevents(result.content).single()
        assertEquals(2, result.occurrenceCount)
        assertTrue(ev.any { it.startsWith("EXDATE") && it.contains("20260914T080000") })
    }

    @Test
    fun `缺少节次时间的课程被跳过并如实记录`() {
        // startNode 5 不在任何 slot 覆盖范围内
        val result = build(schedule = scheduleOf(mondayCourse(startNode = 5, endNode = 6)))
        assertEquals(0, result.eventCount)
        assertEquals(1, result.skipped.size)
        assertEquals(R.string.ics_skip_missing_period, result.skipped.first().reason)
        assertNull(result.failureReason)
    }

    @Test
    fun `部分课程缺时间时其余仍导出`() {
        val good = mondayCourse(id = "good", title = "语文", weeks = listOf(1))
        val bad = mondayCourse(id = "bad", title = "体育", weeks = listOf(1), startNode = 9, endNode = 10)
        val result = build(schedule = scheduleOf(good, bad))
        assertEquals(1, result.eventCount)
        assertEquals(1, result.skipped.size)
        assertEquals("体育", result.skipped.first().title)
    }

    @Test
    fun `缺少节次档案时整体失败并列出全部课程`() {
        val result = build(schedule = scheduleOf(mondayCourse(weeks = listOf(1))), profile = null)
        assertEquals(0, result.eventCount)
        assertEquals(R.string.ics_failure_no_timing, result.failureReason)
        assertEquals(1, result.skipped.size)
        assertEquals(R.string.ics_skip_no_timing, result.skipped.first().reason)
    }

    @Test
    fun `未设开学日期时失败`() {
        val result = build(schedule = scheduleOf(mondayCourse(weeks = listOf(1))), start = null)
        assertEquals(0, result.eventCount)
        assertEquals(R.string.ics_failure_no_term_start, result.failureReason)
    }

    @Test
    fun `UID 在重复导出时保持稳定`() {
        val schedule = scheduleOf(mondayCourse(weeks = listOf(1, 2)))
        val first = build(schedule = schedule)
        val second = build(schedule = schedule)
        val uid1 = vevents(first.content).single().first { it.startsWith("UID:") }
        val uid2 = vevents(second.content).single().first { it.startsWith("UID:") }
        assertEquals(uid1, uid2)
        assertTrue(uid1.endsWith("@cursimple.x500x.com"))
    }

    @Test
    fun `行尾一律为 CRLF`() {
        val result = build(schedule = scheduleOf(mondayCourse(weeks = listOf(1, 2))))
        assertTrue(result.content.contains("\r\n"))
        val withoutCrlf = result.content.replace("\r\n", "")
        assertFalse(withoutCrlf.contains("\n"))
        assertFalse(withoutCrlf.contains("\r"))
    }

    @Test
    fun `DESCRIPTION 含教师与节次信息`() {
        val result = build(schedule = scheduleOf(mondayCourse(weeks = listOf(1))))
        val ev = vevents(result.content).single()
        val desc = ev.first { it.startsWith("DESCRIPTION:") }
        assertTrue(desc.contains("教师：张三"))
        assertTrue(desc.contains("节次：第1-2节"))
        assertTrue(desc.contains("时段：第一大节"))
    }

    @Test
    fun `考试类型写入 DESCRIPTION`() {
        val result = build(
            schedule = scheduleOf(mondayCourse(weeks = listOf(1), category = CourseCategory.Exam)),
        )
        val ev = vevents(result.content).single()
        assertTrue(ev.any { it.startsWith("DESCRIPTION:") && it.contains("类型：考试") })
    }

    // ---- 时区 ----

    @Test
    fun `无夏令时时区写出单个 STANDARD 观察项`() {
        val result = build(schedule = scheduleOf(mondayCourse(weeks = listOf(1, 2))))
        val lines = unfold(result.content)
        assertTrue(lines.contains("BEGIN:VTIMEZONE"))
        assertTrue(lines.contains("TZID:Asia/Shanghai"))
        assertTrue(lines.contains("TZOFFSETTO:+0800"))
        assertEquals(0, lines.count { it == "BEGIN:DAYLIGHT" })
        assertEquals(1, lines.count { it == "BEGIN:STANDARD" })
    }

    @Test
    fun `跨夏令时的时区写出 DAYLIGHT 转换`() {
        val ny = ZoneId.of("America/New_York")
        val nyStart = LocalDate.of(2026, 3, 2) // 周一，早于 3-8 的春季调整
        val result = ScheduleIcsBuilder.build(
            termName = "spring",
            termStartDate = nyStart,
            schedule = scheduleOf(mondayCourse(weeks = listOf(1, 2, 3))),
            manualCourses = emptyList(),
            timingProfile = TermTimingProfile(
                termStartDate = nyStart.toString(),
                slotTimes = listOf(ClassSlotTime(1, 2, "08:00", "09:35")),
            ),
            overrides = emptyList(),
            zone = ny,
            generatedAt = generatedAt,
        )
        val lines = unfold(result.content)
        assertTrue(lines.contains("BEGIN:DAYLIGHT"))
        assertTrue(lines.contains("DTSTART:20260308T020000"))
        assertTrue(lines.contains("TZOFFSETFROM:-0500"))
        assertTrue(lines.contains("TZOFFSETTO:-0400"))
        // 事件仍用墙上时钟 08:00，交给 TZID 处理夏令时
        val ev = vevents(result.content).single()
        assertTrue(ev.any { it.contains("DTSTART;TZID=America/New_York:20260302T080000") })
    }

    // ---- 转义 ----

    @Test
    fun `文本转义处理逗号分号反斜杠换行`() {
        assertEquals("a\\, b", ScheduleIcsBuilder.escapeText("a, b"))
        assertEquals("a\\; b", ScheduleIcsBuilder.escapeText("a; b"))
        assertEquals("a\\\\b", ScheduleIcsBuilder.escapeText("a\\b"))
        assertEquals("line1\\nline2", ScheduleIcsBuilder.escapeText("line1\nline2"))
        assertEquals("line1\\nline2", ScheduleIcsBuilder.escapeText("line1\r\nline2"))
    }

    @Test
    fun `标题里的特殊字符在 SUMMARY 中被转义`() {
        val result = build(
            schedule = scheduleOf(mondayCourse(title = "数学; 复习, 第一讲\\A", weeks = listOf(1))),
        )
        val ev = vevents(result.content).single()
        assertTrue(ev.contains("SUMMARY:数学\\; 复习\\, 第一讲\\\\A"))
    }

    // ---- 折行 ----

    @Test
    fun `ASCII 长行按 75 字节折叠且续行以空格开头`() {
        val line = "SUMMARY:" + "a".repeat(100)
        val folded = ScheduleIcsBuilder.fold(line)
        val physical = folded.split("\r\n")
        assertTrue(physical.size >= 2)
        physical.forEachIndexed { index, seg ->
            assertTrue(seg.toByteArray(Charsets.UTF_8).size <= 75)
            if (index > 0) assertTrue(seg.startsWith(" "))
        }
        // 展开后与原文一致
        assertEquals(line, folded.replace("\r\n ", ""))
    }

    @Test
    fun `中文折行不切断多字节字符且每行不超过 75 字节`() {
        // 每个汉字 3 字节，构造会落在 75 字节边界中间的情况
        val line = "SUMMARY:" + "汉".repeat(60)
        val folded = ScheduleIcsBuilder.fold(line)
        val physical = folded.split("\r\n")
        assertTrue(physical.size >= 2)
        for (seg in physical) {
            val bytes = seg.toByteArray(Charsets.UTF_8)
            assertTrue("行超过 75 字节: ${bytes.size}", bytes.size <= 75)
            // 每个物理行本身必须是合法 UTF-8：往返转换不丢字符
            assertEquals(seg, String(bytes, Charsets.UTF_8))
        }
        assertEquals(line, folded.replace("\r\n ", ""))
    }

    @Test
    fun `恰好 75 字节的行不折叠`() {
        val line = "X".repeat(75)
        val folded = ScheduleIcsBuilder.fold(line)
        assertFalse(folded.contains("\r\n"))
        assertEquals(line, folded)
    }

    @Test
    fun `整份日历里每个物理行都不超过 75 字节`() {
        val result = build(
            schedule = scheduleOf(
                mondayCourse(title = "面向对象程序设计与数据结构实验课程".repeat(2), weeks = listOf(1, 3, 5, 7)),
            ),
        )
        for (physical in result.content.split("\r\n")) {
            assertTrue(physical.toByteArray(Charsets.UTF_8).size <= 75)
        }
    }

    @Test
    fun `手工核对一次完整事件输出`() {
        val result = build(schedule = scheduleOf(mondayCourse(weeks = listOf(1, 2))))
        val ev = vevents(result.content).single()
        assertTrue(ev.any { it.startsWith("UID:c1-w1-n1-2@cursimple.x500x.com") })
        assertTrue(ev.contains("DTSTAMP:20260901T040000Z"))
        assertTrue(ev.contains("DTSTART;TZID=Asia/Shanghai:20260907T080000"))
        assertTrue(ev.contains("DTEND;TZID=Asia/Shanghai:20260907T093500"))
        assertTrue(ev.contains("RRULE:FREQ=WEEKLY;COUNT=2"))
    }
}
