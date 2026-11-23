package com.x500x.cursimple.core.data.event

import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ScheduleEventLibrary(
    val events: List<ScheduleEvent> = emptyList(),
    val termId: String = "",
    val hasCustomOrder: Boolean = false,
)

interface ScheduleEventRepository {
    val eventsFlow: Flow<List<ScheduleEvent>>
    val libraryFlow: Flow<ScheduleEventLibrary>
        get() = eventsFlow.map { ScheduleEventLibrary(events = it) }

    /** Replace existing IDs, otherwise append. */
    suspend fun upsert(event: ScheduleEvent)
    suspend fun remove(eventId: String)

    suspend fun removeFromLibrary(termId: String, eventIds: Set<String>) {
        eventIds.forEach { remove(it) }
    }

    suspend fun reorderLibrary(termId: String, orderedIds: List<String>) {
        error("Event library order storage is unavailable")
    }

    /**
     * 全部学期的事务；扩展生命周期判断要跨学期查归属，默认实现退化为当前学期。
     */
    suspend fun allEvents(): List<ScheduleEvent> = eventsFlow.first()
}
