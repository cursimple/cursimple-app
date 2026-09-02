package com.x500x.cursimple.core.reminder.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

class ReminderNotificationTextTest {
    @Test
    fun titleStableTextCarriesFirstCoursePrefixPerPeriod() {
        assertEquals(
            "周一 08:00 上午首次课：高等数学",
            sampleTitle(firstCoursePeriod = ReminderDayPeriod.Morning).stableText(),
        )
        assertEquals(
            "周一 08:00 下午首次课：高等数学",
            sampleTitle(firstCoursePeriod = ReminderDayPeriod.Afternoon).stableText(),
        )
        assertEquals(
            "周一 08:00 晚上首次课：高等数学",
            sampleTitle(firstCoursePeriod = ReminderDayPeriod.Evening).stableText(),
        )
        assertEquals("周一 08:00 高等数学", sampleTitle().stableText())
    }

    @Test
    fun titleStableTextMarksExamCourses() {
        assertEquals(
            "周一 08:00 考试：高等数学",
            sampleTitle(exam = true).stableText(),
        )
        assertEquals(
            "周一 08:00 上午首次课：考试：高等数学",
            sampleTitle(exam = true, firstCoursePeriod = ReminderDayPeriod.Morning).stableText(),
        )
    }

    @Test
    fun titleStableTextAppendsAdvanceOnlyWhenPositive() {
        assertEquals(
            "周一 08:00 高等数学（提前15分钟）",
            sampleTitle(advanceMinutes = 15).stableText(),
        )
        assertEquals(
            "周一 08:00 上午首次课：高等数学（提前15分钟）",
            sampleTitle(advanceMinutes = 15, firstCoursePeriod = ReminderDayPeriod.Morning).stableText(),
        )
        assertEquals("周一 08:00 高等数学", sampleTitle(advanceMinutes = 0).stableText())
    }

