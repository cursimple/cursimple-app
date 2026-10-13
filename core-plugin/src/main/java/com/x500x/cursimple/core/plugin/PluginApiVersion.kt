package com.x500x.cursimple.core.plugin

object PluginApiVersion {
    /** Highest supported host API; older declared versions remain compatible. */
    const val CURRENT = 12

    /** Minimum API for extension manifests. */
    const val EXTENSION_MIN = 3

    /** Minimum API for component-owned desktop widgets. */
    const val WIDGET_MIN = 9

    /** Minimum API for components that provide app layouts. */
    const val LAYOUT_MIN = 12
}
