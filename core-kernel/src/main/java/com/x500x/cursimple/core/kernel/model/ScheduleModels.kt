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
    /** Sparse per-week locations override [location]; missing legacy data defaults empty. */
    @SerialName("weekLocations") val weekLocations: Map<Int, String> = emptyMap(),
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("category") val category: CourseCategory = CourseCategory.Course,
    @SerialName("time") val time: CourseTimeSlot,
    @SerialName("reminderOnly") val reminderOnly: Boolean = false,
    @SerialName("slotLabelOverride") val slotLabelOverride: String? = null,
    @SerialName("reminderStartTime") val reminderStartTime: String? = null,
    @SerialName("reminderEndTime") val reminderEndTime: String? = null,
    /**
     * Manual tombstones override plugin courses by ID; [mergeCourseSources] filters them
     * consistently.
     */
    @SerialName("hidden") val hidden: Boolean = false,
    /**
     * Plugin-defined additional fields keep their supplied labels and order; legacy data
     * defaults empty.
     */
    @SerialName("details") val details: List<CourseDetailField> = emptyList(),
)

@Serializable
data class CourseDetailField(
    @SerialName("label") val label: String,
    @SerialName("value") val value: String,
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

/** Use a nonblank per-week location, otherwise default [location]. */
fun CourseItem.locationForWeek(week: Int?): String =
    week?.let { weekLocations[it] }?.takeIf { it.isNotBlank() } ?: location

fun List<CourseItem>.visibleScheduleCourses(): List<CourseItem> =
    filterNot { it.reminderOnly }

/**
 * Manual courses override plugin courses by ID. Every consumer must merge sources to avoid
 * duplicate edited courses and reminders.
 */
fun mergeCourseSources(
    pluginCourses: List<CourseItem>,
    manualCourses: List<CourseItem>,
): List<CourseItem> {
    if (manualCourses.isEmpty()) return pluginCourses
    val overriddenIds = manualCourses.mapTo(mutableSetOf()) { it.id }
    return (pluginCourses.filterNot { it.id in overriddenIds } + manualCourses)
        .filterNot { it.hidden }
}

/** Retain deleted-course tombstones for restoration UI. */
fun List<CourseItem>.hiddenCourses(): List<CourseItem> = filter { it.hidden }

/** Nullable-schedule convenience for [mergeCourseSources]. */
fun TermSchedule?.allCoursesWith(manualCourses: List<CourseItem>): List<CourseItem> =
    mergeCourseSources(this?.dailySchedules.orEmpty().flatMap { it.courses }, manualCourses)

