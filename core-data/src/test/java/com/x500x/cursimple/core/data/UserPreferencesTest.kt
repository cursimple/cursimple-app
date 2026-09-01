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
    fun `restoring the same media uri keeps its persisted read permission`() {
        val uri = "content://media/external/images/media/42"

        assertEquals(false, shouldReleasePersistedUriPermission(uri, uri))
        assertEquals(false, shouldReleasePersistedUriPermission(null, uri))
        assertEquals(false, shouldReleasePersistedUriPermission("", uri))
    }

    @Test
    fun `restoring a different or absent media uri releases the previous permission`() {
        val previous = "content://media/external/images/media/42"

        assertEquals(true, shouldReleasePersistedUriPermission(previous, "content://media/external/images/media/7"))
        assertEquals(true, shouldReleasePersistedUriPermission(previous, null))
    }

    @Test
    fun `ai import timeout clamps unsafe values`() {
        assertEquals(MIN_AI_IMPORT_TIMEOUT_SECONDS, coerceAiImportTimeoutSeconds(0))
        assertEquals(120, coerceAiImportTimeoutSeconds(120))
        assertEquals(MAX_AI_IMPORT_TIMEOUT_SECONDS, coerceAiImportTimeoutSeconds(999))
    }

    @Test
    fun `auto silence is off by default and uses vibrate`() {
        val prefs = UserPreferences()

        assertEquals(false, prefs.autoSilence.enabled)
        assertEquals(AutoSilenceMode.Vibrate, prefs.autoSilence.mode)
    }

    @Test
    fun `auto silence session starts empty with unknown previous state`() {
        val session = UserPreferences().autoSilenceSession

        assertEquals(false, session.active)
        assertEquals(RingerModeValues.UNKNOWN, session.previousRingerMode)
        assertEquals(InterruptionFilterValues.UNKNOWN, session.previousInterruptionFilter)
        assertEquals(RingerModeValues.UNKNOWN, session.appliedRingerMode)
        assertEquals(InterruptionFilterValues.UNKNOWN, session.appliedInterruptionFilter)
        assertEquals(0L, session.startedAtMillis)
        assertEquals(0L, session.plannedEndAtMillis)
        assertEquals(0L, session.suppressedUntilMillis)
    }

    @Test
    fun `ringer and interruption filter constants match the platform values`() {
        assertEquals(0, RingerModeValues.SILENT)
        assertEquals(1, RingerModeValues.VIBRATE)
        assertEquals(2, RingerModeValues.NORMAL)
        assertEquals(1, InterruptionFilterValues.ALL)
        assertEquals(2, InterruptionFilterValues.PRIORITY)
        assertEquals(3, InterruptionFilterValues.NONE)
        assertEquals(4, InterruptionFilterValues.ALARMS)
    }

    @Test
    fun `backup export drops credential keys and keeps everything else`() {
        val entries = listOf(
            stringEntry("ai_import_api_key", "sk-secret"),
            stringEntry("ai_import_api_url", "https://api.example.com/v1"),
            stringEntry("ai_import_model", "demo-model"),
            stringEntry("theme_mode", "Dark"),
            stringEntry("webdav_password", "hunter2"),
            stringEntry("webdav_url", DEFAULT_WEBDAV_URL),
            stringEntry("webdav_username", "student@example.com"),
        )

        val exported = excludeBackupEntries(entries, USER_PREFERENCES_CREDENTIAL_KEYS)

        assertEquals(
            listOf(
                "ai_import_api_url",
                "ai_import_model",
                "theme_mode",
                "webdav_url",
                "webdav_username",
            ),
            exported.map { it.name },
        )
        assertEquals("Dark", exported.first { it.name == "theme_mode" }.stringValue)
        assertEquals("student@example.com", exported.first { it.name == "webdav_username" }.stringValue)
    }

    @Test
    fun `restoring a legacy backup ignores its credentials and keeps the local ones`() {
        val legacyBackup = listOf(
            stringEntry("theme_mode", "Dark"),
            stringEntry("webdav_password", "old-device-password"),
            stringEntry("ai_import_api_key", "sk-old-device"),
        )
        val local = listOf(
            stringEntry("theme_mode", "Light"),
            stringEntry("webdav_password", "this-device-password"),
            stringEntry("ai_import_api_key", "sk-this-device"),
        )

        val restored = mergeRestoredBackupEntries(legacyBackup, local, USER_PREFERENCES_CREDENTIAL_KEYS)

        assertEquals("Dark", restored.first { it.name == "theme_mode" }.stringValue)
        assertEquals("this-device-password", restored.first { it.name == "webdav_password" }.stringValue)
        assertEquals("sk-this-device", restored.first { it.name == "ai_import_api_key" }.stringValue)
        assertEquals(1, restored.count { it.name == "webdav_password" })
        assertEquals(1, restored.count { it.name == "ai_import_api_key" })
    }

    @Test
    fun `restoring a credential free backup keeps the local credentials`() {
        val backup = listOf(
            stringEntry("theme_mode", "Dark"),
            stringEntry("webdav_url", DEFAULT_WEBDAV_URL),
        )
        val local = listOf(
            stringEntry("webdav_password", "this-device-password"),
            stringEntry("ai_import_api_key", "sk-this-device"),
        )

        val restored = mergeRestoredBackupEntries(backup, local, USER_PREFERENCES_CREDENTIAL_KEYS)

        assertEquals("this-device-password", restored.first { it.name == "webdav_password" }.stringValue)
        assertEquals("sk-this-device", restored.first { it.name == "ai_import_api_key" }.stringValue)
        assertEquals(DEFAULT_WEBDAV_URL, restored.first { it.name == "webdav_url" }.stringValue)
    }

    @Test
    fun `restoring without local credentials leaves them unset`() {
        val backup = listOf(
            stringEntry("theme_mode", "Dark"),
            stringEntry("webdav_password", "old-device-password"),
        )

        val restored = mergeRestoredBackupEntries(backup, emptyList(), USER_PREFERENCES_CREDENTIAL_KEYS)

        assertEquals(listOf("theme_mode"), restored.map { it.name })
    }

    @Test
    fun `stores without preserved keys restore the backup unchanged`() {
        val backup = listOf(
            stringEntry("schedule_json", "{}"),
            stringEntry("username", "20250001"),
        )

        assertEquals(backup, mergeRestoredBackupEntries(backup, emptyList(), emptySet()))
        assertEquals(backup, excludeBackupEntries(backup, emptySet()))
    }

    private fun stringEntry(name: String, value: String) = PreferencesBackupEntry(
        name = name,
        type = PreferencesBackupValueType.String,
        stringValue = value,
    )
}
