package com.x500x.cursimple.feature.widget

import android.graphics.Bitmap
import com.x500x.cursimple.core.data.widget.ComponentWidgetDefinition

/** A component supplies pixels and navigation regions; the host does not interpret its items. */
data class ComponentWidgetHit(val x: Float, val y: Float, val width: Float, val height: Float, val action: String = "feed")
data class ComponentWidgetRender(val bitmap: Bitmap, val hits: List<ComponentWidgetHit>)
object ComponentWidgetHooks {
    var render: (suspend (ComponentWidgetDefinition, Float, Float) -> ComponentWidgetRender)? = null
}
