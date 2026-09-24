package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.x500x.cursimple.core.reminder.logging.ReminderLogger

/**
 * 厂商副本 receiver 的可见性。
 *
 * 每个小组件都注册了两份：通用的一份，以及带 MIUI / vivo / 荣耀 元数据的厂商副本。
 * 厂商副本只有在对应系统上才有意义，可在别的手机上它同样会进小组件选择器，
 * 于是列表里每个小组件都出现两遍。这里在非厂商机型上把副本禁用掉。
 *
 * 已经放到桌面上的副本不动：禁用组件会让那个小组件直接消失。
 */
internal object WidgetProviderVisibility {

    fun apply(context: Context) {
        val appContext = context.applicationContext
        // 只有小米、vivo 的桌面真认副本上的元数据。华为 / 荣耀 / OPPO 上副本只是重复的一份，
        // 荣耀的 honorcard 标记没在荣耀那边登记过时还会把副本藏起来，所以这几家都收起副本
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
        WidgetCatalog.entries(appContext)
            .flatMap { it.vendorProviders }
            .forEach { component ->
                val inUse = runCatching { manager.getAppWidgetIds(component).isNotEmpty() }
                    .getOrDefault(true)
                val enabled = keepVendorCopies || inUse
                setEnabled(packageManager, component, enabled)
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

/** 应用启动时对齐一次厂商副本的可见性。 */
fun applyWidgetProviderVisibility(context: Context) {
    runCatching { WidgetProviderVisibility.apply(context) }
        .onFailure { error ->
            ReminderLogger.warn("widget.vendor_provider.apply.failure", emptyMap(), error)
        }
}
