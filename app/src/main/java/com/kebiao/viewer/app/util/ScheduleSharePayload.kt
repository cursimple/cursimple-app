package com.kebiao.viewer.app.util

import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import kotlinx.serialization.Serializable

/**
 * Compact, version-tagged payload exchanged via QR / 口令.
 * Kept intentionally small: only the data a recipient needs to see the schedule.
 */
@Serializable
data class ScheduleSharePayload(
    val v: Int = CURRENT_VERSION,
    val termName: String? = null,
    val termStartDate: String? = null,
    val schedule: TermSchedule? = null,
    val manualCourses: List<CourseItem> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
        const val MAGIC_PREFIX: String = "CSV1:"
    }
}
