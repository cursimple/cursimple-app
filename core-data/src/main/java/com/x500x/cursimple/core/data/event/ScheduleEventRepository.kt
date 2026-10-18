package com.x500x.cursimple.core.data.event

import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface ScheduleEventRepository {
    val eventsFlow: Flow<List<ScheduleEvent>>

    /** Replace existing IDs, otherwise append. */
    suspend fun upsert(event: ScheduleEvent)
    suspend fun remove(eventId: String)

    /**
     * 全部学期的事务；扩展生命周期判断要跨学期查归属，默认实现退化为当前学期。
     */
    suspend fun allEvents(): List<ScheduleEvent> = eventsFlow.first()
}
