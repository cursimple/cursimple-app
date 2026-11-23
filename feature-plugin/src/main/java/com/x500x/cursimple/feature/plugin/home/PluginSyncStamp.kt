package com.x500x.cursimple.feature.plugin.home

import android.content.Context

/**
 * Last successful timetable sync per plugin. The plugin home page uses it to confirm a session that the
 * sync already proved valid, instead of asking the user to sign in a second time.
 */
object PluginSyncStamp {
    private const val PREFS = "plugin_sync_stamp"

    private fun baseId(pluginId: String): String = pluginId.substringBefore(":local")

    fun record(context: Context, pluginId: String, at: Long = System.currentTimeMillis()) {
        if (pluginId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(baseId(pluginId), at)
            .apply()
    }

    fun last(context: Context, pluginId: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(baseId(pluginId), 0L)
}
