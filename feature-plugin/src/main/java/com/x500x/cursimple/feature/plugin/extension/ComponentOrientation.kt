package com.x500x.cursimple.feature.plugin.extension

import android.content.pm.ActivityInfo

/** Temporary reader orientation; always release the page's request on exit. */
internal class ComponentOrientation(private val read: () -> Int, private val write: (Int) -> Unit) {
    private var original: Int? = null

    fun request(mode: String) {
        if (mode == "restore") { restore(); return }
        val value = when (mode) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> error("屏幕方向无效")
        }
        if (original == null) original = read()
        write(value)
    }

    fun restore() {
        val value = original ?: return
        original = null
        write(value)
    }
}
