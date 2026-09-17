package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CourseTimeSlot(
    @SerialName("dayOfWeek") val dayOfWeek: Int,
    @SerialName("startNode") val startNode: Int,
    @SerialName("endNode") val endNode: Int,
)

@Serializable
enum class CourseCategory {
    @SerialName("course")
    Course,

    @SerialName("exam")
    Exam,
}

@Serializable
data class CourseItem(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("teacher") val teacher: String = "",
    @SerialName("location") val location: String = "",
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("category") val category: CourseCategory = CourseCategory.Course,
    @SerialName("time") val time: CourseTimeSlot,
    @SerialName("reminderOnly") val reminderOnly: Boolean = false,
    @SerialName("slotLabelOverride") val slotLabelOverride: String? = null,
    @SerialName("reminderStartTime") val reminderStartTime: String? = null,
    @SerialName("reminderEndTime") val reminderEndTime: String? = null,
)

@Serializable
data class DailySchedule(
    @SerialName("dayOfWeek") val dayOfWeek: Int,
    @SerialName("courses") val courses: List<CourseItem> = emptyList(),
)

@Serializable
data class TermSchedule(
    @SerialName("termId") val termId: String,
    @SerialName("updatedAt") val updatedAt: String,
    @SerialName("dailySchedules") val dailySchedules: List<DailySchedule> = emptyList(),
)

fun TermSchedule.coursesOfDay(dayOfWeek: Int): List<CourseItem> {
    return dailySchedules.firstOrNull { it.dayOfWeek == dayOfWeek }?.courses.orEmpty()
}

fun CourseItem.isReminderOnly(): Boolean = reminderOnly

fun List<CourseItem>.visibleScheduleCourses(): List<CourseItem> =
    filterNot { it.reminderOnly }

/**
 * 插件课表与手动课程合成一份课程清单，同一个 id 两边都有时以手动那份为准。
 *
 * 用户改插件课时，改动是以同 id 的手动课形式落库的，插件原件仍在课表里，
 * 所以读课的地方都要走这里，否则一门改过的课会同时按新旧两份算，
 * 网格里显示两遍，提醒也会重复响。
 */
fun mergeCourseSources(
    pluginCourses: List<CourseItem>,
    manualCourses: List<CourseItem>,
): List<CourseItem> {
    if (manualCourses.isEmpty()) return pluginCourses
    val overriddenIds = manualCourses.mapTo(mutableSetOf()) { it.id }
    return pluginCourses.filterNot { it.id in overriddenIds } + manualCourses
}

/** [mergeCourseSources] 的便捷写法：课表为 null 时只剩手动课程。 */
fun TermSchedule?.allCoursesWith(manualCourses: List<CourseItem>): List<CourseItem> =
    mergeCourseSources(this?.dailySchedules.orEmpty().flatMap { it.courses }, manualCourses)

