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
        )
    }

    fun installedCount(context: Context, entry: WidgetCatalogEntry): Int {
        val manager = AppWidgetManager.getInstance(context)
        val all = listOf(entry.provider) + entry.vendorProviders
        return all.sumOf { component ->
            runCatching { manager.getAppWidgetIds(component).size }.getOrDefault(0)
        }
    }

    fun isPinSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }

    sealed interface PinRequestResult {
        /** 请求已被受理。[hasMore] 表示这条落空时还有别的 provider 可以再试。 */
        data class Started(val provider: ComponentName, val hasMore: Boolean) : PinRequestResult
        data object Unsupported : PinRequestResult
        data class Failed(val message: String?) : PinRequestResult
    }

    /**
     * 一键添加要依次尝试的 provider。
     *
     * 厂商启动器（vivo 尤其明显）认的是带自家元数据的那一份副本：拿通用的那份去请求，
     * `requestPinAppWidget` 照样返回 true——它只表示「请求被受理」，不表示弹窗会出现——
     * 然后桌面就把它悄悄丢了，用户既没看到系统弹窗，桌面上也什么都没多。
     * 所以在厂商机型上先请求副本；被禁用的组件直接跳过，请求它必定落空。
     */
    fun pinCandidates(context: Context, entry: WidgetCatalogEntry): List<ComponentName> {
        // 只有 vivo 要先请求副本。小米上带 miuiWidget 的副本没过小米审核时根本不显示，
        // 先请求它等于白请求，通用那份才是看得见的
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
     * 这家桌面基本不会响应一键添加。
     *
     * `isRequestPinAppWidgetSupported` 在 vivo / OPPO 上照样返回 true，请求也会被受理，
     * 然后桌面转头丢掉——实测如此，把「桌面快捷方式」权限开着也一样。系统没有接口能问出来，
     * 只能按已知的桌面直接给手动步骤，省掉那一次点了没反应再等十几秒的过程。
     * 判错时用户可以在手动步骤里点「再试一次一键添加」。
     */
    fun pinLikelyIgnored(context: Context): Boolean = when (detectLauncherVendor(context)) {
        LauncherVendor.Vivo, LauncherVendor.Oppo -> true
        else -> false
    }

    /** [attempt] 是 [pinCandidates] 里的下标；上一条落空后带下一个下标再调一次。 */
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
                .setPackage(context.packageName),
            pendingIntentFlags(),
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

    private fun pendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
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
     * 当前桌面的包名。
     *
     * 没有设过默认桌面时 resolveActivity 给回来的是系统选择器（android / com.android.settings），
     * 拿它去判厂商会判错，这时改从候选里挑一个真正的桌面。
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
        // 一个真正的桌面都查不到时，宁可报出系统给的那个包名，也好过显示「未知」
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
     * 打开厂商的「桌面快捷方式 / 创建桌面图标」权限页。
     *
     * vivo（com.bbk.launcher2）与 MIUI 上，这项权限没给时 `requestPinAppWidget` 照样返回
     * true，桌面却把请求直接丢掉——用户那边就是「点了没反应，也没有弹窗」。
     * 系统不提供这项权限的查询接口，只能把入口摆给用户自己确认。
     * 逐个试，都打不开就落到应用详情页。
     */
    fun openShortcutPermission(context: Context): Boolean {
        val packageName = context.packageName
        val candidates = listOf(
            // vivo / iQOO：权限管理里的单应用权限页，「桌面快捷方式」在这一页
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
        )
        for (intent in candidates) {
            val candidate = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolves = runCatching {
                context.packageManager.queryIntentActivities(candidate, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
            }.getOrDefault(false)
            if (!resolves) continue
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
