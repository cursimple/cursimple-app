package com.kebiao.viewer.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kebiao.viewer.core.data.ThemeAccent
import com.kebiao.viewer.core.data.ThemeMode
import com.kebiao.viewer.core.data.UserPreferences
import com.kebiao.viewer.core.data.UserPreferencesRepository
import com.kebiao.viewer.core.kernel.model.TemporaryScheduleOverride
import com.kebiao.viewer.core.kernel.time.BeijingTime
import com.kebiao.viewer.core.reminder.model.ReminderAlarmBackend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class AppPreferencesViewModel(
    private val repository: UserPreferencesRepository,
    private val refreshScheduleOutputs: suspend () -> Unit = {},
) : ViewModel() {

    val state: StateFlow<UserPreferences> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setThemeAccent(accent: ThemeAccent) {
        viewModelScope.launch { repository.setThemeAccent(accent) }
    }

    fun setTermStartDate(date: LocalDate?) {
        viewModelScope.launch { repository.setTermStartDate(date) }
    }

    fun setDeveloperModeEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setDeveloperModeEnabled(enabled) }
    }

    fun setTimeZoneId(timeZoneId: String) {
        viewModelScope.launch { repository.setTimeZoneId(timeZoneId) }
    }

    fun setTotalScheduleDisplayEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setTotalScheduleDisplayEnabled(enabled) }
    }

    fun setAlarmBackend(backend: ReminderAlarmBackend) {
        viewModelScope.launch {
            repository.setAlarmBackend(backend)
            refreshScheduleOutputs()
        }
    }

    fun setAlarmRingDurationSeconds(seconds: Int) {
        viewModelScope.launch { repository.setAlarmRingDurationSeconds(seconds) }
    }

    fun setAlarmRepeatIntervalSeconds(seconds: Int) {
        viewModelScope.launch { repository.setAlarmRepeatIntervalSeconds(seconds) }
    }

    fun setAlarmRepeatCount(count: Int) {
        viewModelScope.launch { repository.setAlarmRepeatCount(count) }
    }

    fun upsertTemporaryScheduleOverride(override: TemporaryScheduleOverride) {
        viewModelScope.launch {
            repository.upsertTemporaryScheduleOverride(override)
            refreshScheduleOutputs()
        }
    }

    fun removeTemporaryScheduleOverride(id: String) {
        viewModelScope.launch {
            repository.removeTemporaryScheduleOverride(id)
            refreshScheduleOutputs()
        }
    }

    fun clearTemporaryScheduleOverrides() {
        viewModelScope.launch {
            repository.clearTemporaryScheduleOverrides()
            refreshScheduleOutputs()
        }
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        viewModelScope.launch { repository.setPluginEnabled(pluginId, enabled) }
    }

    fun seedEnabledPlugins(pluginIds: Set<String>) {
        viewModelScope.launch { repository.seedEnabledPlugins(pluginIds) }
    }

    fun setDisclaimerAccepted(accepted: Boolean) {
        viewModelScope.launch { repository.setDisclaimerAccepted(accepted) }
    }

    fun setDebugForcedDateTime(dateTime: LocalDateTime?) {
        // Apply in-process override synchronously so the rest of the app sees the new "now"
        // immediately, before DataStore commit and the flow re-emits.
        BeijingTime.setForcedNow(dateTime)
        viewModelScope.launch {
            repository.setDebugForcedDateTime(dateTime)
            refreshScheduleOutputs()
        }
    }
}

class AppPreferencesViewModelFactory(
    private val repository: UserPreferencesRepository,
    private val refreshScheduleOutputs: suspend () -> Unit = {},
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppPreferencesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppPreferencesViewModel(repository, refreshScheduleOutputs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
