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
    /** Dates picked week by week; when set they are the only days the event occurs. */
    @SerialName("weekDates") val weekDates: List<String> = emptyList(),
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

    val pickedDates: List<LocalDate> get() = weekDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()

    val isRecurring: Boolean get() = repeatWeekly || weekDates.size > 1

    /** Term weeks of [pickedDates], counted from [termStart]'s Monday. */
    fun termWeeks(termStart: LocalDate): List<Int> =
        pickedDates.map { resolveTermWeekNumber(termStart, it) }.distinct().sorted()

    fun occursOn(day: LocalDate): Boolean {
        val first = localDate ?: return false
        if (!isValid) return false
        if (weekDates.isNotEmpty()) return day in pickedDates
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

/** The [dayOfWeek] in each of [weeks], counting week 1 from the Monday of [termStart]. */
fun termWeekDates(termStart: LocalDate, dayOfWeek: java.time.DayOfWeek, weeks: Collection<Int>): List<LocalDate> {
    val monday = termStart.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    return weeks.distinct().sorted().map { monday.plusWeeks(it - 1L).plusDays(dayOfWeek.value - 1L) }
}

@Serializable
data class ScheduleEventSource(
    val componentId: String,
    val itemId: String,
)

fun List<ScheduleEvent>.occurrencesOn(day: LocalDate): List<ScheduleEvent> =
    filter { it.occursOn(day) }.sortedWith(compareBy({ it.startMinute }, { it.endMinute }, { it.title }))
