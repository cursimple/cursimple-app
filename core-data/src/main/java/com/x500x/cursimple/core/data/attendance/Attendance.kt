package com.x500x.cursimple.core.data.attendance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AttendanceStatus { Present, Absent, Leave }

/** One class session of a course (by its notebook key and calendar date) and how it went. */
@Serializable
data class AttendanceRecord(
    @SerialName("courseKey") val courseKey: String,
    /** ISO date of the session. */
    @SerialName("date") val date: String,
    @SerialName("status") val status: AttendanceStatus,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
)

/** All marks plus the per-course limit of absences the user wants to stay under. */
@Serializable
data class AttendanceData(
    @SerialName("records") val records: List<AttendanceRecord> = emptyList(),
    /** Absences a course tolerates; courses missing here have no limit. */
    @SerialName("allowances") val allowances: Map<String, Int> = emptyMap(),
) {
    fun statusOf(courseKey: String, date: String): AttendanceStatus? =
        records.firstOrNull { it.courseKey == courseKey && it.date == date }?.status

    /** `(courseKey, date)` to status, for fast lookup while drawing a week. */
    val marks: Map<Pair<String, String>, AttendanceStatus> by lazy {
        records.associate { (it.courseKey to it.date) to it.status }
    }

    fun summaryOf(courseKey: String): AttendanceSummary {
        val mine = records.filter { it.courseKey == courseKey }
        return AttendanceSummary(
            present = mine.count { it.status == AttendanceStatus.Present },
            absent = mine.count { it.status == AttendanceStatus.Absent },
            leave = mine.count { it.status == AttendanceStatus.Leave },
            allowance = allowances[courseKey],
        )
    }
}

data class AttendanceSummary(val present: Int, val absent: Int, val leave: Int, val allowance: Int?) {
    /** Absences still allowed; negative once the limit is passed, null without a limit. */
    val remaining: Int? get() = allowance?.let { it - absent }
    val atLimit: Boolean get() = remaining?.let { it <= 0 } == true
    val total: Int get() = present + absent + leave
}

/** Mark [date] of [courseKey]; a null [status] clears the mark. Pure so it can be tested without storage. */
fun AttendanceData.withMark(courseKey: String, date: String, status: AttendanceStatus?, now: Long): AttendanceData {
    val others = records.filterNot { it.courseKey == courseKey && it.date == date }
    val next = if (status == null) others else others + AttendanceRecord(courseKey, date, status, now)
    return copy(records = next.sortedWith(compareBy({ it.courseKey }, { it.date })))
}

/** Set or clear (null or less than 0) the absence limit of [courseKey]. */
fun AttendanceData.withAllowance(courseKey: String, allowance: Int?): AttendanceData =
    copy(allowances = if (allowance == null || allowance < 0) allowances - courseKey else allowances + (courseKey to allowance))
