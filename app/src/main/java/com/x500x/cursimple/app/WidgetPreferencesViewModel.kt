package com.x500x.cursimple.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.widget.WidgetPreferencesRepository
import com.x500x.cursimple.core.data.widget.WidgetThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WidgetPreferencesViewModel(
    private val repository: WidgetPreferencesRepository,
    private val refreshWidgets: suspend () -> Unit,
) : ViewModel() {
    val state: StateFlow<WidgetThemePreferences> = repository.themePreferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, WidgetThemePreferences())

    fun setWidgetThemeAccent(accent: ThemeAccent) {
        viewModelScope.launch {
            repository.setWidgetThemeAccent(accent)
            refreshWidgets()
        }
    }

    fun setWidgetBackgroundImageUri(uri: String) {
        viewModelScope.launch {
            repository.setWidgetBackgroundImageUri(uri)
            refreshWidgets()
        }
    }

    fun clearWidgetBackgroundImage() {
        viewModelScope.launch {
            repository.clearWidgetBackgroundImage()
            refreshWidgets()
        }
    }

    fun setWidgetOpenAppOnDoubleClickEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWidgetOpenAppOnDoubleClickEnabled(enabled)
            refreshWidgets()
        }
    }

    fun resetWidgetThemePreferences() {
        viewModelScope.launch {
            repository.resetWidgetThemePreferences()
            refreshWidgets()
        }
    }
}

class WidgetPreferencesViewModelFactory(
    private val repository: WidgetPreferencesRepository,
    private val refreshWidgets: suspend () -> Unit,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WidgetPreferencesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WidgetPreferencesViewModel(repository, refreshWidgets) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
