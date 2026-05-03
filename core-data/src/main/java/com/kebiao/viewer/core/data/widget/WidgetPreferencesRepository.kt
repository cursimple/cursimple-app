package com.kebiao.viewer.core.data.widget

import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import kotlinx.coroutines.flow.Flow

enum class WidgetDay {
    Today,
    Tomorrow,
}

interface WidgetPreferencesRepository {
    val widgetDayFlow: Flow<WidgetDay>

    val widgetDayOffsetFlow: Flow<Int>

    val timingProfileFlow: Flow<TermTimingProfile?>

    suspend fun setWidgetDay(day: WidgetDay)

    suspend fun toggleWidgetDay()

    suspend fun setWidgetDayOffset(offset: Int)

    suspend fun shiftWidgetDayOffset(delta: Int)

    suspend fun saveTimingProfile(profile: TermTimingProfile?)
}
