package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * Date-and-time events are independent of course periods and terms; insert temporary display
 * bands outside configured class hours.
 */
@Serializable
data class ScheduleEvent(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    /** 「2026-09-25」 */
    @SerialName("date") val date: String,
    /** 「12:10」 */
    @SerialName("startTime") val startTime: String,
    @SerialName("endTime") val endTime: String,
    @SerialName("location") val location: String = "",
    @SerialName("note") val note: String = "",
    /** Repeat weekly from [date]. */
    @SerialName("repeatWeekly") val repeatWeekly: Boolean = false,
    /** Inclusive repeat end; null means unbounded. */
    @SerialName("repeatUntil") val repeatUntil: String? = null,
    @SerialName("colorArgb") val colorArgb: Long? = null,
    @SerialName("source") val source: ScheduleEventSource? = null,
) {
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
    val startLocalTime: LocalTime? get() = parseClock(startTime)
    val endLocalTime: LocalTime? get() = parseClock(endTime)

    val startMinute: Int? get() = startLocalTime?.let { it.hour * 60 + it.minute }
    val endMinute: Int? get() = endLocalTime?.let { it.hour * 60 + it.minute }

    /** Require end after start on the same day; cross-midnight events are unsupported. */
    val isValid: Boolean
        get() {
            val start = startMinute ?: return false
            val end = endMinute ?: return false
            return localDate != null && end > start && title.isNotBlank()
        }

    fun occursOn(day: LocalDate): Boolean {
        val first = localDate ?: return false
        if (!isValid) return false
        if (!repeatWeekly) return day == first
        if (day.isBefore(first) || day.dayOfWeek != first.dayOfWeek) return false
        val until = repeatUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return until == null || !day.isAfter(until)
    }

    private fun parseClock(raw: String): LocalTime? = runCatching {
        val (h, m) = raw.trim().split(':').map { it.toInt() }
        LocalTime.of(h, m)
    }.getOrNull()
}

@Serializable
data class ScheduleEventSource(
    val componentId: String,
    val itemId: String,
)

fun List<ScheduleEvent>.occurrencesOn(day: LocalDate): List<ScheduleEvent> =
    filter { it.occursOn(day) }.sortedWith(compareBy({ it.startMinute }, { it.endMinute }, { it.title }))
