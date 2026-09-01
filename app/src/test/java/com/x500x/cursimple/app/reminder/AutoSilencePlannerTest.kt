package com.x500x.cursimple.app.reminder

import com.x500x.cursimple.core.data.AutoSilenceMode
import com.x500x.cursimple.core.data.AutoSilenceSession
import com.x500x.cursimple.core.data.InterruptionFilterValues
import com.x500x.cursimple.core.data.RingerModeValues
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry
import com.x500x.cursimple.core.kernel.model.HolidayCalendarSettings
import com.x500x.cursimple.core.kernel.model.HolidayEntryKind
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverrideType
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AutoSilencePlannerTest {

    @Test
    fun `连堂课合并成一段`() {
        val blocks = resolveClassBlocks(
            date = MONDAY_WEEK_1,
            courses = listOf(course("a", startNode = 1, endNode = 2), course("b", startNode = 3, endNode = 4)),
            timingProfile = timingProfile(),
            termStart = TERM_START,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )

        assertEquals(
            listOf(block(MONDAY_WEEK_1, 8, 0, 11, 30)),
            blocks,
        )
    }

    @Test
    fun `午休隔开的上下午课分成两段`() {
        val blocks = resolveClassBlocks(
            date = MONDAY_WEEK_1,
            courses = listOf(course("a", startNode = 1, endNode = 2), course("c", startNode = 5, endNode = 6)),
            timingProfile = timingProfile(),
            termStart = TERM_START,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )

        assertEquals(
            listOf(
                block(MONDAY_WEEK_1, 8, 0, 9, 35),
                block(MONDAY_WEEK_1, 14, 0, 15, 35),
            ),
            blocks,
        )
    }

    @Test
    fun `重叠的课程合并后取更晚的结束时间`() {
        val blocks = mergeClassBlocks(
            listOf(
                block(MONDAY_WEEK_1, 8, 0, 9, 35),
                block(MONDAY_WEEK_1, 8, 0, 11, 30),
            ),
        )

        assertEquals(listOf(block(MONDAY_WEEK_1, 8, 0, 11, 30)), blocks)
    }

    @Test
    fun `假日当天没有上课时段`() {
        val blocks = resolveClassBlocks(
            date = MONDAY_WEEK_1,
            courses = listOf(course("a", startNode = 1, endNode = 2)),
            timingProfile = timingProfile(),
            termStart = TERM_START,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings(
                builtInEnabled = false,
                entries = listOf(
                    HolidayCalendarEntry(
                        date = MONDAY_WEEK_1.toString(),
                        kind = HolidayEntryKind.Holiday,
                        name = "校庆",
                    ),
                ),
            ),
        )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `临时调课按来源日的课表产生时段`() {
        val sunday = LocalDate.of(2026, 3, 8)
        val blocks = resolveClassBlocks(
            date = sunday,
            courses = listOf(course("a", startNode = 1, endNode = 2)),
            timingProfile = timingProfile(),
            termStart = TERM_START,
            overrides = listOf(
                TemporaryScheduleOverride(
                    id = "makeup",
                    type = TemporaryScheduleOverrideType.MakeUp,
                    targetDate = sunday.toString(),
                    sourceDate = MONDAY_WEEK_1.toString(),
                ),
            ),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )

        assertEquals(listOf(block(sunday, 8, 0, 9, 35)), blocks)
    }

    @Test
    fun `临时取消的课不产生时段`() {
        val blocks = resolveClassBlocks(
            date = MONDAY_WEEK_1,
            courses = listOf(course("a", startNode = 1, endNode = 2)),
            timingProfile = timingProfile(),
            termStart = TERM_START,
            overrides = listOf(
                TemporaryScheduleOverride(
                    id = "cancel",
                    type = TemporaryScheduleOverrideType.CancelCourse,
                    targetDate = MONDAY_WEEK_1.toString(),
                    cancelStartNode = 1,
                    cancelEndNode = 2,
                ),
            ),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `不在开课周次的课不产生时段`() {
        val blocks = resolveClassBlocks(
            date = MONDAY_WEEK_1,
            courses = listOf(course("a", startNode = 1, endNode = 2, weeks = listOf(2, 3))),
            timingProfile = timingProfile(),
            termStart = TERM_START,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `开学之前不产生时段`() {
        val beforeTerm = TERM_START.minusWeeks(1)
        val blocks = resolveClassBlocks(
            date = beforeTerm,
            courses = listOf(course("a", startNode = 1, endNode = 2)),
            timingProfile = timingProfile(),
            termStart = TERM_START,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )

        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `节次查不到时刻时退回课程自带的提醒时间`() {
        val blocks = resolveClassBlocks(
            date = MONDAY_WEEK_1,
            courses = listOf(
                course("a", startNode = 90, endNode = 91).copy(
                    reminderStartTime = "19:00",
                    reminderEndTime = "20:30",
                ),
            ),
            timingProfile = timingProfile(),
            termStart = TERM_START,
            overrides = emptyList(),
            holidayCalendar = HolidayCalendarSettings.NONE,
        )

        assertEquals(listOf(block(MONDAY_WEEK_1, 19, 0, 20, 30)), blocks)
    }

    @Test
    fun `上课时段开始算在内结束算在外`() {
        val blocks = listOf(block(MONDAY_WEEK_1, 8, 0, 9, 35))

        assertNull(activeClassBlockAt(dateTime(MONDAY_WEEK_1, 7, 59), blocks))
        assertEquals(blocks.first(), activeClassBlockAt(dateTime(MONDAY_WEEK_1, 8, 0), blocks))
        assertEquals(blocks.first(), activeClassBlockAt(dateTime(MONDAY_WEEK_1, 9, 34), blocks))
        assertNull(activeClassBlockAt(dateTime(MONDAY_WEEK_1, 9, 35), blocks))
    }

    @Test
    fun `下一次切换时刻取最近的上课或下课时刻`() {
        val blocks = listOf(
            block(MONDAY_WEEK_1, 8, 0, 9, 35),
            block(MONDAY_WEEK_1, 14, 0, 15, 35),
        )

        assertEquals(dateTime(MONDAY_WEEK_1, 8, 0), nextClassBoundaryAfter(dateTime(MONDAY_WEEK_1, 7, 0), blocks))
        assertEquals(dateTime(MONDAY_WEEK_1, 9, 35), nextClassBoundaryAfter(dateTime(MONDAY_WEEK_1, 8, 30), blocks))
        assertEquals(dateTime(MONDAY_WEEK_1, 14, 0), nextClassBoundaryAfter(dateTime(MONDAY_WEEK_1, 9, 35), blocks))
        assertNull(nextClassBoundaryAfter(dateTime(MONDAY_WEEK_1, 20, 0), blocks))
    }

    @Test
    fun `上课时段内且功能开启时进入静音`() {
        val blocks = listOf(block(MONDAY_WEEK_1, 8, 0, 9, 35))

        val decision = decideAutoSilence(
            now = dateTime(MONDAY_WEEK_1, 8, 10),
            nowMillis = 1_000L,
            blocks = blocks,
            session = AutoSilenceSession(),
            featureEnabled = true,
        )

        assertEquals(AutoSilenceDecision.Enter(blocks.first()), decision)
    }

    @Test
    fun `功能关闭时不进入静音`() {
        val decision = decideAutoSilence(
            now = dateTime(MONDAY_WEEK_1, 8, 10),
            nowMillis = 1_000L,
            blocks = listOf(block(MONDAY_WEEK_1, 8, 0, 9, 35)),
            session = AutoSilenceSession(),
            featureEnabled = false,
        )

        assertEquals(AutoSilenceDecision.Idle, decision)
    }

    @Test
    fun `手动恢复后的抑制期内不再进入静音`() {
        val decision = decideAutoSilence(
            now = dateTime(MONDAY_WEEK_1, 8, 10),
            nowMillis = 1_000L,
            blocks = listOf(block(MONDAY_WEEK_1, 8, 0, 9, 35)),
            session = AutoSilenceSession(suppressedUntilMillis = 5_000L),
            featureEnabled = true,
        )

        assertEquals(AutoSilenceDecision.Idle, decision)
    }

    @Test
    fun `连堂课中途保持静音不反复切换`() {
        val decision = decideAutoSilence(
            now = dateTime(MONDAY_WEEK_1, 9, 45),
            nowMillis = 1_000L,
            blocks = listOf(block(MONDAY_WEEK_1, 8, 0, 11, 30)),
            session = AutoSilenceSession(active = true),
            featureEnabled = true,
        )

        assertEquals(AutoSilenceDecision.Keep, decision)
    }

    @Test
    fun `下课后恢复`() {
        val decision = decideAutoSilence(
            now = dateTime(MONDAY_WEEK_1, 11, 31),
            nowMillis = 1_000L,
            blocks = listOf(block(MONDAY_WEEK_1, 8, 0, 11, 30)),
            session = AutoSilenceSession(active = true),
            featureEnabled = true,
        )

        assertEquals(AutoSilenceDecision.Restore, decision)
    }

    @Test
    fun `静音期间关掉开关也要恢复`() {
        val decision = decideAutoSilence(
            now = dateTime(MONDAY_WEEK_1, 8, 30),
            nowMillis = 1_000L,
            blocks = listOf(block(MONDAY_WEEK_1, 8, 0, 11, 30)),
            session = AutoSilenceSession(active = true),
            featureEnabled = false,
        )

        assertEquals(AutoSilenceDecision.Restore, decision)
    }

    @Test
    fun `超过计划结束时刻加宽限期判为过期`() {
        val session = AutoSilenceSession(
            active = true,
            startedAtMillis = 1_000L,
            plannedEndAtMillis = 100_000L,
        )

        assertFalse(isAutoSilenceSessionExpired(session, 100_000L + AUTO_SILENCE_EXPIRY_GRACE_MILLIS))
        assertTrue(isAutoSilenceSessionExpired(session, 100_001L + AUTO_SILENCE_EXPIRY_GRACE_MILLIS))
    }

    @Test
    fun `没有计划结束时刻也受绝对上限约束`() {
        val session = AutoSilenceSession(active = true, startedAtMillis = 1_000L)

        assertFalse(isAutoSilenceSessionExpired(session, 1_000L + AUTO_SILENCE_MAX_SESSION_MILLIS))
        assertTrue(isAutoSilenceSessionExpired(session, 1_001L + AUTO_SILENCE_MAX_SESSION_MILLIS))
    }

    @Test
    fun `系统时间被往回调判为过期`() {
        val session = AutoSilenceSession(active = true, startedAtMillis = 10_000_000L, plannedEndAtMillis = 0L)

        assertTrue(isAutoSilenceSessionExpired(session, 10_000_000L - 2 * AUTO_SILENCE_CLOCK_REWIND_TOLERANCE_MILLIS))
    }

    @Test
    fun `没有现场记录时不判过期`() {
        assertFalse(isAutoSilenceSessionExpired(AutoSilenceSession(), Long.MAX_VALUE))
    }

    @Test
    fun `响铃状态下按模式切换`() {
        assertEquals(
            RingerModeValues.VIBRATE,
            resolveRingerModeToApply(AutoSilenceMode.Vibrate, RingerModeValues.NORMAL),
        )
        assertEquals(
            RingerModeValues.SILENT,
            resolveRingerModeToApply(AutoSilenceMode.Silent, RingerModeValues.NORMAL),
        )
        assertEquals(
            RingerModeValues.SILENT,
            resolveRingerModeToApply(AutoSilenceMode.Silent, RingerModeValues.VIBRATE),
        )
    }

    @Test
    fun `手机本来就够安静时不动手`() {
        assertNull(resolveRingerModeToApply(AutoSilenceMode.Vibrate, RingerModeValues.VIBRATE))
        assertNull(resolveRingerModeToApply(AutoSilenceMode.Vibrate, RingerModeValues.SILENT))
        assertNull(resolveRingerModeToApply(AutoSilenceMode.Silent, RingerModeValues.SILENT))
        assertNull(resolveRingerModeToApply(AutoSilenceMode.DoNotDisturb, RingerModeValues.NORMAL))
    }

    @Test
    fun `勿扰模式只在未开启勿扰时写入仅优先级`() {
        assertEquals(
            InterruptionFilterValues.PRIORITY,
            resolveInterruptionFilterToApply(AutoSilenceMode.DoNotDisturb, InterruptionFilterValues.ALL),
        )
        assertNull(
            resolveInterruptionFilterToApply(AutoSilenceMode.DoNotDisturb, InterruptionFilterValues.PRIORITY),
        )
        assertNull(
            resolveInterruptionFilterToApply(AutoSilenceMode.DoNotDisturb, InterruptionFilterValues.NONE),
        )
        assertNull(
            resolveInterruptionFilterToApply(AutoSilenceMode.Silent, InterruptionFilterValues.ALL),
        )
    }

    @Test
    fun `恢复时写回上课前的铃声模式`() {
        val session = AutoSilenceSession(
            active = true,
            previousRingerMode = RingerModeValues.NORMAL,
            appliedRingerMode = RingerModeValues.SILENT,
        )

        assertEquals(RingerModeValues.NORMAL, resolveRingerModeToRestore(session, RingerModeValues.SILENT))
    }

    @Test
    fun `上课前本来就静音时不会被恢复成响铃`() {
        val session = AutoSilenceSession(
            active = true,
            previousRingerMode = RingerModeValues.SILENT,
            appliedRingerMode = RingerModeValues.SILENT,
        )

        assertNull(resolveRingerModeToRestore(session, RingerModeValues.SILENT))
    }

    @Test
    fun `用户上课途中自己改过铃声就不覆盖`() {
        val session = AutoSilenceSession(
            active = true,
            previousRingerMode = RingerModeValues.NORMAL,
            appliedRingerMode = RingerModeValues.SILENT,
        )

        assertNull(resolveRingerModeToRestore(session, RingerModeValues.VIBRATE))
    }

    @Test
    fun `没有记录过铃声模式时不恢复`() {
        val session = AutoSilenceSession(active = true)

        assertNull(resolveRingerModeToRestore(session, RingerModeValues.SILENT))
    }

    @Test
    fun `恢复时写回上课前的勿扰级别`() {
        val session = AutoSilenceSession(
            active = true,
            previousInterruptionFilter = InterruptionFilterValues.ALL,
            appliedInterruptionFilter = InterruptionFilterValues.PRIORITY,
        )

        assertEquals(
            InterruptionFilterValues.ALL,
            resolveInterruptionFilterToRestore(session, InterruptionFilterValues.PRIORITY),
        )
        assertNull(resolveInterruptionFilterToRestore(session, InterruptionFilterValues.NONE))
    }

    private fun course(
        id: String,
        startNode: Int,
        endNode: Int,
        dayOfWeek: Int = 1,
        weeks: List<Int> = emptyList(),
    ): CourseItem = CourseItem(
        id = id,
        title = id,
        weeks = weeks,
        time = CourseTimeSlot(dayOfWeek = dayOfWeek, startNode = startNode, endNode = endNode),
    )

    private fun timingProfile(): TermTimingProfile = TermTimingProfile(
        termStartDate = TERM_START.toString(),
        slotTimes = listOf(
            ClassSlotTime(1, 1, "08:00", "08:45"),
            ClassSlotTime(2, 2, "08:50", "09:35"),
            ClassSlotTime(3, 3, "09:55", "10:40"),
            ClassSlotTime(4, 4, "10:45", "11:30"),
            ClassSlotTime(5, 5, "14:00", "14:45"),
            ClassSlotTime(6, 6, "14:50", "15:35"),
        ),
    )

    private fun block(date: LocalDate, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): ClassBlock =
        ClassBlock(
            start = dateTime(date, startHour, startMinute),
            end = dateTime(date, endHour, endMinute),
        )

    private fun dateTime(date: LocalDate, hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(date, LocalTime.of(hour, minute))

    private companion object {
        val TERM_START: LocalDate = LocalDate.of(2026, 3, 2)
        val MONDAY_WEEK_1: LocalDate = LocalDate.of(2026, 3, 2)
    }
}
