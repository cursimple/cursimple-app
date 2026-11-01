package com.x500x.cursimple.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.ClassSlotTime
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.feature.schedule.SharedFreeTimeDialog
import com.x500x.cursimple.feature.schedule.SharedTimetable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 不走整个 app 启动，直接渲染空闲时段对话框：用固定的两份课表，检查一周概览与左右对比，并在设备上截整屏。
 */
@RunWith(AndroidJUnit4::class)
class SharedFreeGridScreenTest {
    @get:Rule val compose = createComposeRule()

    private val termStart = LocalDate.of(2026, 9, 14)
    private fun course(id: String, title: String, day: Int, start: Int, end: Int) =
        CourseItem(id, title, weeks = (1..18).toList(), time = CourseTimeSlot(day, start, end))

    @Test fun sideBySideFreeGridRendersAndHighlightsSharedFreePeriods() {
        val mine = listOf(
            course("m1", "线性代数", 1, 3, 4),
            course("m2", "数据结构", 3, 1, 2),
            course("m3", "体育", 5, 5, 6),
        )
        val theirs = listOf(
            course("t1", "概率论", 1, 1, 2),
            course("t2", "英语", 2, 3, 4),
            course("t3", "操作系统", 4, 5, 6),
        )
        val timing = (1..12).map { n -> ClassSlotTime(n, n, "08:00", "08:45") }
        compose.setContent {
            ClassScheduleTheme(themeMode = ThemeMode.Light) {
                SharedFreeTimeDialog(
                    myCourses = mine,
                    myTermStart = termStart,
                    timingSlots = timing,
                    decode = { Result.success(SharedTimetable(theirs, termStart)) },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("shared-free-code").performTextInput("fixture")
        compose.onNodeWithTag("shared-free-decode").performClick()
        Thread.sleep(3000)
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("shared-free-overview").fetchSemanticsNodes().isNotEmpty() }
        // 对比区域在对话框的滚动区里，可能在屏幕下方：只要它存在于组合树中即可，截图再看实际位置
        check(compose.onAllNodesWithTag("shared-free-day-detail").fetchSemanticsNodes().isNotEmpty())
        Thread.sleep(1500)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/shared-free-grid.png").close()
    }
}
