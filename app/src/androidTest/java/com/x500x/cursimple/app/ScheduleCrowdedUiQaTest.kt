package com.x500x.cursimple.app

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import com.x500x.cursimple.core.kernel.model.TermTimingProfile
import com.x500x.cursimple.feature.schedule.ScheduleOverflowState
import com.x500x.cursimple.feature.schedule.ScheduleScreen
import com.x500x.cursimple.feature.schedule.ScheduleUiState
import com.x500x.cursimple.feature.schedule.ScheduleViewMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Opt-in QA: a crowded week scrolls instead of crushing course names, and fits again once trimmed. */
class ScheduleCrowdedUiQaTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext

    private val today = LocalDate.now(ZoneId.systemDefault())
    private val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val timing = TermTimingProfile(monday.toString(), listOf(
        ClassSlotTime(1, 1, "08:00", "09:35", "第一节"), ClassSlotTime(2, 2, "10:05", "11:40", "第二节"),
        ClassSlotTime(3, 3, "12:00", "13:35", "午间课"), ClassSlotTime(4, 4, "14:00", "15:35", "第三节"),
        ClassSlotTime(5, 5, "16:05", "17:40", "第四节"), ClassSlotTime(6, 6, "18:00", "18:45", "晚间课"),
        ClassSlotTime(7, 7, "19:00", "20:35", "第五节"), ClassSlotTime(8, 8, "20:45", "22:20", "第六节")))

    private fun course(id: String, title: String, day: Int, node: Int, end: Int = node) =
        CourseItem(id, title, location = "东-16-B", weeks = (1..16).toList(), time = CourseTimeSlot(day, node, end))
    private val courses = listOf(
        course("a", "汇编语言程序设计", 1, 1), course("b", "数据库原理", 2, 1), course("c", "计算机组成与系统结构", 3, 1),
        course("d", "数据库原理及应用", 4, 1), course("e", "计算机组成与系统结构", 1, 2), course("f", "Web开发技术", 2, 2),
        course("g", "计算机组成与系统结构", 3, 2), course("h", "数值分析", 5, 2), course("i", "MATLAB应用", 1, 4),
        course("j", "机器学习", 2, 4), course("k", "汇编语言程序设计", 3, 4), course("l", "学术英语阅读与写作", 4, 4),
        course("m", "人工智能导论", 5, 4), course("n", "数值分析", 1, 5), course("o", "高级可编程逻辑程序设计", 3, 5),
        course("p", "web上机", 2, 7, 8), course("q", "AI创意工坊", 3, 7), course("r", "数据库上机", 4, 7, 8),
        course("s", "汇编语言程序设计", 5, 7), course("t", "数值分析上机", 7, 1, 2),
    )
    private fun event(id: String, title: String, day: Long, start: String, end: String) =
        ScheduleEvent(id, title, date = monday.plusDays(day).toString(), startTime = start, endTime = end)
    private val extraEvents = listOf(
        event("ican", "ican", 5, "08:00", "09:00"),
        event("lab", "数值分析上机", 6, "08:30", "10:00"),
        event("thu", "学术英语答疑", 3, "14:30", "15:30"),
        event("late", "社团例会", 1, "18:45", "19:00"),
    )

    @Test fun crowdedWeekScrollsAndTrimmedWeekFits() {
        assumeTrue(args.getString("crowdedQa") == "true")
        val events = mutableStateOf(extraEvents)
        show(events)
        save("crowded")
        // At 1.0 the crowded week still reads fine on one page; larger fonts must switch to scrolling.
        if (args.getString("expectCrowdedScroll") == "true") {
            compose.runOnIdle { assertTrue("crowded week should scroll", ScheduleOverflowState.autoScrolling.value) }
        }
        events.value = emptyList()
        save("trimmed")
        compose.runOnIdle { assertFalse("trimmed week should fit one page", ScheduleOverflowState.autoScrolling.value) }
    }

    private fun show(events: androidx.compose.runtime.State<List<ScheduleEvent>>) {
        compose.setContent {
            ClassScheduleTheme(themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = ThemeAccent.Green) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                    args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                    ScheduleScreen(state = ScheduleUiState(initialized = true, timingProfile = timing,
                        manualCourses = courses, events = events.value),
                        onAddManualCourse = {}, onSaveCourse = {}, onSaveEvent = {},
                        onCreateCourseReminder = { _, _, _ -> }, onMuteExamReminder = {}, onRestoreExamReminder = {},
                        onRemoveReminderRule = {}, onRemoveManualCourse = {}, onCreateBulkReminder = { _, _, _ -> },
                        onPrevWeek = {}, onNextWeek = {}, onPrevDay = {}, onNextDay = {}, onResetDay = {},
                        onOpenPluginMarket = {}, overrideTermStart = monday, viewMode = ScheduleViewMode.Week,
                        weekOffset = 0, minWeekOffset = 0, maxWeekOffset = 16, termWeekCount = 16,
                        onWeekOffsetChange = {}, onAddWeek = {},
                        dayOffset = ChronoUnit.DAYS.between(today, monday).toInt())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        val bitmap = checkNotNull((1..5).firstNotNullOfOrNull {
            instrumentation.uiAutomation.takeScreenshot() ?: run { Thread.sleep(300); null }
        })
        val suffix = args.getString("qaFontScale")?.let { ".x$it" }.orEmpty()
        val file = File(context.filesDir, "crowded/$name$suffix.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
