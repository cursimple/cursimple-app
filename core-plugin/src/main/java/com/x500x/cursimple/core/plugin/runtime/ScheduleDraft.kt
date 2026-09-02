package com.x500x.cursimple.core.plugin.runtime

import com.x500x.cursimple.core.kernel.model.CourseCategory
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.CourseTimeSlot
import com.x500x.cursimple.core.kernel.model.DailySchedule
import com.x500x.cursimple.core.kernel.model.TermSchedule
import com.x500x.cursimple.core.plugin.manifest.PluginRuntimeLimits
import com.x500x.cursimple.core.plugin.R
import com.x500x.cursimple.core.plugin.pluginRequire
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
        pluginRequire(courses.size <= limits.maxCourses, R.string.plugin_error_draft_too_many_courses)
        return courses.mapIndexed { index, draft ->
            val title = draft.title.trim()
            pluginRequire(title.isNotBlank(), R.string.plugin_error_draft_blank_course_title)
            pluginRequire(
                draft.dayOfWeek in 1..7,
                R.string.plugin_error_draft_invalid_day_of_week,
                draft.dayOfWeek,
            )
            pluginRequire(
                draft.startNode in 1..32,
                R.string.plugin_error_draft_invalid_start_node,
                draft.startNode,
            )
            pluginRequire(
                draft.endNode in draft.startNode..32,
                R.string.plugin_error_draft_invalid_end_node,
                draft.endNode,
            )
            pluginRequire(draft.weeks.all { it in 1..60 }, R.string.plugin_error_draft_invalid_weeks)
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
