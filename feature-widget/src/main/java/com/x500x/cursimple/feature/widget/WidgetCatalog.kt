package com.x500x.cursimple.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class WidgetCatalogEntry(
    val id: String,
    val title: String,
    val description: String,
    val provider: ComponentName,
    /** Vendor-aware twin receivers (MIUI/vivo/HONOR). Empty when not applicable. */
    val vendorProviders: List<ComponentName> = emptyList(),
    /**
     * Component-backed widgets require enabled extensions; timetable and note widgets remain
     * available independently.
     */
    val fromComponents: Boolean = false,
    val componentWidgetKey: String? = null,
)

object WidgetCatalog {
    fun entries(context: Context): List<WidgetCatalogEntry> {
        val pkg = context.packageName
        return listOf(
            WidgetCatalogEntry(
                id = "next",
                title = context.getString(R.string.widget_label_next),
                description = context.getString(R.string.widget_catalog_next_description),
                provider = ComponentName(pkg, NextCourseGlanceWidgetReceiver::class.java.name),
                vendorProviders = listOf(
                    ComponentName(pkg, "com.x500x.cursimple.feature.widget.NextCourseGlanceWidgetReceiverMIUI"),
                ),
            ),
            WidgetCatalogEntry(
                id = "today",
                title = context.getString(R.string.widget_label_today),
                description = context.getString(R.string.widget_catalog_today_description),
                provider = ComponentName(pkg, ScheduleGlanceWidgetReceiver::class.java.name),
                vendorProviders = listOf(
                    ComponentName(pkg, "com.x500x.cursimple.feature.widget.ScheduleGlanceWidgetReceiverMIUI"),
                ),
            ),
            WidgetCatalogEntry(
                id = "reminder",
                title = context.getString(R.string.widget_label_reminder),
                description = context.getString(R.string.widget_catalog_reminder_description),
                provider = ComponentName(pkg, ReminderGlanceWidgetReceiver::class.java.name),
                vendorProviders = listOf(
                    ComponentName(pkg, "com.x500x.cursimple.feature.widget.ReminderGlanceWidgetReceiverMIUI"),
                ),
            ),
            WidgetCatalogEntry(
                id = CalendarWidgetReceiver.CATALOG_ID,
                title = context.getString(R.string.widget_label_calendar),
                description = context.getString(R.string.widget_catalog_calendar_description),
                provider = ComponentName(pkg, CalendarWidgetReceiver::class.java.name),
                vendorProviders = listOf(
                    ComponentName(pkg, "com.x500x.cursimple.feature.widget.CalendarWidgetReceiverMIUI"),
                ),
            ),
            WidgetCatalogEntry(
                id = PendingTaskWidgetReceiver.CATALOG_ID,
                title = context.getString(R.string.widget_label_tasks),
                description = context.getString(R.string.widget_catalog_tasks_description),
                provider = ComponentName(pkg, PendingTaskWidgetReceiver::class.java.name),
                vendorProviders = listOf(
                    ComponentName(pkg, "com.x500x.cursimple.feature.widget.PendingTaskWidgetReceiverMIUI"),
                ),
                fromComponents = true,
            ),
            WidgetCatalogEntry(
                id = MemoTodoWidgetReceiver.CATALOG_ID,
                title = context.getString(R.string.widget_label_memo),
                description = context.getString(R.string.widget_catalog_memo_description),
                provider = ComponentName(pkg, MemoTodoWidgetReceiver::class.java.name),
                vendorProviders = listOf(ComponentName(pkg, MemoTodoWidgetReceiverMIUI::class.java.name)),
            ),
        )
    }

    /** List all app-owned widgets plus available component-backed providers. */
    fun pickerEntries(context: Context): List<WidgetCatalogEntry> {
        val base = entries(context)
        val container = base.first { it.fromComponents }
        return base.filterNot { it.fromComponents } + com.x500x.cursimple.core.data.widget.ComponentWidgetRegistry.read(context).map { definition ->
            container.copy(id = "owned:${definition.key}", title = definition.spec.title, description = definition.spec.description,
                componentWidgetKey = definition.key)
        }
    }

    fun installedCount(context: Context, entry: WidgetCatalogEntry): Int {
        val manager = AppWidgetManager.getInstance(context)
        val all = listOf(entry.provider) + entry.vendorProviders
        return all.sumOf { component ->
            runCatching { manager.getAppWidgetIds(component).count { id -> entry.componentWidgetKey == null || com.x500x.cursimple.core.data.widget.ComponentWidgetBindings.get(context, id) == entry.componentWidgetKey } }.getOrDefault(0)
        }
    }

