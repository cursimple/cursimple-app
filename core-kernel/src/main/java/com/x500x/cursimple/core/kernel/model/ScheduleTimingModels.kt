package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class ClassSlotTime(
    @SerialName("startNode") val startNode: Int,
    @SerialName("endNode") val endNode: Int,
    @SerialName("startTime") val startTime: String,
    @SerialName("endTime") val endTime: String,
    @SerialName("label") val label: String = "",
    /**
     * 内置模板填入的标签标识，只用于按当前语言显示。
     * [label] 保持语言无关：按标签匹配的提醒规则用它作键，跟随语言会让已存规则失配。
     */
    @SerialName("labelKey") val labelKey: String? = null,
)

@Serializable
data class TermTimingProfile(
    @SerialName("termStartDate") val termStartDate: String,
    @SerialName("slotTimes") val slotTimes: List<ClassSlotTime>,
    @SerialName("timezone") val timezone: String = "",
)

/**
 * 开学日期为空或无法解析时返回 null。
 * 节次时间表可以在还没有开学日期时保存，调用方必须自行处理这一状态。
 */
fun TermTimingProfile.termStartLocalDate(): LocalDate? =
    termStartDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun ClassSlotTime.startLocalTime(): LocalTime = LocalTime.parse(startTime)

fun ClassSlotTime.endLocalTime(): LocalTime = LocalTime.parse(endTime)

/**
 * 容错解析节次时间：插件同步、备份恢复、AI 导入写入的时间串未必是合法 ISO（如 "8:00"、"24:10"），
 * 解析失败时返回 null，调用方跳过该节次而不是让整轮提醒同步协程崩掉。
 */
fun ClassSlotTime.startLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(startTime) }.getOrNull()

fun ClassSlotTime.endLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(endTime) }.getOrNull()

fun TermTimingProfile.findSlot(startNode: Int, endNode: Int): ClassSlotTime? {
    return slotTimes.firstOrNull { it.startNode == startNode && it.endNode == endNode }
}

/**
 * 课程第 [startNode]..[endNode] 节覆盖到的节次时段，按节次先后排列。
 *
 * 带上在排序后作息表里的序号（从 0 起），界面据此取「第一节」「午间课」这类名字；
 * 标签为空时也能按序号生成，和课表左侧的节次栏一致。
 */
fun TermTimingProfile.slotsCovering(startNode: Int, endNode: Int): List<IndexedValue<ClassSlotTime>> {
    if (endNode < startNode) return emptyList()
    return slotTimes
        .filter { it.endNode >= it.startNode }
        .sortedWith(compareBy({ it.startNode }, { it.endNode }))
        .withIndex()
        .filter { (_, slot) -> slot.startNode <= endNode && slot.endNode >= startNode }
}

fun TermTimingProfile.findSlotByLabel(label: String): ClassSlotTime? {
    val normalized = label.trim()
    if (normalized.isBlank()) return null
    return slotTimes.firstOrNull { it.label == normalized }
}

fun CourseItem.reminderSlotLabel(timingProfile: TermTimingProfile): String? =
    slotLabelOverride?.takeIf { it.isNotBlank() }
        ?: timingProfile.findSlot(time.startNode, time.endNode)?.label?.takeIf { it.isNotBlank() }
