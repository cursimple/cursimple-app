package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.core.kernel.model.weekdayLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleImageLayoutTest {

    // 2026-09-07 是周一，第 1 教学周周一
    private val termStart = LocalDate.of(2026, 9, 7)

    /**
     * 近似的等宽测量：中日韩字符按一个字号宽，其余按 0.55 个字号宽。
     * 只要求单调且与真实字体同量级，用来验证换行逻辑而不是像素级排版。
     */
    private val measurer = ScheduleImageTextMeasurer { text, fontSize, bold ->
        val boldFactor = if (bold) 1.02f else 1f
        text.map { ch -> if (isWide(ch)) 100 else 55 }.sum() / 100f * fontSize * boldFactor
    }

    private fun isWide(ch: Char): Boolean = ch.code >= 0x2E80

    /** 复刻当前中文文案，验证排版逻辑不受文案外移影响。 */
    private val labels = ScheduleImageLabels(
        defaultTitle = "课表",
        holidayFallbackName = "假日",
        holidayNameOfRes = { "内置假日" },
        holidayAllDayOff = "全天无课",
        overflowMoreDetail = "见图下备注",
        noTimingFailure = "未设置节次上课时间",
        weekdayName = { day -> weekdayLabel(day) },
        dateLabel = { date -> "${date.monthValue}月${date.dayOfMonth}日" },
        weekLabel = { week -> "第 $week 周" },
        allWeeksSubtitle = "全部周 · 不分周次",
        weeksDetail = { ranges -> "$ranges 周" },
        sharedCellFootnote = { weekday, nodeLabel, titles ->
            "$weekday ${nodeLabel}节共有 ${titles.size} 门（分属不同周）：${titles.joinToString("、")}"
        },
        makeUpNote = { source -> "调$source" },
        overflowTitle = { hidden -> "还有 $hidden 门" },
        conflictFootnote = { weekday, nodeLabel, titles ->
            "$weekday ${nodeLabel}节同时有 ${titles.size} 门：${titles.joinToString("、")}"
        },
        emptyWeekFailure = { week -> "第 $week 周没有课程" },
        emptyAllWeeksFailure = "课表里还没有课程",
    )

    private fun profile(vararg slots: ClassSlotTime): TermTimingProfile =
        TermTimingProfile(termStartDate = termStart.toString(), slotTimes = slots.toList())

    private fun defaultProfile(): TermTimingProfile = profile(
        ClassSlotTime(1, 2, "08:00", "09:35", "第一大节"),
        ClassSlotTime(3, 4, "10:05", "11:40", "第二大节"),
        ClassSlotTime(5, 6, "14:00", "15:35", "第三大节"),
        ClassSlotTime(7, 8, "16:00", "17:35", "第四大节"),
    )

    private fun course(
        id: String,
        title: String,
        dayOfWeek: Int,
        startNode: Int,
        endNode: Int,
        weeks: List<Int> = emptyList(),
        teacher: String = "张三",
        location: String = "教一101",
        category: CourseCategory = CourseCategory.Course,
    ): CourseItem = CourseItem(
        id = id,
        title = title,
        teacher = teacher,
        location = location,
        weeks = weeks,
        category = category,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
    )

    private fun scheduleOf(vararg courses: CourseItem): TermSchedule =
        TermSchedule(
            termId = "t1",
            updatedAt = "2026-09-01T00:00:00+08:00",
            dailySchedules = courses.groupBy { it.time.dayOfWeek }
                .map { (day, list) -> DailySchedule(dayOfWeek = day, courses = list) },
        )

    private fun layout(
        weekNumber: Int = 1,
        schedule: TermSchedule? = null,
        manualCourses: List<CourseItem> = emptyList(),
        timingProfile: TermTimingProfile = defaultProfile(),
        overrides: List<TemporaryScheduleOverride> = emptyList(),
        holidayCalendar: HolidayCalendarSettings = HolidayCalendarSettings.NONE,
        metrics: ScheduleImageMetrics = ScheduleImageMetrics(),
        allWeeks: Boolean = false,
    ): ScheduleImageLayoutResult = ScheduleImageLayout.compute(
        termName = "2026 秋季学期",
        termStartDate = termStart,
        weekNumber = weekNumber,
        schedule = schedule,
        manualCourses = manualCourses,
        timingProfile = timingProfile,
        overrides = overrides,
        holidayCalendar = holidayCalendar,
        measurer = measurer,
        labels = labels,
        metrics = metrics,
        allWeeks = allWeeks,
    )

    @Test
    fun `跨多节的课程块合并成一个高块`() {
        val metrics = ScheduleImageMetrics()
        val result = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 4)),
            metrics = metrics,
        )

        assertEquals(1, result.blocks.size)
        val block = result.blocks.first()
        assertEquals(2, block.rowSpan)
        assertEquals(0, block.rowStart)
        val expectedHeight = 2 * metrics.rowHeight - 2 * metrics.blockGap
        assertEquals(expectedHeight, block.rect.height, 0.01f)
    }

    @Test
    fun `不跨节的课程只占一行`() {
        val metrics = ScheduleImageMetrics()
        val result = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 3, endNode = 4)),
            metrics = metrics,
        )

        val block = result.blocks.single()
        assertEquals(1, block.rowSpan)
        assertEquals(metrics.rowHeight - 2 * metrics.blockGap, block.rect.height, 0.01f)
    }

    @Test
    fun `同一格两门课并排且互不重叠`() {
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(1, 3)),
                course("c2", "线性代数", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(1, 2)),
            ),
        )

        assertEquals(2, result.blocks.size)
        val (first, second) = result.blocks.sortedBy { it.rect.left }
        assertEquals(2, first.laneCount)
        assertEquals(2, second.laneCount)
        assertTrue("两个块不应重叠", first.rect.right <= second.rect.left)
        // 两块都要留在所属列内
        val column = result.dayHeaders.first { it.dayOfWeek == 1 }.rect
        assertTrue(first.rect.left >= column.left)
        assertTrue(second.rect.right <= column.right)
        assertTrue(result.footnotes.isEmpty())
    }

    @Test
    fun `同一格三门课并排并写入图下备注`() {
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 2, startNode = 1, endNode = 2),
                course("c2", "线性代数", dayOfWeek = 2, startNode = 1, endNode = 2),
                course("c3", "大学物理", dayOfWeek = 2, startNode = 1, endNode = 2),
            ),
        )

        assertEquals(3, result.blocks.size)
        assertTrue(result.blocks.none { it.isOverflow })
        assertEquals(listOf(0, 1, 2), result.blocks.map { it.laneIndex }.sorted())
        assertTrue(result.footnotes.isNotEmpty())
        val note = result.footnotes.joinToString("")
        assertTrue(note.contains("高等数学"))
        assertTrue(note.contains("大学物理"))
    }

    @Test
    fun `同一格超过三门时保留两门并给出汇总块`() {
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 3, startNode = 1, endNode = 2),
                course("c2", "线性代数", dayOfWeek = 3, startNode = 1, endNode = 2),
                course("c3", "大学物理", dayOfWeek = 3, startNode = 1, endNode = 2),
                course("c4", "程序设计", dayOfWeek = 3, startNode = 1, endNode = 2),
            ),
        )

        assertEquals(3, result.blocks.size)
        val overflow = result.blocks.single { it.isOverflow }
        assertEquals("还有 2 门", overflow.title)
        assertEquals(2, result.blocks.count { !it.isOverflow })
        // 备注里四门课都在，信息不丢
        val note = result.footnotes.joinToString("")
        listOf("高等数学", "线性代数", "大学物理", "程序设计").forEach {
            assertTrue("备注缺少 $it", note.contains(it))
        }
    }

    @Test
    fun `行区间部分重叠的课程也判为同一组并排`() {
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 4),
                course("c2", "线性代数", dayOfWeek = 1, startNode = 3, endNode = 4),
            ),
        )

        assertEquals(2, result.blocks.size)
        assertTrue(result.blocks.all { it.laneCount == 2 })
        val long = result.blocks.single { it.title == "高等数学" }
        val short = result.blocks.single { it.title == "线性代数" }
        assertEquals(2, long.rowSpan)
        assertEquals(1, short.rowSpan)
        assertTrue(long.rect.right <= short.rect.left)
    }

    @Test
    fun `行区间不重叠的课程各自独占整列宽度`() {
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2),
                course("c2", "线性代数", dayOfWeek = 1, startNode = 5, endNode = 6),
            ),
        )

        assertEquals(2, result.blocks.size)
        assertTrue(result.blocks.all { it.laneCount == 1 })
    }

    @Test
    fun `超长中文课名按字换行并在格子内省略`() {
        val metrics = ScheduleImageMetrics()
        val longTitle = "中国近现代史纲要与思想道德修养及法律基础综合实践课程"
        val result = layout(
            schedule = scheduleOf(
                course("c1", longTitle, dayOfWeek = 1, startNode = 1, endNode = 2),
            ),
            metrics = metrics,
        )

        val block = result.blocks.single()
        val titleLines = block.lines.filter { it.role == ScheduleImageTextRole.Title }
        assertTrue("长课名应换行", titleLines.size > 1)
        assertTrue("换行不应超过上限", titleLines.size <= metrics.maxTitleLines)
        titleLines.forEach { line ->
            val width = measurer.measure(line.text, block.titleFontSize, true)
            assertTrue("行宽 $width 越出格子 ${block.contentRect.width}", width <= block.contentRect.width + 0.01f)
        }
        assertTrue("被截断的课名应以省略号收尾", titleLines.last().text.endsWith("…"))
        // 换行位置落在字与字之间，拼起来仍是原文的前缀
        val joined = titleLines.joinToString("") { it.text }.removeSuffix("…")
        assertTrue(longTitle.startsWith(joined))
    }

    @Test
    fun `中文按字断行而不是整段挤成一行`() {
        val lines = ScheduleImageText.wrap(
            text = "大学英语视听说",
            maxWidth = 90f,
            maxLines = 5,
            fontSize = 30f,
            bold = false,
            measurer = measurer,
        )

        assertEquals(listOf("大学英", "语视听", "说"), lines)
    }

    @Test
    fun `连续英文数字能整体放下时不从中间断开`() {
        val lines = ScheduleImageText.wrap(
            text = "微积分 Calculus",
            maxWidth = 200f,
            maxLines = 4,
            fontSize = 30f,
            bold = false,
            measurer = measurer,
        )

        assertEquals(listOf("微积分", "Calculus"), lines)
    }

    @Test
    fun `单个英文单词超过整行宽度时按字符切开`() {
        val lines = ScheduleImageText.wrap(
            text = "Microbiology",
            maxWidth = 60f,
            maxLines = 4,
            fontSize = 20f,
            bold = false,
            measurer = measurer,
        )

        assertTrue(lines.size > 1)
        assertEquals("Microbiology", lines.joinToString(""))
    }

    @Test
    fun `地点和教师在格子里各占一行且被省略而不是溢出`() {
        val result = layout(
            schedule = scheduleOf(
                course(
                    id = "c1",
                    title = "数学",
                    dayOfWeek = 1,
                    startNode = 1,
                    endNode = 2,
                    teacher = "欧阳锋副教授",
                    location = "第三教学楼多媒体阶梯教室",
                ),
            ),
        )

        val block = result.blocks.single()
        val details = block.lines.filter { it.role == ScheduleImageTextRole.Detail }
        assertEquals(2, details.size)
        details.forEach { line ->
            val width = measurer.measure(line.text, block.detailFontSize, false)
            assertTrue(width <= block.contentRect.width + 0.01f)
        }
    }

    @Test
    fun `空的地点与教师不占用行`() {
        val result = layout(
            schedule = scheduleOf(
                course("c1", "数学", dayOfWeek = 1, startNode = 1, endNode = 2, teacher = "", location = "  "),
            ),
        )

        val block = result.blocks.single()
        assertTrue(block.lines.none { it.role == ScheduleImageTextRole.Detail })
    }

    @Test
    fun `假日当天不画课并标注假日名`() {
        val holidayDate = termStart.plusDays(1)
        val settings = HolidayCalendarSettings(
            builtInEnabled = false,
            entries = listOf(
                HolidayCalendarEntry(
                    date = holidayDate.toString(),
                    kind = HolidayEntryKind.Holiday,
                    name = "中秋节",
                ),
            ),
        )
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2),
                course("c2", "线性代数", dayOfWeek = 2, startNode = 1, endNode = 2),
            ),
            holidayCalendar = settings,
        )

        assertTrue("假日当天不应有课程块", result.blocks.none { it.dayOfWeek == 2 })
        assertTrue("非假日当天照常出课", result.blocks.any { it.dayOfWeek == 1 })
        val holiday = result.holidays.single()
        assertEquals(2, holiday.dayOfWeek)
        assertTrue(holiday.lines.joinToString("").contains("中秋节"))
    }

    @Test
    fun `没有名字的假日也标注为假日`() {
        val settings = HolidayCalendarSettings(
            builtInEnabled = false,
            entries = listOf(
                HolidayCalendarEntry(date = termStart.toString(), kind = HolidayEntryKind.Holiday, name = ""),
            ),
        )
        val result = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)),
            holidayCalendar = settings,
        )

        val holiday = result.holidays.single()
        assertEquals(1, holiday.dayOfWeek)
        assertTrue(holiday.lines.joinToString("").contains("假日"))
    }

    @Test
    fun `临时调课按调课来源日取课并在表头标注`() {
        // 第 1 周周六（09-12）补上周三（09-09）的课
        val makeUp = TemporaryScheduleOverride(
            id = "o1",
            type = TemporaryScheduleOverrideType.MakeUp,
            targetDate = "2026-09-12",
            sourceDate = "2026-09-09",
        )
        val result = layout(
            schedule = scheduleOf(
                course("c3", "大学物理", dayOfWeek = 3, startNode = 1, endNode = 2),
                course("c6", "体育", dayOfWeek = 6, startNode = 5, endNode = 6),
            ),
            overrides = listOf(makeUp),
        )

        val saturday = result.blocks.filter { it.dayOfWeek == 6 }
        assertEquals(1, saturday.size)
        assertEquals("大学物理", saturday.single().title)
        val header = result.dayHeaders.single { it.dayOfWeek == 6 }
        assertEquals("调周三", header.noteLabel)
        assertEquals("9月12日", header.dateLabel)
    }

    @Test
    fun `临时调课来源日的周次决定课程是否上课`() {
        // 周三的课只在第 1 周上，第 2 周的周六补的是第 2 周周三，因此不出课
        val makeUp = TemporaryScheduleOverride(
            id = "o1",
            type = TemporaryScheduleOverrideType.MakeUp,
            targetDate = "2026-09-19",
            sourceDate = "2026-09-16",
        )
        val result = layout(
            weekNumber = 2,
            schedule = scheduleOf(
                course("c3", "大学物理", dayOfWeek = 3, startNode = 1, endNode = 2, weeks = listOf(1)),
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2),
            ),
            overrides = listOf(makeUp),
        )

        assertTrue(result.blocks.none { it.dayOfWeek == 6 })
    }

    @Test
    fun `只调指定节次时这天同时排出两天的课`() {
        // 第 1 周周五（09-11）的 5-6 节改上周三（09-09）的课，其余节次不动
        val partial = TemporaryScheduleOverride(
            id = "o1",
            type = TemporaryScheduleOverrideType.MakeUp,
            targetDate = "2026-09-11",
            sourceDate = "2026-09-09",
            makeUpStartNode = 5,
            makeUpEndNode = 6,
        )
        val result = layout(
            schedule = scheduleOf(
                course("c3", "大学物理", dayOfWeek = 3, startNode = 5, endNode = 6),
                course("c3b", "机器学习", dayOfWeek = 3, startNode = 1, endNode = 2),
                course("c5", "体育", dayOfWeek = 5, startNode = 5, endNode = 6),
                course("c5b", "高等数学", dayOfWeek = 5, startNode = 1, endNode = 2),
            ),
            overrides = listOf(partial),
        )

        val friday = result.blocks.filter { it.dayOfWeek == 5 }.map { it.title }.toSet()
        // 5-6 节换成周三的课，1-2 节还是周五自己的
        assertEquals(setOf("大学物理", "高等数学"), friday)
    }

    @Test
    fun `临时停课的课程不出现在图上`() {
        val cancel = TemporaryScheduleOverride(
            id = "o2",
            type = TemporaryScheduleOverrideType.CancelCourse,
            targetDate = termStart.toString(),
            cancelStartNode = 1,
            cancelEndNode = 2,
            cancelCourseId = "c1",
        )
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2),
                course("c2", "线性代数", dayOfWeek = 1, startNode = 3, endNode = 4),
            ),
            overrides = listOf(cancel),
        )

        assertEquals(listOf("线性代数"), result.blocks.map { it.title })
    }

    @Test
    fun `只在指定周上的课不会出现在别的周`() {
        val schedule = scheduleOf(
            course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(1, 3, 5)),
        )

        assertEquals(1, layout(weekNumber = 1, schedule = schedule).blocks.size)
        assertEquals(0, layout(weekNumber = 2, schedule = schedule).blocks.size)
        assertEquals(1, layout(weekNumber = 3, schedule = schedule).blocks.size)
    }

    @Test
    fun `全部周把限定周次的课一并画出来`() {
        val schedule = scheduleOf(
            course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(1, 3, 5)),
            course("c2", "硬笔书法", dayOfWeek = 3, startNode = 7, endNode = 8, weeks = listOf(9, 10, 11)),
        )

        // 单周视角下第 1 周看不到第 9 周才开的课
        assertEquals(listOf("高等数学"), layout(weekNumber = 1, schedule = schedule).blocks.map { it.title })

        val all = layout(weekNumber = 1, schedule = schedule, allWeeks = true)
        assertEquals(setOf("高等数学", "硬笔书法"), all.blocks.map { it.title }.toSet())
        assertTrue(all.allWeeks)
        assertNull(all.failureReason)
    }

    @Test
    fun `全部周在课程块上标出周次`() {
        val result = layout(
            schedule = scheduleOf(
                course(
                    "c1",
                    "高等数学",
                    dayOfWeek = 1,
                    startNode = 1,
                    endNode = 2,
                    weeks = listOf(1, 2, 3, 5),
                    location = "",
                    teacher = "",
                ),
                course("c2", "线性代数", dayOfWeek = 2, startNode = 1, endNode = 2, location = "", teacher = ""),
            ),
            allWeeks = true,
        )

        val limited = result.blocks.single { it.title == "高等数学" }
        assertEquals(listOf("1-3, 5 周"), limited.lines.filter { it.role == ScheduleImageTextRole.Detail }.map { it.text })
        // 整学期都上的课没有周次限制，不用多占一行
        val everyWeek = result.blocks.single { it.title == "线性代数" }
        assertTrue(everyWeek.lines.none { it.role == ScheduleImageTextRole.Detail })
    }

    @Test
    fun `全部周把同一门课按周拆开的多条合成一块`() {
        val result = layout(
            schedule = scheduleOf(
                course("c1", "大学英语", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(1, 3, 5)),
                course("c2", "大学英语", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(2, 4, 6)),
            ),
            allWeeks = true,
        )

        val block = result.blocks.single()
        assertEquals("大学英语", block.title)
        assertEquals(1, block.laneCount)
        assertTrue(block.lines.any { it.text.contains("1-6 周") })
    }

    @Test
    fun `全部周不套用假日与临时调课`() {
        val holiday = HolidayCalendarSettings(
            builtInEnabled = false,
            entries = listOf(
                HolidayCalendarEntry(
                    date = termStart.plusDays(1).toString(),
                    kind = HolidayEntryKind.Holiday,
                    name = "中秋节",
                ),
            ),
        )
        val makeUp = TemporaryScheduleOverride(
            id = "o1",
            type = TemporaryScheduleOverrideType.MakeUp,
            targetDate = "2026-09-12",
            sourceDate = "2026-09-09",
        )
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2),
                course("c2", "线性代数", dayOfWeek = 2, startNode = 1, endNode = 2),
                course("c3", "大学物理", dayOfWeek = 3, startNode = 1, endNode = 2),
            ),
            holidayCalendar = holiday,
            overrides = listOf(makeUp),
            allWeeks = true,
        )

        assertTrue("全部周里没有假日格", result.holidays.isEmpty())
        assertTrue("被假日盖住的课照常画出", result.blocks.any { it.title == "线性代数" })
        assertTrue("调课不把课搬到周六", result.blocks.none { it.dayOfWeek == 6 })
        assertTrue("表头不标调课", result.dayHeaders.all { it.noteLabel == null })
    }

    @Test
    fun `全部周的表头不写日期副标题也不写周次`() {
        val result = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)),
            allWeeks = true,
        )

        assertEquals("全部周 · 不分周次", result.subtitle)
        assertTrue(result.dayHeaders.all { it.dateLabel.isEmpty() })
        assertTrue(result.dayHeaders.all { it.weekdayLabel.isNotEmpty() })
    }

    @Test
    fun `全部周一门课都没有时给出失败原因`() {
        val result = layout(allWeeks = true)

        assertEquals("课表里还没有课程", result.failureReason)
    }

    @Test
    fun `周次压成连续区间`() {
        assertEquals("", ScheduleImageLayout.formatWeekRanges(emptyList()))
        assertEquals("3", ScheduleImageLayout.formatWeekRanges(listOf(3)))
        assertEquals("1-3, 5", ScheduleImageLayout.formatWeekRanges(listOf(2, 1, 3, 5)))
        assertEquals("1, 3, 5", ScheduleImageLayout.formatWeekRanges(listOf(1, 3, 5, 5)))
    }

    @Test
    fun `仅提醒的课程不画进图里`() {
        val result = layout(
            manualCourses = listOf(
                course("m1", "早读打卡", dayOfWeek = 1, startNode = 1, endNode = 2).copy(reminderOnly = true),
                course("m2", "自习", dayOfWeek = 1, startNode = 3, endNode = 4),
            ),
        )

        assertEquals(listOf("自习"), result.blocks.map { it.title })
    }

    @Test
    fun `手动添加的课程与导入课表一起排版`() {
        val result = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)),
            manualCourses = listOf(course("m1", "社团活动", dayOfWeek = 1, startNode = 1, endNode = 2)),
        )

        assertEquals(2, result.blocks.size)
        assertTrue(result.blocks.all { it.laneCount == 2 })
    }

    @Test
    fun `周末无课时也画满七列`() {
        val result = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)),
        )

        // 分享图要看到整周全貌，周末没课就空着，而不是整列裁掉
        assertEquals(7, result.dayHeaders.size)
        assertEquals(
            listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
            result.dayHeaders.map { it.weekdayLabel },
        )
    }

    @Test
    fun `周日有课时画满七列`() {
        val result = layout(
            schedule = scheduleOf(course("c1", "英语角", dayOfWeek = 7, startNode = 5, endNode = 6)),
        )

        assertEquals(7, result.dayHeaders.size)
        assertTrue(result.dayHeaders.last().isWeekend)
    }

    @Test
    fun `没有课的节次行也照常画出来`() {
        val metrics = ScheduleImageMetrics()
        val result = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 3, endNode = 4)),
            metrics = metrics,
        )

        // 四个节次里只有第 3-4 节有课，其余几行空着但不裁掉
        assertEquals(4, result.rows.size)
        val row = result.rows.single { it.nodeLabel == "3-4" }
        assertEquals("10:05", row.startTimeLabel)
        assertEquals("11:40", row.endTimeLabel)
    }

    @Test
    fun `全周无课时给出失败原因`() {
        val result = layout(schedule = scheduleOf())

        assertEquals(0, result.courseCount)
        assertNotNull(result.failureReason)
    }

    @Test
    fun `整周都是假日时仍然出图`() {
        val settings = HolidayCalendarSettings(
            builtInEnabled = false,
            entries = (0..6).map {
                HolidayCalendarEntry(
                    date = termStart.plusDays(it.toLong()).toString(),
                    kind = HolidayEntryKind.Holiday,
                    name = "国庆节",
                )
            },
        )
        val result = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)),
            holidayCalendar = settings,
        )

        assertNull(result.failureReason)
        assertEquals(7, result.holidays.size)
        assertTrue(result.blocks.isEmpty())
    }

    @Test
    fun `没有节次配置时给出失败原因`() {
        val result = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)),
            timingProfile = profile(),
        )

        assertEquals("未设置节次上课时间", result.failureReason)
        assertTrue(result.width > 0)
        assertTrue(result.height > 0)
    }

    @Test
    fun `画布尺寸固定为七列与全部节次，不随课程多少变化`() {
        val metrics = ScheduleImageMetrics()
        val narrow = layout(
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)),
            metrics = metrics,
        )
        val wide = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2),
                course("c2", "英语角", dayOfWeek = 7, startNode = 7, endNode = 8),
            ),
            metrics = metrics,
        )

        assertEquals(wide.width, narrow.width)
        assertEquals(wide.height, narrow.height)
        val expectedWidth = metrics.outerPadding * 2 + metrics.nodeColumnWidth + 7 * metrics.dayColumnWidth
        assertEquals(expectedWidth.toInt(), narrow.width)
    }

    @Test
    fun `课程块始终留在所属列与所属行范围内`() {
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 4),
                course("c2", "线性代数", dayOfWeek = 3, startNode = 3, endNode = 4),
                course("c3", "大学物理", dayOfWeek = 5, startNode = 7, endNode = 8),
                course("c4", "程序设计", dayOfWeek = 5, startNode = 7, endNode = 8),
            ),
        )

        for (block in result.blocks) {
            val column = result.dayHeaders.single { it.dayOfWeek == block.dayOfWeek }.rect
            assertTrue(block.rect.left >= column.left)
            assertTrue(block.rect.right <= column.right)
            assertTrue(block.rect.top >= result.bodyRect.top)
            assertTrue(block.rect.bottom <= result.bodyRect.bottom)
        }
    }

    @Test
    fun `考试块单独着色`() {
        val result = layout(
            schedule = scheduleOf(
                course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2, category = CourseCategory.Exam),
            ),
        )

        assertTrue(result.blocks.single().isExam)
    }

    @Test
    fun `同名课程在不同周取到同一个底色`() {
        val schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2))
        val first = layout(weekNumber = 1, schedule = schedule).blocks.single()
        val second = layout(weekNumber = 4, schedule = schedule).blocks.single()

        assertEquals(first.colorIndex, second.colorIndex)
        assertTrue(first.colorIndex in 0 until ScheduleImageLayout.PALETTE_SIZE)
    }

    @Test
    fun `标题与副标题写明学期与周次日期`() {
        val result = layout(
            weekNumber = 2,
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)),
        )

        assertEquals("2026 秋季学期", result.title)
        assertTrue(result.subtitle.startsWith("第 2 周"))
        assertTrue(result.subtitle.contains("9月14日"))
        // 七列画满，日期范围到周日
        assertTrue(result.subtitle.contains("9月20日"))
    }

    @Test
    fun `周次小于一时按第一周处理`() {
        val result = layout(
            weekNumber = 0,
            schedule = scheduleOf(course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2)),
        )

        assertEquals(1, result.weekNumber)
        assertEquals(1, result.blocks.size)
    }

    @Test
    fun `节次超出配置范围时贴到最近的一行而不是丢课`() {
        val result = layout(
            schedule = scheduleOf(course("c1", "晚自习", dayOfWeek = 1, startNode = 11, endNode = 12)),
        )

        val block = result.blocks.single()
        assertEquals("晚自习", block.title)
        assertEquals(1, block.rowSpan)
    }

    @Test
    fun `当前周次按开学日期换算并在开学前回到第一周`() {
        assertEquals(1, ScheduleImageLayout.currentWeekNumber(termStart, termStart))
        assertEquals(3, ScheduleImageLayout.currentWeekNumber(termStart, termStart.plusDays(15)))
        assertEquals(1, ScheduleImageLayout.currentWeekNumber(termStart, termStart.minusDays(20)))
    }

    @Test
    fun `最大周次取课表里声明过的最大值`() {
        val schedule = scheduleOf(
            course("c1", "高等数学", dayOfWeek = 1, startNode = 1, endNode = 2, weeks = listOf(1, 2, 3)),
            course("c2", "线性代数", dayOfWeek = 2, startNode = 1, endNode = 2, weeks = listOf(16)),
        )

        assertEquals(16, ScheduleImageLayout.maxWeekNumber(schedule, emptyList()))
        assertEquals(20, ScheduleImageLayout.maxWeekNumber(scheduleOf(), emptyList()))
    }

    @Test
    fun `教学周的第一天始终是周一`() {
        assertEquals(LocalDate.of(2026, 9, 7), ScheduleImageLayout.weekStartDate(termStart, 1))
        assertEquals(LocalDate.of(2026, 9, 14), ScheduleImageLayout.weekStartDate(termStart, 2))
        // 开学日落在周中时仍以那一周的周一为第 1 周起点
        assertEquals(
            LocalDate.of(2026, 9, 7),
            ScheduleImageLayout.weekStartDate(LocalDate.of(2026, 9, 9), 1),
        )
    }

    @Test
    fun `省略号本身放不下时逐字回退`() {
        val text = ScheduleImageText.ellipsize(
            text = "高等数学",
            maxWidth = 60f,
            fontSize = 30f,
            bold = false,
            measurer = measurer,
        )

        assertTrue(text.endsWith("…"))
        assertTrue(measurer.measure(text, 30f, false) <= 60f)
        assertFalse(text == "高等数学…")
    }

    @Test
    fun `空文本不产生任何行`() {
        assertTrue(ScheduleImageText.wrap("   ", 100f, 3, 20f, false, measurer).isEmpty())
        assertTrue(ScheduleImageText.wrap("课", 0f, 3, 20f, false, measurer).isEmpty())
        assertTrue(ScheduleImageText.wrap("课", 100f, 0, 20f, false, measurer).isEmpty())
    }

    @Test
    fun `调课可以推翻放假但只放行被挪过去的那门`() {
        // 第 1 周周五（09-11）放假，把周三（09-09）的大学物理挪到这天第 5-6 节
        val holiday = HolidayCalendarSettings(
            builtInEnabled = false,
            entries = listOf(
                HolidayCalendarEntry(date = "2026-09-11", kind = HolidayEntryKind.Holiday, name = "校庆"),
            ),
        )
        val move = TemporaryScheduleOverride(
            id = "mv",
            type = TemporaryScheduleOverrideType.MoveCourse,
            sourceDate = "2026-09-09",
            targetDate = "2026-09-11",
            moveCourseId = "c3",
            moveToStartNode = 5,
            moveToEndNode = 6,
        )
        val result = layout(
            schedule = scheduleOf(
                course("c3", "大学物理", dayOfWeek = 3, startNode = 1, endNode = 2),
                course("c5", "体育", dayOfWeek = 5, startNode = 1, endNode = 2),
            ),
            holidayCalendar = holiday,
            overrides = listOf(move),
        )

        val friday = result.blocks.filter { it.dayOfWeek == 5 }
        // 挪过去的课照常出现，周五自己的常规课仍按放假不出
        assertEquals(listOf("大学物理"), friday.map { it.title })
        assertEquals(5, friday.single().startNode)
        // 这天已经排进了课，不再画「全天无课」的整列块
        assertTrue(result.holidays.none { it.dayOfWeek == 5 })
        // 原本那天（周三）不再有这门课
        assertTrue(result.blocks.none { it.dayOfWeek == 3 && it.title == "大学物理" })
    }
}
