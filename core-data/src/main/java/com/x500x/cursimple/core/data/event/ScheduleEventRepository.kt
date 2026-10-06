package com.x500x.cursimple.core.data.event

import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import kotlinx.coroutines.flow.Flow

interface ScheduleEventRepository {
    val eventsFlow: Flow<List<ScheduleEvent>>

    /** Replace existing IDs, otherwise append. */
    suspend fun upsert(event: ScheduleEvent)
    suspend fun remove(eventId: String)
}
