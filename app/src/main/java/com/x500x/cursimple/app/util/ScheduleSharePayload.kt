package com.x500x.cursimple.app.util

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.serialization.Serializable

/**
 * 通过二维码 / 口令交换的紧凑载荷，带版本号。
 * 只保留接收方查看课表所需的数据。
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
