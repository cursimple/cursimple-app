package com.kebiao.viewer.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CourseTimeSlot(
    @SerialName("dayOfWeek") val dayOfWeek: Int,
    @SerialName("startNode") val startNode: Int,
    @SerialName("endNode") val endNode: Int,
)

@Serializable
data class CourseItem(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("teacher") val teacher: String = "",
    @SerialName("location") val location: String = "",
    @SerialName("weeks") val weeks: List<Int> = emptyList(),
    @SerialName("time") val time: CourseTimeSlot,
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

