package com.x500x.cursimple.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
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

    fun setScheduleCourseTextSizeSp(sizeSp: Int) {
        viewModelScope.launch { repository.setScheduleCourseTextSizeSp(sizeSp) }
    }

    fun setScheduleCourseTextColorArgb(argb: Long) {
        viewModelScope.launch { repository.setScheduleCourseTextColorArgb(argb) }
    }

    fun setScheduleExamTextSizeSp(sizeSp: Int) {
        viewModelScope.launch { repository.setScheduleExamTextSizeSp(sizeSp) }
    }

    fun setScheduleExamTextColorArgb(argb: Long) {
        viewModelScope.launch { repository.setScheduleExamTextColorArgb(argb) }
    }

    fun setScheduleHeaderTextSizeSp(sizeSp: Int) {
        viewModelScope.launch { repository.setScheduleHeaderTextSizeSp(sizeSp) }
    }

    fun setScheduleHeaderTextColorArgb(argb: Long) {
        viewModelScope.launch { repository.setScheduleHeaderTextColorArgb(argb) }
    }

    fun setScheduleTextHorizontalCenter(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleTextHorizontalCenter(enabled) }
    }

    fun setScheduleTextVerticalCenter(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleTextVerticalCenter(enabled) }
    }

    fun setScheduleTextFullCenter(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleTextFullCenter(enabled) }
    }

    fun setScheduleCourseCornerRadiusDp(radiusDp: Int) {
        viewModelScope.launch { repository.setScheduleCourseCornerRadiusDp(radiusDp) }
    }

    fun setScheduleCourseCardHeightDp(heightDp: Int) {
        viewModelScope.launch { repository.setScheduleCourseCardHeightDp(heightDp) }
    }

    fun setScheduleOpacityPercent(percent: Int) {
        viewModelScope.launch { repository.setScheduleOpacityPercent(percent) }
    }

    fun setScheduleInactiveCourseOpacityPercent(percent: Int) {
        viewModelScope.launch { repository.setScheduleInactiveCourseOpacityPercent(percent) }
    }

    fun setScheduleGridBorderColorArgb(argb: Long) {
        viewModelScope.launch { repository.setScheduleGridBorderColorArgb(argb) }
    }

    fun setScheduleGridBorderOpacityPercent(percent: Int) {
        viewModelScope.launch { repository.setScheduleGridBorderOpacityPercent(percent) }
    }

    fun setScheduleGridBorderWidthDp(widthDp: Float) {
        viewModelScope.launch { repository.setScheduleGridBorderWidthDp(widthDp) }
    }

    fun setScheduleGridBorderDashed(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleGridBorderDashed(enabled) }
    }

    fun setScheduleBackgroundColorArgb(argb: Long) {
        viewModelScope.launch { repository.setScheduleBackgroundColorArgb(argb) }
    }

    fun setScheduleBackgroundImageUri(uri: String) {
        viewModelScope.launch { repository.setScheduleBackgroundImageUri(uri) }
    }

    fun clearScheduleBackgroundImage() {
        viewModelScope.launch { repository.clearScheduleBackgroundImage() }
    }

    fun setScheduleNodeColumnTimeEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleNodeColumnTimeEnabled(enabled) }
    }

    fun setScheduleSaturdayVisible(visible: Boolean) {
        viewModelScope.launch { repository.setScheduleSaturdayVisible(visible) }
    }

    fun setScheduleWeekendVisible(visible: Boolean) {
        viewModelScope.launch { repository.setScheduleWeekendVisible(visible) }
    }

    fun setScheduleLocationVisible(visible: Boolean) {
        viewModelScope.launch { repository.setScheduleLocationVisible(visible) }
    }

    fun setScheduleLocationPrefixAtEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleLocationPrefixAtEnabled(enabled) }
    }

    fun setScheduleTeacherVisible(visible: Boolean) {
        viewModelScope.launch { repository.setScheduleTeacherVisible(visible) }
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

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoUpdateEnabled(enabled) }
    }

    fun setIgnoredUpdateVersionCode(versionCode: Int?) {
        viewModelScope.launch { repository.setIgnoredUpdateVersionCode(versionCode) }
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
