package com.kebiao.viewer.core.data.widget

import kotlinx.coroutines.flow.Flow

enum class WidgetDay {
    Today,
    Tomorrow,
}

interface WidgetPreferencesRepository {
    val widgetDayFlow: Flow<WidgetDay>

    suspend fun setWidgetDay(day: WidgetDay)

    suspend fun toggleWidgetDay()
}
