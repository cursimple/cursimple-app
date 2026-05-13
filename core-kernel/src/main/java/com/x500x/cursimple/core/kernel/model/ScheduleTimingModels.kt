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
)

@Serializable
data class TermTimingProfile(
    @SerialName("termStartDate") val termStartDate: String,
    @SerialName("slotTimes") val slotTimes: List<ClassSlotTime>,
    @SerialName("timezone") val timezone: String = "Asia/Shanghai",
)

fun TermTimingProfile.termStartLocalDate(): LocalDate = LocalDate.parse(termStartDate)

fun ClassSlotTime.startLocalTime(): LocalTime = LocalTime.parse(startTime)

fun ClassSlotTime.endLocalTime(): LocalTime = LocalTime.parse(endTime)

fun TermTimingProfile.findSlot(startNode: Int, endNode: Int): ClassSlotTime? {
    return slotTimes.firstOrNull { it.startNode == startNode && it.endNode == endNode }
}
