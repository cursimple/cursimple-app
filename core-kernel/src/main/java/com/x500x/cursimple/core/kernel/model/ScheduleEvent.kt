package com.x500x.cursimple.core.kernel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * 用户自己排进课表的一件事（事务）：组会、社团、考试以外的安排都算。
 *
 * 和课不同，它按真实日期与钟点存，不挂节次，也不属于哪个学期——
 * 换课表、重新导课都不会把它弄丢。网格按钟点把它画在该在的位置，
 * 和节次对不齐也照画，落在午休、晚上这类没有节次的时段时网格会临时插一段出来。
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
    /** 从 [date] 起每周同一天重复 */
    @SerialName("repeatWeekly") val repeatWeekly: Boolean = false,
    /** 重复到哪天为止（含当天）；为空表示一直重复 */
    @SerialName("repeatUntil") val repeatUntil: String? = null,
    /** 自选颜色；为空时按标题从课表配色里取，和课程块一个路数 */
    @SerialName("colorArgb") val colorArgb: Long? = null,
    /** 组件自动生成的事务关联原始内容，点击时由宿主打开该内容。手动事务为空。 */
    @SerialName("source") val source: ScheduleEventSource? = null,
) {
    val localDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
    val startLocalTime: LocalTime? get() = parseClock(startTime)
    val endLocalTime: LocalTime? get() = parseClock(endTime)

    /** 自零点起的分钟数，网格按它排位置 */
    val startMinute: Int? get() = startLocalTime?.let { it.hour * 60 + it.minute }
    val endMinute: Int? get() = endLocalTime?.let { it.hour * 60 + it.minute }

    /** 时间填得通：结束晚于开始。跨零点的不收，网格一天只画一天 */
    val isValid: Boolean
        get() {
            val start = startMinute ?: return false
            val end = endMinute ?: return false
            return localDate != null && end > start && title.isNotBlank()
        }

    /** 这件事 [day] 当天有没有。 */
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

/** [day] 当天所有的事务，按开始时间排好。 */
fun List<ScheduleEvent>.occurrencesOn(day: LocalDate): List<ScheduleEvent> =
    filter { it.occursOn(day) }.sortedWith(compareBy({ it.startMinute }, { it.endMinute }, { it.title }))
