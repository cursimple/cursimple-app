package com.x500x.cursimple.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.feature.schedule.CourseDetailDialog
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Opt-in check that the course detail card wraps short content instead of reserving
 * a fixed height, while still capping and scrolling long content.
 */
class CourseDetailHeightUiQaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()
    private fun enabled() = args.getString("courseDetailQa") == "true"

    private val short = CourseItem("qa-short", "短课", teacher = "杨玉洁", location = "东16-A-109",
        weeks = (3..12).toList(), time = CourseTimeSlot(6, 3, 3))

    private val long = CourseItem("qa-long", "课程名称很长很长很长用于测试多行标题的换行表现",
        teacher = "张三、李四、王五、赵六", location = "东16-A-109 综合实验楼三层最里面那间",
        weeks = (1..20).toList(), time = CourseTimeSlot(6, 3, 3),
        details = listOf(
            com.x500x.cursimple.core.kernel.model.CourseDetailField("课程序号", "806349"),
            com.x500x.cursimple.core.kernel.model.CourseDetailField("课程代码", "01212TS006"),
            com.x500x.cursimple.core.kernel.model.CourseDetailField("学分", "1"),
            com.x500x.cursimple.core.kernel.model.CourseDetailField("备注", "第一周停课，其余照常"),
            com.x500x.cursimple.core.kernel.model.CourseDetailField("考核方式", "考查"),
            com.x500x.cursimple.core.kernel.model.CourseDetailField("开课单位", "生命科学学院"),
        ))

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(context.filesDir, "course-detail-height/${args.getString("qaVariant") ?: "normal"}/$name.png")
        ScreenshotCapture.save(ScreenshotCapture.take(), file)
    }

    private fun render(course: CourseItem) {
        compose.activity.setContent {
            ClassScheduleTheme(
                themeMode = if (args.getString("qaDark") == "true") ThemeMode.Dark else ThemeMode.Light,
                themeAccent = if (args.getString("qaBlue") == "true") ThemeAccent.Blue else ThemeAccent.Green,
            ) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                    args.getString("qaFontScale")?.toFloatOrNull() ?: 1f)) {
                    CourseDetailDialog(courses = listOf(course), timingProfile = null, visibleWeekNumber = 7,
                        isManual = { true }, readOnly = true, onDismiss = {}, onSetReminder = {}, onDelete = {})
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun shortDetailsWrapAndLongDetailsStayScrollable() {
        assumeTrue(enabled())
        AppLocale.cache(context, AppLanguage.Chinese)
        render(short)
        compose.onNodeWithTag("course-detail-card").assertIsDisplayed()
        capture("short")

        render(long)
        compose.onNodeWithTag("course-detail-card").assertIsDisplayed()
        capture("long")
    }
}
