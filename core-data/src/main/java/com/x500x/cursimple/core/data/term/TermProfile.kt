package com.x500x.cursimple.core.data.term

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Term [id] partitions schedules and manual courses; [termStartDate] anchors teaching weeks.
 */
@Serializable
data class TermProfile(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("termStartDate") val termStartDate: String? = null,
    @SerialName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("timingProfileId") val timingProfileId: String? = null,
    /** Explicit blank weeks extend the course-derived term range. */
    @SerialName("extraWeekCount") val extraWeekCount: Int = 0,
    /** Archived terms leave the active list; their statistics stay available. */
    @SerialName("archived") val archived: Boolean = false,
)

fun List<TermProfile>.termStartDateIsoOf(activeTermId: String): String? =
    firstOrNull { it.id == activeTermId }?.termStartDate
