package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

/**
 * Hide unsupported duplicate providers while preserving copies already placed on the home
 * screen.
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
        WidgetCatalog.entries(appContext).forEach { entry ->
            if (entry.fromComponents) {
                setEnabled(packageManager, entry.provider, componentsReady)
                if (!componentsReady) {
                    entry.vendorProviders.forEach { setEnabled(packageManager, it, false) }
                    return@forEach
                }
            }
            entry.vendorProviders.forEach { component ->
                val inUse = runCatching { manager.getAppWidgetIds(component).isNotEmpty() }
                    .getOrDefault(true)
                val enabled = keepVendorCopies || inUse
                setEnabled(packageManager, component, enabled)
            }
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
