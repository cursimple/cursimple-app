package com.kebiao.viewer.app

import android.app.Application
import com.kebiao.viewer.core.data.DataStoreManualCourseRepository
import com.kebiao.viewer.core.data.DataStoreScheduleRepository
import com.kebiao.viewer.core.data.DataStoreUserPreferencesRepository
import com.kebiao.viewer.core.data.ManualCourseRepository
import com.kebiao.viewer.core.data.ScheduleRepository
import com.kebiao.viewer.core.data.UserPreferencesRepository
import com.kebiao.viewer.core.data.plugin.DataStorePluginRegistryRepository
import com.kebiao.viewer.core.data.reminder.DataStoreReminderRepository
import com.kebiao.viewer.core.data.term.DataStoreTermProfileRepository
import com.kebiao.viewer.core.data.term.TermProfileRepository
import com.kebiao.viewer.core.data.widget.DataStoreWidgetPreferencesRepository
import com.kebiao.viewer.core.kernel.model.TermTimingProfile
import com.kebiao.viewer.core.kernel.model.termStartLocalDate
import com.kebiao.viewer.core.plugin.PluginManager
import com.kebiao.viewer.core.reminder.ReminderCoordinator
import com.kebiao.viewer.feature.widget.ScheduleWidgetUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class AppContainer(
    private val app: Application,
) {
    val termProfileRepository: TermProfileRepository = DataStoreTermProfileRepository(app)
    private val scheduleStore = DataStoreScheduleRepository(app, termProfileRepository)
    val scheduleRepository: ScheduleRepository = scheduleStore
    val pluginRegistryRepository = DataStorePluginRegistryRepository(app)
    val reminderRepository = DataStoreReminderRepository(app)
    val widgetPreferencesRepository = DataStoreWidgetPreferencesRepository(app)
    val userPreferencesRepository: UserPreferencesRepository = DataStoreUserPreferencesRepository(app)
    private val manualStore = DataStoreManualCourseRepository(app, termProfileRepository)
    val manualCourseRepository: ManualCourseRepository = manualStore
    val pluginManager = PluginManager(
        context = app,
        registryRepository = pluginRegistryRepository,
    )
    val reminderCoordinator = ReminderCoordinator(
        context = app,
        repository = reminderRepository,
    )

    val bundledPluginCatalog: List<BundledPluginEntry> = listOf(
        BundledPluginEntry(
            pluginId = YANGTZEU_PLUGIN_ID,
            assetRoot = "plugin-dev/yangtzeu-eams-v2",
            name = "长江大学教务插件",
            description = "通过 ATrust + EAMS 抓取课表的内置插件。",
        ),
    )

    init {
        runBlocking {
            // Bootstrap term list: if empty, seed from any existing legacy termStartDate
            // so users keep their schedule after the upgrade.
            val legacyTermStart = userPreferencesRepository.preferencesFlow.first()
                .termStartDate?.toString()
            val activeTermId = termProfileRepository.ensureBootstrapped(
                defaultName = "默认学期",
                legacyTermStartDateIso = legacyTermStart,
            )
            scheduleStore.migrateLegacyScheduleIfNeeded(activeTermId)
            manualStore.migrateLegacyManualIfNeeded(activeTermId)

            // Clean up plugins that are no longer offered (e.g. legacy demo plugins from
            // earlier builds). Do NOT auto-install bundled plugins — the user adds them
            // explicitly from the plugin market.
            val catalogById = bundledPluginCatalog.associateBy { it.pluginId }
            val installedPlugins = pluginManager.getInstalledPlugins()
            installedPlugins
                .filter { it.isBundled && it.pluginId in catalogById }
                .forEach { plugin ->
                    runCatching {
                        pluginManager.ensureBundledPlugin(catalogById.getValue(plugin.pluginId).assetRoot)
                    }
                }
            installedPlugins
                .filterNot { it.pluginId in catalogById }
                .forEach { runCatching { pluginManager.removePlugin(it.pluginId) } }
        }
    }

    suspend fun installBundledPlugin(pluginId: String) {
        val entry = bundledPluginCatalog.firstOrNull { it.pluginId == pluginId } ?: return
        pluginManager.ensureBundledPlugin(entry.assetRoot)
    }

    suspend fun refreshWidgets(timingProfile: TermTimingProfile? = null) {
        if (timingProfile != null) {
            widgetPreferencesRepository.saveTimingProfile(timingProfile)
        }
        ScheduleWidgetUpdater.refreshAll(app)
    }

    suspend fun normalizeTimingProfileForActiveTerm(timingProfile: TermTimingProfile?): TermTimingProfile? {
        if (timingProfile == null) {
            return null
        }
        val activeTermId = termProfileRepository.activeTermId()
        val activeTerm = termProfileRepository.termsFlow.first()
            .firstOrNull { it.id == activeTermId }
        val activeTermStart = activeTerm?.termStartDate?.let(::parseIsoDate)
        val pluginTermStart = runCatching { timingProfile.termStartLocalDate() }.getOrNull()
        val canonicalTermStart = activeTermStart ?: pluginTermStart

        if (canonicalTermStart != null) {
            if (activeTermId.isNotBlank() && activeTermStart != canonicalTermStart) {
                termProfileRepository.setTermStartDate(activeTermId, canonicalTermStart.toString())
            }
            val prefsTermStart = userPreferencesRepository.preferencesFlow.first().termStartDate
            if (prefsTermStart != canonicalTermStart) {
                userPreferencesRepository.setTermStartDate(canonicalTermStart)
            }
            val canonicalIso = canonicalTermStart.toString()
            return if (timingProfile.termStartDate == canonicalIso) {
                timingProfile
            } else {
                timingProfile.copy(termStartDate = canonicalIso)
            }
        }

        return timingProfile
    }

    private companion object {
        const val YANGTZEU_PLUGIN_ID = "yangtzeu-eams-v2"

        fun parseIsoDate(value: String): LocalDate? =
            runCatching { LocalDate.parse(value) }.getOrNull()
    }
}

data class BundledPluginEntry(
    val pluginId: String,
    val assetRoot: String,
    val name: String,
    val description: String,
)
