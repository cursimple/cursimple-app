package com.x500x.cursimple.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.data.AppLanguage
import com.x500x.cursimple.core.data.ClassNoticeAnimation
import com.x500x.cursimple.core.data.ClassNoticeSkin
import com.x500x.cursimple.core.data.ThemeAccent
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.data.UserPreferences
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.kernel.model.CancelCoursePlan
import com.x500x.cursimple.core.kernel.model.CourseMovePlan
import com.x500x.cursimple.core.kernel.model.TemporaryScheduleOverride
import com.x500x.cursimple.core.kernel.time.ScheduleRowFitMode
import com.x500x.cursimple.core.kernel.time.BeijingTime
import com.x500x.cursimple.core.kernel.time.WeekStartDay
import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import com.x500x.cursimple.core.kernel.model.HolidayCalendarEntry

class AppPreferencesViewModel(
    private val repository: UserPreferencesRepository,
    private val refreshScheduleOutputs: suspend () -> Unit = {},
) : ViewModel() {

    val state: StateFlow<UserPreferences> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setVendorPermissionAck(key: String, acked: Boolean) {
        viewModelScope.launch { repository.setVendorPermissionAck(key, acked) }
    }

    fun setWidgetPinUnsupportedOnDevice(unsupported: Boolean) {
        viewModelScope.launch { repository.setWidgetPinUnsupportedOnDevice(unsupported) }
    }

    fun setThemeAccent(accent: ThemeAccent) {
        viewModelScope.launch { repository.setThemeAccent(accent) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.setAppLanguage(language)
            // 小组件与提醒的文字都是本进程按语言解析好再发出去的，重画一遍才会跟着改
            refreshScheduleOutputs()
        }
    }

    fun setTermStartDate(date: LocalDate?) {
        viewModelScope.launch { repository.setTermStartDate(date) }
    }

    fun setFirstRunGuideCompleted(completed: Boolean) {
        viewModelScope.launch { repository.setFirstRunGuideCompleted(completed) }
    }

    fun markTermStartUserDecided() {
        viewModelScope.launch { repository.setTermStartUserDecided(true) }
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

    fun setScheduleTodayHeaderBackgroundColorArgb(argb: Long) {
        viewModelScope.launch { repository.setScheduleTodayHeaderBackgroundColorArgb(argb) }
    }

    fun setScheduleTextHorizontalCenter(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleTextHorizontalCenter(enabled) }
    }

    fun setScheduleAutoShrinkLongTitles(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleAutoShrinkLongTitles(enabled) }
    }

    fun setScheduleTruncationEllipsis(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleTruncationEllipsis(enabled) }
    }

    fun setClassNoticeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setClassNoticeEnabled(enabled)
            refreshScheduleOutputs()
        }
    }

    fun setClassNoticeAdvanceMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.setClassNoticeAdvanceMinutes(minutes)
            refreshScheduleOutputs()
        }
    }

    fun setClassNoticeHeadsUpEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setClassNoticeHeadsUpEnabled(enabled) }
    }

    fun setClassNoticeLockScreenEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setClassNoticeLockScreenEnabled(enabled) }
    }

    fun setClassNoticeFocusNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setClassNoticeFocusNotificationEnabled(enabled) }
    }

    fun setClassNoticeSkin(skin: ClassNoticeSkin) {
        viewModelScope.launch { repository.setClassNoticeSkin(skin) }
    }

    fun setClassNoticeAnimation(animation: ClassNoticeAnimation) {
        viewModelScope.launch { repository.setClassNoticeAnimation(animation) }
    }

    fun setClassNoticeBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setClassNoticeBlurEnabled(enabled) }
    }

    fun setClassNoticeBlurStrength(strength: Int) {
        viewModelScope.launch { repository.setClassNoticeBlurStrength(strength) }
    }

    fun setScheduleTextVerticalCenter(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleTextVerticalCenter(enabled) }
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

    fun setScheduleBackgroundImageTransparencyPercent(percent: Int) {
        viewModelScope.launch { repository.setScheduleBackgroundImageTransparencyPercent(percent) }
    }

    fun setScheduleBackgroundImageUri(uri: String) {
        viewModelScope.launch { repository.setScheduleBackgroundImageUri(uri) }
    }

    fun clearScheduleBackgroundImage() {
        viewModelScope.launch { repository.clearScheduleBackgroundImage() }
    }

    fun setScheduleBackgroundUseHeaderColor() {
        viewModelScope.launch { repository.setScheduleBackgroundUseHeaderColor() }
    }

    fun setScheduleCustomColorsAdaptToTheme(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleCustomColorsAdaptToTheme(enabled) }
    }

    fun setScheduleNodeColumnTimeEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setScheduleNodeColumnTimeEnabled(enabled) }
    }

    fun setScheduleSaturdayVisible(visible: Boolean) {
        viewModelScope.launch { repository.setScheduleSaturdayVisible(visible) }
    }

    fun setScheduleRowFitMode(mode: ScheduleRowFitMode) {
        viewModelScope.launch { repository.setScheduleRowFitMode(mode) }
    }

    fun setScheduleWeekendVisible(visible: Boolean) {
        viewModelScope.launch { repository.setScheduleWeekendVisible(visible) }
    }

    fun setCourseDragEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setCourseDragEnabled(enabled) }
    }

    fun setScheduleWeekStartDay(day: WeekStartDay) {
        viewModelScope.launch {
            repository.setScheduleWeekStartDay(day)
            refreshScheduleOutputs()
        }
    }

    fun setScheduleLocationVisible(visible: Boolean) {
        viewModelScope.launch { repository.setScheduleLocationVisible(visible) }
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

    fun setAlarmRingtoneUri(uri: String?) {
        viewModelScope.launch {
            repository.setAlarmRingtoneUri(uri)
            refreshScheduleOutputs()
        }
    }

    fun setAlarmAlertMode(mode: AlarmAlertMode) {
        viewModelScope.launch {
            repository.setAlarmAlertMode(mode)
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

    /** 拖动调课的改动：删和写放在同一个协程里按顺序做，免得两次写入互相覆盖。 */
    fun applyCourseMove(plan: CourseMovePlan) {
        applyTemporaryOverrideChanges(plan.removeIds, listOfNotNull(plan.upsert))
    }

    /** 逐门停课/恢复的改动，同样要先删后写、在一个协程里按顺序做。 */
    fun applyCancelCoursePlan(plan: CancelCoursePlan) {
        applyTemporaryOverrideChanges(plan.removeIds, plan.upserts)
    }

    private fun applyTemporaryOverrideChanges(
        removeIds: List<String>,
        upserts: List<TemporaryScheduleOverride>,
    ) {
        viewModelScope.launch {
            removeIds.forEach { repository.removeTemporaryScheduleOverride(it) }
            upserts.forEach { repository.upsertTemporaryScheduleOverride(it) }
            // 小组件、闹钟和上课提醒都按调课列表排，改完要跟着重排，和单条增删一样
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

    fun setHolidayCalendarBuiltInEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHolidayCalendarBuiltInEnabled(enabled)
            refreshScheduleOutputs()
        }
    }

    fun setAlarmKeepAliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAlarmKeepAliveEnabled(enabled)
        }
    }

    fun setSkipRemindersOnHoliday(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSkipRemindersOnHoliday(enabled)
            refreshScheduleOutputs()
        }
    }

    fun upsertHolidayCalendarEntry(entry: HolidayCalendarEntry) {
        viewModelScope.launch {
            repository.upsertHolidayCalendarEntry(entry)
            refreshScheduleOutputs()
        }
    }

    fun removeHolidayCalendarEntry(date: String) {
        viewModelScope.launch {
            repository.removeHolidayCalendarEntry(date)
            refreshScheduleOutputs()
        }
    }

    fun clearHolidayCalendarEntries() {
        viewModelScope.launch {
            repository.clearHolidayCalendarEntries()
            refreshScheduleOutputs()
        }
    }

    fun setPluginEnabled(pluginKey: String, enabled: Boolean) {
        viewModelScope.launch { repository.setPluginEnabled(pluginKey, enabled) }
    }

    fun seedEnabledPlugins(pluginKeys: Set<String>) {
        viewModelScope.launch { repository.seedEnabledPlugins(pluginKeys) }
    }

    fun setDisclaimerAccepted(accepted: Boolean) {
        viewModelScope.launch { repository.setDisclaimerAccepted(accepted) }
    }

    fun setDebugForcedDateTime(dateTime: LocalDateTime?) {
        // 先同步写入进程内的时间覆盖，应用其余部分能立刻读到新的当前时间，不必等 DataStore 提交和 flow 重新发射。
        BeijingTime.setForcedNow(dateTime)
        viewModelScope.launch {
            repository.setDebugForcedDateTime(dateTime)
            refreshScheduleOutputs()
        }
    }

    fun setLastSeenVersionCode(versionCode: Int) {
        viewModelScope.launch { repository.setLastSeenVersionCode(versionCode) }
    }

    fun setBetaUpdatesEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBetaUpdatesEnabled(enabled) }
    }

    fun setAppTimeZoneId(zoneId: String?) {
        viewModelScope.launch { repository.setAppTimeZoneId(zoneId) }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoUpdateEnabled(enabled) }
    }

    fun setIgnoredUpdateVersionCode(versionCode: Int?) {
        viewModelScope.launch { repository.setIgnoredUpdateVersionCode(versionCode) }
    }

    fun setMutedUpdateVersionCode(versionCode: Int?) {
        viewModelScope.launch { repository.setMutedUpdateVersionCode(versionCode) }
    }

    fun setUpdateNotice(versionCode: Int, versionName: String) {
        viewModelScope.launch { repository.setUpdateNotice(versionCode, versionName) }
    }

    fun clearUpdateNotice() {
        viewModelScope.launch { repository.clearUpdateNotice() }
    }

    fun setPluginRegistryRepo(repo: String) {
        viewModelScope.launch { repository.setPluginRegistryRepo(repo) }
    }

    fun setComponentMarketIndexUrl(url: String) {
        viewModelScope.launch { repository.setComponentMarketIndexUrl(url) }
    }

    fun setPrivateFilesProviderEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setPrivateFilesProviderEnabled(enabled) }
    }

    fun setWebDavSettings(url: String, username: String, password: String) {
        viewModelScope.launch { repository.setWebDavSettings(url, username, password) }
    }

    fun setAiImportSettings(apiUrl: String, apiKey: String, model: String, timeoutSeconds: Int) {
        viewModelScope.launch { repository.setAiImportSettings(apiUrl, apiKey, model, timeoutSeconds) }
    }

    fun resetScheduleAppearanceAndDisplay() {
        viewModelScope.launch { repository.resetScheduleAppearanceAndDisplay() }
    }

    fun resetAllSettings() {
        BeijingTime.setForcedNow(null)
        viewModelScope.launch {
            repository.resetAllSettings()
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
