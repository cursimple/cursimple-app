package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    val scheduleFlow: Flow<TermSchedule?>
    val lastPluginIdFlow: Flow<String>
    val lastUsernameFlow: Flow<String>
    val lastTermIdFlow: Flow<String>

    suspend fun saveSchedule(schedule: TermSchedule)
    suspend fun saveLastInput(pluginId: String, username: String, termId: String)
    suspend fun clearSchedule()
}

