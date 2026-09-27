package com.x500x.cursimple.core.data.event

import com.x500x.cursimple.core.kernel.model.ScheduleEvent
import kotlinx.coroutines.flow.Flow

/** 用户排进课表的事务。不分学期：换课表、重新导课都不影响它。 */
interface ScheduleEventRepository {
    val eventsFlow: Flow<List<ScheduleEvent>>

    /** id 已存在就原地替换，否则追加。 */
    suspend fun upsert(event: ScheduleEvent)
    suspend fun remove(eventId: String)
}
