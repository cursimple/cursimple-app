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
    /**
     * 某几周单独的地点：周次 → 该周地点。
     *
     * 只给和默认地点不同的周留一条，其余周仍用 [location]。物理实验这种每周换实验室的课，
     * 不必为每周各建一门课。旧数据没有这个字段，反序列化时按空处理，行为和以前一致。
     */
    @SerialName("weekLocations") val weekLocations: Map<Int, String> = emptyMap(),
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("category") val category: CourseCategory = CourseCategory.Course,
    @SerialName("time") val time: CourseTimeSlot,
    @SerialName("reminderOnly") val reminderOnly: Boolean = false,
    @SerialName("slotLabelOverride") val slotLabelOverride: String? = null,
    @SerialName("reminderStartTime") val reminderStartTime: String? = null,
    @SerialName("reminderEndTime") val reminderEndTime: String? = null,
    /**
     * 这门课被用户删掉了。
     *
     * 手动加的课直接删记录就行，插件下发的课删不掉——下次同步它还会回来，
     * 所以改成按原 id 存一条标了这个位的手动课把原件盖住。
     * 过滤只做在 [mergeCourseSources] 一处，读课的地方都走那里，不会漏。
     */
    @SerialName("hidden") val hidden: Boolean = false,
    /**
     * 教务系统带来的附加信息，如课程序号、课程代码、学分，详情里逐条列出。
     *
     * 各校教务给的东西不一样，所以不为每样开一个字段，标签和取值都由插件给，
     * 按插件给的顺序显示。旧数据没有这个字段，按空处理。
     */
    @SerialName("details") val details: List<CourseDetailField> = emptyList(),
)

/** 课程详情里的一条附加信息，如「课程序号：12345」。 */
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

/**
 * 第 [week] 周实际显示的地点。
 *
 * 该周设过单独地点就用它，否则回退到默认 [location]；[week] 为空（比如不知道当前周）时也用默认。
 * 单独地点留空视为没设，同样回退，避免出现空白地点。
 */
fun CourseItem.locationForWeek(week: Int?): String =
    week?.let { weekLocations[it] }?.takeIf { it.isNotBlank() } ?: location

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
    return (pluginCourses.filterNot { it.id in overriddenIds } + manualCourses)
        .filterNot { it.hidden }
}

/** 被用户删掉、仅作为墓碑留在手动课程里的那些；界面据此提供恢复。 */
fun List<CourseItem>.hiddenCourses(): List<CourseItem> = filter { it.hidden }

/** [mergeCourseSources] 的便捷写法：课表为 null 时只剩手动课程。 */
fun TermSchedule?.allCoursesWith(manualCourses: List<CourseItem>): List<CourseItem> =
    mergeCourseSources(this?.dailySchedules.orEmpty().flatMap { it.courses }, manualCourses)

