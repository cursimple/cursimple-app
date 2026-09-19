package com.x500x.cursimple.core.data.term

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户管理的单个学期。[termStartDate] 是第一周的周一（或第一周内的任意一天），用于计算当前周次。
 * 每个学期以 [id] 关联各自的课表快照与手动课程列表。
 */
@Serializable
data class TermProfile(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    /** ISO yyyy-MM-dd 格式；为 null 表示尚未设置，需要询问用户。 */
    @SerialName("termStartDate") val termStartDate: String? = null,
    @SerialName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    /** 绑定的作息 id；为 null 表示跟随当前选中的那一套。 */
    @SerialName("timingProfileId") val timingProfileId: String? = null,
    /**
     * 用户自己加出来的空白周数。
     *
     * 周数本来是从课程周次反推的，最后一周之后就翻不动了。实习、考试周这种
     * 没有课但确实存在的周，只能由用户自己接在后面，这里记的就是加了几周。
     */
    @SerialName("extraWeekCount") val extraWeekCount: Int = 0,
)

/** 活动学期的开学日期（ISO yyyy-MM-dd）；列表里没有该学期时为 null。 */
fun List<TermProfile>.termStartDateIsoOf(activeTermId: String): String? =
    firstOrNull { it.id == activeTermId }?.termStartDate
