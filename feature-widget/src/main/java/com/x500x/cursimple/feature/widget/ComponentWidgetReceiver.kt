package com.x500x.cursimple.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.x500x.cursimple.core.data.widget.ComponentWidgetBindings
import com.x500x.cursimple.core.data.widget.ComponentWidgetRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.x500x.cursimple.core.data.AppLocale

/** Generic desktop surface; pixels, labels and hit regions come from the owner's HTML. */
open class ComponentWidgetReceiver : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (handleVendorWidgetUpdate(context, intent) { updateWidgets(it) }) return
        super.onReceive(context, intent)
    }
    override fun onEnabled(context: Context) = WidgetLifecycleRefresher.onWidgetSetChanged(context, "component_widget_enabled")
    override fun onDisabled(context: Context) = WidgetLifecycleRefresher.onWidgetSetChanged(context, "component_widget_disabled")
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context, ids)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context, intArrayOf(id))
    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { ComponentWidgetBindings.remove(context, it) }
        WidgetLifecycleRefresher.onWidgetSetChanged(context, "component_widget_deleted")
    }
    private fun refresh(context: Context, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try { updateWidgets(context, ids) } finally { pending.finish() }
        }
    }
    companion object {
        private val updateLock = Mutex()

        suspend fun updateWidgets(baseContext: Context, ids: IntArray? = null) = updateLock.withLock {
            val context = AppLocale.wrap(baseContext)
            val manager = AppWidgetManager.getInstance(context)
            val targets = ids ?: catalogWidgetIds(context, manager, "tasks")
            val registry = ComponentWidgetRegistry.read(context)
            for (id in targets) {
                val bound = ComponentWidgetBindings.get(context, id)
                val definition = if (bound == null) registry.singleOrNull()?.also { ComponentWidgetBindings.set(context, id, it.key) }
                    else registry.firstOrNull { it.key == bound }
                val views = RemoteViews(context.packageName, R.layout.widget_component)
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300).coerceAtLeast(1).toFloat()
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180).coerceAtLeast(1).toFloat()
                val rendered = if (definition == null) null else runCatching {
                    requireNotNull(ComponentWidgetHooks.render)(definition, width, height)
                }.onFailure { android.util.Log.e("ComponentWidget", "Owned render failed: ${definition.key}", it) }.getOrNull()
                val result = rendered?.takeIf {
                    ComponentWidgetBindings.get(context, id) == definition?.key &&
                        ComponentWidgetRegistry.read(context).any { current -> current == definition }
                }
                if (result == null) {
                    views.setImageViewBitmap(R.id.component_widget_image, null)
                    views.removeAllViews(R.id.component_widget_hits)
                    views.setViewVisibility(R.id.component_widget_error, View.VISIBLE)
                    views.setTextViewText(R.id.component_widget_error, context.getString(R.string.component_widget_unavailable))
                } else {
                    views.setViewVisibility(R.id.component_widget_error, View.GONE)
                    views.setImageViewBitmap(R.id.component_widget_image, result.bitmap)
                    views.setContentDescription(R.id.component_widget_image, definition!!.spec.title)
                    views.removeAllViews(R.id.component_widget_hits)
                    val scale = context.resources.displayMetrics.density
                    result.hits.forEachIndexed { index, hit ->
                        val region = RemoteViews(context.packageName, R.layout.widget_component_hit)
                        region.setViewPadding(R.id.component_widget_hit_region, (hit.x * scale).toInt(), (hit.y * scale).toInt(),
                            ((width - hit.x - hit.width).coerceAtLeast(0f) * scale).toInt(),
                            ((height - hit.y - hit.height).coerceAtLeast(0f) * scale).toInt())
                        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra(if (hit.action == "settings") "com.x500x.cursimple.extra.OPEN_EXTENSION_SETTINGS" else WidgetDeepLinks.EXTRA_OPEN_COMPONENT_PAGE, definition.componentId)
                            data = Uri.parse("cursimple-widget://open/$id/$index")
                        }
                        if (launch != null) region.setOnClickPendingIntent(R.id.component_widget_hit_target,
                            PendingIntent.getActivity(context, id * 16 + index, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        views.addView(R.id.component_widget_hits, region)
                    }
                }
                manager.updateAppWidget(id, views)
            }
            if (targets.isNotEmpty()) WidgetLifecycleRefresher.onWidgetUpdated(context, "component_widget_update")
        }
    }
}