    @Test
    fun titleStableTextNamesEveryWeekday() {
        val names = (1..7).map { sampleTitle(dayOfWeek = it).stableText().substringBefore(' ') }

        assertEquals(listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"), names)
        assertEquals("周8", sampleTitle(dayOfWeek = 8).stableText().substringBefore(' '))
    }

    @Test
    fun messageStableTextKeepsDateWeekdayTimeAndNodes() {
        assertEquals(
            "9月7日 周一 08:00-09:35 · 第1-2节 · A101",
            sampleMessage().stableText(),
        )
    }

    @Test
    fun messageStableTextFallsBackToPlaceholderRoom() {
        assertEquals(
            "9月7日 周一 08:00-09:35 · 第1-2节 · 待定教室",
            sampleMessage(location = "  ").stableText(),
        )
    }

    @Test
    fun chineseResourcesRenderTheSameTitleAsStableText() {
        val titles = listOf(
            sampleTitle(),
            sampleTitle(exam = true),
            sampleTitle(advanceMinutes = 15),
            sampleTitle(firstCoursePeriod = ReminderDayPeriod.Morning, advanceMinutes = 15),
            sampleTitle(firstCoursePeriod = ReminderDayPeriod.Afternoon),
            sampleTitle(firstCoursePeriod = ReminderDayPeriod.Evening, exam = true),
            sampleTitle(dayOfWeek = 7),
            sampleTitle(dayOfWeek = 8),
        )

        titles.forEach { assertEquals(it.stableText(), renderTitleFromResources(it)) }
    }

    @Test
    fun chineseResourcesRenderTheSameMessageAsStableText() {
        val messages = listOf(
            sampleMessage(),
            sampleMessage(location = ""),
            sampleMessage(dayOfWeek = 7),
            sampleMessage(dayOfWeek = 8),
        )

        messages.forEach { assertEquals(it.stableText(), renderMessageFromResources(it)) }
    }

    @Test
    fun englishResourcesMirrorChinesePlaceholders() {
        val chinese = readStrings(REMINDER_ZH)
        val english = readStrings(REMINDER_EN)

        assertEquals(chinese.keys.sorted(), english.keys.sorted())
        chinese.forEach { (name, value) ->
            assertEquals(name, placeholders(value), placeholders(english.getValue(name)))
        }
    }

    private fun sampleTitle(
        dayOfWeek: Int = 1,
        exam: Boolean = false,
        firstCoursePeriod: ReminderDayPeriod? = null,
        advanceMinutes: Int = 0,
    ): ReminderNotificationTitle = ReminderNotificationTitle(
        dayOfWeek = dayOfWeek,
        startTime = "08:00",
        courseTitle = "高等数学",
        exam = exam,
        firstCoursePeriod = firstCoursePeriod,
        advanceMinutes = advanceMinutes,
    )

    private fun sampleMessage(
        dayOfWeek: Int = 1,
        location: String = "A101",
    ): ReminderNotificationMessage = ReminderNotificationMessage(
        month = 9,
        dayOfMonth = 7,
        dayOfWeek = dayOfWeek,
        startTime = "08:00",
        endTime = "09:35",
        startNode = 1,
        endNode = 2,
        location = location,
    )

    /** 按界面层同样的顺序拼装中文资源，用来核对渲染结果与稳定文本逐字一致。 */
    private fun renderTitleFromResources(title: ReminderNotificationTitle): String {
        val strings = readStrings(REMINDER_ZH)
        val course = if (title.exam) {
            strings.format("reminder_notification_title_exam", title.courseTitle)
        } else {
            title.courseTitle
        }
        val body = when (title.firstCoursePeriod) {
            ReminderDayPeriod.Morning ->
                strings.format("reminder_notification_title_first_course_morning", course)
            ReminderDayPeriod.Afternoon ->
                strings.format("reminder_notification_title_first_course_afternoon", course)
            ReminderDayPeriod.Evening ->
                strings.format("reminder_notification_title_first_course_evening", course)
            null -> course
        }
        val withAdvance = if (title.advanceMinutes > 0) {
            strings.format("reminder_notification_title_advance", body, title.advanceMinutes)
        } else {
            body
        }
        return strings.format(
            "reminder_notification_title",
            weekdayFromResources(title.dayOfWeek),
            title.startTime,
            withAdvance,
        )
    }

    private fun renderMessageFromResources(message: ReminderNotificationMessage): String {
        val strings = readStrings(REMINDER_ZH)
        return strings.format(
            "reminder_notification_message",
            strings.format("reminder_notification_date", message.month, message.dayOfMonth),
            weekdayFromResources(message.dayOfWeek),
            message.startTime,
            message.endTime,
            strings.format("reminder_notification_nodes", message.startNode, message.endNode),
            message.location.ifBlank { strings.getValue("reminder_notification_location_tbd") },
        )
    }

    private fun weekdayFromResources(dayOfWeek: Int): String {
        val kernel = readStrings(KERNEL_ZH)
        val names = listOf(
            "kernel_weekday_monday",
            "kernel_weekday_tuesday",
            "kernel_weekday_wednesday",
            "kernel_weekday_thursday",
            "kernel_weekday_friday",
            "kernel_weekday_saturday",
            "kernel_weekday_sunday",
        )
        return if (dayOfWeek in 1..7) {
            kernel.getValue(names[dayOfWeek - 1])
        } else {
            readStrings(REMINDER_ZH).format("reminder_weekday_other", dayOfWeek)
        }
    }

    private fun Map<String, String>.format(name: String, vararg args: Any): String =
        String.format(Locale.CHINA, getValue(name), *args)

    private fun placeholders(value: String): List<String> =
        PLACEHOLDER.findAll(value).map { it.value }.toList()

    private fun readStrings(path: String): Map<String, String> {
        val text = resolve(path).readText()
        return STRING_ENTRY.findAll(text).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
    }

    private fun resolve(path: String): File =
        listOf(File(path), File("..", path)).first { it.isFile }

    private companion object {
        const val REMINDER_ZH = "core-reminder/src/main/res/values/strings.xml"
        const val REMINDER_EN = "core-reminder/src/main/res/values-en/strings.xml"
        const val KERNEL_ZH = "core-kernel/src/main/res/values/strings.xml"
        val STRING_ENTRY = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val PLACEHOLDER = Regex("""%\d+\$[sd]""")
    }
}
