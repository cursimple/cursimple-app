package com.x500x.cursimple.core.data

import com.x500x.cursimple.core.reminder.model.AlarmAlertMode
import com.x500x.cursimple.core.reminder.model.ReminderAlarmBackend
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesTest {
    @Test
    fun `total schedule display is enabled by default`() {
        assertTrue(UserPreferences().scheduleDisplay.totalScheduleDisplayEnabled)
    }

    @Test
    fun `schedule appearance defaults are explicit`() {
        val prefs = UserPreferences()

        assertEquals(13, prefs.scheduleTextStyle.courseTextSizeSp)
        assertEquals(13, prefs.scheduleTextStyle.examTextSizeSp)
        assertEquals(12, prefs.scheduleTextStyle.headerTextSizeSp)
        assertEquals(0xFF000000L, prefs.scheduleTextStyle.headerTextColorArgb)
        assertEquals(false, prefs.scheduleTextStyle.headerTextColorCustomized)
        assertEquals(0xFF000000L, prefs.scheduleTextStyle.todayHeaderBackgroundColorArgb)
        assertEquals(false, prefs.scheduleTextStyle.todayHeaderBackgroundColorCustomized)
        assertEquals(10, prefs.scheduleCardStyle.courseCornerRadiusDp)
        assertEquals(100, prefs.scheduleCardStyle.courseCardHeightDp)
        assertEquals(0, prefs.scheduleCardStyle.scheduleOpacityPercent)
        assertEquals(50, prefs.scheduleCardStyle.inactiveCourseOpacityPercent)
        assertEquals(100, prefs.scheduleCardStyle.gridBorderOpacityPercent)
        assertEquals(ScheduleBackgroundType.Header, prefs.scheduleBackground.type)
        assertEquals(0xFFFFFFFFL, prefs.scheduleBackground.colorArgb)
        assertEquals(false, prefs.scheduleCustomColorsAdaptToTheme)
    }

    @Test
    fun `schedule appearance coercion clamps unsafe values`() {
        assertEquals(8, ScheduleTextStylePreferences.coerceTextSizeSp(-1))
        assertEquals(32, ScheduleTextStylePreferences.coerceTextSizeSp(99))
        assertEquals(56, ScheduleCardStylePreferences.coerceCardHeightDp(1))
        assertEquals(160, ScheduleCardStylePreferences.coerceCardHeightDp(999))
        assertEquals(0, ScheduleCardStylePreferences.coerceOpacityPercent(-50))
        assertEquals(100, ScheduleCardStylePreferences.coerceOpacityPercent(200))
        assertEquals(0x12345678L, ScheduleTextStylePreferences.coerceArgb(0xFF12345678L))
    }

    @Test
    fun `schedule custom foreground colors adapt to theme while preserving alpha`() {
        assertEquals(
            0x80FFFFFFL,
            adaptScheduleForegroundColorArgb(0x80000000L, darkTheme = true, enabled = true),
        )
        assertEquals(
            0x80123456L,
            adaptScheduleForegroundColorArgb(0x80123456L, darkTheme = true, enabled = false),
        )
        assertEquals(
            0x80000000L,
            adaptScheduleForegroundColorArgb(0x80FFFFFFL, darkTheme = false, enabled = true),
        )
    }

    @Test
    fun `schedule custom background colors adapt to theme while preserving alpha`() {
        assertEquals(
            0x80000000L,
            adaptScheduleBackgroundColorArgb(0x80FFFFFFL, darkTheme = true, enabled = true),
        )
        assertEquals(
            0x80FFFFFFL,
            adaptScheduleBackgroundColorArgb(0x80000000L, darkTheme = false, enabled = true),
        )
        assertEquals(
            0x80FFFFFFL,
            adaptScheduleBackgroundColorArgb(0x80FFFFFFL, darkTheme = true, enabled = false),
        )
    }

    @Test
    fun `schedule display defaults keep existing full week behavior`() {
        val display = UserPreferences().scheduleDisplay

        assertTrue(display.nodeColumnTimeEnabled)
        assertTrue(display.saturdayVisible)
        assertTrue(display.weekendVisible)
        assertTrue(display.locationVisible)
        assertTrue(display.locationPrefixAtEnabled)
        assertTrue(display.teacherVisible)
        assertTrue(display.totalScheduleDisplayEnabled)
    }

    @Test
    fun `temporary schedule overrides are empty by default`() {
        assertEquals(emptyList<Any>(), UserPreferences().temporaryScheduleOverrides)
    }

    @Test
    fun `alarm settings default to app managed clock`() {
        val prefs = UserPreferences()

        assertEquals(ReminderAlarmBackend.AppAlarmClock, prefs.alarmBackend)
        assertEquals(null, prefs.alarmRingtoneUri)
        assertEquals(AlarmAlertMode.RingAndVibrate, prefs.alarmAlertMode)
        assertEquals(2 * 60, prefs.alarmRingDurationSeconds)
        assertEquals(60 * 5, prefs.alarmRepeatIntervalSeconds)
        assertEquals(5, prefs.alarmRepeatCount)
        assertEquals(0L, prefs.lastAlarmPollAtMillis)
    }

    @Test
    fun `market sources use explicit defaults`() {
        val prefs = UserPreferences()

        assertEquals(DEFAULT_PLUGIN_REGISTRY_REPO, prefs.pluginRegistryRepo)
        assertEquals(DEFAULT_COMPONENT_MARKET_INDEX_URL, prefs.componentMarketIndexUrl)
    }

    @Test
    fun `data access integrations are disabled or empty by default`() {
        val prefs = UserPreferences()

        assertEquals(false, prefs.privateFilesProviderEnabled)
        assertEquals(DEFAULT_WEBDAV_URL, prefs.webDavUrl)
        assertEquals("", prefs.webDavUsername)
        assertEquals("", prefs.webDavPassword)
        assertEquals("", prefs.aiImportApiUrl)
        assertEquals("", prefs.aiImportApiKey)
        assertEquals("", prefs.aiImportModel)
        assertEquals(DEFAULT_AI_IMPORT_TIMEOUT_SECONDS, prefs.aiImportTimeoutSeconds)
    }

    @Test
    fun `ai import timeout clamps unsafe values`() {
        assertEquals(MIN_AI_IMPORT_TIMEOUT_SECONDS, coerceAiImportTimeoutSeconds(0))
        assertEquals(120, coerceAiImportTimeoutSeconds(120))
        assertEquals(MAX_AI_IMPORT_TIMEOUT_SECONDS, coerceAiImportTimeoutSeconds(999))
    }
}
