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
     * [label] text remains a stable reminder key; built-in IDs supply localized display labels.
     */
    @SerialName("labelKey") val labelKey: String? = null,
)

@Serializable
data class TermTimingProfile(
    @SerialName("termStartDate") val termStartDate: String,
    @SerialName("slotTimes") val slotTimes: List<ClassSlotTime>,
    @SerialName("timezone") val timezone: String = "",
)

/** Null for absent or invalid term dates; timing profiles can exist without a date. */
fun TermTimingProfile.termStartLocalDate(): LocalDate? =
    termStartDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

fun ClassSlotTime.startLocalTime(): LocalTime = LocalTime.parse(startTime)

fun ClassSlotTime.endLocalTime(): LocalTime = LocalTime.parse(endTime)

/**
 * Return null for invalid imported times so one bad period cannot abort reminder
 * synchronization.
 */
fun ClassSlotTime.startLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(startTime) }.getOrNull()

fun ClassSlotTime.endLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(endTime) }.getOrNull()

fun TermTimingProfile.findSlot(startNode: Int, endNode: Int): ClassSlotTime? {
    return slotTimes.firstOrNull { it.startNode == startNode && it.endNode == endNode }
}

/** Both typed clock times when they are set and in order; null means the course follows its periods. */
fun CourseItem.typedClock(): Pair<LocalTime, LocalTime>? {
    val start = reminderStartTime?.let { runCatching { LocalTime.parse(it.trim()) }.getOrNull() } ?: return null
    val end = reminderEndTime?.let { runCatching { LocalTime.parse(it.trim()) }.getOrNull() } ?: return null
    return (start to end).takeIf { start.isBefore(end) }
}

/** The times [course] actually runs: its typed clock first, then the period matching its nodes. */
fun TermTimingProfile.slotFor(course: CourseItem): ClassSlotTime? {
    val period = findSlot(course.time.startNode, course.time.endNode)
    val (start, end) = course.typedClock() ?: return period
    return ClassSlotTime(
        startNode = course.time.startNode,
        endNode = course.time.endNode,
        startTime = start.toString(),
        endTime = end.toString(),
        label = period?.label.orEmpty(),
        labelKey = period?.labelKey,
    )
}

/** Resolve covered timing slots with sorted zero-based indices for localized display labels. */
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
