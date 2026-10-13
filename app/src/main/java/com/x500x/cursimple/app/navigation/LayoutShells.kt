package com.x500x.cursimple.app.navigation

import com.x500x.cursimple.app.extension.activeExtensionRecords
import com.x500x.cursimple.core.plugin.PluginManager
import com.x500x.cursimple.core.plugin.install.InstalledPluginRecord
import kotlinx.coroutines.CancellationException

/** Shells each installed component declares, keyed by install key; unreadable packages declare none. */
internal suspend fun PluginManager.declaredLayoutShells(records: List<InstalledPluginRecord>): Map<String, Set<String>> =
    records.filter { it.isExtension }.associate { record ->
        val shells = try {
            loadExtensionPackage(record).first.extension?.layouts.orEmpty().map { it.shell }.toSet()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptySet()
        }
        record.installKey to shells
    }

/** Declared shells of installed, enabled and compatible components, keyed by plugin id. */
internal fun activeLayoutShells(
    records: List<InstalledPluginRecord>,
    shells: Map<String, Set<String>>,
    enabledPluginIds: Set<String>,
): Map<String, Set<String>> =
    activeExtensionRecords(records, enabledPluginIds).associate { it.pluginId to shells[it.installKey].orEmpty() }
