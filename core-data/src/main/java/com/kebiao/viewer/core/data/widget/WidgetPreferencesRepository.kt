package com.kebiao.viewer.core.data.widget

import com.kebiao.viewer.core.kernel.model.CourseItem
import com.kebiao.viewer.core.kernel.model.TermSchedule
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.reminder.model.ReminderRule
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WidgetScheduleSnapshot(
    @SerialName("schedule") val schedule: TermSchedule? = null,
    @SerialName("manualCourses") val manualCourses: List<CourseItem> = emptyList(),
    @SerialName("reminderRules") val reminderRules: List<ReminderRule> = emptyList(),
    @SerialName("timingProfile") val timingProfile: TermTimingProfile? = null,
    @SerialName("termStartDateIso") val termStartDateIso: String? = null,
    @SerialName("timeZoneId") val timeZoneId: String = "Asia/Shanghai",
    @SerialName("debugForcedDateTimeIso") val debugForcedDateTimeIso: String? = null,
)

enum class WidgetDay {
    Today,
    Tomorrow,
}

interface WidgetPreferencesRepository {
    val widgetDayFlow: Flow<WidgetDay>

    val widgetDayOffsetFlow: Flow<Int>

    val timingProfileFlow: Flow<TermTimingProfile?>

    val scheduleSnapshotFlow: Flow<WidgetScheduleSnapshot?>

    suspend fun setWidgetDay(day: WidgetDay)

    suspend fun toggleWidgetDay()

    suspend fun setWidgetDayOffset(offset: Int)

    suspend fun shiftWidgetDayOffset(delta: Int)

    suspend fun widgetDayOffset(appWidgetId: Int): Int

    suspend fun setWidgetDayOffset(appWidgetId: Int, offset: Int)

    suspend fun shiftWidgetDayOffset(appWidgetId: Int, delta: Int): Int

    suspend fun clearWidgetDayOffset(appWidgetId: Int)

    suspend fun saveTimingProfile(profile: TermTimingProfile?)

    suspend fun saveScheduleSnapshot(snapshot: WidgetScheduleSnapshot?)
}
