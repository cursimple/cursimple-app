package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

/**
 * Hide unsupported duplicate providers and unassigned component slots while preserving copies
 * already placed on the home screen.
 */
internal object WidgetProviderVisibility {

    fun apply(context: Context) {
        val appContext = context.applicationContext
        // Enable vendor copies only where their metadata is supported; unregistered markers can hide providers elsewhere.
        val keepVendorCopies = when (WidgetCatalog.detectLauncherVendor(appContext)) {
            WidgetCatalog.LauncherVendor.Miui,
            WidgetCatalog.LauncherVendor.Vivo -> true
            WidgetCatalog.LauncherVendor.Huawei,
            WidgetCatalog.LauncherVendor.Oppo,
            WidgetCatalog.LauncherVendor.Samsung,
            WidgetCatalog.LauncherVendor.Other -> false
        }
        val manager = AppWidgetManager.getInstance(appContext)
        val packageManager = appContext.packageManager
        val componentsReady = ComponentWidgetAvailability.isAvailable(appContext)
        fun inUse(component: ComponentName) = runCatching { manager.getAppWidgetIds(component).isNotEmpty() }
            .getOrDefault(true)
        WidgetCatalog.entries(appContext).forEach { entry ->
            if (entry.fromComponents) {
                // The shared legacy provider only keeps existing placements; new ones use slots.
                (listOf(entry.provider) + entry.vendorProviders).forEach {
                    setEnabled(packageManager, it, componentsReady && inUse(it))
                }
                return@forEach
            }
            entry.vendorProviders.forEach { component ->
                setEnabled(packageManager, component, keepVendorCopies || inUse(component))
            }
        }
        for (slot in 0 until ComponentWidgetSlots.COUNT) {
            val assigned = componentsReady && ComponentWidgetSlots.assignedKey(appContext, slot) != null
            setEnabled(packageManager, ComponentWidgetSlots.provider(appContext, slot), assigned)
            val vendor = ComponentWidgetSlots.vendorProvider(appContext, slot)
            setEnabled(packageManager, vendor, assigned && (keepVendorCopies || inUse(vendor)))
        }
    }

    private fun setEnabled(packageManager: PackageManager, component: ComponentName, enabled: Boolean) {
        val target = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val current = runCatching { packageManager.getComponentEnabledSetting(component) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        if (current == target) return
        runCatching {
            packageManager.setComponentEnabledSetting(component, target, PackageManager.DONT_KILL_APP)
        }.onFailure { error ->
            ReminderLogger.warn(
                "widget.vendor_provider.toggle.failure",
                mapOf("component" to component.className, "enabled" to enabled),
                error,
            )
        }
    }
}

fun applyWidgetProviderVisibility(context: Context) {
    runCatching { WidgetProviderVisibility.apply(context) }
        .onFailure { error ->
            ReminderLogger.warn("widget.vendor_provider.apply.failure", emptyMap(), error)
        }
}