    fun isPinSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }

    sealed interface PinRequestResult {
        data class Started(val provider: ComponentName, val hasMore: Boolean) : PinRequestResult
        data object Unsupported : PinRequestResult
        data class Failed(val message: String?) : PinRequestResult
    }

    /**
     * Try enabled vendor-compatible providers in order; request acceptance does not confirm
     * launcher placement.
     */
    fun pinCandidates(context: Context, entry: WidgetCatalogEntry): List<ComponentName> {
        // Prefer the vendor copy only on supported launchers; unregistered vendor metadata can hide otherwise valid providers.
        val vendorFirst = detectLauncherVendor(context) == LauncherVendor.Vivo
        val ordered = if (vendorFirst) {
            entry.vendorProviders + entry.provider
        } else {
            listOf(entry.provider) + entry.vendorProviders
        }
        val packageManager = context.packageManager
        return ordered.filter { component ->
            val state = runCatching { packageManager.getComponentEnabledSetting(component) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
    }

    /**
     * Known unresponsive launchers receive manual instructions despite reported pinning
     * support; users can explicitly retry.
     */
    fun pinLikelyIgnored(context: Context): Boolean = when (detectLauncherVendor(context)) {
        LauncherVendor.Vivo, LauncherVendor.Oppo -> true
        else -> false
    }

    /** [attempt] selects the next [pinCandidates] index after failure. */
    fun requestPin(context: Context, entry: WidgetCatalogEntry, attempt: Int = 0): PinRequestResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return PinRequestResult.Unsupported
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return PinRequestResult.Unsupported
        val candidates = pinCandidates(context, entry)
        if (attempt >= candidates.size) return PinRequestResult.Unsupported
        val callback = PendingIntent.getBroadcast(
            context,
            entry.id.hashCode(),
            Intent(ACTION_WIDGET_PINNED)
                .setClass(context, WidgetPinResultReceiver::class.java)
                .setPackage(context.packageName).apply { entry.componentWidgetKey?.let { data = Uri.parse("cursimple-widget://pin/" + Uri.encode(it)) } },
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )
        var lastError: String? = null
        for (index in attempt until candidates.size) {
            val provider = candidates[index]
            val accepted = runCatching {
                manager.requestPinAppWidget(provider, null, callback)
            }.getOrElse { error ->
                lastError = error.message
                false
            }
            if (accepted) {
                return PinRequestResult.Started(
                    provider = provider,
                    hasMore = index + 1 < candidates.size,
                )
            }
        }
        return PinRequestResult.Failed(lastError)
    }

    /** Action for an app-local broadcast emitted whenever the installed-widget set may have changed. */
    const val ACTION_WIDGET_INSTALLED_CHANGED: String = "com.x500x.cursimple.WIDGET_INSTALLED_CHANGED"

    /** Action delivered by requestPinAppWidget once the launcher accepts a pinned widget. */
    const val ACTION_WIDGET_PINNED: String = "com.x500x.cursimple.WIDGET_PINNED"

    fun notifyInstalledChanged(context: Context) {
        val intent = Intent(ACTION_WIDGET_INSTALLED_CHANGED).setPackage(context.packageName)
        context.applicationContext.sendBroadcast(intent)
    }

    enum class LauncherVendor {
        Miui,
        Huawei,
        Oppo,
        Vivo,
        Samsung,
        Other,
    }

    /**
     * If resolution returns the system chooser, select an actual launcher candidate before
     * vendor detection.
     */
    fun homeLauncherPackage(context: Context): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        if (!resolved.isNullOrBlank() && !isResolverPackage(resolved)) return resolved
        val candidate = runCatching {
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .firstOrNull { !isResolverPackage(it) }
        }.getOrNull()
        return candidate ?: resolved.orEmpty()
    }

    private fun isResolverPackage(packageName: String): Boolean =
        packageName == "android" || packageName.startsWith("com.android.settings")

    /** Detects the foreground launcher's vendor so we can give targeted instructions. */
    fun detectLauncherVendor(context: Context): LauncherVendor {
        val pkg = homeLauncherPackage(context)
        val device = listOf(Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.PRODUCT)
            .joinToString(" ")
        return when {
            pkg.contains("miui", ignoreCase = true) ||
                pkg.startsWith("com.mi", ignoreCase = true) ||
                device.contains("xiaomi", ignoreCase = true) ||
                device.contains("redmi", ignoreCase = true) -> LauncherVendor.Miui
            pkg.contains("huawei", ignoreCase = true) ||
                pkg.contains("honor", ignoreCase = true) ||
                device.contains("huawei", ignoreCase = true) ||
                device.contains("honor", ignoreCase = true) -> LauncherVendor.Huawei
            pkg.contains("oppo", ignoreCase = true) ||
                pkg.contains("oplus", ignoreCase = true) ||
                pkg.contains("realme", ignoreCase = true) ||
                pkg.contains("oneplus", ignoreCase = true) ||
                device.contains("oppo", ignoreCase = true) ||
                device.contains("oplus", ignoreCase = true) ||
                device.contains("realme", ignoreCase = true) ||
                device.contains("oneplus", ignoreCase = true) -> LauncherVendor.Oppo
            pkg.contains("vivo", ignoreCase = true) ||
                pkg.contains("bbk", ignoreCase = true) ||
                pkg.contains("iqoo", ignoreCase = true) ||
                device.contains("vivo", ignoreCase = true) ||
                device.contains("iqoo", ignoreCase = true) -> LauncherVendor.Vivo
            pkg.contains("samsung", ignoreCase = true) ||
                pkg.contains("sec.android", ignoreCase = true) ||
                device.contains("samsung", ignoreCase = true) -> LauncherVendor.Samsung
            else -> LauncherVendor.Other
        }
    }

    /**
     * Offer shortcut-permission destinations for manual confirmation; try candidates then app
     * details.
     */
    fun openShortcutPermission(context: Context): Boolean {
        val packageName = context.packageName
        val candidates = listOf(
            // Vendor per-app permissions include shortcut creation access.
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
            ).putExtra("packagename", packageName),
            Intent().setClassName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            ),
            // MIUI / HyperOS
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity",
            ).putExtra("extra_pkgname", packageName),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
            ).putExtra("extra_pkgname", packageName),
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.PermissionManagerActivity",
            ).putExtra("packageName", packageName),
            Intent().setClassName(
                "com.oplus.safecenter",
                "com.coloros.safecenter.permission.PermissionManagerActivity",
            ).putExtra("packageName", packageName),
        )
        for (intent in candidates) {
            val candidate = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Try destinations directly because package queries can hide vendor settings.
            if (runCatching { context.startActivity(candidate) }.isSuccess) return true
        }
        return openAppDetails(context)
    }

    /** Opens the system "App Info" page so the user can grant background-popup / floating-window permissions. */
    fun openAppDetails(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
