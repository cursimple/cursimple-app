package com.x500x.cursimple.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.widget.ComponentWidgetRegistry
import com.x500x.cursimple.core.reminder.logging.ReminderLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Publishes each slot's owner-rendered picker preview (Android 15+ generated previews). Launchers
 * without support fall back to the app icon; no placeholder card is shipped.
 */
object ComponentWidgetPreviews {
    private const val PREFS = "component_widget_previews"
    // The platform allows roughly two preview updates per provider per hour.
    private const val CONTENT_REFRESH_MILLIS = 30 * 60_000L
    private const val WIDTH_DP = 330f
    private const val HEIGHT_DP = 148f
    private const val MAX_WIDTH_PX = 720
    private val lock = Mutex()

    suspend fun refresh(baseContext: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        lock.withLock {
            val context = AppLocale.wrap(baseContext)
            val manager = AppWidgetManager.getInstance(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val registry = ComponentWidgetRegistry.read(context)
            val now = System.currentTimeMillis()
            for (slot in 0 until ComponentWidgetSlots.COUNT) {
                val definition = ComponentWidgetSlots.assignedKey(context, slot)?.let { key -> registry.firstOrNull { it.key == key } }
                val published = prefs.getString("signature_$slot", null)
                if (definition == null) {
                    if (published != null) {
                        ComponentWidgetSlots.providers(context, slot).forEach { runCatching { manager.removeWidgetPreview(it, CATEGORY) } }
                        prefs.edit().remove("signature_$slot").remove("at_$slot").apply()
                    }
                    continue
                }
                val signature = "${definition.key}@${definition.revision}"
                if (signature == published && now - prefs.getLong("at_$slot", 0) < CONTENT_REFRESH_MILLIS) continue
                val providers = registered(context, manager, ComponentWidgetSlots.providers(context, slot).filter {
                    context.packageManager.getComponentEnabledSetting(it) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                })
                if (providers.isEmpty()) continue
                val render = ComponentWidgetHooks.render ?: return
                val bitmap = runCatching { render(definition, WIDTH_DP, HEIGHT_DP).bitmap }
                    .onFailure { ReminderLogger.warn("widget.component_preview.render_failed", mapOf("slot" to slot), it) }
                    .getOrNull() ?: continue
                val views = RemoteViews(context.packageName, R.layout.widget_component).apply {
                    setViewVisibility(R.id.component_widget_error, View.GONE)
                    setImageViewBitmap(R.id.component_widget_image, bitmap.limitWidth())
                    setContentDescription(R.id.component_widget_image, definition.spec.title)
                }
                // A false result means rate limited; the next data or definition change retries.
                val accepted = providers.all { provider ->
                    runCatching { manager.setWidgetPreview(provider, CATEGORY, views) }
                        .onFailure { ReminderLogger.warn("widget.component_preview.publish_failed", mapOf("slot" to slot), it) }
                        .getOrDefault(false)
                }
                if (accepted) prefs.edit().putString("signature_$slot", signature).putLong("at_$slot", now).apply()
            }
        }
    }

    /**
     * A freshly enabled slot registers with the widget service asynchronously.
     *
     * `getInstalledProvidersForPackage` needs API 26 and the picker-preview APIs need API 31;
     * [refresh] only calls this behind its Android 15+ guard.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun registered(context: Context, manager: AppWidgetManager, wanted: List<ComponentName>): List<ComponentName> {
        var known = emptySet<ComponentName>()
        repeat(10) {
            known = manager.getInstalledProvidersForPackage(context.packageName, null).map { it.provider }.toSet()
            if (known.containsAll(wanted)) return wanted
            delay(500)
        }
        return wanted.filter { it in known }
    }

    private fun Bitmap.limitWidth(): Bitmap = if (width <= MAX_WIDTH_PX) this
        else Bitmap.createScaledBitmap(this, MAX_WIDTH_PX, (height * MAX_WIDTH_PX.toFloat() / width).toInt().coerceAtLeast(1), true)

    private const val CATEGORY = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
}
