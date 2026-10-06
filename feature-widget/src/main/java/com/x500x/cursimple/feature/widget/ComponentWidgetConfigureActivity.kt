package com.x500x.cursimple.feature.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.x500x.cursimple.core.data.AppLocale
import com.x500x.cursimple.core.data.widget.ComponentWidgetBindings
import com.x500x.cursimple.core.data.widget.ComponentWidgetDefinition
import com.x500x.cursimple.core.data.widget.ComponentWidgetRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Chooses an installed owner. It contains no component names, business options or layouts. */
class ComponentWidgetConfigureActivity : Activity() {
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(AppLocale.wrap(base))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val definitions = ComponentWidgetRegistry.read(this)
        val bound = ComponentWidgetBindings.get(this, id)
        val selected = definitions.firstOrNull { it.key == bound } ?: definitions.singleOrNull()
        if (selected != null) { select(id, selected); return }
        val dp = resources.displayMetrics.density
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt())
        }
        column.addView(TextView(this).apply { text = getString(R.string.component_widget_choose); textSize = 20f })
        for (definition in definitions) column.addView(Button(this).apply {
            text = definition.spec.title + "\n" + definition.spec.description
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(0xFFE4EEF8.toInt()); cornerRadius = 12 * dp; setStroke(dp.toInt().coerceAtLeast(1), 0xFF2563EB.toInt())
            }
            setOnClickListener { select(id, definition) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * dp).toInt() })
        if (definitions.isEmpty()) column.addView(TextView(this).apply { text = getString(R.string.component_widget_unavailable) })
        setContentView(ScrollView(this).apply { addView(column) })
    }
    private fun select(id: Int, definition: ComponentWidgetDefinition) {
        if (runCatching { ComponentWidgetBindings.set(this, id, definition.key) }.isFailure) { finish(); return }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        CoroutineScope(Dispatchers.Default).launch { ComponentWidgetReceiver.updateWidgets(applicationContext, intArrayOf(id)) }
        finish()
    }
}
