package com.kebiao.viewer.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kebiao.viewer.core.data.ThemeMode
import com.kebiao.viewer.core.data.UserPreferences
import com.kebiao.viewer.core.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppPreferencesViewModel(
    private val repository: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<UserPreferences> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
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

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        viewModelScope.launch { repository.setPluginEnabled(pluginId, enabled) }
    }

    fun seedEnabledPlugins(pluginIds: Set<String>) {
        viewModelScope.launch { repository.seedEnabledPlugins(pluginIds) }
    }
}

class AppPreferencesViewModelFactory(
    private val repository: UserPreferencesRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppPreferencesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppPreferencesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
