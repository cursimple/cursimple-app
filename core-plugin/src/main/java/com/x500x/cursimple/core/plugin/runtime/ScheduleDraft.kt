package com.x500x.cursimple.core.plugin.runtime

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.plugin.manifest.PluginRuntimeLimits
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.OffsetDateTime

@Serializable
data class ScheduleDraft(
    @SerialName("termId") val termId: String,
    @SerialName("courses") val courses: List<ScheduleDraftCourse> = emptyList(),
    @SerialName("updatedAt") val updatedAt: String? = null,
) {
    fun toTermSchedule(limits: PluginRuntimeLimits = PluginRuntimeLimits()): TermSchedule {
        val normalized = validateAndNormalizeCourses(limits)
        return TermSchedule(
            termId = termId.ifBlank { "plugin" },
            updatedAt = updatedAt?.takeIf(String::isNotBlank) ?: OffsetDateTime.now().toString(),
            dailySchedules = normalized
                .groupBy { it.time.dayOfWeek }
                .toSortedMap()
                .map { (day, courses) ->
                    DailySchedule(
                        dayOfWeek = day,
                        courses = courses.sortedWith(
                            compareBy<CourseItem> { it.time.startNode }
                                .thenBy { it.time.endNode }
                                .thenBy { it.title },
                        ),
                    )
                },
        )
    }

    private fun validateAndNormalizeCourses(limits: PluginRuntimeLimits): List<CourseItem> {
        require(courses.size <= limits.maxCourses) { "插件返回的课程数量过多" }
        return courses.mapIndexed { index, draft ->
            val title = draft.title.trim()
            require(title.isNotBlank()) { "插件返回了空课程名称" }
            require(draft.dayOfWeek in 1..7) { "插件返回了无效星期: ${draft.dayOfWeek}" }
            require(draft.startNode in 1..32) { "插件返回了无效开始节次: ${draft.startNode}" }
            require(draft.endNode in draft.startNode..32) { "插件返回了无效结束节次: ${draft.endNode}" }
            require(draft.weeks.all { it in 1..60 }) { "插件返回了无效教学周" }
            val id = draft.id?.trim()?.takeIf(String::isNotBlank)
                ?: stableCourseId(index, draft)
            CourseItem(
                id = id,
                title = title,
                teacher = draft.teacher.orEmpty().trim(),
                location = draft.location.orEmpty().trim(),
                weeks = draft.weeks.distinct().sorted(),
                category = draft.category,
                time = CourseTimeSlot(
                    dayOfWeek = draft.dayOfWeek,
                    startNode = draft.startNode,
                    endNode = draft.endNode,
                ),
            )
        }
    }
}

@Serializable
data class ScheduleDraftCourse(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String,
    @SerialName("teacher") val teacher: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("dayOfWeek") val dayOfWeek: Int,
    @SerialName("startNode") val startNode: Int,
    @SerialName("endNode") val endNode: Int,
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("category") val category: CourseCategory = CourseCategory.Course,
)

private fun stableCourseId(index: Int, draft: ScheduleDraftCourse): String {
    val raw = listOf(
        index.toString(),
        draft.title,
        draft.teacher.orEmpty(),
        draft.location.orEmpty(),
        draft.dayOfWeek.toString(),
        draft.startNode.toString(),
        draft.endNode.toString(),
        draft.weeks.joinToString(","),
    ).joinToString("|")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "plugin-${digest.take(16)}"
}
