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
